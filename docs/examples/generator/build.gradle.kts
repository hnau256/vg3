import org.gradle.api.tasks.JavaExec
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation("org.hnau.ktcad:ktcad-ktcad:1.0.0")
}

val generatedDir = layout.buildDirectory.dir("generated/examples")
val templateFile = layout.projectDirectory.file("src/main/resources/ExamplesTemplate.kt")

val examplesDir = providers
    .gradleProperty("examples")
    .map { file(it) }
    .orElse(providers.provider { layout.projectDirectory.dir("..").asFile })

val outputDir = providers
    .gradleProperty("out")
    .map { file(it) }
    .orElse(layout.buildDirectory.dir("examples").map { it.asFile })

val imageSize = providers.gradleProperty("size").orElse("512")
val imageCompression = providers.gradleProperty("compression").orElse("6")

// One generated file holding every example as an entry of `listOf(...)`, so a single JVM run
// compiles them all and `Main` iterates the list.
val generateExamples by tasks.registering {
    group = "docs"
    description = "Turn every *.kt in -Pexamples into a single generated listOf (Examples.kt)."
    inputs.files(
        examplesDir.map { dir ->
            dir.listFiles { file -> file.isFile && file.extension == "kt" }?.toList().orEmpty()
        },
    )
    inputs.file(templateFile)
    outputs.dir(generatedDir)
    doLast {
        val files = examplesDir.get()
            .listFiles { file -> file.isFile && file.extension == "kt" }
            ?.sortedBy { it.name }
            .orEmpty()
        val blocks = files.joinToString(",\n") { file ->
            val lines = file.readText().lines()
            val camera = lines
                .map(String::trim)
                .firstOrNull { it.startsWith("// vg3:") }
                ?.removePrefix("// vg3:")
                ?.trim()
                .orEmpty()
            val body = lines
                .filterNot { it.trim().startsWith("// vg3:") }
                .joinToString("\n")
            buildString {
                append("    Example(\n")
                append("        id = \"${file.nameWithoutExtension}\",\n")
                append("        camera = \"${camera.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
                append("        build = {\n")
                append(body)
                append("\n        },\n")
                append("    )")
            }
        }
        val generated = generatedDir.get()
            .file("org/hnau/ktcad/docs/generated/Examples.kt")
            .asFile
        generated.parentFile.mkdirs()
        generated.writeText(templateFile.asFile.readText().replace("/*{{EXAMPLES}}*/", blocks))
    }
}

val exportAssets by tasks.registering(JavaExec::class) {
    group = "docs"
    description = "Export IR JSON and PNG for every example (-Pexamples, -Pout, -Psize, -Pcompression)."
    dependsOn(generateExamples)
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.hnau.ktcad.docs.MainKt")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(outputDir.get().absolutePath, imageSize.get(), imageCompression.get())
        },
    )
    val vg3 = file("../../../processor/target/debug/vg3")
    if (vg3.exists()) {
        environment("VG3_BIN", vg3.absolutePath)
    }
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(generatedDir)
}

tasks.named("compileKotlin") { dependsOn(generateExamples) }
