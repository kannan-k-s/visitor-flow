package ai.visitorflow.demo.data.tenant;

public interface TenantResolver {
  Long resolve(String tenantName);
}
