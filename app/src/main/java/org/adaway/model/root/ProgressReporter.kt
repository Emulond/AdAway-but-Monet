package org.adaway.model.root

/**
 * Reports progress as a whole percentage, emitting only when that percentage changes.
 *
 * The hosts file is written entry by entry, so reporting every entry would post millions of
 * notifications for a hundred distinct values.
 */
class ProgressReporter(
    private val total: Int,
    private val onPercent: (Int) -> Unit
) {
    private var done = 0
    private var reported = -1

    /**
     * Record that one more item was processed, reporting the percentage when it changes.
     */
    fun increment() {
        done++
        val percent = percentOf(done, this.total)
        if (percent != this.reported) {
            this.reported = percent
            this.onPercent(percent)
        }
    }

    companion object {
        /**
         * Compute a whole percentage, clamped to the 0 to 100 range.
         *
         * A total of zero or less means there is nothing to do, which is reported as complete
         * rather than as a division by zero. More items than expected are reported as complete
         * rather than as more than a hundred percent.
         */
        @JvmStatic
        fun percentOf(done: Int, total: Int): Int {
            if (total <= 0) {
                return 100
            }
            val percent = (done.toLong() * 100L / total.toLong()).toInt()
            return percent.coerceIn(0, 100)
        }
    }
}
