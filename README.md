<p align="center">
  <h1 align="center">Scout</h1>
  <p align="center">
    <strong>A privacy-first, offline Android assistant — no internet, no tracking, no compromises.</strong>
  </p>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg" alt="Platform: Android 8.0+">
  <img src="https://img.shields.io/badge/Privacy-100%25%20Air--Gapped-red.svg" alt="Privacy: 100% Air-Gapped">
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple.svg" alt="Kotlin 2.0">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg" alt="Jetpack Compose Material 3">
  <img src="https://img.shields.io/badge/Built%20With-AI%20Assistance-orange.svg" alt="Built with AI Assistance">
</p>

---

## What is Scout?

Scout is a native Android assistant built with **Kotlin** and **Jetpack Compose**. It runs entirely on your device with **zero internet permissions** — no data ever leaves your phone.

Designed for privacy enthusiasts and budget-friendly hardware alike, Scout is lightweight enough to run smoothly on entry-level Android devices while delivering voice-powered productivity without background battery drain.

---

## ✨ Features

### 🛡️ Air-Gapped Privacy
- **Zero internet permissions** — `android.permission.INTERNET` is intentionally absent from the manifest. Scout physically cannot make network calls.
- **All data stays local** — notes, reminders, and command history are stored in an encrypted on-device SQLite database via Room.
- **Offline speech recognition** — uses Android's built-in `RecognizerIntent` with `EXTRA_PREFER_OFFLINE = true`, with a text-input fallback.

### 🔋 Zero Background Battery Drain
- **No background services, no wake-word listeners, no persistent daemons.**
- Scout is completely dormant until you invoke it:
  - 🔘 **Hardware assistant gesture / power key** — opens a transparent bottom-sheet overlay
  - ⚡ **Quick Settings tile** — one pull, one tap
  - 📱 **Home screen widget** — instant voice or text prompt

### ⏰ Natural Language Reminders
- **Relative times:** *"Remind me to check the oven in 2 minutes"*
- **Absolute times:** *"Remind me to call Mom at 5pm"*
- **Smart AM/PM inference:** saying *"at 5"* in the afternoon schedules 5:00 PM today
- **Query reminders:** *"What are my reminders?"*, *"Reminders today"*, *"Any reminders at 9pm?"*
- **Delete reminders:** *"Delete reminder call Mom"*, *"Remove reminder 1"*
- **Tomorrow support:** *"Remind me to submit report tomorrow at 10am"*

### 📝 Quick Notes
- *"Note Buy milk and eggs"* — instantly saved to local storage
- Searchable notes list with swipe-to-delete in the Notes tab

### 🚀 App Launcher
- *"Open Camera"*, *"Launch WhatsApp"* — fuzzy-matches against installed apps and launches them instantly

### 🎨 Modern UI
- **Material 3 + Material You** dynamic theming on Android 12+
- **Dark / Light / System** theme toggle
- **4-tab dashboard:** Command · Reminders · Notes · Modules
- Clean, accessible design with search & filter bars

---

## 📋 Command Reference

| Category | Example Command | What It Does |
|---|---|---|
| **Set Reminder** | *"Remind me to call Mom at 5pm"* | Schedules an exact alarm with notification |
| **Relative Reminder** | *"Remind me to check mail in 30 minutes"* | Schedules from current time |
| **Tomorrow Reminder** | *"Remind me to submit report tomorrow at 10am"* | Schedules for the next day |
| **List Reminders** | *"What are my reminders?"* | Shows all upcoming reminders |
| **Today's Reminders** | *"Reminders today"* | Filters to today only |
| **Check Specific Time** | *"Any reminders at 9pm?"* | Checks a ±15 min window |
| **Delete Reminder** | *"Delete reminder call Mom"* | Cancels alarm and removes record |
| **Delete by Number** | *"Remove reminder 1"* | Removes by list position |
| **Save Note** | *"Note Buy milk and eggs"* | Saves to local database |
| **Take Note** | *"Take note Meeting at 3pm"* | Alternative note syntax |
| **Open App** | *"Open Camera"* | Launches the matching app |
| **Launch App** | *"Launch Calculator"* | Alternative launch syntax |

---

## 🏗️ Architecture

Scout uses a clean **modular plugin architecture**. Each feature is a self-contained module that implements the `AssistantModule` interface:

```
com.pocketcore.assistant/
├── core/
│   ├── AssistantModule.kt      # Plugin interface
│   ├── CommandDispatcher.kt    # Routes input → matching module
│   └── FeatureRegistry.kt     # Module registry
├── modules/
│   ├── reminder/
│   │   ├── ReminderModule.kt   # Create, query, delete reminders
│   │   └── ReminderParser.kt   # Natural language time parser
│   ├── utility/
│   │   ├── NoteModule.kt       # Quick notes
│   │   └── AppLauncherModule.kt # Open installed apps
│   └── template/
│       └── ModuleTemplate.kt   # Starter template for new modules
├── data/
│   ├── PocketCoreDatabase.kt   # Room database (v3)
│   ├── dao/AppDaos.kt          # Data access objects
│   └── entity/                 # NoteEntity, ReminderEntity, CommandLogEntity
├── ui/
│   ├── MainActivity.kt         # Full dashboard (Jetpack Compose)
│   ├── AssistantOverlayActivity.kt  # Transparent voice overlay
│   ├── AssistantViewModel.kt   # UI state management
│   └── theme/Theme.kt          # Material 3 theming
├── receiver/AlarmReceiver.kt   # Scheduled alarm handler
├── tile/PocketCoreTileService.kt # Quick Settings tile
├── widget/PocketCoreWidgetProvider.kt # Home screen widget
└── PocketCoreApp.kt            # Application entry + module registration
```

### Adding a New Module (5 minutes)

1. Create a new Kotlin file implementing `AssistantModule`:
```kotlin
class CalculatorModule : AssistantModule {
    override val id = "calc"
    override val displayName = "Calculator"
    override val description = "Evaluates math expressions offline"
    override val priority = 75

    override fun canHandle(input: String) =
        input.trim().lowercase().startsWith("calc ")

    override suspend fun execute(context: Context, input: String): CommandResult {
        val expression = input.substringAfter("calc ").trim()
        // Your offline logic here
        return CommandResult(success = true, feedbackMessage = "Result: 42")
    }
}
```

2. Register it with one line in `PocketCoreApp.kt`:
```kotlin
FeatureRegistry.registerModule(CalculatorModule())
```

That's it — the module instantly works with voice, text, and appears in the Modules settings screen.

See [`ModuleTemplate.kt`](app/src/main/java/com/pocketcore/assistant/modules/template/ModuleTemplate.kt) for a complete starter template.

---

## 🔒 Permissions

Scout requests the **absolute minimum** permissions needed to function. There are **no internet, camera, microphone, location, or contacts permissions**.

| Permission | Why It's Needed |
|---|---|
| `POST_NOTIFICATIONS` | Show reminder notifications when alarms fire |
| `SCHEDULE_EXACT_ALARM` | Schedule precise reminder alarms via AlarmManager |
| `USE_EXACT_ALARM` | Android 12+ requirement for exact alarm scheduling |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule reminders after device restart |
| `VIBRATE` | Haptic feedback for notifications |
| `QUERY_ALL_PACKAGES` | Discover installed apps for the App Launcher module |

> **Note:** Speech recognition uses the system's built-in `RecognizerIntent` — no microphone permission is needed because the OS handles audio capture in its own secure process.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 17** (tested with Eclipse Adoptium 17.0.12)
- **Android SDK** with API levels 26–35

### Build the Debug APK

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows (PowerShell)
.\gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/scout-v1.0.0-debug.apk`

### Run Unit Tests

```bash
# Linux / macOS
./gradlew testDebugUnitTest

# Windows (PowerShell)
.\gradlew.bat testDebugUnitTest
```

---

## 🤖 AI Disclosure — Vibe Coded

> **Transparency Notice:** This project was **vibe coded** — developed through an interactive, conversational collaboration between a human developer and an AI coding assistant (Google Antigravity / Gemini).
>
> The human developer directed the vision, requirements, and design decisions. The AI assistant helped write code, debug issues, suggest architectural patterns, and generate documentation.
>
> **What this means:**
> - ✅ All code has been reviewed and tested by the human developer
> - ✅ The app works as described and has been verified on real hardware
> - ✅ No AI models run inside the app — it remains fully offline and private
> - ✅ This disclosure is provided in the spirit of responsible AI use and open-source transparency
>
> We believe in being upfront about how software is built. Vibe coding is a legitimate and increasingly common development practice, and we're proud to be transparent about it.

---

## 🗺️ Roadmap

- [ ] Recurring reminders (*"Remind me every Monday at 9am"*)
- [ ] Offline calculator module
- [ ] Clipboard history manager
- [ ] Offline dictionary / thesaurus lookup
- [ ] Tasker / automation integration
- [ ] Export notes and reminders as `.txt` / `.csv`
- [ ] Localization (multi-language support)

---

## 🤝 Contributing

Contributions are welcome! Whether it's a bug fix, new module, UI improvement, or documentation update — we appreciate your help.

1. **Fork** the repository
2. **Create a feature branch:** `git checkout -b feature/my-new-module`
3. **Commit your changes:** `git commit -m "Add calculator module"`
4. **Push to the branch:** `git push origin feature/my-new-module`
5. **Open a Pull Request**

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE).

```
MIT License — Copyright (c) 2026 Scout Project Authors
```

---

<p align="center">
  <strong>Scout</strong> — Your phone. Your data. Your assistant.<br>
  <sub>No cloud. No tracking. No compromises.</sub>
</p>
