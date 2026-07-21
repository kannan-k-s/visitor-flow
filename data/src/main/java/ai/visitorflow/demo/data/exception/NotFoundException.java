package ai.visitorflow.demo.data.exception;

public class NotFoundException extends DomainException {
  public NotFoundException(String resource, Object identifier) {
    super(resource + " not found: " + identifier);
  }
}
