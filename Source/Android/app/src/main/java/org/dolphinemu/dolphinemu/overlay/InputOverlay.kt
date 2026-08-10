// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.overlay

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.dolphinemu.dolphinemu.DolphinApplication
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.NativeLibrary.ButtonType
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.input.model.InputMappingBooleanSetting
import org.dolphinemu.dolphinemu.features.input.model.InputOverrider
import org.dolphinemu.dolphinemu.features.input.model.InputOverrider.ControlId
import org.dolphinemu.dolphinemu.features.input.model.controlleremu.EmulatedController
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting.Companion.getSettingForSIDevice
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting.Companion.getSettingForWiimoteSource
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import java.util.Arrays

/**
 * Draws the interactive input overlay on top of the
 * [SurfaceView] that is rendering emulation.
 *
 * @param context The current [Context].
 * @param attrs   [AttributeSet] for parsing XML attributes.
 */
class InputOverlay(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs),
    OnTouchListener {

    private val overlayButtons: MutableSet<InputOverlayDrawableButton> = HashSet()
    private val overlayDpads: MutableSet<InputOverlayDrawableDpad> = HashSet()
    private val overlayJoysticks: MutableSet<InputOverlayDrawableJoystick> = HashSet()
    private var overlayPointer: InputOverlayPointer? = null

    private var surfacePosition: Rect? = null

    private var isFirstRun = true
    private val gcPadRegistered = BooleanArray(4)
    private val wiimoteRegistered = BooleanArray(4)
    var editMode = false
    private var controllerType = -1
    private var controllerIndex = 0
    private var buttonBeingConfigured: InputOverlayDrawableButton? = null
    private var dpadBeingConfigured: InputOverlayDrawableDpad? = null
    private var joystickBeingConfigured: InputOverlayDrawableJoystick? = null

    // For keep-first-touched behavior
    private var keepFirstTouchedButton: InputOverlayDrawableButton? = null
    private var keepFirstTouchedPointer: Int = -1
    // Defer overlay rebuilds when we're iterating input collections to avoid
    // ConcurrentModificationException from modifying overlayButtons while
    // handling touch events.
    private var pendingRefreshControls: Boolean = false

    private val vibrator: Vibrator? by lazy {
        val ctx = context ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager?
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }
    }

    private val preferences: SharedPreferences
        get() =
            PreferenceManager.getDefaultSharedPreferences(DolphinApplication.getAppContext())

    private fun overlayHapticFeedbackEnabled(): Boolean {
        val gameId = NativeLibrary.GetCurrentGameID()
        return if (gameId != null)
            preferences.getBoolean(
                "OverlayHapticFeedback_$gameId",
                BooleanSetting.MAIN_OVERLAY_HAPTIC_FEEDBACK.boolean
            )
        else
            BooleanSetting.MAIN_OVERLAY_HAPTIC_FEEDBACK.boolean
    }

    private fun overlayKeepFirstTouchedEnabled(): Boolean {
        val gameId = NativeLibrary.GetCurrentGameID()
        return if (gameId != null)
            preferences.getBoolean(
                "OverlayKeepFirstTouched_$gameId",
                BooleanSetting.MAIN_OVERLAY_KEEP_FIRST_TOUCHED.boolean
            )
        else
            BooleanSetting.MAIN_OVERLAY_KEEP_FIRST_TOUCHED.boolean
    }

    private fun getMotionButtonDefaultX(
        legacyId: Int,
        orientation: String = "landscape"
    ): Float =
        when (legacyId) {
            ButtonType.WIIMOTE_SHAKE_X -> 20f
            ButtonType.WIIMOTE_SHAKE_Y -> 120f
            ButtonType.WIIMOTE_SHAKE_Z -> 220f
            ButtonType.WIIMOTE_SWING -> 320f
            ButtonType.WIIMOTE_TILT -> 20f
            ButtonType.WIIMOTE_TILT_FORWARD -> 100f
            ButtonType.WIIMOTE_TILT_BACKWARD -> 110f
            ButtonType.WIIMOTE_TILT_LEFT -> 120f
            ButtonType.WIIMOTE_TILT_RIGHT -> 130f
            ButtonType.WIIMOTE_SWING_FORWARD -> 20f
            ButtonType.WIIMOTE_SWING_BACKWARD -> 120f
            ButtonType.NUNCHUK_SHAKE_X -> 20f
            ButtonType.NUNCHUK_SHAKE_Y -> 120f
            ButtonType.NUNCHUK_SHAKE_Z -> 220f
            ButtonType.NUNCHUK_SWING -> 320f
            ButtonType.NUNCHUK_TILT -> 20f
            ButtonType.NUNCHUK_TILT_FORWARD -> 140f
            ButtonType.NUNCHUK_TILT_BACKWARD -> 150f
            ButtonType.NUNCHUK_TILT_LEFT -> 160f
            ButtonType.NUNCHUK_TILT_RIGHT -> 170f
            ButtonType.NUNCHUK_SWING_FORWARD -> 20f
            ButtonType.NUNCHUK_SWING_BACKWARD -> 120f
            ButtonType.TATACON_RIM_LEFT -> 20f
            ButtonType.TATACON_RIM_RIGHT -> 400f
            ButtonType.TATACON_CENTER_LEFT -> 20f
            ButtonType.TATACON_CENTER_RIGHT -> 400f
            ButtonType.TRIGGER_ANALOG_STICK -> 380f
            ButtonType.WIIMOTE_IR -> 200f
            ButtonType.CLASSIC_TRIGGER_L_HALF -> 20f
            ButtonType.CLASSIC_TRIGGER_R_HALF -> 400f
            ButtonType.GC_L_ANALOG_STICK -> 20f
            ButtonType.GC_R_ANALOG_STICK -> 400f
            ButtonType.CLASSIC_L_ANALOG_STICK -> 20f
            ButtonType.CLASSIC_R_ANALOG_STICK -> 400f
			ButtonType.HOTKEY_SAVE_STATE_1 -> 210f
			ButtonType.HOTKEY_SAVE_STATE_2 -> 270f
			ButtonType.HOTKEY_LOAD_STATE_1 -> 330f
			ButtonType.HOTKEY_LOAD_STATE_2 -> 390f
			ButtonType.HOTKEY_TOGGLE_PAUSE -> 450f
			ButtonType.HOTKEY_TOGGLE_SKIP_EFB -> 510f
			ButtonType.HOTKEY_TOGGLE_IGNORE_FORMAT -> 570f
			ButtonType.HOTKEY_TOGGLE_EFB_TEXTURE -> 600f
			ButtonType.HOTKEY_TOGGLE_IR_RECENTER -> 620f

            else -> getDefaultXFromIntegers(legacyId, orientation)
        }

    private fun getDefaultXFromIntegers(legacyId: Int, orientation: String): Float {
        val base = when (legacyId) {
            ButtonType.BUTTON_A -> "BUTTON_A"
            ButtonType.BUTTON_B -> "BUTTON_B"
            ButtonType.BUTTON_X -> "BUTTON_X"
            ButtonType.BUTTON_Y -> "BUTTON_Y"
            ButtonType.BUTTON_Z -> "BUTTON_Z"
            ButtonType.BUTTON_START -> "BUTTON_START"
            ButtonType.BUTTON_UP -> "BUTTON_UP"
            ButtonType.BUTTON_DOWN -> "BUTTON_DOWN"
            ButtonType.BUTTON_LEFT -> "BUTTON_LEFT"
            ButtonType.BUTTON_RIGHT -> "BUTTON_RIGHT"
            ButtonType.TRIGGER_L -> "TRIGGER_L"
            ButtonType.TRIGGER_R -> "TRIGGER_R"
            ButtonType.TRIGGER_L_HALF -> "TRIGGER_L_HALF"
            ButtonType.TRIGGER_R_HALF -> "TRIGGER_R_HALF"
            ButtonType.STICK_MAIN -> "STICK_MAIN"
            ButtonType.STICK_C -> "STICK_C"
            ButtonType.WIIMOTE_BUTTON_A -> "WIIMOTE_BUTTON_A"
            ButtonType.WIIMOTE_BUTTON_B -> "WIIMOTE_BUTTON_B"
            ButtonType.WIIMOTE_BUTTON_1 -> "WIIMOTE_BUTTON_1"
            ButtonType.WIIMOTE_BUTTON_2 -> "WIIMOTE_BUTTON_2"
            ButtonType.WIIMOTE_BUTTON_MINUS -> "WIIMOTE_BUTTON_MINUS"
            ButtonType.WIIMOTE_BUTTON_PLUS -> "WIIMOTE_BUTTON_PLUS"
            ButtonType.WIIMOTE_BUTTON_HOME -> "WIIMOTE_BUTTON_HOME"
            ButtonType.WIIMOTE_UP -> "WIIMOTE_UP"
            ButtonType.NUNCHUK_BUTTON_C -> "NUNCHUK_BUTTON_C"
            ButtonType.NUNCHUK_BUTTON_Z -> "NUNCHUK_BUTTON_Z"
            ButtonType.NUNCHUK_STICK -> "NUNCHUK_STICK"
            ButtonType.CLASSIC_BUTTON_A -> "CLASSIC_BUTTON_A"
            ButtonType.CLASSIC_BUTTON_B -> "CLASSIC_BUTTON_B"
            ButtonType.CLASSIC_BUTTON_X -> "CLASSIC_BUTTON_X"
            ButtonType.CLASSIC_BUTTON_Y -> "CLASSIC_BUTTON_Y"
            ButtonType.CLASSIC_BUTTON_PLUS -> "CLASSIC_BUTTON_PLUS"
            ButtonType.CLASSIC_BUTTON_MINUS -> "CLASSIC_BUTTON_MINUS"
            ButtonType.CLASSIC_BUTTON_HOME -> "CLASSIC_BUTTON_HOME"
            ButtonType.CLASSIC_BUTTON_ZL -> "CLASSIC_BUTTON_ZL"
            ButtonType.CLASSIC_BUTTON_ZR -> "CLASSIC_BUTTON_ZR"
            ButtonType.CLASSIC_DPAD_UP -> "CLASSIC_DPAD_UP"
            ButtonType.CLASSIC_STICK_LEFT -> "CLASSIC_STICK_LEFT"
            ButtonType.CLASSIC_STICK_RIGHT -> "CLASSIC_STICK_RIGHT"
            ButtonType.CLASSIC_TRIGGER_L -> "CLASSIC_TRIGGER_L"
            ButtonType.CLASSIC_TRIGGER_R -> "CLASSIC_TRIGGER_R"
            else -> return 0f
        }
        val suffix = if (orientation == "portrait") "_PORTRAIT_X" else "_X"
        val ctx = context ?: return 0f
        val resId = ctx.resources.getIdentifier(base + suffix, "integer", ctx.packageName)
        return if (resId != 0) ctx.resources.getInteger(resId).toFloat() else 0f
    }

    private fun getMotionButtonDefaultY(
        legacyId: Int,
        orientation: String = "landscape"
    ): Float = when (legacyId) {
        ButtonType.WIIMOTE_SHAKE_X -> 300f
        ButtonType.WIIMOTE_SHAKE_Y -> 300f
        ButtonType.WIIMOTE_SHAKE_Z -> 300f
        ButtonType.WIIMOTE_SWING -> 320f      // WSW
        ButtonType.WIIMOTE_TILT -> 420f       // WT
        ButtonType.WIIMOTE_TILT_FORWARD -> 420f
        ButtonType.WIIMOTE_TILT_BACKWARD -> 440f
        ButtonType.WIIMOTE_TILT_LEFT -> 460f
        ButtonType.WIIMOTE_TILT_RIGHT -> 480f
        ButtonType.WIIMOTE_SWING_FORWARD -> 340f
        ButtonType.WIIMOTE_SWING_BACKWARD -> 360f
        ButtonType.NUNCHUK_SHAKE_X -> 500f
        ButtonType.NUNCHUK_SHAKE_Y -> 500f
        ButtonType.NUNCHUK_SHAKE_Z -> 500f
        ButtonType.NUNCHUK_SWING -> 520f      // NSW
        ButtonType.NUNCHUK_TILT -> 580f       // NT
        ButtonType.NUNCHUK_TILT_FORWARD -> 600f
        ButtonType.NUNCHUK_TILT_BACKWARD -> 610f
        ButtonType.NUNCHUK_TILT_LEFT -> 600f
        ButtonType.NUNCHUK_TILT_RIGHT -> 600f
        ButtonType.NUNCHUK_SWING_FORWARD -> 540f
        ButtonType.NUNCHUK_SWING_BACKWARD -> 560f
        ButtonType.TATACON_RIM_LEFT -> 200f
        ButtonType.TATACON_RIM_RIGHT -> 200f
        ButtonType.TATACON_CENTER_LEFT -> 260f
        ButtonType.TATACON_CENTER_RIGHT -> 260f
        ButtonType.TRIGGER_ANALOG_STICK -> 560f
        ButtonType.WIIMOTE_IR -> 150f
        ButtonType.CLASSIC_TRIGGER_L_HALF -> 560f
        ButtonType.CLASSIC_TRIGGER_R_HALF -> 560f
        ButtonType.GC_L_ANALOG_STICK -> 560f
        ButtonType.GC_R_ANALOG_STICK -> 560f
        ButtonType.CLASSIC_L_ANALOG_STICK -> 560f
        ButtonType.CLASSIC_R_ANALOG_STICK -> 560f
		ButtonType.HOTKEY_SAVE_STATE_1 -> 30f
		ButtonType.HOTKEY_SAVE_STATE_2 -> 30f
		ButtonType.HOTKEY_LOAD_STATE_1 -> 30f
		ButtonType.HOTKEY_LOAD_STATE_2 -> 30f
		ButtonType.HOTKEY_TOGGLE_PAUSE -> 30f
		ButtonType.HOTKEY_TOGGLE_SKIP_EFB -> 30f
		ButtonType.HOTKEY_TOGGLE_IGNORE_FORMAT -> 30f
		ButtonType.HOTKEY_TOGGLE_EFB_TEXTURE -> 30f
		ButtonType.HOTKEY_TOGGLE_IR_RECENTER -> 30f

        else -> getDefaultYFromIntegers(legacyId, orientation)
    }

    private fun getDefaultYFromIntegers(legacyId: Int, orientation: String): Float {
        val base = when (legacyId) {
            ButtonType.BUTTON_A -> "BUTTON_A"
            ButtonType.BUTTON_B -> "BUTTON_B"
            ButtonType.BUTTON_X -> "BUTTON_X"
            ButtonType.BUTTON_Y -> "BUTTON_Y"
            ButtonType.BUTTON_Z -> "BUTTON_Z"
            ButtonType.BUTTON_START -> "BUTTON_START"
            ButtonType.BUTTON_UP -> "BUTTON_UP"
            ButtonType.BUTTON_DOWN -> "BUTTON_DOWN"
            ButtonType.BUTTON_LEFT -> "BUTTON_LEFT"
            ButtonType.BUTTON_RIGHT -> "BUTTON_RIGHT"
            ButtonType.TRIGGER_L -> "TRIGGER_L"
            ButtonType.TRIGGER_R -> "TRIGGER_R"
            ButtonType.TRIGGER_L_HALF -> "TRIGGER_L_HALF"
            ButtonType.TRIGGER_R_HALF -> "TRIGGER_R_HALF"
            ButtonType.STICK_MAIN -> "STICK_MAIN"
            ButtonType.STICK_C -> "STICK_C"
            ButtonType.WIIMOTE_BUTTON_A -> "WIIMOTE_BUTTON_A"
            ButtonType.WIIMOTE_BUTTON_B -> "WIIMOTE_BUTTON_B"
            ButtonType.WIIMOTE_BUTTON_1 -> "WIIMOTE_BUTTON_1"
            ButtonType.WIIMOTE_BUTTON_2 -> "WIIMOTE_BUTTON_2"
            ButtonType.WIIMOTE_BUTTON_MINUS -> "WIIMOTE_BUTTON_MINUS"
            ButtonType.WIIMOTE_BUTTON_PLUS -> "WIIMOTE_BUTTON_PLUS"
            ButtonType.WIIMOTE_BUTTON_HOME -> "WIIMOTE_BUTTON_HOME"
            ButtonType.WIIMOTE_UP -> "WIIMOTE_UP"
            ButtonType.WIIMOTE_DOWN -> "WIIMOTE_DOWN"
            ButtonType.WIIMOTE_LEFT -> "WIIMOTE_LEFT"
            ButtonType.WIIMOTE_RIGHT -> "WIIMOTE_RIGHT"
            ButtonType.NUNCHUK_BUTTON_C -> "NUNCHUK_BUTTON_C"
            ButtonType.NUNCHUK_BUTTON_Z -> "NUNCHUK_BUTTON_Z"
            ButtonType.NUNCHUK_STICK -> "NUNCHUK_STICK"
            ButtonType.CLASSIC_BUTTON_A -> "CLASSIC_BUTTON_A"
            ButtonType.CLASSIC_BUTTON_B -> "CLASSIC_BUTTON_B"
            ButtonType.CLASSIC_BUTTON_X -> "CLASSIC_BUTTON_X"
            ButtonType.CLASSIC_BUTTON_Y -> "CLASSIC_BUTTON_Y"
            ButtonType.CLASSIC_BUTTON_PLUS -> "CLASSIC_BUTTON_PLUS"
            ButtonType.CLASSIC_BUTTON_MINUS -> "CLASSIC_BUTTON_MINUS"
            ButtonType.CLASSIC_BUTTON_HOME -> "CLASSIC_BUTTON_HOME"
            ButtonType.CLASSIC_BUTTON_ZL -> "CLASSIC_BUTTON_ZL"
            ButtonType.CLASSIC_BUTTON_ZR -> "CLASSIC_BUTTON_ZR"
            ButtonType.CLASSIC_DPAD_UP -> "CLASSIC_DPAD_UP"
            ButtonType.CLASSIC_DPAD_DOWN -> "CLASSIC_DPAD_DOWN"
            ButtonType.CLASSIC_DPAD_LEFT -> "CLASSIC_DPAD_LEFT"
            ButtonType.CLASSIC_DPAD_RIGHT -> "CLASSIC_DPAD_RIGHT"
            ButtonType.CLASSIC_STICK_LEFT -> "CLASSIC_STICK_LEFT"
            ButtonType.CLASSIC_STICK_RIGHT -> "CLASSIC_STICK_RIGHT"
            ButtonType.CLASSIC_TRIGGER_L -> "CLASSIC_TRIGGER_L"
            ButtonType.CLASSIC_TRIGGER_R -> "CLASSIC_TRIGGER_R"
            else -> return 0f
        }
        val suffix = if (orientation == "portrait") "_PORTRAIT_Y" else "_Y"
        val ctx = context ?: return 0f
        val resId = ctx.resources.getIdentifier(base + suffix, "integer", ctx.packageName)
        return if (resId != 0) ctx.resources.getInteger(resId).toFloat() else 0f
    }
    init {
        if (!preferences.getBoolean("OverlayInitV3", false))
            defaultOverlay()

        // Set the on touch listener.
        setOnTouchListener(this)

        // Force draw
        setWillNotDraw(false)

        // Request focus for the overlay so it has priority on presses.
        requestFocus()
    }

	fun recenterPointer() {
    overlayPointer?.recenter()
	}

    fun setSurfacePosition(rect: Rect?) {
        surfacePosition = rect
        initTouchPointer()
    }

    fun initTouchPointer() {
        // Check if we have all the data we need yet
        val aspectRatioAvailable = NativeLibrary.IsRunning()
        if (!aspectRatioAvailable || surfacePosition == null)
            return
        // Check if there's any point in running the pointer code
        if (!NativeLibrary.IsEmulatingWii())
            return

        val gameId = NativeLibrary.GetCurrentGameID()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        // Double tap
        var doubleTapButton = if (gameId != null)
            prefs.getInt("DoubleTap_$gameId", IntSetting.MAIN_DOUBLE_TAP_BUTTON.int)
        else
            IntSetting.MAIN_DOUBLE_TAP_BUTTON.int
        if (configuredControllerType != OVERLAY_WIIMOTE_CLASSIC &&
            doubleTapButton == ButtonType.CLASSIC_BUTTON_A
        ) {
            doubleTapButton = ButtonType.WIIMOTE_BUTTON_A
        }
        var doubleTapControl = InputOverlayPointer.SINGLE_TAP_NONE
        when (doubleTapButton) {
            ButtonType.WIIMOTE_BUTTON_A -> doubleTapControl = ControlId.WIIMOTE_A_BUTTON
			ButtonType.WIIMOTE_BUTTON_B -> doubleTapControl = ControlId.WIIMOTE_B_BUTTON
			ButtonType.WIIMOTE_BUTTON_MINUS -> doubleTapControl = ControlId.WIIMOTE_MINUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_PLUS -> doubleTapControl = ControlId.WIIMOTE_PLUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_1 -> doubleTapControl = ControlId.WIIMOTE_ONE_BUTTON
			ButtonType.WIIMOTE_BUTTON_2 -> doubleTapControl = ControlId.WIIMOTE_TWO_BUTTON
			ButtonType.NUNCHUK_BUTTON_C -> doubleTapControl = ControlId.NUNCHUK_C_BUTTON
			ButtonType.NUNCHUK_BUTTON_Z -> doubleTapControl = ControlId.NUNCHUK_Z_BUTTON
			ButtonType.CLASSIC_BUTTON_A -> doubleTapControl = ControlId.CLASSIC_A_BUTTON
			ButtonType.CLASSIC_BUTTON_B -> doubleTapControl = ControlId.CLASSIC_B_BUTTON
			ButtonType.CLASSIC_BUTTON_X -> doubleTapControl = ControlId.CLASSIC_X_BUTTON
			ButtonType.CLASSIC_BUTTON_Y -> doubleTapControl = ControlId.CLASSIC_Y_BUTTON
			ButtonType.CLASSIC_BUTTON_MINUS -> doubleTapControl = ControlId.CLASSIC_MINUS_BUTTON
			ButtonType.CLASSIC_BUTTON_PLUS -> doubleTapControl = ControlId.CLASSIC_PLUS_BUTTON
			ButtonType.CLASSIC_TRIGGER_L -> doubleTapControl = ControlId.CLASSIC_L_DIGITAL
			ButtonType.CLASSIC_TRIGGER_R -> doubleTapControl = ControlId.CLASSIC_R_DIGITAL
			ButtonType.CLASSIC_BUTTON_ZL -> doubleTapControl = ControlId.CLASSIC_ZL_BUTTON
			ButtonType.CLASSIC_BUTTON_ZR -> doubleTapControl = ControlId.CLASSIC_ZR_BUTTON
		}

        // Double tap hold
        var doubleTapHoldButton = if (gameId != null)
            prefs.getInt("DoubleTapHold_$gameId", IntSetting.MAIN_DOUBLE_TAP_HOLD_BUTTON.int)
        else
            IntSetting.MAIN_DOUBLE_TAP_HOLD_BUTTON.int
        var doubleTapHoldControl = InputOverlayPointer.SINGLE_TAP_NONE
        when (doubleTapHoldButton) {
            ButtonType.WIIMOTE_BUTTON_A -> doubleTapHoldControl = ControlId.WIIMOTE_A_BUTTON
			ButtonType.WIIMOTE_BUTTON_B -> doubleTapHoldControl = ControlId.WIIMOTE_B_BUTTON
			ButtonType.WIIMOTE_BUTTON_MINUS -> doubleTapHoldControl = ControlId.WIIMOTE_MINUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_PLUS -> doubleTapHoldControl = ControlId.WIIMOTE_PLUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_1 -> doubleTapHoldControl = ControlId.WIIMOTE_ONE_BUTTON
			ButtonType.WIIMOTE_BUTTON_2 -> doubleTapHoldControl = ControlId.WIIMOTE_TWO_BUTTON
			ButtonType.NUNCHUK_BUTTON_C -> doubleTapHoldControl = ControlId.NUNCHUK_C_BUTTON
			ButtonType.NUNCHUK_BUTTON_Z -> doubleTapHoldControl = ControlId.NUNCHUK_Z_BUTTON
			ButtonType.CLASSIC_BUTTON_A -> doubleTapHoldControl = ControlId.CLASSIC_A_BUTTON
			ButtonType.CLASSIC_BUTTON_B -> doubleTapHoldControl = ControlId.CLASSIC_B_BUTTON
			ButtonType.CLASSIC_BUTTON_X -> doubleTapHoldControl = ControlId.CLASSIC_X_BUTTON
			ButtonType.CLASSIC_BUTTON_Y -> doubleTapHoldControl = ControlId.CLASSIC_Y_BUTTON
			ButtonType.CLASSIC_BUTTON_MINUS -> doubleTapHoldControl = ControlId.CLASSIC_MINUS_BUTTON
			ButtonType.CLASSIC_BUTTON_PLUS -> doubleTapHoldControl = ControlId.CLASSIC_PLUS_BUTTON
			ButtonType.CLASSIC_TRIGGER_L -> doubleTapHoldControl = ControlId.CLASSIC_L_DIGITAL
			ButtonType.CLASSIC_TRIGGER_R -> doubleTapHoldControl = ControlId.CLASSIC_R_DIGITAL
			ButtonType.CLASSIC_BUTTON_ZL -> doubleTapHoldControl = ControlId.CLASSIC_ZL_BUTTON
			ButtonType.CLASSIC_BUTTON_ZR -> doubleTapHoldControl = ControlId.CLASSIC_ZR_BUTTON
		}

        // Single tap
        var singleTapButton = if (gameId != null)
            prefs.getInt("SingleTap_$gameId", IntSetting.MAIN_SINGLE_TAP_BUTTON.int)
        else
            IntSetting.MAIN_SINGLE_TAP_BUTTON.int
        var singleTapControl = InputOverlayPointer.SINGLE_TAP_NONE
        when (singleTapButton) {
            ButtonType.WIIMOTE_BUTTON_A -> singleTapControl = ControlId.WIIMOTE_A_BUTTON
			ButtonType.WIIMOTE_BUTTON_B -> singleTapControl = ControlId.WIIMOTE_B_BUTTON
			ButtonType.WIIMOTE_BUTTON_MINUS -> singleTapControl = ControlId.WIIMOTE_MINUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_PLUS -> singleTapControl = ControlId.WIIMOTE_PLUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_1 -> singleTapControl = ControlId.WIIMOTE_ONE_BUTTON
			ButtonType.WIIMOTE_BUTTON_2 -> singleTapControl = ControlId.WIIMOTE_TWO_BUTTON
			ButtonType.NUNCHUK_BUTTON_C -> singleTapControl = ControlId.NUNCHUK_C_BUTTON
			ButtonType.NUNCHUK_BUTTON_Z -> singleTapControl = ControlId.NUNCHUK_Z_BUTTON
			ButtonType.CLASSIC_BUTTON_A -> singleTapControl = ControlId.CLASSIC_A_BUTTON
			ButtonType.CLASSIC_BUTTON_B -> singleTapControl = ControlId.CLASSIC_B_BUTTON
			ButtonType.CLASSIC_BUTTON_X -> singleTapControl = ControlId.CLASSIC_X_BUTTON
			ButtonType.CLASSIC_BUTTON_Y -> singleTapControl = ControlId.CLASSIC_Y_BUTTON
			ButtonType.CLASSIC_BUTTON_MINUS -> singleTapControl = ControlId.CLASSIC_MINUS_BUTTON
			ButtonType.CLASSIC_BUTTON_PLUS -> singleTapControl = ControlId.CLASSIC_PLUS_BUTTON
			ButtonType.CLASSIC_TRIGGER_L -> singleTapControl = ControlId.CLASSIC_L_DIGITAL
			ButtonType.CLASSIC_TRIGGER_R -> singleTapControl = ControlId.CLASSIC_R_DIGITAL
			ButtonType.CLASSIC_BUTTON_ZL -> singleTapControl = ControlId.CLASSIC_ZL_BUTTON
			ButtonType.CLASSIC_BUTTON_ZR -> singleTapControl = ControlId.CLASSIC_ZR_BUTTON
		}

        // Single tap hold
        var singleTapHoldButton = if (gameId != null)
            prefs.getInt("SingleTapHold_$gameId", IntSetting.MAIN_SINGLE_TAP_HOLD_BUTTON.int)
        else
            IntSetting.MAIN_SINGLE_TAP_HOLD_BUTTON.int
        var singleTapHoldControl = InputOverlayPointer.SINGLE_TAP_NONE
        when (singleTapHoldButton) {
            ButtonType.WIIMOTE_BUTTON_A -> singleTapHoldControl = ControlId.WIIMOTE_A_BUTTON
			ButtonType.WIIMOTE_BUTTON_B -> singleTapHoldControl = ControlId.WIIMOTE_B_BUTTON
			ButtonType.WIIMOTE_BUTTON_MINUS -> singleTapHoldControl = ControlId.WIIMOTE_MINUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_PLUS -> singleTapHoldControl = ControlId.WIIMOTE_PLUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_1 -> singleTapHoldControl = ControlId.WIIMOTE_ONE_BUTTON
			ButtonType.WIIMOTE_BUTTON_2 -> singleTapHoldControl = ControlId.WIIMOTE_TWO_BUTTON
			ButtonType.NUNCHUK_BUTTON_C -> singleTapHoldControl = ControlId.NUNCHUK_C_BUTTON
			ButtonType.NUNCHUK_BUTTON_Z -> singleTapHoldControl = ControlId.NUNCHUK_Z_BUTTON
			ButtonType.CLASSIC_BUTTON_A -> singleTapHoldControl = ControlId.CLASSIC_A_BUTTON
			ButtonType.CLASSIC_BUTTON_B -> singleTapHoldControl = ControlId.CLASSIC_B_BUTTON
			ButtonType.CLASSIC_BUTTON_X -> singleTapHoldControl = ControlId.CLASSIC_X_BUTTON
			ButtonType.CLASSIC_BUTTON_Y -> singleTapHoldControl = ControlId.CLASSIC_Y_BUTTON
			ButtonType.CLASSIC_BUTTON_MINUS -> singleTapHoldControl = ControlId.CLASSIC_MINUS_BUTTON
			ButtonType.CLASSIC_BUTTON_PLUS -> singleTapHoldControl = ControlId.CLASSIC_PLUS_BUTTON
			ButtonType.CLASSIC_TRIGGER_L -> singleTapHoldControl = ControlId.CLASSIC_L_DIGITAL
			ButtonType.CLASSIC_TRIGGER_R -> singleTapHoldControl = ControlId.CLASSIC_R_DIGITAL
			ButtonType.CLASSIC_BUTTON_ZL -> singleTapHoldControl = ControlId.CLASSIC_ZL_BUTTON
			ButtonType.CLASSIC_BUTTON_ZR -> singleTapHoldControl = ControlId.CLASSIC_ZR_BUTTON
		}

        // Second finger tap
        var secondFingerTapButton = if (gameId != null)
            prefs.getInt("SecondFingerTap_$gameId", IntSetting.MAIN_SECOND_FINGER_TAP_BUTTON.int)
        else
            IntSetting.MAIN_SECOND_FINGER_TAP_BUTTON.int
        var secondFingerTapControl = InputOverlayPointer.SINGLE_TAP_NONE
        when (secondFingerTapButton) {
            ButtonType.WIIMOTE_BUTTON_A -> secondFingerTapControl = ControlId.WIIMOTE_A_BUTTON
			ButtonType.WIIMOTE_BUTTON_B -> secondFingerTapControl = ControlId.WIIMOTE_B_BUTTON
			ButtonType.WIIMOTE_BUTTON_MINUS -> secondFingerTapControl = ControlId.WIIMOTE_MINUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_PLUS -> secondFingerTapControl = ControlId.WIIMOTE_PLUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_1 -> secondFingerTapControl = ControlId.WIIMOTE_ONE_BUTTON
			ButtonType.WIIMOTE_BUTTON_2 -> secondFingerTapControl = ControlId.WIIMOTE_TWO_BUTTON
			ButtonType.NUNCHUK_BUTTON_C -> secondFingerTapControl = ControlId.NUNCHUK_C_BUTTON
			ButtonType.NUNCHUK_BUTTON_Z -> secondFingerTapControl = ControlId.NUNCHUK_Z_BUTTON
			ButtonType.CLASSIC_BUTTON_A -> secondFingerTapControl = ControlId.CLASSIC_A_BUTTON
			ButtonType.CLASSIC_BUTTON_B -> secondFingerTapControl = ControlId.CLASSIC_B_BUTTON
			ButtonType.CLASSIC_BUTTON_X -> secondFingerTapControl = ControlId.CLASSIC_X_BUTTON
			ButtonType.CLASSIC_BUTTON_Y -> secondFingerTapControl = ControlId.CLASSIC_Y_BUTTON
			ButtonType.CLASSIC_BUTTON_MINUS -> secondFingerTapControl = ControlId.CLASSIC_MINUS_BUTTON
			ButtonType.CLASSIC_BUTTON_PLUS -> secondFingerTapControl = ControlId.CLASSIC_PLUS_BUTTON
			ButtonType.CLASSIC_TRIGGER_L -> secondFingerTapControl = ControlId.CLASSIC_L_DIGITAL
			ButtonType.CLASSIC_TRIGGER_R -> secondFingerTapControl = ControlId.CLASSIC_R_DIGITAL
			ButtonType.CLASSIC_BUTTON_ZL -> secondFingerTapControl = ControlId.CLASSIC_ZL_BUTTON
			ButtonType.CLASSIC_BUTTON_ZR -> secondFingerTapControl = ControlId.CLASSIC_ZR_BUTTON
		}

        // Second finger hold
        var secondFingerHoldButton = if (gameId != null)
            prefs.getInt("SecondFingerHold_$gameId", IntSetting.MAIN_SECOND_FINGER_HOLD_BUTTON.int)
        else
            IntSetting.MAIN_SECOND_FINGER_HOLD_BUTTON.int
        var secondFingerHoldControl = InputOverlayPointer.SINGLE_TAP_NONE
        when (secondFingerHoldButton) {
            ButtonType.WIIMOTE_BUTTON_A -> secondFingerHoldControl = ControlId.WIIMOTE_A_BUTTON
			ButtonType.WIIMOTE_BUTTON_B -> secondFingerHoldControl = ControlId.WIIMOTE_B_BUTTON
			ButtonType.WIIMOTE_BUTTON_MINUS -> secondFingerHoldControl = ControlId.WIIMOTE_MINUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_PLUS -> secondFingerHoldControl = ControlId.WIIMOTE_PLUS_BUTTON
			ButtonType.WIIMOTE_BUTTON_1 -> secondFingerHoldControl = ControlId.WIIMOTE_ONE_BUTTON
			ButtonType.WIIMOTE_BUTTON_2 -> secondFingerHoldControl = ControlId.WIIMOTE_TWO_BUTTON
			ButtonType.NUNCHUK_BUTTON_C -> secondFingerHoldControl = ControlId.NUNCHUK_C_BUTTON
			ButtonType.NUNCHUK_BUTTON_Z -> secondFingerHoldControl = ControlId.NUNCHUK_Z_BUTTON
			ButtonType.CLASSIC_BUTTON_A -> secondFingerHoldControl = ControlId.CLASSIC_A_BUTTON
			ButtonType.CLASSIC_BUTTON_B -> secondFingerHoldControl = ControlId.CLASSIC_B_BUTTON
			ButtonType.CLASSIC_BUTTON_X -> secondFingerHoldControl = ControlId.CLASSIC_X_BUTTON
			ButtonType.CLASSIC_BUTTON_Y -> secondFingerHoldControl = ControlId.CLASSIC_Y_BUTTON
			ButtonType.CLASSIC_BUTTON_MINUS -> secondFingerHoldControl = ControlId.CLASSIC_MINUS_BUTTON
			ButtonType.CLASSIC_BUTTON_PLUS -> secondFingerHoldControl = ControlId.CLASSIC_PLUS_BUTTON
			ButtonType.CLASSIC_TRIGGER_L -> secondFingerHoldControl = ControlId.CLASSIC_L_DIGITAL
			ButtonType.CLASSIC_TRIGGER_R -> secondFingerHoldControl = ControlId.CLASSIC_R_DIGITAL
			ButtonType.CLASSIC_BUTTON_ZL -> secondFingerHoldControl = ControlId.CLASSIC_ZL_BUTTON
			ButtonType.CLASSIC_BUTTON_ZR -> secondFingerHoldControl = ControlId.CLASSIC_ZR_BUTTON
		}

        // IR mode
        val irMode = if (gameId != null)
            prefs.getInt("IRMode_$gameId", IntSetting.MAIN_IR_MODE.int)
        else
            IntSetting.MAIN_IR_MODE.int

        // Recenter
        val recenter = if (gameId != null)
            prefs.getBoolean("IRRecenter_$gameId", BooleanSetting.MAIN_IR_ALWAYS_RECENTER.boolean)
        else
            BooleanSetting.MAIN_IR_ALWAYS_RECENTER.boolean

		// Swing on swipe
		val swingOnSwipe = if (gameId != null)
			prefs.getBoolean("IRSwingOnSwipe_$gameId", BooleanSetting.MAIN_IR_SWING_ON_SWIPE.boolean)
		else
			BooleanSetting.MAIN_IR_SWING_ON_SWIPE.boolean

		// Nunchuk swing on swipe
		val nswingOnSwipe = if (gameId != null)
			prefs.getBoolean("IRNSwingOnSwipe_$gameId", BooleanSetting.MAIN_IR_NUNCHUK_SWING_ON_SWIPE.boolean)
		else
			BooleanSetting.MAIN_IR_NUNCHUK_SWING_ON_SWIPE.boolean

        overlayPointer = InputOverlayPointer(
            surfacePosition!!,
            doubleTapControl,
            doubleTapHoldControl,
            singleTapControl,
            singleTapHoldControl,
            secondFingerTapControl,
            secondFingerHoldControl,
            irMode,
            recenter,
			swingOnSwipe,
			nswingOnSwipe,
            controllerIndex
        )
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (button in overlayButtons) {
            button.draw(canvas)
        }

        for (dpad in overlayDpads) {
            dpad.draw(canvas)
        }

        for (joystick in overlayJoysticks) {
            joystick.draw(canvas)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (editMode) {
            return onTouchWhileEditing(event)
        }

        val action = event.actionMasked
        val firstPointer = action != MotionEvent.ACTION_POINTER_DOWN &&
            action != MotionEvent.ACTION_POINTER_UP
        val pointerIndex = if (firstPointer) 0 else event.actionIndex
        // Tracks if any button/joystick is pressed down
        var pressed = false

        for (button in overlayButtons) {
            // Determine the button state to apply based on the MotionEvent action flag.
            when (action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // If a pointer enters the bounds of a button, press that button.
                    if (button.hitTest(
                            event.getX(pointerIndex).toInt(),
                            event.getY(pointerIndex).toInt(),
                            button.useAlphaHitTest
                        )
                    ) {
                        button.setPressedState(if (button.latching) !button.getPressedState() else true)
                        button.trackId = event.getPointerId(pointerIndex)
                        
                        // Track this button for keep-first-touched behavior
                        if (overlayKeepFirstTouchedEnabled() && !button.latching) {
                            keepFirstTouchedButton = button
                            keepFirstTouchedPointer = event.getPointerId(pointerIndex)
                        }
                        
                        pressed = true
                        applyButtonControlState(button)
                        maybeHapticFeedback(hapticDurationForButton(button.legacyId))
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    // If a pointer ends, release the button it was pressing.
                    if (button.trackId == event.getPointerId(pointerIndex)) {
                        if (!button.latching)
                            button.setPressedState(false)
                        applyButtonControlState(button)

                        button.trackId = -1
                        
                        // Clear keep-first-touched tracking if this pointer is released
                        if (keepFirstTouchedPointer == event.getPointerId(pointerIndex)) {
                            keepFirstTouchedButton = null
                            keepFirstTouchedPointer = -1
                        }
                    }
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (overlayKeepFirstTouchedEnabled() && keepFirstTouchedPointer != -1) {
                        val movePointerIndex = event.findPointerIndex(keepFirstTouchedPointer)
                        if (movePointerIndex != -1) {
                            val pointerX = event.getX(movePointerIndex).toInt()
                            val pointerY = event.getY(movePointerIndex).toInt()
                            val pointerOverButton = button.hitTest(
                                pointerX,
                                pointerY,
                                button.useAlphaHitTest
                            )

                            if (keepFirstTouchedButton == button) {
                                // keep the first touched button pressed until release
                                button.setPressedState(true)
                                pressed = true
                            } else if (!button.latching && pointerOverButton) {
                                // Press another button when drag moves over it.
                                if (!button.getPressedState()) {
                                    button.setPressedState(true)
                                    applyButtonControlState(button)
                                }
                                button.trackId = keepFirstTouchedPointer
                                pressed = true
                            } else if (button.trackId == keepFirstTouchedPointer && button != keepFirstTouchedButton) {
                                // Release a non-first-touched button when the pointer leaves it.
                                button.setPressedState(false)
                                applyButtonControlState(button)
                                button.trackId = -1
                            }
                        }
                    }
                }
            }
        }

        // Follow-finger exclusive transfer when keep-first is off: sliding A→B→A
        // releases the previous button and presses the one currently under the finger.
        if (action == MotionEvent.ACTION_MOVE && !overlayKeepFirstTouchedEnabled()) {
            for (i in 0 until event.pointerCount) {
                val pointerId = event.getPointerId(i)
                val pointerX = event.getX(i).toInt()
                val pointerY = event.getY(i).toInt()

                // Prefer staying on the button this pointer already owns if still over it
                // (stable when bounds slightly overlap).
                val underFinger = overlayButtons.firstOrNull { candidate ->
                    candidate.trackId == pointerId &&
                        !candidate.latching &&
                        candidate.hitTest(pointerX, pointerY, candidate.useAlphaHitTest)
                } ?: overlayButtons.firstOrNull { candidate ->
                    !candidate.latching &&
                        candidate.hitTest(pointerX, pointerY, candidate.useAlphaHitTest) &&
                        (candidate.trackId == -1 || candidate.trackId == pointerId)
                }

                for (old in overlayButtons) {
                    if (old.trackId == pointerId && old != underFinger) {
                        if (!old.latching) {
                            old.setPressedState(false)
                            applyButtonControlState(old)
                        }
                        old.trackId = -1
                    }
                }

                if (underFinger != null) {
                    if (!underFinger.getPressedState()) {
                        underFinger.setPressedState(true)
                        applyButtonControlState(underFinger)
                        maybeHapticFeedback(hapticDurationForButton(underFinger.legacyId))
                    }
                    underFinger.trackId = pointerId
                    pressed = true
                }
            }
        }

        for (dpad in overlayDpads) {
            // Determine the button state to apply based on the MotionEvent action flag.
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // If a pointer enters the bounds of a button, press that button.
                    if (dpad.bounds
                            .contains(
                                event.getX(pointerIndex).toInt(),
                                event.getY(pointerIndex).toInt()
                            )
                    ) {
                        dpad.trackId = event.getPointerId(pointerIndex)
                        pressed = true
                    }
                }
            }
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    if (dpad.trackId == event.getPointerId(pointerIndex)) {
                        val dpadPressed = booleanArrayOf(false, false, false, false)

                        if (dpad.bounds.top + dpad.height / 3 > event.getY(pointerIndex).toInt())
                            dpadPressed[0] = true
                        if (dpad.bounds.bottom - dpad.height / 3 < event.getY(pointerIndex).toInt())
                            dpadPressed[1] = true
                        if (dpad.bounds.left + dpad.width / 3 > event.getX(pointerIndex).toInt())
                            dpadPressed[2] = true
                        if (dpad.bounds.right - dpad.width / 3 < event.getX(pointerIndex).toInt())
                            dpadPressed[3] = true

                        // Haptic only when a cardinal direction newly engages (not center touch)
                        var directionNewlyPressed = false
                        for (i in dpadPressed.indices) {
                            if (dpadPressed[i] && !dpad.lastDirectionPressed[i]) {
                                directionNewlyPressed = true
                            }
                            dpad.lastDirectionPressed[i] = dpadPressed[i]
                        }
                        if (directionNewlyPressed) {
                            maybeHapticFeedback(HAPTIC_DPAD_MS)
                        }

                        // Release the buttons first, then press
                        for (i in dpadPressed.indices) {
                            if (!dpadPressed[i]) {
                                InputOverrider.setControlState(
                                    controllerIndex,
                                    dpad.getControl(i),
                                    0.0
                                )
                            } else {
                                InputOverrider.setControlState(
                                    controllerIndex,
                                    dpad.getControl(i),
                                    1.0
                                )
                            }
                        }
                        setDpadState(
                            dpad,
                            dpadPressed[0],
                            dpadPressed[1],
                            dpadPressed[2],
                            dpadPressed[3]
                        )
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    // If a pointer ends, release the buttons.
                    if (dpad.trackId == event.getPointerId(pointerIndex)) {
                        for (i in 0 until 4) {
                            dpad.setState(InputOverlayDrawableDpad.STATE_DEFAULT)
                            InputOverrider.setControlState(
                                controllerIndex,
                                dpad.getControl(i),
                                0.0
                            )
                            dpad.lastDirectionPressed[i] = false
                        }
                        dpad.trackId = -1
                    }
                }
            }
        }

        for (joystick in overlayJoysticks) {
            if (joystick.trackEvent(event)) {
                if (joystick.trackId != -1)
                    pressed = true
            }

            // Max-throw "bump" when stick first reaches the gate edge
            if (joystick.trackId != -1) {
                val atMax = joystick.isAtMaxExtent()
                if (atMax && !joystick.wasAtMaxExtent) {
                    maybeHapticFeedback(HAPTIC_JOYSTICK_MS)
                }
                joystick.wasAtMaxExtent = atMax
            }

            if (!joystick.isAnalogTriggerStick && !joystick.isVerticalTriggerStick) {
                InputOverrider.setControlState(
                    controllerIndex,
                    joystick.xControl,
                    joystick.x.toDouble()
                )
                InputOverrider.setControlState(
                    controllerIndex,
                    joystick.yControl,
                    -joystick.y.toDouble()
                )
            }
        }

        if (controllerType == OVERLAY_GAMECUBE)
            applyGcTriggerAnalogStates()
        if (controllerType == OVERLAY_WIIMOTE_CLASSIC)
            applyClassicTriggerAnalogStates()
        // No button/joystick pressed, safe to move pointer
        val irJoystickActive = overlayJoysticks.any {
            it.legacyId == ButtonType.WIIMOTE_IR && it.trackId != -1
        }

        if (!pressed && overlayPointer != null) {
            overlayPointer!!.onTouch(event)

            if (!irJoystickActive) {
                InputOverrider.setControlState(
                    controllerIndex,
                    ControlId.WIIMOTE_IR_X,
                    overlayPointer!!.x.toDouble()
                )
                InputOverrider.setControlState(
                    controllerIndex,
                    ControlId.WIIMOTE_IR_Y,
                    -overlayPointer!!.y.toDouble()
                )
            }
        }

        if (pendingRefreshControls) {
            pendingRefreshControls = false
            refreshControls()
        }

        invalidate()

        return true
    }

    fun onTouchWhileEditing(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val fingerPositionX = event.getX(pointerIndex).toInt()
        val fingerPositionY = event.getY(pointerIndex).toInt()

        val orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "-Portrait" else ""

        // Maybe combine Button and Joystick as subclasses of the same parent?
        // Or maybe create an interface like IMoveableHUDControl?

        for (button in overlayButtons) {
            // Determine the button state to apply based on the MotionEvent action flag.
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // If no button is being moved now, remember the currently touched button to move.
                    if (buttonBeingConfigured == null &&
                        button.bounds.contains(fingerPositionX, fingerPositionY)
                    ) {
                        buttonBeingConfigured = button
                        buttonBeingConfigured?.onConfigureTouch(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (buttonBeingConfigured != null) {
                        buttonBeingConfigured?.onConfigureTouch(event)
                        invalidate()
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (buttonBeingConfigured == button) {
                        // Persist button position by saving new place.
                        saveControlPosition(
                            buttonBeingConfigured!!.legacyId,
                            buttonBeingConfigured!!.bounds.left,
                            buttonBeingConfigured!!.bounds.top, orientation
                        )
                        buttonBeingConfigured = null
                    }
                }
            }
        }

        for (dpad in overlayDpads) {
            // Determine the button state to apply based on the MotionEvent action flag.
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // If no button is being moved now, remember the currently touched button to move.
                    if (buttonBeingConfigured == null &&
                        dpad.bounds.contains(fingerPositionX, fingerPositionY)
                    ) {
                        dpadBeingConfigured = dpad
                        dpadBeingConfigured?.onConfigureTouch(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (dpadBeingConfigured != null) {
                        dpadBeingConfigured?.onConfigureTouch(event)
                        invalidate()
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (dpadBeingConfigured == dpad) {
                        // Persist button position by saving new place.
                        saveControlPosition(
                            dpadBeingConfigured!!.legacyId,
                            dpadBeingConfigured!!.bounds.left,
                            dpadBeingConfigured!!.bounds.top,
                            orientation
                        )
                        dpadBeingConfigured = null
                    }
                }
            }
        }

        for (joystick in overlayJoysticks) {
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (joystickBeingConfigured == null &&
                        joystick.bounds.contains(fingerPositionX, fingerPositionY)
                    ) {
                        joystickBeingConfigured = joystick
                        joystickBeingConfigured?.onConfigureTouch(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (joystickBeingConfigured != null) {
                        joystickBeingConfigured?.onConfigureTouch(event)
                        invalidate()
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (joystickBeingConfigured != null) {
                        saveControlPosition(
                            joystickBeingConfigured!!.legacyId,
                            joystickBeingConfigured!!.bounds.left,
                            joystickBeingConfigured!!.bounds.top,
                            orientation
                        )
                        joystickBeingConfigured = null
                    }
                }
            }
        }
        return true
    }

    fun onDestroy() {
        unregisterControllers()
    }

    private fun unregisterControllers() {
        for (i in gcPadRegistered.indices) {
            if (gcPadRegistered[i])
                InputOverrider.unregisterGameCube(i)
        }

        for (i in wiimoteRegistered.indices) {
            if (wiimoteRegistered[i])
                InputOverrider.unregisterWii(i)
        }

        Arrays.fill(gcPadRegistered, false)
        Arrays.fill(wiimoteRegistered, false)
    }

    private fun getAnalogControlForTrigger(control: Int): Int = when (control) {
        ControlId.GCPAD_L_DIGITAL -> ControlId.GCPAD_L_ANALOG
        ControlId.GCPAD_R_DIGITAL -> ControlId.GCPAD_R_ANALOG
        ControlId.CLASSIC_L_DIGITAL -> ControlId.CLASSIC_L_ANALOG
        ControlId.CLASSIC_R_DIGITAL -> ControlId.CLASSIC_R_ANALOG
        else -> -1
    }

    private fun applyButtonControlState(button: InputOverlayDrawableButton) {
        // Hotkey buttons
    if (button.isHotkeyButton) {
        if (button.shouldTriggerHotkey()) {
            when (button.legacyId) {
                ButtonType.HOTKEY_SAVE_STATE_1 -> NativeLibrary.SaveState(0)
                ButtonType.HOTKEY_SAVE_STATE_2 -> NativeLibrary.SaveState(1)
                ButtonType.HOTKEY_LOAD_STATE_1 -> NativeLibrary.LoadState(0)
                ButtonType.HOTKEY_LOAD_STATE_2 -> NativeLibrary.LoadState(1)
                ButtonType.HOTKEY_TOGGLE_PAUSE -> {
                    if (NativeLibrary.IsRunning()) {
                        if (NativeLibrary.IsRunningAndUnpaused()) {
                            NativeLibrary.PauseEmulation(false)
                        } else {
                            NativeLibrary.UnPauseEmulation()
                        }

                        // Toast
                        (context as? Activity)?.runOnUiThread {
                            val msg = if (NativeLibrary.IsRunningAndUnpaused())
                                "Emulation: Resumed" else "Emulation: Paused"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                ButtonType.HOTKEY_TOGGLE_SKIP_EFB -> {
                    val before = NativeConfig.getBoolean(
                        NativeConfig.LAYER_ACTIVE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBAccessEnable",
                        true
                    )

                    val after = !before

                    NativeConfig.setBoolean(
                        NativeConfig.LAYER_ACTIVE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBAccessEnable",
                        after
                    )

                    NativeConfig.setBoolean(
                        NativeConfig.LAYER_BASE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBAccessEnable",
                        after
                    )
                    NativeConfig.save(NativeConfig.LAYER_BASE)
                    (context as? Activity)?.runOnUiThread {
                        val isSkipEfbOn = !after
                        Toast.makeText(
                            context,
                            "Skip EFB Access: ${if (isSkipEfbOn) "ON" else "OFF"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                ButtonType.HOTKEY_TOGGLE_IGNORE_FORMAT -> {
                    val before = NativeConfig.getBoolean(
                        NativeConfig.LAYER_ACTIVE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBEmulateFormatChanges",
                        true
                    )
                    val after = !before
                    NativeConfig.setBoolean(
                        NativeConfig.LAYER_ACTIVE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBEmulateFormatChanges",
                        after
                    )
                    NativeConfig.setBoolean(
                        NativeConfig.LAYER_BASE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBEmulateFormatChanges",
                        after
                    )
                    NativeConfig.save(NativeConfig.LAYER_BASE)

                    (context as? Activity)?.runOnUiThread {
                        val enabled = !after
                        Toast.makeText(
                            context,
                            "Ignore Format Changes: ${if (enabled) "ON" else "OFF"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                ButtonType.HOTKEY_TOGGLE_EFB_TEXTURE -> {
                    val before = NativeConfig.getBoolean(
                        NativeConfig.LAYER_ACTIVE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBToTextureEnable",
                        true
                    )
                    val after = !before
                    NativeConfig.setBoolean(
                        NativeConfig.LAYER_ACTIVE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBToTextureEnable",
                        after
                    )
                    NativeConfig.setBoolean(
                        NativeConfig.LAYER_BASE,
                        Settings.FILE_GFX,
                        Settings.SECTION_GFX_HACKS,
                        "EFBToTextureEnable",
                        after
                    )
                    NativeConfig.save(NativeConfig.LAYER_BASE)

                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(
                            context,
                            "Store EFB Copies: ${if (after) "Texture" else "RAM"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                ButtonType.HOTKEY_TOGGLE_IR_RECENTER -> {
                    recenterPointer()
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "IR Recentered", Toast.LENGTH_SHORT).show()
                    }
                }

                ButtonType.HOTKEY_TOGGLE_WIIMOTE_UPRIGHT -> {
                    // Don't refresh controls while we're iterating overlayButtons.
                    setWiimoteSideways(false, false)
                    pendingRefreshControls = true
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Wiimote: Upright", Toast.LENGTH_SHORT).show()
                    }
                }

                ButtonType.HOTKEY_TOGGLE_WIIMOTE_SIDEWAYS -> {
                    // Don't refresh controls while we're iterating overlayButtons.
                    setWiimoteSideways(true, false)
                    pendingRefreshControls = true
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Wiimote: Sideways", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return
    }

		if (button.isAnalogOnly) {
            if (button.control == ControlId.GCPAD_L_ANALOG ||
                button.control == ControlId.GCPAD_R_ANALOG
            ) {
                return
            }
            // Classic LH/RH
            InputOverrider.setControlState(
                controllerIndex,
                button.control,
                if (button.getPressedState()) button.analogPressValue else 0.0
            )
            return
        }

        InputOverrider.setControlState(
            controllerIndex,
            button.control,
            if (button.getPressedState()) 1.0 else 0.0
        )

        // GC L/R analog is merged in applyGcTriggerAnalogStates() so the analog stick cannot
        // zero it out every frame.
        if (getAnalogControlForTrigger(button.control) >= 0)
            return
    }

    private fun applyGcTriggerAnalogStates() {
        var lAnalog = 0.0
        var rAnalog = 0.0

        for (button in overlayButtons) {
            when {
                button.control == ControlId.GCPAD_L_DIGITAL && button.getPressedState() ->
                    lAnalog = 1.0

                button.control == ControlId.GCPAD_R_DIGITAL && button.getPressedState() ->
                    rAnalog = 1.0

                button.control == ControlId.GCPAD_L_ANALOG &&
                    button.isAnalogOnly &&
                    button.getPressedState() ->
                    lAnalog = maxOf(lAnalog, button.analogPressValue)

                button.control == ControlId.GCPAD_R_ANALOG &&
                    button.isAnalogOnly &&
                    button.getPressedState() ->
                    rAnalog = maxOf(rAnalog, button.analogPressValue)
            }
        }

        for (joystick in overlayJoysticks) {
            // L/R joystick：Y axis，y- = L，y+ = R
            if (joystick.isAnalogTriggerStick && joystick.trackId != -1) {
                val stickY = joystick.y
                if (stickY < 0)  // up
                    lAnalog = maxOf(lAnalog, (-stickY).coerceIn(0f, 1f).toDouble())
                if (stickY > 0)  // down
                    rAnalog = maxOf(rAnalog, stickY.coerceIn(0f, 1f).toDouble())
            }

            // LA/RA joystick：Y axis，y+ only
            if (joystick.isVerticalTriggerStick && joystick.trackId != -1) {
                val stickY = joystick.y
                if (joystick.xControl == ControlId.GCPAD_L_ANALOG)
                    lAnalog = maxOf(lAnalog, stickY.coerceIn(0f, 1f).toDouble())
                if (joystick.xControl == ControlId.GCPAD_R_ANALOG)
                    rAnalog = maxOf(rAnalog, stickY.coerceIn(0f, 1f).toDouble())
            }
        }

        InputOverrider.setControlState(controllerIndex, ControlId.GCPAD_L_ANALOG, lAnalog)
        InputOverrider.setControlState(controllerIndex, ControlId.GCPAD_R_ANALOG, rAnalog)

        InputOverrider.setControlState(
            controllerIndex,
            ControlId.GCPAD_L_DIGITAL,
            if (lAnalog > 0.9) 1.0 else 0.0
        )
        InputOverrider.setControlState(
            controllerIndex,
            ControlId.GCPAD_R_DIGITAL,
            if (rAnalog > 0.9) 1.0 else 0.0
        )
    }

    private fun applyClassicTriggerAnalogStates() {
        var lAnalog = 0.0
        var rAnalog = 0.0

        for (joystick in overlayJoysticks) {
            // Classic L/R
            if (joystick.isAnalogTriggerStick && joystick.trackId != -1) {
                val stickY = joystick.y
                if (stickY < 0)  // up = L
                    lAnalog = maxOf(lAnalog, (-stickY).coerceIn(0f, 1f).toDouble())
                if (stickY > 0)  // down = R
                    rAnalog = maxOf(rAnalog, stickY.coerceIn(0f, 1f).toDouble())
            }

            if (joystick.isVerticalTriggerStick && joystick.trackId != -1) {
                val stickY = joystick.y
                if (joystick.xControl == ControlId.CLASSIC_L_ANALOG)
                    lAnalog = maxOf(lAnalog, stickY.coerceIn(0f, 1f).toDouble())
                if (joystick.xControl == ControlId.CLASSIC_R_ANALOG)
                    rAnalog = maxOf(rAnalog, stickY.coerceIn(0f, 1f).toDouble())
            }
        }

        InputOverrider.setControlState(controllerIndex, ControlId.CLASSIC_L_ANALOG, lAnalog)
        InputOverrider.setControlState(controllerIndex, ControlId.CLASSIC_R_ANALOG, rAnalog)
        InputOverrider.setControlState(
            controllerIndex,
            ControlId.CLASSIC_L_DIGITAL,
            if (lAnalog > 0.9) 1.0 else 0.0
        )
        InputOverrider.setControlState(
            controllerIndex,
            ControlId.CLASSIC_R_DIGITAL,
            if (rAnalog > 0.9) 1.0 else 0.0
        )
    }

    private fun hapticDurationForButton(legacyId: Int): Long {
        // Tatacon drum pads get a stronger (longer) hit so Taiko feel is punchier
        return if (isTataconPad(legacyId)) HAPTIC_TATACON_MS else HAPTIC_BUTTON_MS
    }

    private fun isTataconPad(legacyId: Int): Boolean {
        return legacyId == ButtonType.TATACON_RIM_LEFT ||
            legacyId == ButtonType.TATACON_RIM_RIGHT ||
            legacyId == ButtonType.TATACON_CENTER_LEFT ||
            legacyId == ButtonType.TATACON_CENTER_RIGHT
    }

    private fun maybeHapticFeedback(durationMs: Long) {
        if (!overlayHapticFeedbackEnabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun setDpadState(
        dpad: InputOverlayDrawableDpad,
        up: Boolean,
        down: Boolean,
        left: Boolean,
        right: Boolean
    ) {
        if (up) {
            if (left) {
                dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_UP_LEFT)
            } else {
                if (right) {
                    dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_UP_RIGHT)
                } else {
                    dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_UP)
                }
            }
        } else if (down) {
            if (left) {
                dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_DOWN_LEFT)
            } else {
                if (right) {
                    dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_DOWN_RIGHT)
                } else {
                    dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_DOWN)
                }
            }
        } else if (left) {
            dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_LEFT)
        } else if (right) {
            dpad.setState(InputOverlayDrawableDpad.STATE_PRESSED_RIGHT)
        }
    }

    private fun addHotkeyOverlayControls(orientation: String) {
        val hotkeyBase = "MAIN_BUTTON_TOGGLE_HOTKEY_"
        val gameId = NativeLibrary.GetCurrentGameID()
        val isGameCube = gameId?.startsWith("G") == true
        val hotkeyButtons = mutableListOf(
            Triple(ButtonType.HOTKEY_SAVE_STATE_1, "Save1", 0),
            Triple(ButtonType.HOTKEY_SAVE_STATE_2, "Save2", 1),
            Triple(ButtonType.HOTKEY_LOAD_STATE_1, "Load1", 2),
            Triple(ButtonType.HOTKEY_LOAD_STATE_2, "Load2", 3),
            Triple(ButtonType.HOTKEY_TOGGLE_PAUSE, "Pause", 4),
            Triple(ButtonType.HOTKEY_TOGGLE_SKIP_EFB, "Skip\nEFB", 5),
            Triple(ButtonType.HOTKEY_TOGGLE_IGNORE_FORMAT, "Ignore\nFormat", 6),
            Triple(ButtonType.HOTKEY_TOGGLE_EFB_TEXTURE, "Store\nEFB", 7),
        )

        if (!isGameCube) {
            hotkeyButtons.add(Triple(ButtonType.HOTKEY_TOGGLE_IR_RECENTER, "IR\nRecenter", 8))
            hotkeyButtons.add(Triple(ButtonType.HOTKEY_TOGGLE_WIIMOTE_UPRIGHT, "Upright\nWiimote", 9))
            hotkeyButtons.add(Triple(ButtonType.HOTKEY_TOGGLE_WIIMOTE_SIDEWAYS, "Sideways\nWiimote", 10))
        }

        for ((buttonType, label, index) in hotkeyButtons) {
            if (getEffectiveToggle(hotkeyBase + index, orientation)) {
                overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.wiimote_em,
                        R.drawable.wiimote_em_pressed,
                        buttonType,
                        -1,
                        orientation,
                        false,
                        label,
                        isHotkeyButton = true
                    )
                )
            }
        }
    }

    private fun addGameCubeOverlayControls(orientation: String) {
        val toggleBase = "MAIN_BUTTON_TOGGLE_GC_"
        val latchingBase = "MAIN_BUTTON_LATCHING_GC_"

        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_0", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_a,
                    R.drawable.gcpad_a_pressed,
                    ButtonType.BUTTON_A,
                    ControlId.GCPAD_A_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_0", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_1", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_b,
                    R.drawable.gcpad_b_pressed,
                    ButtonType.BUTTON_B,
                    ControlId.GCPAD_B_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_1", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_2", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_x,
                    R.drawable.gcpad_x_pressed,
                    ButtonType.BUTTON_X,
                    ControlId.GCPAD_X_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_2", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_3", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_y,
                    R.drawable.gcpad_y_pressed,
                    ButtonType.BUTTON_Y,
                    ControlId.GCPAD_Y_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_3", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_4", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_z,
                    R.drawable.gcpad_z_pressed,
                    ButtonType.BUTTON_Z,
                    ControlId.GCPAD_Z_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_4", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_5", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_start,
                    R.drawable.gcpad_start_pressed,
                    ButtonType.BUTTON_START,
                    ControlId.GCPAD_START_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_5", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_6", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_l,
                    R.drawable.gcpad_l_pressed,
                    ButtonType.TRIGGER_L,
                    ControlId.GCPAD_L_DIGITAL,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_6", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_7", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.gcpad_r,
                    R.drawable.gcpad_r_pressed,
                    ButtonType.TRIGGER_R,
                    ControlId.GCPAD_R_DIGITAL,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_GC_7", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_8", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad,
                    R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.BUTTON_UP,
                    ControlId.GCPAD_DPAD_UP,
                    ControlId.GCPAD_DPAD_DOWN,
                    ControlId.GCPAD_DPAD_LEFT,
                    ControlId.GCPAD_DPAD_RIGHT,
                    orientation
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_9", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.STICK_MAIN,
                    ControlId.GCPAD_MAIN_STICK_X,
                    ControlId.GCPAD_MAIN_STICK_Y,
                    orientation
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_GC_10", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcpad_c,
                    R.drawable.gcpad_c_pressed,
                    ButtonType.STICK_C,
                    ControlId.GCPAD_C_STICK_X,
                    ControlId.GCPAD_C_STICK_Y,
                    orientation
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "11", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_l,
                    R.drawable.classic_l_pressed,
                    ButtonType.TRIGGER_L_HALF,
                    ControlId.GCPAD_L_ANALOG,
                    orientation,
                    getEffectiveLatching(latchingBase + "8", orientation),
                    "LH",
                    overlayLabelScale = 0.14f,
                    isAnalogOnly = true,
                    analogPressValue = TRIGGER_HALF_PRESS_VALUE
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "12", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_r,
                    R.drawable.classic_r_pressed,
                    ButtonType.TRIGGER_R_HALF,
                    ControlId.GCPAD_R_ANALOG,
                    orientation,
                    getEffectiveLatching(latchingBase + "9", orientation),
                    "RH",
                    overlayLabelScale = 0.14f,
                    isAnalogOnly = true,
                    analogPressValue = TRIGGER_HALF_PRESS_VALUE
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "13", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.TRIGGER_ANALOG_STICK,
                    ControlId.GCPAD_MAIN_STICK_X,
                    ControlId.GCPAD_MAIN_STICK_Y,
                    orientation,
                    "L\nR",
                    isAnalogTriggerStick = true
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "14", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.GC_L_ANALOG_STICK,
                    ControlId.GCPAD_L_ANALOG,
                    ControlId.GCPAD_L_ANALOG,
                    orientation,
                    "LA",
                    isVerticalTriggerStick = true
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "15", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.GC_R_ANALOG_STICK,
                    ControlId.GCPAD_R_ANALOG,
                    ControlId.GCPAD_R_ANALOG,
                    orientation,
                    "RA",
                    isVerticalTriggerStick = true
                )
            )
        }
		addHotkeyOverlayControls(orientation)
    }

    private fun addWiimoteOverlayControls(orientation: String) {
        val toggleBase = "MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_"
        val latchingBase = "MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_"   // Latching independent

        // ==================== Base button ====================
        if (getEffectiveToggle(toggleBase + "0", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_a,
                    R.drawable.wiimote_a_pressed,
                    ButtonType.WIIMOTE_BUTTON_A,
                    ControlId.WIIMOTE_A_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "0", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "1", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_b,
                    R.drawable.wiimote_b_pressed,
                    ButtonType.WIIMOTE_BUTTON_B,
                    ControlId.WIIMOTE_B_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "1", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "2", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_one,
                    R.drawable.wiimote_one_pressed,
                    ButtonType.WIIMOTE_BUTTON_1,
                    ControlId.WIIMOTE_ONE_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "2", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "3", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_two,
                    R.drawable.wiimote_two_pressed,
                    ButtonType.WIIMOTE_BUTTON_2,
                    ControlId.WIIMOTE_TWO_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "3", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "4", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_plus,
                    R.drawable.wiimote_plus_pressed,
                    ButtonType.WIIMOTE_BUTTON_PLUS,
                    ControlId.WIIMOTE_PLUS_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "4", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "5", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_minus,
                    R.drawable.wiimote_minus_pressed,
                    ButtonType.WIIMOTE_BUTTON_MINUS,
                    ControlId.WIIMOTE_MINUS_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "5", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "6", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_home,
                    R.drawable.wiimote_home_pressed,
                    ButtonType.WIIMOTE_BUTTON_HOME,
                    ControlId.WIIMOTE_HOME_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "6", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "7", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad,
                    R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.WIIMOTE_UP,
                    ControlId.WIIMOTE_DPAD_UP,
                    ControlId.WIIMOTE_DPAD_DOWN,
                    ControlId.WIIMOTE_DPAD_LEFT,
                    ControlId.WIIMOTE_DPAD_RIGHT,
                    orientation
                )
            )
        }

        // ==================== Motion Buttons ====================
        if (getEffectiveToggle(toggleBase + "8", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SHAKE_X, ControlId.WIIMOTE_SHAKE_X,
                    orientation, getEffectiveLatching(latchingBase + "7", orientation), "WSKX"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "9", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SHAKE_Y, ControlId.WIIMOTE_SHAKE_Y,
                    orientation, getEffectiveLatching(latchingBase + "8", orientation), "WSKY"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "10", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SHAKE_Z, ControlId.WIIMOTE_SHAKE_Z,
                    orientation, getEffectiveLatching(latchingBase + "9", orientation), "WSKZ"
                )
            )
        }

        if (getEffectiveToggle(toggleBase + "11", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range, R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.WIIMOTE_SWING, ControlId.WIIMOTE_SWING_X, ControlId.WIIMOTE_SWING_Y,
                    orientation, "WSW"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "12", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SWING_FORWARD, ControlId.WIIMOTE_SWING_FORWARD,
                    orientation, false, "WSF"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "13", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SWING_BACKWARD, ControlId.WIIMOTE_SWING_BACKWARD,
                    orientation, false, "WSB"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "14", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad, R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.WIIMOTE_TILT,
                    ControlId.WIIMOTE_TILT_LEFT,
                    ControlId.WIIMOTE_TILT_RIGHT,
                    ControlId.WIIMOTE_TILT_BACKWARD,
                    ControlId.WIIMOTE_TILT_FORWARD,
                    orientation, "WT"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "15", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_FORWARD, ControlId.WIIMOTE_TILT_LEFT,
                    orientation, getEffectiveLatching(latchingBase + "10", orientation), "WTF"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "16", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_BACKWARD, ControlId.WIIMOTE_TILT_RIGHT,
                    orientation, getEffectiveLatching(latchingBase + "11", orientation), "WTB"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "17", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_LEFT, ControlId.WIIMOTE_TILT_BACKWARD,
                    orientation, getEffectiveLatching(latchingBase + "12", orientation), "WTL"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "18", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_RIGHT, ControlId.WIIMOTE_TILT_FORWARD,
                    orientation, getEffectiveLatching(latchingBase + "13", orientation), "WTR"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "19", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range, R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.WIIMOTE_IR,
                    ControlId.WIIMOTE_IR_X,
                    ControlId.WIIMOTE_IR_Y,
                    orientation, "IR"
                )
            )
        }
		addHotkeyOverlayControls(orientation)
    }

    private fun addNunchukOverlayControls(orientation: String) {
        val toggleBase = "MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_"
        val latchingBase = "MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_"   // Latching independent setting

        // ==================== 0-7: Wiimote ====================
        if (getEffectiveToggle(toggleBase + "0", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_a,
                    R.drawable.wiimote_a_pressed,
                    ButtonType.WIIMOTE_BUTTON_A,
                    ControlId.WIIMOTE_A_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "0", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "1", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_b,
                    R.drawable.wiimote_b_pressed,
                    ButtonType.WIIMOTE_BUTTON_B,
                    ControlId.WIIMOTE_B_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "1", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "2", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_one,
                    R.drawable.wiimote_one_pressed,
                    ButtonType.WIIMOTE_BUTTON_1,
                    ControlId.WIIMOTE_ONE_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "2", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "3", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_two,
                    R.drawable.wiimote_two_pressed,
                    ButtonType.WIIMOTE_BUTTON_2,
                    ControlId.WIIMOTE_TWO_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "3", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "4", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_plus,
                    R.drawable.wiimote_plus_pressed,
                    ButtonType.WIIMOTE_BUTTON_PLUS,
                    ControlId.WIIMOTE_PLUS_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "4", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "5", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_minus,
                    R.drawable.wiimote_minus_pressed,
                    ButtonType.WIIMOTE_BUTTON_MINUS,
                    ControlId.WIIMOTE_MINUS_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "5", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "6", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_home,
                    R.drawable.wiimote_home_pressed,
                    ButtonType.WIIMOTE_BUTTON_HOME,
                    ControlId.WIIMOTE_HOME_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "6", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "7", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad,
                    R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.WIIMOTE_UP,
                    ControlId.WIIMOTE_DPAD_UP,
                    ControlId.WIIMOTE_DPAD_DOWN,
                    ControlId.WIIMOTE_DPAD_LEFT,
                    ControlId.WIIMOTE_DPAD_RIGHT,
                    orientation
                )
            )
        }

        // ==================== 8-10: Nunchuk ====================
        if (getEffectiveToggle(toggleBase + "8", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.nunchuk_c,
                    R.drawable.nunchuk_c_pressed,
                    ButtonType.NUNCHUK_BUTTON_C,
                    ControlId.NUNCHUK_C_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "7", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "9", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.nunchuk_z,
                    R.drawable.nunchuk_z_pressed,
                    ButtonType.NUNCHUK_BUTTON_Z,
                    ControlId.NUNCHUK_Z_BUTTON,
                    orientation,
                    getEffectiveLatching(latchingBase + "8", orientation)
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "10", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.NUNCHUK_STICK,
                    ControlId.NUNCHUK_STICK_X,
                    ControlId.NUNCHUK_STICK_Y,
                    orientation
                )
            )
        }

        // ==================== 11-21: Wiimote Motion buttons（Nunchuk mode） ====================
        if (getEffectiveToggle(toggleBase + "11", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SHAKE_X, ControlId.WIIMOTE_SHAKE_X, orientation,
                    getEffectiveLatching(latchingBase + "9", orientation), "WSKX"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "12", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SHAKE_Y, ControlId.WIIMOTE_SHAKE_Y, orientation,
                    getEffectiveLatching(latchingBase + "10", orientation), "WSKY"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "13", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SHAKE_Z, ControlId.WIIMOTE_SHAKE_Z, orientation,
                    getEffectiveLatching(latchingBase + "11", orientation), "WSKZ"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "14", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.WIIMOTE_SWING,
                    ControlId.WIIMOTE_SWING_X,
                    ControlId.WIIMOTE_SWING_Y,
                    orientation,
                    "WSW"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "15", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em,
                    R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SWING_FORWARD,
                    ControlId.WIIMOTE_SWING_FORWARD,
                    orientation,
                    false,
                    "WSF"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "16", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em,
                    R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_SWING_BACKWARD,
                    ControlId.WIIMOTE_SWING_BACKWARD,
                    orientation,
                    false,
                    "WSB"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "17", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad,
                    R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.WIIMOTE_TILT,
                    ControlId.WIIMOTE_TILT_LEFT,
                    ControlId.WIIMOTE_TILT_RIGHT,
                    ControlId.WIIMOTE_TILT_BACKWARD,
                    ControlId.WIIMOTE_TILT_FORWARD,
                    orientation,
                    "WT"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "18", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_FORWARD, ControlId.WIIMOTE_TILT_LEFT, orientation,
                    getEffectiveLatching(latchingBase + "12", orientation), "WTF"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "19", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_BACKWARD, ControlId.WIIMOTE_TILT_RIGHT, orientation,
                    getEffectiveLatching(latchingBase + "13", orientation), "WTB"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "20", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_LEFT, ControlId.WIIMOTE_TILT_BACKWARD, orientation,
                    getEffectiveLatching(latchingBase + "14", orientation), "WTL"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "21", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.WIIMOTE_TILT_RIGHT, ControlId.WIIMOTE_TILT_FORWARD, orientation,
                    getEffectiveLatching(latchingBase + "15", orientation), "WTR"
                )
            )
        }

        // ==================== 22-33: Nunchuk Motion buttons ====================
        if (getEffectiveToggle(toggleBase + "22", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_SHAKE_X, ControlId.NUNCHUK_SHAKE_X, orientation,
                    getEffectiveLatching(latchingBase + "16", orientation), "NSKX"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "23", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_SHAKE_Y, ControlId.NUNCHUK_SHAKE_Y, orientation,
                    getEffectiveLatching(latchingBase + "17", orientation), "NSKY"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "24", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_SHAKE_Z, ControlId.NUNCHUK_SHAKE_Z, orientation,
                    getEffectiveLatching(latchingBase + "18", orientation), "NSKZ"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "25", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.NUNCHUK_SWING,
                    ControlId.NUNCHUK_SWING_X,
                    ControlId.NUNCHUK_SWING_Y,
                    orientation,
                    "NSW"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "26", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em,
                    R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_SWING_FORWARD,
                    ControlId.NUNCHUK_SWING_FORWARD,
                    orientation,
                    false,
                    "NSF"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "27", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_em,
                    R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_SWING_BACKWARD,
                    ControlId.NUNCHUK_SWING_BACKWARD,
                    orientation,
                    false,
                    "NSB"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "28", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad,
                    R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.NUNCHUK_TILT,
                    ControlId.NUNCHUK_TILT_LEFT,
                    ControlId.NUNCHUK_TILT_RIGHT,
                    ControlId.NUNCHUK_TILT_BACKWARD,
                    ControlId.NUNCHUK_TILT_FORWARD,
                    orientation,
                    "NT"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "29", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_TILT_FORWARD, ControlId.NUNCHUK_TILT_LEFT, orientation,
                    getEffectiveLatching(latchingBase + "19", orientation), "NTF"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "30", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_TILT_BACKWARD, ControlId.NUNCHUK_TILT_RIGHT, orientation,
                    getEffectiveLatching(latchingBase + "20", orientation), "NTB"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "31", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_TILT_LEFT, ControlId.NUNCHUK_TILT_BACKWARD, orientation,
                    getEffectiveLatching(latchingBase + "21", orientation), "NTL"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "32", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context, R.drawable.wiimote_em, R.drawable.wiimote_em_pressed,
                    ButtonType.NUNCHUK_TILT_RIGHT, ControlId.NUNCHUK_TILT_FORWARD, orientation,
                    getEffectiveLatching(latchingBase + "22", orientation), "NTR"
                )
            )
        }
        if (getEffectiveToggle(toggleBase + "33", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range, R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.WIIMOTE_IR,
                    ControlId.WIIMOTE_IR_X,
                    ControlId.WIIMOTE_IR_Y,
                    orientation, "IR"
                )
            )
        }
		addHotkeyOverlayControls(orientation)
    }

    private fun addTaTaConOverlayControls(orientation: String) {
        val tataconToggleBase = "MAIN_BUTTON_TOGGLE_TATACON_"
        val tataconLatchBase = "MAIN_BUTTON_LATCHING_TATACON_"

        // 1
        if (getEffectiveToggle(tataconToggleBase + "0", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_one,
                    R.drawable.wiimote_one_pressed,
                    ButtonType.WIIMOTE_BUTTON_1,
                    ControlId.WIIMOTE_ONE_BUTTON,
                    orientation,
                    getEffectiveLatching(tataconLatchBase + "0", orientation)
                )
            )
        }

        // 2
        if (getEffectiveToggle(tataconToggleBase + "1", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_two,
                    R.drawable.wiimote_two_pressed,
                    ButtonType.WIIMOTE_BUTTON_2,
                    ControlId.WIIMOTE_TWO_BUTTON,
                    orientation,
                    getEffectiveLatching(tataconLatchBase + "1", orientation)
                )
            )
        }

        // +
        if (getEffectiveToggle(tataconToggleBase + "2", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_plus,
                    R.drawable.wiimote_plus_pressed,
                    ButtonType.WIIMOTE_BUTTON_PLUS,
                    ControlId.WIIMOTE_PLUS_BUTTON,
                    orientation,
                    getEffectiveLatching(tataconLatchBase + "2", orientation)
                )
            )
        }

        // -
        if (getEffectiveToggle(tataconToggleBase + "3", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_minus,
                    R.drawable.wiimote_minus_pressed,
                    ButtonType.WIIMOTE_BUTTON_MINUS,
                    ControlId.WIIMOTE_MINUS_BUTTON,
                    orientation,
                    getEffectiveLatching(tataconLatchBase + "3", orientation)
                )
            )
        }

        // Home
        if (getEffectiveToggle(tataconToggleBase + "4", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_home,
                    R.drawable.wiimote_home_pressed,
                    ButtonType.WIIMOTE_BUTTON_HOME,
                    ControlId.WIIMOTE_HOME_BUTTON,
                    orientation,
                    getEffectiveLatching(tataconLatchBase + "4", orientation)
                )
            )
        }

        // TaTaCon 4 button
        if (getEffectiveToggle(tataconToggleBase + "5", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.tatacon_rim_left,
                    R.drawable.tatacon_rim_left_pressed,
                    ButtonType.TATACON_RIM_LEFT,
                    ControlId.TATACON_RIM_LEFT,
                    orientation,
                    false
                ).also { it.useAlphaHitTest = true }
            )
        }

        if (getEffectiveToggle(tataconToggleBase + "6", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.tatacon_rim_right,
                    R.drawable.tatacon_rim_right_pressed,
                    ButtonType.TATACON_RIM_RIGHT,
                    ControlId.TATACON_RIM_RIGHT,
                    orientation,
                    false
                ).also { it.useAlphaHitTest = true }
            )
        }

        if (getEffectiveToggle(tataconToggleBase + "7", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.tatacon_center_left,
                    R.drawable.tatacon_center_left_pressed,
                    ButtonType.TATACON_CENTER_LEFT,
                    ControlId.TATACON_CENTER_LEFT,
                    orientation,
                    false
                ).also { it.useAlphaHitTest = true }
            )
        }

        if (getEffectiveToggle(tataconToggleBase + "8", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.tatacon_center_right,
                    R.drawable.tatacon_center_right_pressed,
                    ButtonType.TATACON_CENTER_RIGHT,
                    ControlId.TATACON_CENTER_RIGHT,
                    orientation,
                    false
                ).also { it.useAlphaHitTest = true }
            )
        }
		addHotkeyOverlayControls(orientation)
    }

    private fun addClassicOverlayControls(orientation: String) {
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_0", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_a,
                    R.drawable.classic_a_pressed,
                    ButtonType.CLASSIC_BUTTON_A,
                    ControlId.CLASSIC_A_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_0", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_1", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_b,
                    R.drawable.classic_b_pressed,
                    ButtonType.CLASSIC_BUTTON_B,
                    ControlId.CLASSIC_B_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_1", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_2", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_x,
                    R.drawable.classic_x_pressed,
                    ButtonType.CLASSIC_BUTTON_X,
                    ControlId.CLASSIC_X_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_2", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_3", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_y,
                    R.drawable.classic_y_pressed,
                    ButtonType.CLASSIC_BUTTON_Y,
                    ControlId.CLASSIC_Y_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_3", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_4", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_plus,
                    R.drawable.wiimote_plus_pressed,
                    ButtonType.CLASSIC_BUTTON_PLUS,
                    ControlId.CLASSIC_PLUS_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_4", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_5", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_minus,
                    R.drawable.wiimote_minus_pressed,
                    ButtonType.CLASSIC_BUTTON_MINUS,
                    ControlId.CLASSIC_MINUS_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_5", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_6", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.wiimote_home,
                    R.drawable.wiimote_home_pressed,
                    ButtonType.CLASSIC_BUTTON_HOME,
                    ControlId.CLASSIC_HOME_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_6", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_7", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_l,
                    R.drawable.classic_l_pressed,
                    ButtonType.CLASSIC_TRIGGER_L,
                    ControlId.CLASSIC_L_DIGITAL,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_7", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_8", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_r,
                    R.drawable.classic_r_pressed,
                    ButtonType.CLASSIC_TRIGGER_R,
                    ControlId.CLASSIC_R_DIGITAL,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_8", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_9", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_zl,
                    R.drawable.classic_zl_pressed,
                    ButtonType.CLASSIC_BUTTON_ZL,
                    ControlId.CLASSIC_ZL_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_9", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_10", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_zr,
                    R.drawable.classic_zr_pressed,
                    ButtonType.CLASSIC_BUTTON_ZR,
                    ControlId.CLASSIC_ZR_BUTTON,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_10", orientation)
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_11", orientation)) {
            overlayDpads.add(
                initializeOverlayDpad(
                    context,
                    R.drawable.gcwii_dpad,
                    R.drawable.gcwii_dpad_pressed_one_direction,
                    R.drawable.gcwii_dpad_pressed_two_directions,
                    ButtonType.CLASSIC_DPAD_UP,
                    ControlId.CLASSIC_DPAD_UP,
                    ControlId.CLASSIC_DPAD_DOWN,
                    ControlId.CLASSIC_DPAD_LEFT,
                    ControlId.CLASSIC_DPAD_RIGHT,
                    orientation
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_12", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.CLASSIC_STICK_LEFT,
                    ControlId.CLASSIC_LEFT_STICK_X,
                    ControlId.CLASSIC_LEFT_STICK_Y,
                    orientation
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_13", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.CLASSIC_STICK_RIGHT,
                    ControlId.CLASSIC_RIGHT_STICK_X,
                    ControlId.CLASSIC_RIGHT_STICK_Y,
                    orientation
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_14", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_l,
                    R.drawable.classic_l_pressed,
                    ButtonType.CLASSIC_TRIGGER_L_HALF,
                    ControlId.CLASSIC_L_ANALOG,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_11", orientation),
                    "LH",
                    overlayLabelScale = 0.14f,
                    isAnalogOnly = true,
                    analogPressValue = TRIGGER_HALF_PRESS_VALUE
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_15", orientation)) {
            overlayButtons.add(
                initializeOverlayButton(
                    context,
                    R.drawable.classic_r,
                    R.drawable.classic_r_pressed,
                    ButtonType.CLASSIC_TRIGGER_R_HALF,
                    ControlId.CLASSIC_R_ANALOG,
                    orientation,
                    getEffectiveLatching("MAIN_BUTTON_LATCHING_CLASSIC_12", orientation),
                    "RH",
                    overlayLabelScale = 0.14f,
                    isAnalogOnly = true,
                    analogPressValue = TRIGGER_HALF_PRESS_VALUE
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_16", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.CLASSIC_L_ANALOG_STICK,
                    ControlId.CLASSIC_L_ANALOG,
                    ControlId.CLASSIC_L_ANALOG,
                    orientation,
                    "LA",
                    isVerticalTriggerStick = true
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_17", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.CLASSIC_R_ANALOG_STICK,
                    ControlId.CLASSIC_R_ANALOG,
                    ControlId.CLASSIC_R_ANALOG,
                    orientation,
                    "RA",
                    isVerticalTriggerStick = true
                )
            )
        }
        if (getEffectiveToggle("MAIN_BUTTON_TOGGLE_CLASSIC_18", orientation)) {
            overlayJoysticks.add(
                initializeOverlayJoystick(
                    context,
                    R.drawable.gcwii_joystick_range,
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed,
                    ButtonType.CLASSIC_TRIGGER_ANALOG_STICK,
                    ControlId.CLASSIC_L_ANALOG,
                    ControlId.CLASSIC_L_ANALOG,
                    orientation,
                    "L\nR",
                    isAnalogTriggerStick = true
                )
            )
        }
		addHotkeyOverlayControls(orientation)
    }

    fun refreshControls() {
        unregisterControllers()

        // Remove all the overlay buttons from the HashSet.
        overlayButtons.removeAll(overlayButtons)
        overlayDpads.removeAll(overlayDpads)
        overlayJoysticks.removeAll(overlayJoysticks)

        val orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "-Portrait" else ""

        controllerType = configuredControllerType

        val controllerSetting =
            if (NativeLibrary.IsEmulatingWii()) IntSetting.MAIN_OVERLAY_WII_CONTROLLER else IntSetting.MAIN_OVERLAY_GC_CONTROLLER
        val controllerIndex = controllerSetting.int

        if (BooleanSetting.MAIN_SHOW_INPUT_OVERLAY.boolean) {
            // Add all the enabled overlay items back to the HashSet.
            when (controllerType) {
                OVERLAY_GAMECUBE -> {
                    if (getSettingForSIDevice(controllerIndex).int == DISABLED_GAMECUBE_CONTROLLER && isFirstRun) {
                        Toast.makeText(
                            context,
                            R.string.disabled_gc_overlay_notice,
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    this.controllerIndex = controllerIndex
                    InputOverrider.registerGameCube(this.controllerIndex)
                    gcPadRegistered[this.controllerIndex] = true

                    addGameCubeOverlayControls(orientation)
                }

                OVERLAY_WIIMOTE,
                OVERLAY_WIIMOTE_SIDEWAYS -> {
                    this.controllerIndex = controllerIndex - 4
                    InputOverrider.registerWii(this.controllerIndex)
                    wiimoteRegistered[this.controllerIndex] = true

                    addWiimoteOverlayControls(orientation)
                }

                OVERLAY_WIIMOTE_NUNCHUK -> {
                    this.controllerIndex = controllerIndex - 4
                    InputOverrider.registerWii(this.controllerIndex)
                    wiimoteRegistered[this.controllerIndex] = true

                    addNunchukOverlayControls(orientation)
                }

                OVERLAY_WIIMOTE_CLASSIC -> {
                    this.controllerIndex = controllerIndex - 4
                    InputOverrider.registerWii(this.controllerIndex)
                    wiimoteRegistered[this.controllerIndex] = true

                    addClassicOverlayControls(orientation)
                }

                OVERLAY_WIIMOTE_TATACON -> {
                    this.controllerIndex = controllerIndex - 4
                    InputOverrider.registerWii(this.controllerIndex)
                    wiimoteRegistered[this.controllerIndex] = true

                    addTaTaConOverlayControls(orientation)
                }

                OVERLAY_NONE -> {}
            }
        }

        isFirstRun = false
        invalidate()
    }

    fun refreshOverlayPointer() {
		initTouchPointer()
	}

    fun toggleSidewaysWiimote() {
        setWiimoteSideways(!isWiimoteSideways())
    }

    private fun isWiimoteSideways(): Boolean {
        val wiimoteIndex = when {
            configuredControllerType == OVERLAY_WIIMOTE ||
                configuredControllerType == OVERLAY_WIIMOTE_SIDEWAYS ||
                configuredControllerType == OVERLAY_WIIMOTE_NUNCHUK ||
                configuredControllerType == OVERLAY_WIIMOTE_CLASSIC -> controllerIndex

            else -> return false
        }
        val setting = EmulatedController.getSidewaysWiimoteSetting(wiimoteIndex)
        val mappingSetting = InputMappingBooleanSetting(setting)
        return mappingSetting.boolean
    }

    private fun setWiimoteSideways(sideways: Boolean, refresh: Boolean = true) {
        val wiimoteIndex = when {
            configuredControllerType == OVERLAY_WIIMOTE ||
                configuredControllerType == OVERLAY_WIIMOTE_SIDEWAYS ||
                configuredControllerType == OVERLAY_WIIMOTE_NUNCHUK ||
                configuredControllerType == OVERLAY_WIIMOTE_CLASSIC -> controllerIndex

            else -> return
        }
        val setting = EmulatedController.getSidewaysWiimoteSetting(wiimoteIndex)
        setting.setBooleanValue(sideways)
        if (refresh) refreshControls() else pendingRefreshControls = true
    }

    fun resetButtonPlacement() {
        // Reset only current game + current orientation, including hotkey and motion
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val gameId = NativeLibrary.GetCurrentGameID()
        val isGlobal = gameId == null
        val currentGameId = gameId ?: "Global"
        val targetOri = if (isLandscape) "" else "-Portrait"

        val editor = preferences.edit()
        // Scan all saved overlay keys - robust for hotkey, motion, tatacon, etc.
        for (key in preferences.all.keys) {
            if (!key.endsWith("-X") && !key.endsWith("-Y")) continue
            // Check orientation match
            val isPortraitKey = key.contains("-Portrait")
            val matchesOri = if (isLandscape) !isPortraitKey else isPortraitKey
            if (!matchesOri) continue

            if (isGlobal) {
                // Global reset: remove keys that are global (no gameId or _Global)
                // Global keys are like BUTTON_A-X or BUTTON_A_Global_W-X or BUTTON_A_Global_H-X etc.
                // Keep per-game keys like GALE01
                val isPerGameKey = '_' in key && !key.contains("_Global") && !key.startsWith("BUTTON_") && !key.startsWith("WIIMOTE_") && !key.startsWith("NUNCHUK_") && !key.startsWith("CLASSIC_") && !key.startsWith("HOTKEY_") && !key.startsWith("STICK_") && !key.startsWith("TRIGGER_") && !key.startsWith("TATACON_")
                // Simpler: if key contains _ and second part is not Global and looks like a gameId (4-6 chars uppercase/digit), skip
                // We detect per-game by: contains _<gameId>_ or _<gameId><ori>
                // For global reset, we only remove if it contains _Global or it does NOT contain a gameId pattern
                // GameId pattern: _[A-Z0-9]{4,6}...
                val containsGameId = Regex("_[A-Z0-9]{4,6}(_|-|$)").containsMatchIn(key) && !key.contains("_Global")
                if (!containsGameId) {
                    editor.remove(key)
                }
            } else {
                // Per-game reset: only remove keys containing currentGameId
                if (key.contains(currentGameId)) {
                    editor.remove(key)
                }
            }
        }
        editor.apply()

        val controller = configuredControllerType
        if (controller == OVERLAY_GAMECUBE) {
            if (isLandscape) gcDefaultOverlay() else gcPortraitDefaultOverlay()
        } else if (controller == OVERLAY_WIIMOTE_CLASSIC) {
            if (isLandscape) wiiClassicDefaultOverlay() else wiiClassicPortraitDefaultOverlay()
        } else {
            if (isLandscape) {
                wiiDefaultOverlay()
                wiiOnlyDefaultOverlay()
            } else {
                wiiPortraitDefaultOverlay()
                wiiOnlyPortraitDefaultOverlay()
            }
        }
        refreshControls()
    }

    private fun saveControlPosition(sharedPrefsId: Int, x: Int, y: Int, orientation: String) {
        preferences.edit()
            .putFloat(getXKey(sharedPrefsId, controllerType, orientation), x.toFloat())
            .putFloat(getYKey(sharedPrefsId, controllerType, orientation), y.toFloat())
            .apply()
    }

    /**
     * Initializes an InputOverlayDrawableButton, given by resId, with all of the
     * parameters set for it to be properly shown on the InputOverlay.
     *
     * This works due to the way the X and Y coordinates are stored within
     * the [SharedPreferences].
     *
     * In the input overlay configuration menu,
     * once a touch event begins and then ends (ie. Organizing the buttons to one's own liking for the overlay).
     * the X and Y coordinates of the button at the END of its touch event
     * (when you remove your finger/stylus from the touchscreen) are then stored
     * within a SharedPreferences instance so that those values can be retrieved here.
     *
     * This has a few benefits over the conventional way of storing the values
     * (ie. within the Dolphin ini file).
     *
     *  * No native calls
     *  * Keeps Android-only values inside the Android environment
     *
     * Technically no modifications should need to be performed on the returned
     * InputOverlayDrawableButton. Simply add it to the HashSet of overlay items and wait
     * for Android to call the onDraw method.
     *
     * @param context      The current [Context].
     * @param defaultResId The resource ID of the [Drawable] to get the [Bitmap] of (Default State).
     * @param pressedResId The resource ID of the [Drawable] to get the [Bitmap] of (Pressed State).
     * @param legacyId     Legacy identifier for the button the InputOverlayDrawableButton represents.
     * @param control      Control identifier for the button the InputOverlayDrawableButton represents.
     * @param latching     Whether the button is latching.
     * @return An [InputOverlayDrawableButton] with the correct drawing bounds set.
     */
    // Per game overlay scale and opacity
    private fun getEffectiveScale(): Int {
        val gameId = NativeLibrary.GetCurrentGameID()
        val orientation = if (resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        ) "land" else "port"
        val key = if (gameId != null) "OverlayScale_${gameId}_$orientation" else null
        return if (key != null)
            preferences.getInt(key, IntSetting.MAIN_CONTROL_SCALE.int)
        else
            IntSetting.MAIN_CONTROL_SCALE.int
    }

    private fun getEffectiveOpacity(): Int {
        val gameId = NativeLibrary.GetCurrentGameID()
        val orientation = if (resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        ) "land" else "port"
        val key = if (gameId != null) "OverlayOpacity_${gameId}_$orientation" else null
        return if (key != null)
            preferences.getInt(key, IntSetting.MAIN_CONTROL_OPACITY.int)
        else
            IntSetting.MAIN_CONTROL_OPACITY.int
    }

    // Fall back to BooleanSetting if no gameID
    // Per-orientation support: checks Toggle_${gameId}_Portrait/Landscape_$settingName first
    private fun getEffectiveToggle(settingName: String, orientation: String = ""): Boolean {
        val gameId = NativeLibrary.GetCurrentGameID()
        if (gameId != null) {
            // Normalize orientation: "-Portrait" -> "Portrait", "" -> "Landscape"
            val orientKey = if (orientation.contains("Portrait")) "Portrait" else "Landscape"
            val perOrientKey = "Toggle_${gameId}_${orientKey}_$settingName"
            if (preferences.contains(perOrientKey)) {
                return preferences.getBoolean(
                    perOrientKey,
                    BooleanSetting.valueOf(settingName).boolean
                )
            }
            // Fallback to old global per-game key for compatibility
            return preferences.getBoolean(
                "Toggle_${gameId}_$settingName",
                BooleanSetting.valueOf(settingName).boolean
            )
        } else {
            return BooleanSetting.valueOf(settingName).boolean
        }
    }

    private fun getEffectiveLatching(settingName: String, orientation: String = ""): Boolean {
        val gameId = NativeLibrary.GetCurrentGameID()
        if (gameId != null) {
            val orientKey = if (orientation.contains("Portrait")) "Portrait" else "Landscape"
            val perOrientKey = "Latching_${gameId}_${orientKey}_$settingName"
            if (preferences.contains(perOrientKey)) {
                return preferences.getBoolean(
                    perOrientKey,
                    BooleanSetting.valueOf(settingName).boolean
                )
            }
            return preferences.getBoolean(
                "Latching_${gameId}_$settingName",
                BooleanSetting.valueOf(settingName).boolean
            )
        } else {
            return BooleanSetting.valueOf(settingName).boolean
        }
    }

    private fun initializeOverlayButton(
        context: Context,
        defaultResId: Int,
        pressedResId: Int,
        legacyId: Int,
        control: Int,
        orientation: String,
        latching: Boolean,
        overlayLabel: String? = null,
        overlayLabelScale: Float = 0.28f,
        isAnalogOnly: Boolean = false,
        analogPressValue: Double = 1.0,
		isHotkeyButton: Boolean = false
    ): InputOverlayDrawableButton {
        // Decide scale based on button ID and user preference
        var scale = when (legacyId) {
            ButtonType.BUTTON_A,
            ButtonType.WIIMOTE_BUTTON_B,
            ButtonType.NUNCHUK_BUTTON_Z -> 0.2f

            ButtonType.BUTTON_X,
            ButtonType.BUTTON_Y -> 0.175f

            ButtonType.BUTTON_Z,
            ButtonType.TRIGGER_L,
            ButtonType.TRIGGER_R -> 0.225f

            ButtonType.BUTTON_START -> 0.075f
            ButtonType.WIIMOTE_BUTTON_1,
            ButtonType.WIIMOTE_BUTTON_2 -> if (controllerType == OVERLAY_WIIMOTE_SIDEWAYS) 0.14f else 0.0875f

            ButtonType.WIIMOTE_BUTTON_PLUS,
            ButtonType.WIIMOTE_BUTTON_MINUS,
            ButtonType.WIIMOTE_BUTTON_HOME,
            ButtonType.CLASSIC_BUTTON_PLUS,
            ButtonType.CLASSIC_BUTTON_MINUS,
            ButtonType.CLASSIC_BUTTON_HOME -> 0.0625f

            ButtonType.TRIGGER_L_HALF,
            ButtonType.TRIGGER_R_HALF,
            ButtonType.CLASSIC_TRIGGER_L_HALF,
            ButtonType.CLASSIC_TRIGGER_R_HALF,
            ButtonType.CLASSIC_TRIGGER_L,
            ButtonType.CLASSIC_TRIGGER_R,
            ButtonType.CLASSIC_BUTTON_ZL,
            ButtonType.CLASSIC_BUTTON_ZR -> 0.25f

            else -> 0.125f
        }

        scale *= (getEffectiveScale() + 50).toFloat()
        scale /= 100f
        // TaTaCon buttons extra scale
        when (legacyId) {
            ButtonType.TATACON_RIM_LEFT,
            ButtonType.TATACON_RIM_RIGHT -> scale *= 7.6f

            ButtonType.TATACON_CENTER_LEFT,
            ButtonType.TATACON_CENTER_RIGHT -> scale *= 5.5f
        }

        // Initialize the InputOverlayDrawableButton.
        val defaultStateBitmap =
            resizeBitmap(context, BitmapFactory.decodeResource(resources, defaultResId), scale)
        val pressedStateBitmap =
            resizeBitmap(context, BitmapFactory.decodeResource(resources, pressedResId), scale)

        val overlayDrawable = InputOverlayDrawableButton(
            resources,
            defaultStateBitmap,
            pressedStateBitmap,
            legacyId,
            control,
            latching,
            overlayLabel,
            overlayLabelScale,
            isAnalogOnly,
            analogPressValue,
			isHotkeyButton
        )

        // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
        // These were set in the input overlay configuration menu.
        val drawableX =
            preferences.getFloat(
                getXKey(legacyId, controllerType, orientation),
                getMotionButtonDefaultX(legacyId, orientation)
            ).toInt()
        val drawableY =
            preferences.getFloat(
                getYKey(legacyId, controllerType, orientation),
                getMotionButtonDefaultY(legacyId, orientation)
            ).toInt()

        val width = overlayDrawable.width
        val height = overlayDrawable.height

        // Now set the bounds for the InputOverlayDrawableButton.
        // This will dictate where on the screen (and the what the size) the InputOverlayDrawableButton will be.
        overlayDrawable.setBounds(drawableX, drawableY, drawableX + width, drawableY + height)

        // Need to set the image's position
        overlayDrawable.setPosition(drawableX, drawableY)
        overlayDrawable.setOpacity(getEffectiveOpacity() * 255 / 100)

        return overlayDrawable
    }

    /**
     * Initializes an [InputOverlayDrawableDpad]
     *
     * @param context                   The current [Context].
     * @param defaultResId              The [Bitmap] resource ID of the default sate.
     * @param pressedOneDirectionResId  The [Bitmap] resource ID of the pressed sate in one direction.
     * @param pressedTwoDirectionsResId The [Bitmap] resource ID of the pressed sate in two directions.
     * @param legacyId                  Legacy identifier for the up button.
     * @param upControl                 Control identifier for the up button.
     * @param downControl               Control identifier for the down button.
     * @param leftControl               Control identifier for the left button.
     * @param rightControl              Control identifier for the right button.
     * @return the initialized [InputOverlayDrawableDpad]
     */
    private fun initializeOverlayDpad(
        context: Context,
        defaultResId: Int,
        pressedOneDirectionResId: Int,
        pressedTwoDirectionsResId: Int,
        legacyId: Int,
        upControl: Int,
        downControl: Int,
        leftControl: Int,
        rightControl: Int,
        orientation: String,
        overlayLabel: String? = null
    ): InputOverlayDrawableDpad {
        // Decide scale based on button ID and user preference
        var scale: Float = when (legacyId) {
            ButtonType.BUTTON_UP -> 0.2375f
            ButtonType.CLASSIC_DPAD_UP -> 0.275f
            else -> if (controllerType == OVERLAY_WIIMOTE_SIDEWAYS || controllerType == OVERLAY_WIIMOTE) 0.275f else 0.2125f
        }

        scale *= (getEffectiveScale() + 50).toFloat()
        scale /= 100f

        // Initialize the InputOverlayDrawableDpad.
        val defaultStateBitmap =
            resizeBitmap(context, BitmapFactory.decodeResource(resources, defaultResId), scale)
        val pressedOneDirectionStateBitmap = resizeBitmap(
            context,
            BitmapFactory.decodeResource(resources, pressedOneDirectionResId),
            scale
        )
        val pressedTwoDirectionsStateBitmap = resizeBitmap(
            context,
            BitmapFactory.decodeResource(resources, pressedTwoDirectionsResId),
            scale
        )
        val overlayDrawable = InputOverlayDrawableDpad(
            resources,
            defaultStateBitmap,
            pressedOneDirectionStateBitmap,
            pressedTwoDirectionsStateBitmap,
            legacyId,
            upControl,
            downControl,
            leftControl,
            rightControl,
            overlayLabel
        )

        // The X and Y coordinates of the InputOverlayDrawableDpad on the InputOverlay.
        // These were set in the input overlay configuration menu.
        val drawableX =
            preferences.getFloat(
                getXKey(legacyId, controllerType, orientation),
                getMotionButtonDefaultX(legacyId, orientation)
            ).toInt()
        val drawableY =
            preferences.getFloat(
                getYKey(legacyId, controllerType, orientation),
                getMotionButtonDefaultY(legacyId, orientation)
            ).toInt()

        val width = overlayDrawable.width
        val height = overlayDrawable.height

        // Now set the bounds for the InputOverlayDrawableDpad.
        // This will dictate where on the screen (and the what the size) the InputOverlayDrawableDpad will be.
        overlayDrawable.setBounds(drawableX, drawableY, drawableX + width, drawableY + height)

        // Need to set the image's position
        overlayDrawable.setPosition(drawableX, drawableY)
        overlayDrawable.setOpacity(getEffectiveOpacity() * 255 / 100)

        return overlayDrawable
    }

    /**
     * Initializes an [InputOverlayDrawableJoystick]
     *
     * @param context         The current [Context]
     * @param resOuter        Resource ID for the outer image of the joystick (the static image that shows the circular bounds).
     * @param defaultResInner Resource ID for the default inner image of the joystick (the one you actually move around).
     * @param pressedResInner Resource ID for the pressed inner image of the joystick.
     * @param legacyId        Legacy identifier (ButtonType) for which joystick this is.
     * @param xControl        Control identifier for the X axis.
     * @param yControl        Control identifier for the Y axis.
     * @return the initialized [InputOverlayDrawableJoystick].
     */
    private fun initializeOverlayJoystick(
        context: Context,
        resOuter: Int,
        defaultResInner: Int,
        pressedResInner: Int,
        legacyId: Int,
        xControl: Int,
        yControl: Int,
        orientation: String,
        overlayLabel: String? = null,
        isAnalogTriggerStick: Boolean = false,
        isVerticalTriggerStick: Boolean = false // LA/RA
    ): InputOverlayDrawableJoystick {
        // Decide scale based on user preference
        var scale = 0.275f
        scale *= (getEffectiveScale() + 50).toFloat()
        scale /= 100f

        // Initialize the InputOverlayDrawableJoystick.
        val bitmapOuter =
            resizeBitmap(context, BitmapFactory.decodeResource(resources, resOuter), scale)
        val bitmapInnerDefault = BitmapFactory.decodeResource(resources, defaultResInner)
        val bitmapInnerPressed = BitmapFactory.decodeResource(resources, pressedResInner)

        // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
        // These were set in the input overlay configuration menu.
        val drawableX =
            preferences.getFloat(
                getXKey(legacyId, controllerType, orientation),
                getMotionButtonDefaultX(legacyId, orientation)
            ).toInt()
        val drawableY =
            preferences.getFloat(
                getYKey(legacyId, controllerType, orientation),
                getMotionButtonDefaultY(legacyId, orientation)
            ).toInt()

        // Decide inner scale based on joystick ID
        val innerScale: Float = if (legacyId == ButtonType.STICK_C) 1.833f else 1.375f

        // Now set the bounds for the InputOverlayDrawableJoystick.
        // This will dictate where on the screen (and the what the size) the InputOverlayDrawableJoystick will be.
        val outerSize = bitmapOuter.width
        val outerRect = Rect(drawableX, drawableY, drawableX + outerSize, drawableY + outerSize)
        val innerRect =
            Rect(0, 0, (outerSize / innerScale).toInt(), (outerSize / innerScale).toInt())

        // Send the drawableId to the joystick so it can be referenced when saving control position.
        val overlayDrawable = InputOverlayDrawableJoystick(
            resources,
            bitmapOuter,
            bitmapInnerDefault,
            bitmapInnerPressed,
            outerRect,
            innerRect,
            legacyId,
            xControl,
            yControl,
            controllerIndex,
            overlayLabel,
            isAnalogTriggerStick,
            isVerticalTriggerStick
        )

        // Need to set the image's position
        overlayDrawable.setPosition(drawableX, drawableY)
        overlayDrawable.setOpacity(getEffectiveOpacity() * 255 / 100)
        return overlayDrawable
    }

    override fun isInEditMode(): Boolean {
        return editMode
    }

    private fun defaultOverlay() {
        if (!preferences.getBoolean("OverlayInitV2", false)) {
            // It's possible that a user has created their overlay before this was added
            // Only change the overlay if the 'A' button is not in the upper corner.
            // GameCube
            if (preferences.getFloat(ButtonType.BUTTON_A.toString() + "-X", 0f) == 0f) {
                gcDefaultOverlay()
            }
            if (preferences.getFloat(
                    ButtonType.BUTTON_A.toString() + "-Portrait" + "-X",
                    0f
                ) == 0f
            ) {
                gcPortraitDefaultOverlay()
            }

            // Wii
            if (preferences.getFloat(ButtonType.WIIMOTE_BUTTON_A.toString() + "-X", 0f) == 0f) {
                wiiDefaultOverlay()
            }
            if (preferences.getFloat(
                    ButtonType.WIIMOTE_BUTTON_A.toString() + "-Portrait" + "-X",
                    0f
                ) == 0f
            ) {
                wiiPortraitDefaultOverlay()
            }

            // Wii Classic
            if (preferences.getFloat(ButtonType.CLASSIC_BUTTON_A.toString() + "-X", 0f) == 0f) {
                wiiClassicDefaultOverlay()
            }
            if (preferences.getFloat(
                    ButtonType.CLASSIC_BUTTON_A.toString() + "-Portrait" + "-X",
                    0f
                ) == 0f
            ) {
                wiiClassicPortraitDefaultOverlay()
            }
        }

        if (!preferences.getBoolean("OverlayInitV3", false)) {
            wiiOnlyDefaultOverlay()
            wiiOnlyPortraitDefaultOverlay()
        }

        preferences.edit()
            .putBoolean("OverlayInitV2", true)
            .putBoolean("OverlayInitV3", true)
            .apply()
    }


    //  Clamp default positions for 20:9 (720x1612) - keeps original logic but adds 8% inset
    private fun clampDefaultX(pct: Int, maxX: Float): Float {
        val raw = pct.toFloat() / 1000f * maxX
        val inset = maxX * 0.08f
        // Keep fully visible, assume button ~100px
        return raw.coerceIn(inset, maxX - inset - 100f)
    }
    private fun clampDefaultY(pct: Int, maxY: Float): Float {
        val raw = pct.toFloat() / 1000f * maxY
        val inset = maxY * 0.08f
        return raw.coerceIn(inset, maxY - inset - 100f)
    }

    private fun gcDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for height.
        if (maxY > maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.BUTTON_A.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_A_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_A.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_A_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_B.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_B_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_B.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_B_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_X.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_X_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_X.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_X_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_Y.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_Y_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_Y.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_Y_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_Z.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_Z_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_Z.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_Z_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_UP.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_UP_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_UP.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_UP_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_L.toString() + "-X",
                clampDefaultX(R.integer.TRIGGER_L_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_L.toString() + "-Y",
                clampDefaultY(R.integer.TRIGGER_L_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_R.toString() + "-X",
                clampDefaultX(R.integer.TRIGGER_R_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_R.toString() + "-Y",
                clampDefaultY(R.integer.TRIGGER_R_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_L_HALF.toString() + "-X",
                clampDefaultX(R.integer.TRIGGER_L_HALF_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_L_HALF.toString() + "-Y",
                clampDefaultY(R.integer.TRIGGER_L_HALF_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_R_HALF.toString() + "-X",
                clampDefaultX(R.integer.TRIGGER_R_HALF_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_R_HALF.toString() + "-Y",
                clampDefaultY(R.integer.TRIGGER_R_HALF_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_START.toString() + "-X",
                clampDefaultX(R.integer.BUTTON_START_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_START.toString() + "-Y",
                clampDefaultY(R.integer.BUTTON_START_Y, maxY)
            )
            .putFloat(
                ButtonType.STICK_C.toString() + "-X",
                clampDefaultX(R.integer.STICK_C_X, maxX)
            )
            .putFloat(
                ButtonType.STICK_C.toString() + "-Y",
                clampDefaultY(R.integer.STICK_C_Y, maxY)
            )
            .putFloat(
                ButtonType.STICK_MAIN.toString() + "-X",
                clampDefaultX(R.integer.STICK_MAIN_X, maxX)
            )
            .putFloat(
                ButtonType.STICK_MAIN.toString() + "-Y",
                clampDefaultY(R.integer.STICK_MAIN_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_ANALOG_STICK.toString() + "-X",
                clampDefaultX(R.integer.TRIGGER_ANALOG_STICK_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_ANALOG_STICK.toString() + "-Y",
                clampDefaultY(R.integer.TRIGGER_ANALOG_STICK_Y, maxY)
            )
            .apply()
    }

    private fun gcPortraitDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for height.
        if (maxY < maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }
        val portrait = "-Portrait"

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.BUTTON_A.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_A_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_A.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_A_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_B.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_B_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_B.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_B_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_X.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_X_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_X.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_X_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_Y.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_Y_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_Y.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_Y_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_Z.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_Z_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_Z.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_Z_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_UP.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_UP_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_UP.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_UP_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_L.toString() + portrait + "-X",
                clampDefaultX(R.integer.TRIGGER_L_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_L.toString() + portrait + "-Y",
                clampDefaultY(R.integer.TRIGGER_L_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_R.toString() + portrait + "-X",
                clampDefaultX(R.integer.TRIGGER_R_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_R.toString() + portrait + "-Y",
                clampDefaultY(R.integer.TRIGGER_R_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_L_HALF.toString() + portrait + "-X",
                clampDefaultX(R.integer.TRIGGER_L_HALF_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_L_HALF.toString() + portrait + "-Y",
                clampDefaultY(R.integer.TRIGGER_L_HALF_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_R_HALF.toString() + portrait + "-X",
                clampDefaultX(R.integer.TRIGGER_R_HALF_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.TRIGGER_R_HALF.toString() + portrait + "-Y",
                clampDefaultY(R.integer.TRIGGER_R_HALF_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.BUTTON_START.toString() + portrait + "-X",
                clampDefaultX(R.integer.BUTTON_START_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.BUTTON_START.toString() + portrait + "-Y",
                clampDefaultY(R.integer.BUTTON_START_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.STICK_C.toString() + portrait + "-X",
                clampDefaultX(R.integer.STICK_C_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.STICK_C.toString() + portrait + "-Y",
                clampDefaultY(R.integer.STICK_C_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.STICK_MAIN.toString() + portrait + "-X",
                clampDefaultX(R.integer.STICK_MAIN_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.STICK_MAIN.toString() + portrait + "-Y",
                clampDefaultY(R.integer.STICK_MAIN_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.TRIGGER_ANALOG_STICK.toString() + portrait + "-X",
                resources.getInteger(R.integer.TRIGGER_ANALOG_STICK_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.TRIGGER_ANALOG_STICK.toString() + portrait + "-Y",
                resources.getInteger(R.integer.TRIGGER_ANALOG_STICK_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .apply()
    }

    private fun wiiDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for maxX.
        if (maxY > maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_A_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_A_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_B_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_B_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_1_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_1_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_2_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_2_Y, maxY)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_Z.toString() + "-X",
                clampDefaultX(R.integer.NUNCHUK_BUTTON_Z_X, maxX)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_Z.toString() + "-Y",
                clampDefaultY(R.integer.NUNCHUK_BUTTON_Z_Y, maxY)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_C.toString() + "-X",
                clampDefaultX(R.integer.NUNCHUK_BUTTON_C_X, maxX)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_C.toString() + "-Y",
                clampDefaultY(R.integer.NUNCHUK_BUTTON_C_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_MINUS.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_MINUS_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_MINUS.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_MINUS_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_PLUS.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_PLUS_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_PLUS.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_PLUS_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_UP_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_UP_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_HOME.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_HOME_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_HOME.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_HOME_Y, maxY)
            )
            .putFloat(
                ButtonType.NUNCHUK_STICK.toString() + "-X",
                clampDefaultX(R.integer.NUNCHUK_STICK_X, maxX)
            )
            .putFloat(
                ButtonType.NUNCHUK_STICK.toString() + "-Y",
                clampDefaultY(R.integer.NUNCHUK_STICK_Y, maxY)
            )
            .apply()
    }

    private fun wiiOnlyDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for maxX.
        if (maxY > maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + "_H-X",
                clampDefaultX(R.integer.WIIMOTE_H_BUTTON_A_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + "_H-Y",
                clampDefaultY(R.integer.WIIMOTE_H_BUTTON_A_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + "_H-X",
                clampDefaultX(R.integer.WIIMOTE_H_BUTTON_B_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + "_H-Y",
                clampDefaultY(R.integer.WIIMOTE_H_BUTTON_B_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + "_H-X",
                clampDefaultX(R.integer.WIIMOTE_H_BUTTON_1_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + "_H-Y",
                clampDefaultY(R.integer.WIIMOTE_H_BUTTON_1_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + "_H-X",
                clampDefaultX(R.integer.WIIMOTE_H_BUTTON_2_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + "_H-Y",
                clampDefaultY(R.integer.WIIMOTE_H_BUTTON_2_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + "_O-X",
                clampDefaultX(R.integer.WIIMOTE_O_UP_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + "_O-Y",
                clampDefaultY(R.integer.WIIMOTE_O_UP_Y, maxY)
            )
            // Horizontal dpad
            .putFloat(
                ButtonType.WIIMOTE_RIGHT.toString() + "-X",
                clampDefaultX(R.integer.WIIMOTE_RIGHT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_RIGHT.toString() + "-Y",
                clampDefaultY(R.integer.WIIMOTE_RIGHT_Y, maxY)
            )
            .apply()
    }

    private fun wiiPortraitDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for maxX.
        if (maxY < maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }
        val portrait = "-Portrait"

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_A_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_A_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_B_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_B_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_1_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_1_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_BUTTON_2_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_BUTTON_2_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_Z.toString() + portrait + "-X",
                clampDefaultX(R.integer.NUNCHUK_BUTTON_Z_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_Z.toString() + portrait + "-Y",
                clampDefaultY(R.integer.NUNCHUK_BUTTON_Z_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_C.toString() + portrait + "-X",
                clampDefaultX(R.integer.NUNCHUK_BUTTON_C_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.NUNCHUK_BUTTON_C.toString() + portrait + "-Y",
                clampDefaultY(R.integer.NUNCHUK_BUTTON_C_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_MINUS.toString() + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_BUTTON_MINUS_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_MINUS.toString() + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_BUTTON_MINUS_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_PLUS.toString() + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_BUTTON_PLUS_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_PLUS.toString() + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_BUTTON_PLUS_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_UP_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_UP_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_HOME.toString() + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_BUTTON_HOME_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_HOME.toString() + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_BUTTON_HOME_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.NUNCHUK_STICK.toString() + portrait + "-X",
                clampDefaultX(R.integer.NUNCHUK_STICK_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.NUNCHUK_STICK.toString() + portrait + "-Y",
                clampDefaultY(R.integer.NUNCHUK_STICK_PORTRAIT_Y, maxY)
            )
            // Horizontal dpad
            .putFloat(
                ButtonType.WIIMOTE_RIGHT.toString() + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_RIGHT_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_RIGHT.toString() + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_RIGHT_PORTRAIT_Y, maxY)
            )
            .apply()
    }

    private fun wiiOnlyPortraitDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for maxX.
        if (maxY < maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }
        val portrait = "-Portrait"

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + "_H" + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_A_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_A.toString() + "_H" + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_A_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + "_H" + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_B_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_B.toString() + "_H" + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_B_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + "_H" + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_1_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_1.toString() + "_H" + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_1_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + "_H" + portrait + "-X",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_2_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.WIIMOTE_BUTTON_2.toString() + "_H" + portrait + "-Y",
                resources.getInteger(R.integer.WIIMOTE_H_BUTTON_2_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + "_O" + portrait + "-X",
                clampDefaultX(R.integer.WIIMOTE_O_UP_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.WIIMOTE_UP.toString() + "_O" + portrait + "-Y",
                clampDefaultY(R.integer.WIIMOTE_O_UP_PORTRAIT_Y, maxY)
            )
            .apply()
    }

    private fun wiiClassicDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for maxX.
        if (maxY > maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.CLASSIC_BUTTON_A.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_A_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_A.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_A_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_B.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_B_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_B.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_B_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_X.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_X_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_X.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_X_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_Y.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_Y_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_Y.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_Y_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_MINUS.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_MINUS_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_MINUS.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_MINUS_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_PLUS.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_PLUS_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_PLUS.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_PLUS_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_HOME.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_HOME_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_HOME.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_HOME_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZL.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_ZL_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZL.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_ZL_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZR.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_ZR_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZR.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_ZR_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_DPAD_UP.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_DPAD_UP_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_DPAD_UP.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_DPAD_UP_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_LEFT.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_STICK_LEFT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_LEFT.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_STICK_LEFT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_RIGHT.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_STICK_RIGHT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_RIGHT.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_STICK_RIGHT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_L.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_TRIGGER_L_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_L.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_TRIGGER_L_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_R.toString() + "-X",
                clampDefaultX(R.integer.CLASSIC_TRIGGER_R_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_R.toString() + "-Y",
                clampDefaultY(R.integer.CLASSIC_TRIGGER_R_Y, maxY)
            )
            .apply()
    }

    private fun wiiClassicPortraitDefaultOverlay() {
        // Get screen size
        val display = (context as Activity).windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        var maxX = outMetrics.heightPixels.toFloat()
        var maxY = outMetrics.widthPixels.toFloat()
        // Height and width changes depending on orientation. Use the larger value for maxX.
        if (maxY < maxX) {
            val tmp = maxX
            maxX = maxY
            maxY = tmp
        }
        val portrait = "-Portrait"

        // Each value is a percent from max X/Y stored as an int. Have to bring that value down
        // to a decimal before multiplying by MAX X/Y.
        preferences.edit()
            .putFloat(
                ButtonType.CLASSIC_BUTTON_A.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_A_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_A.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_A_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_B.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_B_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_B.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_B_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_X.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_X_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_X.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_X_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_Y.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_Y_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_Y.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_Y_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_MINUS.toString() + portrait + "-X",
                resources.getInteger(R.integer.CLASSIC_BUTTON_MINUS_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_MINUS.toString() + portrait + "-Y",
                resources.getInteger(R.integer.CLASSIC_BUTTON_MINUS_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_PLUS.toString() + portrait + "-X",
                resources.getInteger(R.integer.CLASSIC_BUTTON_PLUS_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_PLUS.toString() + portrait + "-Y",
                resources.getInteger(R.integer.CLASSIC_BUTTON_PLUS_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_HOME.toString() + portrait + "-X",
                resources.getInteger(R.integer.CLASSIC_BUTTON_HOME_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_HOME.toString() + portrait + "-Y",
                resources.getInteger(R.integer.CLASSIC_BUTTON_HOME_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZL.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_ZL_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZL.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_ZL_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZR.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_BUTTON_ZR_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_BUTTON_ZR.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_BUTTON_ZR_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_DPAD_UP.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_DPAD_UP_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_DPAD_UP.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_DPAD_UP_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_LEFT.toString() + portrait + "-X",
                resources.getInteger(R.integer.CLASSIC_STICK_LEFT_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_LEFT.toString() + portrait + "-Y",
                resources.getInteger(R.integer.CLASSIC_STICK_LEFT_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_RIGHT.toString() + portrait + "-X",
                resources.getInteger(R.integer.CLASSIC_STICK_RIGHT_PORTRAIT_X)
                    .toFloat() / 1000 * maxX
            )
            .putFloat(
                ButtonType.CLASSIC_STICK_RIGHT.toString() + portrait + "-Y",
                resources.getInteger(R.integer.CLASSIC_STICK_RIGHT_PORTRAIT_Y)
                    .toFloat() / 1000 * maxY
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_L.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_TRIGGER_L_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_L.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_TRIGGER_L_PORTRAIT_Y, maxY)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_R.toString() + portrait + "-X",
                clampDefaultX(R.integer.CLASSIC_TRIGGER_R_PORTRAIT_X, maxX)
            )
            .putFloat(
                ButtonType.CLASSIC_TRIGGER_R.toString() + portrait + "-Y",
                clampDefaultY(R.integer.CLASSIC_TRIGGER_R_PORTRAIT_Y, maxY)
            )
            .apply()
    }

    companion object {
        /** Overlay button press vibration duration (ms). */
        private const val HAPTIC_BUTTON_MS = 80L
        /** Tatacon rim/center pads — stronger hit (ms). */
        private const val HAPTIC_TATACON_MS = 120L
        /** D-pad direction engage vibration duration (ms). */
        private const val HAPTIC_DPAD_MS = 30L
        /** Joystick max-throw edge vibration duration (ms). */
        private const val HAPTIC_JOYSTICK_MS = 30L

        const val OVERLAY_GAMECUBE = 0
        const val OVERLAY_WIIMOTE = 1
        const val OVERLAY_WIIMOTE_SIDEWAYS = 2
        const val OVERLAY_WIIMOTE_NUNCHUK = 3
        const val OVERLAY_WIIMOTE_CLASSIC = 4
        const val OVERLAY_NONE = 5
        const val OVERLAY_WIIMOTE_TATACON = 6

        // Analog trigger half-press (below default 90% digital threshold).
        private const val TRIGGER_HALF_PRESS_VALUE = 0.5

        private const val DISABLED_GAMECUBE_CONTROLLER = 0
        private const val EMULATED_GAMECUBE_CONTROLLER = 6
        private const val EMULATED_AM_BASEBOARD = 11
        private const val GAMECUBE_ADAPTER = 12

        // Buttons that have special positions in Wiimote only
        private val WIIMOTE_H_BUTTONS = ArrayList<Int>()

        init {
            WIIMOTE_H_BUTTONS.add(ButtonType.WIIMOTE_BUTTON_A)
            WIIMOTE_H_BUTTONS.add(ButtonType.WIIMOTE_BUTTON_B)
            WIIMOTE_H_BUTTONS.add(ButtonType.WIIMOTE_BUTTON_1)
            WIIMOTE_H_BUTTONS.add(ButtonType.WIIMOTE_BUTTON_2)
        }

        private val WIIMOTE_O_BUTTONS = ArrayList<Int>()

        init {
            WIIMOTE_O_BUTTONS.add(ButtonType.WIIMOTE_UP)
        }

        /**
         * Resizes a [Bitmap] by a given scale factor
         *
         * @param context The current [Context]
         * @param bitmap  The [Bitmap] to scale.
         * @param scale   The scale factor for the bitmap.
         * @return The scaled [Bitmap]
         */
        fun resizeBitmap(context: Context, bitmap: Bitmap, scale: Float): Bitmap {
            // Determine the button size based on the smaller screen dimension.
            // This makes sure the buttons are the same size in both portrait and landscape.
            val dm = context.resources.displayMetrics
            val minScreenDimension = dm.widthPixels.coerceAtMost(dm.heightPixels)

            val maxBitmapDimension = bitmap.width.coerceAtLeast(bitmap.height)
            val bitmapScale = scale * minScreenDimension / maxBitmapDimension

            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * bitmapScale).toInt(),
                (bitmap.height * bitmapScale).toInt(),
                true
            )
        }

        @JvmStatic
        val configuredControllerType: Int
            get() {
                val controllerSetting =
                    if (NativeLibrary.IsEmulatingWii()) IntSetting.MAIN_OVERLAY_WII_CONTROLLER else IntSetting.MAIN_OVERLAY_GC_CONTROLLER
                val controllerIndex = controllerSetting.int

                if (controllerIndex in 0 until 4) {
                    // GameCube controller
                    when (getSettingForSIDevice(controllerIndex).int) {
                        EMULATED_GAMECUBE_CONTROLLER, EMULATED_AM_BASEBOARD -> {
                            return OVERLAY_GAMECUBE
                        }
                    }
                } else if (controllerIndex in 4 until 8) {
                    // Wii Remote
                    val wiimoteIndex = controllerIndex - 4
                    if (getSettingForWiimoteSource(wiimoteIndex).int == 1) {
                        when (EmulatedController.getSelectedWiimoteAttachment(wiimoteIndex)) {
                            1 -> return OVERLAY_WIIMOTE_NUNCHUK
                            2 -> return OVERLAY_WIIMOTE_CLASSIC
                            8 -> return OVERLAY_WIIMOTE_TATACON
                        }

                        val sidewaysSetting =
                            EmulatedController.getSidewaysWiimoteSetting(wiimoteIndex)
                        val sideways: Boolean =
                            InputMappingBooleanSetting(sidewaysSetting).boolean

                        return if (sideways) OVERLAY_WIIMOTE_SIDEWAYS else OVERLAY_WIIMOTE
                    }
                }
                return OVERLAY_NONE
            }

        private fun getKey(
            sharedPrefsId: Int,
            controller: Int,
            orientation: String,
            suffix: String
        ): String {
            val gameId = NativeLibrary.GetCurrentGameID() ?: "Global"

            var key = "${sharedPrefsId}_${gameId}${orientation}"

            // ==================== Controller type suffix ====================
            when (controller) {
                OVERLAY_WIIMOTE_NUNCHUK -> key += "_N"
                OVERLAY_WIIMOTE_TATACON -> key += "_T"
                else -> key += "_W"                    // Wiimote and Sideways
            }

            if (controller == OVERLAY_WIIMOTE_SIDEWAYS && WIIMOTE_H_BUTTONS.contains(sharedPrefsId)) {
                key = "${sharedPrefsId}_${gameId}_H${orientation}"
            } else if (controller == OVERLAY_WIIMOTE && WIIMOTE_O_BUTTONS.contains(sharedPrefsId)) {
                key = "${sharedPrefsId}_${gameId}_O${orientation}"
            }

            return key + suffix
        }

        private fun getXKey(sharedPrefsId: Int, controller: Int, orientation: String): String =
            getKey(sharedPrefsId, controller, orientation, "-X")

        private fun getYKey(sharedPrefsId: Int, controller: Int, orientation: String): String =
            getKey(sharedPrefsId, controller, orientation, "-Y")

    }
}
