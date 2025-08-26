package com.dia.service.impl;

import com.dia.enums.ValidationSeverity;
import com.dia.exceptions.ValidationException;
import com.dia.validation.data.ConceptValidationDto;
import com.dia.validation.data.DetailedValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.validation.data.ValidationResult;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class DetailedValidationReportServiceTest {

    @InjectMocks
    private DetailedValidationReportServiceImpl validationService;

    private ISMDValidationReport mockValidationReport;
    private Model mockOntologyModel;
    private List<ValidationResult> sampleValidationResults;

    @BeforeEach
    void setUp() {
        setupSampleValidationResults();
        setupMockValidationReport();
        setupMockOntologyModel();
    }

    private void setupSampleValidationResults() {
        sampleValidationResults = Arrays.asList(
                new ValidationResult(
                        ValidationSeverity.ERROR,
                        "Missing required property",
                        "required-property-rule",
                        "http://example.com/concept1",
                        "http://example.com/property1",
                        "test-value"
                ),
                new ValidationResult(
                        ValidationSeverity.WARNING,
                        "Deprecated property used",
                        "deprecated-property-rule",
                        "http://example.com/concept1",
                        "http://example.com/property2",
                        "deprecated-value"
                ),
                new ValidationResult(
                        ValidationSeverity.INFO,
                        "Information message",
                        "info-rule",
                        "http://example.com/concept2",
                        "http://example.com/property3",
                        "info-value"
                )
        );
    }

    private void setupMockValidationReport() {
        mockValidationReport = new ISMDValidationReport(
                sampleValidationResults,
                false,
                Instant.now()
        );
    }

    private void setupMockOntologyModel() {
        mockOntologyModel = ModelFactory.createDefaultModel();

        // Create ontology resource with labels and descriptions
        Resource ontologyResource = mockOntologyModel.createResource("http://example.com/ontology");
        ontologyResource.addProperty(RDF.type, SKOS.ConceptScheme);
        ontologyResource.addProperty(SKOS.prefLabel,
                mockOntologyModel.createLiteral("Test Ontology", "en"));
        ontologyResource.addProperty(SKOS.prefLabel,
                mockOntologyModel.createLiteral("Testovací slovník", "cs"));
        ontologyResource.addProperty(DCTerms.description,
                mockOntologyModel.createLiteral("Test description", "en"));
    }

    @Nested
    @DisplayName("generateDetailedReport Tests")
    class GenerateDetailedReportTests {

        @Test
        @DisplayName("Should generate detailed report successfully with valid input")
        void shouldGenerateDetailedReportSuccessfully() {
            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    mockValidationReport, mockOntologyModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.ontology()).isNotNull();
            assertThat(result.validation()).isNotNull();
            assertThat(result.validation()).hasSize(2);

            assertThat(result.ontology().name()).containsEntry("en", "Test Ontology");
            assertThat(result.ontology().name()).containsEntry("cs", "Testovací slovník");

            assertThat(result.validation()).containsKey("http://example.com/concept1");
            assertThat(result.validation()).containsKey("http://example.com/concept2");
        }

        @Test
        @DisplayName("Should handle empty validation results")
        void shouldHandleEmptyValidationResults() {
            // Given
            ISMDValidationReport emptyReport = new ISMDValidationReport(
                    Collections.emptyList(), true, Instant.now());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    emptyReport, mockOntologyModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.validation()).isEmpty();
            assertThat(result.ontology()).isNotNull();
        }

        @Test
        @DisplayName("Should handle validation results with null focus node")
        void shouldHandleValidationResultsWithNullFocusNode() {
            // Given
            List<ValidationResult> resultsWithNullFocus = List.of(
                    new ValidationResult(
                            ValidationSeverity.ERROR,
                            "System error",
                            "system-rule",
                            null,
                            null,
                            "error-value"
                    )
            );
            ISMDValidationReport reportWithNullFocus = new ISMDValidationReport(
                    resultsWithNullFocus, false, Instant.now());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    reportWithNullFocus, mockOntologyModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.validation()).isEmpty();
        }

        @Test
        @DisplayName("Should handle ontology model without metadata")
        void shouldHandleOntologyModelWithoutMetadata() {
            // Given
            Model emptyModel = ModelFactory.createDefaultModel();

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    mockValidationReport, emptyModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.ontology().name()).containsEntry("cs", "Neznámý slovník");
        }
    }

    @Nested
    @DisplayName("generateCSV Tests")
    class GenerateCSVTests {

        @Test
        @DisplayName("Should generate CSV successfully with violations")
        void shouldGenerateCSVSuccessfullyWithViolations() {
            // Given
            DetailedValidationReportDto report = validationService.generateDetailedReport(
                    mockValidationReport, mockOntologyModel);

            // When
            String csvResult = validationService.generateCSV(report);

            // Then
            assertThat(csvResult)
                    .isNotEmpty()
                    .contains("Concept_IRI,Concept_Name,Rule_IRI,Rule_Name")
                    .contains("concept1")
                    .contains("concept2")
                    .contains("required-property-rule")
                    .contains("Missing required property")
                    .contains("Vysoká")
                    .contains("Střední");
        }

        @Test
        @DisplayName("Should generate CSV with no violations message")
        void shouldGenerateCSVWithNoViolationsMessage() {
            // Given
            ISMDValidationReport emptyReport = new ISMDValidationReport(
                    Collections.emptyList(), true, Instant.now());
            DetailedValidationReportDto report = validationService.generateDetailedReport(
                    emptyReport, mockOntologyModel);

            // When
            String csvResult = validationService.generateCSV(report);

            // Then
            assertThat(csvResult).contains("Concept_IRI,Concept_Name,Rule_IRI,Rule_Name");
        }

        @Test
        @DisplayName("Should handle null values in CSV generation")
        void shouldHandleNullValuesInCSVGeneration() {
            // Given
            List<ValidationResult> resultsWithNulls = List.of(
                    new ValidationResult(
                            ValidationSeverity.ERROR,
                            null,
                            null,
                            "http://example.com/concept1",
                            null,
                            null
                    )
            );
            ISMDValidationReport reportWithNulls = new ISMDValidationReport(
                    resultsWithNulls, false, Instant.now());
            DetailedValidationReportDto report = validationService.generateDetailedReport(
                    reportWithNulls, mockOntologyModel);

            // When & Then
            assertThatCode(() -> validationService.generateCSV(report))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should normalize special characters in CSV")
        void shouldNormalizeSpecialCharactersInCSV() {
            // Given
            List<ValidationResult> resultsWithSpecialChars = List.of(
                    new ValidationResult(
                            ValidationSeverity.ERROR,
                            "Message with áčďťů special chars",
                            "rule-with-special-chars",
                            "http://example.com/concept-with-čšž",
                            null,
                            "value with  extra  spaces  "
                    )
            );
            ISMDValidationReport report = new ISMDValidationReport(
                    resultsWithSpecialChars, false, Instant.now());
            DetailedValidationReportDto detailedReport = validationService.generateDetailedReport(
                    report, mockOntologyModel);

            // When
            String csvResult = validationService.generateCSV(detailedReport);

            // Then
            assertThat(csvResult)
                    .contains("concept-with-čšž")
                    .contains("Message with áčďťů special chars");
        }
    }

    @Nested
    @DisplayName("generateCombinedDetailedReport Tests")
    class GenerateCombinedDetailedReportTests {

        @Test
        @DisplayName("Should combine local and global reports successfully")
        void shouldCombineLocalAndGlobalReportsSuccessfully() {
            // Given
            ISMDValidationReport localReport = new ISMDValidationReport(
                    Collections.singletonList(sampleValidationResults.get(0)), false, Instant.now());
            ISMDValidationReport globalReport = new ISMDValidationReport(
                    Collections.singletonList(sampleValidationResults.get(1)), false, Instant.now());
            Model shaclRulesModel = ModelFactory.createDefaultModel();

            // When
            DetailedValidationReportDto result = validationService.generateCombinedDetailedReport(
                    localReport, globalReport, mockOntologyModel, shaclRulesModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.validation()).isNotEmpty();

            boolean hasGlobalPrefix = result.validation().values().stream()
                    .flatMap(concept -> concept.violations().values().stream())
                    .anyMatch(violation -> violation.description().startsWith("[GLOBAL]"));
            assertThat(hasGlobalPrefix).isTrue();
        }

        @Test
        @DisplayName("Should handle empty local and global reports")
        void shouldHandleEmptyLocalAndGlobalReports() {
            // Given
            ISMDValidationReport emptyLocalReport = ISMDValidationReport.empty();
            ISMDValidationReport emptyGlobalReport = ISMDValidationReport.empty();
            Model shaclRulesModel = ModelFactory.createDefaultModel();

            // When
            DetailedValidationReportDto result = validationService.generateCombinedDetailedReport(
                    emptyLocalReport, emptyGlobalReport, mockOntologyModel, shaclRulesModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.validation()).isEmpty();
        }

        @Test
        @DisplayName("Should correctly combine validation status")
        void shouldCorrectlyCombineValidationStatus() {
            // Given
            ISMDValidationReport validLocalReport = new ISMDValidationReport(
                    Collections.emptyList(), true, Instant.now());
            ISMDValidationReport invalidGlobalReport = new ISMDValidationReport(
                    Collections.singletonList(sampleValidationResults.get(0)), false, Instant.now());
            Model shaclRulesModel = ModelFactory.createDefaultModel();

            // When
            DetailedValidationReportDto result = validationService.generateCombinedDetailedReport(
                    validLocalReport, invalidGlobalReport, mockOntologyModel, shaclRulesModel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.validation()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("generateDetailedReportFromTtlFile Tests")
    class GenerateDetailedReportFromTtlFileTests {

        @Test
        @DisplayName("Should generate report from valid TTL file")
        void shouldGenerateReportFromValidTtlFile() throws IOException {
            // Given
            String validTtlContent = """
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                @prefix dcterms: <http://purl.org/dc/terms/> .
                
                <http://example.com/ontology> a skos:ConceptScheme ;
                    skos:prefLabel "Test Ontology"@en ;
                    dcterms:description "Test description"@en .
                """;

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("test.ttl");
            when(mockFile.getBytes()).thenReturn(validTtlContent.getBytes());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReportFromTtlFile(
                    mockValidationReport, mockFile);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.ontology()).isNotNull();
            assertThat(result.validation()).isNotEmpty();
        }

        @Test
        @DisplayName("Should throw ValidationException for invalid TTL syntax")
        void shouldThrowValidationExceptionForInvalidTtlSyntax() throws IOException {
            // Given
            String invalidTtlContent = "This is not valid TTL syntax @#$%^&*";

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("invalid.ttl");
            when(mockFile.getBytes()).thenReturn(invalidTtlContent.getBytes());

            // When & Then
            assertThatThrownBy(() -> validationService.generateDetailedReportFromTtlFile(
                    mockValidationReport, mockFile))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invalid TTL syntax");
        }

        @Test
        @DisplayName("Should throw ValidationException when file reading fails")
        void shouldThrowValidationExceptionWhenFileReadingFails() throws IOException {
            // Given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("test.ttl");
            when(mockFile.getBytes()).thenThrow(new IOException("File read error"));

            // When & Then
            assertThatThrownBy(() -> validationService.generateDetailedReportFromTtlFile(
                    mockValidationReport, mockFile))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Failed to read TTL file");
        }

        @Test
        @DisplayName("Should handle empty TTL file")
        void shouldHandleEmptyTtlFile() throws IOException {
            // Given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("empty.ttl");
            when(mockFile.getBytes()).thenReturn("".getBytes());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReportFromTtlFile(
                    mockValidationReport, mockFile);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.ontology().name()).containsEntry("cs", "Neznámý slovník");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle concept IRI extraction from various formats")
        void shouldHandleConceptIriExtractionFromVariousFormats() {
            // Given
            List<ValidationResult> resultsWithDifferentIris = Arrays.asList(
                    new ValidationResult(ValidationSeverity.ERROR, "msg", "rule",
                            "http://example.com/concept1", null, null),
                    new ValidationResult(ValidationSeverity.ERROR, "msg", "rule",
                            "http://example.com/path#fragment", null, null),
                    new ValidationResult(ValidationSeverity.ERROR, "msg", "rule",
                            "simple-iri", null, null),
                    new ValidationResult(ValidationSeverity.ERROR, "msg", "rule",
                            "", null, null)
            );
            ISMDValidationReport report = new ISMDValidationReport(
                    resultsWithDifferentIris, false, Instant.now());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    report, mockOntologyModel);

            // Then
            assertThat(result.validation()).containsKey("http://example.com/concept1");
            assertThat(result.validation()).containsKey("http://example.com/path#fragment");
            assertThat(result.validation()).containsKey("simple-iri");
            assertThat(result.validation()).containsKey("");
        }

        @Test
        @DisplayName("Should handle unknown rule names correctly")
        void shouldHandleUnknownRuleNamesCorrectly() {
            // Given
            List<ValidationResult> resultsWithUnknownRules = Arrays.asList(
                    new ValidationResult(ValidationSeverity.ERROR, "msg", "unknown-rule",
                            "http://example.com/concept1", null, null),
                    new ValidationResult(ValidationSeverity.ERROR, "msg", "some-other-rule",
                            "http://example.com/concept1", null, null)
            );
            ISMDValidationReport report = new ISMDValidationReport(
                    resultsWithUnknownRules, false, Instant.now());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    report, mockOntologyModel);

            // Then
            ConceptValidationDto conceptValidation = result.validation().get("http://example.com/concept1");
            assertThat(conceptValidation.violations()).hasSize(2);

            boolean hasUnknownRuleName = conceptValidation.violations().values().stream()
                    .anyMatch(v -> v.name().equals("Neznámé pravidlo"));
            assertThat(hasUnknownRuleName).isTrue();
        }

        @Test
        @DisplayName("Should handle multiple violations for same rule")
        void shouldHandleMultipleViolationsForSameRule() {
            // Given
            List<ValidationResult> multipleViolations = Arrays.asList(
                    new ValidationResult(ValidationSeverity.ERROR, "First violation", "same-rule",
                            "http://example.com/concept1", null, "value1"),
                    new ValidationResult(ValidationSeverity.ERROR, "Second violation", "same-rule",
                            "http://example.com/concept1", null, "value2")
            );
            ISMDValidationReport report = new ISMDValidationReport(
                    multipleViolations, false, Instant.now());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    report, mockOntologyModel);

            // Then
            ConceptValidationDto conceptValidation = result.validation().get("http://example.com/concept1");
            assertThat(conceptValidation.violations()).hasSize(1);
            assertThat(conceptValidation.violations()).containsKey("same-rule");
        }

        @Test
        @DisplayName("Should handle severity mapping correctly")
        void shouldHandleSeverityMappingCorrectly() {
            // Given
            List<ValidationResult> allSeverities = Arrays.asList(
                    new ValidationResult(ValidationSeverity.ERROR, "Error msg", "error-rule",
                            "http://example.com/concept1", null, null),
                    new ValidationResult(ValidationSeverity.WARNING, "Warning msg", "warning-rule",
                            "http://example.com/concept2", null, null),
                    new ValidationResult(ValidationSeverity.INFO, "Info msg", "info-rule",
                            "http://example.com/concept3", null, null)
            );
            ISMDValidationReport report = new ISMDValidationReport(
                    allSeverities, false, Instant.now());

            // When
            DetailedValidationReportDto result = validationService.generateDetailedReport(
                    report, mockOntologyModel);

            // Then
            String csv = validationService.generateCSV(result);
            assertThat(csv)
                    .contains("Vysoká")
                    .contains("Střední")
                    .contains("Nízká")
                    .contains("http://www.w3.org/ns/shacl#Violation")
                    .contains("http://www.w3.org/ns/shacl#Warning")
                    .contains("http://www.w3.org/ns/shacl#Info");
        }
    }
}
