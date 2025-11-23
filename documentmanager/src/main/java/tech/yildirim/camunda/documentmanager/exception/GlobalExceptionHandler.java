package tech.yildirim.camunda.documentmanager.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for the document manager application.
 * Handles various types of exceptions and converts them to appropriate HTTP responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation constraint violations.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("Validation constraint violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    /**
     * Handles missing request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    /**
     * Handles missing request parts (like file uploads).
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Void> handleMissingServletRequestPartException(MissingServletRequestPartException ex) {
        log.warn("Missing request part: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    /**
     * Handles method argument type mismatches (like invalid enum values).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }
}
