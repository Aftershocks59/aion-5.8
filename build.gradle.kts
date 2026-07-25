import java.time.Duration
import java.util.concurrent.TimeUnit

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

// Boot both servers, pair them, and fail on anything the logs report.
//
// Three of the four regressions found so far were invisible to the compiler and
// to the unit tests: a task manager that threw once its queue ran dry, a driver
// that refused a blob where its predecessor converted silently, and a batch call
// JDBC forbids. Every one of them surfaced as a stack trace in a log during a
// real boot. This automates the reading that caught them.
//
// It does not replace a client: nothing here logs a character in. It covers
// startup, the login and game server handshake, and any exception raised while
// the servers settle.
tasks.register("smokeTest") {
    group = "verification"
    description = "Start both servers, verify they pair, and fail on any exception logged"

    dependsOn(":AL-Login:classes", ":AL-Game:classes", ":AL-Login:compileScripts", ":AL-Game:compileScripts")

    // Resolve everything the task needs while configuring: reaching into another
    // project while executing is what breaks under the configuration cache.
    val login = ServerUnderTest(
        name = "LoginServer",
        mainClass = "com.aionemu.loginserver.LoginServer",
        workingDir = project(":AL-Login").projectDir,
        classpath = project(":AL-Login").extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath,
        readyMarker = "Login Server started",
    )
    val game = ServerUnderTest(
        name = "GameServer",
        mainClass = "com.aionemu.gameserver.GameServer",
        workingDir = project(":AL-Game").projectDir,
        classpath = project(":AL-Game").extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath,
        readyMarker = "Connected to LoginServer",
    )
    val agent = project(":AL-Commons").tasks.named<Jar>("jar")
    val credentials = listOf("AION_DB_HOST", "AION_DB_PORT", "AION_DB_USER", "AION_DB_PASSWORD")
        .mapNotNull { key -> (findProperty(key) as String? ?: System.getenv(key))?.let { key to it } }
        .toMap()

    doLast {
        runSmokeTest(listOf(login, game), agent.get().archiveFile.get().asFile, credentials)
    }
}

/** Describes one server the smoke test has to bring up. */
data class ServerUnderTest(
    val name: String,
    val mainClass: String,
    val workingDir: File,
    val classpath: FileCollection,
    /** Line proving the server is up; the game server only counts once paired. */
    val readyMarker: String,
)

/**
 * Starts each server in order, waits for its marker, then reports what the logs
 * hold.
 *
 * Always stops what it started, including on failure: a leftover server holds
 * ports 2106, 7777 and 9014 and makes every later run fail for the wrong reason.
 */
fun runSmokeTest(servers: List<ServerUnderTest>, agentJar: File, credentials: Map<String, String>) {
    val readyTimeout = Duration.ofMinutes(3)
    val started = mutableListOf<Pair<ServerUnderTest, Process>>()
    val failures = mutableListOf<String>()

    try {
        for (server in servers) {
            val output = File.createTempFile("smoke-${server.name}-", ".log")
            val command = mutableListOf(
                "${System.getProperty("java.home")}/bin/java",
                "-javaagent:${agentJar.absolutePath}",
                "-Xmx4g",
            )
            credentials.forEach { (key, value) -> command += "-D$key=$value" }
            command += listOf("-cp", server.classpath.asPath, server.mainClass)

            logger.lifecycle("smokeTest: starting ${server.name}")
            val process = ProcessBuilder(command)
                .directory(server.workingDir)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()
            started += server to process

            if (!awaitMarker(output, server.readyMarker, readyTimeout) { process.isAlive }) {
                failures += "${server.name} never reported \"${server.readyMarker}\"."
                failures += tail(output, 40)
                return
            }
            logger.lifecycle("smokeTest: ${server.name} is up")

            // Let the scheduled work fire: the ranking refresh and the periodic task
            // managers only run once the server has settled, and that is where two of
            // the regressions showed.
            Thread.sleep(20_000)
            failures += scanForFailures(server.name, output)
        }
    } finally {
        started.reversed().forEach { (server, process) ->
            logger.lifecycle("smokeTest: stopping ${server.name}")
            process.destroy()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException("smokeTest failed:\n" + failures.joinToString("\n"))
        }
        logger.lifecycle("smokeTest: both servers started and logged no failure")
    }
}

/**
 * Waits for a marker to appear in a growing file.
 *
 * @return true if the marker appeared, false on timeout or if the process died
 */
fun awaitMarker(output: File, marker: String, timeout: Duration, alive: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (System.nanoTime() < deadline) {
        if (output.exists() && output.readText().contains(marker)) {
            return true
        }
        if (!alive()) {
            return false
        }
        Thread.sleep(1_000)
    }
    return false
}

/**
 * Reports the failures a server log holds.
 *
 * Matches the shapes that actually cost us a working server, rather than every
 * line carrying the word error: a stack trace, an aborted script context, or the
 * handler refusing to start.
 */
fun scanForFailures(name: String, output: File): List<String> {
    val text = if (output.exists()) output.readText() else return listOf("$name produced no output.")
    val patterns = mapOf(
        "Exception in thread" to "an uncaught exception",
        "Critical Error" to "a critical error",
        "Error while compiling classes" to "a script context that failed to compile",
        "ConcurrentModificationException" to "a collection modified while being walked",
        "SQLException" to "a database failure",
        "SQLSyntaxErrorException" to "an invalid statement",
        "SQLDataException" to "a value the driver refused",
    )

    return patterns.mapNotNull { (needle, description) ->
        if (text.contains(needle)) "$name logged $description ($needle)." else null
    }
}

/** Returns the last lines of a file, to explain a failure without dumping it whole. */
fun tail(output: File, lines: Int): List<String> =
    if (output.exists()) output.readLines().takeLast(lines) else listOf("(no output)")

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

        // Package the context so the server loads it instead of compiling it again.
        // Compiling the scripts costs about twenty seconds of every start and only
        // repeats what this task just did.
        //
        // The archive name must match PrecompiledScripts.archiveFor: the root path
        // relative to data/scripts, separators turned into dashes. A mismatch only
        // makes the server compile from source, never misbehave.
        val archiveName = root.relativeTo(scriptsDir).invariantSeparatorsPath.replace('/', '-')

        val packageTask = tasks.register<Jar>("packageScripts$suffix") {
            description = "Package the ${root.relativeTo(projectDir).invariantSeparatorsPath} script context"
            from(contextTask.map { it.destinationDirectory })
            // Write beside the other generated caches, where the server reads them.
            destinationDirectory = layout.projectDirectory.dir("cache/scripts")
            archiveFileName = "$archiveName.jar"
        }

        compileScripts.configure { dependsOn(packageTask) }
    }

    // Fail the check task, so a broken script stops a build rather than a boot.
    tasks.named("check") { dependsOn(compileScripts) }
}
