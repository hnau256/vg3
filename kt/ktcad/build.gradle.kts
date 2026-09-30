import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec

plugins {
    id(hnau.plugins.kotlin.serialization.get().pluginId)
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation(hnau.kotlinx.serialization.json)
}

val processorDir = file("../../processor")
val schemaFile = file("../../scheme/vg3.schema.json")
val generatedKotlinDir = layout.buildDirectory.dir("generated/vg3/kotlin")

val generateVg3Schema by tasks.registering(Exec::class) {
    group = "vg3"
    description = "Generate the IR JSON Schema from the Rust model (processor/vg3-schema)."
    workingDir = processorDir
    commandLine("cargo", "run", "-p", "vg3-schema", "--", schemaFile.absolutePath)
    inputs.dir(File(processorDir, "crates/model"))
    inputs.dir(File(processorDir, "crates/schema"))
    inputs.file(File(processorDir, "Cargo.toml"))
    inputs.file(File(processorDir, "Cargo.lock"))
    outputs.file(schemaFile)
}

val codegen by configurations.creating

dependencies {
    add(codegen.name, project(":gen"))
}

val generateVg3Model by tasks.registering(JavaExec::class) {
    group = "vg3"
    description = "Generate @Serializable Kotlin classes from the IR JSON Schema (build/generated/vg3)."
    classpath = codegen
    mainClass.set("org.hnau.ktcad.gen.MainKt")
    args(schemaFile.absolutePath, generatedKotlinDir.get().asFile.absolutePath)
    dependsOn(generateVg3Schema)
    inputs.file(schemaFile)
    outputs.dir(generatedKotlinDir)
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(generatedKotlinDir)
}

tasks.named("compileKotlin") {
    dependsOn(generateVg3Model)
}
