package com.ecom.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Random;
import java.util.logging.Logger;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Catalog Resource - Endpoint principal del servicio de catálogo.
 * Punto de partida del laboratorio. Simula consultas a una base de datos con
 * latencia variable y fallos aleatorios para que el alumno implemente los
 * patrones de tolerancia a fallos durante la Unidad 1.
 */
@Path("/v1/products")
public class CatalogResource {

    private static final Logger LOG = Logger.getLogger(CatalogResource.class.getName());
    private final Random random = new Random();

    /**
     * Rastrea el número de intento de cada solicitud HTTP durante los reintentos.
     */
    private static final ThreadLocal<Integer> ATTEMPT_COUNT = ThreadLocal.withInitial(() -> 0);

    /**
     * Conserva la decisión de fallo durante todos los intentos de una solicitud.
     */
    private static final ThreadLocal<Boolean> SHOULD_FAIL_FIRST_ATTEMPTS = ThreadLocal.withInitial(() -> false);

    /**
     * Endpoint principal que retorna el catálogo de productos.
     * 
     * Simulación de escenarios:
     * - 20% de probabilidad de latencia alta (>3000ms)
     * - 30% de probabilidad de falla de conexión a base de datos
     * - 50% de probabilidad de respuesta exitosa normal
     * 
     * @return Response con el catálogo de productos o datos degradados del fallback
     * @throws InterruptedException si el thread es interrumpido durante la
     *                              simulación de latencia
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(800)
    @Retry(maxRetries = 2, delay = 150)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.7, delay = 5000)
    @Fallback(fallbackMethod = "getCatalogFallback")
    public Response getProducts() throws InterruptedException {
        try {
            ATTEMPT_COUNT.set(ATTEMPT_COUNT.get() + 1);
            int currentAttempt = ATTEMPT_COUNT.get();

            if (currentAttempt == 1) {
                LOG.info("════════════════════════════════════════════════════════════");
            }

            LOG.info("[Intento " + currentAttempt + "/3] Consultando catálogo de productos...");

            if (currentAttempt == 1) {
                SHOULD_FAIL_FIRST_ATTEMPTS.set(random.nextInt(10) < 6);
            }

            boolean shouldFail = SHOULD_FAIL_FIRST_ATTEMPTS.get();

            if (shouldFail && currentAttempt <= 3) {
                if (currentAttempt == 1) {
                    LOG.severe("[Intento " + currentAttempt + "/3] Falla de conexión a la base de datos primaria.");
                    throw new RuntimeException("Database connection timeout");
                } else if (currentAttempt == 2) {
                    LOG.warning("[Intento " + currentAttempt + "/3] Simulando latencia alta en consulta de catálogo...");
                    Thread.sleep(3000);
                } else if (currentAttempt == 3) {
                    LOG.severe("[Intento " + currentAttempt + "/3] Falla de conexión después de reintentos.");
                    throw new RuntimeException("Database connection timeout after retries");
                }
            }

            LOG.info("[Intento " + currentAttempt + "/3] Catálogo retornado exitosamente desde la base de datos primaria.");
            ATTEMPT_COUNT.set(0);
            SHOULD_FAIL_FIRST_ATTEMPTS.set(false);
            return Response.ok("{\"status\":\"SUCCESS\",\"data\":[\"Product A\",\"Product B\",\"Product C\"]}").build();
        } finally {
            // El fallback necesita leer el contador antes de reiniciarlo.
        }
    }

    /**
     * Entrega una respuesta degradada cuando se agotan los intentos, vence el
     * timeout o el circuito está abierto.
     */
    public Response getCatalogFallback() {
        int failedAttempts = ATTEMPT_COUNT.get();
        LOG.info("Fallback activado: Se agotaron todos los " + failedAttempts
                + " intentos. Sirviendo datos desde caché.");
        ATTEMPT_COUNT.set(0);
        SHOULD_FAIL_FIRST_ATTEMPTS.set(false);
        return Response.ok("{\"status\":\"DEGRADED_CACHE\",\"data\":[\"Cached Product A\",\"Cached Product B\"]}").build();
    }

    /**
     * Endpoint de prueba simple para verificar que el servicio está respondiendo.
     * No tiene patrones de resiliencia configurados.
     * 
     * @return Response con mensaje de bienvenida
     */
    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Catalog Service v1.0.0 - Unidad 1 - Alta Disponibilidad";
    }
}
