package com.dia.service.impl;

import com.dia.enums.ValidationSeverity;
import com.dia.exceptions.ValidationException;
import com.dia.service.DetailedValidationReportService;
import com.dia.validation.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetailedValidationReportServiceImpl implements DetailedValidationReportService {

    private static final String SEVERITY_ERROR = "http://www.w3.org/ns/shacl#Violation";
    private static final String SEVERITY_WARNING = "http://www.w3.org/ns/shacl#Warning";
    private static final String SEVERITY_INFO = "http://www.w3.org/ns/shacl#Info";

    @Override
    public DetailedValidationReportDto generateDetailedReport(ISMDValidationReport report, Model ontologyModel) {
        log.info("Generating detailed validation report with {} results", report.getTotalResultCount());

        OntologyInfoDto ontologyInfo = extractOntologyInfo(ontologyModel);
        Map<String, ConceptValidationDto> validationResults = groupValidationResultsByConcept(report);

        return new DetailedValidationReportDto(ontologyInfo, validationResults);
    }

    @Override
    public String generateCSV(DetailedValidationReportDto report) {
        log.info("Converting detailed report to CSV format");

        try {
            StringWriter stringWriter = new StringWriter();

            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withHeader("Concept_IRI", "Concept_Name", "Rule_IRI", "Rule_Name",
                            "Rule_Description", "Severity", "Level", "Violation_Message", "Violating_Value")
                    .withRecordSeparator("\n")
                    .withQuoteMode(QuoteMode.MINIMAL);

            try (CSVPrinter csvPrinter = new CSVPrinter(stringWriter, csvFormat)) {

                report.validation().forEach((conceptIri, conceptValidation) -> {
                    String conceptName = extractConceptName(conceptIri);

                    if (conceptValidation.violations().isEmpty()) {
                        try {
                            csvPrinter.printRecord(
                                    normalizeString(conceptIri),
                                    normalizeString(conceptName),
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "No violations found",
                                    ""
                            );
                        } catch (IOException e) {
                            log.error("Error writing CSV record for concept: {}", conceptIri, e);
                        }
                    } else {
                        conceptValidation.violations().forEach((ruleKey, violation) -> {
                            try {
                                csvPrinter.printRecord(
                                        normalizeString(conceptIri),
                                        normalizeString(conceptName),
                                        normalizeString(ruleKey),
                                        normalizeString(violation.name()),
                                        normalizeString(violation.description()),
                                        normalizeString(violation.severity()),
                                        normalizeString(violation.level()),
                                        normalizeString(violation.value()),
                                        ""
                                );
                            } catch (IOException e) {
                                log.error("Error writing CSV record for concept: {} rule: {}", conceptIri, ruleKey, e);
                            }
                        });
                    }
                });
            }

            return stringWriter.toString();

        } catch (IOException e) {
            log.error("Error generating CSV content", e);
            throw new ValidationException("Failed to generate CSV: " + e.getMessage(), e);
        }
    }

    private String normalizeString(String value) {
        if (value == null) {
            return "";
        }

        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC);

        return normalized.trim();
    }

    @Override
    public DetailedValidationReportDto generateCombinedDetailedReport(
            ISMDValidationReport localReport,
            ISMDValidationReport globalReport,
            Model ontologyModel,
            Model shaclRulesModel) {

        log.info("Generating combined detailed report (local: {}, global: {})",
                localReport.getTotalResultCount(), globalReport.getTotalResultCount());

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

        ISMDValidationReport combinedReport = new ISMDValidationReport(
                combinedResults,
                isValid,
                Instant.now()
        );

        return generateDetailedReport(combinedReport, ontologyModel);
    }

    @Override
    public DetailedValidationReportDto generateDetailedReportFromTtlFile(ISMDValidationReport report, MultipartFile ttlFile) {
        log.info("Generating detailed validation report from TTL file: {} with {} results", ttlFile.getOriginalFilename(), report.getTotalResultCount());

        try {
            String ttlContent = new String(ttlFile.getBytes(), StandardCharsets.UTF_8);
            return generateDetailedReportFromTtl(report, ttlContent);

        } catch (IOException e) {
            log.error("Failed to read TTL file for detailed report generation: {}", ttlFile.getOriginalFilename(), e);
            throw new ValidationException("Failed to read TTL file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to generate detailed report from TTL file: {}", ttlFile.getOriginalFilename(), e);
            throw new ValidationException("Failed to generate detailed report from TTL file: " + e.getMessage(), e);
        }
    }

    private DetailedValidationReportDto generateDetailedReportFromTtl(ISMDValidationReport report, String ttlContent) {
        log.info("Generating detailed validation report from TTL content with {} results", report.getTotalResultCount());

        try {
            Model ontologyModel = parseTtlToModel(ttlContent);
            return generateDetailedReport(report, ontologyModel);

        } catch (Exception e) {
            log.error("Failed to generate detailed report from TTL content", e);
            throw new ValidationException("Failed to generate detailed report from TTL content: " + e.getMessage(), e);
        }
    }

    private OntologyInfoDto extractOntologyInfo(Model ontologyModel) {
        Map<String, String> name = new HashMap<>();
        Map<String, String> description = new HashMap<>();

        Resource owlOntology = ontologyModel.createResource("http://www.w3.org/2002/07/owl#Ontology");
        Resource skosConceptScheme = SKOS.ConceptScheme;

        Set<Resource> ontologyResources = new HashSet<>();
        ontologyModel.listResourcesWithProperty(RDF.type, owlOntology).forEachRemaining(ontologyResources::add);
        ontologyModel.listResourcesWithProperty(RDF.type, skosConceptScheme).forEachRemaining(ontologyResources::add);

        for (Resource ontologyResource : ontologyResources) {
            ontologyResource.listProperties(SKOS.prefLabel).forEachRemaining(stmt -> {
                String lang = stmt.getLanguage();
                String value = stmt.getString();
                name.put(lang != null && !lang.isEmpty() ? lang : "cs", value);
            });

            ontologyResource.listProperties(DCTerms.description).forEachRemaining(stmt -> {
                String lang = stmt.getLanguage();
                String value = stmt.getString();
                description.put(lang != null && !lang.isEmpty() ? lang : "cs", value);
            });
        }

        if (name.isEmpty()) {
            name.put("cs", "Neznámý slovník");
        }

        return new OntologyInfoDto(name, description);
    }

    private Map<String, ConceptValidationDto> groupValidationResultsByConcept(ISMDValidationReport report) {
        Map<String, ConceptValidationDto> conceptValidations = new HashMap<>();

        Map<String, List<ValidationResult>> resultsByConcept = report.results().stream()
                .filter(result -> result.focusNodeUri() != null)
                .collect(Collectors.groupingBy(ValidationResult::focusNodeUri));

        resultsByConcept.forEach((conceptIri, results) -> {
            Map<String, RuleViolationDto> ruleViolations = new HashMap<>();

            Map<String, List<ValidationResult>> resultsByRule = results.stream()
                    .collect(Collectors.groupingBy(result ->
                            result.ruleName() != null ? result.ruleName() : "unknown-rule"));

            resultsByRule.forEach((ruleName, ruleResults) -> {
                ValidationResult firstResult = ruleResults.get(0);

                String violationKey = generateViolationKey(ruleName, ruleViolations.size() + 1);

                String displayName = ruleName != null && !ruleName.equals("unknown-rule") ?
                        ruleName : "Neznámé pravidlo";

                RuleViolationDto violation = new RuleViolationDto(
                        displayName,
                        firstResult.message(),
                        mapSeverityToLevel(firstResult.severity()),
                        mapSeverityToIri(firstResult.severity()),
                        firstResult.value()
                );

                ruleViolations.put(violationKey, violation);
            });

            conceptValidations.put(conceptIri, new ConceptValidationDto(conceptIri, ruleViolations));
        });

        return conceptValidations;
    }

    private String generateViolationKey(String ruleName, int index) {
        if (ruleName != null && !ruleName.equals("unknown-rule")) {
            return ruleName.toLowerCase();
        }
        return "violation-rule-" + index;
    }

    private String mapSeverityToLevel(ValidationSeverity severity) {
        return switch (severity) {
            case ERROR -> "Vysoká";
            case WARNING -> "Střední";
            case INFO -> "Nízká";
        };
    }

    private String mapSeverityToIri(ValidationSeverity severity) {
        return switch (severity) {
            case ERROR -> SEVERITY_ERROR;
            case WARNING -> SEVERITY_WARNING;
            case INFO -> SEVERITY_INFO;
        };
    }

    private String extractConceptName(String conceptIri) {
        if (conceptIri == null) return "";

        int lastSlash = conceptIri.lastIndexOf('/');
        int lastHash = conceptIri.lastIndexOf('#');
        int splitIndex = Math.max(lastSlash, lastHash);

        if (splitIndex >= 0 && splitIndex < conceptIri.length() - 1) {
            return conceptIri.substring(splitIndex + 1);
        }

        return conceptIri;
    }

    private Model parseTtlToModel(String ttlContent) {
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
}