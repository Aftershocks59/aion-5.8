// Bibliotheque partagee par les trois serveurs (jeu, login, chat).
// Produit egalement le javaagent d'instrumentation des callbacks.

plugins {
    `java-library`
}

dependencies {
    api(libs.slf4j.api)
    api(libs.logback.classic)
    api(libs.logback.core)
    api(libs.guava)
    api(libs.commons.io)
    api(libs.commons.lang)
    api(libs.quartz)
    api(libs.javassist)
    api(libs.bonecp)
    api(libs.mysql)
    api(libs.javolution)
    api(libs.jsr305)
    api(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)
    api(libs.activation)
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "com.aionemu.commons.callbacks.JavaAgentEnhancer",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Version" to project.version,
        )
    }
}
