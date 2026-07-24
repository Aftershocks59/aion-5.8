// Build the authentication server: accounts, sessions, game server table.

plugins {
    `java-library`
}

dependencies {
    api(project(":AL-Commons"))
}

// Run the server straight from the module directory, where config/ and data/
// already sit, instead of staging a distribution copy.
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Start the login server against the local configuration"
    mainClass = "com.aionemu.loginserver.LoginServer"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    jvmArgs("-javaagent:${project(":AL-Commons").tasks.jar.get().archiveFile.get().asFile}")
    dependsOn(project(":AL-Commons").tasks.jar)

    // Forward credentials as JVM system properties rather than relying on the
    // environment: the Gradle daemon does not reliably propagate environment
    // changes to forked processes. Accepts -PAION_DB_PASSWORD=... or the
    // matching environment variable.
    listOf("AION_DB_HOST", "AION_DB_PORT", "AION_DB_USER", "AION_DB_PASSWORD", "AION_LS_DB_NAME")
        .forEach { key ->
            (project.findProperty(key) as String? ?: System.getenv(key))
                ?.let { systemProperty(key, it) }
        }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.aionemu.loginserver.LoginServer",
            "Implementation-Version" to project.version,
        )
    }
}
