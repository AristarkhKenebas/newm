package newm

import dev.caulis.core.Monitor

/** newm's workspace.py: one stretch of the desktop (one or more monitors that overlap). */
class Workspace(
    output: Monitor?,
    var posX: Int,
    var posY: Int,
    var width: Int,
    var height: Int,
    var preventAnim: Boolean = false,
) {
    var handle = -1
    val outputs: MutableList<Monitor?> = mutableListOf(output)

    // Hint at view.handle to focus when switching to this workspace (not guaranteed to exist anymore)
    var focusViewHint: Int? = null

    fun swallow(other: Workspace): Boolean {
        if (posX + width <= other.posX) return false
        if (posY + height <= other.posY) return false
        if (posX >= other.posX + other.width) return false
        if (posY >= other.posY + other.height) return false

        val x = minOf(posX, other.posX)
        val y = minOf(posY, other.posY)
        val w = maxOf(posX + width, other.posX + other.width) - x
        val h = maxOf(posY + height, other.posY + other.height) - y
        posX = x
        posY = y
        width = w
        height = h
        outputs += other.outputs
        preventAnim = preventAnim or other.preventAnim
        return true
    }

    fun score(other: Workspace): Double {
        var x = posX.toDouble()
        var y = posY.toDouble()
        var w = width.toDouble()
        var h = height.toDouble()
        if (other.posX > x) {
            w -= (other.posX - x)
            x += (other.posX - x)
        }
        if (other.posY > y) {
            h -= (other.posY - y)
            y += (other.posY - y)
        }
        if (x + w > other.posX + other.width) w -= (x + w - other.posX - other.width)
        if (y + h > other.posY + other.height) h -= (y + h - other.posY - other.height)

        if (w <= 0 || h <= 0) return 0.0
        return w * h / (width * height)
    }

    override fun toString() = "Workspace[%d] at %d, %d --> %d, %d (Outputs: %s)".format(handle, posX, posY, width, height, outputs.map { it?.name })
}
