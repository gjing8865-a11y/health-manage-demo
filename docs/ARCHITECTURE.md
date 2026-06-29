# Architecture Notes

This document explains how Qinglv Health Manager is organized today and what the
next portfolio-grade refactor should move toward.

## Product Scope

Qinglv Health Manager combines several health workflows in one Android app:

- account login and local profile persistence
- dashboard summaries for steps, exercise, heart-rate warning, and device status
- food logging with image-based recognition and calorie/macronutrient summaries
- outdoor exercise tracking with countdown, live metrics, and result records
- sleep analysis based on wearable/device signals
- weather lookup and device sync
- STM32 smart-device data over Wi-Fi/TCP, plus a hardware-free demo mode

## Current Implementation

The app is a Kotlin Android application using Jetpack Compose and Material 3.
`MainActivity` owns the Compose navigation graph and screens are split under
`ui/screens`.

```text
MainActivity
  -> Compose navigation destinations
  -> AppViewModel
       -> repositories
       -> Room DAOs
       -> SharedPreferences
       -> remote data sources for weather and food recognition
       -> WifiDeviceManager and TCP socket device channel
       -> StateFlow UI state
  -> Feature screens render state and send user events
```

The current implementation intentionally favors a working end-to-end prototype:
one central `AppViewModel` orchestrates network calls, permissions, device
connectivity, and screen state. Local persistence now sits behind a thin
repository layer, which makes the next ViewModel split safer and easier to test.
Food-recognition and weather HTTP calls also sit behind remote data sources. The
ViewModel is still too large for long-term maintenance, but data access is no
longer wired directly to DAOs, provider behavior is testable, and pure domain
calculations have started moving out of screen orchestration.

## Data And State

Local persistence uses Room:

- `UserDao` for account data
- `NoteDao` for health notes
- `FoodDao` for food records
- `WeeklyStepDao` for weekly step snapshots
- `SleepDao` for sleep records
- `ExerciseDao` for exercise history

Runtime UI state uses `StateFlow`. Reusable UI state models live in
`viewmodel/UiStateModels.kt`, including pending food items, weather state,
sleep trend points, and demo device payload presentation data.

Pure domain logic now lives under `domain/`:

- sleep estimation from heart rate, blood oxygen, and step samples
- latest/weekly exercise summary calculation
- food calorie and macronutrient statistics
- sustained high-heart-rate alert policy

## Device Flow

The device module supports two reviewer paths:

```text
Real hardware path:
DeviceScreen -> scan/connect Wi-Fi hotspot -> bind network -> TCP endpoint
             -> parse STM32 payload -> update dashboard, sleep, steps, vitals

Demo path:
DeviceScreen -> startDemoDeviceMode()
             -> generated STM32-like payload
             -> same dashboard, sleep, steps, vitals UI state
```

The demo path is important for portfolio review because a recruiter or engineer
can inspect the full health-data loop without owning the STM32 device.

## Target Architecture

The next refactor should split orchestration by responsibility:

```text
ui/screens
  -> feature ViewModels
domain
  -> use cases, validation, and health-summary calculations
data
  -> repositories
  -> Room data sources
  -> remote API data sources
device
  -> Wi-Fi/TCP connection
  -> STM32 payload parser
  -> STM32 endpoint resolver
  -> deterministic demo payload factory
```

Recommended next cuts:

1. Extract device connection state from `AppViewModel`.
2. Split screen-specific state into smaller ViewModels after repositories exist.
3. Add focused unit tests around repositories and data mapping.

## Known Technical Debt

- `AppViewModel` still owns too many responsibilities.
- Sleep, exercise, food-summary, and heart-rate alert calculations now live in the domain layer, but screen-level state still needs feature ViewModels.
- Room schema export is enabled; historical migrations before v9 still need source schema history if upgrade support is required.
- Release builds still need production API-key handling, release signing, and privacy notes.
- Some legacy comments contain encoding artifacts and should be cleaned.
- A short demo GIF or video would make the README even easier to scan.

## Verification

The repository includes GitHub Actions for:

- `:app:testDebugUnitTest`
- `:app:assembleDebug`

Local Windows builds should be run from an ASCII-only path if Gradle test workers
fail under a non-ASCII project directory.
