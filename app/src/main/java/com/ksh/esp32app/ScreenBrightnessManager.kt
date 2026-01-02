package com.ksh.esp32app

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.WindowManager

/**
 * A singleton object to manage the screen's brightness and prevent the device from sleeping.
 * This manager uses a combination of a PowerManager WakeLock and WindowManager flags to
 * keep the screen on and at a specified brightness level.
 */
object ScreenBrightnessManager {
    private var wakeLock: PowerManager.WakeLock? = null
    private var activity: Activity? = null
    private const val BRIGHTNESS_LEVEL = 0.5f // 50% brightness

    /**
     * Acquires the necessary locks and settings to keep the screen on and bright.
     * This method does three things:
     * 1. Acquires a `FULL_WAKE_LOCK` to ensure the CPU and screen stay on.
     * 2. Sets WindowManager flags (`FLAG_KEEP_SCREEN_ON`, etc.) which is the modern preferred way to keep the screen on.
     * 3. Sets the screen brightness of the activity's window to a fixed level (50%).
     *
     * @param context The application context, used to get the PowerManager service.
     * @param activity The current activity, used to manipulate window flags and brightness.
     */
    fun acquireScreenBrightnessLock(context: Context, activity: Activity) {
        this.activity = activity

        // Method 1: Acquire a full wake lock to keep the screen and CPU running.
        if (wakeLock == null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "esp32app:screen_brightness_wakelock"
            ).apply {
                acquire()
            }
            FileLogger.log("ScreenBrightnessManager", "FULL_WAKE_LOCK acquired")
        }

        // Method 2: Add WindowManager flags to the activity to keep the screen on and show the activity over the lockscreen.
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        FileLogger.log("ScreenBrightnessManager", "WindowManager flags applied")

        // Method 3: Manually set the screen brightness for the current window.
        setScreenBrightness(activity, BRIGHTNESS_LEVEL)
        FileLogger.log("ScreenBrightnessManager", "Screen brightness set to 50%")
    }

    /**
     * Releases all locks and resets screen settings to their default state.
     * This method should be called when the app no longer needs to keep the screen on.
     */
    fun releaseScreenBrightnessLock() {
        // Release the wake lock if it is held.
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                FileLogger.log("ScreenBrightnessManager", "FULL_WAKE_LOCK released")
            }
        }
        wakeLock = null

        // Remove the WindowManager flags from the activity.
        activity?.let {
            it.window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            FileLogger.log("ScreenBrightnessManager", "WindowManager flags removed")

            // Reset the screen brightness to the system's default setting.
            setScreenBrightness(it, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            FileLogger.log("ScreenBrightnessManager", "Screen brightness reset to system default")
        }
        activity = null
    }

    /**
     * A helper function to set the screen brightness for a given activity's window.
     *
     * @param activity The activity whose brightness should be changed.
     * @param brightness The brightness value, from 0.0f to 1.0f. Use `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` to use the system default.
     */
    private fun setScreenBrightness(activity: Activity, brightness: Float) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = brightness
        activity.window.attributes = layoutParams
    }

    /**
     * Checks if the PowerManager wake lock is currently held.
     *
     * @return True if the wake lock is held, false otherwise.
     */
    fun isScreenLockHeld(): Boolean {
        return wakeLock?.isHeld ?: false
    }
}