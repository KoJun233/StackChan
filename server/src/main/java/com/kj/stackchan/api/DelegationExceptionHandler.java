package com.kj.stackchan.api;

import com.kj.stackchan.delegation.DelegationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={DelegationController.class,DelegationDeliveryController.class,CompanionOperationController.class})
public class DelegationExceptionHandler {
    @ExceptionHandler(DelegationException.class)
    ResponseEntity<?> domain(DelegationException e) {
        if(e.clarification()!=null) return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                .body(new ClarificationError(e.code(),e.clarification(),e.clarification()));
        HttpStatus status=switch(e.code()) {
            case "REVISION_CONFLICT","IDEMPOTENCY_CONFLICT","INVALID_STATE" -> HttpStatus.CONFLICT;
            case "EVIDENCE_REQUIRED","CAPABILITY_UNAVAILABLE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "EXECUTION_CAPACITY_BUSY","DELEGATION_CAPACITY" -> HttpStatus.TOO_MANY_REQUESTS;
            case "DEPENDENCY_UNAVAILABLE","OPERATION_PENDING" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "ACTION_NOT_ALLOWED" -> HttpStatus.FORBIDDEN;
            default -> e.notFound()?HttpStatus.NOT_FOUND:HttpStatus.BAD_REQUEST;
        };
        return error(status,e.code());
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<DeviceApiExceptionHandler.ApiError> invalid(Exception e) { return error(HttpStatus.BAD_REQUEST,"DELEGATION_INPUT_INVALID"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<DeviceApiExceptionHandler.ApiError> unavailable(Exception e) { return error(HttpStatus.SERVICE_UNAVAILABLE,"DELEGATION_UNAVAILABLE"); }
    private ResponseEntity<DeviceApiExceptionHandler.ApiError> error(HttpStatus status,String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new DeviceApiExceptionHandler.ApiError(code,"操作未完成，请检查设置或刷新后重试。"));
    }
    record ClarificationError(String code,String message,String clarification) {}
}
