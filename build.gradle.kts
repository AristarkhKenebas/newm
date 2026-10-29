// newm on Caulis: the Kotlin port of newm (github.com/jbuchermn/newm, MIT).

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // The native bars are Compose.
    alias(libs.plugins.kotlin.compose)
    application
}

group = "io.github.aristarkhkenebas"
version = "0.1.0-SNAPSHOT"

val javaVersion = libs.versions.java.asProvider().get().toInt()
val bytecodeVersion = libs.versions.java.bytecode.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(bytecodeVersion.toString())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = bytecodeVersion
}

dependencies {
    implementation(libs.caulis.wm)
    implementation(libs.caulis.compose)
    implementation(libs.caulis.screenshot)
    // config.lua.
    implementation(libs.luaj)
    // config.kts, compiled by newm itself.
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    // (Tests that start newm never reach the desktop's buses: no names taken on the session
    // bus, nothing asked of logind, NetworkManager or BlueZ.)
    environment("DBUS_SESSION_BUS_ADDRESS", "unix:path=/nonexistent/newm-test-session-bus")
    environment("DBUS_SYSTEM_BUS_ADDRESS", "unix:path=/nonexistent/newm-test-system-bus")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

application {
    mainClass = "newm.MainKt"
    applicationName = "newm"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-XX:+UseZGC")
}

// `newm --version` says it.
tasks.jar {
    manifest { attributes("Implementation-Version" to project.version) }
}

// The templates (config.kts and the rest, newm's wallpaper) go into newm itself: newm writes
// them for a user who has none, and `newm install-session` into /etc/newm.
tasks.processResources {
    from("templates") { into("newm/templates") }
}

// The `newm` script finds a Java new enough by itself: JAVA_HOME or the `java` on PATH when
// they are, else the newest under /usr/lib/jvm (Arch's default Java may be older than newm's).
// With none, it says what to install instead of Java's own error.
tasks.startScripts {
    val needed = bytecodeVersion
    doLast {
        val find = """
            |# newm needs Java $needed or newer: found here when JAVA_HOME and `java` are older.
            |newm_java_version() { sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "${'$'}1/release" 2>/dev/null; }
            |newm_java_ok() { v=${'$'}(newm_java_version "${'$'}1"); [ -n "${'$'}v" ] && [ "${'$'}v" -ge $needed ]; }
            |if [ -n "${'$'}JAVA_HOME" ] && newm_java_ok "${'$'}JAVA_HOME"; then
            |    :
            |elif [ -z "${'$'}JAVA_HOME" ] && command -v java >/dev/null 2>&1 && newm_java_ok "${'$'}(dirname "${'$'}(dirname "${'$'}(readlink -f "${'$'}(command -v java)")")")"; then
            |    :
            |else
            |    JAVA_HOME=
            |    for newm_jvm in ${'$'}(ls -d /usr/lib/jvm/*/ 2>/dev/null | sort -rV); do
            |        if newm_java_ok "${'$'}{newm_jvm%/}"; then JAVA_HOME=${'$'}{newm_jvm%/}; break; fi
            |    done
            |    if [ -z "${'$'}JAVA_HOME" ]; then
            |        echo "newm needs Java $needed or newer (on Arch: sudo pacman -S jdk-openjdk)." >&2
            |        exit 1
            |    fi
            |    export JAVA_HOME
            |fi
            |# newm needs a D-Bus session bus (its tray, notifications, the portals). Without systemd nothing
            |# starts one at login: newm then starts again inside dbus-run-session, which makes one for as
            |# long as newm runs. Only for newm itself (no arguments), not `newm cmd ...`. NEWM_DBUS=off:
            |# never (newm then only warns in its log). (An environment variable, not a setting: this is
            |# decided before newm reads its configuration.)
            |# The compositor itself (no arguments), not `newm cmd ...` or `newm --version`: only it gets the
            |# AOT cache (one made while `newm --version` ran would be the compositor's after) and the restart loop.
            |newm_compositor=
            |[ ${'$'}# -eq 0 ] && newm_compositor=1
            |if [ -n "${'$'}newm_compositor" ] && [ "${'$'}{NEWM_DBUS:-auto}" != off ] && [ -z "${'$'}DBUS_SESSION_BUS_ADDRESS" ] \
            |    && ! [ -S "${'$'}{XDG_RUNTIME_DIR:-/nonexistent}/bus" ] && command -v dbus-run-session >/dev/null 2>&1; then
            |    echo "newm: no D-Bus session bus: starting inside dbus-run-session (NEWM_DBUS=off: not)" >&2
            |    exec dbus-run-session -- "${'$'}0"
            |fi
            |
            """.trimMargin()
        val script = unixScript
        val text = script.readText()
        // Right after the license header, before the script looks for java.
        val at = text.indexOf("\n# Attempt to set APP_HOME")
        check(at > 0) { "the start script changed: newm's Java finding is not in it" }
        // `newm cmd restart` ends newm with code 3: it starts again here. This shell stays as the
        // restarting one, Java its child: what the shell is sent (TERM, INT, HUP: `pkill newm`,
        // the end of a login) it passes on to Java, and its code is the shell's. With
        // NEWM_SUPERVISED set (dev/newm-dev, which builds anew first and restarts itself) there
        // is no loop: Java takes the shell's place (exec), the signals go straight to it.
        val launch = "exec \"${'$'}JAVACMD\" \"${'$'}@\""
        // A JDK AOT cache (JEPs 483, 514, 515): the classes newm loads, linked, and how its code
        // ran, kept in ~/.cache/newm/aot — one per build. The key: the Java (its release file
        // and path), where newm lies (a Nix store path differs for every build, as its files'
        // times are all the same there), its jars' sizes and times, and the Java options.
        // There: used (-XX:AOTCache); not yet: made while this newm runs, written when it ends
        // (-XX:AOTCacheOutput) under a name of its own (two newm at once do not write one
        // file), taken as the cache at the next start. A cache that does not fit only makes Java
        // say so in its log (newm starts as usual); one Java said was broken is removed at the
        // next start, made anew. Never in the way: no cache when the folder cannot be written
        // (or its path has a ":" or "," Java's log option cannot take), and when Java still does
        // not start with it, once more without. NEWM_AOT=0: none.
        val aot = """
            |newm_aot() {
            |    newm_aot_opt=
            |    [ "${'$'}{NEWM_AOT:-1}" = 0 ] || [ -n "${'$'}newm_aot_off" ] || [ -z "${'$'}newm_compositor" ] && return
            |    newm_aot_dir="${'$'}{XDG_CACHE_HOME:-${'$'}HOME/.cache}/newm/aot"
            |    case "${'$'}newm_aot_dir" in *[:,]*) return ;; esac
            |    { mkdir -p "${'$'}newm_aot_dir" && [ -w "${'$'}newm_aot_dir" ]; } 2>/dev/null || return
            |    newm_aot_key=${'$'}({ cat "${'$'}(dirname "${'$'}(dirname "${'$'}(readlink -f "${'$'}JAVACMD")")")/release"; readlink -f "${'$'}JAVACMD" "${'$'}APP_HOME"; ls -lL --time-style=+%s "${'$'}APP_HOME"/lib/*.jar; echo "${'$'}DEFAULT_JVM_OPTS ${'$'}JAVA_OPTS ${'$'}NEWM_OPTS"; } 2>/dev/null | cksum | cut -d' ' -f1)
            |    newm_aot_file="${'$'}newm_aot_dir/newm-${'$'}newm_aot_key.aot"
            |    newm_aot_log="${'$'}newm_aot_dir/newm-${'$'}newm_aot_key.log"
            |    # Java said last time that the cache was broken (in its log): it goes, made anew.
            |    if grep -qs "error" "${'$'}newm_aot_log"; then rm -f "${'$'}newm_aot_file" "${'$'}newm_aot_dir/newm-${'$'}newm_aot_key".made-*; fi
            |    rm -f "${'$'}newm_aot_log" 2>/dev/null
            |    if [ ! -s "${'$'}newm_aot_file" ]; then
            |        for newm_made in "${'$'}newm_aot_dir/newm-${'$'}newm_aot_key".made-*.aot; do
            |            [ -s "${'$'}newm_made" ] && mv -f "${'$'}newm_made" "${'$'}newm_aot_file" 2>/dev/null
            |        done
            |    fi
            |    ( : > "${'$'}newm_aot_log" ) 2>/dev/null || return
            |    if [ -s "${'$'}newm_aot_file" ]; then
            |        [ -r "${'$'}newm_aot_file" ] && newm_aot_opt="-XX:AOTCache=${'$'}newm_aot_file"
            |    else
            |        # (The caches of other builds go: one is enough.)
            |        for newm_old in "${'$'}newm_aot_dir"/newm-*; do
            |            case "${'$'}newm_old" in "${'$'}newm_aot_dir/newm-${'$'}newm_aot_key".*) ;; *) rm -f "${'$'}newm_old" 2>/dev/null ;; esac
            |        done
            |        newm_aot_opt="-XX:AOTCacheOutput=${'$'}newm_aot_dir/newm-${'$'}newm_aot_key.made-${'$'}$-${'$'}(date +%s).aot"
            |    fi
            |}
            |# Java, with the cache when there is one, in this process's place.
            |newm_java() {
            |    if [ -n "${'$'}newm_aot_opt" ]; then
            |        exec "${'$'}JAVACMD" "${'$'}newm_aot_opt" "-Xlog:aot=error:file=${'$'}newm_aot_log:uptime,level,tags:filecount=0" "${'$'}@"
            |    fi
            |    # (Written unlike the line it replaces, which is looked for in the script.)
            |    exec "${'$'}{JAVACMD}" "${'$'}@"
            |}
            |
            """.trimMargin()
        val restart = """
            |newm_aot
            |if [ -n "${'$'}NEWM_SUPERVISED" ] || [ -z "${'$'}newm_compositor" ]; then newm_java "${'$'}@"; fi
            |newm_child=
            |newm_stopped=
            |# (Sent a signal: passed on, and newm is not started again, whatever code it ends with.)
            |trap 'newm_stopped=1; if [ -n "${'$'}newm_child" ]; then kill -TERM "${'$'}newm_child" 2>/dev/null; else exit 143; fi' TERM INT HUP
            |# (A program started with "&" reads from /dev/null: Java gets this shell's input as its own.)
            |exec 3<&0
            |while :; do
            |    newm_started=${'$'}(date +%s)
            |    newm_java "${'$'}@" 0<&3 3<&- &
            |    newm_child=${'$'}!
            |    # (A signal passed on ends the wait early: waited for again until Java has ended.)
            |    while :; do
            |        wait "${'$'}newm_child"
            |        newm_code=${'$'}?
            |        kill -0 "${'$'}newm_child" 2>/dev/null || break
            |    done
            |    newm_child=
            |    if [ -n "${'$'}newm_stopped" ]; then
            |        exit "${'$'}newm_code"
            |    elif [ "${'$'}newm_code" -eq 1 ] && [ -n "${'$'}newm_aot_opt" ] && [ ${'$'}(( ${'$'}(date +%s) - newm_started )) -le 2 ]; then
            |        echo "newm: Java did not start with its AOT cache: once more without it" >&2
            |        newm_aot_off=1
            |    elif [ "${'$'}newm_code" -ne 3 ]; then
            |        exit "${'$'}newm_code"
            |    else
            |        echo "newm: restarting" >&2
            |    fi
            |    newm_aot
            |done
            """.trimMargin()
        val withJava = text.substring(0, at + 1) + find + aot + text.substring(at + 1)
        check(launch in withJava) { "the start script changed: newm's restart loop is not in it" }
        script.writeText(withJava.replace(launch, restart))
    }
}
