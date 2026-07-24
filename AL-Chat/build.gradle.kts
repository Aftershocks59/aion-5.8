// Build the chat server: channels, broadcasting, relay to the game server.
// Note this is the only module that depends on Netty.

plugins {
    `java-library`
}

dependencies {
    api(project(":AL-Commons"))

    // DEBT: migrate off Netty 3.x (org.jboss.netty package), which is end of
    // life, once the dependency clean-up stage lands.
    implementation(libs.netty3)

    // Inject the chat services through Guice. Only com.google.inject.* is used,
    // so the javax/jakarta namespace split does not apply here.
    implementation(libs.guice)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.aionemu.chatserver.ChatServer",
            "Implementation-Version" to project.version,
        )
    }
}
