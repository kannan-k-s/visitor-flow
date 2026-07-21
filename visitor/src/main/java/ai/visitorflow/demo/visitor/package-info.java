/**
 * Visitor module — the data plane. Anonymous, latency-critical, fail-safe.
 *
 * <p>Hosts {@code /{tenant}/v1/assign} and {@code /{tenant}/v1/track}: assignment
 * strategies (hash | swrr), identity resolution, tracking emit, rate limiting, and
 * the data-plane request filter. Controllers/services/DTOs/mappers live here per
 * feature (e.g. {@code visitor.assignment}, {@code visitor.tracking}); entities and
 * repositories come from {@code data}. Depends on {@code data} only.
 */
package ai.visitorflow.demo.visitor;
