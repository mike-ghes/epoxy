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
class StickyHeaderController(
    private val context: Context
) : EpoxyController(), StickyHeaderCallbacks {

    var items = (0 until 100).map { Pair(it, (it % 5) == 0) }
        set(value) {
            field = value
            requestModelBuild()
        }


    override fun buildModels() {
        items.forEach {
            if (it.second) {
                stickyItemEpoxyHolder {
                    id("sticky-header ${it.first}")
                    title("Sticky header ${it.first}")
                    listener {
                        this@StickyHeaderController.items -= it
                        Toast.makeText(
                            this@StickyHeaderController.context,
                            "clicked",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else { itemEpoxyHolder {
                    id("view holder ${it.first}")
                    title("this is a View Holder item ${it.first}")
                    listener {
                        this@StickyHeaderController.items -= it
                        Toast.makeText(
                            this@StickyHeaderController.context,
                            "clicked",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // Feel feel to use any logic here to determine if the [position] is sticky view or not
    override fun isStickyHeader(position: Int) = adapter.getModelAtPosition(position) is StickyItemEpoxyHolder
}
