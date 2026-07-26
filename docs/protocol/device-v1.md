# Device Protocol v1

Devices connect with a WebSocket upgrade request to `/api/v1/ws/device`. The request must carry the pairing-issued JWT in exactly one `Authorization: Bearer <token>` header. Query-string credentials such as `access_token` are rejected so tokens do not enter URLs, access logs, or browser history. The token must be signed by the companion service, have `scope` set to `device`, contain a UUID subject, include the current credential version in `ver`, include an `exp` expiry claim, and be unexpired. The server derives the device identity only from that verified subject; it ignores any device identifier supplied in the request.

The server keeps one active connection per authenticated device. A new connection atomically replaces and closes the previous live connection. A replaced session cannot send events or receive commands, and its later disconnect cannot remove the replacement session from command delivery. The server rechecks the authenticated token expiry before accepting every event or sending a command. Re-pairing rotates the credential version and closes any active session authenticated with the previous version after the rotation commits.

## Event ordering

Every inbound event has a positive integer `sequence`. Sequences are monotonic for one WebSocket connection. Duplicate or lower sequences are ignored. A reconnect starts a new sequence stream. Malformed JSON, unknown types, invalid fields, or invalid sequences receive this response and do not update device state:

```json
{"type":"error","code":"invalid_event","message":"event rejected"}
```

## Device events

The accepted heartbeat includes the current firmware version:

```json
{"type":"heartbeat","sequence":1,"battery_percent":75,"rssi":-58,"safety_state":"motion_disabled","firmware_version":"b954a43"}
```

`battery_percent` is an integer from 0 through 100. `rssi` is an integer. `safety_state` must be exactly `motion_disabled` in this phase. `firmware_version` is one through 80 ASCII letters, digits, dots, underscores, or hyphens. A heartbeat records device liveness and cannot enable motion; when a version is present the server persists it as the device's current firmware version.

For a rolling upgrade, the server also accepts the original five-field heartbeat from older firmware. New firmware must always send `firmware_version`.

Devices may acknowledge a received command with:

```json
{"type":"command_ack","sequence":2,"command_id":"a command identifier","accepted":true}
```

`command_id` is nonblank and `accepted` is boolean. An acknowledgement is never treated as an actuator command. For `speak_reminder`, `accepted=true` means the device downloaded and completed playback, while `accepted=false` means download or playback failed. The server uses that result to mark the durable reminder `DELIVERED` or `FAILED`. For `configure_voice_detection`, the acknowledgement only reports whether the bounded local settings were accepted; it does not change reminder state.

For `install_wake_model`, `accepted=true` means the device downloaded the fixed same-origin artifact, verified its size, SHA-256 and ESP-SR structure, and persisted a pending model slot before restarting. It does not mean the new WakeNet listener is healthy; final health is reported separately after restart. `accepted=false` means the model was not selected for boot.

New firmware may report a privacy-safe voice turn stage:

```json
{"type":"voice_turn_stage","sequence":3,"turn_id":"550e8400-e29b-41d4-a716-446655440000","stage":"LISTENING","elapsed_ms":120}
```

`turn_id` is a canonical UUID generated at local wake detection. `elapsed_ms` is an integer from 0 through 300000 measured from the device monotonic clock. Device stages are limited to `WAKE_DETECTED`, `LISTENING`, `SPEECH_CAPTURED`, `UPLOAD_STARTED`, `PLAYBACK_STARTED`, `PLAYBACK_COMPLETED`, `LISTENING_RESUMED`, and `FAILED`. A failed event has exactly one additional `failure_code` field from the protocol allowlist; successful stages must not include it. Extra fields, free text, transcripts, replies, audio, URLs, credentials, and server-only stages are rejected.

## Device-authenticated voice HTTP

The device uploads one bounded WAV only after the local `Hi, Stack Chan` WakeNet model detects the wake phrase:

```http
POST /api/v1/device/voice/turn
Authorization: Bearer <device-token>
X-StackChan-Turn-Id: 550e8400-e29b-41d4-a716-446655440000
Content-Type: audio/wav
Accept: application/vnd.stackchan.voice-turn
```

The turn header is optional for rolling compatibility. New firmware sends the same canonical UUID used in stage events; when older firmware omits it, the server creates one. The successful response echoes the resolved ID in `X-StackChan-Turn-Id` without changing the SCV1 body.

The body must be between 44 bytes and 512 KiB. The server authenticates the device token independently of any administrator session, transcribes the WAV, appends the turn to that device's conversation, generates the assistant reply, and synthesizes a PCM WAV. A successful response has media type `application/vnd.stackchan.voice-turn` and this binary layout:

| Offset | Value |
| --- | --- |
| `0..3` | ASCII magic `SCV1` |
| `4..7` | unsigned 32-bit big-endian metadata length |
| `8..` | UTF-8 JSON metadata with exactly `transcript` and `reply` |
| remaining bytes | validated PCM WAV for device playback |

Error responses use safe JSON and do not include provider responses, provider keys, device credentials, or authorization payloads.

Diagnostic persistence is separate from conversation content. It stores only device and turn IDs, strict stages, stage source, server receive time, bounded device elapsed time, status, and allowlisted failure codes for seven days. It never stores the uploaded WAV, transcript, reply, provider response, API key, JWT, or refresh token.

## Local interaction presentation

The device maps its local interaction phase and WebSocket connectivity to distinct visible states: idle, listening, processing, speaking, success, no speech, offline, and recoverable error. Offline connectivity overrides the current phase until the authenticated WebSocket reconnects. Success, no-speech, and recoverable-error feedback are bounded and return to idle; the existing low-brightness pupil screensaver is limited to idle or offline presentation.

This presentation is local-only. It adds no device event, server command, free-text diagnostic field, actuator behavior, or compatibility requirement for older firmware. `voice_turn_stage` remains the only remote diagnostic contract.

## Server commands

The motion-safety command remains:

```json
{"type":"stop_motion","command_id":"550e8400-e29b-41d4-a716-446655440000"}
```

The durable spoken-reminder command is:

```json
{"type":"speak_reminder","command_id":"cmd-123","reminder_id":"f20b6177-3f7a-466a-9eae-70120bbf1912"}
```

The local wake and recording configuration command is:

```json
{"type":"configure_voice_detection","command_id":"cmd-voice","wake_sensitivity":"SENSITIVE","speech_start_threshold":350,"speech_silence_threshold":200}
```

The schema is strict. `wake_sensitivity` is exactly `NORMAL` or `SENSITIVE`. `speech_start_threshold` is an integer from 100 through 5000, `speech_silence_threshold` is an integer from 50 through 4000, and the silence threshold must be lower than the start threshold. The server sends the current values after an administrator saves speech settings and whenever a device establishes a new authenticated connection. Firmware applies the values locally and never receives a provider key or administrator credential.

The safe wake-model installation command is:

```json
{"type":"install_wake_model","command_id":"cmd-model","job_id":"111e8400-e29b-41d4-a716-446655440000","model_name":"wn9l_stackchan_custom","sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","artifact_size":585211}
```

The schema is strict. `job_id` is a canonical UUID, `model_name` is 1 through 31 lowercase ASCII letters, digits or underscores, `sha256` is exactly 64 lowercase hexadecimal characters, and `artifact_size` is 1 through 1048576. A URL or any extra field is rejected. The device derives this fixed same-origin path and authenticates with its existing device Bearer token:

```http
GET /api/v1/device/wake-models/111e8400-e29b-41d4-a716-446655440000/artifact
Authorization: Bearer <device-token>
Accept: application/vnd.stackchan.wake-model
```

The server returns an artifact only while the authenticated device owns the matching `READY` or `INSTALLING` task. Responses use `Cache-Control: no-store`. The device alternates between `model_a` and `model_b`; the factory `model` partition is never an OTA target.

After boot-time WakeNet health confirmation or automatic rollback, the device sends:

```json
{"type":"wake_model_status","sequence":3,"job_id":"111e8400-e29b-41d4-a716-446655440000","status":"INSTALLED","model_name":"wn9l_stackchan_custom","sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
```

`status` is exactly `INSTALLED` or `ROLLED_BACK`. The event is persisted on the device and sent once on every WebSocket reconnect, so the server must handle repeats idempotently. It never enables motion.

The schema is strict: no URL or extra field is accepted, and `reminder_id` must be a canonical UUID. The device derives the fixed same-origin path below from its provisioned server origin and sends the same device Bearer token:

```http
GET /api/v1/device/reminders/f20b6177-3f7a-466a-9eae-70120bbf1912/audio
Authorization: Bearer <device-token>
Accept: audio/wav
```

The server returns audio only when the reminder belongs to the authenticated device and is currently `DISPATCHED`. Offline reminders remain `PENDING`; after the device reconnects, the scheduler synthesizes the WAV and sends `speak_reminder` to the live authenticated session. A dispatch left unacknowledged for more than five minutes is recovered to `PENDING`.

The server sends `stop_motion` only to a currently live, authenticated session and creates no offline motion-command queue. Voice-detection configuration is persisted centrally and resent on reconnect rather than queued as an actuator command. No inbound event, acknowledgement, voice turn, reminder, voice-detection configuration, or wake-model operation can enable motion or request an actuator action.
