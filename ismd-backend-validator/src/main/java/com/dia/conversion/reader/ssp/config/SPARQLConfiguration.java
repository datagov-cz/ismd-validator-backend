package com.dia.conversion.reader.ssp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "conversion.ssp")
public class SPARQLConfiguration {

    private String sparqlEndpoint = "https://slovník.gov.cz/sparql";
    private int timeoutMs = 30000;
    private int maxRetries = 3;
    private boolean useCompression = true;
}
