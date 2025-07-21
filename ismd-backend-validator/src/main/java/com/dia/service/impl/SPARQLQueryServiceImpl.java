package com.dia.service.impl;

import com.dia.exceptions.ValidationException;
import com.dia.service.SPARQLQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

@Service
@RequiredArgsConstructor
@Slf4j
public class SPARQLQueryServiceImpl implements SPARQLQueryService {

    @Value("${validation.global.sparql-endpoint}")
    private String sparqlEndpoint;

    @Value("${validation.global.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${validation.global.retry.delay-ms}")
    private long retryDelayMs;

    @Override
    public boolean executeAsk(String sparqlQuery) throws ValidationException {
        return executeWithRetry(() -> {
            Query query = QueryFactory.create(sparqlQuery);

            try (QueryExecution qexec = QueryExecution.service(sparqlEndpoint, query)) {
                boolean result = qexec.execAsk();
                log.debug("ASK query executed successfully. Result: {}", result);
                return result;
            }
        });
    }

    @Override
    public ResultSet executeSelect(String sparqlQuery) throws ValidationException {
        return executeWithRetry(() -> {
            Query query = QueryFactory.create(sparqlQuery);

            try (QueryExecution qexec = QueryExecution.service(sparqlEndpoint, query)) {
                ResultSet results = qexec.execSelect();
                ResultSet materializedResults = ResultSetFactory.copyResults(results);
                log.debug("SELECT query executed successfully");
                return materializedResults;
            }
        });
    }

    @Override
    public Model executeConstruct(String sparqlQuery) throws ValidationException {
        return executeWithRetry(() -> {
            Query query = QueryFactory.create(sparqlQuery);

            try (QueryExecution qexec = QueryExecution.service(sparqlEndpoint, query)) {
                Model result = qexec.execConstruct();
                log.debug("CONSTRUCT query executed successfully. Model size: {}", result.size());
                return result;
            }
        });
    }

    @Override
    public boolean testConnectivity() {
        try {
            String testQuery = "ASK { ?s ?p ?o } LIMIT 1";
            executeAsk(testQuery);
            log.debug("SPARQL endpoint connectivity test successful");
            return true;
        } catch (Exception e) {
            log.debug("SPARQL endpoint connectivity test failed: {}", e.getMessage());
            return false;
        }
    }

    private <T> T executeWithRetry(Callable<T> operation) throws ValidationException {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;

                if (attempt == maxRetryAttempts) {
                    log.error("SPARQL query failed after {} attempts", attempt, e);
                    break;
                }

                long delayMs = retryDelayMs * attempt;
                log.warn("SPARQL query attempt {} failed, retrying in {}ms: {}",
                        attempt, delayMs, e.getMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ValidationException("Query execution interrupted", ie);
                }
            }
        }

        throw new ValidationException("SPARQL query failed after " + maxRetryAttempts + " attempts", lastException);
    }

    public String getEndpointUrl() {
        return sparqlEndpoint;
    }
}
