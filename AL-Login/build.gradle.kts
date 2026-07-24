// Build the authentication server: accounts, sessions, game server table.

plugins {
    `java-library`
}

dependencies {
    api(project(":AL-Commons"))
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.aionemu.loginserver.LoginServer",
            "Implementation-Version" to project.version,
        )
    }
}
