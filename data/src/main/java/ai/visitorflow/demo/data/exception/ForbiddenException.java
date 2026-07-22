package ai.visitorflow.demo.data.exception;

public class ForbiddenException extends DomainException {
  public ForbiddenException(String message) {
    super(message);
  }
}
