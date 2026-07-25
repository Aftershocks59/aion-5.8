// Share this library across the three servers (game, login, chat).
// Produce the java agent that instruments the callback classes.

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
    api(libs.hikari)
    api(libs.mariadb)
    // Version the two schemas. flyway-mysql carries the MySQL and MariaDB support,
    // which flyway-core no longer ships on its own.
    api(libs.flyway.core)
    api(libs.flyway.mysql)
    api(libs.jsr305)
    api(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)
    api(libs.activation)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
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
