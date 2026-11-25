package com.luna.assistant
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*
import kotlin.random.Random

// Lightweight animated anime-style background: gradient + glowing orb + falling sakura. No bitmaps, ~25fps.
class AuraView(c: Context, private val dark: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bg: Shader? = null
    private var t = 0f
    private val n = 16
    private val px = FloatArray(n) { Random.nextFloat() }
    private val py = FloatArray(n) { Random.nextFloat() }
    private val sp = FloatArray(n) { 0.3f + Random.nextFloat() }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cs = if (dark) intArrayOf(0xFF12082E.toInt(), 0xFF4B2A7A.toInt(), 0xFFE0559F.toInt())
        else intArrayOf(0xFFFFE3F1.toInt(), 0xFFD8C7FF.toInt(), 0xFFA5D8FF.toInt())
        bg = LinearGradient(0f, 0f, w * 0.4f, h.toFloat(), cs, null, Shader.TileMode.CLAMP)
    }
    override fun onDraw(cv: Canvas) {
        t += 0.03f
        p.shader = bg; cv.drawPaint(p); p.shader = null
        val cx = width / 2f; val cy = height * 0.2f
        val r = min(width, height) * 0.12f * (1f + 0.06f * sin(t * 2))
        for (k in 3 downTo 1) { p.color = Color.argb(35, 255, 190, 255); cv.drawCircle(cx, cy, r * (1f + 0.4f * k), p) }
        p.color = Color.argb(190, 255, 240, 255); cv.drawCircle(cx, cy, r, p)
        p.color = Color.argb(150, 255, 200, 230)
        for (i in 0 until n) {
            val y = ((py[i] + t * 0.03f * sp[i]) % 1f) * height
            val x = (px[i] + 0.03f * sin(t + i)) * width
            cv.drawCircle(x, y, 5f + 6f * sp[i], p)
        }
        postInvalidateDelayed(40)
    }
}
