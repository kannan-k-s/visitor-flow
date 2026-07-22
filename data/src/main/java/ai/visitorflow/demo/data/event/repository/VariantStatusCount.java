package ai.visitorflow.demo.data.event.repository;

public interface VariantStatusCount {
  Long getVariantId();
  String getStatus();
  long getTotal();
}
