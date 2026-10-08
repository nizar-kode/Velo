# Velo Test Plan & Procedures

## Verification Levels

Velo is tested at three levels:
1. **Windows Receiver Unit & Integration Tests**: C# xUnit test suite covering discovery, security, crypto tokens, subpixel input accumulation, and end-to-end WebSocket pipelines.
2. **Android Unit & Serialization Tests**: JUnit test suite covering protocol JSON serialization and payload validation.
3. **Hardware / Device End-to-End Tests**: Interactive physical testing with phone and PC.

---

## 1. Automated Test Execution

### Windows Test Suite (.NET 10 xUnit)
```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" test AirPilot\windows\VeloDesktop.Tests\VeloDesktop.Tests.csproj
```
Expected result: **10 passed, 0 failed**.

### Android Test Suite (Gradle JUnit)
```powershell
cd AirPilot\android\AirPilotAndroid
.\gradlew test
```
Expected result: **BUILD SUCCESSFUL, 4 passed**.

---

## 2. Test Cases Overview

| Test ID | Area | Description | Expected Result |
|---|---|---|---|
| **NET-01** | UDP Beacon | Responds to `VELO_PROBE` | Unicast reply starting with `VELO_BEACON\|` |
| **NET-02** | UDP Beacon | Responds to legacy query | Backward compatible discovery reply |
| **NET-03** | UDP Beacon | Creates advertisement packet | Valid 4-part pipe-delimited packet |
| **SEC-01** | Security | PIN generation | Produces 6-digit cryptographic PIN |
| **SEC-02** | Security | Pairing success | Valid PIN yields 256-bit token |
| **SEC-03** | Security | Pairing rejection | Invalid PIN returns rejected |
| **SEC-04** | Security | Pairing revocation | Revoked tokens cannot authenticate |
| **E2E-01** | WebSocket | Full pipeline | Connects to `/velo`, pairs, authenticates, moves mouse |
| **INP-01** | Simulator | Subpixel accumulation | Fractional mouse deltas accumulate smoothly |
| **AND-01** | Android | Protocol models | JSON serialization matches schema |
| **AND-02** | Android | Discovery parser | Parses `VELO_BEACON` pipe format and JSON |
| **AND-03** | Android | Pairing payload | Generates compliant handshake payload |
