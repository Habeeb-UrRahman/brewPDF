# PDF Merger

A native Android app that merges multiple PDF files into one. Share PDFs from any app (Files, Drive, browser) and they appear in a staging list. Reorder by drag-and-drop, then merge with a single tap.

## Features

- **Share Target** — appears in the Android share sheet for PDF files
- **Staging List** — view filename, size, and page count for each PDF
- **Drag & Drop Reorder** — long-press to reorder PDFs before merging
- **Merge** — combines PDFs in exact order shown, saves to external cache
- **Share / Open Result** — share the merged file or open it in any PDF viewer
- **Dark Mode** — automatic Material 3 dynamic color support
- **Fully Offline** — no internet permission, no backend

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| PDF Merge | iText 7 Community (AGPL) |
| Drag & Drop | sh.calvin.reorderable |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Build | Gradle 8.11.1 + AGP 8.7.3 |

## Prerequisites

- **JDK 17+** installed and on PATH
- **Android SDK** with:
  - Build Tools 35
  - Platform SDK 35
- **OR** just open the project in **Android Studio** (Ladybug or newer) — it downloads everything automatically

## Build the APK

### Option A: Android Studio (Recommended)

1. Open the project folder in Android Studio
2. Wait for Gradle sync to complete
3. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Command Line

```bash
# On macOS/Linux:
./gradlew assembleDebug

# On Windows:
gradlew.bat assembleDebug
```

The debug APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Install the APK

### Via ADB
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Via File Transfer
1. Copy `app-debug.apk` to your phone
2. Open it in a file manager
3. Allow "Install from unknown sources" when prompted
4. Install

## How to Use

### Method 1: Share from another app
1. Open any app with PDF files (Files, Google Drive, Chrome, etc.)
2. Select one or more PDFs → tap **Share**
3. Choose **PDF Merger** from the share sheet
4. PDFs appear in the staging list

### Method 2: Add from within the app
1. Open PDF Merger
2. Tap the **+** button in the top bar or the "Add PDFs" button
3. Select PDF files from the file picker

### Merging
1. Arrange PDFs in your desired order (long-press to drag & reorder)
2. Remove unwanted files by tapping **✕**
3. Tap the **Merge PDFs** button
4. Wait for the merge to complete
5. Use **Share** or **Open** from the result sheet

## Output Location

Merged PDFs are saved to the app's external cache directory with a timestamped filename:
```
merged_20260607_231800.pdf
```
It is highly recommended to use the Share or Open buttons upon successful merging to extract the merged file.

## License

This app uses [iText 7 Community](https://itextpdf.com/) which is licensed under **AGPL v3**. If you distribute this app, you must make the source code available under a compatible license.

For personal use and sideloading, this is not an issue.
