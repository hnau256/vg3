rootProject.name = "KtCAD"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.hnau.plugin.settings") version "1.28.1"
}

hnau {
    publish {
        version = "1.0.0"
        gitUrl = "https://github.com/hnau256/vg3"
        exclude = listOf(
            ":gen-schema",
            ":gen-solid",
        )
    }
}
