package com.dia.config.cors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the per-profile CORS configuration.
 *
 * <p>Exact origins always come from {@code cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS},
 * comma-separated) so that each deployed environment sets its own frontend origin without a rebuild.
 * Server-to-server callers (ismd-tool-backend) are not subject to CORS and need no entry here.
 */
@Slf4j
public class CorsConfigUtil {

    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");

    /** Response headers browser JS must be able to read (CSV download filename, request correlation). */
    private static final List<String> EXPOSED_HEADERS = List.of("Content-Disposition", "X-Request-Id");

    /** Localhost only: a deployed stage frontend must be listed explicitly in {@code cors.allowed-origins}. */
    private static final List<String> STAGE_PATTERNS = List.of(
            "http://localhost:[*]",
            "https://localhost:[*]"
    );

    private static final List<String> DEV_PATTERNS = List.of(
            // access from localhost
            "http://localhost:[*]",
            "https://localhost:[*]",

            // access from localhost - mobile device on same network
            "http://192.168.*.*",
            "https://192.168.*.*",

            // tunnel from localhost: https://ngrok.com
            "https://*.ngrok-free.app",

            // tunnel from localhost
            // via cloudflare https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/get-started/create-remote-tunnel/
            "https://*.domain.org",

            // Docker container/service names (aligned in docker-compose.yml)
            "http://ismd-validator-frontend:[*]",
            "http://ismd-validator-backend:[*]",

            // Docker host.docker.internal (for standalone Docker containers)
            "http://host.docker.internal:[*]",
            "https://host.docker.internal:[*]"
    );

    private final List<String> allowedOrigins;

    public CorsConfigUtil(String allowedOrigins) {
        this.allowedOrigins = parseOrigins(allowedOrigins);
    }

    static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public CorsConfiguration createProductionCorsConfiguration() {
        if (allowedOrigins.isEmpty()) {
            log.warn("cors.allowed-origins (CORS_ALLOWED_ORIGINS) is empty in production: "
                    + "all cross-origin browser requests will be rejected");
        } else {
            log.info("CORS allowed origins (production): {}", allowedOrigins);
        }
        var config = base();
        config.setAllowedOrigins(allowedOrigins);
        config.setMaxAge(3600L);
        return config;
    }

    public CorsConfiguration createStageCorsConfiguration() {
        log.info("CORS allowed origins (stage): {}", allowedOrigins);
        var config = base();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedOriginPatterns(STAGE_PATTERNS);
        config.setMaxAge(3600L);
        return config;
    }

    public CorsConfiguration createLocalCorsConfiguration() {
        log.info("CORS allowed origins (local): {}", allowedOrigins);
        var config = base();
        config.setAllowedOrigins(allowedOrigins);
        // Always include development patterns for local environment
        config.setAllowedOriginPatterns(DEV_PATTERNS);
        config.setMaxAge(600L);
        return config;
    }

    private static CorsConfiguration base() {
        var config = new CorsConfiguration();
        config.setAllowedMethods(METHODS);
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(EXPOSED_HEADERS);
        config.setAllowCredentials(true);
        return config;
    }
}
