package ai.visitorflow.demo.data.exception;

public abstract class DomainException extends RuntimeException {
  protected DomainException(String message) {
    super(message);
  }
}
