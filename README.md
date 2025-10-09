# PaperMC Server Android App

This Android app allows selecting, downloading, and hosting a PaperMC Minecraft server directly on an Android device.

## Features

- Fetches available PaperMC versions from the official API
- Allows selecting a version and downloading the server jar
- Configure basic server settings (max players, server port)
- Start and stop the Minecraft server process
- View server console output in real time

## Setup

1. Clone or create the project in Android Studio.
2. Replace the app files with the provided source code.
3. Sync Gradle to download dependencies (OkHttp, Kotlin Coroutines).
4. Run on a device with:
   - Android SDK 24+
   - Sufficient storage and resources
   - Java runtime environment accessible (e.g., Termux or embedded JVM)

## Usage

- Select a PaperMC version from the dropdown.
- Click *Download Selected Version* to download the server jar.
- Configure max players and server port.
- Click *Start Server* to launch the server.
- Server console output appears in the scrollable text view.
- Click *Stop Server* to terminate the server.

## Notes

- The app assumes a Java runtime is available on the device.
- Running a Minecraft server on Android might require advanced setup.
- Permissions for storage and internet are required.
- Performance may vary based on device capabilities.
