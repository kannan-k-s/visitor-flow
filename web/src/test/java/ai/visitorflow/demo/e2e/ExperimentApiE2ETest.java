package ai.visitorflow.demo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ai.visitorflow.demo.data.tenant.model.TenantEntity;
import ai.visitorflow.demo.data.tenant.model.UserEntity;
import ai.visitorflow.demo.data.tenant.repository.TenantRepository;
import ai.visitorflow.demo.data.tenant.repository.UserRepository;
import ai.visitorflow.demo.web.security.JwtService;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
  "spring.data.redis.connect-timeout=2s", "spring.data.redis.timeout=2s"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ExperimentApiE2ETest {
  private static final String TENANT = "demo";
  private static final String ADMIN_EMAIL = "admin@example.com";
  private static final Duration INITIAL_EVENT_DELAY = Duration.ofSeconds(1);
  private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration EVENT_POLL_DELAY = Duration.ofMillis(250);
  private static final Duration BROKER_RECOVERY_TIMEOUT = Duration.ofSeconds(30);

  @LocalServerPort
  private int port;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private Playwright playwright;
  private APIRequestContext anonymous;
  private APIRequestContext authenticated;
  private APIRequestContext wrongTenant;
  private Long experimentId;
  private Long firstVariantId;
  private Long secondVariantId;
  private Long primaryAnonId;
  private Long primaryVariantId;
  private Long linkedAnonId;
  private Long linkedVariantId;
  private final Map<Long, Integer> expectedAssigned = new HashMap<>();
  private final Map<Long, Integer> expectedExposed = new HashMap<>();
  private final Map<Long, Integer> expectedConverted = new HashMap<>();
  private String experimentName;
  private String visitorId;

  @BeforeAll
  void setUp() {
    TenantEntity tenant = tenantRepository.findByName(TENANT).orElseThrow();
    UserEntity user = userRepository.findByEmailAndTenantId(ADMIN_EMAIL, tenant.getId()).orElseThrow();
    String session = jwtService.mint(user.getId(), tenant.getId(), user.getEmail());
    String mismatchedSession = jwtService.mint(user.getId(), tenant.getId() + 1000, user.getEmail());
    playwright = Playwright.create();
    anonymous = context(Map.of());
    authenticated = context(Map.of("Cookie", "session=" + session));
    wrongTenant = context(Map.of("Cookie", "session=" + mismatchedSession));
    experimentName = "playwright-e2e-" + UUID.randomUUID();
    visitorId = "client-visitor-" + UUID.randomUUID();
  }

  @AfterAll
  void tearDown() {
    if (experimentId != null && authenticated != null) {
      authenticated.delete("/" + TENANT + "/v1/experiments/" + experimentId);
    }
    if (wrongTenant != null) {
      wrongTenant.dispose();
    }
    if (authenticated != null) {
      authenticated.dispose();
    }
    if (anonymous != null) {
      anonymous.dispose();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @Test
  @Order(1)
  void coversHealthAuthenticationTenantIsolationAndOAuthState() {
    assertStatus(anonymous.get("/actuator/health"), 200);
    assertError(anonymous.get("/" + TENANT + "/v1/experiments"), 401, "unauthorized");
    assertError(wrongTenant.get("/" + TENANT + "/v1/experiments"), 403, "forbidden");
    assertError(authenticated.get("/missing/v1/experiments"), 404, "not_found");

    APIResponse login = assertStatus(anonymous.get("/" + TENANT + "/v1/auth/login"), 302);
    String location = login.headers().get("location");
    assertThat(location).contains("state=eyJ");
    assertThat(location).contains("/login/oauth2/code/google");
    assertThat(location).doesNotContain("tenant=");
  }

  @Test
  @Order(2)
  void coversExperimentCreateReadListUpdateAndValidationApis() {
    APIResponse create = assertStatus(authenticated.post(
      experimentsPath(), json(experimentRequest(experimentName, "hash", 50, 50, false, null))
    ), 200);
    JsonNode created = body(create);
    experimentId = created.get("id").asLong();
    firstVariantId = created.get("variants").get(0).get("id").asLong();
    secondVariantId = created.get("variants").get(1).get("id").asLong();
    assertThat(created.get("name").asString()).isEqualTo(experimentName);
    assertThat(created.has("analytics_warning")).isFalse();

    assertError(authenticated.post(
      experimentsPath(), json(experimentRequest(experimentName, "hash", 50, 50, false, null))
    ), 400, "invalid_request");
    assertError(authenticated.post(
      experimentsPath(), json(experimentRequest(experimentName, "hash", 90, 9, false, null))
    ), 400, "invalid_request");
    assertError(authenticated.post(
      experimentsPath(), json(experimentRequest(experimentName, "swrr", 50, 50, false, null))
    ), 400, "invalid_request");
    assertError(authenticated.post(
      experimentsPath(), json(experimentRequest(experimentName, "hash", 50, 50, true, 123L))
    ), 400, "invalid_request");

    JsonNode fetched = body(assertStatus(authenticated.get(experimentPath()), 200));
    assertThat(fetched.get("id").asLong()).isEqualTo(experimentId);
    JsonNode page = body(assertStatus(authenticated.get(experimentsPath()), 200));
    assertThat(containsExperiment(page.get("items"), experimentId)).isTrue();
    assertError(authenticated.get(experimentsPath() + "/9223372036854775807"), 404, "not_found");

    APIResponse update = assertStatus(authenticated.put(
      experimentPath(), json(updateRequest(60, 40, firstVariantId, secondVariantId))
    ), 200);
    JsonNode updated = body(update);
    assertThat(updated.get("variants").get(0).get("id").asLong()).isEqualTo(firstVariantId);
    assertThat(updated.get("variants").get(1).get("id").asLong()).isEqualTo(secondVariantId);
    assertThat(updated.has("analytics_warning")).isFalse();

    assertError(authenticated.put(
      experimentPath(), json(updateRequest(50, 50, firstVariantId, Long.MAX_VALUE))
    ), 400, "invalid_request");
    assertError(authenticated.put(
      experimentPath(), json(updateRequest(50, 50, firstVariantId, firstVariantId))
    ), 400, "invalid_request");
  }

  @Test
  @Order(3)
  void coversAssignmentApiIdentityDegradationAndValidation() {
    String path = "/" + TENANT + "/v1/assign";
    assertStatus(anonymous.get(path), 400);
    assertError(anonymous.get(path + "?experiments=-1"), 400, "invalid_request");
    assertError(anonymous.get(path + "?experiments=1", headers("X-Anon-Id", "bad")), 400, "invalid_request");
    assertError(anonymous.get(path + oversizedExperimentQuery()), 400, "invalid_request");
    assertError(anonymous.get("/missing/v1/assign?experiments=1"), 404, "not_found");

    JsonNode unknown = body(assertStatus(anonymous.get(path + "?experiments=9223372036854775807"), 200));
    assertThat(unknown.get("degraded").asBoolean()).isTrue();
    assertThat(unknown.get("assignments").size()).isZero();

    JsonNode assigned = body(assertStatus(anonymous.get(path + "?experiments=" + experimentId), 200));
    primaryAnonId = assigned.get("anon_visitor_id").asLong();
    primaryVariantId = assigned.get("assignments").get(String.valueOf(experimentId)).asLong();
    assertThat(assigned.get("degraded").asBoolean()).isFalse();
    expectedAssigned.merge(primaryVariantId, 1, Integer::sum);

    JsonNode repeated = body(assertStatus(anonymous.get(
      path + "?experiments=" + experimentId, headers("X-Anon-Id", String.valueOf(primaryAnonId))
    ), 200));
    assertThat(repeated.get("anon_visitor_id").asLong()).isEqualTo(primaryAnonId);
    assertThat(repeated.get("assignments").get(String.valueOf(experimentId)).asLong())
      .isEqualTo(primaryVariantId);

    JsonNode linked = body(assertStatus(anonymous.get(
      path + "?experiments=" + experimentId, headers("X-Visitor-Id", visitorId)
    ), 200));
    linkedAnonId = linked.get("anon_visitor_id").asLong();
    linkedVariantId = linked.get("assignments").get(String.valueOf(experimentId)).asLong();
    assertThat(linkedAnonId).isNotEqualTo(primaryAnonId);
    expectedAssigned.merge(linkedVariantId, 1, Integer::sum);

    JsonNode linkedAgain = body(assertStatus(anonymous.get(
      path + "?experiments=" + experimentId, headers("X-Visitor-Id", visitorId)
    ), 200));
    assertThat(linkedAgain.get("anon_visitor_id").asLong()).isEqualTo(linkedAnonId);
    assertThat(linkedAgain.get("assignments").get(String.valueOf(experimentId)).asLong())
      .isEqualTo(linkedVariantId);
  }

  @Test
  @Order(4)
  void coversTrackingApiValidationIdentityIdempotencyAndOrphans() {
    String path = "/" + TENANT + "/v1/track";
    Map<String, Object> exposed = trackingRequest("exposed", Map.of(experimentId, primaryVariantId));
    JsonNode noIdentity = body(assertStatus(anonymous.post(path, json(exposed)), 200));
    assertThat(noIdentity.get("accepted").asInt()).isZero();
    JsonNode unknownVisitor = body(assertStatus(anonymous.post(
      path, json(exposed).setHeader("X-Visitor-Id", "unknown-" + UUID.randomUUID())
    ), 200));
    assertThat(unknownVisitor.get("accepted").asInt()).isZero();

    assertError(anonymous.post(
      path, json(trackingRequest("assigned", Map.of(experimentId, primaryVariantId)))
    ), 400, "invalid_request");
    assertError(anonymous.post(
      path, json(trackingRequest("exposed", Map.of()))
    ), 400, "invalid_request");
    assertError(anonymous.post(
      path, json(trackingRequest("exposed", oversizedTrackingData()))
    ), 400, "invalid_request");
    assertError(anonymous.post(
      path, json(exposed).setHeader("X-Anon-Id", "invalid")
    ), 400, "invalid_request");
    assertError(anonymous.post(
      "/missing/v1/track", json(exposed).setHeader("X-Anon-Id", String.valueOf(primaryAnonId))
    ), 404, "not_found");

    RequestOptions primary = json(exposed).setHeader("X-Anon-Id", String.valueOf(primaryAnonId));
    assertAccepted(anonymous.post(path, primary), 1);
    assertAccepted(anonymous.post(path, primary), 1);
    assertAccepted(anonymous.post(
      path, json(trackingRequest("converted", Map.of(experimentId, primaryVariantId)))
        .setHeader("X-Anon-Id", String.valueOf(primaryAnonId))
    ), 1);
    expectedExposed.merge(primaryVariantId, 1, Integer::sum);
    expectedConverted.merge(primaryVariantId, 1, Integer::sum);

    assertAccepted(anonymous.post(
      path, json(trackingRequest("exposed", Map.of(experimentId, linkedVariantId)))
        .setHeader("X-Visitor-Id", visitorId)
    ), 1);
    expectedExposed.merge(linkedVariantId, 1, Integer::sum);

    assertAccepted(anonymous.post(
      path, json(trackingRequest("converted", Map.of(experimentId, primaryVariantId)))
        .setHeader("X-Anon-Id", String.valueOf(Long.MAX_VALUE - 1))
    ), 1);
  }

  @Test
  @Order(5)
  void coversEventuallyConsistentResultsApiBackedByRabbitEvents() throws InterruptedException {
    assertError(anonymous.get(experimentPath() + "/results"), 401, "unauthorized");
    assertError(authenticated.get(
      experimentsPath() + "/9223372036854775807/results"
    ), 404, "not_found");

    Thread.sleep(INITIAL_EVENT_DELAY.toMillis());
    JsonNode results = awaitResults();
    assertThat(results.get("experiment_id").asLong()).isEqualTo(experimentId);
    assertThat(results.get("orphan_converted").asLong()).isEqualTo(1);
    for (JsonNode variant : results.get("variants")) {
      Long variantId = variant.get("variant_id").asLong();
      int assigned = expectedAssigned.getOrDefault(variantId, 0);
      int exposed = expectedExposed.getOrDefault(variantId, 0);
      int converted = expectedConverted.getOrDefault(variantId, 0);
      assertThat(variant.get("assigned").asInt()).isEqualTo(assigned);
      assertThat(variant.get("exposed").asInt()).isEqualTo(exposed);
      assertThat(variant.get("converted").asInt()).isEqualTo(converted);
      assertThat(new BigDecimal(variant.get("conversion_rate").asString()))
        .isEqualByComparingTo(rate(converted, exposed));
    }
  }

  @Test
  @Order(6)
  void coversTrackingUnavailableAndBrokerRecovery() throws Exception {
    String path = "/" + TENANT + "/v1/track";
    Map<String, Object> event = trackingRequest("exposed", Map.of(experimentId, primaryVariantId));
    dockerCompose("stop", "rabbitmq");
    try {
      assertError(anonymous.post(
        path, json(event).setHeader("X-Anon-Id", String.valueOf(primaryAnonId))
      ), 503, "tracking_unavailable");
    } finally {
      dockerCompose("start", "rabbitmq");
      awaitRabbitHealth();
    }
    awaitTrackingRecovery(path, event);
  }

  @Test
  @Order(7)
  void coversPostTrafficWarningAndDeleteApi() {
    JsonNode updated = body(assertStatus(authenticated.put(
      experimentPath(), json(updateRequest(55, 45, firstVariantId, secondVariantId))
    ), 200));
    assertThat(updated.get("variants").get(0).get("id").asLong()).isEqualTo(firstVariantId);
    assertThat(updated.get("variants").get(1).get("id").asLong()).isEqualTo(secondVariantId);
    assertThat(updated.get("analytics_warning").asString()).contains("may skew analytics");

    JsonNode deleted = body(assertStatus(authenticated.delete(experimentPath()), 200));
    assertThat(deleted.get("experiment_id").asLong()).isEqualTo(experimentId);
    assertThat(deleted.get("deleted").asBoolean()).isTrue();
    assertError(authenticated.get(experimentPath()), 404, "not_found");
    assertError(authenticated.get(experimentPath() + "/results"), 404, "not_found");
    assertError(authenticated.delete(experimentPath()), 404, "not_found");
    JsonNode page = body(assertStatus(authenticated.get(experimentsPath()), 200));
    assertThat(containsExperiment(page.get("items"), experimentId)).isFalse();
    experimentId = null;
  }

  private APIRequestContext context(Map<String, String> headers) {
    return playwright.request().newContext(new APIRequest.NewContextOptions()
      .setBaseURL("http://localhost:" + port)
      .setExtraHTTPHeaders(headers)
      .setMaxRedirects(0)
      .setTimeout(15_000));
  }

  private RequestOptions json(Map<String, ?> data) {
    return RequestOptions.create().setData(data);
  }

  private RequestOptions headers(String name, String value) {
    return RequestOptions.create().setHeader(name, value);
  }

  private APIResponse assertStatus(APIResponse response, int status) {
    assertThat(response.status()).as(response.text()).isEqualTo(status);
    return response;
  }

  private void assertError(APIResponse response, int status, String code) {
    JsonNode error = body(assertStatus(response, status));
    assertThat(error.get("code").asString()).isEqualTo(code);
    assertThat(error.get("message").asString()).isNotBlank();
  }

  private void assertAccepted(APIResponse response, int accepted) {
    assertThat(body(assertStatus(response, 200)).get("accepted").asInt()).isEqualTo(accepted);
  }

  private JsonNode body(APIResponse response) {
    try {
      return objectMapper.readTree(response.text());
    } catch (Exception exception) {
      throw new AssertionError("Response was not valid JSON: " + response.text(), exception);
    }
  }

  private JsonNode awaitResults() throws InterruptedException {
    long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
    JsonNode latest = null;
    do {
      latest = body(assertStatus(authenticated.get(experimentPath() + "/results"), 200));
      if (matchesExpectedEvents(latest)) {
        return latest;
      }
      Thread.sleep(EVENT_POLL_DELAY.toMillis());
    } while (System.nanoTime() < deadline);
    throw new AssertionError("RabbitMQ events did not reach results before timeout: " + latest);
  }

  private void awaitTrackingRecovery(String path, Map<String, Object> event) throws InterruptedException {
    long deadline = System.nanoTime() + BROKER_RECOVERY_TIMEOUT.toNanos();
    APIResponse latest;
    do {
      latest = anonymous.post(
        path, json(event).setHeader("X-Anon-Id", String.valueOf(primaryAnonId))
      );
      if (latest.status() == 200) {
        assertAccepted(latest, 1);
        return;
      }
      assertError(latest, 503, "tracking_unavailable");
      Thread.sleep(EVENT_POLL_DELAY.toMillis());
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Tracking did not recover after RabbitMQ restarted");
  }

  private boolean matchesExpectedEvents(JsonNode results) {
    if (results.get("orphan_converted").asLong() != 1) {
      return false;
    }
    int assigned = 0;
    int exposed = 0;
    int converted = 0;
    for (JsonNode variant : results.get("variants")) {
      assigned += variant.get("assigned").asInt();
      exposed += variant.get("exposed").asInt();
      converted += variant.get("converted").asInt();
    }
    return assigned == expectedAssigned.values().stream().mapToInt(Integer::intValue).sum()
      && exposed == expectedExposed.values().stream().mapToInt(Integer::intValue).sum()
      && converted == expectedConverted.values().stream().mapToInt(Integer::intValue).sum();
  }

  private Map<String, Object> experimentRequest(
    String name, String strategy, int firstAllocation, int secondAllocation,
    boolean bothDefault, Long suppliedId
  ) {
    return Map.of(
      "name", name,
      "strategy", strategy,
      "variants", List.of(
        variant(suppliedId, "control", firstAllocation, true),
        variant(null, "treatment", secondAllocation, bothDefault)
      )
    );
  }

  private Map<String, Object> updateRequest(
    int firstAllocation, int secondAllocation, Long firstId, Long secondId
  ) {
    return Map.of(
      "name", experimentName,
      "strategy", "hash",
      "variants", List.of(
        variant(firstId, "control-updated", firstAllocation, true),
        variant(secondId, "treatment-updated", secondAllocation, false)
      )
    );
  }

  private Map<String, Object> variant(
    Long id, String content, int allocation, boolean defaultVariant
  ) {
    Map<String, Object> variant = new LinkedHashMap<>();
    if (id != null) {
      variant.put("id", id);
    }
    variant.put("content", content);
    variant.put("alloc_pct", allocation);
    variant.put("is_default", defaultVariant);
    return variant;
  }

  private Map<String, Object> trackingRequest(String status, Map<Long, Long> data) {
    return Map.of("status", status, "data", data);
  }

  private Map<Long, Long> oversizedTrackingData() {
    Map<Long, Long> data = new LinkedHashMap<>();
    for (long id = 1; id <= 21; id++) {
      data.put(id, firstVariantId);
    }
    return data;
  }

  private String oversizedExperimentQuery() {
    List<String> params = new ArrayList<>();
    for (int id = 1; id <= 21; id++) {
      params.add("experiments=" + id);
    }
    return "?" + String.join("&", params);
  }

  private boolean containsExperiment(JsonNode items, Long id) {
    for (JsonNode item : items) {
      if (item.get("id").asLong() == id) {
        return true;
      }
    }
    return false;
  }

  private BigDecimal rate(int converted, int exposed) {
    if (exposed == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(converted).divide(BigDecimal.valueOf(exposed), 4, RoundingMode.HALF_UP);
  }

  private void dockerCompose(String... operation) throws Exception {
    List<String> command = new ArrayList<>(List.of(
      "docker", "compose", "--env-file", envFile().toString(), "-f", composeFile().toString()
    ));
    command.addAll(List.of(operation));
    run(command);
  }

  private void awaitRabbitHealth() throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    do {
      String status = run(List.of(
        "docker", "inspect", "--format={{.State.Health.Status}}", "demo-rabbitmq"
      )).trim();
      if ("healthy".equals(status)) {
        return;
      }
      Thread.sleep(500);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("RabbitMQ did not become healthy after restart");
  }

  private String run(List<String> command) throws Exception {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Command timed out: " + command);
    }
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.exitValue()).as(output).isZero();
    return output;
  }

  private Path composeFile() {
    Path fromModule = Path.of("..", "infra", "docker-compose.yml").toAbsolutePath().normalize();
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    return Path.of("infra", "docker-compose.yml").toAbsolutePath().normalize();
  }

  private Path envFile() {
    return composeFile().getParent().getParent().resolve(".env");
  }

  private String experimentsPath() {
    return "/" + TENANT + "/v1/experiments";
  }

  private String experimentPath() {
    return experimentsPath() + "/" + experimentId;
  }
}
