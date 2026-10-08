# Velo Development Roadmap

## Phase 1: Foundational Modernization & Core Architecture (Complete)
- [x] Modern .NET 10 WPF Receiver with system tray and dark mode interface.
- [x] Jetpack Compose Material 3 UI for Android controller.
- [x] Zero-config UDP discovery with `VELO_BEACON|<HOSTNAME>|<IP>|51821` and `VELO_PROBE`.
- [x] 256-bit cryptographic token pairing and PIN authorization.
- [x] Subpixel cursor accumulation using `user32.dll SendInput`.
- [x] Complete test suites in C# xUnit and Kotlin JUnit.

## Phase 2: Input Expansion & Touch Ergonomics
- [x] Tactile left and right click buttons with haptic feedback.
- [ ] Virtual Laser Pointer overlay integration with gyro motion sensor input.
- [ ] Multi-touch gestures (pinch-to-zoom, three-finger swipe for app switching).
- [ ] Full virtual keyboard input stream and modifier keys (Ctrl, Alt, Win).

## Phase 3: Presentation & Media Control
- [ ] Slide deck presenter mode (Next / Previous, slide notes display, elapsed timer).
- [ ] Media control deck (Volume slider, play/pause, track seeking via Windows CoreAudio API).
- [ ] Presentation laser pointer stabilization with Kalman filtering.

## Phase 4: Packaging & Distribution
- [ ] Single-file standalone MSIX / portable Windows binary.
- [ ] Signed Android APK & Google Play release (`com.velo.remote`).
- [ ] Automated GitHub Actions CI/CD release workflow.
