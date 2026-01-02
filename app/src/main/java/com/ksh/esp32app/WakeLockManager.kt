package com.ksh.esp32app

import android.content.Context
import android.os.PowerManager

/**
 * A singleton object to manage a wake lock for the application.
 * A wake lock is a mechanism to indicate that your application needs to keep the device on.
 * This manager handles acquiring and releasing a `SCREEN_DIM_WAKE_LOCK`,
 * which keeps the screen on, but allows it to dim.
 */
object WakeLockManager {
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Acquires the wake lock if it has not already been acquired.
     * The wake lock will time out after 10 minutes.
     *
     * @param context The application context, used to get the [PowerManager] system service.
     */
    fun acquireWakeLock(context: Context) {
        // Check if the wake lock is already held to prevent multiple acquisitions.
        if (wakeLock == null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK, // Keeps the screen on, but allows it to dim.
                "esp32app:serial_wakelock" // A tag for debugging purposes.
            ).apply {
                // Acquire the wake lock with a 10-minute timeout.
                acquire(10 * 60 * 1000L)
            }
            FileLogger.log("WakeLockManager", "WakeLock acquired")
        }
    }

    /**
     * Releases the wake lock if it is currently held.
     * It's important to release the wake lock when it's no longer needed to conserve battery.
     */
    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                FileLogger.log("WakeLockManager", "WakeLock released")
            }
        }
        wakeLock = null
    }

    /**
     * Checks if the wake lock is currently held.
     *
     * @return True if the wake lock is held, false otherwise.
     */
    fun isWakeLockHeld(): Boolean {
        return wakeLock?.isHeld ?: false
    }
}