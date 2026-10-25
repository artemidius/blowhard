package io.github.artemidius.blowhard.evaluation

import io.github.artemidius.blowhard.adapters.mistral.MistralChatConfiguration
import io.github.artemidius.blowhard.adapters.mistral.MistralMeetingNoteGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.platform.DesktopCredentialStores
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path

/** Runs the deterministic fixture evaluation and writes machine-readable and reviewer reports. */
public fun main(args: Array<String>) {
    val configuration = EvaluationArguments.parse(args)
    val dataset = EvaluationDatasetLoader.load()
    val report = when (configuration) {
        is EvaluationRunConfiguration.Fixture -> runBlocking { FixtureEvaluationRunner.run(dataset) }
        is EvaluationRunConfiguration.Live -> runLive(dataset, configuration)
    }
    Files.createDirectories(configuration.output)
    val prefix = "meeting-note-${report.executionKind}-report"
    Files.writeString(configuration.output.resolve("$prefix.json"), EvaluationReportRenderer.json(report))
    Files.writeString(configuration.output.resolve("$prefix.md"), EvaluationReportRenderer.markdown(report))
    println("Wrote ${report.sampleCount} ${report.executionKind} results to ${configuration.output}")
}

internal object EvaluationArguments {
    fun parse(args: Array<String>): EvaluationRunConfiguration = when {
        args.isEmpty() -> EvaluationRunConfiguration.Fixture(defaultOutput())

        args.size == FIXTURE_ARGUMENT_COUNT && args.first() == "--output" ->
            EvaluationRunConfiguration.Fixture(output(args.last()))

        else -> parseLive(args)
    }

    private fun parseLive(args: Array<String>): EvaluationRunConfiguration.Live {
        require(args.size == LIVE_ARGUMENT_COUNT) { USAGE }
        val values = args.iterator()
        require(values.next() == "--live" && values.next() == "--model") { USAGE }
        val modelId = values.next()
        require(values.next() == "--max-cases") { USAGE }
        val casesRaw = values.next()
        require(values.next() == "--output") { USAGE }
        val outputRaw = values.next()
        require(modelId.matches(MODEL_ID_PATTERN)) { "Model ID is invalid" }
        val maximumCases = casesRaw.toIntOrNull()
        require(maximumCases != null && maximumCases in 1..MAXIMUM_LIVE_CASES) {
            "Live case limit is invalid"
        }
        return EvaluationRunConfiguration.Live(output(outputRaw), modelId, maximumCases)
    }

    private fun output(path: String): Path {
        require(path.isNotBlank()) { "Output directory must not be blank" }
        return Path.of(path).toAbsolutePath().normalize()
    }

    private fun defaultOutput(): Path = output("build/reports/evaluation")
}

internal sealed class EvaluationRunConfiguration(val output: Path) {
    class Fixture(output: Path) : EvaluationRunConfiguration(output)

    class Live(output: Path, val modelId: String, val maximumCases: Int) : EvaluationRunConfiguration(output)
}

private fun runLive(dataset: EvaluationDataset, configuration: EvaluationRunConfiguration.Live): EvaluationReport {
    val credentials = DesktopCredentialStores.mistralApiKey().asMistralCredentialProvider()
    val port = MistralMeetingNoteGenerationPort.create(credentials, MistralChatConfiguration(configuration.modelId))
    return try {
        runBlocking {
            withTimeout(LIVE_TOTAL_TIMEOUT_MILLIS) {
                LiveEvaluationRunner.run(dataset, configuration.maximumCases, configuration.modelId, port)
            }
        }
    } finally {
        port.close()
    }
}

private const val LIVE_ARGUMENT_COUNT = 7
private const val FIXTURE_ARGUMENT_COUNT = 2
private const val MAXIMUM_LIVE_CASES = 6
private const val LIVE_TOTAL_TIMEOUT_MILLIS = 7 * 60 * 1_000L
private const val USAGE =
    "Usage: evaluation [--output directory] | --live --model id --max-cases 1..6 --output directory"
private val MODEL_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,255}$")
