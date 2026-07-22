package ai.visitorflow.demo.web.error;

import ai.visitorflow.demo.data.exception.ErrorResponse;
import ai.visitorflow.demo.data.exception.ForbiddenException;
import ai.visitorflow.demo.data.exception.NotFoundException;
import ai.visitorflow.demo.data.exception.TrackingUnavailableException;
import ai.visitorflow.demo.data.exception.ValidationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionAdvice {
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
  }

  @ExceptionHandler({ValidationException.class, MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, "invalid_request", exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
      .findFirst()
      .map(error -> error.getField() + ": " + error.getDefaultMessage())
      .orElse("Request validation failed");
    return response(HttpStatus.BAD_REQUEST, "invalid_request", message);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> forbidden(ForbiddenException exception) {
    return response(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage());
  }

  @ExceptionHandler(TrackingUnavailableException.class)
  public ResponseEntity<ErrorResponse> unavailable(TrackingUnavailableException exception) {
    return response(HttpStatus.SERVICE_UNAVAILABLE, "tracking_unavailable", exception.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ErrorResponse.builder()
      .code(code)
      .message(message)
      .timestamp(Instant.now())
      .build());
  }
}
