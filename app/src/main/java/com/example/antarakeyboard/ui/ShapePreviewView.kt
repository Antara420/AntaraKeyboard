package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.antarakeyboard.model.KeyShape

class ShapePreviewView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {


    var shape: KeyShape = KeyShape.HEX
        set(value) {
            field = value
            invalidate()
        }


    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        style = Paint.Style.FILL
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (shape) {
            KeyShape.HEX -> drawHex(canvas)
            KeyShape.TRIANGLE -> drawTriangle(canvas)
        }
    }


    private fun drawHex(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w / 2
        val cx = w / 2
        val cy = h / 2


        val p = Path()
        for (i in 0..5) {
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        c.drawPath(p, paint)
    }


    private fun drawTriangle(c: Canvas) {
        val p = Path()
        p.moveTo(width / 2f, 0f)
        p.lineTo(0f, height.toFloat())
        p.lineTo(width.toFloat(), height.toFloat())
        p.close()
        c.drawPath(p, paint)
    }
}