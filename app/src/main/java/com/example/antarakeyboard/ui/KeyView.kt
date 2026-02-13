package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.example.antarakeyboard.model.KeyShape
import kotlin.math.min

class KeyView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(ctx, attrs) {

    var shape: KeyShape = KeyShape.HEX
        set(value) {
            field = value
            invalidate()
        }

    var isSpecial: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }

    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 2f || h <= 2f) {
            super.onDraw(canvas)
            return
        }

        val pressed = isPressed

        // boje (približno kao desna slika)
        val bg = when {
            isSpecial -> if (pressed) 0xFF2F5BFF.toInt() else 0xFF2E55E7.toInt() // plavo za specijalne (npr. enter)
            pressed -> 0xFF5A5A5A.toInt()
            else -> 0xFF3E3E3E.toInt()
        }
        val brd = 0xFF0F0F0F.toInt()

        fill.color = bg
        stroke.color = brd

        path.reset()
        when (shape) {
            KeyShape.HEX -> buildHex(path, w, h)
            KeyShape.TRIANGLE -> buildTriangle(path, w, h)
            KeyShape.CIRCLE -> buildCircle(path, w, h)
            KeyShape.CUBE -> buildCubeFront(path, w, h) // jednostavna "kocka" kao kvadrat (možemo kasnije 3D)
        }

        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)

        super.onDraw(canvas)
    }

    private fun buildHex(p: Path, w: Float, h: Float) {
        val r = min(w, h) * 0.5f
        val cx = w * 0.5f
        val cy = h * 0.5f
        // “flat-top” hex, izgleda kao na slici
        val dx = r * 0.866f // cos(30)
        val dy = r * 0.5f

        p.moveTo(cx - dx, cy)
        p.lineTo(cx - dx/2f, cy - dy)
        p.lineTo(cx + dx/2f, cy - dy)
        p.lineTo(cx + dx, cy)
        p.lineTo(cx + dx/2f, cy + dy)
        p.lineTo(cx - dx/2f, cy + dy)
        p.close()
    }

    private fun buildTriangle(p: Path, w: Float, h: Float) {
        p.moveTo(w * 0.5f, h * 0.1f)
        p.lineTo(w * 0.1f, h * 0.9f)
        p.lineTo(w * 0.9f, h * 0.9f)
        p.close()
    }

    private fun buildCircle(p: Path, w: Float, h: Float) {
        // Path za krug: aproksimiraj kao oval
        p.addOval(0f, 0f, w, h, Path.Direction.CW)
    }

    private fun buildCubeFront(p: Path, w: Float, h: Float) {
        // za sad: kvadrat/pravokutnik kao “kocka” front face
        val pad = min(w, h) * 0.12f
        p.addRoundRect(pad, pad, w - pad, h - pad, pad, pad, Path.Direction.CW)
    }
}
