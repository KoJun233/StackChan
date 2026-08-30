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
constexpr int SERVO_RAW_MIN = 0;
constexpr int SERVO_RAW_MAX = 1000;
constexpr int SERVO_RAW_PER_10_DEGREES = 32;
constexpr int PITCH_CENTER_DEGREES = 45;
constexpr int PITCH_MIN_DEGREES = 10;
constexpr int PITCH_MAX_DEGREES = 80;
constexpr int YAW_MIN_DEGREES = -20;
constexpr int YAW_MAX_DEGREES = 20;
constexpr int SERVO_FEEDBACK_TOLERANCE_RAW = 48;

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
constexpr uint16_t PROXIMITY_PRESENT_THRESHOLD = 500;
constexpr uint16_t PROXIMITY_ABSENT_THRESHOLD = 350;
constexpr uint8_t PROXIMITY_CONFIRM_SAMPLES = 3;
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

constexpr char BODY_NVS_NAMESPACE[] = "body_motion";
constexpr char BODY_NVS_YAW_CENTER[] = "yaw_center";
constexpr char BODY_NVS_PITCH_CENTER[] = "pitch_center";
constexpr char BODY_NVS_CALIBRATED[] = "calibrated";
constexpr int BODY_TASK_STACK_SIZE = 6144;
constexpr int BODY_TASK_PRIORITY = 2;
constexpr int BODY_TASK_CORE = 1;
constexpr int BODY_POLL_MS = 50;
constexpr int64_t WORKDAY_TOGGLE_HOLD_US = 1500LL * 1000LL;
// The factory firmware leaves VM enabled well before servo initialization.
// Our fail-closed design powers VM only for an operation, so allow the boost
// rail and both servos to finish a cold start before requesting feedback.
constexpr int SERVO_POWER_SETTLE_MS = 1200;
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

constexpr MotionFrame WAKE_FRAMES[] = {{0, 35, 450}, {0, 48, 450}, {0, 45, 350}};
constexpr MotionFrame LOOK_USER_FRAMES[] = {{0, 52, 650}, {0, 45, 450}};
constexpr MotionFrame NOD_SMALL_FRAMES[] = {{0, 51, 350}, {0, 39, 400}, {0, 48, 350}, {0, 45, 400}};
constexpr MotionFrame THINK_FRAMES[] = {{16, 50, 800}, {10, 48, 450}, {0, 45, 550}};
constexpr MotionFrame DROWSY_FRAMES[] = {{-8, 28, 900}, {5, 38, 650}, {0, 45, 600}};

const char *TAG = "body_hardware";
i2c_master_dev_handle_t s_py32;
i2c_master_dev_handle_t s_ina226;
i2c_master_dev_handle_t s_ltr553;
i2c_master_dev_handle_t s_si12t;
SemaphoreHandle_t s_mutex;
QueueHandle_t s_motion_queue;
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
bool s_workday_toggle_pending;
int64_t s_top_touch_started_us;
volatile bool s_stop_requested;
device_ambient_light_t s_ambient_light = DEVICE_AMBIENT_LIGHT_UNAVAILABLE;
device_ambient_light_t s_ambient_light_candidate = DEVICE_AMBIENT_LIGHT_UNAVAILABLE;
int s_yaw_center_raw;
int s_pitch_center_raw;
uint8_t s_present_confirm_count;
uint8_t s_absent_confirm_count;
uint8_t s_ltr_failure_count;
uint8_t s_ambient_confirm_count;

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
    if (metadata[1] != parameter_count + 2) {
        ESP_LOGW(TAG, "Servo response rejected: stage=length id=%u", id);
        return false;
    }
    if (metadata[2] != 0) {
        ESP_LOGW(TAG, "Servo response rejected: stage=status id=%u", id);
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
    uint8_t data[2] = {};
    if (enabled == nullptr ||
        !servo_read_register(id, SERVO_TORQUE_REGISTER, data, sizeof(data)) || data[0] > 1) {
        return false;
    }
    *enabled = data[0] == 1;
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

bool servo_move(uint8_t id, int raw_position, uint16_t duration_ms)
{
    if (raw_position < SERVO_RAW_MIN || raw_position > SERVO_RAW_MAX) return false;
    uint16_t bounded_duration = std::clamp<uint16_t>(duration_ms, 250, 1000);
    uint8_t data[6] = {
        static_cast<uint8_t>((raw_position >> 8) & 0xFF),
        static_cast<uint8_t>(raw_position & 0xFF),
        static_cast<uint8_t>((bounded_duration >> 8) & 0xFF),
        static_cast<uint8_t>(bounded_duration & 0xFF),
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
    s_stop_requested = true;
}

void disable_servo_output_locked()
{
    if (s_servo_powered) {
        (void)servo_set_torque(YAW_SERVO_ID, false);
        (void)servo_set_torque(PITCH_SERVO_ID, false);
        vTaskDelay(pdMS_TO_TICKS(20));
        (void)set_servo_power(false);
    }
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
    esp_err_t err = add_i2c_device(bus, LTR553_ADDRESS, &s_ltr553);
    uint8_t identity[2] = {};
    if (err == ESP_OK) err = i2c_read_registers(s_ltr553, LTR553_PART_ID, identity, 2);
    if (err != ESP_OK || (identity[0] & 0xF0) != 0x90 || identity[1] != 0x05) {
        return ESP_ERR_NOT_FOUND;
    }
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_LED, 0x6A);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_PULSES, 0x04);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_RATE, 0x02);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_ALS_RATE, 0x03);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_ALS_CONTROL, 0x01);
    if (err == ESP_OK) err = i2c_write_register(s_ltr553, LTR553_PS_CONTROL, 0x03);
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
    uint8_t data[7] = {};
    if (i2c_read_registers(s_ltr553, LTR553_DATA_START, data, sizeof(data)) != ESP_OK) {
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
    uint16_t channel_1 = static_cast<uint16_t>(data[0] | (data[1] << 8));
    uint16_t channel_0 = static_cast<uint16_t>(data[2] | (data[3] << 8));
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

    uint16_t proximity = static_cast<uint16_t>(data[5] | ((data[6] & 0x07) << 8));
    if (!s_present && proximity >= PROXIMITY_PRESENT_THRESHOLD) {
        s_absent_confirm_count = 0;
        if (++s_present_confirm_count >= PROXIMITY_CONFIRM_SAMPLES) {
            s_present = true;
            s_present_confirm_count = 0;
        }
    } else if (s_present && proximity <= PROXIMITY_ABSENT_THRESHOLD) {
        s_present_confirm_count = 0;
        if (++s_absent_confirm_count >= PROXIMITY_CONFIRM_SAMPLES) {
            s_present = false;
            s_absent_confirm_count = 0;
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
        return;
    }
    bool pressed = (value & 0x3F) != 0;
    int64_t now_us = esp_timer_get_time();
    if (pressed && !s_top_touch_pressed) {
        s_top_touch_started_us = now_us;
        safety_diagnostics_t safety = {};
        safety_state_get_diagnostics(&safety);
        if (safety.motion_runtime == SAFETY_MOTION_RUNNING) {
            safety_state_stop_motion_with_reason(SAFETY_FAILURE_TOUCH_STOP);
        }
    } else if (!pressed && s_top_touch_pressed) {
        if (s_top_touch_started_us > 0 && now_us - s_top_touch_started_us >= WORKDAY_TOGGLE_HOLD_US) {
            s_workday_toggle_pending = true;
        }
        s_top_touch_started_us = 0;
    }
    s_top_touch_pressed = pressed;
}

bool wait_frame_locked(uint16_t duration_ms)
{
    int64_t deadline = esp_timer_get_time() + static_cast<int64_t>(duration_ms) * 1000LL;
    while (esp_timer_get_time() < deadline) {
        update_top_touch_locked();
        safety_state_tick(esp_timer_get_time());
        if (s_stop_requested) return false;
        vTaskDelay(pdMS_TO_TICKS(20));
    }
    return true;
}

bool verify_frame_locked(const MotionFrame &frame)
{
    int yaw = 0;
    int pitch = 0;
    return servo_read_position(YAW_SERVO_ID, &yaw) &&
           servo_read_position(PITCH_SERVO_ID, &pitch) &&
           std::abs(yaw - yaw_raw(frame.yaw_degrees)) <= SERVO_FEEDBACK_TOLERANCE_RAW &&
           std::abs(pitch - pitch_raw(frame.pitch_degrees)) <= SERVO_FEEDBACK_TOLERANCE_RAW;
}

bool probe_servo_feedback_locked()
{
    int yaw = 0;
    int pitch = 0;
    if (set_servo_power(true) != ESP_OK) return false;
    vTaskDelay(pdMS_TO_TICKS(SERVO_POWER_SETTLE_MS));
    bool available = read_servo_centers_locked(&yaw, &pitch);
    disable_servo_output_locked();
    return available;
}

void execute_motion_locked(safety_motion_template_t motion)
{
    const MotionDefinition *definition = motion_definition(motion);
    if (definition == nullptr || !s_calibrated) {
        safety_state_fail_motion(SAFETY_FAILURE_NOT_CALIBRATED);
        return;
    }
    for (size_t index = 0; index < definition->frame_count; index++) {
        if (!frame_inside_limits(definition->frames[index])) {
            safety_state_fail_motion(SAFETY_FAILURE_SOFT_LIMIT);
            return;
        }
    }
    s_stop_requested = false;
    if (set_servo_power(true) != ESP_OK) {
        safety_state_fail_motion(SAFETY_FAILURE_HARDWARE_FAILURE);
        return;
    }
    vTaskDelay(pdMS_TO_TICKS(100));
    bool yaw_torque_enabled = false;
    bool pitch_torque_enabled = false;
    if (!servo_set_torque(YAW_SERVO_ID, true) ||
        !servo_read_torque(YAW_SERVO_ID, &yaw_torque_enabled) || !yaw_torque_enabled ||
        !servo_set_torque(PITCH_SERVO_ID, true) ||
        !servo_read_torque(PITCH_SERVO_ID, &pitch_torque_enabled) || !pitch_torque_enabled) {
        safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
        disable_servo_output_locked();
        return;
    }
    for (size_t index = 0; index < definition->frame_count && !s_stop_requested; index++) {
        const MotionFrame &frame = definition->frames[index];
        if (!servo_move(YAW_SERVO_ID, yaw_raw(frame.yaw_degrees), frame.duration_ms) ||
            !servo_move(PITCH_SERVO_ID, pitch_raw(frame.pitch_degrees), frame.duration_ms) ||
            !wait_frame_locked(frame.duration_ms) || !verify_frame_locked(frame)) {
            if (!s_stop_requested) safety_state_fail_motion(SAFETY_FAILURE_FEEDBACK_FAULT);
            break;
        }
    }
    disable_servo_output_locked();
    if (!s_stop_requested) safety_state_complete_motion();
}

void body_task(void *argument)
{
    (void)argument;
    safety_motion_template_t motion = SAFETY_MOTION_WAKE;
    for (;;) {
        if (xQueueReceive(s_motion_queue, &motion, pdMS_TO_TICKS(BODY_POLL_MS)) == pdTRUE) {
            if (take_mutex(portMAX_DELAY)) {
                execute_motion_locked(motion);
                xSemaphoreGive(s_mutex);
            }
        } else if (take_mutex(pdMS_TO_TICKS(20))) {
            update_ltr553_locked();
            update_top_touch_locked();
            safety_state_tick(esp_timer_get_time());
            xSemaphoreGive(s_mutex);
        }
    }
}

}  // namespace

extern "C" esp_err_t body_hardware_init(void)
{
    if (s_initialized) return ESP_OK;
    s_mutex = xSemaphoreCreateMutex();
    s_motion_queue = xQueueCreate(1, sizeof(safety_motion_template_t));
    if (s_mutex == nullptr || s_motion_queue == nullptr) return ESP_ERR_NO_MEM;
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
    s_stop_requested = true;
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
    disable_servo_output_locked();
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
                                           const safety_motion_guard_t *guard)
{
    if (!s_initialized || guard == nullptr ||
        !safety_state_begin_motion(motion, guard, esp_timer_get_time())) {
        return false;
    }
    if (xQueueSend(s_motion_queue, &motion, 0) != pdTRUE) {
        safety_state_fail_motion(SAFETY_FAILURE_BUSY);
        return false;
    }
    return true;
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
