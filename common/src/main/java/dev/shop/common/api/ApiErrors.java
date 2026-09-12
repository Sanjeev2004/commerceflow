package dev.shop.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> domain(ApiException ex, HttpServletRequest request) { return problem(ex.status(), ex.getMessage(), request); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> invalid(Exception ex, HttpServletRequest request) { return problem(HttpStatus.BAD_REQUEST, "Invalid request parameters or body", request); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(Exception ex, HttpServletRequest request) { return problem(HttpStatus.FORBIDDEN, "Insufficient permissions", request); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        LoggerFactory.getLogger(ApiErrors.class).error("Unhandled request error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, HttpServletRequest request) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("correlationId", request.getAttribute("correlationId"));
        return ResponseEntity.status(status).body(body);
    }
}
