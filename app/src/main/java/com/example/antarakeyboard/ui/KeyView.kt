package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.example.antarakeyboard.R
import com.example.antarakeyboard.model.KeyShape
import kotlin.math.min

class KeyView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(ctx, attrs) {

    var shape: KeyShape = KeyShape.HEX
        set(value) { field = value; invalidate() }

    var isSpecial: Boolean = false
        set(value) {
            field = value
            applyTextColor()   // ✅ special text color
            invalidate()
        }

    /** Ako nije null, pregazi normal/special fill */
    var customBgColor: Int? = null
        set(value) { field = value; invalidate() }

    /** Samo za TRIANGLE */
    var triangleFlipped: Boolean = false
        set(value) { field = value; invalidate() }

    /** Ako želiš da key bude kvadrat (hex najbolje izgleda) */
    var forceSquare: Boolean = true
        set(value) { field = value; requestLayout() }

    var hideCompletely: Boolean = false
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.25f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private fun applyThemeColors() {
        // boje iz teme (Light/Dark koje ti biraš u serviceu)
        fill.color = themeColor(R.attr.keyFill, 0xFF777777.toInt())
        stroke.color = themeColor(R.attr.keyStroke, 0xFF222222.toInt())
        // text se rješava u applyTextColor()
    }
    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        val ok = context.theme.resolveAttribute(attr, tv, true)
        return if (ok && tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            tv.data
        } else fallback
    }
    private val path = Path()

    init {
        applyThemeColors()
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        isAllCaps = false
        applyTextColor()
    }

    /** Bitno: da pressed state odmah precrta fill */
    override fun setPressed(pressed: Boolean) {
        val changed = pressed != isPressed
        super.setPressed(pressed)
        if (changed) invalidate()
    }

    /** Ako se text promijeni (npr. enter ikona), boja ostane ok */
    private fun applyTextColor() {
        val c = if (isSpecial) {
            themeColor(R.attr.enterText, 0xFFFFFFFF.toInt())
        } else {
            themeColor(R.attr.keyText, 0xFFFFFFFF.toInt())
        }
        setTextColor(c)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!forceSquare) return
        val s = min(measuredWidth, measuredHeight)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        if (hideCompletely) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 2f || h <= 2f) {
            super.onDraw(canvas)
            return
        }

        val pressed = isPressed

        val keyFill = themeColor(R.attr.keyFill, 0xFF777777.toInt())
        val keyFillPressed = themeColor(R.attr.keyFillPressed, keyFill)
        val keyStroke = themeColor(R.attr.keyStroke, 0xFF222222.toInt())

        val specialFill = themeColor(R.attr.enterFill, 0xFF2E55E7.toInt())
        val specialFillPressed = themeColor(R.attr.enterFillPressed, specialFill)

        val bg = customBgColor ?: when {
            isSpecial -> if (pressed) specialFillPressed else specialFill
            pressed -> keyFillPressed
            else -> keyFill
        }

        fill.color = bg
        stroke.color = keyStroke

        // inset da stroke ne bude odrezan
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

    /** Pointy-top hex koji ispuni kvadrat maksimalno */
    private fun buildHex(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val cx = l + w * 0.5f
        val cy = t + h * 0.5f

        val radius = min(w, h) * 0.52f
        val dx = 0.8660254f * radius // sqrt(3)/2
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
        if (!flipped) {
            // vrh gore
            p.moveTo(l + (r - l) * 0.5f, t)
            p.lineTo(l, b)
            p.lineTo(r, b)
        } else {
            // vrh dolje
            p.moveTo(l, t)
            p.lineTo(r, t)
            p.lineTo(l + (r - l) * 0.5f, b)
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