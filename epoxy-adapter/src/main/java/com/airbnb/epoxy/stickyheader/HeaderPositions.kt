package com.airbnb.epoxy.stickyheader

import androidx.recyclerview.widget.RecyclerView

class HeaderPositions(
    /**
     * Sorted [headerPositions] of all header positions. It should never contain numbers:
     *  - less than 0,
     *  - greater than or equal to [Delegate.itemCount]
     *  - duplicates
     */
    private val headerPositions: MutableList<Int> = mutableListOf(),

    val delegate: Delegate
) : RecyclerView.AdapterDataObserver(), Collection<Int> by headerPositions {

    interface Delegate {

        fun isStickyHeader(position: Int): Boolean

        fun checkIfStickyHeaderShouldBeScraped()

        fun itemCount(): Int
    }

    operator fun get(index: Int): Int {
        recomputeHeadersIfNecessary()
        return headerPositions[index]
    }

    fun clear() = headerPositions.clear()

    private var headersNeedRecompute = false

    /**
     * Finds the header index of [position] in [headerPositions] or -1 if it is not found.
     */
    fun indexOf(position: Int): Int {
        recomputeHeadersIfNecessary()

        var low = 0
        var high = headerPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                headerPositions[middle] > position -> high = middle - 1
                headerPositions[middle] < position -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }
    override fun contains(element: Int): Boolean {
        recomputeHeadersIfNecessary()
        return indexOf(element) != -1
    }

    /**
     * Finds the header index of [position] in [headerPositions] or the one before it if not found
     */
    fun indexOfOrBefore(position: Int): Int {
        recomputeHeadersIfNecessary()
        var low = 0
        var high = headerPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                headerPositions[middle] > position -> high = middle - 1
                middle < headerPositions.size - 1 && headerPositions[middle + 1] <= position -> low =
                    middle + 1
                else -> return middle
            }
        }
        return -1
    }

    /**
     * Finds the header index of [position] or if not found, the one after it in [headerPositions].
     */
    fun indexOfOrNext(position: Int): Int {
        recomputeHeadersIfNecessary()
        var low = 0
        var high = headerPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                middle > 0 && headerPositions[middle - 1] >= position -> high = middle - 1
                headerPositions[middle] < position -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }

    override fun onChanged() {
        // There's no hint at what changed, so go through the adapter.
        headerPositions.clear()
        val itemCount = delegate.itemCount()
        for (i in 0 until itemCount) {
            val isSticky = delegate.isStickyHeader(i)
            if (isSticky) {
                headerPositions.add(i)
            }
        }

        delegate.checkIfStickyHeaderShouldBeScraped()
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        if (headersNeedRecompute) return

        var headerCount = headerPositions.size
        if (headerCount > 0) {
            // Remove headers.
            for (i in positionStart + itemCount - 1 downTo positionStart) {
                val index = indexOf(i)
                if (index != -1) {
                    headerPositions.removeAt(index)
                    headerCount--
                }
            }

            delegate.checkIfStickyHeaderShouldBeScraped()

            // Shift headers below up.
            var i = indexOfOrNext(positionStart + itemCount)
            while (i != -1 && i < headerCount) {
                headerPositions[i] = headerPositions[i] - itemCount
                i++
            }
        }
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        if (headersNeedRecompute) return
        // Shift moved headers by toPosition - fromPosition.
        // Shift headers in-between by -itemCount (reverse if upwards).
        val headerCount = headerPositions.size
        if (headerCount > 0) {
            if (fromPosition < toPosition) {
                var i = indexOfOrNext(fromPosition)
                while (i != -1 && i < headerCount) {
                    val headerPos = headerPositions[i]
                    if (headerPos >= fromPosition && headerPos < fromPosition + itemCount) {
                        headerPositions[i] = headerPos + (toPosition - fromPosition)
                    } else if (headerPos >= fromPosition + itemCount && headerPos <= toPosition) {
                        headerPositions[i] = headerPos - itemCount
                    } else {
                        break
                    }
                    i++
                }
            } else {
                var i = indexOfOrNext(toPosition)
                loop@ while (i != -1 && i < headerCount) {
                    val headerPos = headerPositions[i]
                    when {
                        headerPos >= fromPosition && headerPos < fromPosition + itemCount -> {
                            headerPositions[i] = headerPos + (toPosition - fromPosition)
                        }
                        headerPos in toPosition..fromPosition -> {
                            headerPositions[i] = headerPos + itemCount
                        }
                        else -> break@loop
                    }
                    i++
                }
            }
        }
        headerPositions.sort()
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        /**
         * Since this new range could contains a header, we need to check `delegate.isStickyHeader()`.
         * However, when the dataset changes, there might be multiple ranges that are simultaneously
         * inserted or removed. It would be unsafe to call `delegate.isStickyHeader()` right now
         * as these messages may not be caught up to the dataset yet. We must wait until we've
         * processed all changes before attempting to access the dataset.
         *
         * So we mark headerPositions as dirty and recompute them whenever they are next accessed
         * as if `onChanged` had occurred. This issue is unique to onInserted because:
         *  - onChanged: should not be triggered simultaneously with other events
         *  - onRemoved/onMoved: does not require checking for new headers
         */
        headersNeedRecompute = true
    }

    fun recomputeHeadersIfNecessary() {
        if (headersNeedRecompute) {
            headersNeedRecompute = false
            onChanged()
        }
    }
}