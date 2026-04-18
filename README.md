# Shortcut IME Android

Android IME scaffold for a text expander keyboard with:

- shortcut -> expands to storage
- small Colemak preview panel while typing
- lightweight in-keyboard expansion flow

## Open

Open this folder in Android Studio:

`C:\Users\kore2\Downloads\AI BLOG, SHORTS, YOUTUBE\shortcut-ime-android`

## Current scope

- Settings screen for saving shortcut pairs
- Custom keyboard service using `InputMethodService`
- Preview panel that highlights Colemak key positions in order
- Small typing status line that shows partial-match or exact-match expansion hints
- In-keyboard `Backspace`, `/`, and `'` support for realistic shortcut entry

## Implemented flow

1. Save a shortcut pair in the app screen.
2. Enable the keyboard in Android input settings.
3. Switch to `Shortcut IME Colemak`.
4. Type a shortcut on the custom keyboard.
5. The top preview panel shows the Colemak path while you type.
6. Tap `Space` or `Enter` to expand the exact shortcut into the full sentence.

## Notes

- This workspace does not currently have a full Android SDK/Gradle build chain available, so the project is prepared for Android Studio but APK build verification was not completed here.
