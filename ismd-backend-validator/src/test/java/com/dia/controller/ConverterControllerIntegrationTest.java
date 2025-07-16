package com.dia.controller;

import com.dia.controller.dto.ConversionResponseDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.JsonExportException;
import com.dia.service.ConverterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private String minimalArchiXML;
    private ObjectMapper objectMapper;
    private ConversionResult mockConversionResult;

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
    public void setup() throws IOException {
        this.objectMapper = new ObjectMapper();
        minimalArchiXML = loadTestFile();

        // Create a mock ConversionResult with a transformation result
        mockConversionResult = mock(ConversionResult.class);

        TransformationResult mockTransformationResult = mock(TransformationResult.class);
        when(mockConversionResult.getTransformationResult()).thenReturn(mockTransformationResult);

        reset(converterService);
    }

    private String loadTestFile() throws IOException {
        ClassPathResource resource = new ClassPathResource("/com/dia/minimal-archi.xml", getClass());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ========== SUCCESSFUL CONVERSION TESTS ==========

    @ParameterizedTest(name = "Successful Archi XML conversion to {0}")
    @MethodSource("outputFormatProvider")
    void testSuccessfulArchiXmlConversion(String outputFormat, String expectedOutput) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        // Configure mock service behavior
        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.ARCHI_XML), any())).thenReturn(expectedOutput);
        }

        // Act & Assert
        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        // Verify service interactions
        verify(converterService).processArchiFile(anyString(), any());

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

        // Configure mock service behavior
        when(converterService.processExcelFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.XLSX), any())).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.XLSX), any())).thenReturn(expectedOutput);
        }

        // Act & Assert
        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        // Verify service interactions
        verify(converterService).processExcelFile(any(MultipartFile.class), any());

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

        // Configure mock service behavior
        when(converterService.processEAFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.XMI), any())).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.XMI), any())).thenReturn(expectedOutput);
        }

        // Act & Assert
        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        // Verify service interactions
        verify(converterService).processEAFile(any(MultipartFile.class), any());

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
        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify error DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals(expectedMessage, responseDto.getErrorMessage());
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

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .header("Accept", acceptHeader))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        if ("json".equals(expectedServiceMethod)) {
            verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
        } else {
            verify(converterService).exportToTurtle(eq(FileFormat.ARCHI_XML), any());
        }
    }

    // ========== OUTPUT FORMAT CASE TESTS ==========

    @ParameterizedTest(name = "Output format case: {0}")
    @ValueSource(strings = {"json", "JSON", "Json", "JsOn", "ttl", "TTL", "Ttl", "TtL"})
    void testOutputFormatCaseInsensitive(String outputFormat) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);

        boolean isJson = outputFormat.equalsIgnoreCase("json");
        String expectedOutput;
        if (isJson) {
            expectedOutput = JSON_OUTPUT;
            when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);
        } else {
            expectedOutput = TTL_OUTPUT;
            when(converterService.exportToTurtle(eq(FileFormat.ARCHI_XML), any())).thenReturn(TTL_OUTPUT);
        }

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(expectedOutput, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        if (isJson) {
            verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
        } else {
            verify(converterService).exportToTurtle(eq(FileFormat.ARCHI_XML), any());
        }
    }

    // ========== XML CONTENT TYPE TESTS ==========

    @ParameterizedTest(name = "XML content type: {0}")
    @ValueSource(strings = {"application/xml", "text/xml", "application/xml+custom"})
    void testXmlContentTypeVariations(String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", contentType,
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        verify(converterService).processArchiFile(anyString(), any());
    }

    // ========== FILE FORMAT DETECTION TESTS ==========

    @ParameterizedTest(name = "File format detection: {0}")
    @MethodSource("fileFormatProvider")
    void testFileFormatDetection(String testName, String filename, String contentType, String content,
                                 boolean shouldCallService, int expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, contentType,
                content.getBytes(StandardCharsets.UTF_8));

        if (shouldCallService && filename.endsWith(".xlsx")) {
            when(converterService.processExcelFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);
            when(converterService.exportToJson(eq(FileFormat.XLSX), any())).thenReturn(JSON_OUTPUT);
        } else if (shouldCallService) {
            when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
            when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);
        }

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType("application/json"))
                .andReturn();

        if (expectedStatus == 200) {
            // Parse and verify success DTO response
            String responseBody = result.getResponse().getContentAsString();
            ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

            assertNotNull(responseDto);
            if (filename.endsWith(".ttl")) {
                assertEquals("File processed successfully", responseDto.getOutput());
            } else {
                assertEquals(JSON_OUTPUT, responseDto.getOutput());
            }
            assertNull(responseDto.getErrorMessage());
        } else {
            // Parse and verify error DTO response
            String responseBody = result.getResponse().getContentAsString();
            ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

            assertNotNull(responseDto);
            assertNull(responseDto.getOutput());
            assertNotNull(responseDto.getErrorMessage());
        }

        if (shouldCallService && filename.endsWith(".xlsx")) {
            verify(converterService).processExcelFile(any(MultipartFile.class), any());
        } else if (shouldCallService) {
            verify(converterService).processArchiFile(anyString(), any());
        } else {
            verifyNoInteractions(converterService);
        }
    }

    // ========== FILENAME EDGE CASES ==========

    @ParameterizedTest(name = "Filename edge case: {0}")
    @MethodSource("filenameEdgeCaseProvider")
    void testFilenameEdgeCases(String testName, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());
    }

    // ========== MULTIPLE FILES TESTS ==========

    @ParameterizedTest(name = "Multiple files: {0}")
    @MethodSource("multipleFilesProvider")
    void testMultipleFileRejection(String testName, MockMultipartFile[] files) throws Exception {
        var request = multipart("/api/convertor/convert");
        for (MockMultipartFile file : files) {
            request.file(file);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify error DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals("Můžete nahrát pouze jeden soubor.", responseDto.getErrorMessage());
    }

    // ========== EXCEPTION HANDLING TESTS ==========

    @ParameterizedTest(name = "Export exception: {0}")
    @MethodSource("exportExceptionProvider")
    void testExportExceptions(String outputFormat, String exceptionMessage) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any()))
                    .thenThrow(new JsonExportException(exceptionMessage));
        } else {
            when(converterService.exportToTurtle(eq(FileFormat.ARCHI_XML), any()))
                    .thenThrow(new JsonExportException(exceptionMessage));
        }

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify error DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals(exceptionMessage, responseDto.getErrorMessage());
    }

    @Test
    void testUnsupportedOutputFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), eq(false))).thenReturn(mockConversionResult);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "csv"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify error DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals("Nepodporovaný výstupní formát: csv", responseDto.getErrorMessage());
    }

    // ========== INDIVIDUAL NON-PARAMETERIZABLE TESTS ==========

    @Test
    void testServiceExceptionHandling() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any()))
                .thenThrow(new RuntimeException("Service processing error"));

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify error DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertNull(responseDto.getOutput());
        assertEquals("Service processing error", responseDto.getErrorMessage());
    }

    @Test
    void testDefaultOutputFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
    }

    @Test
    void testRemoveInvalidSourcesParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), eq(true))).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("removeInvalidSources", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        verify(converterService).processArchiFile(anyString(), eq(true));
    }

    @Test
    void testOutputParameterOverridesAcceptHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "json")
                        .header("Accept", "text/turtle"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
    }

    @Test
    void testEmptyOutputParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.ARCHI_XML), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        verify(converterService).exportToJson(eq(FileFormat.ARCHI_XML), any());
    }

    @Test
    void testEmptyMultipartRequest() throws Exception {
        mockMvc.perform(multipart("/api/convertor/convert"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testXlsxRemoveInvalidSourcesParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake xlsx content".getBytes(StandardCharsets.UTF_8));

        when(converterService.processExcelFile(any(MultipartFile.class), eq(true))).thenReturn(mockConversionResult);
        when(converterService.exportToJson(eq(FileFormat.XLSX), any())).thenReturn(JSON_OUTPUT);

        MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("removeInvalidSources", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        // Parse and verify DTO response
        String responseBody = result.getResponse().getContentAsString();
        ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);

        assertNotNull(responseDto);
        assertEquals(JSON_OUTPUT, responseDto.getOutput());
        assertNull(responseDto.getErrorMessage());

        verify(converterService).processExcelFile(any(MultipartFile.class), eq(true));
    }
}