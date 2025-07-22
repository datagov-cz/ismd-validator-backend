package com.dia.service.impl;

import com.dia.enums.ValidationSeverity;
import com.dia.service.DetailedValidationReportService;
import com.dia.validation.config.RuleManager;
import com.dia.validation.data.*;
import com.dia.validation.report.SHACLRuleMetadataExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetailedValidationReportServiceImpl implements DetailedValidationReportService {

    private final RuleManager ruleManager;
    private final SHACLRuleMetadataExtractor metadataExtractor;

    private static final String SHACL_NAME = "http://www.w3.org/ns/shacl#name";
    private static final String SHACL_DESCRIPTION = "http://www.w3.org/ns/shacl#description";
    private static final String SHACL_SEVERITY = "http://www.w3.org/ns/shacl#severity";

    private static final String SEVERITY_ERROR = "http://www.w3.org/ns/shacl#Violation";
    private static final String SEVERITY_WARNING = "http://www.w3.org/ns/shacl#Warning";
    private static final String SEVERITY_INFO = "http://www.w3.org/ns/shacl#Info";

    @Override
    public DetailedValidationReportDto generateDetailedReport(ISMDValidationReport report, Model ontologyModel) {
        return generateDetailedReport(report, ontologyModel, null);
    }

    @Override
    public DetailedValidationReportDto generateDetailedReport(ISMDValidationReport report, Model ontologyModel, Model shaclRulesModel) {
        log.info("Generating detailed validation report with {} results", report.getTotalResultCount());

        OntologyInfoDto ontologyInfo = extractOntologyInfo(ontologyModel);
        Map<String, ConceptValidationDto> validationResults = groupValidationResultsByConcept(report, shaclRulesModel);

        return new DetailedValidationReportDto(ontologyInfo, validationResults);
    }

    @Override
    public String generateCSV(DetailedValidationReportDto report) {
        log.info("Converting detailed report to CSV format");

        StringWriter writer = new StringWriter();

        writer.append("Concept_IRI,Concept_Name,Rule_IRI,Rule_Name,Rule_Description,Severity,Level,Violation_Message,Violating_Value\n");

        report.validation().forEach((conceptIri, conceptValidation) -> {
            String conceptName = extractConceptName(conceptIri);

            if (conceptValidation.violations().isEmpty()) {
                writer.append(String.format("%s,%s,,,,,No violations found,%n",
                        escapeCSV(conceptIri), escapeCSV(conceptName)));
            } else {
                conceptValidation.violations().forEach((ruleIri, violation) -> writer.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        escapeCSV(conceptIri),
                        escapeCSV(conceptName),
                        escapeCSV(ruleIri),
                        escapeCSV(violation.name()),
                        escapeCSV(violation.description()),
                        escapeCSV(violation.severity()),
                        escapeCSV(violation.level()),
                        escapeCSV(violation.message()),
                        escapeCSV(violation.value())
                )));
            }
        });

        return writer.toString();
    }

    @Override
    public DetailedValidationReportDto generateCombinedDetailedReport(
            ISMDValidationReport localReport,
            ISMDValidationReport globalReport,
            Model ontologyModel) {
        return generateCombinedDetailedReport(localReport, globalReport, ontologyModel, null);
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

        return generateDetailedReport(combinedReport, ontologyModel, shaclRulesModel);
    }

    private OntologyInfoDto extractOntologyInfo(Model ontologyModel) {
        Map<String, String> name = new HashMap<>();
        Map<String, String> description = new HashMap<>();

        ontologyModel.listResourcesWithProperty(RDFS.label).forEachRemaining(resource -> {
            resource.listProperties(RDFS.label).forEachRemaining(stmt -> {
                String lang = stmt.getLanguage();
                String value = stmt.getString();
                if (lang != null && !lang.isEmpty()) {
                    name.put(lang, value);
                } else {
                    name.put("cs", value);
                }
            });
        });

        ontologyModel.listResourcesWithProperty(RDFS.comment).forEachRemaining(resource -> {
            resource.listProperties(RDFS.comment).forEachRemaining(stmt -> {
                String lang = stmt.getLanguage();
                String value = stmt.getString();
                if (lang != null && !lang.isEmpty()) {
                    description.put(lang, value);
                } else {
                    description.put("cs", value);
                }
            });
        });

        if (name.isEmpty()) {
            name.put("cs", "Neznámý slovník");
            name.put("en", "Unknown ontology");
        }
        if (description.isEmpty()) {
            description.put("cs", "Popis není k dispozici");
        }

        return new OntologyInfoDto(name, description);
    }

    private Map<String, ConceptValidationDto> groupValidationResultsByConcept(ISMDValidationReport report, Model shaclRulesModel) {
        Map<String, ConceptValidationDto> conceptValidations = new HashMap<>();

        Map<String, SHACLRuleMetadataExtractor.RuleMetadata> ruleMetadataMap;
        if (shaclRulesModel != null) {
            ruleMetadataMap = metadataExtractor.extractAllRuleMetadata(shaclRulesModel);
        } else {
            ruleMetadataMap = new HashMap<>();
        }

        Map<String, List<ValidationResult>> resultsByConcept = report.results().stream()
                .filter(result -> result.focusNodeUri() != null) // Only results with focus nodes
                .collect(Collectors.groupingBy(ValidationResult::focusNodeUri));

        resultsByConcept.forEach((conceptIri, results) -> {
            Map<String, RuleViolationDto> ruleViolations = new HashMap<>();

            Map<String, List<ValidationResult>> resultsByRule = results.stream()
                    .collect(Collectors.groupingBy(ValidationResult::ruleName));

            resultsByRule.forEach((ruleName, ruleResults) -> {
                ValidationResult firstResult = ruleResults.get(0);

                SHACLRuleMetadataExtractor.RuleMetadata ruleMetadata = ruleMetadataMap.get(ruleName);

                String ruleNameDisplay = ruleMetadata != null ? ruleMetadata.name() : getRuleName(ruleName);
                String ruleDescription = ruleMetadata != null ? ruleMetadata.description() : getRuleDescription(ruleName);

                RuleViolationDto violation = new RuleViolationDto(
                        ruleNameDisplay,
                        ruleDescription,
                        mapSeverityToLevel(firstResult.severity()),
                        mapSeverityToIri(firstResult.severity()),
                        firstResult.message(),
                        firstResult.value()
                );

                ruleViolations.put(ruleName, violation);
            });

            conceptValidations.put(conceptIri, new ConceptValidationDto(conceptIri, ruleViolations));
        });

        return conceptValidations;
    }

    private String getRuleName(String ruleName) {
        // TODO
        return ruleName;
    }

    private String getRuleDescription(String ruleName) {
        // TODO
        return ruleName;
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

    private String escapeCSV(String value) {
        if (value == null) return "";

        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}
