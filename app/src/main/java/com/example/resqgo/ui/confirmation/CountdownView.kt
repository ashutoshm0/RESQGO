package com.example.resqgo.ui.confirmation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.resqgo.R

class CountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 30f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 30f
        color = ContextCompat.getColor(context, R.color.white)
        alpha = 50
    }

    private val rectF = RectF()
    var progress: Float = 1f // 1f = 100%, 0f = 0%
        set(value) {
            field = value
            
            // Color shifts from Amber (warning) to Red (danger) as it approaches 0
            if (value > 0.5f) {
                paint.color = ContextCompat.getColor(context, R.color.resqgo_warning)
            } else {
                paint.color = ContextCompat.getColor(context, R.color.resqgo_alert)
            }
            
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = paint.strokeWidth / 2
        rectF.set(padding, padding, w - padding, h - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background track
        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)
        
        // Draw progress
        val sweepAngle = 360f * progress
        // Start from top (-90 degrees)
        canvas.drawArc(rectF, -90f, sweepAngle, false, paint)
    }
}
