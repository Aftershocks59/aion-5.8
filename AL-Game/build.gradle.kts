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

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.aionemu.gameserver.GameServer",
            "Implementation-Version" to project.version,
        )
    }
}
