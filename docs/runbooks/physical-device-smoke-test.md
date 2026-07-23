# Physical Device Smoke Test

This procedure is for a future test only. It was not performed while adding the firmware foundation.

## Physical Transport Profiles

| Profile | Build directory | Explicit SDKCONFIG | SDKCONFIG defaults | Device transport and trust |
| --- | --- | --- | --- | --- |
| Default secure | `build-secure` | `sdkconfig.profile-secure` | `sdkconfig.defaults` | `https://` + `wss://`, public CA bundle |
| Self-signed HTTPS test | `build-lan-test-cert` | `sdkconfig.profile-lan-test-cert` | `sdkconfig.defaults;sdkconfig.lan-test.defaults` | `https://` + `wss://`, embedded ignored certificate |
| Private LAN HTTP development | `build-lan-http` | `sdkconfig.profile-lan-http` | `sdkconfig.defaults;sdkconfig.lan-http.defaults` | Private IPv4 `http://` + `ws://` only |

The private LAN HTTP profile is for deliberate development testing on a trusted private network. Build the LAN firmware and start the LAN server overlay with:

```powershell
Push-Location firmware
idf.py -B build-lan-http -D IDF_TARGET=esp32s3 -D SDKCONFIG="sdkconfig.profile-lan-http" -D SDKCONFIG_DEFAULTS="sdkconfig.defaults;sdkconfig.lan-http.defaults" build
if ($LASTEXITCODE -ne 0) { throw 'LAN HTTP firmware build failed' }
Pop-Location
docker compose -f compose.yaml -f compose.lan.yaml up --build -d
& .\scripts\verify-lan-compose.ps1
```

List candidate private IPv4 addresses instead of guessing the hotspot address:

```powershell
Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object { $_.IPAddress -match '^(10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.)' } |
  Select-Object InterfaceAlias, IPAddress
```

Select the Windows hotspot adapter's actual address. Use `http://<selected-address>:8080` only as the server base URL in the USB provisioning JSON. Access the administrator UI locally through `http://localhost:8080`. Never copy the SSID, password, pairing code, or complete provisioning request into evidence.

LAN mode deliberately has no TLS. Claim and refresh redirects are rejected. Device tokens remain in the `Authorization` header and encrypted NVS, but a LAN attacker can observe them because the traffic is unencrypted. PostgreSQL and Redis remain unexposed, and the device remains `motion_disabled`. The only operator-facing LAN statuses required by this feature are `invalid_request`, `wifi_connection_failed`, `claim_failed`, and `needs_usb_repair`.

Rebooting LAN firmware preserves the device identity and retries using the existing bounded backoff. If secure firmware is flashed over an identity stored by LAN HTTP firmware, the device must print `needs_usb_repair` and wait for fresh HTTPS USB pairing. It must not reinterpret or upgrade the stored HTTP origin.

After LAN testing, restore the loopback deployment:

```powershell
docker compose -f compose.yaml up -d --force-recreate server
```

## Preconditions

- Review the CoreS3 power setup and keep all external motion hardware disconnected.
- Build the firmware with the ESP-IDF version pinned by `firmware/sdkconfig.defaults`.
- Select one physical transport profile and keep its firmware configuration, server base URL, and trust model aligned:
  - Default secure firmware requires an `https://` origin whose certificate chains to the ESP-IDF public CA bundle, then derives a same-origin `wss://` device endpoint.
  - Self-signed HTTPS test firmware also requires an `https://` origin and derives `wss://`, but the server must present the test certificate embedded from `firmware/main/lan-test-server.pem`. This profile trusts that certificate instead of the public CA bundle and must not be used as a production image.
  - Private LAN HTTP development firmware requires an `http://` origin containing a literal private IPv4 address and derives a same-origin `ws://` device endpoint. It uses no TLS and must remain on a trusted private development network.
- The selected origin must serve the fixed pairing claim path `/api/v1/pairing/claim`. After a successful claim, the device derives the fixed same-origin WebSocket path `/api/v1/ws/device`; it does not accept a server-provided arbitrary WebSocket URL. The device sends its bearer credential only in the WebSocket `Authorization` header, which TLS protects in the two HTTPS profiles but LAN observers can read in the HTTP development profile.
- Review the target device's eFuse inventory before a first flash. This project enables HMAC-protected NVS encryption; first encrypted-NVS initialization on an unprovisioned device can write the configured HMAC key to eFuse block 5. Treat that as permanent security provisioning.
- Use a deliberate serial port value in place of `<PORT>`; do not reuse a port from an unrelated device.

## Future Flash And Check

1. From `firmware/`, build and flash the firmware revision that matches the running server using `-D IDF_TARGET=esp32s3` plus the selected profile's mapped build directory, explicit `SDKCONFIG` file, and `SDKCONFIG_DEFAULTS`, then run `idf.py -B <BUILD_DIRECTORY> -p <PORT> monitor`. Credential checks below are invalid until this matching profile firmware is flashed. Do not rely on the root `firmware/sdkconfig`, because its generated target or mode options may belong to a different profile.
2. Confirm the serial output contains `CoreS3 display, touch, microphone, and speaker initialized; actuators remain disabled`, and visually confirm the display shows the idle face.
3. Confirm the serial output contains `Safety state is motion_disabled` and `safety_state=motion_disabled`.
4. With no device identity, confirm the serial output says `Valid device identity unavailable; waiting before transport start`.
5. With no Wi-Fi credentials, confirm the serial output says `Wi-Fi credentials are unavailable; waiting without provisioning`.
6. With saved Wi-Fi credentials, temporarily remove AP availability and confirm `Wi-Fi reconnect scheduled` appears with delays that increase no higher than 60 seconds. Restore AP availability and confirm `Wi-Fi connected; transport may connect when identity is available` appears.
7. Confirm no attached hardware moves. Stop the monitor and remove power before connecting any future hardware integration.

## Voice, Screensaver, And Reminder Checks

Perform these checks only after the user has explicitly confirmed the target device, serial port, transport profile, and Git commit to flash. Configure the administrator speech settings with either a test-capable OpenAI-compatible ASR/TTS provider or the supported Alibaba Cloud Model Studio Beijing adapter without copying its API key, Workspace ID, or full provider response into evidence. Run **测试语音识别与合成** successfully before the physical checks; this test consumes one short TTS request and one ASR request.

For a custom wake-word image, first complete [Custom Wake Word Model](custom-wake-word-model.md). The selected model name, model source and wake-word text may be recorded; model binaries, original audio, credentials and complete external-service responses may not.

1. Confirm the serial output contains `WakeNet model selected` with the expected model, source and wake words, followed by `WakeNet listening`. For the default image, the wake words must be `Hi,Stack Chan`; for a custom image, `source=configured` and the wake words must match the authorized phrase. Leave the device untouched for at least one minute and confirm the server receives no voice-turn upload before a wake-word detection.
2. Say the wake phrase expected by the current image, then a short benign request. Confirm the face changes to listening, recording ends after local silence or at the eight-second maximum, the face changes to thinking, and the speaker plays the synthesized reply. Confirm the device returns to the idle face and a second wake phrase works, proving microphone listening resumed after playback.
3. Temporarily make the configured speech provider unavailable without exposing its credentials. Confirm the device shows the error face briefly, returns to idle, remains `motion_disabled`, and can complete another turn after the provider is restored.
4. Leave the device untouched for 300 seconds. Confirm the display changes to low brightness and only the two pupils move every 2.5 seconds; the background, eye whites, mouth and status indicator must not periodically redraw or visibly flash. Touch the screen and confirm normal brightness and the current face return immediately.
5. In the reminder management page, create a one-time benign reminder for the online test device a few minutes in the future. Let the device enter the screensaver first. At the scheduled time, confirm it exits the screensaver, downloads only the fixed same-origin reminder path, speaks the reminder, sends `accepted=true`, and the reminder becomes `DELIVERED`.
6. Repeat with the device offline at the scheduled time. Confirm the reminder remains `PENDING`, reconnect the device, then confirm it is dispatched, spoken, acknowledged once, and becomes `DELIVERED`.
7. During all checks, confirm no actuator initializes or moves and `safety_state=motion_disabled` remains unchanged.

## USB Provisioning Contract

After the foundation logs `USB provisioning ready`, send exactly one compact provisioning JSON line over the base USB serial connection. Supply the required type, Wi-Fi network name and password, server base URL for the firmware's selected transport profile, and fresh one-time pairing code directly to the device; do not paste that line into test evidence.

The preferred operator path is the Fantastic-admin page **机器人设备 → 设备配网**:

1. Open the administrator UI through `http://localhost:8080` for LAN development, or an HTTPS origin for secure deployment, using the latest Microsoft Edge or Google Chrome.
2. Close ESP-IDF monitor and any other program holding the robot serial port, connect the robot through physical USB, then choose **通过 USB 连接机器人**.
3. Enter the Wi-Fi name and password plus the server origin that matches the flashed firmware profile, then choose **写入 Wi-Fi 配置**.
4. The page requests a fresh one-time pairing code from the server and writes the compact JSON line directly to the selected serial port. The Wi-Fi password is not sent to the server or browser storage and is cleared from the form after success or failure.
5. Wait for the non-secret `complete` result, then confirm the device appears online. If Web Serial is unavailable, use the manual one-time pairing-code section and a trusted serial tool.

- Default secure firmware accepts only an `https://` origin, with at most a trailing slash and no non-root path, query, fragment, or userinfo. The server certificate must chain to the ESP-IDF public CA bundle.
- Self-signed HTTPS test firmware applies the same `https://` origin syntax, but the server must present the certificate embedded from `firmware/main/lan-test-server.pem`; the public CA bundle is not the trust source for this profile.
- Private LAN HTTP development firmware accepts only `http://` with a literal address in `10.0.0.0/8`, `172.16.0.0/12`, or `192.168.0.0/16` and an optional valid port. It rejects a trailing slash, paths, queries, fragments, userinfo, DNS names, `localhost`, IPv6, loopback, link-local, and public IPv4 addresses.
- It reports only a status JSON line: `started`, `complete`, or a non-secret failure code. It never prints the SSID, password, pairing code, device token, or raw server response.
- On `complete`, verify the dashboard shows the device online.
- Keep the device connected through a credential refresh and confirm it reconnects successfully. This is a required check after flashing matching firmware.
- With the same physical device and hardware identity, provision again over USB with a fresh one-time code. Confirm the replacement credentials reconnect successfully and the prior credentials no longer work. This same-hardware re-pair is also a required check after flashing matching firmware.
- Use the temporary protocol-log procedure below, then send Stop motion and confirm the device remains physically still. The dashboard does not currently expose ACK evidence.
- Reconfigure Wi-Fi or pair again only with physical USB access and a fresh one-time code. A valid re-provisioning request first removes the old device identity, so a failed replacement requires a new code rather than restoring the old credential.

## Protocol ACK Debug Evidence

Before sending Stop motion, temporarily enable package-level protocol DEBUG without editing `compose.yaml`:

```powershell
@'
services:
  server:
    environment:
      LOGGING_LEVEL_COM_KJ_STACKCHAN_DEVICE: DEBUG
'@ | docker compose -f compose.yaml -f - up -d --force-recreate server
docker compose ps server
```

After the recreated server is healthy and the device is online again, send Stop motion, confirm no physical movement, and inspect the protocol log:

```powershell
docker compose logs --since 10m server | Select-String 'acknowledged command'
```

Correlate the matching acknowledgement with `accepted=true`. Copy only the required ACK line into evidence and redact nonessential identifiers. Whether the check passes or fails, remove the temporary DEBUG override by recreating from the base Compose file:

```powershell
docker compose -f compose.yaml up -d --force-recreate server
docker compose ps server
```

## Evidence Rules

- Record only timestamps, pass/fail results, firmware version, non-secret protocol identifiers needed to correlate the command, and the relevant redacted log lines.
- Never record access or refresh credentials, `Authorization` headers, pairing codes, Wi-Fi passwords, complete provisioning requests, or complete HTTP/WebSocket bodies.
- Keep the device serial monitor and the server protocol log as the ACK evidence path; do not describe the dashboard or static safe face as receiving an ACK.

The current firmware initializes the CoreS3 display, touch panel, microphone, and speaker through M5Unified. It does not initialize the camera, NFC, external LED, servo, or other motion driver. Audio and screen behavior require the checks above before being claimed as physically verified; motion remains disabled throughout.
