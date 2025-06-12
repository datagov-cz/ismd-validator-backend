package com.dia.controller;

import com.dia.enums.FileFormat;
import com.dia.exceptions.JsonExportException;
import com.dia.service.ConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test for {@link ConverterController}.
 *
 * @see ConverterController
 */
@SpringBootTest()
@ContextConfiguration(classes = ConverterControllerIntegrationTest.TestConfig.class)
class ConverterControllerIntegrationTest {

    private static final String JSON_OUTPUT = "{\"result\":\"success\"}";
    private static final String TTL_OUTPUT = "@prefix : <http://example.org/> .\n:subject :predicate :object .";
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ConverterService converterService;
    private MockMvc mockMvc;
    private String minimalArchiXML;

    static Stream<Arguments> outputFormatProvider() {
        return Stream.of(
                Arguments.of("json", "application/json", JSON_OUTPUT),
                Arguments.of("ttl", "text/plain", TTL_OUTPUT)
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
                        415, null)
        );
    }

    static Stream<Arguments> acceptHeaderProvider() {
        return Stream.of(
                Arguments.of("application/json", "application/json", "json"),
                Arguments.of("text/turtle", "text/plain", "ttl"),
                Arguments.of("application/x-turtle", "text/plain", "ttl")
        );
    }

    // ========== SUCCESSFUL CONVERSION TESTS ==========

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

    // ========== FILE VALIDATION TESTS ==========

    static Stream<Arguments> exportExceptionProvider() {
        return Stream.of(
                Arguments.of("json", "Error exporting to JSON"),
                Arguments.of("ttl", "Error exporting to Turtle")
        );
    }

    @BeforeEach
    public void setup() throws IOException {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        minimalArchiXML = loadTestFile();
        reset(converterService);
    }

    // ========== ACCEPT HEADER TESTS ==========

    private String loadTestFile() throws IOException {
        ClassPathResource resource = new ClassPathResource("/com/dia/minimal-archi.xml", getClass());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "Successful Archi XML conversion to {0}")
    @MethodSource("outputFormatProvider")
    void testSuccessfulArchiXmlConversion(String outputFormat, String expectedContentType, String expectedOutput) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        // Configure mock service behavior
        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(FileFormat.ARCHI_XML)).thenReturn(expectedOutput);
        }

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType(expectedContentType))
                .andExpect(content().string(expectedOutput));

        // Verify service interactions
        verify(converterService).parseArchiFromString(anyString());
        verify(converterService).convertArchi(false);

        if ("json".equals(outputFormat)) {
            verify(converterService).exportToJson(FileFormat.ARCHI_XML);
        } else {
            verify(converterService).exportToTurtle(FileFormat.ARCHI_XML);
        }
    }

    // ========== OUTPUT FORMAT CASE TESTS ==========

    @ParameterizedTest(name = "Successful XLSX conversion to {0}")
    @MethodSource("outputFormatProvider")
    void testSuccessfulXlsxConversion(String outputFormat, String expectedContentType, String expectedOutput) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake xlsx content".getBytes(StandardCharsets.UTF_8));

        // Configure mock service behavior
        doNothing().when(converterService).parseExcelFromFile(any(MockMultipartFile.class));
        doNothing().when(converterService).convertExcel(false);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(FileFormat.XLSX)).thenReturn(expectedOutput);
        } else {
            when(converterService.exportToTurtle(FileFormat.XLSX)).thenReturn(expectedOutput);
        }

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType(expectedContentType))
                .andExpect(content().string(expectedOutput));

        // Verify service interactions
        verify(converterService).parseExcelFromFile(any(MockMultipartFile.class));
        verify(converterService).convertExcel(false);

        if ("json".equals(outputFormat)) {
            verify(converterService).exportToJson(FileFormat.XLSX);
        } else {
            verify(converterService).exportToTurtle(FileFormat.XLSX);
        }
    }

    // ========== XML CONTENT TYPE TESTS ==========

    @ParameterizedTest(name = "File validation: {0}")
    @MethodSource("fileValidationProvider")
    void testFileValidation(String testName, MockMultipartFile file, int expectedStatus, String expectedMessage) throws Exception {
        mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().is(expectedStatus));
    }

    // ========== FILE FORMAT DETECTION TESTS ==========

    @ParameterizedTest(name = "Accept header: {0}")
    @MethodSource("acceptHeaderProvider")
    void testAcceptHeaderFormatDetection(String acceptHeader, String expectedContentType, String expectedServiceMethod) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);

        if ("json".equals(expectedServiceMethod)) {
            when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);
        } else {
            when(converterService.exportToTurtle(FileFormat.ARCHI_XML)).thenReturn(TTL_OUTPUT);
        }

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .header("Accept", acceptHeader))
                .andExpect(status().isOk())
                .andExpect(content().contentType(expectedContentType));

        if ("json".equals(expectedServiceMethod)) {
            verify(converterService).exportToJson(FileFormat.ARCHI_XML);
        } else {
            verify(converterService).exportToTurtle(FileFormat.ARCHI_XML);
        }
    }

    @ParameterizedTest(name = "Output format case: {0}")
    @ValueSource(strings = {"json", "JSON", "Json", "JsOn", "ttl", "TTL", "Ttl", "TtL"})
    void testOutputFormatCaseInsensitive(String outputFormat) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);

        boolean isJson = outputFormat.equalsIgnoreCase("json");
        if (isJson) {
            when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);
        } else {
            when(converterService.exportToTurtle(FileFormat.ARCHI_XML)).thenReturn(TTL_OUTPUT);
        }

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isOk())
                .andExpect(content().contentType(isJson ? "application/json" : "text/plain"));

        if (isJson) {
            verify(converterService).exportToJson(FileFormat.ARCHI_XML);
        } else {
            verify(converterService).exportToTurtle(FileFormat.ARCHI_XML);
        }
    }

    // ========== FILENAME EDGE CASES ==========

    @ParameterizedTest(name = "XML content type: {0}")
    @ValueSource(strings = {"application/xml", "text/xml", "application/xml+custom"})
    void testXmlContentTypeVariations(String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", contentType,
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isOk());

        verify(converterService).parseArchiFromString(anyString());
    }

    @ParameterizedTest(name = "File format detection: {0}")
    @MethodSource("fileFormatProvider")
    void testFileFormatDetection(String testName, String filename, String contentType, String content,
                                 boolean shouldCallService, int expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, contentType,
                content.getBytes(StandardCharsets.UTF_8));

        if (shouldCallService && filename.endsWith(".xlsx")) {
            doNothing().when(converterService).parseExcelFromFile(any(MockMultipartFile.class));
            doNothing().when(converterService).convertExcel(false);
            when(converterService.exportToJson(FileFormat.XLSX)).thenReturn(JSON_OUTPUT);
        } else if (shouldCallService) {
            doNothing().when(converterService).parseArchiFromString(anyString());
            doNothing().when(converterService).convertArchi(false);
            when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);
        }

        mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().is(expectedStatus));

        if (shouldCallService && filename.endsWith(".xlsx")) {
            verify(converterService).parseExcelFromFile(any(MockMultipartFile.class));
        } else if (shouldCallService) {
            verify(converterService).parseArchiFromString(anyString());
        } else {
            verifyNoInteractions(converterService);
        }
    }

    // ========== MULTIPLE FILES TESTS ==========

    @ParameterizedTest(name = "Filename edge case: {0}")
    @MethodSource("filenameEdgeCaseProvider")
    void testFilenameEdgeCases(String testName, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "Multiple files: {0}")
    @MethodSource("multipleFilesProvider")
    void testMultipleFileRejection(String testName, MockMultipartFile[] files) throws Exception {
        var request = multipart("/api/convertor/convert");
        for (MockMultipartFile file : files) {
            request.file(file);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest());
    }

    // ========== EXCEPTION HANDLING TESTS ==========

    @ParameterizedTest(name = "Export exception: {0}")
    @MethodSource("exportExceptionProvider")
    void testExportExceptions(String outputFormat, String exceptionMessage) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);

        if ("json".equals(outputFormat)) {
            when(converterService.exportToJson(FileFormat.ARCHI_XML))
                    .thenThrow(new JsonExportException(exceptionMessage));
        } else {
            when(converterService.exportToTurtle(FileFormat.ARCHI_XML))
                    .thenThrow(new JsonExportException(exceptionMessage));
        }

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", outputFormat))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(exceptionMessage));
    }

    @Test
    void testUnsupportedOutputFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "csv"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string("Nepodporovaný výstupní formát: csv"));
    }

    // ========== INDIVIDUAL NON-PARAMETERIZABLE TESTS ==========

    @Test
    void testServiceExceptionHandling() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doThrow(new RuntimeException("Service processing error"))
                .when(converterService).parseArchiFromString(anyString());

        mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Service processing error"));
    }

    @Test
    void testDefaultOutputFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(JSON_OUTPUT));

        verify(converterService).exportToJson(FileFormat.ARCHI_XML);
    }

    @Test
    void testRemoveInvalidSourcesParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(true);
        when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("removeInvalidSources", "true"))
                .andExpect(status().isOk());

        verify(converterService).convertArchi(true);
    }

    @Test
    void testOutputParameterOverridesAcceptHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "json")
                        .header("Accept", "text/turtle"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(converterService).exportToJson(FileFormat.ARCHI_XML);
    }

    @Test
    void testEmptyOutputParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xml", "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportToJson(FileFormat.ARCHI_XML)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(converterService).exportToJson(FileFormat.ARCHI_XML);
    }

    @Test
    void testEmptyMultipartRequest() throws Exception {
        mockMvc.perform(multipart("/api/convertor/convert"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testXlsxRemoveInvalidSourcesParameter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake xlsx content".getBytes(StandardCharsets.UTF_8));

        doNothing().when(converterService).parseExcelFromFile(any(MockMultipartFile.class));
        doNothing().when(converterService).convertExcel(true);
        when(converterService.exportToJson(FileFormat.XLSX)).thenReturn(JSON_OUTPUT);

        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("removeInvalidSources", "true"))
                .andExpect(status().isOk());

        verify(converterService).convertExcel(true);
    }

    @Configuration
    @Import(ConverterController.class)
    static class TestConfig {
        @Bean
        public ConverterService converterService() {
            return mock(ConverterService.class);
        }
    }
}