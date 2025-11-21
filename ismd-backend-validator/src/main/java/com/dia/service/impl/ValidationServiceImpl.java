package com.dia.service.impl;

import com.dia.conversion.data.TransformationResult;
import com.dia.controller.exception.EmptyContentException;
import com.dia.controller.exception.InvalidFileException;
import com.dia.controller.exception.InvalidFormatException;
import com.dia.controller.exception.ValidationException;
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
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

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
    public ISMDValidationReport validateModel(Model model) {
        log.info("Starting direct model validation");
        return shaclEngine.validate(model);
    }


    @Override
    public ISMDValidationReport validateRdf(String rdfContent, String format) {
        log.info("Starting RDF string validation with format: {}", format);

        if (rdfContent == null || rdfContent.trim().isEmpty()) {
            throw new EmptyContentException("RDF content cannot be null or empty");
        }

        if (format == null || format.trim().isEmpty()) {
            throw new InvalidFormatException("RDF format cannot be null or empty");
        }

        try {
            Model model = parseRdfString(rdfContent, format);
            return shaclEngine.validate(model);

        } catch (ValidationException | InvalidFormatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse RDF content for validation", e);
            throw new ValidationException("Failed to parse RDF content: " + e.getMessage(), e);
        }
    }

    @Override
    public ISMDValidationReport validateTtl(String ttlContent) {
        log.info("Starting TTL validation");

        if (ttlContent == null || ttlContent.trim().isEmpty()) {
            throw new EmptyContentException("TTL content cannot be null or empty");
        }

        try {
            Model model = parseTtlString(ttlContent);
            return shaclEngine.validate(model);

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse TTL content for validation", e);
            throw new ValidationException("Failed to parse TTL content: " + e.getMessage(), e);
        }
    }

    @Override
    public ISMDValidationReport validateTtlFile(MultipartFile file) {
        if (file == null) {
            throw new InvalidFileException("File cannot be null");
        }

        log.info("Starting TTL file validation: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        try {
            validateTtlFileFormat(file);
            String ttlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            return validateTtl(ttlContent);

        } catch (ValidationException | InvalidFileException | EmptyContentException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to read TTL file: {}", file.getOriginalFilename(), e);
            throw new InvalidFileException("Failed to read TTL file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("TTL file validation failed: {}", file.getOriginalFilename(), e);
            throw new ValidationException("TTL file validation failed: " + e.getMessage(), e);
        }
    }

    private Model extractDataModel(TransformationResult result, ValidationTiming timing) {
        return switch (timing) {
            case BEFORE_EXPORT -> result.getOntModel();
            case JSON_EXPORT -> convertFromJsonLd(result);
            case TTL_EXPORT -> convertFromTtl(result);
            default -> throw new IllegalArgumentException("Unknown validation timing: " + timing);
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

    private Model parseRdfString(String rdfContent, String format) {
        try {
            Model model = ModelFactory.createDefaultModel();

            model.read(new StringReader(rdfContent), null, format);

            log.debug("Parsed {} content into model with {} statements", format, model.size());
            return model;

        } catch (RiotException e) {
            log.error("Failed to parse {} content - invalid syntax", format, e);
            throw new ValidationException("Invalid " + format + " syntax: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse {} content", format, e);
            throw new ValidationException("Failed to parse " + format + " content: " + e.getMessage(), e);
        }
    }

    private Model parseTtlString(String ttlContent) {
        try {
            Model model = ModelFactory.createDefaultModel();

            InputStream inputStream = new ByteArrayInputStream(ttlContent.getBytes(StandardCharsets.UTF_8));
            RDFDataMgr.read(model, inputStream, Lang.TTL);

            log.debug("Parsed TTL content into model with {} statements", model.size());
            return model;

        } catch (RiotException e) {
            log.error("Failed to parse TTL content - invalid syntax", e);
            throw new ValidationException("Invalid TTL syntax: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse TTL content", e);
            throw new ValidationException("Failed to parse TTL content: " + e.getMessage(), e);
        }
    }

    private void validateTtlFileFormat(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyContentException("TTL file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".ttl")) {
            throw new InvalidFileException("File must have .ttl extension");
        }
    }

    public ValidationConfigurationSummary getConfigurationSummary() {
        return new ValidationConfigurationSummary(
                ruleManager.getAllRuleNames().size(),
                ruleManager.getEnabledRuleNames().size(),
                ruleManager.getEnabledRuleNames().stream().toList(),
                config.getDefaultTiming()
        );
    }
}