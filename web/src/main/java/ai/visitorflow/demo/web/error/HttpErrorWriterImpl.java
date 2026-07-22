package ai.visitorflow.demo.web.error;

import ai.visitorflow.demo.data.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class HttpErrorWriterImpl implements HttpErrorWriter {
  private final ObjectMapper objectMapper;

  @Override
  public void write(
    HttpServletResponse response, int status, String code, String message
  ) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
      .code(code)
      .message(message)
      .timestamp(Instant.now())
      .build());
  }
}
