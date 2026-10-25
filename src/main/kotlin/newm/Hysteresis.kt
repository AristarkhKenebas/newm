package newm

import kotlin.math.abs

/** newm's hysteresis.py: a rounding that keeps its value until [amount] past the middle. */
class Hysteresis(private val amount: Double, initialValue: Double) {
    private var at = pyRound(initialValue)

    operator fun invoke(value: Double): Int {
        val i = floor(value)
        val j = ceil(value)
        if (at != i && at != j) at = pyRound(value)

        val di = abs(value - i)
        val dj = abs(value - j)
        if (di + amount < dj && at == j) at = i
        if (dj + amount < di && at == i) at = j

        return at
    }
}
