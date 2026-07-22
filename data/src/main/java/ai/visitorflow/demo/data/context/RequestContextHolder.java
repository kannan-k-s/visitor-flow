package ai.visitorflow.demo.data.context;

public final class RequestContextHolder {
  private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

  private RequestContextHolder() {
  }

  public static void set(RequestContext context) {
    CONTEXT.set(context);
  }

  public static RequestContext get() {
    RequestContext context = CONTEXT.get();
    if (context == null) {
      throw new IllegalStateException("RequestContext not initialised");
    }
    return context;
  }

  public static Long tenantId() {
    return get().getTenantId();
  }

  public static Long userId() {
    return get().getUserId();
  }

  public static String visitorId() {
    return get().getVisitorId();
  }

  public static Long anonVisitorId() {
    return get().getAnonVisitorId();
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
