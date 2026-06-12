// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent

/**
 * Custom [BitmapDrawable] that is capable
 * of storing it's own ID.
 *
 * @param res                [Resources] instance.
 * @param defaultStateBitmap [Bitmap] to use with the default state Drawable.
 * @param pressedStateBitmap [Bitmap] to use with the pressed state Drawable.
 * @param legacyId           Legacy identifier (ButtonType) for this type of button.
 * @param control            Control ID for this type of button.
 * @param latching           Whether this button is latching.
 */
class InputOverlayDrawableButton(
    res: Resources,
    defaultStateBitmap: Bitmap,
    pressedStateBitmap: Bitmap,
    val legacyId: Int,
    val control: Int,
    var latching: Boolean,
    val overlayLabel: String? = null,
    val overlayLabelScale: Float = 0.28f,
    val isAnalogOnly: Boolean = false,
    val analogPressValue: Double = 1.0
) {
    var trackId: Int = -1
    var useAlphaHitTest: Boolean = false
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var controlPositionX = 0
    private var controlPositionY = 0
    val width: Int
    val height: Int
    private val defaultStateBitmap: BitmapDrawable
    private val pressedStateBitmap: BitmapDrawable
    private var pressedState = false

    init {
        this.defaultStateBitmap = BitmapDrawable(res, defaultStateBitmap)
        this.pressedStateBitmap = BitmapDrawable(res, pressedStateBitmap)
        width = this.defaultStateBitmap.intrinsicWidth
        height = this.defaultStateBitmap.intrinsicHeight
    }

    fun onConfigureTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previousTouchX = event.x.toInt()
                previousTouchY = event.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                controlPositionX += event.x.toInt() - previousTouchX
                controlPositionY += event.y.toInt() - previousTouchY
                setBounds(
                    controlPositionX,
                    controlPositionY,
                    width + controlPositionX,
                    height + controlPositionY
                )
                previousTouchX = event.x.toInt()
                previousTouchY = event.y.toInt()
            }
        }
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    fun draw(canvas: Canvas) {
        currentStateBitmapDrawable.draw(canvas)
        overlayLabel?.let { drawLabel(canvas, it, bounds) }
    }

    private fun drawLabel(canvas: Canvas, label: String, bounds: Rect) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (pressedState) Color.parseColor("#E6FFFFFF") else Color.BLACK
        textSize = bounds.width() * 0.30f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    canvas.drawText(
        label,
        bounds.exactCenterX(),
        bounds.exactCenterY() + paint.textSize / 3f,
        paint
    )
}

    private val currentStateBitmapDrawable: BitmapDrawable
        get() = if (pressedState) pressedStateBitmap else defaultStateBitmap

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        defaultStateBitmap.setBounds(left, top, right, bottom)
        pressedStateBitmap.setBounds(left, top, right, bottom)
    }

    fun setOpacity(value: Int) {
        defaultStateBitmap.alpha = value
        pressedStateBitmap.alpha = value
    }

    val bounds: Rect
        get() = defaultStateBitmap.bounds

    /**
     * Returns true only if the pixel at (x, y) has alpha > threshold.
     * Falls back to bounds check if useAlphaHitTest is false.
     */
    fun hitTest(x: Int, y: Int, useAlphaHitTest: Boolean = false): Boolean {
        if (!bounds.contains(x, y)) return false
        if (!useAlphaHitTest) return true

        val bitmap = defaultStateBitmap.bitmap ?: return true
        val bx = ((x - bounds.left).toFloat() / bounds.width() * bitmap.width).toInt()
            .coerceIn(0, bitmap.width - 1)
        val by = ((y - bounds.top).toFloat() / bounds.height() * bitmap.height).toInt()
            .coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(bx, by)
        return (pixel ushr 24) > 10  // alpha threshold
    }

    fun setPressedState(isPressed: Boolean) {
        pressedState = isPressed
    }

    fun getPressedState(): Boolean {
        return pressedState;
    }
}
