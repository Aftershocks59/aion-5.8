// Configuration commune aux quatre modules.
//
// Chaque module declare lui-meme `plugins { java-library }` : c'est ce qui
// genere ses accesseurs types (api, implementation, tasks.jar...). Ici on se
// contente de reagir a cette application, ce qui rend l'ordre indifferent.

subprojects {
    group = "com.aionemu"
    version = "5.8"

    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        // Le depot utilise une arborescence historique "src/" a plat plutot que
        // la convention Maven "src/main/java". On adapte le layout au code
        // existant au lieu de deplacer 3265 fichiers.
        extensions.configure<SourceSetContainer> {
            named("main") {
                java.setSrcDirs(listOf("src"))
                resources.setSrcDirs(emptyList<String>())
            }
            named("test") {
                java.setSrcDirs(emptyList<String>())
                resources.setSrcDirs(emptyList<String>())
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            // Base heritee de Java 7 : le bruit des avertissements masquerait
            // les erreurs reelles pendant la migration. A reactiver au palier B.
            options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:none"))
            options.isDeprecation = false
        }

        tasks.withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}
