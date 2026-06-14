// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.overlay

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.features.input.model.InputOverrider

class InputOverlayPointer(
    surfacePosition: Rect,
    private val doubleTapControl: Int,
	private val doubleTapHoldControl: Int,
	private val singleTapControl: Int,
	private val singleTapHoldControl: Int,
	private val secondFingerTapControl: Int,
    private val secondFingerHoldControl: Int,
    private var mode: Int,
    private var recenter: Boolean,
    private val controllerIndex: Int
) {
    var x = 0.0f
    var y = 0.0f
    private var oldX = 0.0f
    private var oldY = 0.0f

    private val gameCenterX: Float
    private val gameCenterY: Float
    private val gameWidthHalfInv: Float
    private val gameHeightHalfInv: Float

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var doubleTap = false
	private var doubleTapHolding = false
    private var trackId = -1
	private var secondTrackId = -1

    init {
        gameCenterX = (surfacePosition.left + surfacePosition.right) / 2f
        gameCenterY = (surfacePosition.top + surfacePosition.bottom) / 2f

        var gameWidth = (surfacePosition.right - surfacePosition.left).toFloat()
        var gameHeight = (surfacePosition.bottom - surfacePosition.top).toFloat()

        // Adjusting for device's black bars.
        val surfaceAR = gameWidth / gameHeight
        val gameAR = NativeLibrary.GetGameAspectRatio()
        if (gameAR <= surfaceAR) {
            // Black bars on left/right
            gameWidth = gameHeight * gameAR
        } else {
            // Black bars on top/bottom
            gameHeight = gameWidth / gameAR
        }

        gameWidthHalfInv = 1f / (gameWidth * 0.5f)
        gameHeightHalfInv = 1f / (gameHeight * 0.5f)
    }

    fun onTouch(event: MotionEvent) {
        val action = event.actionMasked
        val firstPointer = action != MotionEvent.ACTION_POINTER_DOWN &&
            action != MotionEvent.ACTION_POINTER_UP
        val pointerIndex = if (firstPointer) 0 else event.actionIndex

        when (action) {
			MotionEvent.ACTION_DOWN,
			MotionEvent.ACTION_POINTER_DOWN -> {
				if (trackId == -1) {
					trackId = event.getPointerId(pointerIndex)
					touchStartX = event.getX(pointerIndex)
					touchStartY = event.getY(pointerIndex)
					touchPress()
				} else if (secondTrackId == -1) {
					// second finger
					secondTrackId = event.getPointerId(pointerIndex)
					secondFingerPress()
				}
			}

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(pointerIndex)
                if (trackId == pointerId) {
                    trackId = -1
                    if (singleTapHoldControl != SINGLE_TAP_NONE) {
                        InputOverrider.setControlState(controllerIndex, singleTapHoldControl, 0.0)
                    }
                    if (doubleTapHolding && doubleTapHoldControl != SINGLE_TAP_NONE) {
                        doubleTapHolding = false
                        InputOverrider.setControlState(controllerIndex, doubleTapHoldControl, 0.0)
                    }
                    if (mode == MODE_DRAG)
                        updateOldAxes()
                    if (recenter)
                        reset()
                } else if (secondTrackId == pointerId) {
                    // second finger release
                    secondTrackId = -1
                    secondFingerRelease()
                }
            }
        }

        val eventPointerIndex = event.findPointerIndex(trackId)
        if (trackId == -1 || eventPointerIndex == -1)
            return

        if (mode == MODE_FOLLOW) {
            x = (event.getX(eventPointerIndex) - gameCenterX) * gameWidthHalfInv
            y = (event.getY(eventPointerIndex) - gameCenterY) * gameHeightHalfInv
        } else if (mode == MODE_DRAG) {
            x = oldX + (event.getX(eventPointerIndex) - touchStartX) * gameWidthHalfInv
            y = oldY + (event.getY(eventPointerIndex) - touchStartY) * gameHeightHalfInv
        }
    }

    private fun touchPress() {
        if (mode != MODE_DISABLED) {
            if (singleTapControl != SINGLE_TAP_NONE) {
                InputOverrider.setControlState(controllerIndex, singleTapControl, 1.0)
                Handler(Looper.myLooper()!!).postDelayed({
                    InputOverrider.setControlState(controllerIndex, singleTapControl, 0.0)
                }, 50)
            }

            if (singleTapHoldControl != SINGLE_TAP_NONE) {
                InputOverrider.setControlState(controllerIndex, singleTapHoldControl, 1.0)
            }

            if (doubleTap) {
				if (doubleTapControl != SINGLE_TAP_NONE) {
                    InputOverrider.setControlState(controllerIndex, doubleTapControl, 1.0)
                    Handler(Looper.myLooper()!!).postDelayed({
                        InputOverrider.setControlState(controllerIndex, doubleTapControl, 0.0)
                    }, 50)
                }
                if (doubleTapHoldControl != SINGLE_TAP_NONE) {
                    doubleTapHolding = true
                    InputOverrider.setControlState(controllerIndex, doubleTapHoldControl, 1.0)
                }
            } else {
                doubleTap = true
                Handler(Looper.myLooper()!!).postDelayed({ doubleTap = false }, 300)
            }
        }
    }
    private fun secondFingerPress() {
        if (secondFingerTapControl != SINGLE_TAP_NONE) {
            InputOverrider.setControlState(controllerIndex, secondFingerTapControl, 1.0)
            Handler(Looper.myLooper()!!).postDelayed({
                InputOverrider.setControlState(controllerIndex, secondFingerTapControl, 0.0)
            }, 50)
        }
        if (secondFingerHoldControl != SINGLE_TAP_NONE) {
            InputOverrider.setControlState(controllerIndex, secondFingerHoldControl, 1.0)
        }
    }

    private fun secondFingerRelease() {
        if (secondFingerHoldControl != SINGLE_TAP_NONE) {
            InputOverrider.setControlState(controllerIndex, secondFingerHoldControl, 0.0)
        }
    }
    private fun updateOldAxes() {
        oldX = x
        oldY = y
    }

    private fun reset() {
        oldY = 0.0f
        oldX = 0.0f
        y = 0.0f
        x = 0.0f
    }

    fun setMode(mode: Int) {
        this.mode = mode
        if (mode == MODE_DRAG)
            updateOldAxes()
    }

    fun setRecenter(recenter: Boolean) {
        this.recenter = recenter
    }

    companion object {
        const val MODE_DISABLED = 0
        const val MODE_FOLLOW = 1
        const val MODE_DRAG = 2
		const val SINGLE_TAP_NONE = -1

		@JvmField
		var SINGLE_TAP_OPTIONS = arrayListOf(
			-1,  // None
			NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
			NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
			NativeLibrary.ButtonType.WIIMOTE_BUTTON_2,
			NativeLibrary.ButtonType.CLASSIC_BUTTON_A
		)

		@JvmField
		var SINGLE_TAP_HOLD_OPTIONS = arrayListOf(
			-1,  // None
			NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
			NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
			NativeLibrary.ButtonType.WIIMOTE_BUTTON_2,
			NativeLibrary.ButtonType.CLASSIC_BUTTON_A
		)

        @JvmField
        var DOUBLE_TAP_OPTIONS = arrayListOf(
			-1,  // None
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_2,
            NativeLibrary.ButtonType.CLASSIC_BUTTON_A
        )

		@JvmField
        var DOUBLE_TAP_HOLD_OPTIONS = arrayListOf(
            -1,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_2,
            NativeLibrary.ButtonType.CLASSIC_BUTTON_A
        )

        @JvmField
        var SECOND_FINGER_TAP_OPTIONS = arrayListOf(
            -1,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_2,
            NativeLibrary.ButtonType.CLASSIC_BUTTON_A
        )

        @JvmField
        var SECOND_FINGER_HOLD_OPTIONS = arrayListOf(
            -1,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
            NativeLibrary.ButtonType.WIIMOTE_BUTTON_2,
            NativeLibrary.ButtonType.CLASSIC_BUTTON_A
        )
    }
}
