package com.example.screentime

import android.content.Context
import android.graphics.Typeface

object Fonts {
    private var base: Typeface? = null

    private fun base(ctx: Context): Typeface {
        base?.let { return it }
        val t = try {
            ctx.resources.getFont(R.font.appfont)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        base = t
        return t
    }

    fun regular(ctx: Context): Typeface = Typeface.create(base(ctx), 400, false)
    fun medium(ctx: Context): Typeface = Typeface.create(base(ctx), 500, false)
    fun light(ctx: Context): Typeface = Typeface.create(base(ctx), 300, false)
}
