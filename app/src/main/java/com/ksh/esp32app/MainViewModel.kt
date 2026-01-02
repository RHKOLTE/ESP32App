package com.ksh.esp32app

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.Charset
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents the state of the UI.
 * This data class holds all the properties that the UI uses to render itself.
 * It is serializable so that it can be saved and restored from a file.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UiState(
    // Connection Properties
    /** The list of available USB serial drivers (devices). Not serialized. */
    @Transient
    val devices: List<UsbSerialDriver> = emptyList(),
    /** The currently selected USB serial driver. Not serialized. */
    @Transient
    val selectedDevice: UsbSerialDriver? = null,
    /** True if the app is currently connected to a device. Not serialized. */
    @Transient
    val isConnected: Boolean = false,

    // Serial Communication Settings
    /** The baud rate for serial communication (e.g., 9600, 115200). */
    val baudRate: Int = 115200,
    /** The number of data bits for serial communication (5, 6, 7, or 8). */
    val dataBits: Int = 8,
    /** The number of stop bits for serial communication (1 or 2). */
    val stopBits: Int = 1,
    /** The parity for serial communication (None, Even, Odd). */
    val parity: Int = UsbSerialPort.PARITY_NONE,
    /** The flow control setting (currently unused). */
    val flowControl: Int = 0,
    /** If true, the status of the control lines (RTS, CTS, etc.) is displayed. */
    val showControlLines: Boolean = false,

    // Control Line Status
    /** The status of the Request to Send (RTS) line. Not serialized. */
    @Transient
    val rts: Boolean = false,
    /** The status of the Clear to Send (CTS) line. Not serialized. */
    @Transient
    val cts: Boolean = false,
    /** The status of the Data Terminal Ready (DTR) line. Not serialized. */
    @Transient
    val dtr: Boolean = false,
    /** The status of the Data Set Ready (DSR) line. Not serialized. */
    @Transient
    val dsr: Boolean = false,
    /** The status of the Carrier Detect (CD) line. Not serialized. */
    @Transient
    val cd: Boolean = false,
    /** The status of the Ring Indicator (RI) line. Not serialized. */
    @Transient
    val ri: Boolean = false,

    // Terminal Display Settings
    /** The list of lines currently displayed in the terminal. Not serialized. */
    @Transient
    val terminalLines: List<TerminalLine> = emptyList(),
    /** The font size for the terminal text in sp. */
    val fontSize: Int = 14,
    /** The font style for the terminal text ("Normal", "Bold", "Italic"). */
    val fontStyle: String = "Normal",
    /** The character set to use for decoding incoming data (e.g., "UTF-8"). */
    val charset: String = "UTF-8",
    /** The color of the incoming terminal text. */
    val textColor: String = "Black",
    /** The display mode for terminal data ("Text" or "Hex"). */
    val displayMode: String = "Text",
    /** If true, the terminal automatically scrolls to the end of the buffer. */
    val autoScroll: Boolean = true,
    /** If true, connection status messages ("Connected", "Disconnected") are shown. */
    val showConnectionMessages: Boolean = true,
    /** The quiet period in seconds to wait before processing data after connection. */
    val quietPeriod: Int = 30,
    /** If true, a timestamp is prepended to each line in the terminal. */
    val showTimestamps: Boolean = false,
    /** The format for the timestamps, using SimpleDateFormat pattern. */
    val timestampFormat: String = "HH:mm:ss.SSS",
    /** The maximum number of lines to keep in the terminal buffer. */
    val maxLines: Int = 1000,

    // Data Sending Settings
    /** The newline character(s) to append to sent data ("CR", "LF", "CR+LF"). */
    val sendNewline: String = "LF",
    /** If true, data sent from the app is echoed locally to the terminal. */
    val localEcho: Boolean = true,
    /** The color of the echoed text. */
    val echoColor: String = "Green",

    // Miscellaneous Settings
    /** If true, logs are written to a file. */
    val isLogToFile: Boolean = false,
    /** If true, more detailed information is logged. */
    val detailedLogging: Boolean = false,
    /** The maximum size of the log file in megabytes. */
    val logFileSize: Int = 1,
    /** The number of macro buttons to display. */
    val macroButtonCount: Int = 6,
    /** The list of user-defined macros. */
    val macros: List<Macro> = List(6) { Macro(name = "M${it + 1}", value = "M${it + 1}") }
)

/**
 * Represents a one-time event sent from the ViewModel to the UI.
 */
sealed class UiEvent {
    /** Event to trigger sharing of the log file. */
    data class ShareLog(val uri: Uri) : UiEvent()
    /** Event to trigger sharing of the settings file. */
    data class ShareSettings(val uri: Uri) : UiEvent()
    /** Event to indicate that settings have been imported successfully. */
    data object ImportSuccess : UiEvent()
}

/**
 * The main ViewModel for the application.
 * It manages the UI state, handles user interactions, and communicates with the [SerialService].
 */
class MainViewModel(application: Application, private val settingsRepository: SettingsRepository) :
    AndroidViewModel(application), SerialListener {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    private val usbManager by lazy { getApplication<Application>().getSystemService(Context.USB_SERVICE) as UsbManager }
    private var serialService: SerialService? = null
    private var currentLine = byteArrayOf()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            FileLogger.log("ViewModel", "onServiceConnected: service connected")
            serialService = (service as SerialService.SerialBinder).getService()
            _uiState.value.selectedDevice?.let {
                val uiState = _uiState.value
                serialService?.connect(
                    this@MainViewModel,
                    it.ports[0],
                    uiState.baudRate,
                    uiState.dataBits,
                    uiState.stopBits,
                    uiState.parity,
                    uiState.quietPeriod
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            FileLogger.log("ViewModel", "onServiceDisconnected: service disconnected")
            serialService = null
        }
    }

    init {
        FileLogger.initialize(application)
        FileLogger.log("ViewModel", "Initializing.")

        viewModelScope.launch {
            val savedSettings = settingsRepository.settingsFlow.first()
            _uiState.update { currentState ->
                // Restore settings, but keep transient state like the device list.
                savedSettings.copy(devices = currentState.devices)
            }
            refreshDevices()
        }
    }

    /** Saves the current UI state to the settings repository. */
    private fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveSettings(uiState.value)
        }
    }

    /** Exports the current settings to a JSON file and triggers a share intent. */
    fun onExportSettings() {
        viewModelScope.launch {
            try {
                FileLogger.log("ViewModel", "Exporting settings...")
                val application = getApplication<Application>()
                val settingsJson = Json.encodeToString(UiState.serializer(), _uiState.value)
                val directory = application.getExternalFilesDir(null)
                val file = File(directory, "settings.json")

                file.writeText(settingsJson)

                val uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.provider",
                    file
                )

                _eventFlow.emit(UiEvent.ShareSettings(uri))
            } catch (e: Exception) {
                FileLogger.log("ViewModel", "Error exporting settings", e)
            }
        }
    }

    /** Imports settings from a JSON file URI. */
    fun onImportSettings(uri: Uri) {
        viewModelScope.launch {
            try {
                FileLogger.log("ViewModel", "Importing settings...")
                val content = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                content?.let {
                    val importedSettings = Json.decodeFromString<UiState>(it)

                    // Update the state with the imported settings, preserving non-serializable state.
                    _uiState.update { currentState ->
                        currentState.copy(
                            baudRate = importedSettings.baudRate,
                            dataBits = importedSettings.dataBits,
                            stopBits = importedSettings.stopBits,
                            parity = importedSettings.parity,
                            flowControl = importedSettings.flowControl,
                            showControlLines = importedSettings.showControlLines,

                            fontSize = importedSettings.fontSize,
                            fontStyle = importedSettings.fontStyle,
                            charset = importedSettings.charset,
                            textColor = importedSettings.textColor,
                            displayMode = importedSettings.displayMode,
                            autoScroll = importedSettings.autoScroll,
                            showConnectionMessages = importedSettings.showConnectionMessages,
                            quietPeriod = importedSettings.quietPeriod,
                            showTimestamps = importedSettings.showTimestamps,
                            timestampFormat = importedSettings.timestampFormat,
                            maxLines = importedSettings.maxLines,

                            sendNewline = importedSettings.sendNewline,
                            localEcho = importedSettings.localEcho,
                            echoColor = importedSettings.echoColor,

                            isLogToFile = importedSettings.isLogToFile,
                            detailedLogging = importedSettings.detailedLogging,
                            logFileSize = importedSettings.logFileSize,

                            macroButtonCount = importedSettings.macroButtonCount,
                            macros = importedSettings.macros
                        )
                    }

                    saveSettings()
                    _eventFlow.emit(UiEvent.ImportSuccess)
                }
            } catch (e: Exception) {
                FileLogger.log("ViewModel", "Error importing settings", e)
            }
        }
    }

    // region Settings Updaters
    // The following functions update a specific setting in the UiState and then save all settings.

    fun setBaudRate(baudRate: Int) {
        _uiState.update { it.copy(baudRate = baudRate) }
        saveSettings()
    }

    fun setDataBits(dataBits: Int) {
        _uiState.update { it.copy(dataBits = dataBits) }
        saveSettings()
    }

    fun setStopBits(stopBits: Int) {
        _uiState.update { it.copy(stopBits = stopBits) }
        saveSettings()
    }

    fun setParity(parity: Int) {
        _uiState.update { it.copy(parity = parity) }
        saveSettings()
    }

    fun setFlowControl(flowControl: Int) {
        _uiState.update { it.copy(flowControl = flowControl) }
        saveSettings()
    }

    fun setShowControlLines(show: Boolean) {
        _uiState.update { it.copy(showControlLines = show) }
        saveSettings()
    }

    fun setFontSize(size: Int) {
        _uiState.update { it.copy(fontSize = size) }
        saveSettings()
    }

    fun setFontStyle(style: String) {
        _uiState.update { it.copy(fontStyle = style) }
        saveSettings()
    }

    fun setCharset(charset: String) {
        _uiState.update { it.copy(charset = charset) }
        saveSettings()
    }

    fun setTextColor(color: String) {
        _uiState.update { it.copy(textColor = color) }
        saveSettings()
    }

    fun setDisplayMode(mode: String) {
        _uiState.update { it.copy(displayMode = mode) }
        saveSettings()
    }

    fun setAutoScroll(auto: Boolean) {
        _uiState.update { it.copy(autoScroll = auto) }
        saveSettings()
    }

    fun setShowConnectionMessages(show: Boolean) {
        _uiState.update { it.copy(showConnectionMessages = show) }
        saveSettings()
    }

    fun setQuietPeriod(period: Int) {
        _uiState.update { it.copy(quietPeriod = period) }
        saveSettings()
    }

    fun setShowTimestamps(show: Boolean) {
        _uiState.update { it.copy(showTimestamps = show) }
        saveSettings()
    }

    fun setTimestampFormat(format: String) {
        _uiState.update { it.copy(timestampFormat = format) }
        saveSettings()
    }

    fun setMaxLines(lines: Int) {
        _uiState.update { it.copy(maxLines = lines) }
        saveSettings()
    }

    fun setSendNewline(newline: String) {
        _uiState.update { it.copy(sendNewline = newline) }
        saveSettings()
    }

    fun setLocalEcho(echo: Boolean) {
        _uiState.update { it.copy(localEcho = echo) }
        saveSettings()
    }

    fun setEchoColor(color: String) {
        _uiState.update { it.copy(echoColor = color) }
        saveSettings()
    }

    fun setLogToFile(isLogToFile: Boolean) {
        _uiState.update { it.copy(isLogToFile = isLogToFile) }
        saveSettings()
    }

    fun setDetailedLogging(detailed: Boolean) {
        _uiState.update { it.copy(detailedLogging = detailed) }
        saveSettings()
    }

    fun setLogFileSize(logFileSize: Int) {
        _uiState.update { it.copy(logFileSize = logFileSize) }
        saveSettings()
    }

    fun setMacroButtonCount(count: Int) {
        _uiState.update { it.copy(macroButtonCount = count) }
        saveSettings()
    }

    fun setMacro(index: Int, macro: Macro) {
        val macros = _uiState.value.macros.toMutableList()
        if (index < macros.size) {
            macros[index] = macro
            _uiState.update { it.copy(macros = macros) }
            saveSettings()
        }
    }
    // endregion

    /** Clears the content of the log file. */
    fun clearLog() {
        FileLogger.clear()
    }

    /** Triggers a share intent for the log file. */
    fun onShareLog() {
        viewModelScope.launch {
            FileLogger.getLogFile()?.let {
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.provider",
                    it
                )
                _eventFlow.emit(UiEvent.ShareLog(uri))
            }
        }
    }

    /** Sends a string of data to the connected serial device. */
    fun send(data: String) {
        if (!_uiState.value.isConnected) return

        if (_uiState.value.localEcho) {
            addTerminalLine(data, LineType.OUTGOING)
        }

        val dataBytes = if (_uiState.value.displayMode == "Hex") {
            try {
                // Convert hex string to byte array
                data.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (e: Exception) {
                onSerialIoError(e)
                return
            }
        } else {
            data.toByteArray()
        }

        // Append newline characters based on the setting
        val newlineBytes = when (_uiState.value.sendNewline) {
            "CR" -> byteArrayOf(0x0D)
            "LF" -> byteArrayOf(0x0A)
            "CR+LF" -> byteArrayOf(0x0D, 0x0A)
            else -> byteArrayOf()
        }

        val bytesToSend = dataBytes + newlineBytes
        serialService?.write(bytesToSend)
    }

    /** Refreshes the list of available USB serial devices. */
    fun refreshDevices() {
        viewModelScope.launch {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            _uiState.update { it.copy(devices = drivers) }
        }
    }

    /** Sets the selected USB serial device. */
    fun selectDevice(driver: UsbSerialDriver) {
        _uiState.update { it.copy(selectedDevice = driver) }
    }

    /** Initiates the connection to the selected device. */
    fun connect() {
        val driver = _uiState.value.selectedDevice ?: return

        if (usbManager.hasPermission(driver.device)) {
            connectToDevice(driver)
        } else {
            // Request USB permission if not already granted.
            val intent = Intent("com.ksh.esp32app.USB_PERMISSION")
            intent.setPackage(getApplication<Application>().packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                getApplication(),
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            usbManager.requestPermission(driver.device, pendingIntent)
        }
    }

    /** Binds to the SerialService and establishes the serial connection. */
    fun connectToDevice(driver: UsbSerialDriver) {
        // Clear terminal and serial buffer before connecting
        _uiState.update { it.copy(terminalLines = emptyList()) }
        currentLine = byteArrayOf()

        val intent = Intent(getApplication(), SerialService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        _uiState.update { it.copy(isConnected = true, selectedDevice = driver) }
    }

    /** Disconnects from the serial device and unbinds from the service. */
    fun disconnect() {
        if (!_uiState.value.isConnected) return

        if (_uiState.value.showConnectionMessages) {
            addTerminalLine("Disconnected", LineType.STATUS)
        }
        _uiState.update { it.copy(isConnected = false) }
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (ignored: IllegalArgumentException) {}
        serialService?.disconnect()
    }

    /** Adds a line of text to the terminal buffer. */
    private fun addTerminalLine(line: String, type: LineType) {
        val uiState = _uiState.value
        val text = if (uiState.showTimestamps) {
            try {
                val timestamp = java.text.SimpleDateFormat(uiState.timestampFormat, java.util.Locale.getDefault()).format(java.util.Date())
                "$timestamp: $line"
            } catch (e: Exception) {
                "[Timestamp Error]: $line"
            }
        } else {
            line
        }

        val newLines = (uiState.terminalLines + TerminalLine(text, type)).takeLast(uiState.maxLines)
        _uiState.update { it.copy(terminalLines = newLines) }
    }

    // region SerialListener Callbacks

    override fun onSerialConnect() {
        if (_uiState.value.showConnectionMessages) {
            addTerminalLine("Connected", LineType.STATUS)
        }
    }

    override fun onSerialConnectError(e: Exception) {
        addTerminalLine("Connection Failed: ${e.message}", LineType.STATUS)
        disconnect()
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
        viewModelScope.launch(Dispatchers.Default) {
            val uiState = _uiState.value
            val charset = Charset.forName(uiState.charset)
            val displayMode = uiState.displayMode

            val newLines = mutableListOf<TerminalLine>()

            val dataCopy = synchronized(datas) {
                val copy = mutableListOf<ByteArray>()
                while (datas.isNotEmpty()) {
                    copy.add(datas.removeFirst())
                }
                copy
            }

            for (data in dataCopy) {
                if (uiState.detailedLogging) {
                    FileLogger.log("ViewModel", "Raw data received: ${data.joinToString(" ") { "%02X".format(it) }}")
                }
                var beginning = 0
                for (i in data.indices) {
                    // Split data by newline character (0x0A).
                    if (data[i] == 0x0A.toByte()) {
                        val lineBytes = currentLine + data.copyOfRange(beginning, i)
                        if (lineBytes.isNotEmpty()) {
                            val text = if (displayMode == "Hex") {
                                lineBytes.joinToString(" ") { "%02X".format(it) }.replace("0D", "")
                            } else {
                                String(lineBytes, charset).replace("\r", "")
                            }
                            newLines.add(TerminalLine(text, LineType.INCOMING))
                        }
                        currentLine = byteArrayOf()
                        beginning = i + 1
                    }
                }
                // Add any remaining data to the current line buffer.
                if (beginning < data.size) {
                    currentLine += data.copyOfRange(beginning, data.size)
                }
            }

            if (newLines.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.value.let {
                        val allLines = (it.terminalLines + newLines.map { line ->
                            val text = if (it.showTimestamps) {
                                try {
                                    val timestamp = java.text.SimpleDateFormat(it.timestampFormat, java.util.Locale.getDefault()).format(java.util.Date())
                                    "$timestamp: ${line.text}"
                                } catch (e: Exception) {
                                    "[Timestamp Error]: ${line.text}"
                                }
                            } else {
                                line.text
                            }
                            TerminalLine(text, line.type)
                        }).takeLast(it.maxLines)
                        _uiState.update { it.copy(terminalLines = allLines) }
                    }
                }
            }
        }
    }

    override fun onSerialIoError(e: Exception) {
        if (_uiState.value.isConnected) {
            val msg = "Serial port run error: ${e.message}"
            FileLogger.log("ViewModel", msg, e)
            addTerminalLine(msg, LineType.STATUS)
            disconnect()
        }
    }

    // endregion

    /** Cleans up resources when the ViewModel is cleared. */
    override fun onCleared() {
        FileLogger.log("ViewModel", "onCleared: Cleaning up resources.")

        if (_uiState.value.isConnected) {
            serialService?.disconnect()
        }

        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: Exception) {
            // Ignore exceptions if service is not bound.
        }

        val intent = Intent(getApplication(), SerialService::class.java)
        getApplication<Application>().stopService(intent)

        super.onCleared()
    }
}
