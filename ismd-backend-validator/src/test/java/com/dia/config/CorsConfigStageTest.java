package com.dia.config;

import com.dia.config.cors.CorsConfig;
import com.dia.config.cors.CorsConfigUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test for {@link CorsConfigUtil} when we simulate stage environment.
 *
 * @see CorsConfig
 */
@SpringBootTest(properties = "cors.allowed-origins=https://stage.example.gov.cz")
@ActiveProfiles("stage")
@AutoConfigureMockMvc
class CorsConfigStageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void stageFrontendDomain() throws Exception {
        String testOrigin = "https://stage.example.gov.cz";

        mockMvc.perform(get("/actuator/health")
                        .header("Origin", testOrigin))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Origin", testOrigin));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000",             // Local development HTTP
            "https://localhost:3000",            // Local development HTTPS
    })
    void allowedOriginsTest(String testOrigin) throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", testOrigin))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Origin", testOrigin));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.domain.org",
            "http://192.168.0.18",
            "https://b2b6-89-103-185-10.ngrok-free.app",
            "https://t1.domain.org",
    })
    void blockedOriginsTest(String testOrigin) throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", testOrigin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
