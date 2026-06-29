# Security And Privacy Notes

Qinglv Health Manager is a portfolio/demo project, but it handles health-related
records and device signals. This document explains the current safeguards and
what must be done before any production release.

## Data Stored Locally

The app stores account/profile data, food records, sleep records, weekly steps,
exercise history, and notes in local Room tables or local app storage.

The current demo does not include a backend account system. Local records are
intended for single-device review and should not be treated as a production
medical record system.

## API Keys And Local Secrets

API keys are loaded from `local.properties` at build time:

- `ZHIPU_API_KEY`
- `WEATHER_API_KEY`
- `AMAP_API_KEY`

`local.properties`, env files, Android package outputs, and signing/key files
are ignored by Git. `local.properties.example` documents the required keys
without committing real values.

Before publishing or sharing any public build, rotate keys that were ever used
in local development and prefer server-side token exchange for production API
access.

## Android Data Protection

Current app-level safeguards:

- `android:allowBackup="false"` disables Android cloud backup for the app.
- `data_extraction_rules.xml` excludes app databases, shared preferences, files,
  and external files from cloud backup and device transfer.
- `backup_rules.xml` excludes the same local data categories for older Android
  backup behavior.
- `android:usesCleartextTraffic="false"` disables cleartext HTTP by default.
- `network_security_config.xml` only permits cleartext traffic for the STM32
  local device IP `192.168.4.1`.
- `FileProvider` is not exported and grants file URI access only when requested.

## Permissions

The app requests permissions needed for its demo scope:

- Bluetooth and nearby-device permissions for wearable/device flows.
- Location and Wi-Fi permissions for hotspot scanning and weather/location
  features.
- Internet access for weather and food-recognition APIs.
- Vibration for heart-rate alerts.

Legacy broad storage permissions are not requested.

## Production Release Checklist

Before shipping outside portfolio review:

- Move API access behind a backend or short-lived token service.
- Add release signing outside the repository and document the signing process.
- Review third-party SDK privacy requirements for AMap, weather, and AI food
  recognition providers.
- Add a user-facing privacy policy covering local health records and optional
  network-backed features.
- Add historical Room migrations if supporting upgrades from pre-v9 installs.
- Decide whether release builds should enable minification/obfuscation and
  verify the full QA checklist afterward.
