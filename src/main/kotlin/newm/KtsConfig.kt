package newm

import dev.caulis.log.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.loadScriptFromJar
import kotlin.script.experimental.jvmhost.saveToJar

private val log = Logger("config")

/** What config.kts is: Kotlin, written inside newm's configuration ([NewmConfig]). */
@KotlinScript(fileExtension = "kts", compilationConfiguration = NewmScriptCompilation::class)
abstract class NewmScript

object NewmScriptCompilation : ScriptCompilationConfiguration({
    implicitReceivers(NewmConfig::class)
    // (The bar's helpers by their names: an object's members cannot be imported with *.)
    defaultImports(
        "newm.*", "newm.SystemInfo.cpu", "newm.SystemInfo.memory", "newm.SystemInfo.temperature", "newm.SystemInfo.battery",
        "newm.SystemInfo.wifi", "newm.SystemInfo.date", "newm.SystemInfo.time", "newm.SystemInfo.user",
    )
    jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
}) {
    private fun readResolve(): Any = NewmScriptCompilation
}

/**
 * config.kts: compiled by a Java of its own (a short-lived process: the Kotlin compiler never
 * stays in newm — it is hundreds of megabytes), kept compiled in ~/.cache/newm/config-kts by
 * the script and the newm it was compiled for; newm itself only loads the compiled script
 * and runs it on the compositor's thread. The first start, or after a change: a few seconds;
 * then it comes from the cache (no compiler at all).
 */
object KtsConfig {

    /** A compiled config.kts, or what is wrong in it ("config.kts:12: Unresolved reference: foo"). */
    class Compiled internal constructor(internal val script: CompiledScript?, val problems: List<String>)

    /** The compiler process's Java options by default. */
    val DEFAULT_JVM_OPTIONS = listOf("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss4m", "-Djava.awt.headless=true")

    const val DEFAULT_TIMEOUT_SECONDS = 180L

    /**
     * The compiler's Java options (compositor { configCompilerJvmOptions }, else
     * NEWM_CONFIG_COMPILER_OPTS): small and quick to start — it runs a few seconds and ends.
     */
    @Volatile
    var jvmOptions: List<String> = System.getenv("NEWM_CONFIG_COMPILER_OPTS")?.takeIf { it.isNotBlank() }?.trim()?.split(Regex("\\s+")) ?: DEFAULT_JVM_OPTIONS

    /** At most this long for a compilation (compositor { configCompilerTimeoutSeconds }, else NEWM_CONFIG_COMPILER_TIMEOUT): then it is stopped, and said. */
    @Volatile
    var timeoutSeconds: Long = System.getenv("NEWM_CONFIG_COMPILER_TIMEOUT")?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_SECONDS

    /** Compiles [file] (slow the first time: not on the compositor's thread when it can be helped). */
    fun compile(file: Path): Compiled {
        val name = file.fileName.toString()
        val code = try {
            Files.readString(file)
        } catch (e: Exception) {
            return Compiled(null, listOf("$name cannot be read: ${e.message ?: e}"))
        }
        return compile(code, name)
    }

    /** Compiles [code], named [name] in what is said of it ("config.kts:12: ..."). */
    fun compile(code: String, name: String): Compiled {
        val started = System.nanoTime()
        val folder = cacheFolder()
        val jar = folder.resolve(key(code, name) + ".jar")
        // Compiled before for this newm: loaded, nothing compiled.
        if (Files.isRegularFile(jar)) {
            val loaded = try {
                jar.toFile().loadScriptFromJar(checkMissingDependencies = true)
            } catch (e: Exception) {
                log.info { "$jar cannot be loaded ($e): compiled anew" }
                null
            }
            if (loaded != null) {
                // (Newest again, so it is not the one cleared away.)
                jar.toFile().setLastModified(System.currentTimeMillis())
                log.info { "$name compiled before: taken from $folder in ${(System.nanoTime() - started) / 1_000_000} ms" }
                return Compiled(loaded, emptyList())
            }
            runCatching { Files.deleteIfExists(jar) }
        }
        val problems = try {
            Files.createDirectories(folder)
            compileElsewhere(code, name, jar)
        } catch (e: Exception) {
            // (The compiler itself failed, not the file: said like a mistake in it, never a silent thread.)
            log.info { "$name could not be compiled: $e" }
            return Compiled(null, listOf("$name cannot be compiled: ${e.message ?: e}"))
        }
        log.info { "$name compiled in ${(System.nanoTime() - started) / 1_000_000} ms (a Java of its own)" }
        clearOldJars()
        if (problems.isNotEmpty() || !Files.isRegularFile(jar)) return Compiled(null, problems.ifEmpty { listOf("$name cannot be compiled (the compiler said nothing)") })
        val loaded = try {
            jar.toFile().loadScriptFromJar(checkMissingDependencies = true)
        } catch (e: Exception) {
            null
        } ?: return Compiled(null, listOf("$name was compiled but cannot be loaded"))
        return Compiled(loaded, emptyList())
    }

    // Runs the compiler in a Java of its own: [code] into [jar]; what is wrong in it.
    private fun compileElsewhere(code: String, name: String, jar: Path): List<String> {
        val folder = jar.parent
        val source = Files.createTempFile(folder, "compiling-", ".kts")
        val output = Files.createTempFile(folder, "compiling-", ".out")
        try {
            Files.writeString(source, code)
            val java = ProcessHandle.current().info().command().orElse(null)?.takeIf { Files.isExecutable(Path.of(it)) }
                ?: Path.of(System.getProperty("java.home"), "bin", "java").toString()
            val own = System.getProperty("java.class.path").orEmpty()
            val classpath = (own.split(File.pathSeparator) + compilerJars()).filter { it.isNotEmpty() }.distinct().joinToString(File.pathSeparator)
            val command = listOf(java) + jvmOptions + listOf(
                "--enable-native-access=ALL-UNNAMED",
                // What the script may use: newm's own class path (not the compiler's jars).
                "-Dnewm.scriptClasspath=$own",
                "-cp", classpath, KtsCompilerProcess::class.java.name, source.toString(), jar.toString(), name,
            )
            log.info { "Compiling $name in a Java of its own (${jvmOptions.joinToString(" ")}, at most $timeoutSeconds s)" }
            val process = dev.caulis.ffi.Processes.start(ProcessBuilder(command)
                .redirectOutput(output.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null"))))
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return listOf("$name: compiling took longer than $timeoutSeconds seconds and was stopped (compositor { configCompilerTimeoutSeconds })")
            }
            val lines = Files.readAllLines(output)
            val problems = lines.filter { it.startsWith(PROBLEM) }.map { it.removePrefix(PROBLEM).replace("\\n", "\n").replace("\\\\", "\\") }
            if (lines.none { it == DONE } && problems.isEmpty()) {
                return listOf("$name cannot be compiled: the compiler ended with code ${process.exitValue()}")
            }
            return problems
        } finally {
            runCatching { Files.deleteIfExists(source) }
            runCatching { Files.deleteIfExists(output) }
        }
    }

    // The compiler's own jars: in lib/compiler beside newm's (not on newm's class path, so never
    // loaded into it); none there (tests, a build of its own): they are on the class path.
    private fun compilerJars(): List<String> {
        val home = runCatching { Path.of(KtsConfig::class.java.protectionDomain.codeSource.location.toURI()).parent }.getOrNull() ?: return emptyList()
        val dir = home.resolve("compiler")
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { all -> all.filter { it.toString().endsWith(".jar") }.map { it.toString() }.sorted().toList() }
    }

    // Every save (and every newm) compiles anew into ~/.cache/newm: only the newest few are kept.
    private fun clearOldJars() {
        try {
            val jars = cacheFolder().toFile().listFiles { f -> f.isFile && f.name.endsWith(".jar") }.orEmpty()
            val old = jars.sortedByDescending { it.lastModified() }.drop(KEPT_JARS)
            for (jar in old) jar.delete()
            if (old.isNotEmpty()) log.info { "${old.size} old compiled config.kts cleared from ${cacheFolder()}" }
        } catch (e: Exception) {
            log.info { "${cacheFolder()} could not be cleared: $e" }
        }
    }

    private const val KEPT_JARS = 5

    /** Runs a compiled config.kts: its settings, or its problems. */
    fun run(compiled: Compiled, config: NewmConfig, name: String = "config.kts"): List<String> {
        val script = compiled.script ?: return compiled.problems
        val result = kotlinx.coroutines.runBlocking { BasicJvmScriptEvaluator()(script, ScriptEvaluationConfiguration { implicitReceivers(config) }) }
        val problems = problems(name, result).toMutableList()
        val value = result.valueOrNull()?.returnValue
        if (value is ResultValue.Error) {
            val error = value.error
            val line = error.stackTrace.firstOrNull { it.fileName == name }?.lineNumber
            problems += "$name${if (line != null && line > 0) ":$line" else ""}: ${error.message ?: error}"
        }
        return problems.distinct()
    }

    internal fun problems(name: String, result: ResultWithDiagnostics<*>): List<String> = result.reports
        .filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
        .map { report ->
            val line = report.location?.start?.line
            "$name${if (line != null) ":$line" else ""}: ${report.message}"
        }

    private fun cacheFolder(): Path {
        val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.startsWith("/") }?.let { Path.of(it) }
            ?: Path.of(System.getenv("HOME") ?: System.getProperty("user.home"), ".cache")
        return base.resolve("newm").resolve("config-kts")
    }

    // The script (and its name: the compiled class is named by it), and what it was compiled
    // against: another newm needs another compilation.
    private fun key(code: String, name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(name.toByteArray())
        digest.update(0)
        digest.update(code.toByteArray())
        for (entry in System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)) {
            val jar = File(entry)
            digest.update(entry.toByteArray())
            if (jar.isFile) digest.update("${jar.length()}:${jar.lastModified()}".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    // The compiler process's lines: a problem each, then that it is done.
    internal const val PROBLEM = "problem "
    internal const val DONE = "done"
}

/**
 * The compiler's side (a Java of its own, started by [KtsConfig]): compiles a config.kts into
 * a jar, says each problem on a line, and ends. Arguments: the script, the jar, its name.
 */
object KtsCompilerProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val (source, jar, name) = args
        val code = Files.readString(Path.of(source))
        val classpath = System.getProperty("newm.scriptClasspath")?.split(File.pathSeparator)?.filter { it.isNotEmpty() }?.map(::File)
        val base = createJvmCompilationConfigurationFromTemplate<NewmScript>()
        // Compiled against newm's class path (what it runs with), not this process's (the compiler's own jars too).
        val configuration = if (classpath == null) base else ScriptCompilationConfiguration(base) {
            dependencies(JvmDependency(classpath))
        }
        val compiler = JvmScriptCompiler(defaultJvmScriptingHostConfiguration)
        val out = StringBuilder()
        val result = try {
            kotlinx.coroutines.runBlocking { compiler(code.toScriptSource(name), configuration) }
        } catch (e: Exception) {
            // (The compiler itself failed, not the script.)
            print("${KtsConfig.PROBLEM}$name cannot be compiled: ${(e.message ?: e.toString()).replace("\n", " ")}\n${KtsConfig.DONE}\n")
            System.out.flush()
            Runtime.getRuntime().halt(0)
            return
        }
        for (problem in KtsConfig.problems(name, result)) out.append(KtsConfig.PROBLEM).append(problem.replace("\\", "\\\\").replace("\n", "\\n")).append('\n')
        val compiled = result.valueOrNull() as? KJvmCompiledScript
        if (compiled != null) {
            // (Written whole, then put in its place: newm never loads half a jar.)
            val target = Path.of(jar)
            val partial = Files.createTempFile(target.parent, "compiled-", ".part")
            try {
                compiled.saveToJar(partial.toFile())
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(partial)
            }
        }
        out.append(KtsConfig.DONE).append('\n')
        print(out)
        System.out.flush()
        // (The compiler may leave threads of its own behind.)
        Runtime.getRuntime().halt(0)
    }
}
