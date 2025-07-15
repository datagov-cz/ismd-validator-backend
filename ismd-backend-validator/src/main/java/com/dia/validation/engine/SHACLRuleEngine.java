package com.dia.validation.engine;

import com.dia.exceptions.ValidationException;
import com.dia.validation.config.RuleManager;
import com.dia.validation.config.ValidationConfiguration;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.validation.data.ValidationResult;
import com.dia.validation.data.ValidationSeverity;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.validation.Severity;
import org.apache.jena.sparql.path.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
@Slf4j
public class SHACLRuleEngine {

    private final ValidationConfiguration config;
    private final RuleManager ruleManager;
    private final ExecutorService executorService;

    @Autowired
    public SHACLRuleEngine(ValidationConfiguration config, RuleManager ruleManager) {
        this.config = config;
        this.ruleManager = ruleManager;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    /**
     * Validate data model against enabled SHACL rules
     */
    public ISMDValidationReport validate(Model dataModel) {
        log.info("Starting SHACL validation with {} enabled rules",
                ruleManager.getEnabledRuleNames().size());

        Instant startTime = Instant.now();

        try {
            Model shapesModel = ruleManager.getCombinedEnabledRules();

            if (shapesModel.isEmpty()) {
                log.warn("No enabled validation rules found");
                return ISMDValidationReport.empty();
            }

            ISMDValidationReport report = executeValidationWithTimeout(dataModel, shapesModel);

            Instant endTime = Instant.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            log.info("SHACL validation completed in {}ms. Results: {} errors, {} warnings, {} info",
                    durationMs,
                    report.getErrorCount(),
                    report.getWarningCount(),
                    report.getInfoCount());

            return report;

        } catch (Exception e) {
            log.error("SHACL validation failed", e);
            return ISMDValidationReport.error("Validation execution failed: " + e.getMessage());
        }
    }

    /**
     * Execute validation with timeout
     */
    private ISMDValidationReport executeValidationWithTimeout(Model dataModel, Model shapesModel)
            throws ValidationException {

        Future<ISMDValidationReport> future = executorService.submit(() -> {
            try {
                return executeValidation(dataModel, shapesModel);
            } catch (Exception e) {
                throw new RuntimeException("Validation execution failed", e);
            }
        });

        try {
            return future.get(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ValidationException("Validation timed out after " + config.getTimeoutMs() + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("Validation was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new ValidationException("Validation execution failed", cause);
        }
    }

    /**
     * Core SHACL validation execution using Shapes.parse approach
     */
    private ISMDValidationReport executeValidation(Model dataModel, Model shapesModel) {
        try {
            log.debug("Executing SHACL validation. Data model: {} triples, Shapes model: {} triples",
                    dataModel.size(), shapesModel.size());

            Graph dataGraph = dataModel.getGraph();
            Graph shapesGraph = shapesModel.getGraph();

            org.apache.jena.shacl.Shapes shapes = org.apache.jena.shacl.Shapes.parse(shapesGraph);

            ValidationReport jenaReport = ShaclValidator.get().validate(shapes, dataGraph);

            return convertValidationReport(jenaReport);

        } catch (Exception e) {
            log.error("Error during SHACL validation execution", e);
            throw new ValidationException("SHACL validation execution failed", e);
        }
    }

    /**
     * Convert Jena ValidationReport to custom format
     */
    private ISMDValidationReport convertValidationReport(ValidationReport jenaReport) {
        List<ValidationResult> results = new ArrayList<>();

        try {
            jenaReport.getEntries().forEach(entry -> {
                try {
                    ValidationResult result = convertValidationEntry(entry);
                    results.add(result);
                } catch (Exception e) {
                    log.warn("Failed to convert validation entry: {}", e.getMessage());
                    log.debug("Entry details: {}", entry.toString());
                }
            });
        } catch (Exception e) {
            log.error("Failed to get validation entries", e);
            return handleValidationReportAlternative(jenaReport);
        }

        boolean isValid = jenaReport.conforms();

        log.debug("Converted {} validation entries. Overall result: {}",
                results.size(), isValid ? "VALID" : "INVALID");

        return new ISMDValidationReport(results, isValid, Instant.now());
    }

    /**
     * Alternative method to handle ValidationReport if primary approach fails
     */
    private ISMDValidationReport handleValidationReportAlternative(ValidationReport jenaReport) {
        try {
            boolean isValid = jenaReport.conforms();

            if (isValid) {
                log.info("Validation passed, no entries to process");
                return new ISMDValidationReport(new ArrayList<>(), true, Instant.now());
            } else {
                log.warn("Validation failed but could not extract detailed entries");
                ValidationResult errorResult = new ValidationResult(
                        ValidationSeverity.ERROR,
                        "Validation failed - unable to extract detailed results",
                        "system-error",
                        null,
                        null,
                        null
                );
                return new ISMDValidationReport(
                        List.of(errorResult),
                        false,
                        Instant.now()
                );
            }
        } catch (Exception e) {
            log.error("Complete failure processing validation report", e);
            return ISMDValidationReport.error("Failed to process validation report: " + e.getMessage());
        }
    }

    /**
     * Convert a single validation entry
     */
    private ValidationResult convertValidationEntry(ReportEntry entry) {
        try {
            ValidationSeverity severity = ValidationSeverity.INFO;
            if (entry.severity() != null) {
                severity = convertSeverity(entry.severity());
            }

            String message = entry.message();
            if (message == null || message.trim().isEmpty()) {
                message = "Validation constraint violation";
            }

            String focusNodeUri = null;
            try {
                Node focusNode = entry.focusNode();
                focusNodeUri = nodeToUri(focusNode);
            } catch (Exception e) {
                log.debug("Could not extract focus node: {}", e.getMessage());
            }

            String resultPathUri = null;
            try {
                Path resultPath = entry.resultPath();
                resultPathUri = pathToUri(resultPath);
            } catch (Exception e) {
                log.debug("Could not extract result path: {}", e.getMessage());
            }

            String ruleName = "unknown-rule";
            try {
                ruleName = extractRuleNameFromEntry(entry);
            } catch (Exception e) {
                log.debug("Could not extract source shape: {}", e.getMessage());
            }

            String valueString = null;
            try {
                Node valueNode = entry.value();
                valueString = nodeToString(valueNode);
            } catch (Exception e) {
                log.debug("Could not extract value: {}", e.getMessage());
            }

            return new ValidationResult(
                    severity,
                    message,
                    ruleName,
                    focusNodeUri,
                    resultPathUri,
                    valueString
            );

        } catch (Exception e) {
            log.error("Failed to convert validation entry", e);
            return new ValidationResult(
                    ValidationSeverity.ERROR,
                    "Failed to process validation result: " + e.getMessage(),
                    "conversion-error",
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * Convert Node to URI string
     */
    private String nodeToUri(Node node) {
        if (node == null) {
            return null;
        }
        if (node.isURI()) {
            return node.getURI();
        }
        return node.toString();
    }

    /**
     * Convert Path to URI string
     */
    private String pathToUri(Path path) {
        if (path == null) {
            return null;
        }
        return path.toString();
    }

    /**
     * Convert Node to string representation
     */
    private String nodeToString(Node node) {
        if (node == null) {
            return null;
        }

        if (node.isLiteral()) {
            return node.getLiteralLexicalForm();
        } else if (node.isURI()) {
            return node.getURI();
        } else {
            return node.toString();
        }
    }

    /**
     * Extract rule name from ReportEntry - fallback approach
     */
    private String extractRuleNameFromEntry(ReportEntry entry) {
        try {
            String entryString = entry.toString();

            if (entryString.contains("Shape")) {
                String[] parts = entryString.split("\\s+");
                for (String part : parts) {
                    if (part.contains("Shape") || part.contains("Rule")) {
                        return cleanRuleName(part);
                    }
                }
            }

            return "validation-rule";

        } catch (Exception e) {
            log.debug("Error extracting rule name from entry: {}", e.getMessage());
            return "unknown-rule";
        }
    }

    /**
     * Clean up extracted rule name
     */
    private String cleanRuleName(String rawName) {
        if (rawName == null) {
            return "unknown-rule";
        }

        String cleaned = rawName.replaceAll("^.*[#/]", "")
                .replaceAll("[\\[\\]()]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase();

        return cleaned.isEmpty() ? "unknown-rule" : cleaned;
    }

    /**
     * Extract rule name from Node
     */
    private String extractRuleNameFromNode(Node node) {
        if (node == null) {
            return "unknown-rule";
        }

        try {
            if (node.isURI()) {
                String uri = node.getURI();

                int lastHash = uri.lastIndexOf('#');
                int lastSlash = uri.lastIndexOf('/');
                int splitIndex = Math.max(lastHash, lastSlash);

                if (splitIndex >= 0 && splitIndex < uri.length() - 1) {
                    return uri.substring(splitIndex + 1);
                }

                return uri;
            } else {
                return node.toString();
            }
        } catch (Exception e) {
            log.debug("Error extracting rule name from node: {}", e.getMessage());
            return "extraction-error";
        }
    }

    /**
     * Convert Jena severity to enum
     */
    private ValidationSeverity convertSeverity(Severity severity) {
        if (severity == null) {
            return ValidationSeverity.INFO;
        }

        try {
            if (severity == Severity.Violation) {
                return ValidationSeverity.ERROR;
            } else if (severity == Severity.Warning) {
                return ValidationSeverity.WARNING;
            } else if (severity == Severity.Info) {
                return ValidationSeverity.INFO;
            } else {
                log.debug("Unknown severity: {}, defaulting to INFO", severity);
                return ValidationSeverity.INFO;
            }
        } catch (Exception e) {
            log.debug("Error converting severity: {}, defaulting to INFO", e.getMessage());
            return ValidationSeverity.INFO;
        }
    }

    /**
     * Validate specific rules only
     */
    public ISMDValidationReport validateWithRules(Model dataModel, List<String> ruleNames) {
        log.info("Starting targeted SHACL validation with {} specific rules", ruleNames.size());

        try {
            Model shapesModel = combineSpecificRules(ruleNames);

            if (shapesModel.isEmpty()) {
                log.warn("No valid rules found for names: {}", ruleNames);
                return ISMDValidationReport.empty();
            }

            return executeValidationWithTimeout(dataModel, shapesModel);

        } catch (Exception e) {
            log.error("Targeted SHACL validation failed", e);
            return ISMDValidationReport.error("Targeted validation execution failed: " + e.getMessage());
        }
    }

    /**
     * Combine specific rules into a single model
     */
    private Model combineSpecificRules(List<String> ruleNames) {
        Model combinedModel = ModelFactory.createDefaultModel();

        for (String ruleName : ruleNames) {
            ruleManager.getRuleModel(ruleName).ifPresent(combinedModel::add);
        }

        return combinedModel;
    }
}