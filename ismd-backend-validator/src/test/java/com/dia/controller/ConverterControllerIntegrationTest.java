package com.dia.controller;

import com.dia.controller.dto.CatalogRecordDto;
import com.dia.controller.dto.ConversionResponseDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.service.*;
import com.dia.validation.data.DetailedValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test for {@link ConverterController}.
 *
 * @see ConverterController
 */
@WebMvcTest(ConverterController.class)
class ConverterControllerIntegrationTest {

    private static final String JSON_OUTPUT = "{\"result\":\"success\"}";
    private static final String TTL_OUTPUT = "@prefix : <http://example.org/> .\n:subject :predicate :object .";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConverterService converterService;

    @MockitoBean
    private ValidationService validationService;

    @MockitoBean
    private ValidationReportService validationReportService;

    @MockitoBean
    private DetailedValidationReportService detailedValidationReportService;

    @MockitoBean
    private CatalogReportService catalogReportService;

    private String minimalArchiXML;
    private ObjectMapper objectMapper;
    private ConversionResult mockConversionResult;
    private ValidationResultsDto mockValidationResults;
    private DetailedValidationReportDto mockDetailedReport;
    private CatalogRecordDto mockCatalogRecord;

    static Stream<Arguments> outputFormatProvider() {
        return Stream.of(
                Arguments.of("json", JSON_OUTPUT),
                Arguments.of("ttl", TTL_OUTPUT)
        );
    }

    static Stream<Arguments> fileValidationProvider() {
        return Stream.of(
                Arguments.of("Empty file",
                        new MockMultipartFile("file", "empty.xml", "application/xml", new byte[0]),
                        400, "Nebyl vložen žádný soubor."),
                Arguments.of("Oversized file",
                        new MockMultipartFile("file", "large.xml", "application/xml", new byte[5_242_881]),
                        413, "Soubor je příliš velký. Maximální povolená velikost je 5 MB."),
                Arguments.of("Unsupported format",
                        new MockMultipartFile("file", "test.pdf", "application/pdf", "PDF content".getBytes()),
                        415, "Nepodporovaný formát souboru.")
        );
    }

    static Stream<Arguments> acceptHeaderProvider() {
        return Stream.of(
                Arguments.of("application/json", "json"),
                Arguments.of("text/turtle", "ttl"),
                Arguments.of("application/x-turtle", "ttl")
        );
    }

    static Stream<Arguments> fileFormatProvider() {
        String turtleContent = "@prefix : <http://example.org/> .\n:subject :predicate :object .";
        String genericXml = "<?xml version=\"1.0\"?>\n<root><element>content</element></root>";
        String xlsxContent = "fake xlsx content";

        return Stream.of(
                Arguments.of("Turtle file", "test.ttl", "text/turtle", turtleContent, false, 200),
                Arguments.of("Generic XML", "generic.xml", "application/xml", genericXml, false, 415),
                Arguments.of("XLSX file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxContent, true, 200)
        );
    }

    static Stream<Arguments> filenameEdgeCaseProvider() {
        return Stream.of(
                Arguments.of("Null filename", null),
                Arguments.of("Empty filename", "")
        );
    }

    static Stream<Arguments> multipleFilesProvider() {
        String content = "test content";
        return Stream.of(
                Arguments.of("Same parameter name",
                        new MockMultipartFile[]{
                                new MockMultipartFile("file", "test1.xml", "application/xml", content.getBytes()),
                                new MockMultipartFile("file", "test2.xml", "application/xml", content.getBytes())
                        }),
                Arguments.of("Different parameter names",
                        new MockMultipartFile[]{
                                new MockMultipartFile("file", "test1.xml", "application/xml", content.getBytes()),
                                new MockMultipartFile("file2", "test2.xml", "application/xml", content.getBytes())
                        })
        );
    }

    static Stream<Arguments> exportExceptionProvider() {
        return Stream.of(
                Arguments.of("json", "Error exporting to JSON"),
                Arguments.of("ttl", "Error exporting to Turtle")
        );
    }

    @BeforeEach
    public void setup() {
        this.objectMapper = new ObjectMapper();
        minimalArchiXML = loadTestFile();

        // Create mock objects
        mockConversionResult = mock(ConversionResult.class);
        TransformationResult mockTransformationResult = mock(TransformationResult.class);
        when(mockConversionResult.getTransformationResult()).thenReturn(mockTransformationResult);

        ISMDValidationReport mockValidationReport = mock(ISMDValidationReport.class);
        mockValidationResults = mock(ValidationResultsDto.class);
        mockDetailedReport = mock(DetailedValidationReportDto.class);
        mockCatalogRecord = mock(CatalogRecordDto.class);

        // Configure service mocks
        when(validationService.validate(any(TransformationResult.class))).thenReturn(mockValidationReport);
        when(validationReportService.convertToDto(any(ISMDValidationReport.class))).thenReturn(mockValidationResults);
        when(detailedValidationReportService.generateDetailedReport(any(), any())).thenReturn(mockDetailedReport);
        when(catalogReportService.generateCatalogReport(any(), any())).thenReturn(Optional.of(mockCatalogRecord));

        reset(converterService);
    }

    private String loadTestFile() {
        // Create a minimal Archi XML for testing
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate"
                                name="Test Model" id="test-model" version="4.0.0">
                    <element name="Test Element" id="test-element"/>
                </archimate:model>
                """;
    }

    // ========== SUCCESSFUL CONVERSION TESTS ==========

    @ParameterizedTest(name = "Successful Archi XML conversion to {0}")
    @MethodSource("outputFormatProvider")
    void testSuccessfulArchiXmlConversion(String outputFormat, String expectedOutput) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.ARCHI_XML), any())).thenReturn(expectedOutput);
        }

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNotNull(responseDto.getValidationResults());

        verify(converterService).processArchiFile(anyString(), any());
        verify(validationService).validate(any(TransformationResult.class));
        verify(validationReportService).convertToDto(any(ISMDValidationReport.class));

        if ("json".equals(outputFormat)) {
            verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
        } else {
            verify(converterService).exportToTurtle(eq(FileFormat.ARCHI_XML), any());
        }
    }

    @ParameterizedTest(name = "Successful XLSX conversion to {0}")
    @MethodSource("outputFormatProvider")
    void testSuccessfulXlsxConversion(String outputFormat, String expectedOutput) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake xlsx content".getBytes(StandardCharsets.UTF_8));

        when(converterService.processExcelFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.XLSX), any())).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.XLSX), any())).thenReturn(expectedOutput);
        }

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNotNull(responseDto.getValidationResults());

        verify(converterService).processExcelFile(any(MultipartFile.class), any());
        verify(validationService).validate(any(TransformationResult.class));
        verify(validationReportService).convertToDto(any(ISMDValidationReport.class));

        if ("json".equals(outputFormat)) {
            verify(converterService).exportToJson(eq(FileFormat.XLSX), any());
        } else {
            verify(converterService).exportToTurtle(eq(FileFormat.XLSX), any());
        }
    }

    @ParameterizedTest(name = "Successful XMI conversion to {0}")
    @MethodSource("outputFormatProvider")
    void testSuccessfulXmiConversion(String outputFormat, String expectedOutput) throws Exception {
        String xmiContent = "<?xml version=\"1.0\"?>\n<xmi:XMI xmi:version=\"2.0\"><test/></xmi:XMI>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xmi", "application/xml",
                xmiContent.getBytes(StandardCharsets.UTF_8));

        when(converterService.processEAFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.XMI), any())).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.XMI), any())).thenReturn(expectedOutput);
        }

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNotNull(responseDto.getValidationResults());

        verify(converterService).processEAFile(any(MultipartFile.class), any());
        verify(validationService).validate(any(TransformationResult.class));
        verify(validationReportService).convertToDto(any(ISMDValidationReport.class));

        if ("json".equals(outputFormat)) {
            verify(converterService).exportToJson(eq(FileFormat.XMI), any());
        } else {
            verify(converterService).exportToTurtle(eq(FileFormat.XMI), any());
        }
    }

    // ========== FILE VALIDATION TESTS ==========

    @ParameterizedTest(name = "File validation: {0}")
    @MethodSource("fileValidationProvider")
    void testFileValidation(String testName, MockMultipartFile file, int expectedStatus, String expectedMessage) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/converter/convert").file(file))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals(expectedMessage, responseDto.getErrorMessage());
        assertNull(responseDto.getValidationResults());
    }

    // ========== ACCEPT HEADER TESTS ==========

    @ParameterizedTest(name = "Accept header: {0}")
    @MethodSource("acceptHeaderProvider")
    void testAcceptHeaderFormatDetection(String acceptHeader, String expectedServiceMethod) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);

        String expectedOutput;
        if ("json".equals(expectedServiceMethod)) {
            expectedOutput = JSON_OUTPUT;
            when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);
        } else {
            expectedOutput = TTL_OUTPUT;
            when(converterService.exportToTurtle(eq(FileFormat.ARCHI_XML), any())).thenReturn(TTL_OUTPUT);
        }

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .header("Accept", acceptHeader))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNotNull(responseDto.getValidationResults());

        verify(validationService).validate(any(TransformationResult.class));
        verify(validationReportService).convertToDto(any(ISMDValidationReport.class));

        if ("json".equals(expectedServiceMethod)) {
            verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
        } else {
            verify(converterService).exportToTurtle(eq(FileFormat.ARCHI_XML), any());
        }
    }

    // ========== SSP CONVERSION TESTS ==========

    @Test
    void testSuccessfulSSPConversion() throws Exception {
        String testIri = "http://example.org/test-ontology";

        when(converterService.processSSPOntology(eq(testIri), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.SSP), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(post("/api/converter/ssp/convert")
                        .param("iri", testIri)
                        .param("output", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNotNull(responseDto.getValidationResults());

        verify(converterService).processSSPOntology(eq(testIri), any());
        verify(validationService).validate(any(TransformationResult.class));
        verify(validationReportService).convertToDto(any(ISMDValidationReport.class));
    }

    @Test
    void testSSPConversionEmptyIRI() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/converter/ssp/convert")
                        .param("iri", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals("Nebylo vloženo IRI slovníku určeného k převodu.", responseDto.getErrorMessage());
        assertNull(responseDto.getValidationResults());
    }

    // ========== DETAILED REPORT TESTS ==========

    @Test
    void testDetailedReportIncluded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("includeDetailedReport", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNotNull(responseDto.getValidationReport());

        verify(detailedValidationReportService).generateDetailedReport(any(), any());
    }

    @Test
    void testDetailedReportExcluded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("includeDetailedReport", "false"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getValidationReport());

        verifyNoInteractions(detailedValidationReportService);
    }

    // ========== CATALOG RECORD TESTS ==========

    @Test
    void testCatalogRecordIncluded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("includeCatalogRecord", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNotNull(responseDto.getCatalogReport());

        verify(catalogReportService).generateCatalogReport(any(), any());
    }

    // ========== EXCEPTION HANDLING TESTS ==========

    @Test
    void testServiceExceptionHandling() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any()))
                .thenThrow(new RuntimeException("Service processing error"));

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals("Service processing error", responseDto.getErrorMessage());
        assertNull(responseDto.getValidationResults());

        verifyNoInteractions(validationService);
        verifyNoInteractions(validationReportService);
    }

    @Test
    void testValidationServiceException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(validationService.validate(any(TransformationResult.class)))
                .thenThrow(new RuntimeException("Validation service error"));
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNull(responseDto.getValidationResults());

        verify(converterService).processArchiFile(anyString(), any());
        verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
        verify(validationService, times(2)).validate(any(TransformationResult.class));
        verifyNoInteractions(validationReportService);
    }

    // ========== INDIVIDUAL SPECIFIC TESTS ==========

    @Test
    void testTurtleFileProcessing() throws Exception {
        String turtleContent = "@prefix : <http://example.org/> .\n:subject :predicate :object .";
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.ttl", "text/turtle", turtleContent.getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals("File processed successfully", responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
        assertNull(responseDto.getValidationResults()); // Turtle files don't get validated
    }

    @Test
    void testUnsupportedOutputFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert")
                        .file(file)
                        .param("output", "csv"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertNull(responseDto.getValidationResults());
    }

    @Test
    void testDefaultParameters() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/converter/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNotNull(responseDto.getValidationResults());
        assertNotNull(responseDto.getValidationReport());
        assertNotNull(responseDto.getCatalogReport());

        verify(detailedValidationReportService).generateDetailedReport(any(), any());
        verify(catalogReportService).generateCatalogReport(any(), any());
    }

    // ========== DOWNLOAD ENDPOINT TESTS ==========

    @Test
    void testDownloadDetailedValidationReportCSV() throws Exception {
        ConversionResponseDto mockResponse = mock(ConversionResponseDto.class);
        when(mockResponse.getValidationReport()).thenReturn(mockDetailedReport);
        when(detailedValidationReportService.generateCSV(mockDetailedReport)).thenReturn("CSV content");

        String requestBody = objectMapper.writeValueAsString(mockResponse);

        MvcResult result = mockMvc.perform(post("/api/converter/convert/detailed-report/csv")
                        .contentType("application/json")
                        .content(requestBody)
                        .param("filename", "test-report"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertEquals("CSV content", responseBody);

        verify(detailedValidationReportService).generateCSV(mockDetailedReport);
    }

    @Test
    void testDownloadCatalogRecordJSON() throws Exception {
        ConversionResponseDto mockResponse = mock(ConversionResponseDto.class);
        when(mockResponse.getCatalogReport()).thenReturn(mockCatalogRecord);

        String requestBody = objectMapper.writeValueAsString(mockResponse);

        MvcResult result = mockMvc.perform(post("/api/converter/convert/catalog-record/json")
                        .contentType("application/json")
                        .content(requestBody)
                        .param("filename", "test-catalog"))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(result.getResponse().getContentAsString());
        // The response should be JSON content of the catalog record
    }
}