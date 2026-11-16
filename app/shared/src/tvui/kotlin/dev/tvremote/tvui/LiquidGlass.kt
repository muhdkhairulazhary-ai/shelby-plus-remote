package dev.tvremote.tvui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

/** Static, hardware-friendly frosted surfaces. No view capture or live blur on low-memory TVs. */
object LiquidGlass {
    private const val NAVY = 0xFF050C15.toInt()
    private const val NAVY_LIT = 0xFF0D1722.toInt()
    private const val LIGHT_BLUE = 0x997A9EC0.toInt()
    private const val LIGHT_WARM = 0x808D7569.toInt()
    private const val CLEAR = 0x00000000
    private const val FILL_TOP = 0xA0354351.toInt()
    private const val FILL_BOTTOM = 0x90152331.toInt()
    private const val EDGE = 0x85EAF2FF.toInt()
    private const val HIGHLIGHT = 0x38FFFFFF
    private const val BLUE_EDGE = 0xFF8AD8FF.toInt()
    private const val BLUE_HALO = 0x552EA5FF
    private const val EDGE_BRIGHT = 0xE0F1F6FF.toInt()
    private const val EDGE_DIM = 0x38C2D8EF

    fun atmosphere(): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shaders = emptyList<Shader>()
        override fun onBoundsChange(b: Rect) {
            val w = b.width().toFloat().coerceAtLeast(1f)
            val h = b.height().toFloat().coerceAtLeast(1f)
            shaders = listOf(
                LinearGradient(0f, 0f, w, h, intArrayOf(NAVY, NAVY_LIT, NAVY), null, Shader.TileMode.CLAMP),
                light(w * .28f, h * .06f, w * .25f, LIGHT_BLUE, .55f, 1.5f, -28f),
                light(w * .61f, h * .84f, w * .24f, LIGHT_BLUE, .45f, 1.3f, 24f),
                light(w * .07f, h * .82f, w * .24f, LIGHT_WARM, .45f, 1.2f, -24f)
            )
        }
        override fun draw(canvas: Canvas) { for (shader in shaders) { paint.shader = shader; canvas.drawRect(bounds, paint) } }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
    }

    private fun light(x: Float, y: Float, radius: Float, color: Int, sx: Float, sy: Float, angle: Float): Shader =
        RadialGradient(x, y, radius, color, CLEAR, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(sx, sy, x, y); postRotate(angle, x, y) })
        }

    /** Silver specular edge fades along the curve, rather than a flat Material card border. */
    private class Edge(c: Context, private val radius: Float, private val focused: Boolean) : Drawable() {
        private val width = TvUi.dp(c, 1)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width }
        private val rect = RectF()
        override fun onBoundsChange(b: Rect) {
            rect.set(b); rect.inset(width / 2, width / 2)
            paint.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat().coerceAtLeast(b.top + 1f),
                intArrayOf(if (focused) BLUE_EDGE else EDGE_BRIGHT, if (focused) BLUE_EDGE else EDGE_DIM, EDGE),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        }
        override fun draw(canvas: Canvas) = canvas.drawRoundRect(rect, radius, radius, paint)
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    fun surface(c: Context, radius: Int = 22, focused: Boolean = false): Drawable {
        val r = TvUi.dp(c, radius)
        val fill = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(FILL_TOP, FILL_BOTTOM)).apply {
            cornerRadius = r
        }
        val shine = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(HIGHLIGHT, CLEAR, CLEAR)).apply { cornerRadius = r }
        val halo = GradientDrawable().apply {
            cornerRadius = r + TvUi.dp(c, 2); setColor(CLEAR)
            setStroke(TvUi.dp(c, 4).toInt(), if (focused) BLUE_HALO else CLEAR)
        }
        return LayerDrawable(arrayOf(halo, fill, shine, Edge(c, r, focused))).apply {
            val inset = TvUi.dp(c, 2).toInt()
            for (i in 1..3) setLayerInset(i, inset, inset, inset, inset)
        }
    }

    /** A clear reflection for colorful tiles; no tinted fill obscures their original artwork. */
    fun reflection(c: Context, radius: Float = TvUi.R_TILE): Drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(HIGHLIGHT, CLEAR, CLEAR)).apply {
        cornerRadius = TvUi.dp(c, radius); setStroke(TvUi.dp(c, 1).toInt().coerceAtLeast(1), EDGE)
    }

    fun states(c: Context, radius: Int = 22) = StateListDrawable().apply {
        val fade = if (Motion.on && !Motion.calm) 120 else 0
        setEnterFadeDuration(fade); setExitFadeDuration(fade)
        addState(intArrayOf(android.R.attr.state_focused), surface(c, radius, true))
        addState(intArrayOf(android.R.attr.state_pressed), surface(c, radius, true))
        addState(intArrayOf(), surface(c, radius))
    }

    /** No scaling: labels and adjacent controls never overlap when a D-pad focus moves. */
    fun focus(v: View, focused: Boolean) {
        v.animate().cancel()
        val z = if (focused) TvUi.dp(v.context, 3) else 0f
        if (!Motion.on || Motion.calm) v.translationZ = z
        else v.animate().translationZ(z).setDuration(120).start()
    }
}
