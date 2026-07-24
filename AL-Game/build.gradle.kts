// Build the game server, which holds most of the code (~2950 files).
//
// Note that data/scripts/ (quests, AI, instances) is NOT compiled here: the
// ScriptCompiler in AL-Commons compiles those sources at server startup.

plugins {
    `java-library`
}

dependencies {
    api(project(":AL-Commons"))

    implementation(libs.trove4j)
    implementation(libs.joda.time)
    implementation(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)

    // DEBT: consume lambdaj from the local jar. The library was abandoned in
    // 2013 and this version is missing from Maven Central. Drop it in favour of
    // Java 8 streams; hamcrest exists only to satisfy it.
    implementation(files("libs/lambdaj-2.4.jar"))
    implementation(libs.hamcrest)
}

// Run the server straight from the module directory, where config/ and data/
// already sit, instead of staging a distribution copy of roughly 600 MB.
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Start the game server against the local configuration"
    mainClass = "com.aionemu.gameserver.GameServer"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    maxHeapSize = "4g"
    jvmArgs("-javaagent:${project(":AL-Commons").tasks.jar.get().archiveFile.get().asFile}")
    dependsOn(project(":AL-Commons").tasks.jar)

    // Forward credentials as JVM system properties rather than relying on the
    // environment: the Gradle daemon does not reliably propagate environment
    // changes to forked processes. Accepts -PAION_DB_PASSWORD=... or the
    // matching environment variable.
    listOf("AION_DB_HOST", "AION_DB_PORT", "AION_DB_USER", "AION_DB_PASSWORD", "AION_GS_DB_NAME")
        .forEach { key ->
            (project.findProperty(key) as String? ?: System.getenv(key))
                ?.let { systemProperty(key, it) }
        }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.aionemu.gameserver.GameServer",
            "Implementation-Version" to project.version,
        )
    }
}
