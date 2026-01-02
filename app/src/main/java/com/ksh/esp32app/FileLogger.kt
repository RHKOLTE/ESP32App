package com.ksh.esp32app

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A singleton object for logging messages to a file.
 * This logger writes messages with timestamps to a file named "app_log.txt" in the app's external files directory.
 * It needs to be initialized with an Android [Context] before use.
 */
object FileLogger {
    private var writer: PrintWriter? = null
    private const val LOG_FILE_NAME = "app_log.txt"
    private var logFile: File? = null
    private var context: Context? = null

    /**
     * Initializes the logger.
     * This method sets up the log file and the [PrintWriter] to write to it.
     * It should be called once, typically in the Application's `onCreate` method.
     *
     * @param context The application context, used to access the file system.
     */
    fun initialize(context: Context) {
        this.context = context
        try {
            val logDir = context.getExternalFilesDir(null)
            if (logDir != null) {
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                logFile = File(logDir, LOG_FILE_NAME)
                // Open the FileWriter in append mode.
                val fileWriter = FileWriter(logFile, true)
                writer = PrintWriter(fileWriter)
                log("--- Log Initialized in ${logFile?.absolutePath} ---")
            }
        } catch (e: IOException) {
            // If an error occurs during initialization, print the stack trace.
            e.printStackTrace()
        }
    }

    /**
     * Returns the log file.
     *
     * @return The [File] object representing the log file, or null if the logger has not been initialized.
     */
    fun getLogFile(): File? {
        return logFile
    }

    /**
     * Clears the log file.
     * This method closes the current writer, deletes the log file, and then re-initializes the logger,
     * creating a new, empty log file.
     */
    fun clear() {
        try {
            writer?.close()
            logFile?.delete()
            // Re-initialize the logger after clearing.
            context?.let { initialize(it) }
            log("--- Log Cleared ---")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Logs a simple message to the file.
     * The message is prepended with a timestamp.
     *
     * @param message The message to log.
     */
    fun log(message: String) {
        writer?.let {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            it.println("$timestamp: $message")
            it.flush() // Ensure the message is written to the file immediately.
        }
    }

    /**
     * Logs a message with a tag.
     * This is a convenience method to format the log message with a tag, similar to Android's `Log` class.
     *
     * @param tag The tag for the log message.
     * @param message The message to log.
     */
    fun log(tag: String, message: String) {
        log("[$tag] $message")
    }

    /**
     * Logs a message with a tag and a throwable.
     * This logs the message and then prints the stack trace of the throwable to the log file.
     *
     * @param tag The tag for the log message.
     * @param message The message to log.
     * @param tr The throwable (e.g., an exception) to log.
     */
    fun log(tag: String, message: String, tr: Throwable) {
        log("[$tag] $message. Exception: ${tr.message}")
        writer?.let {
            tr.printStackTrace(it)
            it.flush()
        }
    }

    /**
     * Closes the logger.
     * This closes the [PrintWriter] and releases any associated resources.
     * It should be called when the application is shutting down.
     */
    fun close() {
        try {
            log("--- Log Closed ---")
            writer?.close()
            writer = null
            logFile = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
