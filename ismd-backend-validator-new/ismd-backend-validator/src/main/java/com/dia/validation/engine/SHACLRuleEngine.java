package com.dia.validation.engine;

import com.dia.exceptions.ValidationException;
import com.dia.validation.ValidationResult;
import com.dia.validation.ValidationSeverity;
import com.dia.validation.config.RuleManager;
import com.dia.validation.config.ValidationConfiguration;
import com.dia.validation.data.ISMDValidationReport;
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
import java.util.Set;
import java.util.concurrent.*;

@Component
@Slf4j
public class SHACLRuleEngine {

    private static final String LOCAL_SHACL_BASE_URI = "https://slovník.gov.cz/shacl/lokální/";
    private static final String GLOBAL_SHACL_BASE_URI = "https://slovník.gov.cz/shacl/globální/";

    private final ValidationConfiguration config;
    private final RuleManager ruleManager;
    private final ExecutorService executorService;

    @Autowired
    public SHACLRuleEngine(ValidationConfiguration config, RuleManager ruleManager) {
        this.config = config;
        this.ruleManager = ruleManager;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    public ISMDValidationReport validate(Model dataModel) {
        log.info("Starting SHACL validation with {} enabled rules: {}",
                ruleManager.getEnabledRuleNames().size(), ruleManager.getEnabledRuleNames().toString());

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

    private ISMDValidationReport executeValidationWithTimeout(Model dataModel, Model shapesModel)
            throws ValidationException {

        Future<ISMDValidationReport> future = executorService.submit(() -> {
            try {
                return executeValidation(dataModel, shapesModel);
            } catch (Exception e) {
                throw new ValidationException("Validation execution failed", e);
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
            if (cause instanceof ValidationException) {
                throw (ValidationException) cause;
            }
            throw new ValidationException("Validation execution failed", cause);
        }
    }

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

    private ValidationResult convertValidationEntry(ReportEntry entry) {
        try {
            ValidationSeverity severity = extractSeverity(entry);
            String message = extractMessage(entry);
            String focusNodeUri = extractFocusNodeUri(entry);
            String resultPathUri = extractResultPathUri(entry);
            String ruleName = extractRuleNameFromEntry(entry);
            String valueString = extractValueString(entry);

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
            return createErrorResult("Failed to process validation result: " + e.getMessage());
        }
    }

    private ValidationSeverity extractSeverity(ReportEntry entry) {
        try {
            return entry.severity() != null ? convertSeverity(entry.severity()) : ValidationSeverity.INFO;
        } catch (Exception e) {
            log.debug("Could not extract severity: {}", e.getMessage());
            return ValidationSeverity.INFO;
        }
    }

    private String extractMessage(ReportEntry entry) {
        try {
            String message = entry.message();
            return (message != null && !message.trim().isEmpty()) ? message : "Validation constraint violation";
        } catch (Exception e) {
            log.debug("Could not extract message: {}", e.getMessage());
            return "Validation constraint violation";
        }
    }

    private String extractFocusNodeUri(ReportEntry entry) {
        try {
            Node focusNode = entry.focusNode();
            return nodeToUri(focusNode);
        } catch (Exception e) {
            log.debug("Could not extract focus node: {}", e.getMessage());
            return null;
        }
    }

    private String extractResultPathUri(ReportEntry entry) {
        try {
            Path resultPath = entry.resultPath();
            return pathToUri(resultPath);
        } catch (Exception e) {
            log.debug("Could not extract result path: {}", e.getMessage());
            return null;
        }
    }

    private String extractRuleNameFromEntry(ReportEntry entry) {
        try {
            Node sourceShape = entry.source();
            if (sourceShape != null && sourceShape.isURI()) {
                String shapeUri = sourceShape.getURI();

                if (shapeUri.startsWith("https://slovník.gov.cz/shacl/")) {
                    return shapeUri;
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting rule IRI: {}", e.getMessage());
        }

        return GLOBAL_SHACL_BASE_URI + "unknown-rule";
    }


    private String extractValueString(ReportEntry entry) {
        try {
            Node valueNode = entry.value();
            return nodeToString(valueNode);
        } catch (Exception e) {
            log.debug("Could not extract value: {}", e.getMessage());
            return null;
        }
    }

    private ValidationResult createErrorResult(String errorMessage) {
        return new ValidationResult(
                ValidationSeverity.ERROR,
                errorMessage,
                "conversion-error",
                null,
                null,
                null
        );
    }

    private String nodeToUri(Node node) {
        if (node == null) {
            return null;
        }
        if (node.isURI()) {
            return node.getURI();
        }
        return node.toString();
    }

    private String pathToUri(Path path) {
        if (path == null) {
            return null;
        }
        return path.toString();
    }

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

    private Model combineSpecificRules(List<String> ruleNames) {
        Model combinedModel = ModelFactory.createDefaultModel();

        for (String ruleName : ruleNames) {
            ruleManager.getRuleModel(ruleName).ifPresent(combinedModel::add);
        }

        return combinedModel;
    }
}