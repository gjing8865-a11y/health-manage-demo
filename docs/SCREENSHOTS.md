# Demo Screenshots

These screenshots were captured from the debug build running on an Android
emulator. The device and sleep screens use the hardware-free STM32 demo mode, so
reviewers can inspect the core health-data flow without physical hardware.

## Dashboard

![Health dashboard](images/screen-01-launch.png)

The dashboard shows the main health summary, weekly activity trend, and quick
access to the app's core modules.

## Device Setup

![Device setup](images/screen-02-device.png)

The device page keeps the real STM32 hotspot/TCP path visible while also
offering a demo-data action for portfolio review.

## STM32 Demo Data

![STM32 demo data](images/screen-03-device-demo.png)

Demo mode simulates heart rate, blood oxygen, steps, battery, sleep score, and
weekly step data. This proves the downstream UI can be reviewed without pairing
the physical smart band.

## Sleep Analysis

![Sleep analysis](images/screen-04-sleep-demo.png)

The sleep page consumes demo vitals and displays a sleep score, timing metrics,
deep-sleep duration, wake count, and sleep-stage timeline.
