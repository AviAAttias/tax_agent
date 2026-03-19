package com.abco.taxassessment.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global exception handler producing RFC 9457 Problem Details responses (§5).
 *
 * All error responses use application/problem+json content type.
 * The traceId field is populated from MDC (injected by OTel Micrometer bridge).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEMS_BASE_URI = "https://abco.com/problems/";

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<ProblemDetail> handleTenantContextMissing(TenantContextMissingException ex,
                                                                     HttpServletRequest request) {
        log.warn("Tenant context missing: {}", ex.getMessage());
        meterRegistry.counter("api.errors", "type", "tenant_context_missing").increment();
        ProblemDetail problem = buildProblem(HttpStatus.UNAUTHORIZED, "tenant-context-missing",
                "Tenant Authentication Required", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex,
                                                                 HttpServletRequest request) {
        log.info("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.NOT_FOUND, "resource-not-found",
                "Resource Not Found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DuplicateStatementException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateStatement(DuplicateStatementException ex,
                                                                    HttpServletRequest request) {
        log.info("Duplicate statement: {}", ex.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.CONFLICT, "duplicate-statement",
                "Duplicate Statement", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.info("Business rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation",
                "Business Rule Violation", ex.getMessage(), request.getRequestURI());
        problem.setProperty("errorCode", ex.getErrorCode());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.info("Validation failed: {}", detail);
        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "validation-error",
                "Validation Failed", detail, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex,
                                                               HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.UNAUTHORIZED, "authentication-failed",
                "Authentication Failed", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex,
                                                              HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.FORBIDDEN, "access-denied",
                "Access Denied", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing request: {}", request.getRequestURI(), ex);
        meterRegistry.counter("api.errors", "type", "unexpected").increment();
        ProblemDetail problem = buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error", "An unexpected error occurred", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ProblemDetail buildProblem(HttpStatus status, String typeSlug, String title,
                                        String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(PROBLEMS_BASE_URI + typeSlug));
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setInstance(URI.create(instance));
        return problem;
    }
}
