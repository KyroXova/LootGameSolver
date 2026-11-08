plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = providers.gradleProperty("modVersion").get()

val releaseChangelog = layout.projectDirectory.file(providers.gradleProperty("releaseChangelog").get())
val releaseVersion = version.toString()
tasks.named<ProcessResources>("processResources") {
    from(releaseChangelog) { into("META-INF"); rename { "CHANGELOG.md" } }
}
val verifyReleaseContent by tasks.registering {
    val changelogFile = releaseChangelog.asFile
    val currentVersion = releaseVersion
    inputs.file(changelogFile)
    inputs.property("releaseVersion", currentVersion)
    doLast {
        val notes = changelogFile.readText()
        check(notes.isNotBlank() && notes.lineSequence().first().contains(currentVersion)) {
            "Release changelog must exist, contain release notes, and name the current version"
        }
    }
}
tasks.named("check") { dependsOn(verifyReleaseContent) }
tasks.matching { it.name == "publishCurseforge" }.configureEach {
    dependsOn("check")
}
