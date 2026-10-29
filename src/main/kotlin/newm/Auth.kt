package newm

import dev.caulis.log.Logger
import newm.bar.verbatim
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

private val log = Logger("auth")

private val confGreeterUser = configuredValue("greeter_user", "greeter")
// lock { pamService = "login" }: the PAM service the password is checked with (/etc/pam.d/<name>).
private val confPamService = configuredValue("panels.lock.pam_service", "login")
// lock { checkTimeout = 60 }: seconds a password check may take before the lock screen asks again (PAM can hang: an
// unreachable LDAP server). Longer than pam_fprintd's 30 s: with it before pam_unix PAM waits for a finger first.
private val confCheckTimeout = configuredValue("panels.lock.check_timeout", 60)
// greeterCommand = "newm": what greetd starts once the user is let in (unset: this newm's own start script).
private val confGreeterCommand = configuredValue<String?>("greeter_command", null)

/** What a password check says: whether it was right, and (if not) the words to show for it. */
internal data class AuthOutcome(val ok: Boolean, val message: String? = null)

private interface Backend {
    fun initAuth(user: String, message: String? = null) {}
    fun enterCred(cred: String) {}
    /** Starts the user's session, then [then] (on the compositor's thread). */
    fun startSession(then: () -> Unit) = then()
}

/** newm's _PAMBackend: the password checked by PAM (service "login" unless lock { pamService } says otherwise). */
private class PamBackend(private val auth: AuthBackend) : Backend {
    private var user: String? = null
    // The check running now (counted up each time: a late answer of an earlier one is not taken).
    private var attempt = 0
    private var checking = false

    override fun initAuth(user: String, message: String?) {
        this.user = user
        auth.requestCred("Password?", user, error = message)
    }

    override fun enterCred(cred: String) {
        val u = user ?: return
        // (One check at a time: a second Enter while PAM thinks is not a second guess.)
        if (checking) return log.info { "A password check runs already: this one is not taken" }
        checking = true
        val number = ++attempt
        val service = confPamService().ifBlank { "login" }
        // (PAM may wait seconds after a wrong password: not on the compositor's thread.)
        Thread({
            val outcome = try {
                Pam.authenticate(service, u, cred)
            } catch (e: Throwable) {
                // (libpam missing, or the FFI refused: said on the lock screen, never a lock screen that waits for ever.)
                log.error("PAM could not be used", e)
                AuthOutcome(false, "The password cannot be checked (PAM: ${verbatim(e.javaClass.simpleName)}). See newm's log.")
            }
            auth.post { done(number, u, outcome) }
        }, "newm-pam").apply { isDaemon = true }.start()
        // A check that never ends (a hanging PAM module) is given up: the lock screen asks again.
        val timeout = confCheckTimeout().coerceIn(1, 600)
        auth.after(timeout * 1000L) {
            if (attempt == number && checking) {
                log.warn("The password check took more than $timeout s: asked again (lock { checkTimeout })")
                done(number, u, AuthOutcome(false, "Checking the password took too long (a fingerprint reader waiting?). Again:"))
            }
        }
    }

    private fun done(number: Int, u: String, outcome: AuthOutcome) {
        if (number != attempt || !checking) return
        checking = false
        log.info { "PAM Backend: ${if (outcome.ok) "" else "not "}successful" }
        auth.authResult(outcome.ok)
        if (!outcome.ok) {
            // (A new attempt counts on: the given-up thread's answer is dropped when it comes.)
            attempt++
            initAuth(u, outcome.message)
        }
    }

    override fun startSession(then: () -> Unit) {
        log.warn("Unsupported operation")
        then()
    }
}

/**
 * newm's _GreetdBackend: newm as the greeter of greetd (its socket in GREETD_SOCK). greetd is
 * talked to on a thread of its own (after a wrong password its answer takes seconds); what it
 * answers is taken on on the compositor's thread, as [PamBackend] does.
 */
private class GreetdBackend(private val auth: AuthBackend) : Backend {
    private var user: String? = null
    // (Only the greetd thread touches the socket.)
    private var socket: SocketChannel? = null
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "newm-greetd").apply { isDaemon = true } }

    private fun openSocket() {
        val path = System.getenv("GREETD_SOCK") ?: return log.error("Not in a greetd session")
        socket = SocketChannel.open(UnixDomainSocketAddress.of(path))
        log.info { "Connected to greetd" }
    }

    private fun send(message: Map<String, Any?>): Map<String, Any?> {
        if (socket == null) openSocket()
        val s = socket ?: return emptyMap()
        val bytes = Json.write(message).toByteArray()
        val out = ByteBuffer.allocate(4 + bytes.size).order(ByteOrder.nativeOrder()).putInt(bytes.size).put(bytes).flip()
        while (out.hasRemaining()) s.write(out)
        val lengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder())
        while (lengthBuffer.hasRemaining()) if (s.read(lengthBuffer) < 0) error("greetd closed the connection")
        // (greetd's answers are small: a length past 1 MB is not greetd's.)
        val length = lengthBuffer.flip().getInt()
        if (length !in 0..1_048_576) error("greetd sent a message of $length bytes")
        val reply = ByteBuffer.allocate(length)
        while (reply.hasRemaining()) if (s.read(reply) < 0) error("greetd closed the connection")
        return Json.parseObject(String(reply.array()))
    }

    // [work] on the greetd thread; a broken connection is logged and opened anew next time.
    private fun talk(what: String, work: () -> Unit) = worker.execute {
        try {
            work()
        } catch (e: Exception) {
            log.error("greetd: $what failed ($e)")
            runCatching { socket?.close() }
            socket = null
        }
    }

    override fun initAuth(user: String, message: String?) {
        this.user = user
        talk("starting a session for $user") {
            send(mapOf("type" to "cancel_session"))
            val result = send(mapOf("type" to "create_session", "username" to user))
            auth.post {
                when (result["type"]) {
                    "auth_message" -> auth.requestCred(result["auth_message"] as? String, user)
                    // (No password needed.)
                    "success" -> auth.authResult(true)
                    else -> {
                        // greetd refused the user: asked for one again.
                        log.warn("greetd refused $user: ${result["description"] ?: result["error_type"] ?: result["type"]}")
                        auth.initSession()
                    }
                }
            }
        }
    }

    override fun enterCred(cred: String) {
        val u = user ?: return
        talk("checking the password") {
            val result = send(mapOf("type" to "post_auth_message_response", "response" to cred))
            auth.post {
                when (result["type"]) {
                    "auth_message" -> auth.requestCred(result["auth_message"] as? String, u)
                    "success" -> auth.authResult(true)
                    else -> {
                        // A wrong password (greetd's error ends that conversation): a new one, so the password can be typed again.
                        auth.authResult(false)
                        if (user == u) initAuth(u)
                    }
                }
            }
        }
    }

    override fun startSession(then: () -> Unit) {
        // greeter_command, else newm's own start script (the legacy start-newm is not installed any more).
        val command = confGreeterCommand()?.takeIf { it.isNotBlank() }?.split(' ')?.filter { it.isNotEmpty() }
            ?: listOf(launcherPath()?.toString() ?: "newm")
        log.info { "Starting the session: ${command.joinToString(" ")}" }
        talk("starting the session") {
            try {
                // greetd starts it once the greeter has ended; an error (no such program) is told in the
                // log, and greetd then starts the greeter again.
                val result = send(mapOf("type" to "start_session", "cmd" to command))
                if (result["type"] == "success") log.info { "greetd starts the session once the greeter ends" }
                else log.error("greetd did not take the session ${command.joinToString(" ")}: ${result["description"] ?: result["error_type"] ?: result["type"]} (greeter_command)")
            } finally {
                auth.post(then)
            }
        }
    }
}

/** The user of a uid as the system knows them (NSS: /etc/passwd, systemd-homed, LDAP, SSSD — through libc). */
internal object Users {
    /** The login name of [uid] (getpwuid_r), or null if the system does not know it. */
    fun nameOf(uid: Int): String? = try {
        val linker = Linker.nativeLinker()
        val getpwuid = linker.downcallHandle(
            linker.defaultLookup().find("getpwuid_r").get(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
        )
        Arena.ofConfined().use { arena ->
            // struct passwd (pw_name first), a buffer for its strings, and where the result is told.
            val pwd = arena.allocate(64)
            val buffer = arena.allocate(16_384)
            val result = arena.allocate(ValueLayout.ADDRESS)
            val rc = getpwuid.invokeExact(uid, pwd, buffer, 16_384L, result) as Int
            if (rc != 0 || result.get(ValueLayout.ADDRESS, 0).address() == 0L) null
            else pwd.get(ValueLayout.ADDRESS, 0).reinterpret(4096).getString(0)
        }
    } catch (e: Throwable) {
        log.warn("getpwuid_r failed: $e")
        null
    }

    /** True when newm runs as greetd's greeter (see [greeterMode]). */
    fun isGreeter(): Boolean = greeterMode(current(currentUid()), confGreeterUser(), System.getenv("GREETD_SOCK"))

    /**
     * newm is greetd's greeter when greetd gave it its socket (GREETD_SOCK: greetd sets it for its
     * greeter only, never for the sessions it starts), or when its [user] is [greeterUser] (the
     * greeter_user setting; distributions name that user differently: Void's is _greeter).
     */
    internal fun greeterMode(user: String?, greeterUser: String, greetdSock: String?): Boolean =
        !greetdSock.isNullOrBlank() || (user != null && user == greeterUser)

    /** This process's user id. */
    fun currentUid(): Int = Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int

    /** The login name of this process's user: libc, else what Java and the session say. */
    fun current(uid: Int): String? = nameOf(uid)
        ?: System.getProperty("user.name")?.takeIf { it.isNotBlank() && it != "?" }
        ?: System.getenv("USER")?.takeIf { it.isNotBlank() }

    /**
     * Who can log in (the greeter's list): `getent passwd` — every user NSS lists, not only
     * /etc/passwd's — less those with no login shell. (name, login shell) It may take long (an
     * LDAP or SSSD server far away or not there): ended after [timeoutSeconds], /etc/passwd then.
     * Never on the compositor's thread.
     */
    fun loginUsers(timeoutSeconds: Long = 5): List<Pair<String, String>> {
        val lines = try {
            val p = ProcessBuilder("getent", "passwd").redirectError(ProcessBuilder.Redirect.DISCARD).start()
            // (Read on a thread of its own: a reading that never ends is cut by ending getent.)
            val read = java.util.concurrent.CompletableFuture.supplyAsync { p.inputStream.bufferedReader().readLines() }
            if (!p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                p.descendants().forEach { it.destroyForcibly() }
                p.destroyForcibly()
                log.warn("getent passwd took more than $timeoutSeconds s: the users of /etc/passwd only")
                null
            } else read.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS).takeIf { p.exitValue() == 0 }
        } catch (e: Exception) {
            log.error("getent passwd failed: the users of /etc/passwd only ($e)")
            null
        } ?: runCatching { Files.readAllLines(Path.of("/etc/passwd")) }.getOrDefault(emptyList())
        return passwdUsers(lines)
    }

    /** (name, login shell) of passwd lines. */
    internal fun passwdUsers(lines: List<String>): List<Pair<String, String>> = lines.mapNotNull { line ->
        val u = line.split(":")
        if (u.size < 7) null else u[0] to u[6]
    }
}

/**
 * newm's auth_backend.py: unlocking. On lock the lock screen (newm's own, or a lock panel
 * program over D-Bus) is asked for the password of the user; PAM checks it and newm unlocks.
 * As greetd's greeter, newm asks for the user first and starts their session.
 */
class AuthBackend(private val layout: Layout) {
    private var state = "initial"
    private var waitingCred: Map<String, Any?> = emptyMap()
    private val uid: Int = (Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int)
    // The user of this session (NSS knows them: homed, LDAP, SSSD as well as /etc/passwd).
    private val currentUser: String? = Users.current(uid)
    private val greeterUser = confGreeterUser()
    private val backend: Backend

    init {
        if (currentUser == null) log.error("Could not find the current user (uid $uid): the lock screen says so")
        backend = if (isGreeter()) GreetdBackend(this) else PamBackend(this)
    }

    /** Runs [action] on the compositor's thread (from PAM's). */
    internal fun post(action: () -> Unit) = layout.caulis.post { action() }

    /** Runs [action] on the compositor's thread after [millis]. */
    internal fun after(millis: Long, action: () -> Unit) {
        layout.caulis.after(millis) { action() }
    }

    fun isGreeter(): Boolean = Users.greeterMode(currentUser, greeterUser, System.getenv("GREETD_SOCK"))

    fun initSession() {
        state = "wait_user"
        log.trace { "Requesting user" }
        // (Asked of NSS on a thread of its own: it may take seconds, the greeter keeps drawing meanwhile.)
        Thread({
            val users = Users.loginUsers().filter { (name, shell) -> name != greeterUser && name != currentUser && "nologin" !in shell && !shell.endsWith("/false") }.map { it.first }
            post { if (state == "wait_user") publish(AuthRequest(mapOf("kind" to "auth_request_user", "users" to users), ::onUser)) }
        }, "newm-users").apply { isDaemon = true }.start()
    }

    fun lock() {
        log.trace { "Locking for user $currentUser" }
        val user = currentUser
        if (user == null) {
            // (Said on the lock screen: nothing can be checked without the user's name.)
            layout.panelsUi.authProblem("newm cannot tell who you are (uid ${verbatim("$uid")}), so it cannot check a password. See newm's log.")
            return
        }
        if (!isGreeter()) backend.initAuth(user)
    }

    // newm's own lock screen asks it, and so can a lock panel program (over D-Bus).
    private fun publish(req: AuthRequest) {
        layout.panelsUi.authRequest(req)
        layout.dbusEndpoint.publishAuthRequest(req)
    }

    // --- the panels' side (what they send was checked by AuthRequest.accepts)

    private fun onUser(msg: Map<String, Any?>) {
        val user = msg["user"] as? String ?: return
        log.trace { "Received user $user" }
        backend.initAuth(user)
    }

    private fun onCred(msg: Map<String, Any?>) {
        val cred = msg["cred"] as? String ?: return
        log.trace { "Received credentials" } // (never the password itself)
        backend.enterCred(cred)
    }

    // --- the backends' side

    // ([error]: why the last try failed, if PAM said more than "wrong": shown above the field.)
    internal fun requestCred(message: String? = null, forUser: String? = null, error: String? = null) {
        if (message != null && forUser != null) {
            waitingCred = mapOf("kind" to "auth_request_cred", "user" to forUser, "message" to message)
        }
        if (error != null) waitingCred = waitingCred + ("error" to error) else waitingCred = waitingCred - "error"
        log.trace { "Requesting credentials" }
        publish(AuthRequest(waitingCred, ::onCred))
        state = "wait_cred"
    }

    internal fun authResult(successful: Boolean) {
        if (!successful) {
            log.info { "Verification failed" } // (the backend asks again by itself)
            return
        }
        state = "initial"
        if (isGreeter()) {
            log.info { "Starting session after successful verification" }
            backend.startSession { layout.terminate() }
        } else {
            log.info { "Unlocking after successful verification" }
            layout.trustedUnlock()
        }
    }
}

/** Linux-PAM through the JVM's foreign function interface (python-pam's authenticate). */
internal object Pam {
    private const val PAM_SUCCESS = 0
    private const val PAM_BUF_ERR = 5
    private const val PAM_AUTH_ERR = 7
    private const val PAM_PROMPT_ECHO_OFF = 1
    private const val PAM_PROMPT_ECHO_ON = 2
    private const val PAM_ERROR_MSG = 3
    private const val PAM_TEXT_INFO = 4
    private const val PAM_REINITIALIZE_CRED = 0x0008

    private val linker = Linker.nativeLinker()
    private val pam = SymbolLookup.libraryLookup("libpam.so.0", Arena.global())
    private val libc = linker.defaultLookup()
    private val ADDRESS = ValueLayout.ADDRESS
    private val INT = ValueLayout.JAVA_INT

    private val pamStart = linker.downcallHandle(pam.find("pam_start").get(), FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
    private val pamAuthenticate = linker.downcallHandle(pam.find("pam_authenticate").get(), FunctionDescriptor.of(INT, ADDRESS, INT))
    private val pamSetcred = linker.downcallHandle(pam.find("pam_setcred").get(), FunctionDescriptor.of(INT, ADDRESS, INT))
    private val pamEnd = linker.downcallHandle(pam.find("pam_end").get(), FunctionDescriptor.of(INT, ADDRESS, INT))
    private val pamStrerror = linker.downcallHandle(pam.find("pam_strerror").get(), FunctionDescriptor.of(ADDRESS, ADDRESS, INT))
    private val calloc = linker.downcallHandle(libc.find("calloc").get(), FunctionDescriptor.of(ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG))
    private val strdup = linker.downcallHandle(libc.find("strdup").get(), FunctionDescriptor.of(ADDRESS, ADDRESS))

    // The password of the authentication running on this thread (the conversation reads it),
    // and what PAM said meanwhile ("The account is locked": shown on the lock screen).
    private val password = ThreadLocal<String>()
    private val said = ThreadLocal<MutableList<String>>()

    private val conversation: MemorySegment = linker.upcallStub(
        MethodHandles.lookup().findStatic(
            Pam::class.java, "converse",
            MethodType.methodType(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
        ),
        FunctionDescriptor.of(INT, INT, ADDRESS, ADDRESS, ADDRESS),
        Arena.global(),
    )

    // PAM asks: each prompt for a secret or a text gets the password (PAM frees what it is given);
    // its messages and notes are kept to be shown (a few, not long).
    @JvmStatic
    fun converse(count: Int, messages: MemorySegment, responsesOut: MemorySegment, @Suppress("UNUSED_PARAMETER") data: MemorySegment): Int {
        if (count <= 0 || count > 32) return PAM_BUF_ERR
        val responses = (calloc.invokeExact(count.toLong(), 16L) as MemorySegment).reinterpret(16L * count)
        if (responses.address() == 0L) return PAM_BUF_ERR
        val list = messages.reinterpret(8L * count)
        Arena.ofConfined().use { arena ->
            for (n in 0 until count) {
                val message = list.getAtIndex(ADDRESS, n.toLong()).reinterpret(16)
                val style = message.get(INT, 0)
                if (style == PAM_PROMPT_ECHO_OFF || style == PAM_PROMPT_ECHO_ON) {
                    val copy = strdup.invokeExact(arena.allocateFrom(password.get() ?: "")) as MemorySegment
                    responses.set(ADDRESS, 16L * n, copy)
                } else if (style == PAM_ERROR_MSG || style == PAM_TEXT_INFO) {
                    val text = message.get(ADDRESS, 8).takeIf { it.address() != 0L }?.reinterpret(1024)?.let { runCatching { it.getString(0) }.getOrNull() }
                    if (!text.isNullOrBlank()) said.get()?.takeIf { it.size < 4 }?.add(text.take(200))
                }
            }
        }
        responsesOut.reinterpret(8).set(ADDRESS, 0, responses)
        return PAM_SUCCESS
    }

    private fun describe(handle: MemorySegment, rc: Int): String =
        runCatching { (pamStrerror.invokeExact(handle, rc) as MemorySegment).reinterpret(256).getString(0) }.getOrDefault("PAM error $rc")

    /**
     * Checks [password] for [user] with PAM's [service]: right, or not and why (a wrong
     * password: no words, the lock screen says its own; anything else: PAM's words).
     */
    fun authenticate(service: String, user: String, password: String): AuthOutcome = try {
        this.password.set(password)
        val messages = ArrayList<String>()
        said.set(messages)
        Arena.ofConfined().use { arena ->
            val conv = arena.allocate(16)
            conv.set(ADDRESS, 0, conversation)
            conv.set(ADDRESS, 8, MemorySegment.NULL)
            val handleOut = arena.allocate(ADDRESS)
            var rc = pamStart.invokeExact(arena.allocateFrom(service), arena.allocateFrom(user), conv, handleOut) as Int
            if (rc != PAM_SUCCESS) {
                log.error("pam_start($service) failed: $rc")
                return AuthOutcome(false, "PAM cannot start (service ${verbatim(service)}, error ${verbatim("$rc")}). See newm's log.")
            }
            val handle = handleOut.get(ADDRESS, 0)
            rc = pamAuthenticate.invokeExact(handle, 0) as Int
            if (rc == PAM_SUCCESS) pamSetcred.invokeExact(handle, PAM_REINITIALIZE_CRED) as Int
            val why = if (rc == PAM_SUCCESS || rc == PAM_AUTH_ERR) null else describe(handle, rc)
            pamEnd.invokeExact(handle, rc) as Int
            if (why != null) log.warn("PAM: $why ($rc)")
            // (PAM's own words: shown as they are, never taken for newm's to translate.)
            val shown = (messages + listOfNotNull(why)).joinToString("\n") { verbatim(it) }.ifEmpty { null }
            AuthOutcome(rc == PAM_SUCCESS, shown)
        }
    } catch (e: Throwable) {
        log.error("PAM failed", e)
        AuthOutcome(false, "The password cannot be checked (PAM failed). See newm's log.")
    } finally {
        this.password.remove()
        said.remove()
    }
}
