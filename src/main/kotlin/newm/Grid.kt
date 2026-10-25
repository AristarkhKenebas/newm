package newm

import dev.caulis.log.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private val confTimeScale = configuredValue("grid.time_scale", .3)
private val confThrowPs = configuredValue("grid.throw_ps", listOf(1.0, 5.0, 15.0))
private val confMinDist = configuredValue("grid.min_dist", .05)
private val confGridDebug = configuredValue("grid.debug", false)

/**
 * newm's grid.py: a value moved by a gesture, snapping to whole numbers between [x0] and [x1]
 * (soft beyond them by [dOvr]), remembering its speed so a throw goes on to the next ones.
 */
class Grid(name: String, x0: Int, x1: Int, xi: Double, private val dOvr: Double = 0.0, private val mSnap: Double = 1.0) {
    private val log = Logger("grid")
    val name = "%s-%d".format(name, (System.currentTimeMillis() / 1000) % 1000)
    private val x0 = x0
    private val x1 = x1
    private val xi = xi.toInt()

    // Initially allow to handle xi out of bounds
    private var allowOutOfBounds = true

    private var lastT: Double? = null
    private var lastX: Double? = null
    private var lastP: Double? = null
    private var lastXOutput: Double? = null
    private var lastPOutput: Double? = null

    private fun bounds(x: Double): Pair<Int, Int> {
        if (x0 <= x && x <= x1) allowOutOfBounds = false
        var b0 = x0
        var b1 = x1
        if (allowOutOfBounds && x < b0) b0 = floor(x)
        if (allowOutOfBounds && x > b1) b1 = ceil(x)
        return b0 to b1
    }

    fun at(x: Double, silent: Boolean = false): Double {
        val (b0, b1) = bounds(x)
        val t = now()
        if (!silent) {
            val lx = lastX
            val lt = lastT
            if (lx != null && lt != null) lastP = (x - lx) / (t - lt)
            lastX = x
        }

        val xp: Double = when {
            x < b0 -> if (mSnap == 1.0) {
                if (dOvr > 0) {
                    var y = x - b0
                    y = dOvr * (1 / (1 - y / dOvr) - 1)
                    b0 + y
                } else {
                    b0.toDouble()
                }
            } else {
                val y = max(0.0, x - b0 + 1)
                if (y == 0.0) b0 - dOvr else b0 - dOvr + dOvr / (1.0 + ((1.0 - y) / y).pow(mSnap))
            }
            x < b1 -> {
                val y = x - floor(x)
                if (y == 0.0) floor(x).toDouble() else floor(x) + 1 / (1.0 + ((1.0 - y) / y).pow(mSnap))
            }
            else -> if (mSnap == 1.0) {
                if (dOvr > 0) {
                    var y = x - b1
                    y = dOvr * (1 / (1 + y / dOvr) - 1)
                    b1 - y
                } else {
                    b1.toDouble()
                }
            } else {
                val y = min(1.0, max(0.0, x - b1))
                if (y == 0.0) b1.toDouble() else b1 + dOvr / (1.0 + ((1.0 - y) / y).pow(mSnap))
            }
        }

        if (!silent) {
            val lxo = lastXOutput
            val lt = lastT
            if (lxo != null && lt != null) lastPOutput = (xp - lxo) / (t - lt)
            lastXOutput = xp
            lastT = t
        }
        if (!silent && confGridDebug()) log.info { "GRID[$name]: ${now()}, $x, $xp, ${lastP ?: 0.0}, ${lastPOutput ?: 0.0}" }
        return xp
    }

    /** Where it ends and how long it takes to get there (seconds). */
    fun final(throwDistMax: Double? = null): Pair<Int, Double> {
        val maxDist = throwDistMax ?: (1.0 - confMinDist())
        val lastOutput = lastXOutput ?: return pyRound(at(xi.toDouble())) to 0.0

        val (b0, b1) = bounds(lastOutput)

        // Find final x
        val xBase = lastOutput
        val p = lastP ?: 0.0

        val xFinals = arrayListOf(pyRound(xBase))
        if (p > 0) {
            if (xFinals[0] > xBase) xFinals += pyRound(xBase)
            var x = xFinals[0] + 1
            while (x < xBase + maxDist) {
                xFinals += x
                x += 1
            }
        } else if (p < 0) {
            if (xFinals[0] < xBase) xFinals += pyRound(xBase)
            var x = xFinals[0] - 1
            while (x > xBase - maxDist) {
                xFinals += x
                x -= 1
            }
        }

        val throwPs = confThrowPs()
        var iFinal = 0
        while (iFinal < throwPs.size) {
            if (abs(p) < throwPs[iFinal]) break
            iFinal += 1
        }
        var xf = xFinals[min(iFinal, xFinals.size - 1)]
        xf = min(b1, max(b0, xf))
        val dx = abs(lastOutput - xf)
        var dt = dx * confTimeScale()

        // Speed up animation if a high momentum is involved
        val compareT = abs(xBase - xf) / max(abs(p), 0.01)
        if (compareT < dt) dt = compareT

        return xf to dt
    }
}
