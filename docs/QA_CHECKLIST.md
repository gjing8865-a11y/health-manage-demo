# QA Checklist

Use this checklist before sharing the repository or recording screenshots.

## Build And Tests

- Run `./gradlew :app:assembleDebug --console=plain`.
- Run `./gradlew :app:testDebugUnitTest --console=plain`.
- Confirm GitHub Actions is green for the latest `main` commit.
- Confirm `local.properties` is not tracked and real API keys are not committed.

## First Launch

- Install the debug build on an emulator or device.
- Register a new local account.
- Log out and log back in.
- Restart the app and confirm auto-login/profile restoration still works.

## Core App Flows

- Open the dashboard and confirm health summary cards render without crashing.
- Add a health note.
- Add a manual food record and confirm calorie/macronutrient totals update.
- Start an exercise flow, complete it, and confirm the exercise result appears.
- Open the sleep screen and confirm empty-state and trend UI render correctly.

## Hardware-Free Device Review

- Open the device screen.
- Tap the demo data action.
- Confirm heart rate, blood oxygen, steps, weekly steps, battery, and sleep data update.
- Return to dashboard and confirm device-driven data is reflected there.
- Exit demo mode and confirm device state resets.

## API-Backed Optional Flows

- Add `ZHIPU_API_KEY` locally and test image-based food recognition.
- Add `WEATHER_API_KEY` locally and test weather sync.
- Add `AMAP_API_KEY` locally before testing map/provider-specific flows.
- Remove keys again and confirm the app shows graceful unavailable/error states.

## Privacy And Security

- Confirm `local.properties`, APK/AAB outputs, and release folders stay ignored.
- Confirm Android backup and device-transfer extraction are disabled for local health data.
- Confirm cleartext traffic is restricted to the STM32 local device IP.
- Confirm Room schema files are generated under `app/schemas`.
