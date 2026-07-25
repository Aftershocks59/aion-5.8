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
                java.setSrcDirs(listOf("test"))
                resources.setSrcDirs(listOf("test-resources"))
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

        // Run tests on the JUnit 5 platform.
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }

        registerScriptCompilation()
    }
}

/**
 * Compiles the runtime script contexts the way the server does.
 *
 * data/scripts holds thousands of sources the server compiles at startup, one
 * context at a time, each into its own classloader. Gradle never saw them, so a
 * broken script surfaced only after a full boot, and one bad file aborts its
 * whole context: quests, instances and admin commands silently stopped loading.
 *
 * Each context compiles on its own. A single shared javac run would accept
 * references across contexts that the runtime classloaders reject, which is
 * exactly the mistake this is meant to catch.
 */
fun Project.registerScriptCompilation() {
    val scriptsDir = file("data/scripts")
    if (!scriptsDir.isDirectory) {
        return
    }

    // Read the roots from the descriptors the server itself reads, so a context
    // added later is picked up without touching this build.
    val rootPattern = Regex("""root\s*=\s*"([^"]+)"""")
    val roots = fileTree(scriptsDir) { include("**/*.xml") }.files
        .flatMap { descriptor -> rootPattern.findAll(descriptor.readText()).map { it.groupValues[1] }.toList() }
        .map { file(it.removePrefix("./")) }
        .filter { it.isDirectory }
        .distinctBy { it.absolutePath }

    val compileScripts = tasks.register("compileScripts") {
        group = "verification"
        description = "Compile every runtime script context, one classloader at a time"
    }

    val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main")

    roots.forEach { root ->
        val suffix = root.relativeTo(scriptsDir).invariantSeparatorsPath
            .split('/')
            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

        val contextTask = tasks.register<JavaCompile>("compileScripts$suffix") {
            description = "Compile the ${root.relativeTo(projectDir).invariantSeparatorsPath} script context"
            source = fileTree(root) { include("**/*.java") }
            classpath = files(mainSourceSet.map { it.runtimeClasspath })
            destinationDirectory = layout.buildDirectory.dir("script-classes/$suffix")
        }

        compileScripts.configure { dependsOn(contextTask) }
    }

    // Fail the check task, so a broken script stops a build rather than a boot.
    tasks.named("check") { dependsOn(compileScripts) }
}
