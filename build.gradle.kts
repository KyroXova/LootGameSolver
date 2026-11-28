import java.util.zip.ZipFile

plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("jacoco")
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
tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}
tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.named("check") { dependsOn(verifyReleaseContent) }
tasks.matching { it.name == "publishCurseforge" }.configureEach {
    dependsOn("check")
    dependsOn("bundledBaritoneJar")
}

// The local Baritone artifact is supplied explicitly for integration builds.
val baritoneJarPath = providers.gradleProperty("baritoneJar")
val bundledBaritoneJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assemble LootGameSolver and a supplied production Baritone jar"
    dependsOn("reobfJar")
    archiveClassifier.set("bundled-staging")
    // The published jar replaces reobfJar's output, so a normal build can overwrite it.
    outputs.upToDateWhen { false }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ zipTree(tasks.named("reobfJar").get().outputs.files.singleFile) })
    from({
        val supplied = baritoneJarPath.orNull ?: throw GradleException("Supply -PbaritoneJar=<production jar>")
        val jar = file(supplied)
        if (!jar.isFile) throw GradleException("Baritone jar does not exist: $jar")
        zipTree(jar)
    })
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    manifest.attributes(
        "MixinConfigs" to "mixins.baritone.json",
        "MixinConnector" to "baritone.launch.BaritoneMixinConnector",
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true"
    )
    val finalDest = layout.buildDirectory.file("libs/lootgamesolver-${project.version}.jar")
    doLast {
        val target = finalDest.get().asFile
        archiveFile.get().asFile.copyTo(target, overwrite = true)
        ZipFile(target).use { bundled ->
            check(bundled.getEntry("baritone/api/BaritoneAPI.class") != null) { "Bundled jar is missing Baritone" }
            check(bundled.getEntry("com/kyroxova/lootgamesolver/client/ClientHooks.class") != null) {
                "Bundled jar is missing LootGameSolver"
            }
        }
        archiveFile.get().asFile.delete()
    }
}
