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

## Device-authenticated voice HTTP

The device uploads one bounded WAV only after the local `Hi, Stack Chan` WakeNet model detects the wake phrase:

```http
POST /api/v1/device/voice/turn
Authorization: Bearer <device-token>
Content-Type: audio/wav
Accept: application/vnd.stackchan.voice-turn
```

The body must be between 44 bytes and 512 KiB. The server authenticates the device token independently of any administrator session, transcribes the WAV, appends the turn to that device's conversation, generates the assistant reply, and synthesizes a PCM WAV. A successful response has media type `application/vnd.stackchan.voice-turn` and this binary layout:

| Offset | Value |
| --- | --- |
| `0..3` | ASCII magic `SCV1` |
| `4..7` | unsigned 32-bit big-endian metadata length |
| `8..` | UTF-8 JSON metadata with exactly `transcript` and `reply` |
| remaining bytes | validated PCM WAV for device playback |

Error responses use safe JSON and do not include provider responses, provider keys, device credentials, or authorization payloads.

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

The schema is strict: no URL or extra field is accepted, and `reminder_id` must be a canonical UUID. The device derives the fixed same-origin path below from its provisioned server origin and sends the same device Bearer token:

```http
GET /api/v1/device/reminders/f20b6177-3f7a-466a-9eae-70120bbf1912/audio
Authorization: Bearer <device-token>
Accept: audio/wav
```

The server returns audio only when the reminder belongs to the authenticated device and is currently `DISPATCHED`. Offline reminders remain `PENDING`; after the device reconnects, the scheduler synthesizes the WAV and sends `speak_reminder` to the live authenticated session. A dispatch left unacknowledged for more than five minutes is recovered to `PENDING`.

The server sends `stop_motion` only to a currently live, authenticated session and creates no offline motion-command queue. Voice-detection configuration is persisted centrally and resent on reconnect rather than queued as an actuator command. No inbound event, acknowledgement, voice turn, reminder, or voice-detection configuration can enable motion or request an actuator action.
