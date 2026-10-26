package newm

import dev.caulis.log.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

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
 * config.kts: compiled by newm (on a thread of its own: the first time, or after a change, a
 * few seconds; then it comes from ~/.cache/newm) and run on the compositor's thread.
 */
object KtsConfig {

    /** A compiled config.kts, or what is wrong in it ("config.kts:12: Unresolved reference: foo"). */
    class Compiled internal constructor(internal val script: CompiledScript?, val problems: List<String>)

    private val host by lazy {
        BasicJvmScriptingHost(
            ScriptingHostConfiguration {
                jvm {
                    compilationCache(
                        CompiledScriptJarsCache { source, _ ->
                            Files.createDirectories(cacheFolder())
                            cacheFolder().resolve(key(source) + ".jar").toFile()
                        },
                    )
                }
            },
        )
    }

    /** Compiles [file] (slow: not on the compositor's thread). */
    fun compile(file: Path): Compiled {
        val name = file.fileName.toString()
        val code = try {
            Files.readString(file)
        } catch (e: Exception) {
            return Compiled(null, listOf("$name cannot be read: ${e.message ?: e}"))
        }
        val started = System.nanoTime()
        val result = host.runInCoroutineContext {
            host.compiler(code.toScriptSource(name), createJvmCompilationConfigurationFromTemplate<NewmScript>())
        }
        log.info { "$name compiled in ${(System.nanoTime() - started) / 1_000_000} ms" }
        return Compiled(result.valueOrNull(), problems(name, result))
    }

    /** Runs a compiled config.kts: its settings, or its problems. */
    fun run(compiled: Compiled, config: NewmConfig, name: String = "config.kts"): List<String> {
        val script = compiled.script ?: return compiled.problems
        val result = host.runInCoroutineContext { host.evaluator(script, ScriptEvaluationConfiguration { implicitReceivers(config) }) }
        val problems = problems(name, result).toMutableList()
        val value = result.valueOrNull()?.returnValue
        if (value is ResultValue.Error) {
            val error = value.error
            val line = error.stackTrace.firstOrNull { it.fileName == name }?.lineNumber
            problems += "$name${if (line != null && line > 0) ":$line" else ""}: ${error.message ?: error}"
        }
        return problems.distinct()
    }

    private fun problems(name: String, result: ResultWithDiagnostics<*>): List<String> = result.reports
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

    // The script, and what it was compiled against (another newm needs another compilation).
    private fun key(source: SourceCode): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(source.text.toByteArray())
        for (entry in System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)) {
            val jar = File(entry)
            digest.update(entry.toByteArray())
            if (jar.isFile) digest.update("${jar.length()}:${jar.lastModified()}".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }
}
