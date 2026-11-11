package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ToolApprovalGateTest {
    @Test
    fun `mutation cannot execute before exact explicit approval`() = runTest {
        val fixture = fixture()
        val binding = binding()

        assertEquals(ToolApprovalOutcome.ACCEPTED, fixture.gate.request(binding))
        assertEquals(ToolCallOutcome.REJECTED, fixture.gate.execute(binding).outcome)
        assertEquals(0, fixture.executions)

        assertEquals(ToolApprovalOutcome.ACCEPTED, fixture.gate.approve(binding.id))
        assertEquals(ToolCallOutcome.SUCCEEDED, fixture.gate.execute(binding).outcome)
        assertEquals(1, fixture.executions)
        assertEquals(ToolApprovalStatus.CONSUMED, fixture.gate.status(binding.id))
        assertEquals(ToolCallOutcome.REJECTED, fixture.gate.execute(binding).outcome)
        assertEquals(1, fixture.executions)
    }

    @Test
    fun `approval is bound to complete arguments destination and identity`() = runTest {
        val fixture = fixture()
        val approved = binding()
        fixture.gate.request(approved)
        fixture.gate.approve(approved.id)

        val changedArguments = approved.copy(
            proposal = proposal("{\"title\":\"changed\"}"),
        )
        val changedDestination = approved.copy(destination = ToolApprovalDestination("another notebook"))
        val changedCall = approved.copy(
            proposal = ToolCallProposal(ToolCallId("call-2"), approved.proposal.name, approved.proposal.argumentsJson),
        )

        listOf(changedArguments, changedDestination, changedCall).forEach { changed ->
            assertEquals(ToolCallOutcome.REJECTED, fixture.gate.execute(changed).outcome)
        }
        assertEquals(0, fixture.executions)
        assertEquals(ToolCallOutcome.SUCCEEDED, fixture.gate.execute(approved).outcome)
    }

    @Test
    fun `denied and expired approvals fail closed`() = runTest {
        val deniedFixture = fixture()
        val denied = binding()
        deniedFixture.gate.request(denied)
        assertEquals(ToolApprovalOutcome.ACCEPTED, deniedFixture.gate.deny(denied.id))
        assertEquals(ToolCallOutcome.REJECTED, deniedFixture.gate.execute(denied).outcome)
        assertEquals(ToolApprovalOutcome.NOT_PENDING, deniedFixture.gate.approve(denied.id))

        val expiredFixture = fixture(now = 999)
        val expired = binding(expiresAt = 1_000)
        expiredFixture.gate.request(expired)
        expiredFixture.now = 1_000
        assertEquals(ToolApprovalStatus.EXPIRED, expiredFixture.gate.status(expired.id))
        assertEquals(ToolApprovalOutcome.EXPIRED, expiredFixture.gate.approve(expired.id))
        assertEquals(ToolCallOutcome.REJECTED, expiredFixture.gate.execute(expired).outcome)
    }

    @Test
    fun `request rejects invalid unsafe and duplicate registrations`() = runTest {
        val fixture = fixture()
        val valid = binding()
        assertEquals(ToolApprovalOutcome.ACCEPTED, fixture.gate.request(valid))
        assertEquals(ToolApprovalOutcome.DUPLICATE_ID, fixture.gate.request(valid))
        assertEquals(
            ToolApprovalOutcome.INVALID_ARGUMENTS,
            fixture.gate.request(binding(id = "approval-2", arguments = "{}")),
        )
        assertEquals(
            ToolApprovalOutcome.UNKNOWN_TOOL,
            fixture.gate.request(binding(id = "approval-3", name = "unknown")),
        )

        val readOnly = registration(mutating = false)
        val gate = assertNotNull(
            ToolApprovalGate.create(listOf(ApprovedToolRegistration(readOnly)), ToolApprovalClock { 0 }),
        )
        assertEquals(ToolApprovalOutcome.NOT_MUTATING, gate.request(valid))
        assertEquals(null, ToolApprovalGate.create(emptyList(), ToolApprovalClock { 0 }))
        assertEquals(
            null,
            ToolApprovalGate.create(
                listOf(ApprovedToolRegistration(readOnly), ApprovedToolRegistration(readOnly)),
                ToolApprovalClock { 0 },
            ),
        )
    }

    @Test
    fun `interrupted execution consumes consent without replay`() = runTest {
        val binding = binding()
        val registration = ApprovedToolRegistration(
            RegisteredTool(registration().definition) { throw CancellationException("interrupted") },
        )
        val gate = assertNotNull(
            ToolApprovalGate.create(listOf(registration), ToolApprovalClock { 0 }),
        )
        gate.request(binding)
        gate.approve(binding.id)

        assertFailsWith<CancellationException> { gate.execute(binding) }
        assertEquals(ToolApprovalStatus.CONSUMED, gate.status(binding.id))
        assertEquals(ToolCallOutcome.REJECTED, gate.execute(binding).outcome)
    }

    private fun fixture(now: Long = 0): ApprovalFixture = ApprovalFixture(now)
}

private class ApprovalFixture(var now: Long) {
    var executions: Int = 0
    val gate: ToolApprovalGate = assertNotNull(
        ToolApprovalGate.create(
            listOf(
                ApprovedToolRegistration(
                    RegisteredTool(registration().definition) {
                        executions += 1
                        ToolExecutionResult.Completed("{\"saved\":true}")
                    },
                ),
            ),
            ToolApprovalClock { now },
        ),
    )
}

private fun registration(mutating: Boolean = true): RegisteredTool = RegisteredTool(
    ToolDefinition(
        ToolName("create_note"),
        "Create one local note",
        TOOL_SCHEMA,
        mutating,
    ),
) { ToolExecutionResult.Completed("{\"saved\":true}") }

private const val TOOL_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"title":{"type":"string"}},"required":["title"]}"""

private fun binding(
    id: String = "approval-1",
    name: String = "create_note",
    arguments: String = "{\"title\":\"Release plan\"}",
    expiresAt: Long = 1_000,
): ToolApprovalBinding = ToolApprovalBinding(
    ToolApprovalId(id),
    proposal(arguments, name),
    ToolApprovalDestination("Local notes / Release plan"),
    expiresAt,
)

private fun proposal(arguments: String, name: String = "create_note"): ToolCallProposal =
    ToolCallProposal(ToolCallId("call-1"), ToolName(name), arguments)
