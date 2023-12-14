package com.iammert.rangeview.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat


class RangeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    interface OnRangeValueListener {
        fun rangeChanged(maxValue: Float, minValue: Float, currentLeftValue: Float, currentRightValue: Float)
    }

    interface OnRangePositionListener {
        fun leftTogglePositionChanged(xCoordinate: Float, value: Float)

        fun rightTogglePositionChanged(xCoordinate: Float, value: Float)
    }

    interface OnRangeDraggingListener {
        fun onDraggingStateChanged(draggingState: DraggingState)
    }

    var rangeValueChangeListener: OnRangeValueListener? = null

    var rangePositionChangeListener: OnRangePositionListener? = null

    var rangeDraggingChangeListener: OnRangeDraggingListener? = null

    private var maxValue: Float = DEFAULT_MAX_VALUE

    private var minValue: Float = DEFAULT_MIN_VALUE

    private var currentLeftValue: Float? = null

    private var currentRightValue: Float? = null

    private var bgColor: Int = ContextCompat.getColor(context, R.color.rangeView_colorBackground)

    private var strokeColor: Int = ContextCompat.getColor(context, R.color.rangeView_colorStroke)

    private var maskColor: Int = ContextCompat.getColor(context, R.color.rangeView_colorMask)

    private var rippleColor: Int = ContextCompat.getColor(context, R.color.rangeView_colorRipple)

    private var strokeWidth: Float = resources.getDimension(R.dimen.rangeView_StrokeWidth)

    private var toggleRadius: Float = resources.getDimension(R.dimen.rangeView_ToggleRadius)

    private var toggleWidth: Float = resources.getDimension(R.dimen.rangeView_ToggleWidth)

    private var horizontalMargin: Float = resources.getDimension(R.dimen.rangeView_HorizontalSpace)

    private var strokeCornerRadius: Float = resources.getDimension(R.dimen.rangeView_strokeCornerRadius)

    private var backgroundCornerRadius: Float = resources.getDimension(R.dimen.rangeView_backgroundCornerRadius)

    private var bitmap: Bitmap? = null

    private var canvas: Canvas? = null

    private var backgroundBitmap: Bitmap? = null

    private var backgroundCanvas: Canvas? = null

    private var touchSizeFactor = 2f;

    private val eraser: Paint = Paint().apply {
        color = -0x1
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        flags = Paint.ANTI_ALIAS_FLAG
    }

    private var backgroundPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        flags = Paint.ANTI_ALIAS_FLAG
    }

    private var maskPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        color = maskColor
        flags = Paint.ANTI_ALIAS_FLAG
    }

    private var rangeStrokePaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
        strokeWidth = this@RangeView.strokeWidth
    }

    private var rangeTogglePaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        color = strokeColor
        flags = Paint.ANTI_ALIAS_FLAG
    }

    private var ripplePaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 4f
        color = rippleColor
        flags = Paint.ANTI_ALIAS_FLAG
    }

    private var draggingStateData: DraggingStateData = DraggingStateData.idle()

    private val totalValueRect: RectF = RectF()

    private val rangeValueRectF: RectF = RectF()

    private val rangeStrokeRectF: RectF = RectF()

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RangeView)
        bgColor = a.getColor(R.styleable.RangeView_colorBackground, bgColor)
        strokeColor = a.getColor(R.styleable.RangeView_strokeColor, strokeColor)
        rippleColor = a.getColor(R.styleable.RangeView_rippleColor, rippleColor)
        strokeWidth = a.getDimension(R.styleable.RangeView_strokeWidth, strokeWidth)
        minValue = a.getFloat(R.styleable.RangeView_minValue, minValue)
        maxValue = a.getFloat(R.styleable.RangeView_maxValue, maxValue)
        strokeCornerRadius = a.getDimension(R.styleable.RangeView_strokeCornerRadius, strokeCornerRadius)
        backgroundCornerRadius = a.getDimension(R.styleable.RangeView_backgroundCornerRadius, backgroundCornerRadius)
        horizontalMargin = a.getDimension(R.styleable.RangeView_horizontalSpace, horizontalMargin)

        backgroundPaint.color = bgColor
        rangeStrokePaint.color = strokeColor
        rangeStrokePaint.strokeWidth = strokeWidth
        rangeTogglePaint.color = strokeColor
        ripplePaint.color = rippleColor
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        totalValueRect.set(0f + horizontalMargin, 0f, measuredWidth.toFloat() - horizontalMargin, measuredHeight.toFloat())

        if (currentLeftValue == null || currentRightValue == null) {
            rangeValueRectF.set(
                    totalValueRect.left,
                    totalValueRect.top,
                    totalValueRect.right,
                    totalValueRect.bottom)
        } else {
            val leftRangePosition = ((totalValueRect.width()) * currentLeftValue!!) / maxValue
            val rightRangePosition = (totalValueRect.width() * currentRightValue!!) / maxValue
            rangeValueRectF.set(
                    leftRangePosition + horizontalMargin,
                    totalValueRect.top,
                    rightRangePosition + horizontalMargin,
                    totalValueRect.bottom)
        }
        rangeStrokeRectF.set(rangeValueRectF.left,
                rangeValueRectF.top + strokeWidth / 2,
                rangeValueRectF.right,
                rangeValueRectF.bottom - strokeWidth / 2)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        initializeBitmap()

        //Draw background color
        this.backgroundCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        this.backgroundCanvas?.drawRoundRect(totalValueRect, backgroundCornerRadius, backgroundCornerRadius, backgroundPaint)

        //Draw mask
        this.canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        this.canvas?.drawRoundRect(totalValueRect, backgroundCornerRadius, backgroundCornerRadius, maskPaint)

        //Clear range rectangle
        this.canvas?.drawRoundRect(rangeValueRectF, strokeCornerRadius, strokeCornerRadius, eraser)

        //Draw range rectangle stroke
        this.canvas?.drawRoundRect(rangeStrokeRectF, strokeCornerRadius, strokeCornerRadius, rangeStrokePaint)


        //Draw left toggle over range stroke
        this.canvas?.drawRoundRect(
                rangeValueRectF.left,
                height.toFloat(),
                rangeValueRectF.left + toggleWidth,
                0f,
                toggleRadius, toggleRadius,
                rangeTogglePaint)

        //Draw right toggle over range stroke
        this.canvas?.drawRoundRect(
                rangeValueRectF.right - toggleWidth,
                height.toFloat(),
                rangeValueRectF.right,
                0f,
                toggleRadius, toggleRadius,
                rangeTogglePaint)

        //Draw ripples on toggles
        val lineYTop = height.toFloat() * (1 / 3f)
        val lineYBottom = height.toFloat() * (2 / 3f)
        val lineX1 = rangeValueRectF.left + (toggleWidth / 2) - (toggleWidth / 10)
        val lineX2 = rangeValueRectF.left + (toggleWidth / 2) + (toggleWidth / 10)
        val lineX3 = rangeValueRectF.right - (toggleWidth / 2) - (toggleWidth / 10)
        val lineX4 = rangeValueRectF.right - (toggleWidth / 2) + (toggleWidth / 10)
        this.canvas?.drawLines(floatArrayOf(
                lineX1, lineYTop, lineX1, lineYBottom,
                lineX2, lineYTop, lineX2, lineYBottom,
                lineX3, lineYTop, lineX3, lineYBottom,
                lineX4, lineYTop, lineX4, lineYBottom,
        ), ripplePaint)


        //Draw background bitmap to original canvas
        backgroundBitmap?.let {
            canvas?.drawBitmap(it, 0f, 0f, null)
        }

        //Draw prepared bitmap to original canvas
        bitmap?.let {
            canvas?.drawBitmap(it, 0f, 0f, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingStateData = when {
                    isTouchOnLeftToggle(event) && isTouchOnRightToggle(event) -> DraggingStateData.createConflict(event)
                    isTouchOnLeftToggle(event) -> DraggingStateData.left(event)
                    isTouchOnRightToggle(event) -> DraggingStateData.right(event)
                    else -> DraggingStateData.idle()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (draggingStateData.draggingState) {
                    DraggingState.DRAGGING_CONFLICT_TOGGLE -> {
                        if (Math.abs(draggingStateData.motionX - event.x) < SLOP_DIFF) {
                            return true
                        }

                        val direction = resolveMovingWay(event, draggingStateData)
                        draggingStateData = when (direction) {
                            Direction.LEFT -> {
                                draggingLeftToggle(event)
                                DraggingStateData.left(event)
                            }

                            Direction.RIGHT -> {
                                draggingRightToggle(event)
                                DraggingStateData.right(event)
                            }
                        }
                    }

                    DraggingState.DRAGGING_RIGHT_TOGGLE -> {
                        if (isRightToggleExceed(event)) {
                            return true
                        } else {
                            draggingRightToggle(event)
                        }
                    }

                    DraggingState.DRAGGING_LEFT_TOGGLE -> {
                        if (isLeftToggleExceed(event)) {
                            return true
                        } else {
                            draggingLeftToggle(event)
                        }
                    }

                    else -> {
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                rangeDraggingChangeListener?.onDraggingStateChanged(DraggingState.DRAGGING_END)
                draggingStateData = DraggingStateData.idle()
            }
        }

        rangeDraggingChangeListener?.onDraggingStateChanged(draggingStateData.draggingState)

        return true
    }

    fun setMaxValue(maxValue: Float) {
        this.maxValue = maxValue
        postInvalidate()
    }

    fun setMinValue(minValue: Float) {
        this.minValue = minValue
        postInvalidate()
    }

    fun setCurrentValues(leftValue: Float, rightValue: Float) {
        currentLeftValue = leftValue
        currentRightValue = rightValue
        requestLayout()
        postInvalidate()
    }

    fun getXPositionOfValue(value: Float): Float {
        if (value < minValue || value > maxValue) {
            return 0f
        }
        return (((totalValueRect.width()) * value) / maxValue) + horizontalMargin
    }

    private fun resolveMovingWay(motionEvent: MotionEvent, stateData: DraggingStateData): Direction {
        return if (motionEvent.x > stateData.motionX) Direction.RIGHT else Direction.LEFT
    }

    private fun initializeBitmap() {
        if (bitmap == null || canvas == null) {
            bitmap?.recycle()

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap?.let {
                this.canvas = Canvas(it)
            }

            backgroundBitmap?.let {
                this.backgroundCanvas = Canvas(it)
            }
        }
    }

    private fun draggingLeftToggle(motionEvent: MotionEvent) {
        rangeValueRectF.set(motionEvent.x, rangeValueRectF.top, rangeValueRectF.right, rangeValueRectF.bottom)
        rangeStrokeRectF.set(rangeValueRectF.left, rangeValueRectF.top + strokeWidth / 2, rangeValueRectF.right, rangeValueRectF.bottom - strokeWidth / 2)
        rangePositionChangeListener?.leftTogglePositionChanged(rangeValueRectF.left, getLeftValue())
        postInvalidate()
        notifyRangeChanged()
    }

    private fun draggingRightToggle(motionEvent: MotionEvent) {
        rangeValueRectF.set(rangeValueRectF.left, rangeValueRectF.top, motionEvent.x, rangeValueRectF.bottom)
        rangeStrokeRectF.set(rangeValueRectF.left, rangeValueRectF.top + strokeWidth / 2, rangeValueRectF.right, rangeValueRectF.bottom - strokeWidth / 2)
        rangePositionChangeListener?.rightTogglePositionChanged(rangeValueRectF.right, getRightValue())
        postInvalidate()
        notifyRangeChanged()
    }

    private fun isLeftToggleExceed(motionEvent: MotionEvent): Boolean {
        return motionEvent.x < totalValueRect.left || motionEvent.x > rangeValueRectF.right
    }

    private fun isRightToggleExceed(motionEvent: MotionEvent): Boolean {
        return motionEvent.x < rangeValueRectF.left || motionEvent.x > totalValueRect.right
    }

    private fun isTouchOnLeftToggle(motionEvent: MotionEvent): Boolean {
        return motionEvent.x > rangeValueRectF.left - (toggleWidth * touchSizeFactor) && motionEvent.x < rangeValueRectF.left + toggleWidth + (toggleWidth * touchSizeFactor)
    }

    private fun isTouchOnRightToggle(motionEvent: MotionEvent): Boolean {
        return motionEvent.x > rangeValueRectF.right - toggleWidth - (toggleWidth * touchSizeFactor) && motionEvent.x < rangeValueRectF.right + (toggleWidth * touchSizeFactor)
    }

    private fun getLeftValue(): Float {
        val totalDiffInPx = totalValueRect.right - totalValueRect.left
        val firstValueInPx = rangeValueRectF.left - totalValueRect.left
        return maxValue * firstValueInPx / totalDiffInPx
    }

    private fun getRightValue(): Float {
        val totalDiffInPx = totalValueRect.right - totalValueRect.left
        val secondValueInPx = rangeValueRectF.right - totalValueRect.left
        return maxValue * secondValueInPx / totalDiffInPx
    }

    private fun notifyRangeChanged() {
        val firstValue = getLeftValue()
        val secondValue = getRightValue()

        val leftValue = Math.min(firstValue, secondValue)
        val rightValue = Math.max(firstValue, secondValue)

        currentLeftValue = leftValue
        currentRightValue = rightValue

        rangeValueChangeListener?.rangeChanged(maxValue, minValue, leftValue, rightValue)
    }

    companion object {

        private const val DEFAULT_MAX_VALUE = 1f

        private const val DEFAULT_MIN_VALUE = 0f

        private const val SLOP_DIFF = 20f
    }

}