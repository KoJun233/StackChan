package com.kj.stackchan.expression;

import java.util.UUID;
import java.util.Set;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceExpressionService {
    private static final int MINIMUM_FPS = 1;
    private static final int MAXIMUM_FPS = 60;
    private static final Set<String> PREVIEW_CATEGORIES = Set.of("EMOTION", "SYSTEM", "BEHAVIOR");
    private static final Set<String> PREVIEW_EMOTIONS = Set.of(
            "NEUTRAL", "HAPPY", "LOVING", "SAD", "ANGRY", "SURPRISED", "CONFUSED",
            "SHY", "TIRED", "FOCUSED", "NERVOUS", "CONTENT");
    private static final Set<String> PREVIEW_SYSTEM = Set.of(
            "IDLE", "LISTENING", "PROCESSING", "SPEAKING", "SUCCESS", "NO_SPEECH",
            "RECOVERABLE_ERROR", "OFFLINE", "UPDATING");
    private static final Set<String> PREVIEW_BEHAVIORS = Set.of(
            "BOOT_APPEAR", "WAKE", "IDLE_BREATHE", "PROXIMITY_CURIOUS",
            "SHAKE_DIZZY", "DROWSY_SLEEP");
    private final DeviceRepository deviceRepository;
    private final DeviceCommandGateway commandGateway;
    private final CompanionRoleService roleService;

    public DeviceExpressionService(DeviceRepository deviceRepository,
                                   DeviceCommandGateway commandGateway,
                                   CompanionRoleService roleService) {
        this.deviceRepository = deviceRepository;
        this.commandGateway = commandGateway;
        this.roleService = roleService;
    }

    @Transactional(readOnly = true)
    public boolean apply(UUID deviceId, UUID roleId, ExpressionSuggestionParser.Suggestion suggestion) {
        if (deviceId == null || roleId == null || suggestion == null) return false;
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || !device.isDynamicExpressionSupported()) return false;
        CompanionRoleService.RoleSnapshot role = roleService.get(roleId);
        return commandGateway.configureExpression(
                deviceId, role.expressionThemeColor(), suggestion.emotion().name(),
                suggestion.intensity().name(), suggestion.durationSeconds());
    }

    @Transactional(readOnly = true)
    public boolean synchronizeActiveRoleTheme(UUID deviceId) {
        if (deviceId == null) return false;
        CompanionRoleService.RoleSnapshot role = roleService.getActive(deviceId);
        return apply(deviceId, role.id(), new ExpressionSuggestionParser.Suggestion(
                "", CompanionEmotion.NEUTRAL, EmotionIntensity.MEDIUM, 5));
    }

    @Transactional(readOnly = true)
    public FrameRateSettings getFrameRateSettings(UUID deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return null;
        return snapshot(device, device.isDynamicExpressionSupported() &&
                commandGateway.isConnected(deviceId));
    }

    @Transactional
    public FrameRateSettings configureFrameRate(UUID deviceId, String mode, int minFps, int maxFps) {
        validateFrameRate(mode, minFps, maxFps);
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return null;
        device.configureExpressionFrameRate(mode, minFps, maxFps);
        deviceRepository.save(device);
        boolean applied = device.isDynamicExpressionSupported() &&
                commandGateway.configureExpressionFrameRate(deviceId, mode, minFps, maxFps);
        return snapshot(device, applied);
    }

    @Transactional(readOnly = true)
    public boolean preview(UUID deviceId, String category, String value, int durationSeconds) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || !device.isDynamicExpressionSupported()) return false;
        validatePreview(category, value, durationSeconds);
        return commandGateway.previewExpression(deviceId, category, value, durationSeconds);
    }

    @Transactional(readOnly = true)
    public boolean synchronizeFrameRate(UUID deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        return device != null && device.isDynamicExpressionSupported() &&
                commandGateway.configureExpressionFrameRate(deviceId, device.getExpressionFpsMode(),
                        device.getExpressionMinFps(), device.getExpressionMaxFps());
    }

    private static FrameRateSettings snapshot(DeviceEntity device, boolean applied) {
        return new FrameRateSettings(device.getExpressionFpsMode(), device.getExpressionMinFps(),
                device.getExpressionMaxFps(), applied);
    }

    private static void validateFrameRate(String mode, int minFps, int maxFps) {
        if (!("FIXED".equals(mode) || "ADAPTIVE".equals(mode)) ||
                minFps < MINIMUM_FPS || minFps > MAXIMUM_FPS ||
                maxFps < MINIMUM_FPS || maxFps > MAXIMUM_FPS ||
                minFps > maxFps || ("FIXED".equals(mode) && minFps != maxFps)) {
            throw new IllegalArgumentException("Invalid expression frame-rate policy");
        }
    }

    private static void validatePreview(String category, String value, int durationSeconds) {
        Set<String> values = switch (category) {
            case "EMOTION" -> PREVIEW_EMOTIONS;
            case "SYSTEM" -> PREVIEW_SYSTEM;
            case "BEHAVIOR" -> PREVIEW_BEHAVIORS;
            default -> Set.of();
        };
        if (!PREVIEW_CATEGORIES.contains(category) || !values.contains(value) ||
                durationSeconds < 1 || durationSeconds > 15) {
            throw new IllegalArgumentException("Invalid expression preview");
        }
    }

    public record FrameRateSettings(String mode, int minFps, int maxFps, boolean applied) {}
}
