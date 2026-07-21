---
name: feature-slice
description: Scaffold a new versioned API feature (controller → service → DTOs → mapper → repository → entity) for this Spring Boot experimentation service, pre-wired to obey all 11 project rules and the code style in AGENTS.md. Use when adding a new resource/endpoint or a vertical slice.
---

# Feature slice scaffold

Generate a complete vertical slice for one resource under `ai.visitorflow.demo.<feature>`,
following **every** rule in `AGENTS.md` §2 and the code style in §5 (2-space indent,
K&R braces). Read `AGENTS.md` first if not already loaded.

## Before generating
Confirm (ask only if ambiguous):
- **Feature/module name** (e.g. `experiment`, `tracking`) and **resource** (e.g. `Experiment`).
- **Plane**: control plane (OAuth/RBAC) or data plane (API-key, fail-safe). Data-plane
  endpoints must degrade to a safe default, never error on the render path (`design.md` §7.7).
- **Operations** needed (create/get/list/patch/lifecycle…).
- That the shared `ModelMapper` bean exists (`common/config/MappingConfig`). If not, create it
  once (see the snippet in `AGENTS.md` R11).

## Files to create (per resource `Xxx` in feature `feat`)

```
feat/controller/v1/XxxController.java
feat/service/XxxService.java               (public interface — R10)
feat/service/XxxServiceImpl.java           (package-protected impl — R10)
feat/dto/request/CreateXxxRequest.java     (+ other requests as needed)
feat/dto/response/XxxResponse.java
feat/mapper/XxxMapper.java                 (public interface — R10)
feat/mapper/XxxMapperImpl.java             (package-protected impl, delegates to ModelMapper — R10/R11)
feat/repository/XxxRepository.java
feat/model/XxxEntity.java
```
Add a Liquibase changelog for any new table (never `ddl-auto`).

## Templates (adapt names/fields; keep the annotations exactly; 2-space indent)

**Controller** — versioned (R1), one service call returning a DTO, no ambient params (R2, R6):
```java
package ai.visitorflow.demo.feat.controller.v1;

@RestController
@RequestMapping("/v1/xxxs")
@RequiredArgsConstructor                 // R4
public class XxxController {
  private final XxxService xxxService;

  @PostMapping
  public XxxResponse create(@Valid @RequestBody CreateXxxRequest request) {
    return xxxService.create(request);          // R2: one call, returns DTO
  }

  @GetMapping("/{id}")
  public XxxResponse get(@PathVariable Long id) {  // no tenantId param — R6
    return xxxService.get(id);
  }
}
```

**Service** — public interface + package-protected impl (R10), constructor injection (R4),
reads context (R7), returns DTO (R2), passes tenantId only to repo (R9):
```java
// XxxService.java — public contract (R10)
package ai.visitorflow.demo.feat.service;

public interface XxxService {
  XxxResponse create(CreateXxxRequest request);
  XxxResponse get(Long id);
}
```
```java
// XxxServiceImpl.java — package-protected impl, SAME package (R10)
package ai.visitorflow.demo.feat.service;

@Service                                       // no `public` on the class — R10
@RequiredArgsConstructor                       // R4
class XxxServiceImpl implements XxxService {
  private final XxxRepository xxxRepository;
  private final XxxMapper xxxMapper;

  @Override
  public XxxResponse create(CreateXxxRequest request) {
    Long tenantId = RequestContextHolder.tenantId();     // R7: read from context
    XxxEntity saved = xxxRepository.save(xxxMapper.toEntity(request, tenantId));
    return xxxMapper.toResponse(saved);                  // R2: return DTO
  }

  @Override
  public XxxResponse get(Long id) {
    Long tenantId = RequestContextHolder.tenantId();
    return xxxRepository.findByIdAndTenantId(id, tenantId)   // R9
      .map(xxxMapper::toResponse)
      .orElseThrow(() -> new NotFoundException("xxx", id));
  }
}
```

**Request DTO** — `@Builder @Getter` (R3), `@JsonProperty` snake_case (R5), no ambient fields (R6):
```java
package ai.visitorflow.demo.feat.dto.request;

@Getter
@Builder
@Jacksonized                                    // Builder + Jackson deserialization
public class CreateXxxRequest {
  @JsonProperty("display_name")                 // R5
  @NotBlank
  private String displayName;

  @JsonProperty("allocation")
  private Map<String, Integer> allocation;
  // NO tenant_id / user_id here — R6
}
```

**Response DTO** — `@Builder @Getter` (R3), every field `@JsonProperty` snake_case (R5),
non-`final` private fields + no-arg ctor so ModelMapper can populate it (R11):
```java
package ai.visitorflow.demo.feat.dto.response;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)    // R11: ModelMapper instantiation
@AllArgsConstructor(access = AccessLevel.PRIVATE)   // required by @Builder alongside @NoArgsConstructor
public class XxxResponse {
  @JsonProperty("id")            private Long id;
  @JsonProperty("display_name")  private String displayName;
  @JsonProperty("state")         private String state;
}
```

**Mapper** — public interface + package-protected impl (R10), delegates to the shared
`ModelMapper` bean (R11); invoked from the service, never from the model (R2/R8):
```java
// XxxMapper.java — public contract (R10)
package ai.visitorflow.demo.feat.mapper;

public interface XxxMapper {
  XxxEntity toEntity(CreateXxxRequest req, Long tenantId);
  XxxResponse toResponse(XxxEntity e);
}
```
```java
// XxxMapperImpl.java — package-protected impl (R10), uses ModelMapper (R11)
package ai.visitorflow.demo.feat.mapper;

@Component                                     // no `public` on the class — R10
@RequiredArgsConstructor                       // R4
class XxxMapperImpl implements XxxMapper {
  private final ModelMapper modelMapper;

  @Override
  public XxxEntity toEntity(CreateXxxRequest req, Long tenantId) {
    XxxEntity entity = XxxEntity.builder().tenantId(tenantId).build();  // context value via builder (R3)
    modelMapper.map(req, entity);   // R11: fill business fields into the existing instance
    return entity;
  }

  @Override
  public XxxResponse toResponse(XxxEntity e) {
    return modelMapper.map(e, XxxResponse.class);
  }
}
```
> Seeding `tenantId` through the builder and then calling `map(source, destination)` keeps
> the context value out of the request (R6) with **no setter** (R3). For renames or computed
> fields, register a `TypeMap`/`Converter` in a `@PostConstruct` here instead of hand-mapping —
> e.g. `modelMapper.typeMap(CreateXxxRequest.class, XxxEntity.class).addMappings(m -> m.map(…))`.

**Repository** — every method takes `tenantId` (R9):
```java
package ai.visitorflow.demo.feat.repository;

public interface XxxRepository extends JpaRepository<XxxEntity, Long> {
  Optional<XxxEntity> findByIdAndTenantId(Long id, Long tenantId);
  List<XxxEntity> findAllByTenantId(Long tenantId);
  // Do NOT expose findById(Long) without tenantId.
}
```

**Entity** — `@Builder @Getter` (R3), plain `Long` FKs, no associations (R8), non-`final`
fields + no-arg ctor for JPA/ModelMapper (R11):
```java
package ai.visitorflow.demo.feat.model;

@Entity
@Table(name = "xxxs", indexes = @Index(name = "ix_xxx_tenant", columnList = "tenant_id"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA + ModelMapper need it
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class XxxEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;                 // R9 scope; plain Long

  @Column(name = "experiment_id")
  private Long experimentId;             // R8: FK as Long, not @ManyToOne

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "state")
  private String state;
}
```

## After generating
- Composite index leading with `tenant_id` for tenant-scoped tables.
- Register `TypeMap`s for any non-trivial mapping instead of hand-mapping (R11).
- Add/extend a `@ControllerAdvice` so `NotFoundException` etc. map to snake_case error DTOs.
- Run `mvnw.cmd clean verify`.
- Self-check against the Definition of Done in `AGENTS.md` §6 (or run `/convention-check`).
```
