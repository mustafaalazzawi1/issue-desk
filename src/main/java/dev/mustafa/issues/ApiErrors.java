package dev.mustafa.issues;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(
            Map.of(
                "message",
                ex.getReason() == null ? "Request could not be completed." : ex.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
    var errors = new java.util.TreeMap<String, String>();
    for (var error : ex.getBindingResult().getFieldErrors())
      errors.putIfAbsent(error.getField(), error.getDefaultMessage());
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Please check the highlighted fields.", "fields", errors));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(Exception ex) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", "A value is missing or has an unsupported format."));
  }
}
