plugins {
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation(hnau.kotlinpoet.core)
    implementation(hnau.kotlinx.serialization.json)
}
