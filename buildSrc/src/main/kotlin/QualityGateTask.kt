import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import javax.inject.Inject

abstract class QualityGateTask @Inject constructor(
    private val layout: ProjectLayout,
    objects: ObjectFactory,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sarifFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:Input
    val failOnFindings: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    @TaskAction
    fun runGate() {
        // Only include existing SARIF files
        val existing = sarifFiles.files.filter { it.isFile && it.extension == "sarif" }
        if (existing.isEmpty()) {
            logger.warn("[qualityGate] No SARIF files found. Did detekt run?")
            return
        }
        val ruleRegex = Regex("\"ruleId\"\\s*:\\s*\"([^\"]+)\"")
        val total = existing.sumOf { f -> ruleRegex.findAll(f.readText()).count() }
        if (total > 0 && failOnFindings.get()) {
            throw GradleException("qualityGate failed: $total detekt findings detected. Fix before building.")
        }
        if (total == 0) {
            logger.lifecycle("[qualityGate] Passed: 0 detekt findings and spotless clean.")
        } else {
            logger.lifecycle("[qualityGate] Passed with $total findings (failOnFindings disabled).")
        }
    }
}
