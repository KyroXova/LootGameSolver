plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = providers.gradleProperty("modVersion").get()

val releaseChangelog = layout.projectDirectory.file(providers.gradleProperty("releaseChangelog").get())
tasks.named<ProcessResources>("processResources") {
    from(releaseChangelog) { into("META-INF"); rename { "CHANGELOG.md" } }
}
val verifyReleaseContent by tasks.registering {
    inputs.file(releaseChangelog)
    inputs.property("releaseVersion", version.toString())
    doLast {
        val notes = inputs.files.singleFile.readText()
        check(notes.isNotBlank() && notes.lineSequence().first().contains(inputs.properties["releaseVersion"].toString())) {
            "Release changelog must exist, contain release notes, and name the current version"
        }
    }
}
tasks.named("check") { dependsOn(verifyReleaseContent) }
tasks.matching { it.name == "publishCurseforge" }.configureEach {
    dependsOn("check")
}
