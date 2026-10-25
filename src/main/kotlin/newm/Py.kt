package newm

// What newm's Python code relies on, the same on the JVM.

/** Python 3's round(): halves go to the even neighbour (round(0.5) == 0, round(1.5) == 2). */
fun pyRound(x: Double): Int = Math.rint(x).toInt()

fun floor(x: Double): Int = Math.floor(x).toInt()

fun ceil(x: Double): Int = Math.ceil(x).toInt()

/** Seconds as Python's time.time() gives them (for durations: monotonic). */
fun now(): Double = System.nanoTime() / 1e9
