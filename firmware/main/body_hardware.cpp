/*
 * K151 safety-oriented body support.
 *
 * Register addresses and physical wiring are derived from the MIT-licensed
 * M5Stack StackChan firmware. LTR-553 operation follows the Lite-On datasheet.
 * This implementation intentionally exposes no raw samples and accepts no
 * caller-provided angles, speeds, loops, or URLs.
 */
#include "body_hardware.h"
#include "companion_hardware.h"
#include "voice_control.h"
#include "body_touch_policy.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>

#include "bsp/esp-bsp.h"
#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "driver/uart.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "nvs.h"

namespace {

constexpr uart_port_t SERVO_UART = UART_NUM_1;
constexpr int SERVO_TX_PIN = 6;
constexpr int SERVO_RX_PIN = 7;
constexpr int SERVO_BAUD = 1000000;
constexpr uint8_t YAW_SERVO_ID = 1;
constexpr uint8_t PITCH_SERVO_ID = 2;
constexpr uint8_t SERVO_TORQUE_REGISTER = 40;
constexpr uint8_t SERVO_GOAL_POSITION_REGISTER = 42;
constexpr uint8_t SERVO_PRESENT_POSITION_REGISTER = 56;
constexpr uint8_t SERVO_MOVING_REGISTER = 66;
constexpr uint8_t SERVO_MIN_POSITION_REGISTER = 9;
constexpr uint8_t SERVO_DEAD_ZONE_REGISTER = 26;
constexpr uint8_t SERVO_PRESENT_LOAD_REGISTER = 60;
constexpr uint8_t SERVO_PRESENT_CURRENT_REGISTER = 69;
constexpr int SERVO_RAW_MIN = 0;
constexpr int SERVO_RAW_MAX = 1000;
constexpr int SERVO_RAW_PER_10_DEGREES = 32;
constexpr int PITCH_CENTER_DEGREES = 45;
// Factory StackChan uses raw 620 as the pitch zero reference. This is only a
// diagnostic reference: an individual assembly can have a different center.
constexpr int FACTORY_PITCH_ZERO_RAW = 620;
constexpr int PITCH_MIN_DEGREES = 10;
constexpr int PITCH_MAX_DEGREES = 80;
constexpr int YAW_MIN_DEGREES = -20;
constexpr int YAW_MAX_DEGREES = 20;
// Narrower than half the smallest template's total travel, so stationary
// feedback cannot satisfy every target in any fixed sequence.
constexpr int SERVO_FEEDBACK_TOLERANCE_RAW = 8;
// If the SCSCL still reports motion at the programmed run-time boundary,
// give a near target a bounded chance to settle. Keep honoring local stop and
// the motion watchdog.
constexpr int SERVO_NEAR_TARGET_SETTLE_MS = 250;
constexpr int SERVO_NEAR_TARGET_POLL_MS = 50;
constexpr int SERVO_IDLE_RECOVERY_WAIT_MS = 200;
constexpr int SERVO_IDLE_RECOVERY_ATTEMPTS = 2;
// M5Stack's K151 uses a 20 ms SCSCL goal for its own motion scheduler. Our
// fixed expression frames use a 50 ms command cadence. Give each servo goal
// 60 ms so consecutive small moves overlap instead of stopping between goals.
// Multi-hundred-ms goals previously undershot on this device.
constexpr uint16_t SERVO_GOAL_TIME_MS = 60;
constexpr int SERVO_FRAME_STEP_MS = 50;

constexpr uint8_t PY32_ADDRESS = 0x6F;
constexpr uint8_t PY32_VERSION_REGISTER = 0x02;
constexpr uint8_t PY32_GPIO_MODE_LOW_REGISTER = 0x03;
constexpr uint8_t PY32_GPIO_OUTPUT_LOW_REGISTER = 0x05;
constexpr uint8_t PY32_GPIO_INPUT_LOW_REGISTER = 0x07;
constexpr uint8_t PY32_GPIO_PULL_UP_LOW_REGISTER = 0x09;
constexpr uint8_t PY32_GPIO_PULL_DOWN_LOW_REGISTER = 0x0B;
constexpr uint8_t PY32_SERVO_POWER_BIT = 0x01;

constexpr uint8_t INA226_ADDRESS = 0x41;
constexpr uint8_t INA226_BUS_VOLTAGE_REGISTER = 0x02;
constexpr uint8_t INA226_MANUFACTURER_ID_REGISTER = 0xFE;
constexpr uint8_t INA226_DIE_ID_REGISTER = 0xFF;
constexpr uint16_t INA226_MANUFACTURER_ID = 0x5449;
constexpr uint16_t INA226_DIE_ID_MASK = 0xFFF0;
constexpr uint16_t INA226_DIE_ID = 0x2260;
// INA226 bus-voltage LSB is 1.25 mV. Keep diagnostics categorical so raw
// battery telemetry never leaves the device log.
constexpr uint16_t BATTERY_SOURCE_ABSENT_RAW = 2400;  // 3.0 V
constexpr uint16_t BATTERY_SOURCE_LOW_RAW = 2800;     // 3.5 V

constexpr uint8_t LTR553_ADDRESS = 0x23;
constexpr uint8_t LTR553_ALS_CONTROL = 0x80;
constexpr uint8_t LTR553_PS_CONTROL = 0x81;
constexpr uint8_t LTR553_PS_LED = 0x82;
constexpr uint8_t LTR553_PS_PULSES = 0x83;
constexpr uint8_t LTR553_PS_RATE = 0x84;
constexpr uint8_t LTR553_ALS_RATE = 0x85;
constexpr uint8_t LTR553_PART_ID = 0x86;
constexpr uint8_t LTR553_MANUFACTURER_ID = 0x87;
constexpr uint8_t LTR553_DATA_START = 0x88;
constexpr uint8_t LTR553_STATUS = 0x8C;
constexpr uint8_t LTR553_PS_DATA_START = 0x8D;
// Match the M5Stack StackChan LTR553 example: 40 kHz IR pulses, 100% duty,
// 100 mA peak current, one pulse and a 50 ms proximity measurement period.
constexpr uint8_t LTR553_PS_LED_STACKCHAN = 0x3C;
constexpr uint8_t LTR553_PS_PULSES_STACKCHAN = 0x01;
constexpr uint8_t LTR553_PS_RATE_STACKCHAN = 0x00;
// On this CoreS3, a hand at the StackChan sensor window produced 16-99 while
// the uncovered sensor stayed below 16. Require independent fresh samples.
constexpr uint16_t PROXIMITY_PRESENT_THRESHOLD = 16;
constexpr uint16_t PROXIMITY_ABSENT_THRESHOLD = 15;
constexpr uint8_t PROXIMITY_CONFIRM_SAMPLES = 3;
constexpr int64_t PROXIMITY_BAND_LOG_INTERVAL_US = 2LL * 1000 * 1000;
constexpr uint8_t AMBIENT_CONFIRM_SAMPLES = 3;
constexpr int AMBIENT_BRIGHTNESS_DARK = 18;
constexpr int AMBIENT_BRIGHTNESS_DIM = 32;
constexpr int AMBIENT_BRIGHTNESS_NORMAL = 63;
constexpr int AMBIENT_BRIGHTNESS_BRIGHT = 78;

constexpr uint8_t SI12T_ADDRESS = 0x68;
constexpr uint8_t SI12T_SENSITIVITY_FIRST = 0x02;
constexpr uint8_t SI12T_SENSITIVITY_LAST = 0x06;
constexpr uint8_t SI12T_CONTROL_1 = 0x08;
constexpr uint8_t SI12T_CONTROL_2 = 0x09;
constexpr uint8_t SI12T_REFERENCE_RESET_1 = 0x0A;
constexpr uint8_t SI12T_REFERENCE_RESET_2 = 0x0B;
constexpr uint8_t SI12T_CHANNEL_HOLD_1 = 0x0C;
constexpr uint8_t SI12T_CHANNEL_HOLD_2 = 0x0D;
constexpr uint8_t SI12T_CALIBRATION_HOLD_1 = 0x0E;
constexpr uint8_t SI12T_CALIBRATION_HOLD_2 = 0x0F;
constexpr uint8_t SI12T_OUTPUT = 0x10;
constexpr uint8_t TOP_TOUCH_CONFIRM_SAMPLES = 2;

constexpr char BODY_NVS_NAMESPACE[] = "body_motion";
constexpr char BODY_NVS_YAW_CENTER[] = "yaw_center";
constexpr char BODY_NVS_PITCH_CENTER[] = "pitch_center";
constexpr char BODY_NVS_CALIBRATED[] = "calibrated";
constexpr int BODY_TASK_STACK_SIZE = 6144;
constexpr int BODY_TASK_PRIORITY = 2;
constexpr int BODY_TASK_CORE = 1;
constexpr int BODY_POLL_MS = 50;
// The factory firmware leaves VM enabled well before servo initialization.
// Our fail-closed design powers VM only for an operation, so allow the boost
// rail and both servos to finish a cold start before requesting feedback.
constexpr int SERVO_POWER_SETTLE_MS = SAFETY_MOTION_POWER_SETTLE_MS;
constexpr int SERVO_FEEDBACK_RETRY_DELAY_MS = 75;
constexpr int SERVO_FEEDBACK_ATTEMPTS = 3;
constexpr int64_t SERVO_RESPONSE_HEADER_TIMEOUT_US = 50000;
constexpr int SERVO_RESPONSE_POLL_MS = 10;

struct MotionFrame {
    int yaw_degrees;
    int pitch_degrees;
    uint16_t duration_ms;
};

struct MotionDefinition {
    const MotionFrame *frames;
    size_t frame_count;
};

struct MotionRequest {
    safety_motion_template_t motion;
    uint32_t stop_generation;
    uint32_t failure_count;
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
};

constexpr MotionFrame WAKE_FRAMES[] = {{-8, 45, 300}, {0, 63, 700}, {0, 45, 500}};
constexpr MotionFrame LOOK_USER_FRAMES[] = {{0, 60, 650}, {0, 45, 550}};
// Return from a visible upward nod to the calibrated forward pose. The former
// second frame crossed below that pose and repeatedly stopped short on this K151.
constexpr MotionFrame NOD_SMALL_FRAMES[] = {{0, 65, 1000}, {0, 45, 1000}};
constexpr MotionFrame THINK_FRAMES[] = {{16, 50, 800}, {10, 48, 450}, {0, 45, 550}};
constexpr MotionFrame DROWSY_FRAMES[] = {{-12, 45, 900}, {-5, 48, 650}, {0, 45, 600}};

template <size_t N>
constexpr bool stationary_feedback_cannot_pass(const MotionFrame (&frames)[N])
{
    int min_yaw = frames[0].yaw_degrees;
    int max_yaw = min_yaw;
    int min_pitch = frames[0].pitch_degrees;
    int max_pitch = min_pitch;
    for (size_t index = 1; index < N; index++) {
        min_yaw = frames[index].yaw_degrees < min_yaw ? frames[index].yaw_degrees : min_yaw;
        max_yaw = frames[index].yaw_degrees > max_yaw ? frames[index].yaw_degrees : max_yaw;
        min_pitch = frames[index].pitch_degrees < min_pitch ? frames[index].pitch_degrees : min_pitch;
        max_pitch = frames[index].pitch_degrees > max_pitch ? frames[index].pitch_degrees : max_pitch;
    }
    return (max_yaw - min_yaw) * SERVO_RAW_PER_10_DEGREES / 10 >
               2 * SERVO_FEEDBACK_TOLERANCE_RAW ||
           (max_pitch - min_pitch) * SERVO_RAW_PER_10_DEGREES / 10 >
               2 * SERVO_FEEDBACK_TOLERANCE_RAW;
}

static_assert(stationary_feedback_cannot_pass(WAKE_FRAMES));
static_assert(stationary_feedback_cannot_pass(LOOK_USER_FRAMES));
static_assert(stationary_feedback_cannot_pass(NOD_SMALL_FRAMES));
static_assert(stationary_feedback_cannot_pass(THINK_FRAMES));
static_assert(stationary_feedback_cannot_pass(DROWSY_FRAMES));

const char *TAG = "body_hardware";
i2c_master_dev_handle_t s_py32;
i2c_master_dev_handle_t s_ina226;
i2c_master_dev_handle_t s_ltr553;
i2c_master_dev_handle_t s_si12t;
SemaphoreHandle_t s_mutex;
QueueHandle_t s_motion_queue;
QueueHandle_t s_motion_result_queue;
TaskHandle_t s_task;
bool s_initialized;
bool s_servo_uart_ready;
bool s_servo_powered;
bool s_body_motion_supported;
bool s_body_touch_supported;
bool s_proximity_supported;
bool s_ambient_light_supported;
bool s_servo_feedback_supported;
bool s_calibrated;
bool s_present;
bool s_top_touch_pressed;
bool s_top_touch_used_for_stop;
bool s_top_touch_idle_at_press;
bool s_top_touch_affection_pending;
uint8_t s_top_touch_confirm_count;
bool s_workday_toggle_pending;
int64_t s_top_touch_started_us;
uint32_t s_stop_generation;
device_ambient_light_t s_ambient_light = DEVICE_AMBIENT_LIGHT_UNAVAILABLE;
device_ambient_light_t s_ambient_light_candidate = DEVICE_AMBIENT_LIGHT_UNAVAILABLE;
int s_yaw_center_raw;
int s_pitch_center_raw;
uint8_t s_present_confirm_count;
uint8_t s_absent_confirm_count;
uint8_t s_ltr_failure_count;
uint8_t s_ambient_confirm_count;
uint8_t s_proximity_band = 0xFF;
int64_t s_last_proximity_band_log_us;

bool take_mutex(TickType_t timeout)
{
    return s_mutex != nullptr && xSemaphoreTake(s_mutex, timeout) == pdTRUE;
}

esp_err_t add_i2c_device(i2c_master_bus_handle_t bus, uint8_t address,
                         i2c_master_dev_handle_t *device)
{
    if (bus == nullptr || device == nullptr) return ESP_ERR_INVALID_ARG;
    i2c_device_config_t config = {};
    config.dev_addr_length = I2C_ADDR_BIT_LEN_7;
    config.device_address = address;
    config.scl_speed_hz = 100000;
    return i2c_master_bus_add_device(bus, &config, device);
}

esp_err_t i2c_write_register(i2c_master_dev_handle_t device, uint8_t reg, uint8_t value)
{
    uint8_t payload[] = {reg, value};
    return device == nullptr ? ESP_ERR_INVALID_STATE
                             : i2c_master_transmit(device, payload, sizeof(payload), 100);
}

esp_err_t i2c_read_registers(i2c_master_dev_handle_t device, uint8_t reg,
                             uint8_t *output, size_t length)
{
    return device == nullptr || output == nullptr || length == 0
        ? ESP_ERR_INVALID_ARG
        : i2c_master_transmit_receive(device, &reg, 1, output, length, 100);
}

esp_err_t i2c_update_bits(i2c_master_dev_handle_t device, uint8_t reg,
                          uint8_t mask, bool enabled)
{
    uint8_t value = 0;
    esp_err_t err = i2c_read_registers(device, reg, &value, 1);
    if (err != ESP_OK) return err;
    value = enabled ? static_cast<uint8_t>(value | mask)
                    : static_cast<uint8_t>(value & ~mask);
    return i2c_write_register(device, reg, value);
}

esp_err_t set_servo_power(bool enabled)
{
    if (s_py32 == nullptr) return ESP_ERR_INVALID_STATE;
    // Reassert the complete VM_EN pin configuration before every power change.
    // The expander can reset independently while the CoreS3 keeps running.
    esp_err_t err = i2c_update_bits(s_py32, PY32_GPIO_MODE_LOW_REGISTER,
                                    PY32_SERVO_POWER_BIT, true);
    if (err == ESP_OK) {
        err = i2c_update_bits(s_py32, PY32_GPIO_PULL_DOWN_LOW_REGISTER,
                              PY32_SERVO_POWER_BIT, false);
    }
    if (err == ESP_OK) {
        err = i2c_update_bits(s_py32, PY32_GPIO_PULL_UP_LOW_REGISTER,
                              PY32_SERVO_POWER_BIT, true);
    }
    if (err == ESP_OK) {
        err = i2c_update_bits(s_py32, PY32_GPIO_OUTPUT_LOW_REGISTER,
                              PY32_SERVO_POWER_BIT, enabled);
    }
    uint8_t mode = 0;
    uint8_t output = 0;
    if (err == ESP_OK) {
        err = i2c_read_registers(s_py32, PY32_GPIO_MODE_LOW_REGISTER, &mode, 1);
    }
    if (err == ESP_OK) {
        err = i2c_read_registers(s_py32, PY32_GPIO_OUTPUT_LOW_REGISTER, &output, 1);
    }
    if (err == ESP_OK && ((mode & PY32_SERVO_POWER_BIT) == 0 ||
                          ((output & PY32_SERVO_POWER_BIT) != 0) != enabled)) {
        err = ESP_ERR_INVALID_RESPONSE;
    }
    if (err == ESP_OK) s_servo_powered = enabled;
    return err;
}

const char *battery_source_state()
{
    if (s_ina226 == nullptr) return "unavailable";
    uint8_t data[2] = {};
    if (i2c_read_registers(s_ina226, INA226_BUS_VOLTAGE_REGISTER,
                           data, sizeof(data)) != ESP_OK) {
        return "unavailable";
    }
    uint16_t raw = static_cast<uint16_t>((data[0] << 8) | data[1]);
    if (raw < BATTERY_SOURCE_ABSENT_RAW) return "absent";
    if (raw < BATTERY_SOURCE_LOW_RAW) return "low";
    return "present";
}

uint8_t servo_checksum(const uint8_t *packet, size_t start, size_t end)
{
    uint8_t sum = 0;
    for (size_t index = start; index < end; index++) sum += packet[index];
    return static_cast<uint8_t>(~sum);
}

bool servo_receive(uint8_t id, uint8_t *parameters, size_t parameter_count)
{
    // A torque/position WRITE may return a late zero-parameter ACK after the
    // next READ has flushed its input. Consume only a valid ACK for the same
    // servo, then continue waiting for the requested feedback packet.
    for (int response_index = 0; response_index < 3; response_index++) {
        uint8_t previous = 0;
        bool header_found = false;
        TickType_t read_timeout = pdMS_TO_TICKS(SERVO_RESPONSE_POLL_MS);
        if (read_timeout == 0) read_timeout = 1;
        int64_t deadline = esp_timer_get_time() + SERVO_RESPONSE_HEADER_TIMEOUT_US;
        while (esp_timer_get_time() < deadline) {
            uint8_t value = 0;
            if (uart_read_bytes(SERVO_UART, &value, 1, read_timeout) != 1) continue;
            if (previous == 0xFF && value == 0xFF) {
                header_found = true;
                break;
            }
            previous = value;
        }
        if (!header_found) {
            ESP_LOGW(TAG, "Servo response rejected: stage=header id=%u", id);
            return false;
        }
        uint8_t metadata[3] = {};
        if (uart_read_bytes(SERVO_UART, metadata, sizeof(metadata), pdMS_TO_TICKS(30)) !=
            sizeof(metadata)) {
            ESP_LOGW(TAG, "Servo response rejected: stage=metadata id=%u", id);
            return false;
        }
        if (metadata[0] != id) {
            ESP_LOGW(TAG, "Servo response rejected: stage=id id=%u", id);
            return false;
        }
        if (metadata[2] != 0) {
            ESP_LOGW(TAG, "Servo response rejected: stage=status id=%u", id);
            return false;
        }
        if (parameter_count > 0 && metadata[1] == 2) {
            uint8_t checksum = 0;
            if (uart_read_bytes(SERVO_UART, &checksum, 1, pdMS_TO_TICKS(30)) != 1 ||
                checksum != static_cast<uint8_t>(~(id + metadata[1] + metadata[2]))) {
                ESP_LOGW(TAG, "Servo response rejected: stage=ack_checksum id=%u", id);
                return false;
            }
            ESP_LOGI(TAG, "Servo prior write ACK skipped: id=%u", id);
            continue;
        }
        if (metadata[1] != parameter_count + 2) {
            ESP_LOGW(TAG, "Servo response rejected: stage=length id=%u actual=%u expected=%u",
                     id, metadata[1], static_cast<unsigned>(parameter_count + 2));
            return false;
        }
        std::array<uint8_t, 24> tail = {};
        size_t tail_size = parameter_count + 1;
        if (tail_size > tail.size() ||
            uart_read_bytes(SERVO_UART, tail.data(), tail_size, pdMS_TO_TICKS(30)) !=
                static_cast<int>(tail_size)) {
            ESP_LOGW(TAG, "Servo response rejected: stage=tail id=%u", id);
            return false;
        }
        uint8_t sum = static_cast<uint8_t>(id + metadata[1] + metadata[2]);
        for (size_t index = 0; index < parameter_count; index++) sum += tail[index];
        if (static_cast<uint8_t>(~sum) != tail[parameter_count]) {
            ESP_LOGW(TAG, "Servo response rejected: stage=checksum id=%u", id);
            return false;
        }
        if (parameters != nullptr && parameter_count > 0) {
            memcpy(parameters, tail.data(), parameter_count);
        }
        return true;
    }
    ESP_LOGW(TAG, "Servo response rejected: stage=prior_ack_limit id=%u", id);
    return false;
}

bool servo_send(uint8_t id, uint8_t instruction, uint8_t reg,
                const uint8_t *data, size_t data_size, bool expect_ack)
{
    std::array<uint8_t, 32> packet = {};
    size_t parameter_count = data_size + 1;
    size_t packet_size = 7 + data_size;
    if (!s_servo_uart_ready || packet_size > packet.size()) return false;
    packet[0] = 0xFF;
    packet[1] = 0xFF;
    packet[2] = id;
    packet[3] = static_cast<uint8_t>(parameter_count + 2);
    packet[4] = instruction;
    packet[5] = reg;
    if (data_size > 0 && data != nullptr) memcpy(packet.data() + 6, data, data_size);
    packet[packet_size - 1] = servo_checksum(packet.data(), 2, packet_size - 1);
    uart_flush_input(SERVO_UART);
    if (uart_write_bytes(SERVO_UART, packet.data(), packet_size) != static_cast<int>(packet_size) ||
        uart_wait_tx_done(SERVO_UART, pdMS_TO_TICKS(20)) != ESP_OK) {
        return false;
    }
    return !expect_ack || servo_receive(id, nullptr, 0);
}

bool servo_ping(uint8_t id)
{
    uint8_t packet[] = {0xFF, 0xFF, id, 0x02, 0x01, 0x00};
    if (!s_servo_uart_ready) return false;
    packet[5] = servo_checksum(packet, 2, 5);
    uart_flush_input(SERVO_UART);
    if (uart_write_bytes(SERVO_UART, packet, sizeof(packet)) != static_cast<int>(sizeof(packet)) ||
        uart_wait_tx_done(SERVO_UART, pdMS_TO_TICKS(20)) != ESP_OK) {
        return false;
    }
    return servo_receive(id, nullptr, 0);
}

bool servo_write_register(uint8_t id, uint8_t reg, const uint8_t *data, size_t length)
{
    // SCS0009 status-return configuration can omit write acknowledgements. Safety-critical
    // writes are verified with a subsequent register/position read instead of trusting an ACK.
    return servo_send(id, 0x03, reg, data, length, false);
}

bool servo_read_register(uint8_t id, uint8_t reg, uint8_t *data, size_t length)
{
    uint8_t read_length = static_cast<uint8_t>(length);
    if (!servo_send(id, 0x02, reg, &read_length, 1, false)) return false;
    return servo_receive(id, data, length);
}

bool servo_set_torque(uint8_t id, bool enabled)
{
    uint8_t value = enabled ? 1 : 0;
    return servo_write_register(id, SERVO_TORQUE_REGISTER, &value, 1);
}

bool servo_read_torque(uint8_t id, bool *enabled)
{
    uint8_t data = 0;
    if (enabled == nullptr ||
        !servo_read_register(id, SERVO_TORQUE_REGISTER, &data, sizeof(data)) || data > 1) {
        return false;
    }
    *enabled = data == 1;
    return true;
}

bool servo_read_position(uint8_t id, int *position)
{
    uint8_t data[2] = {};
    if (position == nullptr ||
        !servo_read_register(id, SERVO_PRESENT_POSITION_REGISTER, data, sizeof(data))) {
        return false;
    }
    int raw = (static_cast<int>(data[0]) << 8) | data[1];
    if (raw < SERVO_RAW_MIN || raw > SERVO_RAW_MAX) return false;
    *position = raw;
    return true;
}

bool servo_read_goal(uint8_t id, int *position)
{
    uint8_t data[2] = {};
    if (position == nullptr ||
        !servo_read_register(id, SERVO_GOAL_POSITION_REGISTER, data, sizeof(data))) {
        return false;
    }
    int raw = (static_cast<int>(data[0]) << 8) | data[1];
    if (raw < SERVO_RAW_MIN || raw > SERVO_RAW_MAX) return false;
    *position = raw;
    return true;
}

bool servo_read_moving(uint8_t id, bool *moving)
{
    uint8_t value = 0;
    if (moving == nullptr ||
        !servo_read_register(id, SERVO_MOVING_REGISTER, &value, sizeof(value)) ||
        value > 1) {
        return false;
    }
    *moving = value == 1;
    return true;
}

bool read_servo_centers_locked(int *yaw, int *pitch)
{
    if (yaw == nullptr || pitch == nullptr) return false;
    for (int attempt = 1; attempt <= SERVO_FEEDBACK_ATTEMPTS; attempt++) {
        const char *failed_stage = nullptr;
        if (!servo_set_torque(YAW_SERVO_ID, false)) failed_stage = "yaw_torque_write";
        else if (!servo_set_torque(PITCH_SERVO_ID, false)) failed_stage = "pitch_torque_write";
        else {
            // StackChan's FTServo integration does not rely on a torque-register
            // readback. Validate the bus through passive position feedback after
            // both torque-off writes; calibration still sends no target position.
            vTaskDelay(pdMS_TO_TICKS(20));
            if (!servo_read_position(YAW_SERVO_ID, yaw)) failed_stage = "yaw_position";
        }
        if (failed_stage == nullptr && !servo_read_position(PITCH_SERVO_ID, pitch)) {
            failed_stage = "pitch_position";
        }
        if (failed_stage == nullptr) {
            if (attempt > 1) {
                ESP_LOGI(TAG, "Servo feedback recovered after retry: attempt=%d", attempt);
            }
            return true;
        }
        ESP_LOGW(TAG, "Servo feedback unavailable: stage=%s attempt=%d/%d",
                 failed_stage, attempt, SERVO_FEEDBACK_ATTEMPTS);
        if (attempt < SERVO_FEEDBACK_ATTEMPTS) {
            vTaskDelay(pdMS_TO_TICKS(SERVO_FEEDBACK_RETRY_DELAY_MS));
        }
    }
    bool ping_detected = false;
    for (uint8_t id = 1; id <= 8; id++) {
        if (servo_ping(id)) {
            ESP_LOGI(TAG, "Servo ping response detected: id=%u", id);
            ping_detected = true;
        }
    }
    if (!ping_detected) {
        ESP_LOGW(TAG, "Servo ping probe found no response for ids=1..8");
    }
    return false;
}

bool servo_move(uint8_t id, int raw_position)
{
    if (raw_position < SERVO_RAW_MIN || raw_position > SERVO_RAW_MAX) return false;
    uint8_t data[6] = {
        static_cast<uint8_t>((raw_position >> 8) & 0xFF),
        static_cast<uint8_t>(raw_position & 0xFF),
        static_cast<uint8_t>((SERVO_GOAL_TIME_MS >> 8) & 0xFF),
        static_cast<uint8_t>(SERVO_GOAL_TIME_MS & 0xFF),
        0,
        0,
    };
    return servo_write_register(id, SERVO_GOAL_POSITION_REGISTER, data, sizeof(data));
}

int yaw_raw(int degrees)
{
    return s_yaw_center_raw + degrees * SERVO_RAW_PER_10_DEGREES / 10;
}

int pitch_raw(int degrees)
{
    return s_pitch_center_raw +
           (degrees - PITCH_CENTER_DEGREES) * SERVO_RAW_PER_10_DEGREES / 10;
}

bool frame_inside_limits(const MotionFrame &frame)
{
    if (frame.yaw_degrees < YAW_MIN_DEGREES || frame.yaw_degrees > YAW_MAX_DEGREES ||
        frame.pitch_degrees < PITCH_MIN_DEGREES || frame.pitch_degrees > PITCH_MAX_DEGREES) {
        return false;
    }
    // This unit repeatedly stopped short below its calibrated forward pose,
    // and that pose lies below the factory pitch reference. Reject such frames
    // before energizing VM; all current fixed templates stay at or above it.
    if (s_pitch_center_raw < FACTORY_PITCH_ZERO_RAW &&
        frame.pitch_degrees < PITCH_CENTER_DEGREES) return false;
    int yaw = yaw_raw(frame.yaw_degrees);
    int pitch = pitch_raw(frame.pitch_degrees);
    return yaw >= SERVO_RAW_MIN && yaw <= SERVO_RAW_MAX &&
           pitch >= SERVO_RAW_MIN && pitch <= SERVO_RAW_MAX;
}

const MotionDefinition *motion_definition(safety_motion_template_t motion)
{
    static constexpr MotionDefinition definitions[] = {
        {WAKE_FRAMES, std::size(WAKE_FRAMES)},
        {LOOK_USER_FRAMES, std::size(LOOK_USER_FRAMES)},
        {NOD_SMALL_FRAMES, std::size(NOD_SMALL_FRAMES)},
        {THINK_FRAMES, std::size(THINK_FRAMES)},
        {DROWSY_FRAMES, std::size(DROWSY_FRAMES)},
    };
    return motion >= 0 && motion < SAFETY_MOTION_TEMPLATE_COUNT ? &definitions[motion] : nullptr;
}

void request_stop_callback(void *context)
{
    (void)context;
    (void)__atomic_add_fetch(&s_stop_generation, 1, __ATOMIC_SEQ_CST);
}

bool motion_stopped(uint32_t stop_generation)
{
    return __atomic_load_n(&s_stop_generation, __ATOMIC_SEQ_CST) != stop_generation;
}

void classify_stop(const MotionRequest &request, body_motion_result_t *result)
{
    result->status = BODY_MOTION_STOPPED;
    safety_diagnostics_t safety = {};
    safety_state_get_diagnostics(&safety);
    result->failure = safety.failure_count > request.failure_count
                          ? safety.last_failure : SAFETY_FAILURE_NONE;
    if (result->failure != SAFETY_FAILURE_NONE &&
        result->failure != SAFETY_FAILURE_TOUCH_STOP &&
        result->failure != SAFETY_FAILURE_VOICE_STOP) {
        result->status = BODY_MOTION_FAILED;
    }
}

bool disable_servo_output_locked()
{
    // VM may have gone high even when the preceding readback failed and the
    // cached flag stayed false. Always attempt to cut the electrical gate.
    bool yaw_off = !s_servo_uart_ready || servo_set_torque(YAW_SERVO_ID, false);
    bool pitch_off = !s_servo_uart_ready || servo_set_torque(PITCH_SERVO_ID, false);
    vTaskDelay(pdMS_TO_TICKS(20));
    esp_err_t power_off = set_servo_power(false);
    if (!yaw_off || !pitch_off || power_off != ESP_OK) {
        ESP_LOGW(TAG, "Servo shutdown could not be fully verified: yaw=%s pitch=%s power=%s",
                 yaw_off ? "off" : "unknown", pitch_off ? "off" : "unknown",
                 esp_err_to_name(power_off));
    }
    return power_off == ESP_OK;
}

bool load_calibration_locked()
{
    nvs_handle_t handle = 0;
    if (nvs_open(BODY_NVS_NAMESPACE, NVS_READONLY, &handle) != ESP_OK) return false;
    int32_t yaw = 0;
    int32_t pitch = 0;
    uint8_t calibrated = 0;
    esp_err_t err = nvs_get_i32(handle, BODY_NVS_YAW_CENTER, &yaw);
    if (err == ESP_OK) err = nvs_get_i32(handle, BODY_NVS_PITCH_CENTER, &pitch);
    if (err == ESP_OK) err = nvs_get_u8(handle, BODY_NVS_CALIBRATED, &calibrated);
    nvs_close(handle);
    if (err != ESP_OK || calibrated != 1 || yaw < SERVO_RAW_MIN || yaw > SERVO_RAW_MAX ||
        pitch < SERVO_RAW_MIN || pitch > SERVO_RAW_MAX) {
        return false;
    }
    s_yaw_center_raw = yaw;
    s_pitch_center_raw = pitch;
    s_calibrated = true;
    return true;
}

const char *pitch_center_factory_category()
{
    const int delta = s_pitch_center_raw - FACTORY_PITCH_ZERO_RAW;
    if (delta < 0) return "below_factory_zero";
    if (delta < 16) return "factory_zero_to_5deg";
    if (delta < 48) return "5_to_15deg";
    if (delta < 112) return "15_to_35deg";
    return "over_35deg";
}

esp_err_t save_calibration_locked(int yaw, int pitch)
{
    nvs_handle_t handle = 0;
    esp_err_t err = nvs_open(BODY_NVS_NAMESPACE, NVS_READWRITE, &handle);
    if (err == ESP_OK) err = nvs_set_i32(handle, BODY_NVS_YAW_CENTER, yaw);
    if (err == ESP_OK) err = nvs_set_i32(handle, BODY_NVS_PITCH_CENTER, pitch);
    if (err == ESP_OK) err = nvs_set_u8(handle, BODY_NVS_CALIBRATED, 1);
    if (err == ESP_OK) err = nvs_commit(handle);
    if (handle != 0) nvs_close(handle);
    return err;
}

esp_err_t configure_py32(i2c_master_bus_handle_t bus)
{
    esp_err_t err = add_i2c_device(bus, PY32_ADDRESS, &s_py32);
    uint8_t version = 0;
    if (err == ESP_OK) err = i2c_read_registers(s_py32, PY32_VERSION_REGISTER, &version, 1);
    if (err != ESP_OK || version == 0 || version == 0xFF) return ESP_ERR_NOT_FOUND;
    err = i2c_update_bits(s_py32, PY32_GPIO_OUTPUT_LOW_REGISTER,
                          PY32_SERVO_POWER_BIT, false);
    if (err == ESP_OK) {
        err = i2c_update_bits(s_py32, PY32_GPIO_MODE_LOW_REGISTER,
                              PY32_SERVO_POWER_BIT, true);
    }
    if (err == ESP_OK) {
        err = i2c_update_bits(s_py32, PY32_GPIO_PULL_DOWN_LOW_REGISTER,
                              PY32_SERVO_POWER_BIT, false);
    }
    if (err == ESP_OK) {
        err = i2c_update_bits(s_py32, PY32_GPIO_PULL_UP_LOW_REGISTER,
                              PY32_SERVO_POWER_BIT, true);
    }
    s_servo_powered = false;
    return err;
}

esp_err_t configure_ina226(i2c_master_bus_handle_t bus)
{
    esp_err_t err = add_i2c_device(bus, INA226_ADDRESS, &s_ina226);
    uint8_t identity[2] = {};
    if (err == ESP_OK) {
        err = i2c_read_registers(s_ina226, INA226_MANUFACTURER_ID_REGISTER,
                                 identity, sizeof(identity));
    }
    uint16_t manufacturer = static_cast<uint16_t>((identity[0] << 8) | identity[1]);
    if (err != ESP_OK || manufacturer != INA226_MANUFACTURER_ID) {
        s_ina226 = nullptr;
        return ESP_ERR_NOT_FOUND;
    }
    if (err == ESP_OK) {
        err = i2c_read_registers(s_ina226, INA226_DIE_ID_REGISTER,
                                 identity, sizeof(identity));
    }
    uint16_t die = static_cast<uint16_t>((identity[0] << 8) | identity[1]);
    if (err != ESP_OK || (die & INA226_DIE_ID_MASK) != INA226_DIE_ID) {
        s_ina226 = nullptr;
        return ESP_ERR_NOT_FOUND;
    }
    return ESP_OK;
}

esp_err_t configure_ltr553(i2c_master_bus_handle_t bus)
{
    // CoreS3 places the LTR553 next to the camera on the same ribbon cable.
    // The BSP camera power feature enables that shared rail without starting
    // the camera or allocating its frame buffers.
    esp_err_t err = bsp_feature_enable(BSP_FEATURE_CAMERA, true);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "LTR553 unavailable: stage=power error=%s", esp_err_to_name(err));
        return err;
    }
    vTaskDelay(pdMS_TO_TICKS(100));
    err = i2c_master_probe(bus, LTR553_ADDRESS, 100);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "LTR553 unavailable: stage=probe error=%s", esp_err_to_name(err));
        (void)bsp_feature_enable(BSP_FEATURE_CAMERA, false);
        return err;
    }
    err = add_i2c_device(bus, LTR553_ADDRESS, &s_ltr553);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "LTR553 unavailable: stage=bus error=%s", esp_err_to_name(err));
        return err;
    }
    uint8_t identity[2] = {};
    err = i2c_read_registers(s_ltr553, LTR553_PART_ID, identity, 2);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "LTR553 unavailable: stage=identity_read error=%s", esp_err_to_name(err));
        return err;
    }
    if ((identity[0] & 0xF0) != 0x90 || identity[1] != 0x05) {
        ESP_LOGW(TAG, "LTR553 unavailable: stage=identity_mismatch");
        return ESP_ERR_NOT_FOUND;
    }
    // Use the StackChan example's PS profile. The former reset-default
    // 60 kHz / 100 ms profile never left the lowest observed category here.
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_LED,
                                                 LTR553_PS_LED_STACKCHAN);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_PULSES,
                                                 LTR553_PS_PULSES_STACKCHAN);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_RATE,
                                                 LTR553_PS_RATE_STACKCHAN);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_ALS_RATE, 0x03);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_ALS_CONTROL, 0x01);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_CONTROL, 0x02);
    uint8_t ps_mode = 0;
    uint8_t ps_led = 0;
    uint8_t ps_pulses = 0;
    uint8_t ps_rate = 0;
    if (err == ESP_OK) err = i2c_read_registers(s_ltr553, LTR553_PS_CONTROL, &ps_mode, 1);
    if (err == ESP_OK) err = i2c_read_registers(s_ltr553, LTR553_PS_LED, &ps_led, 1);
    if (err == ESP_OK) err = i2c_read_registers(s_ltr553, LTR553_PS_PULSES, &ps_pulses, 1);
    if (err == ESP_OK) err = i2c_read_registers(s_ltr553, LTR553_PS_RATE, &ps_rate, 1);
    if (err == ESP_OK && ((ps_mode & 0x03) != 0x02 ||
                          ps_led != LTR553_PS_LED_STACKCHAN ||
                          (ps_pulses & 0x0F) != LTR553_PS_PULSES_STACKCHAN ||
                          (ps_rate & 0x0F) != LTR553_PS_RATE_STACKCHAN)) {
        err = ESP_ERR_INVALID_STATE;
    }
    if (err == ESP_OK) ESP_LOGI(TAG, "LTR553 StackChan proximity profile: readback=matched");
    if (err != ESP_OK)
        ESP_LOGW(TAG, "LTR553 unavailable: stage=configure error=%s", esp_err_to_name(err));
    return err;
}

esp_err_t configure_si12t(i2c_master_bus_handle_t bus)
{
    esp_err_t err = add_i2c_device(bus, SI12T_ADDRESS, &s_si12t);
    for (uint8_t reg = SI12T_SENSITIVITY_FIRST;
         err == ESP_OK && reg <= SI12T_SENSITIVITY_LAST; reg++) {
        err = i2c_write_register(s_si12t, reg, 0x33);
    }
    constexpr uint8_t clear_registers[] = {
        SI12T_REFERENCE_RESET_1, SI12T_REFERENCE_RESET_2,
        SI12T_CHANNEL_HOLD_1, SI12T_CHANNEL_HOLD_2,
        SI12T_CALIBRATION_HOLD_1, SI12T_CALIBRATION_HOLD_2,
    };
    for (uint8_t reg : clear_registers) {
        if (err == ESP_OK) err = i2c_write_register(s_si12t, reg, 0x00);
    }
    if (err == ESP_OK) err = i2c_write_register(s_si12t, SI12T_CONTROL_2, 0x0F);
    if (err == ESP_OK) err = i2c_write_register(s_si12t, SI12T_CONTROL_2, 0x07);
    if (err == ESP_OK) err = i2c_write_register(s_si12t, SI12T_CONTROL_1, 0x22);
    return err;
}

esp_err_t configure_servo_uart()
{
    uart_config_t config = {};
    config.baud_rate = SERVO_BAUD;
    config.data_bits = UART_DATA_8_BITS;
    config.parity = UART_PARITY_DISABLE;
    config.stop_bits = UART_STOP_BITS_1;
    config.flow_ctrl = UART_HW_FLOWCTRL_DISABLE;
    config.source_clk = UART_SCLK_DEFAULT;
    esp_err_t err = uart_driver_install(SERVO_UART, 1024, 1024, 0, nullptr, 0);
    if (err == ESP_OK) err = uart_param_config(SERVO_UART, &config);
    if (err == ESP_OK) {
        err = uart_set_pin(SERVO_UART, SERVO_TX_PIN, SERVO_RX_PIN,
                           UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
    }
    s_servo_uart_ready = err == ESP_OK;
    return err;
}

void update_ltr553_locked()
{
    if (!s_proximity_supported && !s_ambient_light_supported) return;
    uint8_t als_data[4] = {};
    uint8_t ps_data[2] = {};
    uint8_t status = 0;
    // The M5Stack LTR5XX reference reads each pair from its own register
    // start; do not span ALS status and PS data in one transaction.
    if (i2c_read_registers(s_ltr553, LTR553_DATA_START, als_data, sizeof(als_data)) != ESP_OK ||
        i2c_read_registers(s_ltr553, LTR553_STATUS, &status, 1) != ESP_OK ||
        i2c_read_registers(s_ltr553, LTR553_PS_DATA_START, ps_data,
                           sizeof(ps_data)) != ESP_OK) {
        if (++s_ltr_failure_count >= 5) {
            s_proximity_supported = false;
            s_ambient_light_supported = false;
            s_present = false;
            s_ambient_light = DEVICE_AMBIENT_LIGHT_UNAVAILABLE;
            s_ambient_light_candidate = DEVICE_AMBIENT_LIGHT_UNAVAILABLE;
            s_ambient_confirm_count = 0;
        }
        return;
    }
    s_ltr_failure_count = 0;
    uint16_t channel_1 = static_cast<uint16_t>(als_data[0] | (als_data[1] << 8));
    uint16_t channel_0 = static_cast<uint16_t>(als_data[2] | (als_data[3] << 8));
    uint32_t ambient = static_cast<uint32_t>(channel_0) + channel_1;
    device_ambient_light_t observed = DEVICE_AMBIENT_LIGHT_BRIGHT;
    if (ambient < 100) observed = DEVICE_AMBIENT_LIGHT_DARK;
    else if (ambient < 1000) observed = DEVICE_AMBIENT_LIGHT_DIM;
    else if (ambient < 20000) observed = DEVICE_AMBIENT_LIGHT_NORMAL;
    if (observed == s_ambient_light) {
        s_ambient_light_candidate = observed;
        s_ambient_confirm_count = 0;
    } else if (observed != s_ambient_light_candidate) {
        s_ambient_light_candidate = observed;
        s_ambient_confirm_count = 1;
    } else if (++s_ambient_confirm_count >= AMBIENT_CONFIRM_SAMPLES) {
        s_ambient_light = observed;
        s_ambient_confirm_count = 0;
        int brightness = AMBIENT_BRIGHTNESS_NORMAL;
        if (s_ambient_light == DEVICE_AMBIENT_LIGHT_DARK) brightness = AMBIENT_BRIGHTNESS_DARK;
        else if (s_ambient_light == DEVICE_AMBIENT_LIGHT_DIM) brightness = AMBIENT_BRIGHTNESS_DIM;
        else if (s_ambient_light == DEVICE_AMBIENT_LIGHT_BRIGHT) brightness = AMBIENT_BRIGHTNESS_BRIGHT;
        (void)companion_hardware_set_ambient_brightness(brightness);
    }

    uint16_t proximity = static_cast<uint16_t>(ps_data[0] | ((ps_data[1] & 0x07) << 8));
    // Status bit 0 marks a new PS conversion. Do not count repeated reads of
    // the same conversion as separate proximity confirmations.
    if ((status & 0x01) == 0) return;
    // Keep diagnostic categories coarse and bound serial log volume.
    uint8_t band = proximity < 16 ? 0 : proximity < 32 ? 1 :
                   proximity < 100 ? 2 : proximity < 250 ? 3 :
                   proximity < 500 ? 4 : 5;
    if (band != s_proximity_band) {
        int64_t now_us = esp_timer_get_time();
        if (s_proximity_band == 0xFF ||
            now_us - s_last_proximity_band_log_us >= PROXIMITY_BAND_LOG_INTERVAL_US) {
            constexpr const char *bands[] = {"under_16", "16_to_31", "32_to_99",
                                              "100_to_249", "250_to_499", "500_or_more"};
            ESP_LOGI(TAG, "LTR553 proximity band: %s data=new", bands[band]);
            s_last_proximity_band_log_us = now_us;
        }
        s_proximity_band = band;
    }
    if (!s_present && proximity >= PROXIMITY_PRESENT_THRESHOLD) {
        s_absent_confirm_count = 0;
        if (++s_present_confirm_count >= PROXIMITY_CONFIRM_SAMPLES) {
            s_present = true;
            s_present_confirm_count = 0;
            ESP_LOGI(TAG, "LTR553 presence changed: present=yes");
        }
    } else if (s_present && proximity <= PROXIMITY_ABSENT_THRESHOLD) {
        s_present_confirm_count = 0;
        if (++s_absent_confirm_count >= PROXIMITY_CONFIRM_SAMPLES) {
            s_present = false;
            s_absent_confirm_count = 0;
            ESP_LOGI(TAG, "LTR553 presence changed: present=no");
        }
    } else {
        s_present_confirm_count = 0;
        s_absent_confirm_count = 0;
    }
}

void update_top_touch_locked()
{
    if (!s_body_touch_supported) return;
    uint8_t value = 0;
    if (i2c_read_registers(s_si12t, SI12T_OUTPUT, &value, 1) != ESP_OK) {
        s_body_touch_supported = false;
        s_top_touch_confirm_count = 0;
        return;
    }
    bool sample_pressed = (value & 0x3F) != 0;
    if (sample_pressed) {
        if (s_top_touch_confirm_count < TOP_TOUCH_CONFIRM_SAMPLES)
            s_top_touch_confirm_count++;
    } else {
        s_top_touch_confirm_count = 0;
    }
    bool pressed = s_top_touch_confirm_count >= TOP_TOUCH_CONFIRM_SAMPLES;
    int64_t now_us = esp_timer_get_time();
    if (pressed && !s_top_touch_pressed) {
        s_top_touch_started_us = now_us;
        s_top_touch_used_for_stop = false;
        s_top_touch_idle_at_press = !voice_control_motion_blocked();
        safety_diagnostics_t safety = {};
        safety_state_get_diagnostics(&safety);
        if (safety.motion_runtime == SAFETY_MOTION_RUNNING) {
            s_top_touch_used_for_stop = true;
            ESP_LOGW(TAG, "Servo top touch confirmed: samples=%u",
                     static_cast<unsigned>(s_top_touch_confirm_count));
            safety_state_stop_motion_with_reason(SAFETY_FAILURE_TOUCH_STOP);
        }
    } else if (!pressed && s_top_touch_pressed) {
        if (s_top_touch_started_us > 0) {
            body_touch_action_t action = body_touch_release_action(
                    (uint32_t)((now_us - s_top_touch_started_us) / 1000LL), s_top_touch_used_for_stop,
                    s_top_touch_idle_at_press, !voice_control_motion_blocked());
            if (action == BODY_TOUCH_WORKDAY_TOGGLE) s_workday_toggle_pending = true;
            else if (action == BODY_TOUCH_AFFECTION) s_top_touch_affection_pending = true;
        }
        s_top_touch_started_us = 0;
    }
    s_top_touch_pressed = pressed;
}

bool wait_frame_locked(uint16_t duration_ms, uint32_t stop_generation)
{
    int64_t deadline = esp_timer_get_time() + static_cast<int64_t>(duration_ms) * 1000LL;
    while (esp_timer_get_time() < deadline) {
        update_top_touch_locked();
        safety_state_tick(esp_timer_get_time());
        if (motion_stopped(stop_generation)) return false;
        const int64_t remaining_us = deadline - esp_timer_get_time();
        if (remaining_us <= 0) break;
        const uint32_t sleep_ms = static_cast<uint32_t>(
            std::min<int64_t>(20, (remaining_us + 999) / 1000));
        vTaskDelay(std::max<TickType_t>(1, pdMS_TO_TICKS(sleep_ms)));
    }
    return !motion_stopped(stop_generation);
}

const char *write_paced_frame_locked(const MotionFrame &frame, int start_yaw,
                                     int start_pitch, uint32_t stop_generation)
{
    const int steps = std::max(1, (frame.duration_ms + SERVO_FRAME_STEP_MS - 1) /
                                    SERVO_FRAME_STEP_MS);
    const int yaw_target = yaw_raw(frame.yaw_degrees);
    const int pitch_target = pitch_raw(frame.pitch_degrees);
    for (int step = 1; step <= steps; step++) {
        if (motion_stopped(stop_generation)) return "stopped";
        const int yaw_goal = start_yaw + (yaw_target - start_yaw) * step / steps;
        const int pitch_goal = start_pitch + (pitch_target - start_pitch) * step / steps;
        if (!servo_move(YAW_SERVO_ID, yaw_goal)) return "yaw_goal_write";
        if (!servo_move(PITCH_SERVO_ID, pitch_goal)) return "pitch_goal_write";
        const int elapsed_before = frame.duration_ms * (step - 1) / steps;
        const int elapsed_after = frame.duration_ms * step / steps;
        if (!wait_frame_locked(elapsed_after - elapsed_before, stop_generation))
            return "stopped";
    }
    return nullptr;
}

const char *verify_frame_locked(const MotionFrame &frame, int *observed_yaw,
                                int *observed_pitch)
{
    if (observed_yaw == nullptr || observed_pitch == nullptr) return "missing_output";
    const int yaw_target = yaw_raw(frame.yaw_degrees);
    const int pitch_target = pitch_raw(frame.pitch_degrees);
    int yaw_goal = 0;
    int pitch_goal = 0;
    int yaw = 0;
    int pitch = 0;
    if (!servo_read_goal(YAW_SERVO_ID, &yaw_goal)) return "yaw_goal_read";
    if (!servo_read_goal(PITCH_SERVO_ID, &pitch_goal)) return "pitch_goal_read";
    if (yaw_goal != yaw_target) return "yaw_goal_mismatch";
    if (pitch_goal != pitch_target) return "pitch_goal_mismatch";
    if (!servo_read_position(YAW_SERVO_ID, &yaw)) return "yaw_position";
    if (!servo_read_position(PITCH_SERVO_ID, &pitch)) return "pitch_position";
    *observed_yaw = yaw;
    *observed_pitch = pitch;
    int yaw_error = std::abs(yaw - yaw_target);
    int pitch_error = std::abs(pitch - pitch_target);
    if (yaw_error > SERVO_FEEDBACK_TOLERANCE_RAW)
        return yaw_error <= 2 * SERVO_FEEDBACK_TOLERANCE_RAW
                   ? "yaw_target_near" : "yaw_target_far";
    if (pitch_error > SERVO_FEEDBACK_TOLERANCE_RAW)
        return pitch_error <= 2 * SERVO_FEEDBACK_TOLERANCE_RAW
                   ? "pitch_target_near" : "pitch_target_far";
    return nullptr;
}

const char *verify_frame_with_settle_locked(const MotionFrame &frame,
                                            uint32_t stop_generation,
                                            int *observed_yaw,
                                            int *observed_pitch)
{
    const char *failure = verify_frame_locked(frame, observed_yaw, observed_pitch);
    if (failure == nullptr) return nullptr;
    // A far miss remains a failure, but the movement flag distinguishes a
    // late frame from a stationary servo without exposing raw positions.
    bool far = false;
    uint8_t moving_id = 0;
    const char *near_stage = nullptr;
    const char *idle_stage = nullptr;
    const char *moving_read_stage = nullptr;
    const char *far_moving_stage = nullptr;
    const char *moving_timeout_stage = nullptr;
    if (strcmp(failure, "yaw_target_near") == 0) {
        moving_id = YAW_SERVO_ID;
        near_stage = "yaw_target_near";
        idle_stage = "yaw_target_near_idle";
        moving_read_stage = "yaw_moving_read";
        moving_timeout_stage = "yaw_target_near_moving_timeout";
    } else if (strcmp(failure, "yaw_target_far") == 0) {
        moving_id = YAW_SERVO_ID;
        far = true;
        idle_stage = "yaw_target_far_idle";
        moving_read_stage = "yaw_moving_read";
        far_moving_stage = "yaw_target_far_moving";
    } else if (strcmp(failure, "pitch_target_near") == 0) {
        moving_id = PITCH_SERVO_ID;
        near_stage = "pitch_target_near";
        idle_stage = "pitch_target_near_idle";
        moving_read_stage = "pitch_moving_read";
        moving_timeout_stage = "pitch_target_near_moving_timeout";
    } else if (strcmp(failure, "pitch_target_far") == 0) {
        moving_id = PITCH_SERVO_ID;
        far = true;
        idle_stage = "pitch_target_far_idle";
        moving_read_stage = "pitch_moving_read";
        far_moving_stage = "pitch_target_far_moving";
    } else {
        return failure;
    }
    if (far) {
        bool moving = false;
        if (!servo_read_moving(moving_id, &moving)) return moving_read_stage;
        return moving ? far_moving_stage : idle_stage;
    }
    // Only a near miss while the servo still reports motion can earn more
    // time. Read errors, wrong goals and stationary servos fail shut.
    for (int elapsed_ms = 0; elapsed_ms < SERVO_NEAR_TARGET_SETTLE_MS;
         elapsed_ms += SERVO_NEAR_TARGET_POLL_MS) {
        bool moving = false;
        if (!servo_read_moving(moving_id, &moving)) return moving_read_stage;
        if (!moving) return idle_stage;
        if (!wait_frame_locked(SERVO_NEAR_TARGET_POLL_MS, stop_generation))
            return "stopped";
        failure = verify_frame_locked(frame, observed_yaw, observed_pitch);
        if (failure == nullptr) return nullptr;
        if (strcmp(failure, near_stage) != 0) return failure;
    }
    return moving_timeout_stage;
}

bool pitch_recovery_safe_locked()
{
    uint8_t telemetry[4] = {};
    uint8_t current_bytes[2] = {};
    if (!servo_read_register(PITCH_SERVO_ID, SERVO_PRESENT_LOAD_REGISTER,
                             telemetry, sizeof(telemetry)) ||
        !servo_read_register(PITCH_SERVO_ID, SERVO_PRESENT_CURRENT_REGISTER,
                             current_bytes, sizeof(current_bytes))) {
        return false;
    }
    const int load = ((static_cast<int>(telemetry[0]) << 8) | telemetry[1]) & 0x3FF;
    const int current = ((static_cast<int>(current_bytes[0]) << 8) |
                         current_bytes[1]) & 0x7FFF;
    return telemetry[2] >= 48 && telemetry[2] <= 74 && telemetry[3] < 60 &&
           load < 650 && current < 350;
}

const char *recover_idle_pitch_locked(const MotionFrame &frame,
                                      uint32_t stop_generation,
                                      int *observed_yaw, int *observed_pitch)
{
    const int target = pitch_raw(frame.pitch_degrees);
    int previous_error = std::abs(*observed_pitch - target);
    for (int attempt = 1; attempt <= SERVO_IDLE_RECOVERY_ATTEMPTS; attempt++) {
        if (motion_stopped(stop_generation)) return "stopped";
        if (!pitch_recovery_safe_locked()) return "pitch_target_recovery_unsafe";
        if (!servo_move(PITCH_SERVO_ID, target)) return "pitch_goal_write";
        if (!wait_frame_locked(SERVO_IDLE_RECOVERY_WAIT_MS, stop_generation))
            return "stopped";
        const char *failure = verify_frame_locked(frame, observed_yaw, observed_pitch);
        if (failure == nullptr) {
            ESP_LOGI(TAG, "Servo pitch recovery complete: attempts=%d", attempt);
            return nullptr;
        }
        if (strncmp(failure, "pitch_target", 12) != 0) return failure;
        const int error = std::abs(*observed_pitch - target);
        if (error >= previous_error - 1) return "pitch_target_recovery_stalled";
        previous_error = error;
    }
    return "pitch_target_recovery_exhausted";
}

const char *start_pose_category(int raw_error)
{
    if (raw_error <= SERVO_FEEDBACK_TOLERANCE_RAW) return "centered";
    if (raw_error <= 2 * SERVO_FEEDBACK_TOLERANCE_RAW) return "offset";
    return "far";
}

const char *frame_progress_category(int raw_travel)
{
    if (raw_travel <= 1) return "none";
    if (raw_travel <= SERVO_FEEDBACK_TOLERANCE_RAW) return "small";
    return "clear";
}

const char *frame_error_category(int raw_error)
{
    if (raw_error <= SERVO_FEEDBACK_TOLERANCE_RAW) return "within_tolerance";
    if (raw_error <= 2 * SERVO_FEEDBACK_TOLERANCE_RAW) return "9_to_16";
    if (raw_error <= 4 * SERVO_FEEDBACK_TOLERANCE_RAW) return "17_to_32";
    if (raw_error <= 8 * SERVO_FEEDBACK_TOLERANCE_RAW) return "33_to_64";
    return "over_64";
}

void log_pitch_target_diagnostics(int target, int observed, int previous)
{
    uint8_t limits[4] = {};
    const char *limit_state = "read_failed";
    if (servo_read_register(PITCH_SERVO_ID, SERVO_MIN_POSITION_REGISTER,
                            limits, sizeof(limits))) {
        int minimum = (static_cast<int>(limits[0]) << 8) | limits[1];
        int maximum = (static_cast<int>(limits[2]) << 8) | limits[3];
        if (minimum >= maximum) limit_state = "invalid_or_wheel_mode";
        else if (target < minimum) limit_state = "below_minimum";
        else if (target > maximum) limit_state = "above_maximum";
        else limit_state = "inside_limits";
    }
    uint8_t telemetry[4] = {};
    const char *voltage_state = "read_failed";
    const char *load_state = "read_failed";
    const char *temperature_state = "read_failed";
    if (servo_read_register(PITCH_SERVO_ID, SERVO_PRESENT_LOAD_REGISTER,
                            telemetry, sizeof(telemetry))) {
        const int load = ((static_cast<int>(telemetry[0]) << 8) | telemetry[1]) & 0x3FF;
        load_state = load >= 650 ? "high" : load >= 300 ? "elevated" : "normal";
        const uint8_t voltage = telemetry[2];
        if (voltage < 40) voltage_state = "under_4v";
        else if (voltage < 48) voltage_state = "4_to_4_7v";
        else if (voltage <= 74) voltage_state = "4_8_to_7_4v";
        else voltage_state = "over_7_4v";
        temperature_state = telemetry[3] >= 70 ? "hot"
                            : telemetry[3] >= 60 ? "warm" : "normal";
    }
    uint8_t current_bytes[2] = {};
    const char *current_state = "read_failed";
    if (servo_read_register(PITCH_SERVO_ID, SERVO_PRESENT_CURRENT_REGISTER,
                            current_bytes, sizeof(current_bytes))) {
        int current = (static_cast<int>(current_bytes[0]) << 8) | current_bytes[1];
        current &= 0x7FFF;
        current_state = current >= 350 ? "high"
                        : current >= 200 ? "elevated" : "normal";
    }
    uint8_t dead_zone[2] = {};
    const char *dead_zone_state = "read_failed";
    if (servo_read_register(PITCH_SERVO_ID, SERVO_DEAD_ZONE_REGISTER,
                            dead_zone, sizeof(dead_zone))) {
        int error = std::abs(observed - target);
        int widest_zone = std::max(dead_zone[0], dead_zone[1]);
        dead_zone_state = widest_zone >= error ? "covers_error"
                          : widest_zone >= error / 2 ? "near_error" : "below_error";
    }
    const char *direction = "at_target";
    if (observed != target) {
        direction = (target > previous) == (observed < target)
                        ? "short_of_target" : "past_target";
    }
    ESP_LOGW(TAG, "Servo pitch diagnostic: error=%s direction=%s travel=%s limit=%s voltage=%s load=%s current=%s temp=%s dead_zone=%s",
             frame_error_category(std::abs(observed - target)), direction,
             frame_progress_category(std::abs(observed - previous)),
             limit_state, voltage_state, load_state, current_state,
             temperature_state, dead_zone_state);
}

bool probe_servo_feedback_locked()
{
    int yaw = 0;
    int pitch = 0;
    if (set_servo_power(true) != ESP_OK) return false;
    vTaskDelay(pdMS_TO_TICKS(SERVO_POWER_SETTLE_MS));
    bool available = read_servo_centers_locked(&yaw, &pitch);
    bool powered_off = disable_servo_output_locked();
    return available && powered_off;
}

body_motion_result_t execute_motion_locked(const MotionRequest &request)
{
    safety_motion_template_t motion = request.motion;
    body_motion_result_t result = {};
    memcpy(result.command_id, request.command_id, sizeof(result.command_id));
    result.motion = motion;
    result.status = BODY_MOTION_FAILED;
    result.failure = SAFETY_FAILURE_NONE;
    const MotionDefinition *definition = motion_definition(motion);
    if (definition == nullptr || !s_calibrated) {
        safety_state_fail_motion(SAFETY_FAILURE_NOT_CALIBRATED);
        result.failure = SAFETY_FAILURE_NOT_CALIBRATED;
        return result;
    }
    for (size_t index = 0; index < definition->frame_count; index++) {
        if (!frame_inside_limits(definition->frames[index])) {
            safety_state_fail_motion(SAFETY_FAILURE_SOFT_LIMIT);
            result.failure = SAFETY_FAILURE_SOFT_LIMIT;
            return result;
        }
    }
    // A stop can arrive after queueing but before this task acquires the mutex.
    // Never clear that stop here or energize VM for a cancelled command.
    if (motion_stopped(request.stop_generation) ||
        safety_state_current() != SAFETY_STATE_MOTION_ARMED) {
        classify_stop(request, &result);
        return result;
    }
    if (set_servo_power(true) != ESP_OK) {
        (void)disable_servo_output_locked();
        safety_state_fail_motion(SAFETY_FAILURE_HARDWARE_FAILURE);
        result.failure = SAFETY_FAILURE_HARDWARE_FAILURE;
        return result;
    }
    if (!wait_frame_locked(SERVO_POWER_SETTLE_MS, request.stop_generation)) {
        if (!disable_servo_output_locked())
            safety_state_fail_motion(SAFETY_FAILURE_HARDWARE_FAILURE);
        classify_stop(request, &result);
        return result;
    }
    ESP_LOGI(TAG, "Servo power source: state=%s", battery_source_state());
    bool yaw_torque_enabled = false;
    bool pitch_torque_enabled = false;
    const char *torque_failure_stage = nullptr;
    if (!servo_set_torque(YAW_SERVO_ID, true)) torque_failure_stage = "yaw_write";
    else if (!servo_read_torque(YAW_SERVO_ID, &yaw_torque_enabled)) torque_failure_stage = "yaw_read";
    else if (!yaw_torque_enabled) torque_failure_stage = "yaw_off";
    else if (!servo_set_torque(PITCH_SERVO_ID, true)) torque_failure_stage = "pitch_write";
    else if (!servo_read_torque(PITCH_SERVO_ID, &pitch_torque_enabled)) torque_failure_stage = "pitch_read";
    else if (!pitch_torque_enabled) torque_failure_stage = "pitch_off";
    if (torque_failure_stage != nullptr) {
        ESP_LOGW(TAG, "Servo motion unavailable: stage=%s", torque_failure_stage);
        safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
        result.failure = disable_servo_output_locked()
                             ? SAFETY_FAILURE_FEEDBACK_FAULT
                             : SAFETY_FAILURE_HARDWARE_FAILURE;
        if (result.failure == SAFETY_FAILURE_HARDWARE_FAILURE)
            safety_state_fail_motion(result.failure);
        return result;
    }
    int initial_yaw = 0;
    int initial_pitch = 0;
    const char *initial_failure_stage = nullptr;
    if (!servo_read_position(YAW_SERVO_ID, &initial_yaw))
        initial_failure_stage = "initial_yaw";
    else if (!servo_read_position(PITCH_SERVO_ID, &initial_pitch))
        initial_failure_stage = "initial_pitch";
    if (initial_failure_stage != nullptr) {
        ESP_LOGW(TAG, "Servo motion unavailable: stage=%s", initial_failure_stage);
        safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
        result.failure = disable_servo_output_locked()
                             ? SAFETY_FAILURE_FEEDBACK_FAULT
                             : SAFETY_FAILURE_HARDWARE_FAILURE;
        if (result.failure == SAFETY_FAILURE_HARDWARE_FAILURE)
            safety_state_fail_motion(result.failure);
        return result;
    }
    ESP_LOGI(TAG, "Servo start pose: yaw=%s pitch=%s",
             start_pose_category(std::abs(initial_yaw - s_yaw_center_raw)),
             start_pose_category(std::abs(initial_pitch - s_pitch_center_raw)));
    bool feedback_failed = false;
    bool observed_travel = false;
    int previous_yaw = initial_yaw;
    int previous_pitch = initial_pitch;
    for (size_t index = 0;
         index < definition->frame_count && !motion_stopped(request.stop_generation);
         index++) {
        const MotionFrame &frame = definition->frames[index];
        const char *frame_failure_stage = nullptr;
        int observed_yaw = 0;
        int observed_pitch = 0;
        frame_failure_stage = write_paced_frame_locked(
            frame, previous_yaw, previous_pitch, request.stop_generation);
        if (frame_failure_stage == nullptr)
            frame_failure_stage = verify_frame_with_settle_locked(
                frame, request.stop_generation, &observed_yaw, &observed_pitch);
        if (frame_failure_stage != nullptr &&
            (strcmp(frame_failure_stage, "pitch_target_far_idle") == 0 ||
             strcmp(frame_failure_stage, "pitch_target_near_idle") == 0)) {
            frame_failure_stage = recover_idle_pitch_locked(
                frame, request.stop_generation, &observed_yaw, &observed_pitch);
        }
        if (frame_failure_stage != nullptr) {
            if (!motion_stopped(request.stop_generation)) {
                const char *progress = "unavailable";
                if (strncmp(frame_failure_stage, "yaw_target", 10) == 0)
                    progress = frame_progress_category(std::abs(observed_yaw - previous_yaw));
                else if (strncmp(frame_failure_stage, "pitch_target", 12) == 0)
                    progress = frame_progress_category(std::abs(observed_pitch - previous_pitch));
                ESP_LOGW(TAG, "Servo motion unavailable: stage=%s frame=%u progress=%s",
                         frame_failure_stage, static_cast<unsigned>(index), progress);
                if (strncmp(frame_failure_stage, "pitch_target", 12) == 0)
                    log_pitch_target_diagnostics(pitch_raw(frame.pitch_degrees),
                                                 observed_pitch, previous_pitch);
                safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
                feedback_failed = true;
            }
            break;
        }
        previous_yaw = observed_yaw;
        previous_pitch = observed_pitch;
        if (std::abs(observed_yaw - initial_yaw) > SERVO_FEEDBACK_TOLERANCE_RAW ||
            std::abs(observed_pitch - initial_pitch) > SERVO_FEEDBACK_TOLERANCE_RAW)
            observed_travel = true;
    }
    if (!motion_stopped(request.stop_generation) && !feedback_failed && !observed_travel) {
        ESP_LOGW(TAG, "Servo motion unavailable: stage=no_observed_travel");
        safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
        feedback_failed = true;
    }
    bool shutdown_ok = disable_servo_output_locked();
    if (!shutdown_ok)
        safety_state_fail_motion(SAFETY_FAILURE_HARDWARE_FAILURE);
    safety_state_tick(esp_timer_get_time());
    if (!shutdown_ok) {
        result.failure = SAFETY_FAILURE_HARDWARE_FAILURE;
    } else if (feedback_failed) {
        result.failure = SAFETY_FAILURE_FEEDBACK_FAULT;
    } else if (motion_stopped(request.stop_generation)) {
        classify_stop(request, &result);
    } else {
        result.status = BODY_MOTION_COMPLETED;
    }
    return result;
}

void body_task(void *argument)
{
    (void)argument;
    MotionRequest request = {};
    for (;;) {
        if (xQueueReceive(s_motion_queue, &request, pdMS_TO_TICKS(BODY_POLL_MS)) == pdTRUE) {
            companion_emotion_t emotion = COMPANION_EMOTION_CONTENT;
            if (request.motion == SAFETY_MOTION_LOOK_USER) emotion = COMPANION_EMOTION_SURPRISED;
            else if (request.motion == SAFETY_MOTION_THINK) emotion = COMPANION_EMOTION_FOCUSED;
            else if (request.motion == SAFETY_MOTION_WAKE) emotion = COMPANION_EMOTION_HAPPY;
            else if (request.motion == SAFETY_MOTION_DROWSY) emotion = COMPANION_EMOTION_TIRED;
            companion_hardware_set_body_emotion(emotion);
            if (take_mutex(portMAX_DELAY)) {
                body_motion_result_t result = execute_motion_locked(request);
                xSemaphoreGive(s_mutex);
                companion_hardware_set_body_emotion(COMPANION_EMOTION_NEUTRAL);
                // Keep the motion gate busy until its final result has a slot.
                if (xQueueSend(s_motion_result_queue, &result, 0) != pdTRUE) {
                    safety_state_fail_motion(SAFETY_FAILURE_HARDWARE_FAILURE);
                } else if (result.status == BODY_MOTION_COMPLETED) {
                    safety_state_complete_motion();
                }
                voice_control_resume_wake_after_body_action();
            } else {
                companion_hardware_set_body_emotion(COMPANION_EMOTION_NEUTRAL);
                safety_state_fail_motion(SAFETY_FAILURE_HARDWARE_FAILURE);
                voice_control_resume_wake_after_body_action();
            }
        } else if (take_mutex(pdMS_TO_TICKS(20))) {
            update_ltr553_locked();
            update_top_touch_locked();
            safety_state_tick(esp_timer_get_time());
            bool affection = s_top_touch_affection_pending;
            s_top_touch_affection_pending = false;
            xSemaphoreGive(s_mutex);
            if (affection && !voice_control_motion_blocked()) companion_hardware_respond_to_top_touch();
        }
    }
}

}  // namespace

extern "C" esp_err_t body_hardware_init(void)
{
    if (s_initialized) return ESP_OK;
    s_mutex = xSemaphoreCreateMutex();
    s_motion_queue = xQueueCreate(1, sizeof(MotionRequest));
    s_motion_result_queue = xQueueCreate(1, sizeof(body_motion_result_t));
    if (s_mutex == nullptr || s_motion_queue == nullptr ||
        s_motion_result_queue == nullptr) return ESP_ERR_NO_MEM;
    i2c_master_bus_handle_t bus = bsp_i2c_get_handle();
    esp_err_t py32_err = configure_py32(bus);
    (void)configure_ina226(bus);
    esp_err_t uart_err = configure_servo_uart();
    esp_err_t ltr_err = configure_ltr553(bus);
    esp_err_t touch_err = configure_si12t(bus);
    s_body_motion_supported = py32_err == ESP_OK && uart_err == ESP_OK;
    s_proximity_supported = ltr_err == ESP_OK;
    s_ambient_light_supported = ltr_err == ESP_OK;
    s_body_touch_supported = touch_err == ESP_OK;
    s_servo_feedback_supported = false;
    s_calibrated = load_calibration_locked();
    if (s_calibrated)
        ESP_LOGI(TAG, "Servo pitch calibration reference: factory_delta=%s",
                 pitch_center_factory_category());
    safety_state_set_motion_capabilities(s_body_motion_supported, false);
    safety_state_set_calibrated(s_calibrated);
    safety_state_register_stop_callback(request_stop_callback, nullptr);
    if (xTaskCreatePinnedToCore(body_task, "body_safety", BODY_TASK_STACK_SIZE, nullptr,
                                BODY_TASK_PRIORITY, &s_task, BODY_TASK_CORE) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    s_initialized = true;
    ESP_LOGI(TAG, "K151 body: motion=%s touch=%s proximity=%s ambient=%s calibrated=%s power=off",
             s_body_motion_supported ? "yes" : "no",
             s_body_touch_supported ? "yes" : "no",
             s_proximity_supported ? "yes" : "no",
             s_ambient_light_supported ? "yes" : "no",
             s_calibrated ? "yes" : "no");
    return ESP_OK;
}

extern "C" esp_err_t body_hardware_calibrate_center(void)
{
    if (!s_initialized || !s_body_motion_supported) return ESP_ERR_NOT_SUPPORTED;
    safety_state_stop_motion();
    if (!take_mutex(pdMS_TO_TICKS(1000))) return ESP_ERR_TIMEOUT;
    request_stop_callback(nullptr);
    esp_err_t err = set_servo_power(true);
    int yaw = 0;
    int pitch = 0;
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Servo calibration unavailable: stage=power_enable error=%s",
                 esp_err_to_name(err));
    }
    if (err == ESP_OK) vTaskDelay(pdMS_TO_TICKS(SERVO_POWER_SETTLE_MS));
    if (err == ESP_OK) {
        uint8_t input = 0;
        bool input_high = i2c_read_registers(s_py32, PY32_GPIO_INPUT_LOW_REGISTER,
                                             &input, 1) == ESP_OK &&
                          (input & PY32_SERVO_POWER_BIT) != 0;
        ESP_LOGI(TAG, "Servo electrical gate: direction=output latch=high input=%s rx_idle=%s battery_source=%s",
                 input_high ? "high" : "low",
                 gpio_get_level(static_cast<gpio_num_t>(SERVO_RX_PIN)) ? "high" : "low",
                 battery_source_state());
    }
    if (err == ESP_OK && !read_servo_centers_locked(&yaw, &pitch)) {
        err = ESP_ERR_NOT_FOUND;
    }
    if (err == ESP_OK) err = save_calibration_locked(yaw, pitch);
    if (err != ESP_OK && err != ESP_ERR_NOT_FOUND) {
        ESP_LOGW(TAG, "Servo calibration unavailable: stage=persist error=%s",
                 esp_err_to_name(err));
    }
    if (!disable_servo_output_locked() && err == ESP_OK) err = ESP_FAIL;
    if (err == ESP_OK) {
        s_yaw_center_raw = yaw;
        s_pitch_center_raw = pitch;
        s_calibrated = true;
        s_servo_feedback_supported = true;
        safety_state_set_motion_capabilities(true, true);
        safety_state_set_calibrated(true);
        ESP_LOGI(TAG, "Servo centers calibrated from validated feedback; motion remains disabled");
    } else {
        s_servo_feedback_supported = false;
        safety_state_set_motion_capabilities(s_body_motion_supported, false);
        safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
    }
    xSemaphoreGive(s_mutex);
    return err;
}

extern "C" bool body_hardware_set_motion_enabled(bool enabled)
{
    if (!s_initialized) return false;
    if (!enabled) return safety_state_set_admin_enabled(false);
    if (!s_body_motion_supported || !s_calibrated) {
        return safety_state_set_admin_enabled(true);
    }
    if (!take_mutex(pdMS_TO_TICKS(1000))) {
        safety_state_fail_motion(SAFETY_FAILURE_BUSY);
        return false;
    }
    s_servo_feedback_supported = probe_servo_feedback_locked();
    safety_state_set_motion_capabilities(s_body_motion_supported,
                                         s_servo_feedback_supported);
    xSemaphoreGive(s_mutex);
    if (!s_servo_feedback_supported) {
        safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
        return false;
    }
    return safety_state_set_admin_enabled(enabled);
}

extern "C" bool body_hardware_play_motion(safety_motion_template_t motion,
                                           const safety_motion_guard_t *guard,
                                           const char *command_id)
{
    if (!s_initialized || guard == nullptr || command_id == nullptr ||
        strnlen(command_id, DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN) == 0 ||
        strnlen(command_id, DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN) >=
            DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN ||
        uxQueueSpacesAvailable(s_motion_result_queue) == 0 ||
        !safety_state_begin_motion(motion, guard, esp_timer_get_time())) {
        return false;
    }
    safety_diagnostics_t safety = {};
    safety_state_get_diagnostics(&safety);
    MotionRequest request = {motion,
                             __atomic_load_n(&s_stop_generation, __ATOMIC_SEQ_CST),
                             safety.failure_count,
                             {0}};
    strncpy(request.command_id, command_id, sizeof(request.command_id) - 1);
    if (xQueueSend(s_motion_queue, &request, 0) != pdTRUE) {
        safety_state_fail_motion(SAFETY_FAILURE_BUSY);
        return false;
    }
    return true;
}

extern "C" bool body_hardware_take_motion_result(body_motion_result_t *result)
{
    return result != nullptr && s_motion_result_queue != nullptr &&
           xQueueReceive(s_motion_result_queue, result, 0) == pdTRUE;
}

extern "C" bool body_hardware_peek_motion_result(body_motion_result_t *result)
{
    return result != nullptr && s_motion_result_queue != nullptr &&
           xQueuePeek(s_motion_result_queue, result, 0) == pdTRUE;
}

extern "C" void body_hardware_get_diagnostics(device_body_diagnostics_t *diagnostics)
{
    if (diagnostics == nullptr) return;
    memset(diagnostics, 0, sizeof(*diagnostics));
    bool locked = take_mutex(portMAX_DELAY);
    if (locked) {
        diagnostics->body_motion_supported = s_body_motion_supported;
        diagnostics->body_touch_supported = s_body_touch_supported;
        diagnostics->proximity_supported = s_proximity_supported;
        diagnostics->ambient_light_supported = s_ambient_light_supported;
        diagnostics->servo_feedback_supported = s_servo_feedback_supported;
        diagnostics->calibrated = s_calibrated;
        diagnostics->present = s_present;
        diagnostics->ambient_light = s_ambient_light;
    }
    safety_diagnostics_t safety = {};
    safety_state_get_diagnostics(&safety);
    if (locked) xSemaphoreGive(s_mutex);
    diagnostics->motion_runtime = safety.motion_runtime;
    diagnostics->safety_state = safety.state;
    diagnostics->last_failure = safety.last_failure;
    diagnostics->failure_count = safety.failure_count;
}

extern "C" bool body_hardware_take_workday_toggle(void)
{
    if (!s_initialized || !take_mutex(pdMS_TO_TICKS(50))) return false;
    bool pending = s_workday_toggle_pending;
    s_workday_toggle_pending = false;
    xSemaphoreGive(s_mutex);
    return pending;
}
