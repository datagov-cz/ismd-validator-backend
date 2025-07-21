package com.dia.service.impl;

import com.dia.conversion.data.TransformationResult;
import com.dia.exceptions.ValidationException;
import com.dia.service.SPARQLQueryService;
import com.dia.service.ValidationService;
import com.dia.service.record.RuleInfo;
import com.dia.service.record.ValidationConfigurationSummary;
import com.dia.validation.config.RuleManager;
import com.dia.validation.config.ValidationConfiguration;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.validation.data.ValidationResult;
import com.dia.validation.rules.GlobalValidationRule;
import com.dia.enums.ValidationTiming;
import com.dia.validation.engine.SHACLRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceImpl implements ValidationService {

    private final SHACLRuleEngine shaclEngine;
    private final RuleManager ruleManager;
    private final ValidationConfiguration config;
    private final List<GlobalValidationRule> globalValidationRules;
    private final SPARQLQueryService sparqlQueryService;

    @Override
    public ISMDValidationReport validate(TransformationResult result) {
        return validate(result, config.getDefaultTiming());
    }

    @Override
    public ISMDValidationReport validate(TransformationResult result, ValidationTiming timing) {
        log.info("Starting local validation with timing: {}", timing);

        try {
            Model dataModel = extractDataModel(result, timing);
            return shaclEngine.validate(dataModel);

        } catch (Exception e) {
            log.error("Local validation failed for timing: {}", timing, e);
            return ISMDValidationReport.error("Local validation failed: " + e.getMessage());
        }
    }

    @Override
    public ISMDValidationReport validateWithRules(TransformationResult result, List<String> ruleNames, ValidationTiming timing) {
        log.info("Starting targeted validation with {} rules and timing: {}", ruleNames.size(), timing);

        try {
            Model dataModel = extractDataModel(result, timing);
            return shaclEngine.validateWithRules(dataModel, ruleNames);

        } catch (Exception e) {
            log.error("Targeted validation failed", e);
            return ISMDValidationReport.error("Targeted validation failed: " + e.getMessage());
        }
    }

    @Override
    public ISMDValidationReport validateModel(Model model) {
        log.info("Starting direct model validation");
        return shaclEngine.validate(model);
    }

    @Override
    public ISMDValidationReport validateRdf(String rdfContent, String format) {
        log.info("Starting RDF string validation with format: {}", format);

        try {
            Model model = ModelFactory.createDefaultModel();
            model.read(new StringReader(rdfContent), null, format);

            return shaclEngine.validate(model);

        } catch (Exception e) {
            log.error("Failed to parse RDF content for validation", e);
            return ISMDValidationReport.error("Failed to parse RDF content: " + e.getMessage());
        }
    }

    @Override
    public ISMDValidationReport validateGlobally(TransformationResult result) {
        log.info("Starting global validation with {} available rules", globalValidationRules.size());

        if (!isGlobalValidationAvailable()) {
            log.warn("Global validation is not available (no connectivity to external services)");
            return ISMDValidationReport.empty();
        }

        List<ValidationResult> allResults = new ArrayList<>();
        List<String> enabledGlobalRules = getEnabledGlobalRuleNames();

        if (enabledGlobalRules.isEmpty()) {
            log.info("No global validation rules are enabled");
            return ISMDValidationReport.empty();
        }

        log.info("Executing {} enabled global validation rules: {}", enabledGlobalRules.size(), enabledGlobalRules);

        for (String ruleName : enabledGlobalRules) {
            try {
                GlobalValidationRule rule = findGlobalRule(ruleName);
                if (rule != null) {
                    List<ValidationResult> ruleResults = executeGlobalRuleWithTimeout(rule, result);
                    allResults.addAll(ruleResults);
                    log.debug("Global rule '{}' completed with {} results", ruleName, ruleResults.size());
                } else {
                    log.warn("Global rule '{}' is enabled but not found", ruleName);
                }
            } catch (Exception e) {
                log.error("Global validation rule '{}' failed", ruleName, e);

                if (!config.isContinueOnRuleError()) {
                    allResults.add(createGlobalRuleFailureResult(ruleName, e));
                }
            }
        }

        boolean isValid = allResults.stream().noneMatch(ValidationResult::isError);

        log.info("Global validation completed. {} total results, valid: {}", allResults.size(), isValid);
        return new ISMDValidationReport(allResults, isValid, Instant.now());
    }

    @Override
    public ISMDValidationReport validateComplete(TransformationResult result, boolean includeGlobal) {
        ISMDValidationReport localReport = validate(result);

        if (!includeGlobal) {
            return localReport;
        }

        ISMDValidationReport globalReport = validateGlobally(result);

        List<ValidationResult> combinedResults = new ArrayList<>(localReport.results());

        List<ValidationResult> globalResults = globalReport.results().stream()
                .map(result -> new ValidationResult(
                        result.severity(),
                        "[GLOBAL] " + result.message(),
                        result.ruleName(),
                        result.focusNodeUri(),
                        result.resultPathUri(),
                        result.value()
                ))
                .toList();

        combinedResults.addAll(globalResults);

        boolean isValid = localReport.isValid() && globalReport.isValid();

        return new ISMDValidationReport(combinedResults, isValid, Instant.now());
    }

    private Model extractDataModel(TransformationResult result, ValidationTiming timing) {
        return switch (timing) {
            case BEFORE_EXPORT -> result.getOntModel();
            case JSON_EXPORT -> convertFromJsonLd(result);
            case TTL_EXPORT -> convertFromTtl(result);
        };
    }

    private Model convertFromJsonLd(TransformationResult result) {
        try {
            StringWriter writer = new StringWriter();
            result.getOntModel().write(writer, "JSON-LD");
            String jsonLd = writer.toString();

            Model model = ModelFactory.createDefaultModel();
            model.read(new StringReader(jsonLd), null, "JSON-LD");

            log.debug("Converted to JSON-LD model with {} statements", model.size());
            return model;

        } catch (Exception e) {
            log.error("Failed to convert via JSON-LD export", e);
            throw new ValidationException("JSON-LD conversion failed", e);
        }
    }

    private Model convertFromTtl(TransformationResult result) {
        try {
            StringWriter writer = new StringWriter();
            result.getOntModel().write(writer, "TTL");
            String ttl = writer.toString();

            Model model = ModelFactory.createDefaultModel();
            model.read(new StringReader(ttl), null, "TTL");

            log.debug("Converted to TTL model with {} statements", model.size());
            return model;

        } catch (Exception e) {
            log.error("Failed to convert via TTL export", e);
            throw new ValidationException("TTL conversion failed", e);
        }
    }

    private List<ValidationResult> executeGlobalRuleWithTimeout(GlobalValidationRule rule, TransformationResult result) {
        try {
            CompletableFuture<List<ValidationResult>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return rule.validate(result);
                } catch (Exception e) {
                    log.error("Global rule '{}' execution failed", rule.getRuleName(), e);
                    throw new ValidationException("Global rule execution failed", e);
                }
            });

            return future.get(config.getTimeoutMs(), TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("Global rule '{}' timed out or failed", rule.getRuleName(), e);
            throw new ValidationException("Global rule execution failed: " + e.getMessage(), e);
        }
    }

    private ValidationResult createGlobalRuleFailureResult(String ruleName, Exception e) {
        return new ValidationResult(
                com.dia.enums.ValidationSeverity.WARNING,
                "Globální validační pravidlo selhalo: " + e.getMessage(),
                ruleName + "-execution-failed",
                null,
                null,
                null
        );
    }

    private GlobalValidationRule findGlobalRule(String ruleName) {
        return globalValidationRules.stream()
                .filter(rule -> rule.getRuleName().equals(ruleName))
                .findFirst()
                .orElse(null);
    }

    private List<String> getEnabledGlobalRuleNames() {
        return globalValidationRules.stream()
                .map(GlobalValidationRule::getRuleName)
                .filter(config::isRuleEnabled)
                .toList();
    }

    private boolean isGlobalValidationAvailable() {
        try {
            return sparqlQueryService.testConnectivity();
        } catch (Exception e) {
            log.debug("Global validation connectivity test failed", e);
            return false;
        }
    }

    @Override
    public ValidationConfigurationSummary getConfigurationSummary() {
        int totalLocalRules = ruleManager.getAllRuleNames().size();
        int enabledLocalRules = ruleManager.getEnabledRuleNames().size();
        int totalGlobalRules = globalValidationRules.size();
        int enabledGlobalRules = getEnabledGlobalRuleNames().size();

        List<String> allEnabledRules = new ArrayList<>();
        allEnabledRules.addAll(ruleManager.getEnabledRuleNames());
        allEnabledRules.addAll(getEnabledGlobalRuleNames());

        return new ValidationConfigurationSummary(
                totalLocalRules + totalGlobalRules,
                enabledLocalRules + enabledGlobalRules,
                allEnabledRules,
                config.getDefaultTiming()
        );
    }

    @Override
    public void setRuleEnabled(String ruleName, boolean enabled) {
        log.info("Setting rule '{}' enabled: {}", ruleName, enabled);

        if (ruleManager.getAllRuleNames().contains(ruleName)) {
            config.setRuleEnabled(ruleName, enabled);
            log.info("Local rule '{}' is now {}", ruleName, enabled ? "enabled" : "disabled");
            return;
        }

        boolean isGlobalRule = globalValidationRules.stream()
                .anyMatch(rule -> rule.getRuleName().equals(ruleName));

        if (isGlobalRule) {
            config.setRuleEnabled(ruleName, enabled);
            log.info("Global rule '{}' is now {}", ruleName, enabled ? "enabled" : "disabled");
            return;
        }

        throw new IllegalArgumentException("Unknown validation rule: " + ruleName);
    }

    @Override
    public Optional<RuleInfo> getRuleInfo(String ruleName) {
        Optional<RuleInfo> localRuleInfo = ruleManager.getRuleDefinition(ruleName)
                .map(def -> new RuleInfo(
                        def.name(),
                        def.filename(),
                        config.isRuleEnabled(ruleName),
                        def.metadata()
                ));

        if (localRuleInfo.isPresent()) {
            return localRuleInfo;
        }

        return globalValidationRules.stream()
                .filter(rule -> rule.getRuleName().equals(ruleName))
                .findFirst()
                .map(rule -> new RuleInfo(
                        rule.getRuleName(),
                        "global-rule",
                        config.isRuleEnabled(ruleName),
                        java.util.Map.of(
                                "type", "global",
                                "description", rule.getDescription(),
                                "defaultSeverity", rule.getDefaultSeverity().toString(),
                                "requiresNetworkAccess", String.valueOf(rule.requiresNetworkAccess())
                        )
                ));
    }

    @Override
    public List<String> getAllRuleNames() {
        List<String> allRules = new ArrayList<>();
        allRules.addAll(ruleManager.getAllRuleNames());
        allRules.addAll(globalValidationRules.stream().map(GlobalValidationRule::getRuleName).toList());
        return allRules;
    }

    @Override
    public List<String> getEnabledRuleNames() {
        List<String> enabledRules = new ArrayList<>();
        enabledRules.addAll(ruleManager.getEnabledRuleNames());
        enabledRules.addAll(getEnabledGlobalRuleNames());
        return enabledRules;
    }

    @Override
    public ISMDValidationReport testValidation() {
        log.info("Running validation test with empty model");

        Model emptyModel = ModelFactory.createDefaultModel();
        ISMDValidationReport report = shaclEngine.validate(emptyModel);

        log.info("Test validation completed: {}", report.getSummary());
        return report;
    }

    @Override
    public boolean testGlobalValidationConnectivity() {
        return isGlobalValidationAvailable();
    }
}