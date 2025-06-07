package com.dia.controller;

import com.dia.exceptions.FileParsingException;
import com.dia.service.ConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
//@ContextConfiguration(classes = ConverterControllerConcurrencyTest.TestConfig.class)
class ConverterControllerConcurrencyTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ConverterService converterService;

    private MockMvc mockMvc;
    private final String testXmlContent = "<?xml version=\"1.0\"?>\n<archimate:model xmlns:archimate=\"http://www.archimatetool.com/archimate\"/>";

    /*@BeforeEach
    public void setup() throws FileParsingException {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        reset(converterService);

        // Configure mock service for all tests
        doNothing().when(converterService).parseArchiFromString(anyString());
        doNothing().when(converterService).convertArchi(anyBoolean());
        when(converterService.exportArchiToJson()).thenReturn("{\"result\":\"success\"}");
        when(converterService.exportArchiToTurtle()).thenReturn("@prefix : <http://example.org/> .");
    }

    @Configuration
    @Import(ConverterController.class)
    static class TestConfig {
        @Bean
        public ConverterService converterService() {
            return mock(ConverterService.class);
        }
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
                            testXmlContent.getBytes(StandardCharsets.UTF_8)
                    );

                    mockMvc.perform(multipart("/api/convertor/convert")
                                    .file(file))
                            .andExpect(status().isOk());

                    // Verify MDC is cleaned up after request

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        assertTrue(completionLatch.await(30, TimeUnit.SECONDS));
        assertThat(exceptions).isEmpty();

        executor.shutdown();
    }
     */
}
