# ESP32 Serial Terminal App

This Android application serves as a powerful and highly configurable serial terminal, designed primarily for interacting with USB serial devices like the ESP32. It provides a robust interface for communication, debugging, and settings management.

## Core Features

- **USB Serial Connection**: Connects to any standard USB serial device detected by Android, with specific handling for ESP32 and similar microcontrollers.
- **Advanced Connection Control**: To ensure stability, the app features a configurable **"Connection Quiet Period"**. This allows the app to wait for a specified duration (1-60 seconds) after connecting, weathering the initial data flood from devices that can cause other terminals to freeze or crash.
- **Prevents Device Resets**: The app intelligently manages DTR/RTS control lines to prevent the target device from resetting upon connection.
- **Persistent Settings**: All your configurations, from serial parameters to UI preferences, are automatically saved and restored on the next launch.

### Terminal Interface

- **Real-time Data Display**: View incoming serial data in either plain **Text** or **Hex** format.
- **Customizable Display**: Tailor the look and feel of the terminal by adjusting font size, style (Normal, Bold, Italic), and text color.
- **Timestamps**: Optionally prepend a timestamp to each incoming line of data, with a customizable format.
- **Auto-Scroll**: Keep the latest data in view with an auto-scroll feature that can be toggled.
- **Character Set**: Supports different character encodings, including UTF-8.

### Serial Configuration

- **Standard Parameters**: Full control over Baud Rate, Data Bits, Stop Bits, and Parity.
- **Flow Control**: Support for hardware flow control (though currently disabled).

### Data Sending & Macros

- **Send Data**: Easily send commands to the connected device as either Text or Hex.
- **Configurable Newline**: Automatically append CR, LF, or CR+LF to your sent messages.
- **Local Echo**: See the commands you send directly in the terminal.
- **Macro Buttons**: Configure up to 6 macro buttons for quick access to frequently used commands. Macros can be named and edited with a long-press.

### Logging & Settings Management

- **File Logging**: The app maintains a detailed log file (`app_log.txt`) that can be used for debugging.
- **Share Logs**: Easily share the log file directly from the app.
- **Import/Export Settings**: Save your entire app configuration to a `settings.json` file. This is perfect for backing up your setup or sharing it with others.

## Key Libraries Used

- [**usb-serial-for-android** by mik3y](https://github.com/mik3y/usb-serial-for-android): The core library that provides the robust, low-level driver for handling USB serial communication.
- **Jetpack Compose**: The entire user interface is built with Google's modern, declarative UI toolkit for Android.
- **Kotlin Coroutines**: Used extensively for managing background tasks, ensuring the UI remains responsive even during I/O operations.
- **Kotlinx Serialization**: Handles the seamless saving and loading of the application's settings to and from a JSON file.
- **AndroidX ViewModel**: Manages the UI state and survives configuration changes, providing a stable experience.
- **AndroidX Navigation**: Manages the navigation between the various screens and settings tabs within the app.
