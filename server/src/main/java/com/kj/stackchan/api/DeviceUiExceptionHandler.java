package com.kj.stackchan.api;

import com.kj.stackchan.device.DeviceUiException;
import com.kj.stackchan.device.InvalidDeviceTokenException;
import com.kj.stackchan.interaction.InvalidInteractionSettingsException;
import com.kj.stackchan.role.*;
import com.kj.stackchan.workday.InvalidWorkdayStateException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DeviceUiController.class)
public class DeviceUiExceptionHandler {
    @ExceptionHandler(DeviceUiException.class)
    ResponseEntity<DeviceApiExceptionHandler.ApiError> ui(DeviceUiException exception) {
        return error(exception.status(), exception.code());
    }
    @ExceptionHandler(InvalidDeviceTokenException.class)
    ResponseEntity<DeviceApiExceptionHandler.ApiError> unauthorized(InvalidDeviceTokenException exception) {
        return error(HttpStatus.UNAUTHORIZED, "device_credentials_invalid");
    }
    @ExceptionHandler({RoleConflictException.class, InvalidWorkdayStateException.class})
    ResponseEntity<DeviceApiExceptionHandler.ApiError> conflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "device_ui_busy_or_changed");
    }
    @ExceptionHandler(RoleNotFoundException.class)
    ResponseEntity<DeviceApiExceptionHandler.ApiError> notFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, "device_ui_target_unavailable");
    }
    @ExceptionHandler({InvalidRoleException.class, InvalidInteractionSettingsException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<DeviceApiExceptionHandler.ApiError> invalid(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "invalid_ui_request");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<DeviceApiExceptionHandler.ApiError> unavailable(Exception exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "device_ui_unavailable");
    }
    private ResponseEntity<DeviceApiExceptionHandler.ApiError> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(DeviceApiExceptionHandler.JSON_UTF8)
                .body(new DeviceApiExceptionHandler.ApiError(code, "操作未保存或已失效，请刷新后重试。"));
    }
}
