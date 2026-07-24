// Serveur de chat : canaux, diffusion, relais vers le serveur de jeu.
// Seul module a dependre de Netty.

plugins {
    `java-library`
}

dependencies {
    api(project(":AL-Commons"))

    // DETTE : Netty 3.x (package org.jboss.netty), fin de vie. Migration Netty 4
    // a prevoir apres le palier B.
    implementation(libs.netty3)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.aionemu.chatserver.ChatServer",
            "Implementation-Version" to project.version,
        )
    }
}
