package com.airbnb.epoxy.stickyheader

import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.BaseEpoxyAdapter
import kotlinx.android.parcel.Parcelize

/**
 * Adds sticky headers capabilities to your [RecyclerView.Adapter].
 * The adapter / controller must override [StickyHeaderCallbacks.isStickyHeader] to
 * indicate which items are sticky.
 *
 * Example usage:
 * ```
 *  class StickyHeaderController() : EpoxyController() {
 *      override fun isStickyHeader(position: Int) {
 *          // Write your logic to tell which item is sticky.
 *      }
 *  }
 * ```
 */
class StickyHeaderLinearLayoutManager @JvmOverloads constructor(
    context: Context,
    orientation: Int = RecyclerView.VERTICAL,
    reverseLayout: Boolean = false
) : LinearLayoutManager(context, orientation, reverseLayout), HeaderPositions.Delegate {

    private var adapter: BaseEpoxyAdapter? = null

    // Translation for header
    private var translationX: Float = 0f
    private var translationY: Float = 0f

    // Header positions for the currently displayed list and their observer.
    private val headerPositions = HeaderPositions(delegate = this)

    // Sticky header's ViewHolder and dirty state.
    private var stickyHeader: View? = null
    private var stickyHeaderPosition = RecyclerView.NO_POSITION

    // Save / Restore scroll state
    private var scrollPosition = RecyclerView.NO_POSITION
    private var scrollOffset = 0

    override fun onAttachedToWindow(recyclerView: RecyclerView) {
        super.onAttachedToWindow(recyclerView)
        setAdapter(recyclerView.adapter)
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        setAdapter(newAdapter)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setAdapter(newAdapter: RecyclerView.Adapter<*>?) {
        adapter?.unregisterAdapterDataObserver(headerPositions)
        if (newAdapter is BaseEpoxyAdapter) {
            adapter = newAdapter
            adapter?.registerAdapterDataObserver(headerPositions)
            headerPositions.onChanged()
        } else {
            adapter = null
            headerPositions.clear()
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(
            superState = super.onSaveInstanceState(),
            scrollPosition = scrollPosition,
            scrollOffset = scrollOffset
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? SavedState)?.let {
            scrollPosition = it.scrollPosition
            scrollOffset = it.scrollOffset
            super.onRestoreInstanceState(it.superState)
        }
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State?): Int {
        val scrolled = restoreView { super.scrollVerticallyBy(dy, recycler, state) }
        if (scrolled != 0) {
            updateStickyHeader(recycler, false)
        }
        return scrolled
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State?): Int {
        val scrolled = restoreView { super.scrollHorizontallyBy(dx, recycler, state) }
        if (scrolled != 0) {
            updateStickyHeader(recycler, false)
        }
        return scrolled
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        restoreView { super.onLayoutChildren(recycler, state) }
        if (!state.isPreLayout) {
            updateStickyHeader(recycler, true)
        }
    }

    override fun scrollToPosition(position: Int) = scrollToPositionWithOffset(position, INVALID_OFFSET)

    override fun scrollToPositionWithOffset(position: Int, offset: Int) = scrollToPositionWithOffset(position, offset, true)

    private fun scrollToPositionWithOffset(position: Int, offset: Int, adjustForStickyHeader: Boolean) {
        // Reset pending scroll.
        setScrollState(RecyclerView.NO_POSITION, INVALID_OFFSET)

        // Adjusting is disabled.
        if (!adjustForStickyHeader) {
            super.scrollToPositionWithOffset(position, offset)
            return
        }

        // There is no header above or the position is a header.
        val headerIndex = headerPositions.indexOfOrBefore(position)
        if (headerIndex == -1 || headerPositions.contains(position)) {
            super.scrollToPositionWithOffset(position, offset)
            return
        }

        // The position is right below a header, scroll to the header.
        if (headerPositions.contains(position - 1)) {
            super.scrollToPositionWithOffset(position - 1, offset)
            return
        }

        // Current sticky header is the same as at the position. Adjust the scroll offset and reset pending scroll.
        if (stickyHeader != null && headerIndex == headerPositions.indexOf(stickyHeaderPosition)) {
            val adjustedOffset = (if (offset != INVALID_OFFSET) offset else 0) + stickyHeader!!.height
            super.scrollToPositionWithOffset(position, adjustedOffset)
            return
        }

        // Remember this position and offset and scroll to it to trigger creating the sticky header.
        setScrollState(position, offset)
        super.scrollToPositionWithOffset(position, offset)
    }

    //region Computation
    // Mainly [RecyclerView] functionality by removing sticky header from calculations

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = restoreView { super.computeVerticalScrollExtent(state) }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = restoreView { super.computeVerticalScrollOffset(state) }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = restoreView { super.computeVerticalScrollRange(state) }

    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = restoreView { super.computeHorizontalScrollExtent(state) }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int = restoreView { super.computeHorizontalScrollOffset(state) }

    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int = restoreView { super.computeHorizontalScrollRange(state) }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? = restoreView { super.computeScrollVectorForPosition(targetPosition) }

    override fun onFocusSearchFailed(
        focused: View,
        focusDirection: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): View? = restoreView { super.onFocusSearchFailed(focused, focusDirection, recycler, state) }

    /**
     * Perform the [operation] without the sticky header view by
     * detaching the view -> performing operation -> detaching the view.
     */
    private fun <T> restoreView(operation: () -> T): T {
        stickyHeader?.let(this::detachView)
        val result = operation()
        stickyHeader?.let(this::attachView)
        return result
    }

    //endregion

    /**
     * Offsets the vertical location of the sticky header relative to the its default position.
     */
    fun setStickyHeaderTranslationY(translationY: Float) {
        this.translationY = translationY
        requestLayout()
    }

    /**
     * Offsets the horizontal location of the sticky header relative to the its default position.
     */
    fun setStickyHeaderTranslationX(translationX: Float) {
        this.translationX = translationX
        requestLayout()
    }

    /**
     * Returns true if `view` is the current sticky header.
     */
    fun isStickyHeader(view: View): Boolean = view === stickyHeader

    /**
     * Updates the sticky header state (creation, binding, display), to be called whenever there's a layout or scroll
     */
    private fun updateStickyHeader(recycler: RecyclerView.Recycler, layout: Boolean) {
        val childCount = childCount
        if (headerPositions.isNotEmpty() && childCount > 0) {
            // Find first valid child.
            var anchorView: View? = null
            var anchorIndex = -1
            var anchorPos = -1
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val params = child!!.layoutParams as RecyclerView.LayoutParams
                if (isViewValidAnchor(child, params)) {
                    anchorView = child
                    anchorIndex = i
                    anchorPos = params.viewAdapterPosition
                    break
                }
            }
            if (anchorView != null && anchorPos != -1) {
                val headerCount = headerPositions.size
                val headerIndex = headerPositions.indexOfOrBefore(anchorPos)
                val headerPos = if (headerIndex != -1) headerPositions[headerIndex] else -1
                val nextHeaderPos = if (headerCount > headerIndex + 1) headerPositions[headerIndex + 1] else -1

                // Show sticky header if:
                // - There's one to show;
                // - It's on the edge or it's not the anchor view;
                // - Isn't followed by another sticky header;
                if (headerPos != -1 &&
                    (headerPos != anchorPos || isViewOnBoundary(anchorView)) &&
                    nextHeaderPos != headerPos + 1
                ) {
                    // 1. Ensure existing sticky header, if any, is of correct type.
                    if (stickyHeader != null && getItemViewType(stickyHeader!!) != adapter?.getItemViewType(headerPos)) {
                        // A sticky header was shown before but is not of the correct type. Scrap it.
                        scrapStickyHeader(recycler)
                    }

                    // 2. Ensure sticky header is created, if absent, or bound, if being laid out or the position changed.
                    if (stickyHeader == null) createStickyHeader(recycler, headerPos)
                    // 3. Bind the sticky header
                    if (layout || getPosition(stickyHeader!!) != headerPos) bindStickyHeader(recycler, stickyHeader!!, headerPos)

                    // 4. Draw the sticky header using translation values which depend on orientation, direction and
                    // position of the next header view.
                    stickyHeader?.let {
                        val nextHeaderView: View? = if (nextHeaderPos != -1) {
                            val nextHeaderView = getChildAt(anchorIndex + (nextHeaderPos - anchorPos))
                            // The header view itself is added to the RecyclerView. Discard it if it comes up.
                            if (nextHeaderView === stickyHeader) null else nextHeaderView
                        } else null
                        it.translationX = getX(it, nextHeaderView)
                        it.translationY = getY(it, nextHeaderView)
                    }
                    return
                }
            }
        }

        if (stickyHeader != null) {
            scrapStickyHeader(recycler)
        }
    }

    /**
     * Creates [RecyclerView.ViewHolder] for [position], including measure / layout, and assigns it to
     * [stickyHeader].
     */
    private fun createStickyHeader(recycler: RecyclerView.Recycler, position: Int) {
        val stickyHeader = recycler.getViewForPosition(position)

        // Setup sticky header if the adapter requires it.
        adapter?.setupStickyHeaderView(stickyHeader)

        // Add sticky header as a child view, to be detached / reattached whenever LinearLayoutManager#fill() is called,
        // which happens on layout and scroll (see overrides).
        addView(stickyHeader)
        measureAndLayout(stickyHeader)

        // Ignore sticky header, as it's fully managed by this LayoutManager.
        ignoreView(stickyHeader)

        this.stickyHeader = stickyHeader
        this.stickyHeaderPosition = position
    }

    /**
     * Binds the [stickyHeader] for the given [position].
     */
    private fun bindStickyHeader(recycler: RecyclerView.Recycler, stickyHeader: View, position: Int) {
        // Bind the sticky header.
        recycler.bindViewToPosition(stickyHeader, position)
        stickyHeaderPosition = position
        measureAndLayout(stickyHeader)

        // If we have a pending scroll wait until the end of layout and scroll again.
        if (scrollPosition != RecyclerView.NO_POSITION) {
            stickyHeader.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < 16) stickyHeader.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    else stickyHeader.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (scrollPosition != RecyclerView.NO_POSITION) {
                        scrollToPositionWithOffset(scrollPosition, scrollOffset)
                        setScrollState(RecyclerView.NO_POSITION, INVALID_OFFSET)
                    }
                }
            })
        }
    }

    /**
     * Measures and lays out [stickyHeader].
     */
    private fun measureAndLayout(stickyHeader: View) {
        measureChildWithMargins(stickyHeader, 0, 0)
        when (orientation) {
            RecyclerView.VERTICAL -> stickyHeader.layout(paddingLeft, 0, width - paddingRight, stickyHeader.measuredHeight)
            else -> stickyHeader.layout(0, paddingTop, stickyHeader.measuredWidth, height - paddingBottom)
        }
    }

    /**
     * Returns [stickyHeader] to the [RecyclerView]'s [RecyclerView.RecycledViewPool], assigning it
     * to `null`.
     *
     * @param recycler If passed, the sticky header will be returned to the recycled view pool.
     */
    private fun scrapStickyHeader(recycler: RecyclerView.Recycler?) {
        val stickyHeader = stickyHeader ?: return
        this.stickyHeader = null
        this.stickyHeaderPosition = RecyclerView.NO_POSITION

        // Revert translation values.
        stickyHeader.translationX = 0f
        stickyHeader.translationY = 0f

        // Teardown holder if the adapter requires it.
        adapter?.teardownStickyHeaderView(stickyHeader)

        // Stop ignoring sticky header so that it can be recycled.
        stopIgnoringView(stickyHeader)

        // Remove and recycle sticky header.
        removeView(stickyHeader)
        recycler?.recycleView(stickyHeader)
    }

    /**
     * Returns true when `view` is a valid anchor, ie. the first view to be valid and visible.
     */
    private fun isViewValidAnchor(view: View, params: RecyclerView.LayoutParams): Boolean {
        return when {
            !params.isItemRemoved && !params.isViewInvalid -> when (orientation) {
                RecyclerView.VERTICAL -> when {
                    reverseLayout -> view.top + view.translationY <= height + translationY
                    else -> view.bottom - view.translationY >= translationY
                }
                else -> when {
                    reverseLayout -> view.left + view.translationX <= width + translationX
                    else -> view.right - view.translationX >= translationX
                }
            }
            else -> false
        }
    }

    /**
     * Returns true when the `view` is at the edge of the parent [RecyclerView].
     */
    private fun isViewOnBoundary(view: View): Boolean {
        return when (orientation) {
            RecyclerView.VERTICAL -> when {
                reverseLayout -> view.bottom - view.translationY > height + translationY
                else -> view.top + view.translationY < translationY
            }
            else -> when {
                reverseLayout -> view.right - view.translationX > width + translationX
                else -> view.left + view.translationX < translationX
            }
        }
    }

    /**
     * Returns the position in the Y axis to position the header appropriately, depending on orientation, direction and
     * [android.R.attr.clipToPadding].
     */
    private fun getY(headerView: View, nextHeaderView: View?): Float {
        when (orientation) {
            RecyclerView.VERTICAL -> {
                var y = translationY
                if (reverseLayout) {
                    y += (height - headerView.height).toFloat()
                }
                if (nextHeaderView != null) {
                    val bottomMargin = (nextHeaderView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                    val topMargin = (nextHeaderView.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
                    y = when {
                        reverseLayout -> (nextHeaderView.bottom + bottomMargin).toFloat().coerceAtLeast(y)
                        else -> (nextHeaderView.top - topMargin - headerView.height).toFloat().coerceAtMost(y)
                    }
                }
                return y
            }
            else -> return translationY
        }
    }

    /**
     * Returns the position in the X axis to position the header appropriately, depending on orientation, direction and
     * [android.R.attr.clipToPadding].
     */
    private fun getX(headerView: View, nextHeaderView: View?): Float {
        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                var x = translationX
                if (reverseLayout) {
                    x += (width - headerView.width).toFloat()
                }
                if (nextHeaderView != null) {
                    val leftMargin = (nextHeaderView.layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
                    val rightMargin = (nextHeaderView.layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0
                    x = when {
                        reverseLayout -> (nextHeaderView.right + rightMargin).toFloat().coerceAtLeast(x)
                        else -> (nextHeaderView.left - leftMargin - headerView.width).toFloat().coerceAtMost(x)
                    }
                }
                return x
            }
            else -> return translationX
        }
    }

    override fun isStickyHeader(position: Int): Boolean {
        return adapter?.isStickyHeader(position) ?: false
    }

    override fun checkIfStickyHeaderShouldBeScraped() {
        // Remove sticky header immediately if the entry it represents has been removed. A layout will follow.
        if (stickyHeader != null && !headerPositions.contains(stickyHeaderPosition)) {
            scrapStickyHeader(null)
        }
    }

    override fun itemCount(): Int {
        return adapter?.itemCount ?: 0
    }

    private fun setScrollState(position: Int, offset: Int) {
        scrollPosition = position
        scrollOffset = offset
    }

    /**
     * Save / restore existing [RecyclerView] state and
     * scrolling position and offset.
     */
    @Parcelize
    data class SavedState(
        val superState: Parcelable?,
        val scrollPosition: Int,
        val scrollOffset: Int
    ) : Parcelable
}
