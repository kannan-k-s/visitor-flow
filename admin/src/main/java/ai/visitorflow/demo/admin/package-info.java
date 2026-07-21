/**
 * Admin module — the control plane. Authenticated (OAuth + RBAC), consistent.
 *
 * <p>Hosts experiment configuration, the results dashboard, and RBAC, with the
 * control-plane request filter. Controllers/services/DTOs/mappers live here per
 * feature (e.g. {@code admin.experiment}, {@code admin.results}); entities and
 * repositories come from {@code data}. Depends on {@code data} only.
 */
package ai.visitorflow.demo.admin;
