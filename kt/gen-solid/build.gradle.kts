plugins {
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation(hnau.ksp.api)
    implementation(hnau.kotlinpoet.core)
    implementation(hnau.kotlinpoet.ksp)
}
