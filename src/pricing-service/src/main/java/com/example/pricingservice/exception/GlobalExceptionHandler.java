package com.example.pricingservice.exception;

import com.example.pricingservice.config.CorrelationIdFilter;
import com.example.pricingservice.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Business error: status={}, code={}, message={}, correlationId={}",
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

    // Malformed JSON or a value of the wrong type (e.g. latitude "abc") fails before bean validation runs
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Unreadable request body on path {}, correlationId={}", request.getRequestURI(), correlationId);
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is malformed or contains invalid values",
                "MALFORMED_REQUEST", request, correlationId);
    }

    // Unknown path (404), wrong method (405) or content type (415) keep their status instead of falling into the 500 handler
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleSpringMvcError(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        HttpStatus status = HttpStatus.valueOf(((org.springframework.web.ErrorResponse) ex).getStatusCode().value());
        log.warn("{} {} -> {}, correlationId={}", request.getMethod(), request.getRequestURI(), status.value(), correlationId);
        return buildResponse(status, status.getReasonPhrase() + ": " + request.getMethod() + " " + request.getRequestURI(),
                status.name(), request, correlationId);
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
        Object attr = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER);
        return attr != null ? attr.toString() : null;
    }
}
