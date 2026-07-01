package com.dia.validation.global;

import com.dia.validation.config.GlobalValidationConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level tests for {@link CorpusSparqlClient} against an in-process
 * {@link HttpServer} stub (no WireMock dependency needed). Verifies the same-IRI hit path,
 * the no-hit path, and — critically — that a 500, a timeout, and an unset endpoint all
 * degrade to "no hits" (fail-open) rather than throwing.
 */
class CorpusSparqlClientTest {

    private static final String POJEM = CorpusSparqlClient.TYPE_POJEM;
    private static final String IRI_A = "https://slovník.gov.cz/a";
    private static final String IRI_B = "https://slovník.gov.cz/b";

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status, String body, long delayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sparql", exchange -> {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/sparql-results+json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/sparql";
    }

    private CorpusSparqlClient clientFor(String endpoint, int timeoutMs) {
        GlobalValidationConfiguration cfg = new GlobalValidationConfiguration();
        cfg.getSparql().setEndpoint(endpoint);
        cfg.getSparql().setTimeout(timeoutMs);
        cfg.getCircuitBreaker().setFailureThreshold(5);
        cfg.getCircuitBreaker().setCooldownMs(30_000);
        return new CorpusSparqlClient(cfg);
    }

    private static String selectJson(String var, String... uris) {
        StringBuilder bindings = new StringBuilder();
        for (int i = 0; i < uris.length; i++) {
            if (i > 0) bindings.append(",");
            bindings.append("{\"").append(var).append("\":{\"type\":\"uri\",\"value\":\"")
                    .append(uris[i]).append("\"}}");
        }
        return "{\"head\":{\"vars\":[\"" + var + "\"]},\"results\":{\"bindings\":[" + bindings + "]}}";
    }

    @Test
    void sameIri_returnsCorpusMatches() throws IOException {
        String endpoint = startServer(200, selectJson("s", IRI_A), 0);
        CorpusSparqlClient client = clientFor(endpoint, 2000);

        Set<String> found = client.findExistingIris(List.of(IRI_A, IRI_B), POJEM);

        assertThat(found).containsExactly(IRI_A);
    }

    @Test
    void sameIri_noMatch_returnsEmpty() throws IOException {
        String endpoint = startServer(200, selectJson("s"), 0);
        CorpusSparqlClient client = clientFor(endpoint, 2000);

        assertThat(client.findExistingIris(List.of(IRI_A), POJEM)).isEmpty();
    }

    @Test
    void serverError_failsOpenToEmpty() throws IOException {
        String endpoint = startServer(500, "boom", 0);
        CorpusSparqlClient client = clientFor(endpoint, 2000);

        assertThat(client.findExistingIris(List.of(IRI_A), POJEM)).isEmpty();
    }

    @Test
    void timeout_failsOpenToEmpty() throws IOException {
        String endpoint = startServer(200, selectJson("s", IRI_A), 1500);
        CorpusSparqlClient client = clientFor(endpoint, 200); // 200ms timeout < 1500ms delay

        assertThat(client.findExistingIris(List.of(IRI_A), POJEM)).isEmpty();
    }

    @Test
    void unconfiguredEndpoint_isNotConfigured_andSkips() {
        CorpusSparqlClient client = clientFor("", 2000);

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.findExistingIris(List.of(IRI_A), POJEM)).isEmpty();
    }

    @Test
    void unsafeIris_areFilteredOutBeforeQuery() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sparql", exchange -> {
            hits.incrementAndGet();
            byte[] b = selectJson("s").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, b.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sparql";
        CorpusSparqlClient client = clientFor(endpoint, 2000);

        // Only unsafe IRIs -> no query issued at all.
        Set<String> found = client.findExistingIris(List.of("not a uri", "ftp://x"), POJEM);

        assertThat(found).isEmpty();
        assertThat(hits.get()).isZero();
    }
}
