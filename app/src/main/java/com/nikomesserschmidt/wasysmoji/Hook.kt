package com.nikomesserschmidt.wasysmoji

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.content.res.Resources
import android.widget.ImageView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Modifier

/** Redirects WhatsApp emoji rendering to Android's system emoji font. */
class Hook : IXposedHookLoadPackage {
    companion object {
        private const val WA_PACKAGE = "com.whatsapp"
        private const val EMOJI_MANAGER_CLASS = "X.1Bj"
        private const val ANIMATED_EMOJI_PROVIDER_CLASS = "X.EyK"
        private const val SINGLE_EMOJI_VIEW_CLASS = "X.LIv"
    }

    override fun handleLoadPackage(lp: XC_LoadPackage.LoadPackageParam) {
        if (lp.packageName != WA_PACKAGE) return
        try {
            hookEmojiManager(lp.classLoader)
            hookSingleEmojiView(lp.classLoader)
            hookAnimatedProvider(lp.classLoader)
            XposedBridge.log("BlobmojiWA: system emoji hooks installed")
        } catch (t: Throwable) {
            XposedBridge.log("BlobmojiWA: hook failed – ${t.message}")
            XposedBridge.log(t)
        }
    }

    private fun hookEmojiManager(classLoader: ClassLoader) {
        val clazz = XposedHelpers.findClass(EMOJI_MANAGER_CLASS, classLoader)
        var count = 0
        for (method in clazz.declaredMethods) {
            if (!Modifier.isPublic(method.modifiers) || Modifier.isStatic(method.modifiers)) continue
            if (method.returnType != BitmapDrawable::class.java) continue
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (method.name == "A03" || method.name == "A05") {
                        val stack = Thread.currentThread().stackTrace
                        val single = stack.any {
                            it.className == "X.LIv" || it.className.endsWith("C48094LIv")
                        }
                        if (single) {
                            XposedBridge.log(
                                "BlobmojiWA: single ${method.name} args=${describeArgs(param.args)} " +
                                    "caller=${stack.firstOrNull { it.className.contains("LIv") }}"
                            )
                        }
                    }
                    // Preserve Unicode in chat text so Android renders it
                    // directly instead of installing a clipping ImageSpan.
                    if (method.name == "A05" && isInlineChatStack()) {
                        param.result = null
                        return
                    }
                    val codePoints = findCodePoints(param.args) ?: return
                    val resources = param.args.firstOrNull { it is Resources } as? Resources
                        ?: return
                    val drawable = makeSystemEmojiDrawable(
                        resources,
                        codePoints,
                        standalone = method.name == "A05" && singleEmojiStack()
                    )
                    if (drawable != null) {
                        param.result = drawable
                        if (method.name == "A05" && singleEmojiStack()) {
                            val bitmap = drawable.bitmap
                            XposedBridge.log(
                                "BlobmojiWA: single drawable bitmap=${bitmap.width}x${bitmap.height} " +
                                    "intrinsic=${drawable.intrinsicWidth}x${drawable.intrinsicHeight} " +
                                    "bounds=${drawable.bounds}"
                            )
                        }
                    }
                }
            })
            XposedBridge.log("BlobmojiWA: replaced ${method.name}")
            count++
        }
        XposedBridge.log("BlobmojiWA: replaced $count emoji bitmap methods")
    }

    private fun hookSingleEmojiView(classLoader: ClassLoader) {
        val clazz = XposedHelpers.findClass(SINGLE_EMOJI_VIEW_CLASS, classLoader)
        for (method in clazz.declaredMethods) {
            if (method.name != "setImageView") continue
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val image = XposedHelpers.getObjectField(param.thisObject, "A00")
                    if (image is ImageView) {
                        image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        image.adjustViewBounds = false
                        val drawable = image.drawable
                        if (drawable != null) {
                            drawable.setBounds(0, 0, image.width, image.height)
                            drawable.invalidateSelf()
                        }
                        XposedBridge.log(
                            "BlobmojiWA: standalone ImageView size=" +
                                image.width + "x" + image.height +
                                " drawable=" + drawable?.intrinsicWidth + "x" + drawable?.intrinsicHeight
                        )
                    }
                }
            })
            XposedBridge.log("BlobmojiWA: hooked standalone ${method.name}")
        }
    }

    /** Disable WhatsApp's animated emoji assets so the static system glyph is used. */
    private fun hookAnimatedProvider(classLoader: ClassLoader) {
        val clazz = XposedHelpers.findClass(ANIMATED_EMOJI_PROVIDER_CLASS, classLoader)
        var count = 0
        for (method in clazz.declaredMethods) {
            if (method.name != "A01" && method.name != "A02") continue
            if (Modifier.isStatic(method.modifiers)) continue
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
            XposedBridge.log("BlobmojiWA: disabled animated emoji ${method.name}")
            count++
        }
        XposedBridge.log("BlobmojiWA: disabled $count animated emoji provider methods")
    }

    private fun isInlineChatStack(): Boolean {
        return Thread.currentThread().stackTrace.any {
            it.className == "X.1g7" ||
                it.className.endsWith("AbstractC34851g7")
        }
    }

    private fun singleEmojiStack(): Boolean {
        return Thread.currentThread().stackTrace.any {
            it.className == "X.LIv" || it.className.endsWith("C48094LIv")
        }
    }

    private fun describeArgs(args: Array<out Any?>): String {
        return args.joinToString(prefix = "[", postfix = "]") { arg ->
            when (arg) {
                null -> "null"
                is Resources -> "Resources"
                is Long -> "Long($arg)"
                is Boolean -> "Boolean($arg)"
                is Number -> "${arg.javaClass.simpleName}($arg)"
                else -> {
                    val details = StringBuilder(arg.javaClass.name)
                    try {
                        val value = XposedHelpers.callMethod(arg, "A01")
                        if (value is IntArray) {
                            details.append(" cp=")
                                .append(value.joinToString(",", prefix = "[", postfix = "]"))
                        }
                    } catch (_: Throwable) {
                    }
                    details.toString()
                }
            }
        }
    }

    private fun findCodePoints(args: Array<out Any?>): IntArray? {
        for (arg in args) {
            if (arg == null || arg is Resources || arg is Number || arg is Boolean) continue
            try {
                // A02 is the full message and A00 is the current UTF-16
                // offset. Extract only the current emoji sequence.
                val original = XposedHelpers.getObjectField(arg, "A02")
                val start = XposedHelpers.getIntField(arg, "A00")
                if (original is CharSequence && start >= 0 && start < original.length) {
                    val key = args.filterIsInstance<Long>().firstOrNull()
                    // The low nibble stores the sequence length in code points;
                    // variation selector 16 does not count as another glyph.
                    var length = Character.charCount(Character.codePointAt(original, start))
                    if (key != null) {
                        val count = (key and 15L).toInt()
                        var seen = 0
                        var pos = start
                        while (seen < count && pos < original.length) {
                            val cp = Character.codePointAt(original, pos)
                            pos += Character.charCount(cp)
                            if (cp != 0xFE0F) seen++
                        }
                        if (pos < original.length && Character.codePointAt(original, pos) == 0xFE0F) {
                            pos += Character.charCount(0xFE0F)
                        }
                        length = (pos - start).coerceAtLeast(length)
                    }
                    val end = (start + length).coerceAtMost(original.length)
                    return original.subSequence(start, end).toString()
                        .codePoints()
                        .toArray()
                        .filterNot { it in 0xD800..0xDFFF }
                        .toIntArray()
                }
            } catch (_: Throwable) {
                // Try the next argument.
            }
            try {
                val value = XposedHelpers.callMethod(arg, "A01")
                if (value is IntArray && value.isNotEmpty()) {
                    return value.filterNot { it in 0xD800..0xDFFF }.toIntArray()
                }
            } catch (_: Throwable) {
                // Try the next argument.
            }
        }
        return null
    }

    private fun makeSystemEmojiDrawable(
        resources: Resources,
        codePoints: IntArray,
        standalone: Boolean = false
    ): BitmapDrawable? {
        val text = buildString {
            for (codePoint in codePoints) {
                if (Character.isValidCodePoint(codePoint) &&
                    codePoint !in 0xD800..0xDFFF
                ) appendCodePoint(codePoint)
            }
        }
        if (text.isEmpty()) return null

        // The standalone message view needs a larger raster than picker icons.
        val size = if (standalone) 512 else 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        // Use the system typeface so Android selects its color emoji font.
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        // Fit wide joined sequences inside the raster without clipping.
        paint.textAlign = Paint.Align.CENTER
        var textSize = size * 0.98f
        paint.textSize = textSize
        val maxWidth = size * 0.96f
        val measuredWidth = paint.measureText(text)
        if (measuredWidth > maxWidth && measuredWidth > 0f) {
            textSize *= maxWidth / measuredWidth
            paint.textSize = textSize
        }
        val metrics = paint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, size / 2f, baseline, paint)

        // Remove transparent margins before returning the drawable.
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if ((pixels[y * size + x] ushr 24) != 0) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        val margin = if (standalone) 4 else 2
        left = (left - margin).coerceAtLeast(0)
        top = (top - margin).coerceAtLeast(0)
        right = (right + margin).coerceAtMost(size - 1)
        bottom = (bottom + margin).coerceAtMost(size - 1)
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        if (cropped !== bitmap) bitmap.recycle()

        // WhatsApp uses square drawable bounds; keep the source square too.
        val side = maxOf(cropped.width, cropped.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val squareCanvas = Canvas(square)
        squareCanvas.drawBitmap(
            cropped,
            ((side - cropped.width) / 2f),
            ((side - cropped.height) / 2f),
            null
        )
        cropped.recycle()
        return BitmapDrawable(resources, square)
    }
}
