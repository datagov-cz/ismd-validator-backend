package com.dia.controller;

import com.dia.controller.dto.ConversionResponseDto;
import com.dia.converter.data.ConversionResult;
import com.dia.converter.data.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.ExcelReadingException;
import com.dia.exceptions.FileParsingException;
import com.dia.service.ConverterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@ContextConfiguration(classes = ConverterControllerConcurrencyTest.TestConfig.class)
class ConverterControllerConcurrencyTest {

    // Load test content from actual resource files
    private String testArchiXmlContent;
    private String testXmiContent;
    private byte[] testXlsxContent;

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ConverterService converterService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() throws IOException, FileParsingException, ExcelReadingException {
        // INITIALIZE MOCKMVC - This was missing!
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        this.objectMapper = new ObjectMapper();

        // Load test content from resource files
        loadTestContent();

        // Create a mock ConversionResult with a transformation result
        ConversionResult mockConversionResult = mock(ConversionResult.class);
        TransformationResult mockTransformationResult = mock(TransformationResult.class);
        when(mockConversionResult.getTransformationResult()).thenReturn(mockTransformationResult);

        reset(converterService);

        // CRITICAL: Mock ALL service methods with any() matchers
        when(converterService.processArchiFile(anyString(), any())).thenReturn(mockConversionResult);
        when(converterService.processExcelFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);
        when(converterService.processEAFile(any(MultipartFile.class), any())).thenReturn(mockConversionResult);

        // Mock export methods
        when(converterService.exportToJson(any(FileFormat.class), any())).thenReturn("{\"result\":\"success\"}");
        when(converterService.exportToTurtle(any(FileFormat.class), any())).thenReturn("@prefix : <http://example.org/> .\n:subject :predicate :object .");
    }

    private void loadTestContent() throws IOException {
        // Load Archi XML from resources
        try (InputStream archiStream = getClass().getResourceAsStream("/com/dia/minimal-archi.xml")) {
            if (archiStream == null) {
                throw new IOException("Could not load minimal-archi.xml from resources");
            }
            testArchiXmlContent = new String(archiStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Create fake XLSX content (just for file extension testing)
        testXlsxContent = "PK\u0003\u0004fake xlsx content".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void testMDCCleanupAcrossThreads() throws Exception {
        // This test verifies that MDC doesn't leak between requests
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            final int requestNumber = i;
            executor.submit(() -> {
                try {
                    // Verify MDC is clean at start
                    assertThat(MDC.get("requestId")).isNull();

                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            "test" + requestNumber + ".xml",
                            "application/xml",
                            testArchiXmlContent.getBytes(StandardCharsets.UTF_8)
                    );

                    mockMvc.perform(multipart("/api/convertor/convert")
                                    .file(file))
                            .andExpect(status().isOk());

                    // Verify MDC is cleaned up after request
                    assertThat(MDC.get("requestId")).isNull();

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        assertTrue(completionLatch.await(30, TimeUnit.SECONDS));
        // Fix the assertion - check if list is empty, not null
        assertThat(exceptions.isEmpty()).isTrue();

        executor.shutdown();
    }

    @Test
    void testConcurrentFileProcessing() throws Exception {
        // Test that multiple file processing requests can be handled concurrently
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            final int requestNumber = i;
            executor.submit(() -> {
                try {
                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            "test" + requestNumber + ".xml",
                            "application/xml",
                            testArchiXmlContent.getBytes(StandardCharsets.UTF_8)
                    );

                    mockMvc.perform(multipart("/api/convertor/convert")
                                    .file(file)
                                    .param("output", "json"))
                            .andExpect(status().isOk());

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        assertTrue(completionLatch.await(30, TimeUnit.SECONDS));
        // Fix the assertion - check if list is empty, not null
        assertThat(exceptions.isEmpty()).isTrue();

        // Verify that all requests called the service
        verify(converterService, times(numberOfThreads)).processArchiFile(anyString(), any());
        verify(converterService, times(numberOfThreads)).exportToJson(eq(FileFormat.ARCHI_XML), any());

        executor.shutdown();
    }

    @Test
    void testConcurrentDifferentFileFormats() throws Exception {
        int numberOfThreads = 9; // 3 of each format
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        // Create proper content for each file format that matches your controller's detection logic
        String archiXmlContent = testArchiXmlContent;
        String xmiContent = testXmiContent;
        // For XLSX, use the loaded content
        byte[] xlsxBytes = testXlsxContent;

        List<Callable<Void>> tasks = new ArrayList<>();

        // Create tasks for different file formats
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;

            if (i % 3 == 0) {
                // ARCHI XML files
                tasks.add(() -> {
                    MockMultipartFile file = new MockMultipartFile(
                            "file", "test" + index + ".xml", "application/xml",
                            archiXmlContent.getBytes(StandardCharsets.UTF_8));

                    MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                                    .file(file)
                                    .param("output", "json"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType("application/json"))
                            .andReturn();

                    // Verify response content
                    String responseBody = result.getResponse().getContentAsString();
                    ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);
                    assertNotNull(responseDto);
                    assertNotNull(responseDto.getOutput());
                    assertNull(responseDto.getErrorMessage());

                    return null;
                });
            } else if (i % 3 == 1) {
                // XMI files
                tasks.add(() -> {
                    MockMultipartFile file = new MockMultipartFile(
                            "file", "test" + index + ".xmi", "application/xml",
                            xmiContent.getBytes(StandardCharsets.UTF_8));

                    MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                                    .file(file)
                                    .param("output", "json"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType("application/json"))
                            .andReturn();

                    // Verify response content
                    String responseBody = result.getResponse().getContentAsString();
                    ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);
                    assertNotNull(responseDto);
                    assertNotNull(responseDto.getOutput());
                    assertNull(responseDto.getErrorMessage());

                    return null;
                });
            } else {
                // XLSX files
                tasks.add(() -> {
                    MockMultipartFile file = new MockMultipartFile(
                            "file", "test" + index + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            xlsxBytes);

                    MvcResult result = mockMvc.perform(multipart("/api/convertor/convert")
                                    .file(file)
                                    .param("output", "json"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType("application/json"))
                            .andReturn();

                    // Verify response content
                    String responseBody = result.getResponse().getContentAsString();
                    ConversionResponseDto responseDto = objectMapper.readValue(responseBody, ConversionResponseDto.class);
                    assertNotNull(responseDto);
                    assertNotNull(responseDto.getOutput());
                    assertNull(responseDto.getErrorMessage());

                    return null;
                });
            }
        }

        // Execute all tasks concurrently
        List<Future<Void>> futures = executorService.invokeAll(tasks);

        // Wait for all tasks to complete
        for (Future<Void> future : futures) {
            future.get(); // This will throw an exception if the task failed
        }

        executorService.shutdown();

        // Verify that service methods were called
        // Use atLeastOnce() since we can't predict exact concurrent execution order
        verify(converterService, atLeastOnce()).processArchiFile(anyString(), any());
        verify(converterService, atLeastOnce()).processEAFile(any(MultipartFile.class), any());
        verify(converterService, atLeastOnce()).processExcelFile(any(MultipartFile.class), any());
        verify(converterService, atLeastOnce()).exportToJson(any(FileFormat.class), any());
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