package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteDate
import io.github.artemidius.blowhard.core.api.MeetingNoteGenerationRejection
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationCase
import io.github.artemidius.blowhard.core.application.MeetingNoteEvaluationExpectation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class EvaluationDataset(
    val datasetVersion: String,
    val templateVersion: String,
    val fixtureModel: String,
    val reportDate: String,
    val cases: List<EvaluationDatasetCase>,
) {
    init {
        require(cases.size in MINIMUM_CASES..MAXIMUM_CASES) { "Evaluation dataset size is invalid" }
        require(cases.map { it.id }.distinct().size == cases.size) { "Evaluation case IDs must be unique" }
    }
}

@Serializable
internal data class EvaluationDatasetCase(
    val id: String,
    val pages: List<String>,
    val providerJson: String,
    val expected: ExpectedContent? = null,
    val expectedRejection: String? = null,
) {
    fun toCoreCase(): MeetingNoteEvaluationCase {
        val content = expected?.toContent()
        val rejection = expectedRejection?.let(MeetingNoteGenerationRejection::valueOf)
        return MeetingNoteEvaluationCase(
            id = id,
            extraction = DocumentExtraction(
                sourceDocumentId = DocumentId("eval-$id"),
                processor = "synthetic-evaluation-v1",
                processedAtEpochMilliseconds = 0,
                pages = pages.mapIndexed(::ExtractedDocumentPage),
            ),
            expectation = MeetingNoteEvaluationExpectation(content != null, content, rejection),
        )
    }
}

@Serializable
internal data class ExpectedContent(
    val title: String? = null,
    val decisions: List<ExpectedDecision> = emptyList(),
    @SerialName("action_items") val actionItems: List<ExpectedAction> = emptyList(),
) {
    fun toContent(): MeetingNoteContent = MeetingNoteContent(
        title,
        decisions.map(ExpectedDecision::toDecision),
        actionItems.map(ExpectedAction::toAction),
    )
}

@Serializable
internal data class ExpectedDecision(val summary: String, val sources: List<ExpectedSource>) {
    fun toDecision(): MeetingDecision = MeetingDecision(summary, sources.map(ExpectedSource::toReference))
}

@Serializable
internal data class ExpectedAction(
    val description: String,
    val owner: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val sources: List<ExpectedSource>,
) {
    fun toAction(): MeetingActionItem = MeetingActionItem(
        description,
        owner,
        dueDate?.let(::MeetingNoteDate),
        sources.map(ExpectedSource::toReference),
    )
}

@Serializable
internal data class ExpectedSource(@SerialName("page_index") val pageIndex: Int, val excerpt: String) {
    fun toReference(): MeetingNoteSourceReference = MeetingNoteSourceReference(pageIndex, excerpt)
}

internal object EvaluationDatasetLoader {
    private val json = Json { ignoreUnknownKeys = false }

    fun load(resourceName: String = "/evaluation/dataset-v1.json"): EvaluationDataset {
        val source = checkNotNull(EvaluationDatasetLoader::class.java.getResource(resourceName)) {
            "Evaluation dataset is unavailable"
        }
        return source.openStream().bufferedReader().use { json.decodeFromString(it.readText()) }
    }
}

private const val MINIMUM_CASES = 12
private const val MAXIMUM_CASES = 20
