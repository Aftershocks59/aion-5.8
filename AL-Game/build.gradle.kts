// Serveur de jeu. Contient l'essentiel du code (~2950 fichiers).
//
// Note : data/scripts/ (quetes, IA, instances) n'est PAS compile ici. Ces
// sources sont compilees a chaud au demarrage par le ScriptCompiler d'AL-Commons.

plugins {
    `java-library`
}

dependencies {
    api(project(":AL-Commons"))

    implementation(libs.trove4j)
    implementation(libs.joda.time)
    implementation(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)

    // DETTE : lambdaj est abandonne depuis 2013 et absent de Maven Central dans
    // cette version. Consomme depuis le jar local en attendant son retrait
    // (palier B) au profit des streams Java 8. hamcrest n'existe que pour lui.
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
