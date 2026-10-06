import org.gradle.api.tasks.JavaExec
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation("org.hnau.ktcad:ktcad-ktcad:1.0.0")
}

val generatedDir = layout.buildDirectory.dir("generated/examples")
val templateFile = layout.projectDirectory.file("src/main/resources/ExampleTemplate.kt")
val stubFile = layout.projectDirectory.file("src/main/resources/ExampleStub.kt")

val inputFile = providers
    .gradleProperty("input")
    .map { file(it) }
    .orElse(providers.provider { stubFile.asFile })

val outputDir = providers
    .gradleProperty("out")
    .map { file(it) }
    .orElse(layout.buildDirectory.dir("examples").map { it.asFile })

// The single wrapper shared by every example: `run { <snippet> }` (see ExampleTemplate.kt).
val generateExample by tasks.registering {
    group = "docs"
    description = "Wrap the snippet selected with -Pinput into the shared ExampleTemplate."
    inputs.file(inputFile)
    inputs.file(templateFile)
    outputs.dir(generatedDir)
    doLast {
        val body = inputFile.get().readText()
        val template = templateFile.asFile.readText()
        val generated = generatedDir.get()
            .file("org/hnau/ktcad/docs/generated/Example.kt")
            .asFile
        generated.parentFile.mkdirs()
        generated.writeText(template.replace("/*{{BODY}}*/", body))
    }
}

val exportAssets by tasks.registering(JavaExec::class) {
    group = "docs"
    description = "Compile the selected example and export its IR JSON and PNG (-Pinput, -Pout)."
    dependsOn(generateExample)
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.hnau.ktcad.docs.MainKt")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(inputFile.get().absolutePath, outputDir.get().absolutePath)
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

tasks.named("compileKotlin") { dependsOn(generateExample) }
