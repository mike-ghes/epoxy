package com.airbnb.epoxy.kotlinsample

import android.content.Context
import android.widget.Toast
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.kotlinsample.models.StickyItemEpoxyHolder
import com.airbnb.epoxy.kotlinsample.models.itemEpoxyHolder
import com.airbnb.epoxy.kotlinsample.models.stickyItemEpoxyHolder
import com.airbnb.epoxy.stickyheader.StickyHeaderCallbacks

/**
 * Showcases [EpoxyController] with sticky header support
 */

data class Item(val id: Int) {
    val isSticky = (id % 5 == 0)
}

class StickyHeaderController(
    private val context: Context
) : EpoxyController(), StickyHeaderCallbacks {

    var nextId = 100
    var items = (0 until nextId).map { Item(it) }
        set(value) {
            field = value
            requestModelBuild()
        }

    fun reset() {
        items = (0 until 100).map { Item(it) }
        nextId = 100
    }

    fun shuffle() {
        items = items.shuffled()
    }

    fun remove(item: Item) {
        // Removing an item is fine but trying to add at the same time will crash
        items = items - item + Item(nextId++)
    }

    override fun buildModels() {

        itemEpoxyHolder {
            id("reset")
            title("Reset")
            listener {
                this@StickyHeaderController.reset()
            }
        }

        itemEpoxyHolder {
            id("shuffle-items")
            title("Shuffle")
            listener {
                this@StickyHeaderController.shuffle()
            }
        }

        items.forEach {
            if (it.isSticky) {
                stickyItemEpoxyHolder {
                    id("sticky-header ${it.id}")
                    title("Sticky header ${it.id}")
                    listener {
                        this@StickyHeaderController.remove(it)
                    }
                }
            } else {
                itemEpoxyHolder {
                    id("view holder ${it.id}")
                    title("this is a View Holder item ${it.id}")
                    listener {
                        this@StickyHeaderController.remove(it)
                    }
                }
            }
        }
    }

    // Feel feel to use any logic here to determine if the [position] is sticky view or not
    override fun isStickyHeader(position: Int) =
        adapter.getModelAtPosition(position) is StickyItemEpoxyHolder
}
