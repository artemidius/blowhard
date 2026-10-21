package io.github.artemidius.blowhard.evaluation

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

/** Runs the deterministic fixture evaluation and writes machine-readable and reviewer reports. */
public fun main(args: Array<String>) {
    val output = EvaluationArguments.parse(args)
    val dataset = EvaluationDatasetLoader.load()
    val report = runBlocking { FixtureEvaluationRunner.run(dataset) }
    Files.createDirectories(output)
    Files.writeString(output.resolve("meeting-note-fixture-report.json"), EvaluationReportRenderer.json(report))
    Files.writeString(output.resolve("meeting-note-fixture-report.md"), EvaluationReportRenderer.markdown(report))
    println("Wrote ${report.sampleCount} synthetic fixture results to $output")
}

internal object EvaluationArguments {
    fun parse(args: Array<String>): Path {
        require(args.size <= 2) { "Usage: evaluation [--output directory]" }
        require(args.isEmpty() || (args.size == 2 && args[0] == "--output")) {
            "Usage: evaluation [--output directory]"
        }
        val path = if (args.isEmpty()) "build/reports/evaluation" else args[1]
        require(path.isNotBlank()) { "Output directory must not be blank" }
        return Path.of(path).toAbsolutePath().normalize()
    }
}
