# Building APK

## Android Studio

1. Open [shortcut-ime-android](/C:/Users/kore2/Downloads/AI%20BLOG,%20SHORTS,%20YOUTUBE/shortcut-ime-android) in Android Studio.
2. Let Android Studio install the missing Android SDK packages if prompted.
3. Use JDK 17.
4. Sync the project.
5. Run `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

## Suggested checks

1. Save at least one shortcut in the app screen.
2. Enable `Shortcut IME Colemak` in Android keyboard settings.
3. Switch to the keyboard.
4. Type a shortcut and confirm:
   - the preview panel lights up the Colemak path
   - the status line shows the candidate expansion
   - `Space` or `Enter` expands the exact shortcut

## Current environment note

This Codex workspace does not have `java`, `gradle`, or `adb`, so the APK could not be built locally here.
