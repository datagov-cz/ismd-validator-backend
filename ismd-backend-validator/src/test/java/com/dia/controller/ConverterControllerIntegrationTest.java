package com.dia.controller;

import com.dia.exceptions.JsonExportException;
import com.dia.service.ConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest()
@ContextConfiguration(classes = ConverterControllerIntegrationTest.TestConfig.class)
class ConverterControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ConverterService converterService;

    private MockMvc mockMvc;
    private String minimalArchiXML;

    @BeforeEach
    public void setup() throws IOException {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        minimalArchiXML = loadTestFile();

        reset(converterService);
    }

    @Configuration
    @Import(ConverterController.class)
    static class TestConfig {
        @Bean
        public ConverterService converterService() {
            return mock(ConverterService.class);
        }
    }

    private String loadTestFile() throws IOException {
        ClassPathResource resource = new ClassPathResource("/com/dia/minimal-archi.xml", getClass());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static final String JSON_OUTPUT = "{\"result\":\"success\"}";
    private static final String TTL_OUTPUT = "@prefix : <http://example.org/> .\n:subject :predicate :object .";

    @Test
    void testSuccessfulArchiXmlToJsonConversion() throws Exception {
        // Reset the mock before each test to ensure clean state
        reset(converterService);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        // Configure mock service behavior
        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportArchiToJson()).thenReturn(JSON_OUTPUT);

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(JSON_OUTPUT));

        // Verify service interactions
        verify(converterService).parseArchiFromString(anyString());
        verify(converterService).convertArchi(false);
        verify(converterService).exportArchiToJson();
    }

    @Test
    void testSuccessfulArchiXmlToTurtleConversion() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        // Configure mock service behavior
        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportArchiToTurtle()).thenReturn(TTL_OUTPUT);

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "ttl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().string(TTL_OUTPUT));

        // Verify service interactions
        verify(converterService).parseArchiFromString(anyString());
        verify(converterService).convertArchi(false);
        verify(converterService).exportArchiToTurtle();
    }

    @Test
    void testEmptyFileUpload() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.xml",
                "application/xml",
                new byte[0]
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testOversizedFileUpload() throws Exception {
        // Arrange - Create a file larger than 5MB
        byte[] oversizedContent = new byte[5_242_881]; // 5MB + 1 byte
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.xml",
                "application/xml",
                oversizedContent
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void testUnsupportedFileFormat() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "PDF content".getBytes(StandardCharsets.UTF_8)
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void testUnsupportedOutputFormat() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "csv"))  // Unsupported format
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string("Nepodporovaný výstupní formát: csv"));
    }

    @Test
    void testServiceExceptionHandling() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        // Configure service to throw exception
        doThrow(new RuntimeException("Service processing error"))
                .when(converterService).parseArchiFromString(anyString());

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Service processing error"));
    }

    @Test
    void testJsonExportException() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportArchiToJson())
                .thenThrow(new JsonExportException("Error exporting to JSON"));

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "json"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error exporting to JSON"));
    }

    @Test
    void testTurtleExportException() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportArchiToTurtle())
                .thenThrow(new JsonExportException("Error exporting to Turtle"));

        // Act & Assert
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file)
                        .param("output", "ttl"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error exporting to Turtle"));
    }

    @Test
    void testDefaultOutputFormat() throws Exception {
        // Arrange - Test that JSON is used when no output format is specified
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xml",
                "application/xml",
                minimalArchiXML.getBytes(StandardCharsets.UTF_8)
        );

        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(false);
        when(converterService.exportArchiToJson()).thenReturn(JSON_OUTPUT);

        // Act & Assert - Don't specify output param, should default to JSON
        mockMvc.perform(multipart("/api/convertor/convert")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(JSON_OUTPUT));

        verify(converterService).exportArchiToJson();
    }
}
