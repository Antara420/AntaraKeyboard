package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
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
    var customBgColor: Int? = null
        set(value) { field = value; invalidate() }


    /**
     * Samo za TRIANGLE:
     * false = normalan trokut (vrh gore)
     * true  = obrnuti trokut (vrh dolje)
     */
    var triangleFlipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Ako želiš da key uvijek bude savršen kvadrat (preporučeno za hex) */
    var forceSquare: Boolean = true
        set(value) {
            field = value
            requestLayout()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.25f) // malo tanji rub, “zategnutije”
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)

        // Sitni trik: tekst ostane čitljiv i centriran
        isAllCaps = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!forceSquare) return

        val s = min(measuredWidth, measuredHeight)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 2f || h <= 2f) {
            super.onDraw(canvas)
            return
        }

        val pressed = isPressed

        // Boje (tamna baza + plava special)
        val bg = customBgColor ?: when {
            isSpecial -> if (pressed) 0xFF2A55FF.toInt() else 0xFF2E55E7.toInt()
            pressed -> 0xFF585858.toInt()
            else -> 0xFF3E3E3E.toInt()
        }

        val brd = 0xFF0F0F0F.toInt()

        fill.color = bg
        stroke.color = brd

        // Inset da stroke ne bude odrezan + minimalan “air”
        val inset = stroke.strokeWidth * 0.5f + dpF(0.75f)

        val l = inset
        val t = inset
        val r = w - inset
        val b = h - inset

        if (r <= l || b <= t) {
            super.onDraw(canvas)
            return
        }

        path.reset()
        when (shape) {
            KeyShape.HEX -> buildHex(path, l, t, r, b)
            KeyShape.TRIANGLE -> buildTriangle(path, l, t, r, b, triangleFlipped)
            KeyShape.CIRCLE -> buildCircle(path, l, t, r, b)
            KeyShape.CUBE -> buildCubeFront(path, l, t, r, b)
        }

        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)

        super.onDraw(canvas)
    }

    /** Pointy-top hex koji ispuni kvadrat maksimalno (kao na slici) */
    private fun buildHex(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val cx = l + w * 0.5f
        val cy = t + h * 0.5f

        // radius prema manjoj dimenziji, ali mrvicu “podebljan” (manje praznine)
        val radius = min(w, h) * 0.52f

        // 0.866 = sqrt(3)/2
        val dx = 0.8660254f * radius
        val dy = 0.5f * radius

        p.moveTo(cx, cy - radius)
        p.lineTo(cx + dx, cy - dy)
        p.lineTo(cx + dx, cy + dy)
        p.lineTo(cx, cy + radius)
        p.lineTo(cx - dx, cy + dy)
        p.lineTo(cx - dx, cy - dy)
        p.close()
    }

    private fun buildTriangle(p: Path, l: Float, t: Float, r: Float, b: Float, flipped: Boolean) {
        // ✅ bez insett-a da trokut bude maksimalan
        val left = l
        val top = t
        val right = r
        val bottom = b

        if (!flipped) {
            // vrh gore
            p.moveTo(left + (right - left) * 0.5f, top)
            p.lineTo(left, bottom)
            p.lineTo(right, bottom)
        } else {
            // vrh dolje
            p.moveTo(left, top)
            p.lineTo(right, top)
            p.lineTo(left + (right - left) * 0.5f, bottom)
        }
        p.close()
    }




    private fun buildCircle(p: Path, l: Float, t: Float, r: Float, b: Float) {
        p.addOval(l, t, r, b, Path.Direction.CW)
    }

    private fun buildCubeFront(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val pad = min(w, h) * 0.06f
        val rr = min(w, h) * 0.14f
        p.addRoundRect(l + pad, t + pad, r - pad, b - pad, rr, rr, Path.Direction.CW)
    }

    private fun dpF(v: Float): Float = v * resources.displayMetrics.density
}
