package com.example.identityservice.exception;

import com.example.identityservice.config.CorrelationIdFilter;
import com.example.identityservice.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Business exception: status={}, code={}, message={}, correlationId={}",
                ex.getStatus(), ex.getCode(), ex.getMessage(), correlationId);
        return buildResponse(ex.getStatus(), ex.getMessage(), ex.getCode(), request, correlationId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error on path {}: {}, correlationId={}", request.getRequestURI(), details, correlationId);
        return buildResponse(HttpStatus.BAD_REQUEST, details, "VALIDATION_ERROR", request, correlationId);
    }

    // Malformed JSON or an unknown enum value (e.g. role "ROOT") fails before bean validation runs
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Unreadable request body on path {}, correlationId={}", request.getRequestURI(), correlationId);
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is malformed or contains invalid values",
                "MALFORMED_REQUEST", request, correlationId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Internal server error on path {}: correlationId={}", request.getRequestURI(), correlationId, ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                "INTERNAL_SERVER_ERROR", request, correlationId);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String code,
                                                        HttpServletRequest request, String correlationId) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .code(code)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    // Returns null when the caller sent no X-Correlation-ID; the field is then omitted from the error body
    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            Object attr = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER);
            correlationId = attr != null ? attr.toString() : null;
        }
        return correlationId;
    }
}
