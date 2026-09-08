# Play Console notes

## Foreground service declaration (`microphone`)

- Type: `microphone` (`CaptureForegroundService`, `foregroundServiceType="microphone"`).
- Purpose statement: "SongPad records the user's voice and piano ideas while the recording screen is open or the user has
  moved to another app mid-take. The service starts only from the visible recording screen and stops when the take is
  saved or discarded. Audio is stored on the device only."
- Video: a 30-second capture of Record → home → notification → return → Save.
- Permissions: `RECORD_AUDIO` (runtime, requested on first Record tap), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (runtime, requested in context).

## Data safety form

- Collected: none. Shared: none. **Audio never leaves the device.** No accounts, analytics, crash reporting or network
  access in Phase 1 (the app declares no `INTERNET` permission).
- On-device data: audio recordings, MIDI, lyrics, notes, settings. Deleted on uninstall; Auto Backup excludes audio (§9.5).
- Export is user-initiated through the system file picker or share sheet.

## Privacy policy (draft)

SongPad stores everything you record and write on your device only. It does not send audio, text or usage data anywhere.
Exporting a song creates a file you choose where to save or share. Uninstalling the app deletes all of its data.
Microphone access is used only while a recording screen is open. Notifications are used only for optional reminders you
can turn off in Settings.

## Open source notices

Listed in Settings → About → Open source licenses. LAME (LGPL 2.1) obligations: docs/audio/LAME_INTEGRATION.md.
