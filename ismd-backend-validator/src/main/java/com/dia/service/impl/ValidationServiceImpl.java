package com.dia.service.impl;

import com.dia.conversion.data.TransformationResult;
import com.dia.exceptions.ValidationException;
import com.dia.service.ValidationService;
import com.dia.service.record.ValidationConfigurationSummary;
import com.dia.validation.config.RuleManager;
import com.dia.validation.config.ValidationConfiguration;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.enums.ValidationTiming;
import com.dia.validation.engine.SHACLRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceImpl implements ValidationService {

    private final SHACLRuleEngine shaclEngine;
    private final RuleManager ruleManager;
    private final ValidationConfiguration config;

    @Override
    public ISMDValidationReport validate(TransformationResult result) {
        return validate(result, config.getDefaultTiming());
    }

    @Override
    public ISMDValidationReport validate(TransformationResult result, ValidationTiming timing) {
        log.info("Starting validation with timing: {}", timing);

        try {
            Model dataModel = extractDataModel(result, timing);
            return shaclEngine.validate(dataModel);

        } catch (Exception e) {
            log.error("Validation failed for timing: {}", timing, e);
            return ISMDValidationReport.error("Validation failed: " + e.getMessage());
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

    public ValidationConfigurationSummary getConfigurationSummary() {
        return new ValidationConfigurationSummary(
                ruleManager.getAllRuleNames().size(),
                ruleManager.getEnabledRuleNames().size(),
                ruleManager.getEnabledRuleNames(),
                config.getDefaultTiming()
        );
    }

    public void setRuleEnabled(String ruleName, boolean enabled) {
        log.info("Setting rule '{}' enabled: {}", ruleName, enabled);

        if (!ruleManager.getAllRuleNames().contains(ruleName)) {
            throw new IllegalArgumentException("Unknown validation rule: " + ruleName);
        }

        config.setRuleEnabled(ruleName, enabled);
        log.info("Rule '{}' is now {}", ruleName, enabled ? "enabled" : "disabled");
    }

    public ISMDValidationReport testValidation() {
        log.info("Running validation test with empty model");

        Model emptyModel = ModelFactory.createDefaultModel();
        ISMDValidationReport report = shaclEngine.validate(emptyModel);

        log.info("Test validation completed: {}", report.getSummary());
        return report;
    }
}

