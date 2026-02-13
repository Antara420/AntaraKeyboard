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
            KeyShape.CIRCLE -> drawCircle(canvas)
            KeyShape.CUBE -> drawCube(canvas)
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

    private fun drawCircle(c: Canvas) {
        val r = (minOf(width, height) * 0.45f)
        c.drawCircle(width / 2f, height / 2f, r, paint)
    }

    private fun drawCube(c: Canvas) {
        // "kocka" kao 3D cube (dvije plohe + spojnice)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = minOf(w, h) * 0.15f
        val dx = pad * 0.6f
        val dy = pad * 0.6f

        val front = Path().apply {
            moveTo(pad, pad + dy)
            lineTo(w - pad - dx, pad + dy)
            lineTo(w - pad - dx, h - pad)
            lineTo(pad, h - pad)
            close()
        }

        val top = Path().apply {
            moveTo(pad, pad + dy)
            lineTo(pad + dx, pad)
            lineTo(w - pad, pad)
            lineTo(w - pad - dx, pad + dy)
            close()
        }

        val side = Path().apply {
            moveTo(w - pad - dx, pad + dy)
            lineTo(w - pad, pad)
            lineTo(w - pad, h - pad - dy)
            lineTo(w - pad - dx, h - pad)
            close()
        }

        c.drawPath(front, paint)
        c.drawPath(top, paint)
        c.drawPath(side, paint)
    }

}


