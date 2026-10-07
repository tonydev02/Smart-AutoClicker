# On-device diagnostics

Smart detection writes one `klickr-diagnostic-YYYYMMDD-HHmmss.log` file per detection session. On Android 10 and newer, files are created through `MediaStore.Downloads` at `Downloads/Klickr/Diagnostics/`; no broad storage permission is required. Each record is flushed promptly, and logging failures fall back to Logcat.

On Android versions below 10, diagnostics use the app-specific external directory returned by `Context.getExternalFilesDir("Diagnostics")`. This fallback does not request dangerous or broad storage permissions and is not expected to appear in the public Downloads collection.
