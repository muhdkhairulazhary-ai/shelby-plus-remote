package dev.tvremote.tvui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

/** Static, hardware-friendly frosted surfaces. No view capture or live blur on low-memory TVs. */
object LiquidGlass {
    private const val NAVY = 0xFF050C15.toInt()
    private const val NAVY_LIT = 0xFF132A3C.toInt()
    private const val LIGHT_BLUE = 0x655685B0
    private const val LIGHT_WARM = 0x427C6558
    private const val CLEAR = 0x00000000
    private const val FILL_TOP = 0xCC354351.toInt()
    private const val FILL_BOTTOM = 0xC0152331.toInt()
    private const val EDGE = 0x85EAF2FF.toInt()
    private const val HIGHLIGHT = 0x43FFFFFF
    private const val BLUE_EDGE = 0xFF8AD8FF.toInt()
    private const val BLUE_HALO = 0x552EA5FF

    fun atmosphere(): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shaders = emptyList<Shader>()
        override fun onBoundsChange(b: Rect) {
            val w = b.width().toFloat().coerceAtLeast(1f)
            val h = b.height().toFloat().coerceAtLeast(1f)
            shaders = listOf(
                LinearGradient(0f, 0f, w, h, intArrayOf(NAVY, NAVY_LIT, NAVY), null, Shader.TileMode.CLAMP),
                RadialGradient(w * .26f, h * .1f, w * .34f, LIGHT_BLUE, CLEAR, Shader.TileMode.CLAMP),
                RadialGradient(w * .66f, h * .82f, w * .32f, LIGHT_BLUE, CLEAR, Shader.TileMode.CLAMP),
                RadialGradient(w * .08f, h * .86f, w * .24f, LIGHT_WARM, CLEAR, Shader.TileMode.CLAMP)
            )
        }
        override fun draw(canvas: Canvas) { for (shader in shaders) { paint.shader = shader; canvas.drawRect(bounds, paint) } }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
    }

    fun surface(c: Context, radius: Int = 22, focused: Boolean = false): Drawable {
        val r = TvUi.dp(c, radius)
        val fill = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(FILL_TOP, FILL_BOTTOM)).apply {
            cornerRadius = r; setStroke(TvUi.dp(c, 1).toInt().coerceAtLeast(1), if (focused) BLUE_EDGE else EDGE)
        }
        val shine = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(HIGHLIGHT, CLEAR, CLEAR)).apply { cornerRadius = r }
        val halo = GradientDrawable().apply {
            cornerRadius = r + TvUi.dp(c, 2); setColor(CLEAR)
            setStroke(TvUi.dp(c, 4).toInt(), if (focused) BLUE_HALO else CLEAR)
        }
        return LayerDrawable(arrayOf(halo, fill, shine)).apply {
            val inset = TvUi.dp(c, 2).toInt()
            setLayerInset(1, inset, inset, inset, inset); setLayerInset(2, inset, inset, inset, inset)
        }
    }

    /** A clear reflection for colorful tiles; no tinted fill obscures their original artwork. */
    fun reflection(c: Context): Drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(HIGHLIGHT, CLEAR, CLEAR)).apply {
        cornerRadius = TvUi.dp(c, TvUi.R_TILE); setStroke(TvUi.dp(c, 1).toInt().coerceAtLeast(1), EDGE)
    }

    fun states(c: Context, radius: Int = 22) = StateListDrawable().apply {
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
