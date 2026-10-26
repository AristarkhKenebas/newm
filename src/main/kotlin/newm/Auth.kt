package newm

import dev.caulis.log.Logger
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

private val log = Logger("auth")

private val confGreeterUser = configuredValue("greeter_user", "greeter")

private interface Backend {
    fun initAuth(user: String) {}
    fun enterCred(cred: String) {}
    fun startSession() {}
}

/** newm's _PAMBackend: the password checked by PAM (service "login", as python-pam's default). */
private class PamBackend(private val auth: AuthBackend) : Backend {
    private var user: String? = null

    override fun initAuth(user: String) {
        this.user = user
        auth.requestCred("Password?", user)
    }

    override fun enterCred(cred: String) {
        val u = user ?: return
        // (PAM may wait seconds after a wrong password: not on the compositor's thread.)
        Thread({
            val ok = Pam.authenticate(u, cred)
            auth.post {
                log.info { "PAM Backend: ${if (ok) "" else "not "}successful" }
                auth.authResult(ok)
                if (!ok) initAuth(u)
            }
        }, "newm-pam").apply { isDaemon = true }.start()
    }

    override fun startSession() = log.warn("Unsupported operation")
}

/** newm's _GreetdBackend: newm as the greeter of greetd (its socket in GREETD_SOCK). */
private class GreetdBackend(private val auth: AuthBackend) : Backend {
    private var user: String? = null
    private var socket: SocketChannel? = null

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
        val reply = ByteBuffer.allocate(lengthBuffer.flip().getInt())
        while (reply.hasRemaining()) if (s.read(reply) < 0) error("greetd closed the connection")
        return Json.parseObject(String(reply.array()))
    }

    override fun initAuth(user: String) {
        this.user = user
        send(mapOf("type" to "cancel_session"))
        val result = send(mapOf("type" to "create_session", "username" to user))
        if (result["type"] == "auth_message") auth.requestCred(result["auth_message"] as String?, user)
    }

    override fun enterCred(cred: String) {
        val result = send(mapOf("type" to "post_auth_message_response", "response" to cred))
        if (result["type"] == "auth_message") auth.requestCred(result["auth_message"] as String?, user) else auth.authResult(result["type"] == "success")
    }

    override fun startSession() {
        send(mapOf("type" to "start_session", "cmd" to listOf("start-newm")))
    }
}

/**
 * newm's auth_backend.py: unlocking. On lock the lock screen panel is asked (over D-Bus) for
 * the password of the user; PAM checks it and newm unlocks. As greetd's greeter, newm asks
 * for the user first and starts their session.
 */
class AuthBackend(private val layout: Layout) {
    // (name, uid, login shell, is the greeter)
    private data class User(val name: String, val uid: Int, val shell: String, val greeter: Boolean)

    private val users = ArrayList<User>()
    private var state = "initial"
    private var waitingCred: Map<String, Any?> = emptyMap()
    private val uid: Int = (Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int)
    private val backend: Backend

    init {
        val greeterUser = confGreeterUser()
        try {
            for (line in Files.readAllLines(Path.of("/etc/passwd"))) {
                val u = line.split(":")
                if (u.size < 7) continue
                if ("nologin" !in u[6] || u[0] == greeterUser) users += User(u[0], u[2].toInt(), u[6], u[0] == greeterUser)
            }
        } catch (e: Exception) {
            log.error("/etc/passwd cannot be read", e)
        }
        if (users.none { it.greeter }) log.warn("Could not find greeter: $greeterUser")
        if (users.none { it.uid == uid }) log.error("Fatal! Could not find current user")
        backend = if (isGreeter()) GreetdBackend(this) else PamBackend(this)
    }

    /** Runs [action] on the compositor's thread (from PAM's). */
    internal fun post(action: () -> Unit) = layout.caulis.post { action() }

    fun isGreeter(): Boolean {
        val user = users.firstOrNull { it.uid == uid } ?: return false.also { log.warn("Could not find current user: $uid") }
        return user.greeter
    }

    fun initSession() {
        state = "wait_user"
        log.trace { "Requesting user" }
        publish(AuthRequest(mapOf("kind" to "auth_request_user", "users" to users.filter { !it.greeter }.map { it.name }), ::onUser))
    }

    fun lock() {
        val current = users.firstOrNull { it.uid == uid }
        log.trace { "Locking for user ${current?.name}" }
        if (current != null && !current.greeter) backend.initAuth(current.name)
    }

    // newm's own lock screen asks it, and so can a panel program (over D-Bus).
    private fun publish(req: AuthRequest) {
        layout.panelsUi.authRequest(req)
        layout.dbusEndpoint.publishAuthRequest(req)
    }

    // --- the panels' side

    private fun onUser(msg: Map<String, Any?>) {
        log.trace { "Received user ${msg["user"]}" }
        backend.initAuth(msg["user"] as String)
    }

    private fun onCred(msg: Map<String, Any?>) {
        log.trace { "Received credentials" } // (never the password itself)
        backend.enterCred(msg["cred"] as String)
    }

    // --- the backends' side

    internal fun requestCred(message: String? = null, forUser: String? = null) {
        if (message != null && forUser != null) {
            waitingCred = mapOf("kind" to "auth_request_cred", "user" to forUser, "message" to message)
        }
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
            backend.startSession()
            layout.terminate()
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
    private const val PAM_PROMPT_ECHO_OFF = 1
    private const val PAM_PROMPT_ECHO_ON = 2
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
    private val calloc = linker.downcallHandle(libc.find("calloc").get(), FunctionDescriptor.of(ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG))
    private val strdup = linker.downcallHandle(libc.find("strdup").get(), FunctionDescriptor.of(ADDRESS, ADDRESS))

    // The password of the authentication running on this thread (the conversation reads it).
    private val password = ThreadLocal<String>()

    private val conversation: MemorySegment = linker.upcallStub(
        MethodHandles.lookup().findStatic(
            Pam::class.java, "converse",
            MethodType.methodType(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
        ),
        FunctionDescriptor.of(INT, INT, ADDRESS, ADDRESS, ADDRESS),
        Arena.global(),
    )

    // PAM asks: each prompt for a secret or a text gets the password (PAM frees what it is given).
    @JvmStatic
    fun converse(count: Int, messages: MemorySegment, responsesOut: MemorySegment, @Suppress("UNUSED_PARAMETER") data: MemorySegment): Int {
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
                }
            }
        }
        responsesOut.reinterpret(8).set(ADDRESS, 0, responses)
        return PAM_SUCCESS
    }

    /** True if [password] is [user]'s (PAM service "login"). */
    fun authenticate(user: String, password: String): Boolean = try {
        this.password.set(password)
        Arena.ofConfined().use { arena ->
            val conv = arena.allocate(16)
            conv.set(ADDRESS, 0, conversation)
            conv.set(ADDRESS, 8, MemorySegment.NULL)
            val handleOut = arena.allocate(ADDRESS)
            var rc = pamStart.invokeExact(arena.allocateFrom("login"), arena.allocateFrom(user), conv, handleOut) as Int
            if (rc != PAM_SUCCESS) return false
            val handle = handleOut.get(ADDRESS, 0)
            rc = pamAuthenticate.invokeExact(handle, 0) as Int
            if (rc == PAM_SUCCESS) pamSetcred.invokeExact(handle, PAM_REINITIALIZE_CRED) as Int
            pamEnd.invokeExact(handle, rc) as Int
            rc == PAM_SUCCESS
        }
    } catch (e: Throwable) {
        log.error("PAM failed", e)
        false
    } finally {
        this.password.remove()
    }
}
