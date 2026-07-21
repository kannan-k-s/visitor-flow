package ai.visitorflow.demo.web.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface HttpErrorWriter {
  void write(HttpServletResponse response, int status, String code, String message) throws IOException;
}
