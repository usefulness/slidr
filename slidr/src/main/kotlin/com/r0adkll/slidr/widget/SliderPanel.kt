package com.r0adkll.slidr.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.r0adkll.slidr.model.SlidrConfig
import com.r0adkll.slidr.model.SlidrInterface
import com.r0adkll.slidr.model.SlidrPosition
import com.r0adkll.slidr.util.getNavigationBarSize
import kotlin.math.abs

internal class SliderPanel(context: Context, private val decorView: View, private val config: SlidrConfig) : FrameLayout(context) {
    private var screenWidth = 0
    private var screenHeight = 0
    private lateinit var dragHelper: ViewDragHelper
    private var listener: OnPanelSlideListener? = null
    private val scrimPaint = Paint().apply {
        color = config.scrimColor
        alpha = toAlpha(config.scrimStartAlpha)
    }
    private val scrimRenderer = ScrimRenderer(this, decorView)
    private var isLocked = false
    private var isEdgeTouched = false
    private var edgePosition = 0
    private val softKeySize = context.getNavigationBarSize()

    /**
     * The drag helper callback interface for the Left position
     */
    private val leftCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            val edgeCase = !config.edgeOnly || dragHelper.isEdgeTouched(edgePosition, pointerId)
            return child.id == decorView.id && edgeCase
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = clamp(left, 0, screenWidth)

        override fun getViewHorizontalDragRange(child: View): Int = screenWidth

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val left = releasedChild.left
            var settleLeft = softKeySize
            val leftThreshold = (width * config.distanceThreshold).toInt()
            val isVerticalSwiping = Math.abs(yvel) > config.velocityThreshold
            if (xvel > 0) {
                if (abs(xvel) > config.velocityThreshold && !isVerticalSwiping) {
                    settleLeft = screenWidth
                } else if (left > leftThreshold) {
                    settleLeft = screenWidth
                }
            } else if (xvel == 0f) {
                if (left > leftThreshold) {
                    settleLeft = screenWidth
                }
            }
            dragHelper.settleCapturedViewAt(settleLeft, releasedChild.top)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - left.toFloat() / screenWidth.toFloat()
            listener?.onSlideChange(percent)

            // Update the dimmer alpha
            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            listener?.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView.left == softKeySize) {
                    // State Open
                    listener?.onOpened()
                } else {
                    // State Closed
                    listener?.onClosed()
                }

                ViewDragHelper.STATE_DRAGGING -> {
                }

                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment from the right of the screen
     */
    private val rightCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            val edgeCase = !config.edgeOnly || dragHelper.isEdgeTouched(edgePosition, pointerId)
            return child.id == decorView.id && edgeCase
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = clamp(left, -screenWidth, 0)

        override fun getViewHorizontalDragRange(child: View): Int = screenWidth

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val left = releasedChild.left
            var settleLeft = 0
            val leftThreshold = (width * config.distanceThreshold).toInt()
            val isVerticalSwiping = Math.abs(yvel) > config.velocityThreshold
            if (xvel < 0) {
                if (Math.abs(xvel) > config.velocityThreshold && !isVerticalSwiping) {
                    settleLeft = -screenWidth
                } else if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            } else if (xvel == 0f) {
                if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            }
            dragHelper.settleCapturedViewAt(settleLeft, releasedChild.top)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - Math.abs(left)
                .toFloat() / screenWidth.toFloat()
            listener?.onSlideChange(percent)

            // Update the dimmer alpha
            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            listener?.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView.left == 0) {
                    // State Open
                    listener?.onOpened()
                } else {
                    // State Closed
                    listener?.onClosed()
                }

                ViewDragHelper.STATE_DRAGGING -> {
                }

                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment from the top of the screen
     */
    private val topCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child.id == decorView.id && (!config.edgeOnly || isEdgeTouched)

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = clamp(top, 0, screenHeight)

        override fun getViewVerticalDragRange(child: View): Int = screenHeight

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val top = releasedChild.top
            var settleTop = 0
            val topThreshold = (height * config.distanceThreshold).toInt()
            val isSideSwiping = Math.abs(xvel) > config.velocityThreshold
            if (yvel > 0) {
                if (Math.abs(yvel) > config.velocityThreshold && !isSideSwiping) {
                    settleTop = screenHeight
                } else if (top > topThreshold) {
                    settleTop = screenHeight
                }
            } else if (yvel == 0f) {
                if (top > topThreshold) {
                    settleTop = screenHeight
                }
            }
            dragHelper.settleCapturedViewAt(releasedChild.left, settleTop)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - Math.abs(top)
                .toFloat() / screenHeight.toFloat()
            listener?.onSlideChange(percent)

            // Update the dimmer alpha
            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            listener?.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView.top == 0) {
                    // State Open
                    listener?.onOpened()
                } else {
                    // State Closed
                    listener?.onClosed()
                }

                ViewDragHelper.STATE_DRAGGING -> {
                }

                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment from the bottom of hte screen
     */
    private val bottomCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child.id == decorView.id && (!config.edgeOnly || isEdgeTouched)

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = clamp(top, -screenHeight, 0)

        override fun getViewVerticalDragRange(child: View): Int = screenHeight

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val top = releasedChild.top
            var settleTop = 0
            val topThreshold = (height * config.distanceThreshold).toInt()
            val isSideSwiping = Math.abs(xvel) > config.velocityThreshold
            if (yvel < 0) {
                if (Math.abs(yvel) > config.velocityThreshold && !isSideSwiping) {
                    settleTop = -screenHeight
                } else if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            } else if (yvel == 0f) {
                if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            }
            dragHelper.settleCapturedViewAt(releasedChild.left, settleTop)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - Math.abs(top)
                .toFloat() / screenHeight.toFloat()
            listener?.onSlideChange(percent)

            // Update the dimmer alpha
            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            listener?.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView.top == 0) {
                    // State Open
                    listener?.onOpened()
                } else {
                    // State Closed
                    listener?.onClosed()
                }

                ViewDragHelper.STATE_DRAGGING -> {
                }

                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment in both vertical directions
     */
    private val verticalCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child.id == decorView.id && (!config.edgeOnly || isEdgeTouched)

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = clamp(top, -screenHeight, screenHeight)

        override fun getViewVerticalDragRange(child: View): Int = screenHeight

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val top = releasedChild.top
            var settleTop = 0
            val topThreshold = (height * config.distanceThreshold).toInt()
            val isSideSwiping = Math.abs(xvel) > config.velocityThreshold
            if (yvel > 0) {
                // Being slinged down
                if (Math.abs(yvel) > config.velocityThreshold && !isSideSwiping) {
                    settleTop = screenHeight
                } else if (top > topThreshold) {
                    settleTop = screenHeight
                }
            } else if (yvel < 0) {
                // Being slinged up
                if (Math.abs(yvel) > config.velocityThreshold && !isSideSwiping) {
                    settleTop = -screenHeight
                } else if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            } else {
                if (top > topThreshold) {
                    settleTop = screenHeight
                } else if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            }
            dragHelper.settleCapturedViewAt(releasedChild.left, settleTop)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - Math.abs(top)
                .toFloat() / screenHeight.toFloat()
            listener?.onSlideChange(percent)

            // Update the dimmer alpha
            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            listener?.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView.top == 0) {
                    // State Open
                    listener?.onOpened()
                } else {
                    // State Closed
                    listener?.onClosed()
                }

                ViewDragHelper.STATE_DRAGGING -> {
                }

                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment in both horizontal directions
     */
    private val horizontalCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            val edgeCase = !config.edgeOnly || dragHelper.isEdgeTouched(edgePosition, pointerId)
            return child.id == decorView.id && edgeCase
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = clamp(left, -screenWidth, screenWidth)

        override fun getViewHorizontalDragRange(child: View): Int = screenWidth

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val left = releasedChild.left
            var settleLeft = softKeySize
            val leftThreshold = (width * config.distanceThreshold).toInt()
            val isVerticalSwiping = Math.abs(yvel) > config.velocityThreshold
            if (xvel > 0) {
                if (Math.abs(xvel) > config.velocityThreshold && !isVerticalSwiping) {
                    settleLeft = screenWidth
                } else if (left > leftThreshold) {
                    settleLeft = screenWidth
                }
            } else if (xvel < 0) {
                if (Math.abs(xvel) > config.velocityThreshold && !isVerticalSwiping) {
                    settleLeft = -screenWidth
                } else if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            } else {
                if (left > leftThreshold) {
                    settleLeft = screenWidth
                } else if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            }
            dragHelper.settleCapturedViewAt(settleLeft + softKeySize, releasedChild.top)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - Math.abs(left)
                .toFloat() / screenWidth.toFloat()
            listener?.onSlideChange(percent)

            // Update the dimmer alpha
            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            listener?.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView.left == 0) {
                    // State Open
                    listener?.onOpened()
                } else {
                    // State Closed
                    listener?.onClosed()
                }

                ViewDragHelper.STATE_DRAGGING -> {
                }

                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    init {
        setWillNotDraw(false)
        val density = resources.displayMetrics.density
        val minVel = MIN_FLING_VELOCITY * density
        val callback: ViewDragHelper.Callback
        when (config.position) {
            SlidrPosition.Left -> {
                callback = leftCallback
                edgePosition = ViewDragHelper.EDGE_LEFT
            }

            SlidrPosition.Right -> {
                callback = rightCallback
                edgePosition = ViewDragHelper.EDGE_RIGHT
            }

            SlidrPosition.Top -> {
                callback = topCallback
                edgePosition = ViewDragHelper.EDGE_TOP
            }

            SlidrPosition.Bottom -> {
                callback = bottomCallback
                edgePosition = ViewDragHelper.EDGE_BOTTOM
            }

            SlidrPosition.Vertical -> {
                callback = verticalCallback
                edgePosition = ViewDragHelper.EDGE_TOP or ViewDragHelper.EDGE_BOTTOM
            }

            SlidrPosition.Horizontal -> {
                callback = horizontalCallback
                edgePosition = ViewDragHelper.EDGE_LEFT or ViewDragHelper.EDGE_RIGHT
            }
        }
        dragHelper = ViewDragHelper.create(this, config.sensitivity, callback).apply {
            minVelocity = minVel
            setEdgeTrackingEnabled(edgePosition)
        }
        isMotionEventSplittingEnabled = false
        /*
         * This is so we can get the height of the view and
         * ignore the system navigation that would be included if we
         * retrieved this value from the DisplayMetrics
         */
        post {
            screenHeight = height
            screenWidth = width
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isLocked) {
            return false
        }
        if (config.edgeOnly) {
            isEdgeTouched = canDragFromEdge(ev)
        }

        // Fix for pull request #13 and issue #12
        val interceptForDrag: Boolean = try {
            dragHelper.shouldInterceptTouchEvent(ev)
        } catch (ignored: Exception) {
            false
        }
        return interceptForDrag && !isLocked
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) {
            return false
        }
        try {
            dragHelper.processTouchEvent(event)
        } catch (ignored: IllegalArgumentException) {
            return false
        }
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        scrimRenderer.render(canvas, config.position, scrimPaint)
    }

    /**
     * Set the panel slide listener that gets called based on slider changes
     *
     * @param listener callback implementation
     */
    fun setOnPanelSlideListener(listener: OnPanelSlideListener?) {
        this.listener = listener
    }

    /**
     * Get the default [SlidrInterface] from which to control the panel with after attachment
     */
    val defaultInterface: SlidrInterface = object : SlidrInterface {
        override fun lock() {
            this@SliderPanel.lock()
        }

        override fun unlock() {
            this@SliderPanel.unlock()
        }
    }

    private fun lock() {
        dragHelper.abort()
        isLocked = true
    }

    private fun unlock() {
        dragHelper.abort()
        isLocked = false
    }

    private fun canDragFromEdge(ev: MotionEvent): Boolean {
        val xPoint = ev.x
        val yPoint = ev.y
        return when (config.position) {
            SlidrPosition.Left -> xPoint < config.getEdgeSize(width.toFloat())

            SlidrPosition.Right -> xPoint > width - config.getEdgeSize(width.toFloat())

            SlidrPosition.Bottom -> yPoint > height - config.getEdgeSize(height.toFloat())

            SlidrPosition.Top -> yPoint < config.getEdgeSize(height.toFloat())

            SlidrPosition.Horizontal ->
                xPoint < config.getEdgeSize(width.toFloat()) || xPoint > width - config.getEdgeSize(width.toFloat())

            SlidrPosition.Vertical ->
                yPoint < config.getEdgeSize(height.toFloat()) || yPoint > height - config.getEdgeSize(height.toFloat())
        }
    }

    private fun applyScrim(percent: Float) {
        val alpha = percent * (config.scrimStartAlpha - config.scrimEndAlpha) + config.scrimEndAlpha
        scrimPaint.alpha = toAlpha(alpha)
        invalidate(scrimRenderer.getDirtyRect(config.position))
    }

    /**
     * The panel sliding interface that gets called
     * whenever the panel is closed or opened
     */
    interface OnPanelSlideListener {
        fun onStateChanged(state: Int)
        fun onClosed()
        fun onOpened()
        fun onSlideChange(percent: Float)
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 400 // dips per second
        private fun clamp(value: Int, min: Int, max: Int): Int = min.coerceAtLeast(max.coerceAtMost(value))

        private fun toAlpha(percentage: Float): Int = (percentage * 255).toInt()
    }
}
