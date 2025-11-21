package com.dia.service.impl;

import com.dia.controller.exception.CatalogGenerationException;
import com.dia.controller.exception.EmptyContentException;
import com.dia.controller.exception.InvalidFileException;
import com.dia.controller.exception.ValidationException;
import com.dia.dto.CatalogRecordDto;
import com.dia.controller.dto.SeverityGroupDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class CatalogReportServiceImplTest {

    @InjectMocks
    private CatalogReportServiceImpl catalogService;

    @Mock
    private ConversionResult mockConversionResult;

    @Mock
    private TransformationResult mockTransformationResult;

    @Mock
    private OntologyData mockOntologyData;

    private OntModel sampleOntModel;
    private ValidationResultsDto validValidationResults;
    private ValidationResultsDto invalidValidationResults;

    @BeforeEach
    void setUp() {
        setupSampleOntModel();
        setupValidationResults();
        setupMockConversionResult();
    }

    private void setupSampleOntModel() {
        sampleOntModel = ModelFactory.createOntologyModel();

        // Create ontology with metadata
        Ontology ontology = sampleOntModel.createOntology("http://example.com/vocabulary");
        ontology.addProperty(RDFS.label, sampleOntModel.createLiteral("Test Vocabulary", "en"));
        ontology.addProperty(RDFS.label, sampleOntModel.createLiteral("Testovací slovník", "cs"));
        ontology.addProperty(DCTerms.description, sampleOntModel.createLiteral("Test description", "en"));
        ontology.addProperty(DCTerms.description, sampleOntModel.createLiteral("Testovací popis", "cs"));
    }

    private void setupValidationResults() {
        // Valid results - no errors
        List<SeverityGroupDto> validGroups = Arrays.asList(
                createSeverityGroup("WARNING", 2),
                createSeverityGroup("INFO", 5)
        );
        validValidationResults = new ValidationResultsDto(validGroups);

        // Invalid results - contains errors
        List<SeverityGroupDto> invalidGroups = Arrays.asList(
                createSeverityGroup("ERROR", 1),
                createSeverityGroup("WARNING", 2)
        );
        invalidValidationResults = new ValidationResultsDto(invalidGroups);
    }

    private SeverityGroupDto createSeverityGroup(String severity, int count) {
        SeverityGroupDto group = mock(SeverityGroupDto.class);
        when(group.getSeverity()).thenReturn(severity);
        when(group.getCount()).thenReturn(count);
        return group;
    }

    private void setupMockConversionResult() {
        when(mockConversionResult.getOntologyData()).thenReturn(mockOntologyData);
        when(mockConversionResult.getTransformationResult()).thenReturn(mockTransformationResult);
        when(mockTransformationResult.getOntModel()).thenReturn(sampleOntModel);
    }

    @Nested
    @DisplayName("generateCatalogReport Tests")
    class GenerateCatalogReportTests {

        @Test
        @DisplayName("Should generate catalog report successfully when validation passes")
        void shouldGenerateCatalogReportSuccessfullyWhenValidationPasses() {
            // Given
            String requestId = "test-request-123";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, requestId);

            // Then
            assertThat(result).isPresent();

            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord)
                    .satisfies(report -> {
                        assertThat(report.getContext()).isEqualTo("https://ofn.gov.cz/dcat-ap-cz-rozhraní-katalogů-otevřených-dat/2024-05-28/kontexty/rozhraní-katalogů-otevřených-dat.jsonld");
                        assertThat(report.getIri()).isEqualTo("http://example.com/vocabulary");
                        assertThat(report.getTyp()).isEqualTo("Datová sada");
                        assertThat(report.getNazev()).containsEntry("en", "Test Vocabulary");
                        assertThat(report.getNazev()).containsEntry("cs", "Testovací slovník");
                        assertThat(report.getPopis()).containsEntry("en", "Test description");
                        assertThat(report.getPopis()).containsEntry("cs", "Testovací popis");
                        assertThat(report.getDistribuce()).hasSize(1);
                        assertThat(report.getKlicoveSlovo()).containsEntry("cs", List.of("slovník"));
                        assertThat(report.getKlicoveSlovo()).containsEntry("en", List.of("vocabulary"));
                    });
        }

        @Test
        @DisplayName("Should skip catalog generation when validation contains errors")
        void shouldSkipCatalogGenerationWhenValidationContainsErrors() {
            // Given
            String requestId = "test-request-with-errors";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, invalidValidationResults, requestId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should skip catalog generation when validation results are null")
        void shouldSkipCatalogGenerationWhenValidationResultsAreNull() {
            // Given
            String requestId = "test-request-null-validation";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, null, requestId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle conversion result with empty ontology model")
        void shouldHandleConversionResultWithEmptyOntologyModel() {
            // Given
            OntModel emptyModel = ModelFactory.createOntologyModel();
            when(mockTransformationResult.getOntModel()).thenReturn(emptyModel);
            String requestId = "test-request-empty-model";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, requestId);

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getIri()).isEqualTo("_:ds");
            assertThat(catalogRecord.getNazev()).containsEntry("cs", "Converted Vocabulary");
            assertThat(catalogRecord.getPopis()).containsEntry("cs", "Popis slovníku");
        }

        @Test
        @DisplayName("Should return empty when exception occurs during processing")
        void shouldReturnEmptyWhenExceptionOccursDuringProcessing() {
            // Given
            when(mockTransformationResult.getOntModel()).thenThrow(new RuntimeException("Processing error"));
            String requestId = "test-request-exception";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, requestId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("generateCatalogReportFromFile Tests")
    class GenerateCatalogReportFromFileTests {

        @Test
        @DisplayName("Should generate catalog report from valid TTL file")
        void shouldGenerateCatalogReportFromValidTtlFile() throws IOException {
            // Given
            String validTtlContent = """
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix dcterms: <http://purl.org/dc/terms/> .
                
                <http://example.com/vocab> a owl:Ontology ;
                    rdfs:label "File Vocabulary"@en ;
                    rdfs:label "Souborový slovník"@cs ;
                    dcterms:description "Description from file"@en .
                """;

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getBytes()).thenReturn(validTtlContent.getBytes());
            String requestId = "test-file-request";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReportFromFile(
                    mockFile, validValidationResults, requestId);

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getIri()).isEqualTo("http://example.com/vocab");
            assertThat(catalogRecord.getNazev()).containsEntry("en", "File Vocabulary");
            assertThat(catalogRecord.getNazev()).containsEntry("cs", "Souborový slovník");
        }

        @Test
        @DisplayName("Should skip catalog generation from file when validation has errors")
        void shouldSkipCatalogGenerationFromFileWhenValidationHasErrors() throws IOException {
            // Given
            String ttlContent = "@prefix test: <http://test.com/> .";
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getBytes()).thenReturn(ttlContent.getBytes());
            String requestId = "test-file-errors";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReportFromFile(
                    mockFile, invalidValidationResults, requestId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw InvalidFileException when file is null")
        void shouldThrowInvalidFileExceptionWhenFileIsNull() {
            // Given
            String requestId = "test-null-file";

            // When & Then
            assertThatThrownBy(() -> catalogService.generateCatalogReportFromFile(
                    null, validValidationResults, requestId))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("File cannot be null");
        }

        @Test
        @DisplayName("Should throw EmptyContentException when file is empty")
        void shouldThrowEmptyContentExceptionWhenFileIsEmpty() throws IOException {
            // Given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(true);
            String requestId = "test-empty-file-exception";

            // When & Then
            assertThatThrownBy(() -> catalogService.generateCatalogReportFromFile(
                    mockFile, validValidationResults, requestId))
                    .isInstanceOf(EmptyContentException.class)
                    .hasMessageContaining("File is empty");
        }

        @Test
        @DisplayName("Should throw InvalidFileException when file reading fails")
        void shouldThrowInvalidFileExceptionWhenFileReadingFails() throws IOException {
            // Given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getBytes()).thenThrow(new IOException("File read error"));
            String requestId = "test-file-read-error";

            // When & Then
            assertThatThrownBy(() -> catalogService.generateCatalogReportFromFile(
                    mockFile, validValidationResults, requestId))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("Failed to read file");
        }

        @Test
        @DisplayName("Should throw ValidationException when TTL parsing fails")
        void shouldThrowValidationExceptionWhenTtlParsingFails() throws IOException {
            // Given
            String invalidTtlContent = "This is not valid TTL @#$%^&*";
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getBytes()).thenReturn(invalidTtlContent.getBytes());
            String requestId = "test-invalid-ttl";

            // When & Then
            assertThatThrownBy(() -> catalogService.generateCatalogReportFromFile(
                    mockFile, validValidationResults, requestId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invalid TTL syntax");
        }

        @Test
        @DisplayName("Should handle empty TTL content")
        void shouldHandleEmptyTtlContent() throws IOException {
            // Given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getBytes()).thenReturn("".getBytes());
            String requestId = "test-empty-content";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReportFromFile(
                    mockFile, validValidationResults, requestId);

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getIri()).isEqualTo("_:ds");
            assertThat(catalogRecord.getNazev()).containsEntry("cs", "Converted Vocabulary");
        }
    }

    @Nested
    @DisplayName("generateCatalogReportFromTool Tests")
    class GenerateCatalogReportFromToolTests {

        @Test
        @DisplayName("Should throw EmptyContentException when request is null")
        void shouldThrowEmptyContentExceptionWhenRequestIsNull() {
            // Given
            String requestId = "test-null-request";

            // When & Then
            assertThatThrownBy(() -> catalogService.generateCatalogReportFromTool(
                    null, requestId))
                    .isInstanceOf(EmptyContentException.class)
                    .hasMessageContaining("Request cannot be null");
        }
    }

    @Nested
    @DisplayName("Validation Logic Tests")
    class ValidationLogicTests {

        @Test
        @DisplayName("Should allow generation when validation results have no severity groups")
        void shouldAllowGenerationWhenValidationResultsHaveNoSeverityGroups() {
            // Given
            ValidationResultsDto emptyValidation = new ValidationResultsDto(Collections.emptyList());
            String requestId = "test-empty-groups";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, emptyValidation, requestId);

            // Then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should skip generation when ERROR count is greater than zero")
        void shouldSkipGenerationWhenErrorCountIsGreaterThanZero() {
            // Given
            List<SeverityGroupDto> groupsWithErrors = List.of(
                    createSeverityGroup("ERROR", 3),
                    createSeverityGroup("WARNING", 0)
            );
            ValidationResultsDto validationWithErrors = new ValidationResultsDto(groupsWithErrors);
            String requestId = "test-error-count";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validationWithErrors, requestId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should allow generation when ERROR count is zero")
        void shouldAllowGenerationWhenErrorCountIsZero() {
            // Given
            List<SeverityGroupDto> groupsWithoutErrors = List.of(
                    createSeverityGroup("ERROR", 0),
                    createSeverityGroup("WARNING", 5)
            );
            ValidationResultsDto validationWithoutErrors = new ValidationResultsDto(groupsWithoutErrors);
            String requestId = "test-zero-errors";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validationWithoutErrors, requestId);

            // Then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should handle case insensitive severity matching")
        void shouldHandleCaseInsensitiveSeverityMatching() {
            // Given
            List<SeverityGroupDto> mixedCaseGroups = Arrays.asList(
                    createSeverityGroup("error", 1),
                    createSeverityGroup("Error", 0),
                    createSeverityGroup("ERROR", 2)
            );
            ValidationResultsDto mixedCaseValidation = new ValidationResultsDto(mixedCaseGroups);
            String requestId = "test-case-insensitive";

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, mixedCaseValidation, requestId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Metadata Extraction Tests")
    class MetadataExtractionTests {

        @Test
        @DisplayName("Should extract metadata from ontology with full multilingual properties")
        void shouldExtractMetadataFromOntologyWithFullMultilingualProperties() {
            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-multilingual");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getNazev())
                    .containsEntry("en", "Test Vocabulary")
                    .containsEntry("cs", "Testovací slovník");
            assertThat(catalogRecord.getPopis())
                    .containsEntry("en", "Test description")
                    .containsEntry("cs", "Testovací popis");
        }

        @Test
        @DisplayName("Should handle ontology with only English labels")
        void shouldHandleOntologyWithOnlyEnglishLabels() {
            // Given
            OntModel englishOnlyModel = ModelFactory.createOntologyModel();
            Ontology ontology = englishOnlyModel.createOntology("http://example.com/en-vocab");
            ontology.addProperty(RDFS.label, englishOnlyModel.createLiteral("English Only Vocab", "en"));
            when(mockTransformationResult.getOntModel()).thenReturn(englishOnlyModel);

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-english-only");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getNazev()).containsEntry("en", "English Only Vocab");
            assertThat(catalogRecord.getPopis())
                    .containsEntry("cs", "Popis slovníku")
                    .containsEntry("en", "Vocabulary description");
        }

        @Test
        @DisplayName("Should handle ontology with no language tags")
        void shouldHandleOntologyWithNoLanguageTags() {
            // Given
            OntModel noLangModel = ModelFactory.createOntologyModel();
            Ontology ontology = noLangModel.createOntology("http://example.com/no-lang");
            ontology.addProperty(RDFS.label, noLangModel.createLiteral("No Language Vocab"));
            when(mockTransformationResult.getOntModel()).thenReturn(noLangModel);

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-no-lang");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getNazev())
                    .containsEntry("cs", "No Language Vocab")
                    .containsEntry("en", "No Language Vocab");
        }

        @Test
        @DisplayName("Should fallback to resource with rdfs:label when no ontology found")
        void shouldFallbackToResourceWithRdfsLabelWhenNoOntologyFound() {
            // Given
            OntModel modelWithResource = ModelFactory.createOntologyModel();
            Resource resource = modelWithResource.createResource("http://example.com/resource");
            resource.addProperty(RDFS.label, modelWithResource.createLiteral("Resource Label", "en"));
            when(mockTransformationResult.getOntModel()).thenReturn(modelWithResource);

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-fallback-resource");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getIri()).isEqualTo("http://example.com/resource");
            assertThat(catalogRecord.getNazev()).containsEntry("en", "Resource Label");
        }

        @Test
        @DisplayName("Should extract local name from IRI for default title")
        void shouldExtractLocalNameFromIriForDefaultTitle() {
            // Given
            OntModel modelWithIri = ModelFactory.createOntologyModel();
            modelWithIri.createOntology("http://example.com/path/vocabularyName");
            when(mockTransformationResult.getOntModel()).thenReturn(modelWithIri);

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-local-name");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getNazev()).containsEntry("cs", "vocabularyName");
        }

        @Test
        @DisplayName("Should handle IRI with hash fragment")
        void shouldHandleIriWithHashFragment() {
            // Given
            OntModel modelWithHash = ModelFactory.createOntologyModel();
            modelWithHash.createOntology("http://example.com/vocab#MainConcept");
            when(mockTransformationResult.getOntModel()).thenReturn(modelWithHash);

            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-hash-fragment");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();
            assertThat(catalogRecord.getNazev()).containsEntry("cs", "MainConcept");
        }
    }

    @Nested
    @DisplayName("Distribution and Structure Tests")
    class DistributionAndStructureTests {

        @Test
        @DisplayName("Should create catalog record with correct default structure")
        void shouldCreateCatalogRecordWithCorrectDefaultStructure() {
            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-structure");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto catalogRecord = result.get();

            assertThat(catalogRecord)
                    .satisfies(report -> {
                        assertThat(report.getPrvekRuian()).containsExactly("https://linked.cuzk.cz/resource/ruian/stat/1");
                        assertThat(report.getGeografickeUzemi()).isEmpty();
                        assertThat(report.getProstorovePokryti()).isEmpty();
                        assertThat(report.getPeriodicitaAktualizace()).isEqualTo("http://publications.europa.eu/resource/authority/frequency/IRREG");
                        assertThat(report.getTema()).isEmpty();
                        assertThat(report.getKonceptEuroVoc()).containsExactly("http://eurovoc.europa.eu/438");
                        assertThat(report.getSpecifikace()).containsExactly("https://ofn.gov.cz/slovníky/draft/");
                        assertThat(report.getKontaktniBod()).isEmpty();
                        assertThat(report.getDistribuce()).hasSize(1);
                    });
        }

        @Test
        @DisplayName("Should create distribution with correct default values")
        void shouldCreateDistributionWithCorrectDefaultValues() {
            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-distribution");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto.DistribuceDto distribution = result.get().getDistribuce().get(0);

            assertThat(distribution)
                    .satisfies(dist -> {
                        assertThat(dist.getTyp()).isEqualTo("Distribuce");
                        assertThat(dist.getSouborKeStazeni()).isEmpty();
                        assertThat(dist.getPristupoveUrl()).isEmpty();
                        assertThat(dist.getTypMedia()).isEqualTo("http://www.iana.org/assignments/media-types/text/turtle");
                        assertThat(dist.getFormat()).isEqualTo("http://publications.europa.eu/resource/authority/file-type/RDF_TURTLE");
                        assertThat(dist.getSchema()).isEqualTo("https://ofn.gov.cz/slovníky/draft/schémata/konceptuální-model.json");
                        assertThat(dist.getPodminkyUziti()).isNotNull();
                    });
        }

        @Test
        @DisplayName("Should create usage conditions with correct default values")
        void shouldCreateUsageConditionsWithCorrectDefaultValues() {
            // When
            Optional<CatalogRecordDto> result = catalogService.generateCatalogReport(
                    mockConversionResult, validValidationResults, "test-usage-conditions");

            // Then
            assertThat(result).isPresent();
            CatalogRecordDto.PodminkyUzitiDto conditions = result.get().getDistribuce().get(0).getPodminkyUziti();

            assertThat(conditions)
                    .satisfies(cond -> {
                        assertThat(cond.getTyp()).isEqualTo("Specifikace podmínek užití");
                        assertThat(cond.getAutorskeDilo()).isEqualTo("https://data.gov.cz/podmínky-užití/neobsahuje-autorská-díla/");
                        assertThat(cond.getDatabizeJakoAutorskeDilo()).isEqualTo("https://data.gov.cz/podmínky-užití/není-autorskoprávně-chráněnou-databází/");
                        assertThat(cond.getDatabizeChranenaZvlastnimiPravy()).isEqualTo("https://data.gov.cz/podmínky-užití/není-chráněna-zvláštním-právem-pořizovatele-databáze/");
                        assertThat(cond.getOsobniUdaje()).isEqualTo("https://data.gov.cz/podmínky-užití/neobsahuje-osobní-údaje/");
                    });
        }
    }
}
