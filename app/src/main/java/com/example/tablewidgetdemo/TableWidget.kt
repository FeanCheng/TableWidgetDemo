package com.example.tablewidgetdemo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntRange
import androidx.core.graphics.contains
import androidx.core.util.forEach
import androidx.core.util.getOrDefault
import androidx.core.view.updateLayoutParams
import com.blankj.utilcode.util.SizeUtils
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * created by feancheng on 2020/9/30
 * Desc: a table widget
 */
@SuppressLint("UseCompatLoadingForDrawables")
class TableWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    rowCount: Int = 3,
    columnCount: Int = 4,
    private var isVertical: Boolean = false
) :
    View(context, attrs, defStyleAttr) {


    val TAG = "TableWidget"

    //行
    @IntRange(from = 1)
    private var mRowCount: Int = 3
    //列
    @IntRange(from = 1)
    private var mColumnCount = 4

    private val mBorderStrokeWidth = 5f
    private val mInnerBorderStrokeWidth = 4f
    private val mTextSize = SizeUtils.sp2px(14f) * 1.0f

    private var mOldViewWidth = 0
    private var mOldViewHeight = 0
    /**
     * 是否是激活状态 激活代表可以编辑
     */
    private var isActive = false
    private var isDrag = false
    private var isDraged = false


    private val mBorderPaint = Paint()
    private val mInnerBorderPaint = Paint()
    private val mFocusItemPaint = Paint()
    private val mTextPaint = TextPaint()


    private val mBorderPath = Path()



    //initial status
    val STATUS_INIT = 0

    //drag status
    private val STATUS_DRAG = 1
    private val STATUS_ROTATE = 3
    private val STATUS_ZOOM = 4
    private val STATUS_CANCEL = 5
    private val STATUS_BLANK = 6
    private val STATUS_CLICK = 7
    private val STATUS_REGION = 8

    private var mDegree: Float = 0f //rotate degree

    /**
     * 触摸状态 用于分辨当前手势需要做什么对应的事情
     */
    private var mTouchStatus = STATUS_INIT


    /**
     * 点击唤起外部输入框监听
     */
    var mOnTableItemClickListener: OnTableItemClickListener? = null

    private val MAX_ROW: Int = 8
    private val MAX_COLUMN = 8

    private val mMinWidth: Int by lazy {
        400 + mDrawableWidth
    }
    private val mMinHeight: Int by lazy {
        200 + mDrawableHeight
    }

    private var mCurSingleItemHeight = 0
        get() {
            if (field == 0) {
                field = mViewHeight / mRowCount
            }
            return field
        }




    private var mViewWidth: Int = if (isVertical) {
        SizeUtils.dp2px(100f)
    } else {
        SizeUtils.dp2px(300f)
    }
        get() {
            if (field == 0) {
                field = max(width - mDrawableWidth, mMinWidth - mDrawableWidth)
            }
            return field
        }
    private var mViewHeight: Int = if (isVertical) {
        SizeUtils.dp2px(300f)
    } else {
        SizeUtils.dp2px(100f)
    }
        get() {
            if (field == 0) {
                field = max(height - mDrawableHeight, mMinHeight - mDrawableHeight)
            }
            return field
        }


    private val mFirstTouchPoint = PointF()
    private val mCurMovePointF = PointF()
    private val mPreMovePointF = PointF()
    private val mCenterPoint = PointF()

    private var mLTPoint = Point()
    private var mRTPoint = Point()
    private var mRBPoint = Point()
    private var mLBPoint = Point()


    private var mDrawableWidth: Int = 0
        set(value) {
            field = value
            mHalfDrawableWidth = (field shr 1).toFloat()
        }
    private var mDrawableHeight: Int = 0
        set(value) {
            field = value
            mHalfDrawableHeight = (field shr 1).toFloat()
        }
    private var mHalfDrawableWidth: Float = 0F
        get() {
            if (field == 0f) {
                field = (mDrawableWidth shr 1).toFloat()
            }
            return field
        }
    private var mHalfDrawableHeight: Float = 0F
        get() {
            if (field == 0f) {
                field = (mDrawableHeight shr 1).toFloat()
            }
            return field
        }

    private val leftTopDrawable: Drawable by lazy {
        context.resources.getDrawable(R.mipmap.aviary_delete_knob)
    }
    private val rightTopDrawable: Drawable by lazy {
        context.resources.getDrawable(R.mipmap.icon_rotate)
    }
    private val rightBottomDrawable: Drawable by lazy {
        context.resources.getDrawable(R.mipmap.aviary_resize_knob)
    }


    //数据实体
    private var mEntityMap: SparseArray<TableEntity> = SparseArray()

    init {
        if (isVertical) {
            mRowCount = columnCount
            mColumnCount = rowCount
        } else {
            mRowCount = rowCount
            mColumnCount = columnCount
        }

        mBorderPaint.color = Color.BLACK
        mBorderPaint.style = Paint.Style.STROKE
        mBorderPaint.strokeWidth = mBorderStrokeWidth
        mBorderPaint.isAntiAlias = true



        mInnerBorderPaint.color = Color.RED
        mInnerBorderPaint.style = Paint.Style.STROKE
        mInnerBorderPaint.strokeWidth = mInnerBorderStrokeWidth
        mInnerBorderPaint.isAntiAlias = true


        mFocusItemPaint.color = Color.GRAY
        mFocusItemPaint.style = Paint.Style.FILL_AND_STROKE
        mFocusItemPaint.isAntiAlias = true

        mTextPaint.color = Color.BLACK
        mTextPaint.style = Paint.Style.STROKE
        mTextPaint.textSize = mTextSize
        mTextPaint.isAntiAlias = true

        mDrawableWidth = leftTopDrawable.intrinsicWidth
        mDrawableHeight = leftTopDrawable.intrinsicHeight

        mMinHeight
        mMinWidth

        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * 对每个方格进行重重新计算它的点击区域
     */
    private fun reCalculationItemClickRegion(canvas: Canvas) {

        val rowHeight = (height.toFloat() - mDrawableHeight) / mRowCount
        val columnWidth = (width.toFloat() - mDrawableWidth) / mColumnCount
        for (column in 0 until mColumnCount) {
            val left = column * columnWidth + mHalfDrawableWidth
            for (row in 0 until mRowCount) {
                val top = row * rowHeight + mHalfDrawableHeight
                val key = row * 10 + column * 1
                val region = RectF(left, top, left + columnWidth, top + rowHeight)
                val entity = mEntityMap.getOrDefault(
                    key, TableEntity(
                        region,
                        "0",
                        row + 1,
                        column + 1
                    )
                )
                entity.region = region
                entity.row = row + 1
                entity.column = column + 1
                mEntityMap.put(key, entity)
                if (entity.isFocusRegion) {
                    canvas.drawRect(entity.region, mFocusItemPaint)
                }
                if (TextUtils.isEmpty(entity.text).not()) {
                    drawText(canvas, entity)
                }
            }
        }
    }

    /**
     * 如果用户进行删除行的操作 就对当前即将被删除行的数据进行清空
     */
    private fun clearComingSoonDeletedRowRegionData() {
        val maxKey = if (isVertical) {
            mRowCount * 10
        } else {
            (mRowCount - 1) * 10 + mColumnCount - 1 * 1
        }
        mEntityMap.forEach { key, value ->
            if ((isVertical && key >= maxKey) || key > maxKey) {
                value.reset()
            }
        }
    }

    /**
     * 如果用户进行删除列的操作 就对当前即将被删除列的数据进行清空
     */
    private fun clearComingSoonDeletedColumnRegionData() {
        val mod = mColumnCount - 1

        mEntityMap.forEach { key, value ->
            if (key % 10 == mod) {
                //对数据实体进行清空数据
                value.reset()
            }
        }
    }


    private fun drawText(canvas: Canvas, entity: TableEntity) {
        canvas.save()
        canvas.clipRect(entity.region)

        val width = if (isVertical) {
            entity.region.height()
        } else {
            entity.region.width()
        }.toInt()

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(
                entity.text,
                0,
                entity.text.length,
                mTextPaint,
                width
            ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

        } else {
            StaticLayout(
                entity.text,
                mTextPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                1f,
                0.5f,
                false
            )
        }
        if (isVertical) {
            canvas.translate(
                entity.region.right,
                entity.region.top
            )
            canvas.rotate(90f)
            canvas.translate(0f, entity.region.width() / 2 - (layout.height / 2))
        } else {
            canvas.translate(
                entity.region.centerX() - (layout.width / 2),
                entity.region.centerY() - (layout.height / 2)
            )
        }

        layout.draw(canvas)
        canvas.restore()
    }


    /**
     * 更新当前是否是激活状态
     */
    fun updateActiveStatus(isActive: Boolean) {
        this.isActive = isActive
        invalidate()
    }

//

//    fun updateBorderColor(color: Int) {
//        mBorderPaint.color = color
//        invalidate()
//    }
//
//    fun updateInnerBorderColor(color: Int) {
//        mInnerBorderPaint.color = color
//        invalidate()
//    }


    fun updateRowCount(@IntRange(from = 1, to = 8) rowCount: Int): Int {
        if (rowCount < 1) {
            mRowCount = 1
            return mRowCount
        }
        if (rowCount > MAX_ROW) {
            mRowCount = MAX_ROW
            return mRowCount
        }
        //clear  row map
        val isDeleteRow = mRowCount > rowCount
        mRowCount = rowCount
        if (isDeleteRow) {
            clearComingSoonDeletedRowRegionData()
        }

        mViewHeight = if (isDeleteRow) {
            if ((mViewHeight - mCurSingleItemHeight + mDrawableHeight) <= mMinHeight) {
                mCurSingleItemHeight = (mMinHeight - mDrawableHeight) / mRowCount
                invalidate()
                mMinHeight - mDrawableHeight

            } else {
                mViewHeight - mCurSingleItemHeight
            }
        } else {
            mViewHeight + mCurSingleItemHeight
        }
        safeRequestLayout()

        return mRowCount
    }


    fun updateColumnCount(@IntRange(from = 1, to = 8) columnCount: Int): Int {
        if (columnCount < 1) {
            mColumnCount = 1
            return mColumnCount
        }
        if (columnCount > MAX_COLUMN) {
            mColumnCount = MAX_COLUMN
            return mColumnCount
        }
        //clear column map
        if (mColumnCount > columnCount) {
            clearComingSoonDeletedColumnRegionData()
        }
        mColumnCount = columnCount
        invalidate()
        return mColumnCount
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val width = max(mViewWidth + mDrawableWidth, mMinWidth)
        val height = max(mViewHeight + mDrawableHeight, mMinHeight)

        mCenterPoint.set((width shr 1).toFloat() + left, (height shr 1).toFloat() + top)

        setMeasuredDimension(width, height)
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawInnerBorder(canvas)
        drawBorder(canvas)
        reCalculationItemClickRegion(canvas)
        obtainLTRBControl()

        if (isActive) {
            rightBottomDrawable.setBounds(
                (mRBPoint.x - mHalfDrawableWidth).toInt(),
                (mRBPoint.y - mHalfDrawableHeight).toInt(),
                (mRBPoint.x + mHalfDrawableWidth).toInt(),
                (mRBPoint.y + mHalfDrawableHeight).toInt()
            )
            rightBottomDrawable.draw(canvas)
            leftTopDrawable.setBounds(
                (mLTPoint.x - mHalfDrawableWidth).toInt(),
                (mLTPoint.y - mHalfDrawableHeight).toInt(),
                (mLTPoint.x + mHalfDrawableWidth).toInt(),
                (mLTPoint.y + mHalfDrawableHeight).toInt()
            )
            leftTopDrawable.draw(canvas)
//            rightTopDrawable.setBounds(
//                (mRTPoint.x - mHalfDrawableWidth).toInt(),
//                (mRTPoint.y - mHalfDrawableHeight).toInt(),
//                (mRTPoint.x + mHalfDrawableWidth).toInt(),
//                (mRTPoint.y + mHalfDrawableHeight).toInt()
//            )
//            rightTopDrawable.draw(canvas)
        }


    }


    private fun drawInnerBorder(canvas: Canvas) {


        if (mColumnCount > 1) {
            val columnWidth = (width.toFloat() - mDrawableWidth) / mColumnCount
            for (column in 1 until mColumnCount) {
                canvas.drawLine(
                    (columnWidth * column) - mInnerBorderStrokeWidth / 2 + mHalfDrawableWidth,
                    mHalfDrawableHeight,
                    (columnWidth * column) + mInnerBorderStrokeWidth / 2 + mHalfDrawableWidth,
                    height - mHalfDrawableHeight,
                    mInnerBorderPaint
                )
            }
        }


        if (mRowCount > 1) {
            val rowHeight = (height.toFloat() - mDrawableHeight) / mRowCount
            for (column in 1 until mRowCount) {
                canvas.drawLine(
                    mHalfDrawableWidth,
                    (rowHeight * column) - mInnerBorderStrokeWidth / 2 + mHalfDrawableHeight,
                    width - mHalfDrawableWidth,
                    (rowHeight * column) + mInnerBorderStrokeWidth / 2 + mHalfDrawableHeight,
                    mInnerBorderPaint
                )
            }
        }
    }

    private fun drawBorder(canvas: Canvas) {

        mBorderPath.reset()
        mBorderPath.moveTo(
            mHalfDrawableWidth,
            mHalfDrawableHeight
        )

        mBorderPath.lineTo(
            width - mHalfDrawableWidth,
            mHalfDrawableHeight
        )

        mBorderPath.lineTo(
            width - mHalfDrawableWidth,
            height - mHalfDrawableHeight
        )
        mBorderPath.lineTo(
            mHalfDrawableWidth,
            height - mHalfDrawableHeight
        )
        mBorderPath.close()
        canvas.drawPath(mBorderPath, mBorderPaint)
    }


    private fun safeRequestLayout() {
        if (isInLayout.not()) {
            requestLayout()
            invalidate()
        }
    }


    private fun obtainLTRBControl() {
        mLTPoint = Point(mHalfDrawableWidth.toInt(), (mHalfDrawableHeight).toInt())
        mRTPoint = Point((width - mHalfDrawableWidth).toInt(), (mHalfDrawableHeight).toInt())
        mLBPoint = Point((mHalfDrawableWidth.toInt()), (height - mHalfDrawableHeight).toInt())
        mRBPoint =
            Point((width - mHalfDrawableWidth).toInt(), (height - mHalfDrawableHeight).toInt())

    }


    private fun move() {
        val dx = mCurMovePointF.x - mFirstTouchPoint.x
        val dy = mCurMovePointF.y - mFirstTouchPoint.y
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = (left + dx).toInt()
            topMargin = (top + dy).toInt()
        }
    }



    private fun zoom(w: Int, h: Int) {
        val dx = mCurMovePointF.x - mFirstTouchPoint.x
        val dy = mCurMovePointF.y - mFirstTouchPoint.y
        mViewHeight = (h + dy).toInt()
        mViewWidth = (w + dx).toInt()
        safeRequestLayout()
    }


    /**
     * 当手势事件进行到 down时 需要去检测当前手势事件属于哪一种
     */
    private fun judgeStatus(x: Float, y: Float): Int {
        val touchPoint = PointF(x, y)
        val ltRect = RectF(
            mLTPoint.x - mHalfDrawableWidth,
            mLTPoint.y - mHalfDrawableHeight,
            mLTPoint.x + mHalfDrawableWidth,
            mLTPoint.y + mHalfDrawableHeight
        )
        if (ltRect.contains(touchPoint)) {
            return STATUS_CANCEL
        }
        val rtRect = RectF(
            mRTPoint.x - mHalfDrawableWidth,
            mRTPoint.y - mHalfDrawableHeight,
            mRTPoint.x + mHalfDrawableWidth,
            mRTPoint.y + mHalfDrawableHeight
        )
        if (rtRect.contains(touchPoint)) {
            return STATUS_ROTATE
        }
        val rbRect = RectF(
            mRBPoint.x - mHalfDrawableWidth,
            mRBPoint.y - mHalfDrawableHeight,
            mRBPoint.x + mHalfDrawableWidth,
            mRBPoint.y + mHalfDrawableHeight
        )
        if (rbRect.contains(touchPoint)) {
            return STATUS_ZOOM
        }
        // 检测别的区域
        val viewRect = RectF(
            mLTPoint.x.toFloat(), mLTPoint.y.toFloat(),
            mRBPoint.x.toFloat(), mRBPoint.y.toFloat()
        )
        if (viewRect.contains(touchPoint)) {
            return STATUS_REGION
        }
        return STATUS_BLANK
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {

                return if (isActive) {
                    isDrag = false
                    isDraged = false
                    mTouchStatus = judgeStatus(event.x, event.y)
                    if (mTouchStatus == STATUS_BLANK) {
                        false
                    } else {
                        mFirstTouchPoint.set(event.x, event.y)
                        mPreMovePointF.set(mFirstTouchPoint)
                        mOldViewWidth = mViewWidth
                        mOldViewHeight = mViewHeight
                        true
                    }

                } else {
                    isActive = true
                    invalidate()
                    false
                }
            }
            MotionEvent.ACTION_MOVE -> {

                mCurMovePointF.set(event.x, event.y)
                val dx = mCurMovePointF.x - mFirstTouchPoint.x
                val dy = mCurMovePointF.y - mFirstTouchPoint.y


                if (isDraged) {
                    isDrag = true
                }
                isDraged = !(abs(dx) <= 10 && abs(dy) <= 10) //滑动距离超出10 将其判定为 拖拽

                //对当前事件坐标处于表格区域内 进行重新判断 将拆分为点击 或者 拖拽
                mTouchStatus =
                    if ((isDrag || isDraged) && (mTouchStatus == STATUS_REGION || mTouchStatus == STATUS_CLICK)) {
                        STATUS_DRAG
                    } else if (mTouchStatus != STATUS_DRAG && isDrag.not() && isDraged.not() && mTouchStatus == STATUS_REGION) {
                        STATUS_CLICK
                    } else {
                        mTouchStatus
                    }
                when (mTouchStatus) {
                    STATUS_DRAG -> {
                        move()
                    }
                    STATUS_ROTATE -> {
//                        rotate()
                    }
                    STATUS_ZOOM -> {
                        zoom(mOldViewWidth, mOldViewHeight)
                    }
                }
                mPreMovePointF.set(mCurMovePointF)
            }
            MotionEvent.ACTION_UP -> {
                when (mTouchStatus) {
                    STATUS_DRAG -> {
                        isDrag = false
                        isDraged = false
                    }
                    STATUS_ROTATE -> {
//                        LogUtils.dTag(TAG,"rotate")
                    }
                    STATUS_CANCEL -> {
                        (parent as? ViewGroup)?.removeView(this)
                    }
                    STATUS_ZOOM -> {
                        mViewWidth = (mOldViewWidth + mCurMovePointF.x - mFirstTouchPoint.x).toInt()
                        mViewHeight =
                            (mOldViewHeight + mCurMovePointF.y - mFirstTouchPoint.y).toInt()
                        if (mViewWidth <= mMinWidth - mDrawableWidth) {
                            mViewWidth = mMinWidth - mDrawableWidth
                        }
                        if (mViewHeight <= mMinHeight - mDrawableHeight) {
                            mViewHeight = mMinHeight - mDrawableHeight

                        }
                        mCurSingleItemHeight = mViewHeight / mRowCount
                        safeRequestLayout()
                    }
                    STATUS_CLICK -> {
                        //在手指抬起的时候 对所有区域进行一次遍历 对命中区域进行isFocusRegion 赋值true
                        mEntityMap.forEach { _, value ->
                            if (value.region.contains(event.x, event.y)) {
                                mOnTableItemClickListener?.onItemClick(this, value)
                                value.isFocusRegion = true
                            } else {
                                value.isFocusRegion = false
                            }
                            invalidate()
                        }
                    }
                }


            }
        }
        return super.onTouchEvent(event)
    }


    interface OnTableItemClickListener {
        fun onItemClick(view: TableWidget, entity: TableEntity)
    }
}