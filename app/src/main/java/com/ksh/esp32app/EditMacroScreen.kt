package com.ksh.esp32app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A composable screen for editing a [Macro].
 * This screen allows the user to change the name, value, and other properties of a macro.
 *
 * @param macro The initial [Macro] object to be edited.
 * @param onSave A callback function that is invoked when the user saves the changes. It receives the updated [Macro] object.
 * @param onCancel A callback function that is invoked when the user cancels the editing process.
 */
@Composable
fun EditMacroScreen(macro: Macro, onSave: (Macro) -> Unit, onCancel: () -> Unit) {
    // State variables to hold the edited macro properties.
    // `remember(macro)` is used to reset the state if a different macro is passed to the composable.
    var name by remember(macro) { mutableStateOf(macro.name) }
    var value by remember(macro) { mutableStateOf(macro.value) }
    var isHex by remember(macro) { mutableStateOf(macro.isHex) }
    var repeat by remember(macro) { mutableStateOf(macro.repeat) }
    var repeatDelay by remember(macro) { mutableStateOf(macro.repeatDelay.toString()) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Input field for the macro name.
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        
        // Input field for the macro value (the command to be sent).
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") })
        
        // Radio buttons to select whether the value is plain text or a hexadecimal string.
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !isHex, onClick = { isHex = false })
            Text("Text")
            RadioButton(selected = isHex, onClick = { isHex = true })
            Text("HEX")
        }
        
        // Checkbox to enable or disable repeating the macro command.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = repeat, onCheckedChange = { repeat = it })
            Text("Repeat")
        }
        
        // Input field for the repeat delay in milliseconds.
        OutlinedTextField(value = repeatDelay, onValueChange = { repeatDelay = it }, label = { Text("Repeat Delay (ms)") })
        
        // Buttons for saving or canceling the edit.
        Row {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = { 
                // When saving, create a new Macro object from the current state and pass it to the onSave callback.
                onSave(Macro(name, value, isHex, repeat, repeatDelay.toIntOrNull() ?: 0))
            }) {
                Text("Save")
            }
        }
    }
}