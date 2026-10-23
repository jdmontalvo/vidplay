package com.vidplay.app.player

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Player container with a single, conflict-free gesture pipeline:
 *  - tap / double tap / long press (GestureDetector)
 *  - pinch zoom (ScaleGestureDetector)
 *  - one-finger pan while zoomed
 *  - horizontal seek and vertical brightness/volume while at 1x
 *
 * The whole PlayerView is transformed. The outer FrameLayout clips the result.
 */
class ZoomablePlayerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onToggleControls()
        fun onTogglePlay()
        fun onSeekBack()
        fun onSeekForward()
        fun onSeekPreview(deltaMs: Long)
        fun onSeekCommit(deltaMs: Long)
        fun onBoostStart()
        fun onBoostEnd()
        fun onBrightnessDelta(delta: Float)
        fun onVolumeDelta(delta: Float)
        fun onZoomChanged(scale: Float)
    }

    var listener: Listener? = null

    var maxZoom: Float = 5f
        set(value) {
            field = value.coerceAtLeast(1f)
            scale = scale.coerceAtMost(field)
            applyTransform()
        }

    private var playerView: PlayerView? = null
    private var scale = 1f
    private var translationX = 0f
    private var translationY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var boosting = false
    private var mode = DragMode.NONE
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener?.onToggleControls()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when {
                e.x < width * 0.34f -> listener?.onSeekBack()
                e.x > width * 0.66f -> listener?.onSeekForward()
                else -> listener?.onTogglePlay()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (scale > 1.01f) return
            boosting = true
            mode = DragMode.NONE
            dragging = false
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onBoostStart()
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = DragMode.NONE
            dragging = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = (scale * detector.scaleFactor).coerceIn(1f, maxZoom)
            applyScaleAround(target, detector.focusX, detector.focusY)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (scale < 1.01f) resetZoom()
        }
    })

    private enum class DragMode { NONE, PAN, SEEK, BRIGHTNESS, VOLUME }

    init {
        isClickable = true
        isFocusable = true
        clipChildren = true
        clipToPadding = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        playerView = findPlayerView(this)
        playerView?.isClickable = false
        playerView?.isFocusable = false
        playerView?.isLongClickable = false
        playerView?.isHapticFeedbackEnabled = false
        playerView?.pivotX = width / 2f
        playerView?.pivotY = height / 2f
    }

    private fun findPlayerView(view: android.view.View): PlayerView? {
        if (view is PlayerView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findPlayerView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragging = false
                boosting = false
                mode = DragMode.NONE
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = DragMode.NONE
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress || event.pointerCount > 1) {
                    mode = DragMode.NONE
                    dragging = false
                    lastX = event.x
                    lastY = event.y
                    return true
                }
                if (boosting) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    dragging = true
                    mode = when {
                        abs(dx) > abs(dy) && scale > 1.01f -> DragMode.PAN
                        abs(dx) > abs(dy) -> DragMode.SEEK
                        event.x < width / 2f -> DragMode.BRIGHTNESS
                        else -> DragMode.VOLUME
                    }
                }
                when (mode) {
                    DragMode.PAN -> {
                        translationX += event.x - lastX
                        translationY += event.y - lastY
                        clampTranslation()
                        applyTransform()
                    }
                    DragMode.SEEK -> {
                        val delta = ((event.x - downX) / width.coerceAtLeast(1) * SEEK_RANGE_MS).toLong()
                        listener?.onSeekPreview(delta)
                    }
                    DragMode.BRIGHTNESS -> {
                        val delta = (lastY - event.y) / height.coerceAtLeast(1).toFloat()
                        listener?.onBrightnessDelta(delta)
                    }
                    DragMode.VOLUME -> {
                        val delta = (lastY - event.y) / height.coerceAtLeast(1).toFloat()
                        listener?.onVolumeDelta(delta)
                    }
                    DragMode.NONE -> Unit
                }
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_UP -> {
                if (boosting) {
                    boosting = false
                    listener?.onBoostEnd()
                } else if (dragging && mode == DragMode.SEEK) {
                    val delta = ((event.x - downX) / width.coerceAtLeast(1) * SEEK_RANGE_MS).toLong()
                    listener?.onSeekCommit(delta)
                }
                dragging = false
                mode = DragMode.NONE
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (boosting) {
                    boosting = false
                    listener?.onBoostEnd()
                }
                dragging = false
                mode = DragMode.NONE
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyScaleAround(target: Float, focusX: Float, focusY: Float) {
        if (target == scale) return
        val centerX = width / 2f
        val centerY = height / 2f
        translationX += (scale - target) * (focusX - centerX)
        translationY += (scale - target) * (focusY - centerY)
        scale = target
        clampTranslation()
        applyTransform()
        listener?.onZoomChanged(scale)
    }

    private fun clampTranslation() {
        val maxX = (width * (scale - 1f)) / 2f
        val maxY = (height * (scale - 1f)) / 2f
        translationX = translationX.coerceIn(-maxX, maxX)
        translationY = translationY.coerceIn(-maxY, maxY)
        if (scale <= 1.001f) {
            translationX = 0f
            translationY = 0f
        }
    }

    private fun applyTransform() {
        val view = playerView ?: return
        view.pivotX = width / 2f
        view.pivotY = height / 2f
        view.scaleX = scale
        view.scaleY = scale
        view.translationX = translationX
        view.translationY = translationY
    }

    fun zoom(): Float = scale

    fun setZoom(target: Float) {
        applyScaleAround(target.coerceIn(1f, maxZoom), width / 2f, height / 2f)
    }

    fun resetZoom(animated: Boolean = true) {
        val view = playerView ?: return
        if (!animated) {
            scale = 1f
            translationX = 0f
            translationY = 0f
            applyTransform()
            listener?.onZoomChanged(scale)
            return
        }
        val startScale = scale
        val startX = translationX
        val startY = translationY
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedFraction
                scale = startScale + (1f - startScale) * t
                translationX = startX * (1f - t)
                translationY = startY * (1f - t)
                applyTransform()
                listener?.onZoomChanged(scale)
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampTranslation()
        applyTransform()
    }

    companion object {
        private const val SEEK_RANGE_MS = 90_000f
    }
}
