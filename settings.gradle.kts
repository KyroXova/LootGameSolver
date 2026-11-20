pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle & Central mirror
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version ("2.0.34")
}
