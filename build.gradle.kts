// Share the settings common to the four modules.
//
// Let each module declare `plugins { java-library }` itself: that is what
// generates its typed accessors (api, implementation, tasks.jar...). React to
// that application here so the order stays irrelevant.

subprojects {
    group = "com.aionemu"
    version = "5.8"

    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        // Map the source sets onto the historic flat "src/" tree rather than the
        // Maven "src/main/java" convention, to avoid moving 3265 files.
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
            // Silence warnings while migrating: on a Java 7 era codebase they
            // would bury the real errors. Re-enable once the migration lands.
            options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:none"))
            options.isDeprecation = false
        }

        tasks.withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}
