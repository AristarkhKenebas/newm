package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

private val log = Logger("media")

private const val MPRIS_PREFIX = "org.mpris.MediaPlayer2."
private const val MPRIS_PATH = "/org/mpris/MediaPlayer2"
private const val PLAYER = "org.mpris.MediaPlayer2.Player"

/** What a media player plays now (MPRIS): its title, artist, whether it plays, where it is (microseconds; [atNanos]: when that was read). */
data class MediaNow(
    /** The player's bus name ("org.mpris.MediaPlayer2.spotify"). */
    val player: String,
    /** The player's own name ("Spotify", "Firefox"; else from its bus name). */
    val identity: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canPlayPause: Boolean,
    val lengthMicros: Long,
    val positionMicros: Long,
    val atNanos: Long,
) {
    /** Where it is now (0..1 of its length; 0 when the length is not known). */
    fun progress(nowNanos: Long = System.nanoTime()): Float {
        if (lengthMicros <= 0) return 0f
        val moved = if (playing) (nowNanos - atNanos) / 1000 else 0
        return ((positionMicros + moved).toFloat() / lengthMicros).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * What [player]'s Player properties (unwrapped: see dictOf) say, or null when it plays
         * nothing (no title). [identity]: its name, else the bus name's last part.
         */
        fun of(player: String, props: Map<String, Any?>, identity: String?, nowNanos: Long = System.nanoTime()): MediaNow? {
            val meta = dictOf(props["Metadata"])
            val title = meta["xesam:title"]?.toString().orEmpty()
            val status = props["PlaybackStatus"]?.toString()
            if (title.isBlank() && status != "Playing") return null
            val artist = (meta["xesam:artist"] as? List<*>)?.joinToString(", ") { it.toString() } ?: meta["xesam:artist"]?.toString().orEmpty()
            return MediaNow(
                player = player,
                identity = identity?.takeIf { it.isNotBlank() } ?: player.removePrefix(MPRIS_PREFIX).substringBefore('.').replaceFirstChar { it.uppercase() },
                title = title,
                artist = artist,
                playing = status == "Playing",
                canPrevious = props["CanGoPrevious"] == true,
                canNext = props["CanGoNext"] == true,
                canPlayPause = props["CanPause"] == true || props["CanPlay"] == true,
                lengthMicros = (meta["mpris:length"] as? Number)?.toLong() ?: 0L,
                positionMicros = (props["Position"] as? Number)?.toLong() ?: 0L,
                atNanos = nowNanos,
            )
        }

        /** The one to show of [players]: one that plays (the last to start), else the last one heard of. */
        fun choose(players: List<MediaNow>, lastPlaying: String?): MediaNow? =
            players.firstOrNull { it.playing && it.player == lastPlaying } ?: players.lastOrNull { it.playing }
                ?: players.firstOrNull { it.player == lastPlaying } ?: players.lastOrNull()
    }
}

/**
 * The media players on the session bus (MPRIS: Spotify, mpv with its script, Firefox, ...):
 * what one plays, and its buttons. Started only when a bar shows "media" or the quick settings
 * show the media card; everything runs on the compositor's thread.
 */
class Media(private val caulis: Caulis) {

    /** What to show now (null: nothing plays). */
    var now by mutableStateOf<MediaNow?>(null)
        private set

    private var bus: DBusScope? = null
    // By bus name: what each player plays, and its unique name (to know whose signal it is).
    private val players = LinkedHashMap<String, MediaNow>()
    private val uniqueNames = HashMap<String, String>()
    private val identities = HashMap<String, String>()
    private var lastPlaying: String? = null

    /** Starts listening (once). */
    fun start() {
        if (bus != null) return
        val bus = caulis.sessionBus ?: return log.warn("No session bus: no media player in the bar")
        this.bus = bus
        log.info { "Following the media players (MPRIS)" }
        bus.onSignal("org.freedesktop.DBus", "NameOwnerChanged", "/org/freedesktop/DBus", sender = "org.freedesktop.DBus") { m ->
            val name = m.body.getOrNull(0) as? String ?: return@onSignal
            if (!name.startsWith(MPRIS_PREFIX)) return@onSignal
            val owner = (m.body.getOrNull(2) as? String).orEmpty()
            if (owner.isEmpty()) gone(name) else {
                uniqueNames[name] = owner
                read(name)
            }
        }
        bus.onSignal(PROPERTIES_IFACE, "PropertiesChanged", MPRIS_PATH) { m ->
            val name = uniqueNames.entries.firstOrNull { it.value == m.sender }?.key ?: return@onSignal
            read(name)
        }
        bus.onSignal(PLAYER, "Seeked", MPRIS_PATH) { m ->
            uniqueNames.entries.firstOrNull { it.value == m.sender }?.key?.let(::read)
        }
        bus.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "ListNames") { reply ->
            if (reply.isError) return@call
            for (name in (reply.body.firstOrNull() as? List<*>).orEmpty().mapNotNull { it as? String }.filter { it.startsWith(MPRIS_PREFIX) }) {
                bus.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "GetNameOwner", "s", listOf(name)) { r ->
                    (r.body.firstOrNull() as? String)?.let { uniqueNames[name] = it }
                    read(name)
                }
            }
        }
    }

    // Reads what [name] plays now.
    private fun read(name: String) {
        val bus = bus ?: return
        if (name !in identities) bus.getAll(name, MPRIS_PATH, "org.mpris.MediaPlayer2") { props ->
            identities[name] = props?.get("Identity")?.toString().orEmpty()
        }
        bus.getAll(name, MPRIS_PATH, PLAYER) { props ->
            if (props == null) return@getAll gone(name)
            val playing = MediaNow.of(name, props, identities[name])
            if (playing == null) players.remove(name) else {
                // (Last heard of last.)
                players.remove(name)
                players[name] = playing
                if (playing.playing) lastPlaying = name
            }
            show()
        }
    }

    private fun gone(name: String) {
        players.remove(name)
        uniqueNames.remove(name)
        identities.remove(name)
        show()
    }

    private fun show() {
        val chosen = MediaNow.choose(players.values.toList(), lastPlaying)
        if (chosen?.player != now?.player || chosen?.playing != now?.playing) log.trace { "Media: ${chosen?.identity ?: "nothing"}${if (chosen?.playing == true) " plays" else ""}" }
        now = chosen
    }

    /** Play or pause, the next one, the one before ([what]: "PlayPause", "Next", "Previous") of the player shown. */
    fun press(what: String) {
        val player = now?.player ?: return
        log.info { "Media: $what" }
        bus?.call(player, ObjectPath(MPRIS_PATH), PLAYER, what) { reply -> errorOf(reply)?.let { log.trace { "Media $what: $it" } } }
    }
}
