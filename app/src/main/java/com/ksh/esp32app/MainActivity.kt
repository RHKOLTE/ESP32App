package com.ksh.esp32app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.ksh.esp32app.ui.theme.ESP32AppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The main activity of the application.
 * It handles USB permission requests, notification permissions, and sets up the UI.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("com.ksh.esp32app.USB_PERMISSION" == intent.action) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    device?.let {
                        val driver = viewModel.uiState.value.devices.find { it.device.deviceId == device.deviceId }
                        if(driver != null) {
                            viewModel.connectToDevice(driver)
                        }
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted
        } else {
            // Permission denied
        }
    }

    /**
     * Called when the activity is first created.
     * Registers receivers, requests permissions, and sets the content view.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentFilter = IntentFilter("com.ksh.esp32app.USB_PERMISSION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(permissionReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(permissionReceiver, intentFilter)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ESP32AppTheme {
                AppUi(viewModel, this)
            }
        }
    }

    /**
     * Called when the activity is being destroyed.
     * Unregisters receivers, releases screen brightness lock, and stops the serial service.
     */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(permissionReceiver)
        ScreenBrightnessManager.releaseScreenBrightnessLock()
        try {
            val intent = Intent(this, SerialService::class.java)
            stopService(intent)
        } catch(ignored: Exception) {}
    }
}

/**
 * Represents an item in the navigation drawer.
 * @param route The route to navigate to.
 * @param title The title of the navigation item.
 * @param icon The icon for the navigation item.
 */
data class NavItem(val route: String, val title: String, val icon: ImageVector)

/**
 * The main UI of the application.
 *
 * @param viewModel The [MainViewModel] for this UI.
 * @param activity The [MainActivity] instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUi(viewModel: MainViewModel = viewModel(), activity: MainActivity) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { viewModel.onImportSettings(it) } }
    )
    val uploadHmiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { viewModel.uploadHmiParameters(it) } }
    )

    LaunchedEffect(key1 = Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShareFile -> {
                    val shareIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        type = event.mimeType
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }
                is UiEvent.ImportSuccess -> {
                    Toast.makeText(
                        context,
                        "Settings imported successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is UiEvent.HmiUploadSuccess -> {
                    Toast.makeText(
                        context,
                        "HMI parameters uploaded successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Monitor connection state and manage screen brightness
    LaunchedEffect(key1 = uiState.isConnected) {
        if (uiState.isConnected) {
            ScreenBrightnessManager.acquireScreenBrightnessLock(context, activity)
            FileLogger.log("AppUi", "Screen brightness lock acquired on connection")
        } else {
            ScreenBrightnessManager.releaseScreenBrightnessLock()
            FileLogger.log("AppUi", "Screen brightness lock released on disconnection")
        }
    }

    val navItems = listOf(
        NavItem("terminal", "Terminal", Icons.Outlined.Terminal),
        NavItem("hmi", "HMI", Icons.Outlined.DataObject),
        NavItem("devices", "USB Devices", Icons.Outlined.Usb),
        NavItem("settings", "Settings", Icons.Outlined.Settings),
        NavItem("info", "Info", Icons.Outlined.Info)
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                navItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title) },
                        selected = item.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(navItems.find { it.route == currentRoute }?.title ?: "Terminal") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onShareLog() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Log")
                        }
                        IconButton(onClick = {
                            if (uiState.isConnected) viewModel.disconnect() else viewModel.connect()
                        }, enabled = uiState.selectedDevice != null) {
                            Icon(
                                if (uiState.isConnected) Icons.Default.UsbOff else Icons.Default.Usb,
                                contentDescription = if (uiState.isConnected) "Disconnect" else "Connect"
                            )
                        }
                    }
                )
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = "terminal",
                modifier = Modifier.padding(it)
            ) {
                composable("terminal") { TerminalScreen(viewModel, navController) }
                composable("hmi") { HmiScreen(viewModel) }
                composable("devices") { DeviceScreen(viewModel) }
                composable("settings") { SettingsScreen(viewModel, onImportClick = { importSettingsLauncher.launch("application/json") }, onUploadHmiClick = { uploadHmiLauncher.launch("application/json")}) }
                composable("info") { InfoScreen() }
                composable(
                    "edit_macro/{index}",
                    arguments = listOf(navArgument("index") { type = NavType.IntType })
                ) { backStackEntry ->
                    val index = backStackEntry.arguments?.getInt("index") ?: -1
                    if (index != -1) {
                        EditMacroScreen(
                            macro = uiState.macros.getOrElse(index) { Macro() },
                            onSave = {
                                FileLogger.log("AppUi", "Saving macro $index: $it")
                                viewModel.setMacro(index, it)
                                navController.popBackStack()
                            },
                            onCancel = {
                                FileLogger.log("AppUi", "Cancelled editing macro $index")
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}


/**
 * Composable function for the Terminal screen.
 * Displays terminal output, input field, and macro buttons.
 *
 * @param viewModel The [MainViewModel] for this screen.
 * @param navController The [NavController] for navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(viewModel: MainViewModel, navController: androidx.navigation.NavController) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    val textColor = when (uiState.textColor) {
        "Red" -> Color.Red
        "Green" -> Color.Green
        "Blue" -> Color.Blue
        "Cyan" -> Color.Cyan
        else -> Color.Black
    }
    val echoColor = when (uiState.echoColor) {
        "Red" -> Color.Red
        "Green" -> Color.Green
        "Blue" -> Color.Blue
        "Cyan" -> Color.Cyan
        else -> Color.Black
    }

    if (uiState.autoScroll) {
        LaunchedEffect(uiState.terminalLines.size) {
            if (uiState.terminalLines.isNotEmpty()) {
                listState.scrollToItem(uiState.terminalLines.size - 1)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (uiState.showControlLines) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                ControlLineIndicator("RTS", uiState.rts)
                ControlLineIndicator("CTS", uiState.cts)
                ControlLineIndicator("DTR", uiState.dtr)
                ControlLineIndicator("DSR", uiState.dsr)
                ControlLineIndicator("CD", uiState.cd)
                ControlLineIndicator("RI", uiState.ri)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(uiState.terminalLines) { line ->
                val color = when(line.type) {
                    LineType.INCOMING, LineType.STATUS -> textColor
                    LineType.OUTGOING -> echoColor
                }
                Text(
                    text = line.text,
                    fontSize = uiState.fontSize.sp,
                    modifier = Modifier.fillMaxWidth(),
                    fontStyle = if(uiState.fontStyle == "Italic") FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if(uiState.fontStyle == "Bold") FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for(i in 0 until uiState.macroButtonCount) {
                Card(
                    modifier = Modifier.weight(1f).combinedClickable(
                        onClick = {
                            val macro = uiState.macros.getOrElse(i) { Macro() }
                            viewModel.send(macro.value)
                            FileLogger.log("Terminal", "Macro $i clicked")
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            navController.navigate("edit_macro/$i")
                            FileLogger.log("Terminal", "Macro $i long-pressed")
                        }
                    )
                ) {
                    Text(
                        text = uiState.macros.getOrElse(i) { Macro("M${i+1}") }.name,
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = textInput, onValueChange = { textInput = it }, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.send(textInput); textInput = "" }) {
                Text("Send")
            }
        }
    }
}

/**
 * Composable function for the HMI screen.
 * This screen will display the HMI parameters.
 *
 * @param viewModel The [MainViewModel] for this screen.
 */
@Composable
fun HmiScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val hmiParameters by viewModel.hmiParameters.collectAsState(initial = emptyList())
    val context = LocalContext.current

    var parameterToEdit by remember { mutableStateOf<HmiParameter?>(null) }
    var showPasswordDialogFor by remember { mutableStateOf<HmiParameter?>(null) }

    showPasswordDialogFor?.let { param ->
        PasswordDialog(
            onDismiss = { showPasswordDialogFor = null },
            onConfirm = { password ->
                showPasswordDialogFor = null
                if (password == "1234") { // Hardcoded password
                    parameterToEdit = param
                } else {
                    Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    parameterToEdit?.let { param ->
        HmiEditDialog(
            parameter = param,
            onDismiss = { parameterToEdit = null },
            onSave = { newValue ->
                viewModel.setHmiParameter(param, newValue)
                parameterToEdit = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { viewModel.fetchHmiParameters() },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isConnected
        ) {
            Text("Fetch All Parameters")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hmiParameters) { parameter ->
                HmiParameterItem(
                    parameter = parameter,
                    isConnected = uiState.isConnected,
                    onEditClick = {
                        if (parameter.isProtected) {
                            showPasswordDialogFor = parameter
                        } else {
                            parameterToEdit = parameter
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HmiParameterItem(parameter: HmiParameter, isConnected: Boolean, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = parameter.displayName, fontWeight = FontWeight.Bold)
                val displayValue = if (parameter.isProtected && parameter.value.isNotEmpty()) "********" else parameter.value
                Text(text = displayValue)
            }
            Button(onClick = onEditClick, enabled = isConnected && parameter.editable) {
                Text("Edit")
            }
        }
    }
}

private fun getHmiErrorMessage(rule: String?, value: String): String? {
    if (rule.isNullOrEmpty()) return null // No validation needed

    val intValue = value.toIntOrNull()
    when {
        rule.matches(Regex("^\\d+-\\d+$")) -> {
            val parts = rule.split("-")
            val min = parts[0].toInt()
            val max = parts[1].toInt()
            if (intValue == null || intValue !in min..max) return "Must be a number between $min-$max."
        }
        rule.startsWith("C") && rule.contains("-") -> {
            if (!value.matches(Regex("^C[0-9]{5}$"))) return "Must match format C00001-C99999."
        }
        rule.startsWith("SS") && rule.contains("-") -> {
            if (!value.matches(Regex("^SS[0-9]{3}$"))) return "Must match format SS001-SS999."
        }
        rule == "URL" -> {
            if (!Patterns.WEB_URL.matcher(value).matches()) return "Must be a valid URL."
        }
        rule == "YYYY:MM:DD:HH:MM:SS" -> {
            if (!value.matches(Regex("^[0-9]{4}:[0-1][0-9]:[0-3][0-9]:[0-2][0-9]:[0-5][0-9]:[0-5][0-9]$"))) return "Format: YYYY:MM:DD:HH:MM:SS"
        }
    }
    return null // No error
}


@Composable
fun HmiEditDialog(
    parameter: HmiParameter,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(parameter) { mutableStateOf(parameter.value) }
    var errorMessage by remember(parameter) { mutableStateOf<String?>(null) }

    val onValueChange: (String) -> Unit = {
        text = it
        errorMessage = getHmiErrorMessage(parameter.validationRule, it)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${parameter.displayName}") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = onValueChange,
                    label = { Text("New Value") },
                    isError = errorMessage != null,
                    trailingIcon = {
                        if (errorMessage != null) {
                            Icon(Icons.Filled.Error, "error", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = errorMessage == null) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password Required") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


/**
 * Displays an indicator for a serial control line (e.g., RTS, CTS).
 * @param name The name of the control line.
 * @param active Whether the control line is active.
 */
@Composable
fun ControlLineIndicator(name: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name)
        Box(modifier = Modifier.size(24.dp).background(if (active) Color.Green else Color.Red))
    }
}

/**
 * Composable function for the Device selection screen.
 * Displays a list of available USB serial devices.
 *
 * @param viewModel The [MainViewModel] for this screen.
 */
@Composable
fun DeviceScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(uiState.devices) { device ->
            DeviceListItem(device = device, selected = device.device.deviceId == uiState.selectedDevice?.device?.deviceId, connected = uiState.isConnected && device.device.deviceId == uiState.selectedDevice?.device?.deviceId) {
                viewModel.selectDevice(device)
            }
        }
    }
}

/**
 * Displays a single item in the device list.
 *
 * @param device The USB serial driver for the device.
 * @param selected Whether the device is selected.
 * @param connected Whether the device is currently connected.
 * @param onClick The callback to be invoked when the item is clicked.
 */
@Composable
fun DeviceListItem(device: UsbSerialDriver, selected: Boolean, connected: Boolean, onClick: () -> Unit) {
    val color = if (connected) Color.Green else if (selected) Color.LightGray else Color.Transparent
    Text(
        text = "${device.device.deviceName} - ${device.javaClass.simpleName}",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).background(color).padding(8.dp)
    )
}

/**
 * Composable function for the Info screen.
 * Displays information about the application.
 */
@Composable
fun InfoScreen() {
    Text(text = "Info Screen", modifier = Modifier.padding(16.dp))
}

/**
 * Composable function for the Settings screen.
 * Provides access to Serial, Terminal, Send, and Misc settings via tabs.
 *
 * @param viewModel The [MainViewModel] for this screen.
 * @param onImportClick The callback to be invoked when the import settings button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onImportClick: () -> Unit, onUploadHmiClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Serial", "Terminal", "Send", "HMI", "Misc")

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title) })
            }
        }
        when (selectedTabIndex) {
            0 -> SerialSettingsTab(uiState, viewModel)
            1 -> TerminalSettingsTab(uiState, viewModel)
            2 -> SendSettingsTab(uiState, viewModel)
            3 -> HmiSettingsTab(uiState, viewModel, onUploadHmiClick)
            4 -> MiscSettingsTab(uiState, viewModel, onImportClick)
        }
    }
}

/**
 * Composable for the Serial settings tab.
 *
 * @param uiState The current [UiState].
 * @param viewModel The [MainViewModel].
 */
@Composable
fun SerialSettingsTab(uiState: UiState, viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        SettingDropDown("Baud Rate", uiState.baudRate.toString(), listOf("9600", "19200", "38400", "57600", "115200")) { viewModel.setBaudRate(it.toInt()) }
        SettingDropDown("Data Bits", uiState.dataBits.toString(), listOf("5", "6", "7", "8")) { viewModel.setDataBits(it.toInt()) }
        SettingDropDown("Stop Bits", uiState.stopBits.toString(), listOf("1", "2")) { viewModel.setStopBits(it.toInt()) }
        SettingDropDown("Parity", when(uiState.parity) {
            UsbSerialPort.PARITY_NONE -> "None"
            UsbSerialPort.PARITY_EVEN -> "Even"
            UsbSerialPort.PARITY_ODD -> "Odd"
            else -> "None"
        }, listOf("None", "Even", "Odd")) {
            val parity = when(it) {
                "None" -> UsbSerialPort.PARITY_NONE
                "Even" -> UsbSerialPort.PARITY_EVEN
                "Odd" -> UsbSerialPort.PARITY_ODD
                else -> UsbSerialPort.PARITY_NONE
            }
            viewModel.setParity(parity)
        }
        SettingDropDown("Flow Control", when(uiState.flowControl) {
            0 -> "None"
            else -> "None"
        }, listOf("None")) { viewModel.setFlowControl(0) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.showControlLines, onCheckedChange = { viewModel.setShowControlLines(it) })
            Text("Show Control Lines")
        }
    }
}

/**
 * Composable for the Terminal settings tab.
 *
 * @param uiState The current [UiState].
 * @param viewModel The [MainViewModel].
 */
@Composable
fun TerminalSettingsTab(uiState: UiState, viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = uiState.fontSize.toString(), onValueChange = { viewModel.setFontSize(it.toIntOrNull() ?: 14) }, label = { Text("Font Size") })
        SettingDropDown("Font Style", uiState.fontStyle, listOf("Normal", "Bold", "Italic")) { viewModel.setFontStyle(it) }
        SettingDropDown("Charset", uiState.charset, listOf("UTF-8", "windows-1250")) { viewModel.setCharset(it) }
        SettingDropDown("Text Color", uiState.textColor, listOf("Black", "Red", "Green", "Blue", "Cyan")) { viewModel.setTextColor(it) }
        SettingDropDown("Display Mode", uiState.displayMode, listOf("Text", "Hex")) { viewModel.setDisplayMode(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.autoScroll, onCheckedChange = { viewModel.setAutoScroll(it) })
            Text("Auto scroll to end of buffer")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.showConnectionMessages, onCheckedChange = { viewModel.setShowConnectionMessages(it) })
            Text("Show connection messages")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column {
            Text("Connection Quiet Period: ${uiState.quietPeriod}s")
            Slider(
                value = uiState.quietPeriod.toFloat(),
                onValueChange = { viewModel.setQuietPeriod(it.roundToInt()) },
                valueRange = 1f..60f,
                steps = 58
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.showTimestamps, onCheckedChange = { viewModel.setShowTimestamps(it) })
            Text("Show timestamps")
        }
        OutlinedTextField(value = uiState.timestampFormat, onValueChange = { viewModel.setTimestampFormat(it) }, label = { Text("Timestamp Format") })
        OutlinedTextField(value = uiState.maxLines.toString(), onValueChange = { viewModel.setMaxLines(it.toIntOrNull() ?: 1000) }, label = { Text("Max Lines") })
    }
}

/**
 * Composable for the Send settings tab.
 *
 * @param uiState The current [UiState].
 * @param viewModel The [MainViewModel].
 */
@Composable
fun SendSettingsTab(uiState: UiState, viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        SettingDropDown("Newline", uiState.sendNewline, listOf("CR", "LF", "CR+LF")) { viewModel.setSendNewline(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.localEcho, onCheckedChange = { viewModel.setLocalEcho(it) })
            Text("Local Echo")
        }
        SettingDropDown("Echo Color", uiState.echoColor, listOf("Black", "Red", "Green", "Blue", "Cyan")) { viewModel.setEchoColor(it) }
    }
}

/**
 * Composable for the HMI settings tab.
 */
@Composable
fun HmiSettingsTab(uiState: UiState, viewModel: MainViewModel, onUploadClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Define the HMI screen layout by uploading a custom hmi_parameters.json file. " +
                    "Download the current configuration to use as a template."
        )
        OutlinedTextField(
            value = uiState.hmiCommandDelay.toString(),
            onValueChange = { viewModel.setHmiCommandDelay(it.toIntOrNull() ?: 50) },
            label = { Text("HMI Command Delay (ms)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.showHmiResponsesInTerminal, onCheckedChange = { viewModel.setShowHmiResponsesInTerminal(it) })
            Text("Show HMI Responses in Terminal")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onUploadClick) {
            Text("Upload HMI JSON")
        }
        Button(onClick = { viewModel.downloadHmiParameters() }) {
            Text("Download HMI JSON")
        }
    }
}


/**
 * Composable for the Miscellaneous settings tab.
 *
 * @param uiState The current [UiState].
 * @param viewModel The [MainViewModel].
 * @param onImportClick The callback to be invoked when the import settings button is clicked.
 */
@Composable
fun MiscSettingsTab(uiState: UiState, viewModel: MainViewModel, onImportClick: () -> Unit) {
    val context = LocalContext.current
    val logPath = remember { FileLogger.getLogFile()?.absolutePath ?: "Log file not created yet" }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Log File Path: $logPath", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { viewModel.clearLog() }) {
            Text("Clear Log")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.detailedLogging, onCheckedChange = { viewModel.setDetailedLogging(it) })
            Text("Detailed Logging")
        }
        OutlinedTextField(value = uiState.logFileSize.toString(), onValueChange = { viewModel.setLogFileSize(it.toIntOrNull() ?: 1) }, label = { Text("Log File Size (MB)") })
        SettingDropDown("Macro Buttons", uiState.macroButtonCount.toString(), listOf("1", "2", "3", "4", "5", "6")) { viewModel.setMacroButtonCount(it.toInt()) }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImportClick) {
                Text("Import App Settings")
            }
            Button(onClick = { viewModel.onExportSettings() }) {
                Text("Export App Settings")
            }
        }
    }
}


/**
 * A reusable composable for a dropdown menu setting.
 *
 * @param label The label for the setting.
 * @param selectedValue The currently selected value.
 * @param options The list of options to display in the dropdown.
 * @param onValueChange The callback to be invoked when a new value is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingDropDown(label: String, selectedValue: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onValueChange(option)
                    expanded = false
                })
            }
        }
    }
}
