# brewPDF

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

## Download & Install

1. Go to the [Releases](../../releases) page of this repository.
2. Download the latest `app-debug.apk` (or `app-release.apk`) file.
3. Open the downloaded file on your Android device.
4. Allow "Install from unknown sources" if prompted, and tap **Install**.

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
