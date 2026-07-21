/**
 * Data module — shared persistence and domain for both planes.
 *
 * <p>Holds JPA entities (plain {@code Long} FKs, R8), tenant-scoped repositories
 * (R9), the request context, and MySQL/Redis infrastructure config. Per
 * feature, entities live in {@code data.<feature>.model} and repositories in
 * {@code data.<feature>.repository}; the plane modules ({@code visitor},
 * {@code admin}) depend on them. This module depends on neither plane.
 */
package ai.visitorflow.demo.data;
