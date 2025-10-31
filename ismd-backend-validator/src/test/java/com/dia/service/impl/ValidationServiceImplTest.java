package com.dia.service.impl;

import com.dia.conversion.data.TransformationResult;
import com.dia.service.record.ValidationConfigurationSummary;
import com.dia.validation.config.RuleManager;
import com.dia.validation.config.ValidationConfiguration;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.enums.ValidationTiming;
import com.dia.validation.engine.SHACLRuleEngine;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class ValidationServiceImplTest {

    @Mock
    private SHACLRuleEngine shaclEngine;

    @Mock
    private RuleManager ruleManager;

    @Mock
    private ValidationConfiguration config;

    @Mock
    private TransformationResult transformationResult;

    @Mock
    private OntModel ontModel;

    @Mock
    private Model model;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private ISMDValidationReport validationReport;

    @InjectMocks
    private ValidationServiceImpl validationService;

    @BeforeEach
    void setUp() {
        when(config.getDefaultTiming()).thenReturn(ValidationTiming.BEFORE_EXPORT);
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);
    }

    @Test
    void testValidate_WithDefaultTiming() {
        // Arrange
        when(transformationResult.getOntModel()).thenReturn(ontModel);

        // Act
        ISMDValidationReport result = validationService.validate(transformationResult);

        // Assert
        assertNotNull(result);
        assertEquals(validationReport, result);
        verify(config).getDefaultTiming();
        verify(shaclEngine).validate(ontModel);
    }

    @Test
    void testValidate_WithSpecificTiming_BeforeExport() {
        // Arrange
        when(transformationResult.getOntModel()).thenReturn(ontModel);

        // Act
        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.BEFORE_EXPORT);

        // Assert
        assertNotNull(result);
        assertEquals(validationReport, result);
        verify(shaclEngine).validate(ontModel);
    }

    @Test
    void testValidate_WithUnknownTiming() {
        // Arrange
        ValidationTiming unknownTiming = null;

        // Act & Assert
        ISMDValidationReport result = validationService.validate(transformationResult, unknownTiming);

        assertNotNull(result);
    }

    @Test
    void testValidate_JsonExportConversionFails() {
        // Arrange
        when(transformationResult.getOntModel()).thenReturn(ontModel);

        doThrow(new RuntimeException("JSON-LD conversion failed"))
                .when(ontModel).write(any(StringWriter.class), eq("JSON-LD"));

        // Act
        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.JSON_EXPORT);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testValidateModel() {
        // Act
        ISMDValidationReport result = validationService.validateModel(model);

        // Assert
        assertNotNull(result);
        assertEquals(validationReport, result);
        verify(shaclEngine).validate(model);
    }

    @Test
    void testValidateRdf_ParseError() {
        // Arrange
        String invalidRdfContent = "invalid rdf content";
        String format = "TTL";

        try (MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class)) {
            Model parsedModel = mock(Model.class);
            modelFactory.when(ModelFactory::createDefaultModel).thenReturn(parsedModel);
            doThrow(new RiotException("Parse error")).when(parsedModel).read((InputStream) any(), isNull(), eq(format));

            // Act
            ISMDValidationReport result = validationService.validateRdf(invalidRdfContent, format);

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    void testValidateTtl_Success() {
        // Arrange
        String ttlContent = "@prefix ex: <http://example.org/> . ex:test a ex:Class .";

        try (MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<RDFDataMgr> rdfDataMgr = mockStatic(RDFDataMgr.class)) {

            Model parsedModel = mock(Model.class);
            modelFactory.when(ModelFactory::createDefaultModel).thenReturn(parsedModel);
            when(parsedModel.size()).thenReturn(1L);

            rdfDataMgr.when(() -> RDFDataMgr.read(any(Model.class), any(InputStream.class), eq(Lang.TTL)))
                    .thenAnswer(invocation -> null);

            // Act
            ISMDValidationReport result = validationService.validateTtl(ttlContent);

            // Assert
            assertNotNull(result);
            assertEquals(validationReport, result);
            verify(shaclEngine).validate(parsedModel);
        }
    }

    @Test
    void testValidateTtl_ParseError() {
        // Arrange
        String invalidTtlContent = "invalid ttl content";

        try (MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<RDFDataMgr> rdfDataMgr = mockStatic(RDFDataMgr.class)) {

            Model parsedModel = mock(Model.class);
            modelFactory.when(ModelFactory::createDefaultModel).thenReturn(parsedModel);

            rdfDataMgr.when(() -> RDFDataMgr.read(any(Model.class), any(InputStream.class), eq(Lang.TTL)))
                    .thenThrow(new RiotException("Parse error"));

            // Act
            ISMDValidationReport result = validationService.validateTtl(invalidTtlContent);

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    void testValidateTtlFile_Success() throws IOException {
        // Arrange
        String ttlContent = "@prefix ex: <http://example.org/> . ex:test a ex:Class .";
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.ttl");
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getBytes()).thenReturn(ttlContent.getBytes(StandardCharsets.UTF_8));

        try (MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<RDFDataMgr> rdfDataMgr = mockStatic(RDFDataMgr.class)) {

            Model parsedModel = mock(Model.class);
            modelFactory.when(ModelFactory::createDefaultModel).thenReturn(parsedModel);
            when(parsedModel.size()).thenReturn(1L);

            rdfDataMgr.when(() -> RDFDataMgr.read(any(Model.class), any(InputStream.class), eq(Lang.TTL)))
                    .thenAnswer(invocation -> null);

            // Act
            ISMDValidationReport result = validationService.validateTtlFile(multipartFile);

            // Assert
            assertNotNull(result);
            assertEquals(validationReport, result);
            verify(shaclEngine).validate(parsedModel);
        }
    }

    @Test
    void testValidateTtlFile_EmptyFile() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(true);

        // Act
        ISMDValidationReport result = validationService.validateTtlFile(multipartFile);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testValidateTtlFile_InvalidExtension() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");

        // Act
        ISMDValidationReport result = validationService.validateTtlFile(multipartFile);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testValidateTtlFile_NullFilename() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(null);

        // Act
        ISMDValidationReport result = validationService.validateTtlFile(multipartFile);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testValidateTtlFile_IoException() throws IOException {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.ttl");
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getBytes()).thenThrow(new IOException("File read error"));

        // Act
        ISMDValidationReport result = validationService.validateTtlFile(multipartFile);

        // Assert
        assertNotNull(result);
    }

    @Test
    void testGetConfigurationSummary() {
        // Arrange
        Set<String> allRules = Set.of("rule1", "rule2", "rule3");
        Set<String> enabledRules = Set.of("rule1", "rule2");
        List<String> enabledRulesList = Arrays.asList("rule1", "rule2");

        when(ruleManager.getAllRuleNames()).thenReturn(allRules);
        when(ruleManager.getEnabledRuleNames()).thenReturn(enabledRules);
        when(config.getDefaultTiming()).thenReturn(ValidationTiming.BEFORE_EXPORT);

        // Act
        ValidationConfigurationSummary result = validationService.getConfigurationSummary();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.totalRules());
        assertEquals(2, result.enabledRules());
        assertEquals(enabledRulesList.size(), result.enabledRules());
        assertTrue(result.allEnabledRules().containsAll(enabledRulesList));
        assertEquals(ValidationTiming.BEFORE_EXPORT, result.defaultTiming());
    }

    @Test
    void testValidate_ExceptionHandling() {
        // Arrange
        when(transformationResult.getOntModel()).thenThrow(new RuntimeException("Unexpected error"));

        // Act
        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.BEFORE_EXPORT);

        // Assert
        assertNotNull(result);
    }
}