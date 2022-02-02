package com.airbnb.epoxy.stickyheader

import androidx.recyclerview.widget.RecyclerView

class HeaderPositions(
    val list: MutableList<Int> = mutableListOf(),
    val delegate: Delegate
) : RecyclerView.AdapterDataObserver(), Collection<Int> by list {

    interface Delegate {

        fun isStickyHeader(position: Int): Boolean

        fun checkIfStickyHeaderShouldBeScraped()

        fun itemCount(): Int
    }

    operator fun get(index: Int): Int {
        return list[index]
    }

    fun clear() = list.clear()

    /**
     * Finds the header index of `position` in `headerPositions`.
     */
    fun indexOf(position: Int): Int {
        var low = 0
        var high = list.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                list[middle] > position -> high = middle - 1
                list[middle] < position -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }

    override fun contains(element: Int): Boolean {
        return indexOf(element) != -1
    }

    /**
     * Finds the header index of `position` or the one before it in `headerPositions`.
     */
    fun indexOfOrBefore(position: Int): Int {
        var low = 0
        var high = list.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                list[middle] > position -> high = middle - 1
                middle < list.size - 1 && list[middle + 1] <= position -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }

    /**
     * Finds the header index of `position` or the one next to it in `headerPositions`.
     */
    fun indexOfOrNext(position: Int): Int {
        var low = 0
        var high = list.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                middle > 0 && list[middle - 1] >= position -> high = middle - 1
                list[middle] < position -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }

    override fun onChanged() {
        // There's no hint at what changed, so go through the adapter.
        list.clear()
        val itemCount = delegate.itemCount()
        for (i in 0 until itemCount) {
            val isSticky = delegate.isStickyHeader(i)
            if (isSticky) {
                list.add(i)
            }
        }

        delegate.checkIfStickyHeaderShouldBeScraped()
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        var headerCount = list.size
        if (headerCount > 0) {
            // Remove headers.
            for (i in positionStart + itemCount - 1 downTo positionStart) {
                val index = indexOf(i)
                if (index != -1) {
                    list.removeAt(index)
                    headerCount--
                }
            }

            delegate.checkIfStickyHeaderShouldBeScraped()

            // Shift headers below up.
            var i = indexOfOrNext(positionStart + itemCount)
            while (i != -1 && i < headerCount) {
                list[i] = list[i] - itemCount
                i++
            }
        }
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        // Shift moved headers by toPosition - fromPosition.
        // Shift headers in-between by -itemCount (reverse if upwards).
        val headerCount = list.size
        if (headerCount > 0) {
            if (fromPosition < toPosition) {
                var i = indexOfOrNext(fromPosition)
                while (i != -1 && i < headerCount) {
                    val headerPos = list[i]
                    if (headerPos >= fromPosition && headerPos < fromPosition + itemCount) {
                        list[i] = headerPos + (toPosition - fromPosition)
                    } else if (headerPos >= fromPosition + itemCount && headerPos <= toPosition) {
                        list[i] = headerPos - itemCount
                    } else {
                        break
                    }
                    i++
                }
            } else {
                var i = indexOfOrNext(toPosition)
                loop@ while (i != -1 && i < headerCount) {
                    val headerPos = list[i]
                    when {
                        headerPos >= fromPosition && headerPos < fromPosition + itemCount -> {
                            list[i] = headerPos + (toPosition - fromPosition)
                        }
                        headerPos in toPosition..fromPosition -> {
                            list[i] = headerPos + itemCount
                        }
                        else -> break@loop
                    }
                    i++
                }
            }
        }
        list.sort()
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        // Shift headers below down.
        val headerCount = list.size
        if (headerCount > 0) {
            var i = indexOfOrNext(positionStart)
            while (i != -1 && i < headerCount) {
                list[i] = list[i] + itemCount
                i++
            }
        }

        // Add new headers.
        for (i in positionStart until positionStart + itemCount) {
            val isSticky = delegate.isStickyHeader(i)
            if (isSticky) {
                val headerIndex = indexOfOrNext(i)
                if (headerIndex != -1) {
                    list.add(headerIndex, i)
                } else {
                    list.add(i)
                }
            }
        }
    }
}