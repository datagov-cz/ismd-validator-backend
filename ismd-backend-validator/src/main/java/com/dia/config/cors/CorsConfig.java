package com.dia.config.cors;

import com.dia.config.DomainApplicationProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@Slf4j
public class CorsConfig {

    private final Environment env;
    private final String allowedOrigins;

    public CorsConfig(Environment env, @Value("${cors.allowed-origins:}") String allowedOrigins) {
        this.env = env;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsFilter corsFilter() {
        String activeProfile = DomainApplicationProfile.getProfile(env);
        log.info("Configuring CORS for profile: {}", activeProfile);

        var util = new CorsConfigUtil(allowedOrigins);
        CorsConfiguration config;
        if (DomainApplicationProfile.isActive(env, DomainApplicationProfile.PRODUCTION)) {
            config = util.createProductionCorsConfiguration();
        } else if (DomainApplicationProfile.isActive(env, DomainApplicationProfile.STAGE)) {
            config = util.createStageCorsConfiguration();
        } else {
            config = util.createLocalCorsConfiguration();
        }

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
