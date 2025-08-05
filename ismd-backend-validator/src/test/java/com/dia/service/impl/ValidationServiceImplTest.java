package com.dia.service.impl;

import com.dia.conversion.data.TransformationResult;
import com.dia.service.record.ValidationConfigurationSummary;
import com.dia.validation.config.RuleManager;
import com.dia.validation.config.ValidationConfiguration;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.enums.ValidationTiming;
import com.dia.validation.engine.SHACLRuleEngine;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.ontology.OntModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    private ISMDValidationReport validationReport;

    @InjectMocks
    private ValidationServiceImpl validationService;

    private Model testModel;
    private OntModel testOntModel;

    @BeforeEach
    void setUp() {
        testModel = ModelFactory.createDefaultModel();
        testModel.createResource("http://example.org/resource1")
                .addProperty(testModel.createProperty("http://example.org/property1"), "value1");

        testOntModel = ModelFactory.createOntologyModel();
        testOntModel.createResource("http://example.org/ontResource")
                .addProperty(testOntModel.createProperty("http://example.org/ontProperty"), "ontValue");
    }

    @Test
    void validate_WithTransformationResult_ShouldUseDefaultTiming() {
        when(config.getDefaultTiming()).thenReturn(ValidationTiming.BEFORE_EXPORT);
        when(transformationResult.getOntModel()).thenReturn(testOntModel);
        when(shaclEngine.validate(testOntModel)).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validate(transformationResult);

        assertSame(validationReport, result);
        verify(config).getDefaultTiming();
        verify(transformationResult).getOntModel();
        verify(shaclEngine).validate(testOntModel);
    }

    @Test
    void validate_WithBeforeExportTiming_ShouldUseOntModel() {
        when(transformationResult.getOntModel()).thenReturn(testOntModel);
        when(shaclEngine.validate(testOntModel)).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.BEFORE_EXPORT);

        assertSame(validationReport, result);
        verify(transformationResult).getOntModel();
        verify(shaclEngine).validate(testOntModel);
    }

    @Test
    void validate_WithJsonExportTiming_ShouldConvertFromJsonLd() {
        OntModel ontModel = ModelFactory.createOntologyModel();
        ontModel.createResource("http://example.org/test").addProperty(
                ontModel.createProperty("http://example.org/prop"), "test");

        when(transformationResult.getOntModel()).thenReturn(ontModel);
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.JSON_EXPORT);

        assertSame(validationReport, result);
        verify(transformationResult).getOntModel();
        verify(shaclEngine).validate(any(Model.class));
    }

    @Test
    void validate_WithTtlExportTiming_ShouldConvertFromTtl() {
        OntModel ontModel = ModelFactory.createOntologyModel();
        ontModel.createResource("http://example.org/test").addProperty(
                ontModel.createProperty("http://example.org/prop"), "test");

        when(transformationResult.getOntModel()).thenReturn(ontModel);
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.TTL_EXPORT);

        assertSame(validationReport, result);
        verify(transformationResult).getOntModel();
        verify(shaclEngine).validate(any(Model.class));
    }

    @Test
    void validate_WithException_ShouldReturnErrorReport() {
        when(transformationResult.getOntModel()).thenThrow(new RuntimeException("Test exception"));

        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.BEFORE_EXPORT);

        assertNotNull(result);
    }

    @Test
    void validateModel_ShouldCallShaclEngine() {
        when(shaclEngine.validate(testModel)).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validateModel(testModel);

        assertSame(validationReport, result);
        verify(shaclEngine).validate(testModel);
    }

    @Test
    void validateRdf_WithValidRdf_ShouldReturnValidationReport() {
        String rdfContent = "@prefix ex: <http://example.org/> . ex:test ex:prop \"value\" .";
        String format = "TTL";
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validateRdf(rdfContent, format);

        assertSame(validationReport, result);
        verify(shaclEngine).validate(any(Model.class));
    }

    @Test
    void validateRdf_WithInvalidRdf_ShouldReturnErrorReport() {
        String invalidRdfContent = "invalid rdf content";
        String format = "TTL";

        ISMDValidationReport result = validationService.validateRdf(invalidRdfContent, format);

        assertNotNull(result);
    }

    @Test
    void validateRdf_WithJsonLdFormat_ShouldWork() {
        String jsonLdContent = "{\"@context\": {\"ex\": \"http://example.org/\"}, \"@id\": \"ex:test\", \"ex:prop\": \"value\"}";
        String format = "JSON-LD";
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);

        ISMDValidationReport result = validationService.validateRdf(jsonLdContent, format);

        assertSame(validationReport, result);
        verify(shaclEngine).validate(any(Model.class));
    }

    @Test
    void getConfigurationSummary_ShouldReturnCorrectSummary() {
        Set<String> allRules = new HashSet<>();
        allRules.add("rule1");
        allRules.add("rule2");
        allRules.add("rule3");
        Set<String> enabledRules = new HashSet<>();
        enabledRules.add("rule1");
        enabledRules.add("rule2");
        ValidationTiming defaultTiming = ValidationTiming.BEFORE_EXPORT;

        when(ruleManager.getAllRuleNames()).thenReturn(allRules);
        when(ruleManager.getEnabledRuleNames()).thenReturn(enabledRules);
        when(config.getDefaultTiming()).thenReturn(defaultTiming);

        ValidationConfigurationSummary summary = validationService.getConfigurationSummary();

        assertNotNull(summary);
        assertEquals(3, summary.totalRules());
        assertEquals(2, summary.enabledRules());
        assertEquals(defaultTiming, summary.defaultTiming());
    }

    @Test
    void convertFromJsonLd_WithConversionError_ShouldThrowValidationException() {
        OntModel brokenModel = mock(OntModel.class);
        when(transformationResult.getOntModel()).thenReturn(brokenModel);
        when(brokenModel.write(any(StringWriter.class), eq("JSON-LD")))
                .thenThrow(new RuntimeException("JSON-LD write failed"));

        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.JSON_EXPORT);

        assertNotNull(result);
    }

    @Test
    void convertFromTtl_WithConversionError_ShouldThrowValidationException() {
        OntModel brokenModel = mock(OntModel.class);
        when(transformationResult.getOntModel()).thenReturn(brokenModel);
        when(brokenModel.write(any(StringWriter.class), eq("TTL")))
                .thenThrow(new RuntimeException("TTL write failed"));

        ISMDValidationReport result = validationService.validate(transformationResult, ValidationTiming.TTL_EXPORT);

        assertNotNull(result);
    }

    @Test
    void convertFromJsonLd_Success_ShouldLogDebugMessage() {
        OntModel ontModel = ModelFactory.createOntologyModel();
        ontModel.createResource("http://example.org/test").addProperty(
                ontModel.createProperty("http://example.org/prop"), "test");

        when(transformationResult.getOntModel()).thenReturn(ontModel);
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);

        validationService.validate(transformationResult, ValidationTiming.JSON_EXPORT);

        verify(shaclEngine).validate(any(Model.class));
    }

    @Test
    void convertFromTtl_Success_ShouldLogDebugMessage() {
        OntModel ontModel = ModelFactory.createOntologyModel();
        ontModel.createResource("http://example.org/test").addProperty(
                ontModel.createProperty("http://example.org/prop"), "test");

        when(transformationResult.getOntModel()).thenReturn(ontModel);
        when(shaclEngine.validate(any(Model.class))).thenReturn(validationReport);

        validationService.validate(transformationResult, ValidationTiming.TTL_EXPORT);

        verify(shaclEngine).validate(any(Model.class));
    }
}