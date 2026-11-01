# Contributing to Scout

Thank you for your interest in contributing to Scout! This document provides guidelines and instructions for contributing.

## Code of Conduct

Be respectful, inclusive, and constructive. We're all here to build something useful together.

## How to Contribute

### Reporting Bugs

1. **Check existing issues** to avoid duplicates
2. **Open a new issue** with:
   - A clear, descriptive title
   - Steps to reproduce the bug
   - Expected vs. actual behavior
   - Your device model and Android version
   - Screenshots or logs if available

### Suggesting Features

1. Open an issue with the `enhancement` label
2. Describe the feature and why it would be useful
3. If it's a new module, explain the voice/text commands it would handle

### Submitting Code

1. **Fork** the repo and create a branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Follow the existing code style:**
   - Kotlin with Jetpack Compose
   - Material 3 design patterns
   - All new features must work **100% offline**
   - No internet permissions — ever

3. **Write or update tests** in `app/src/test/`

4. **Test your changes:**
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

5. **Commit with clear messages:**
   ```bash
   git commit -m "Add offline calculator module"
   ```

6. **Push and open a Pull Request** against `main`

## Adding a New Module

Scout's plugin architecture makes adding features straightforward:

1. Create your module class implementing `AssistantModule`
2. Place it under `app/src/main/java/com/pocketcore/assistant/modules/`
3. Register it in `PocketCoreApp.kt` with `FeatureRegistry.registerModule(YourModule())`
4. See `modules/template/ModuleTemplate.kt` for a complete example

### Module Checklist

- [ ] Implements `AssistantModule` interface
- [ ] Has a unique `id`
- [ ] `canHandle()` correctly identifies relevant input
- [ ] `execute()` works completely offline
- [ ] Registered in `PocketCoreApp.kt`
- [ ] Unit tests added
- [ ] No internet or network-related code

## Project Structure

```
app/src/main/java/com/pocketcore/assistant/
├── core/          # Plugin interfaces and routing
├── modules/       # Feature modules (reminder, notes, app launcher)
├── data/          # Room database, DAOs, entities
├── ui/            # Jetpack Compose UI and ViewModel
├── receiver/      # Broadcast receivers (alarms)
├── tile/          # Quick Settings tile
└── widget/        # Home screen widget
```

## Build Requirements

- JDK 17
- Android SDK (API 26–35)

## Questions?

Open an issue — we're happy to help!
