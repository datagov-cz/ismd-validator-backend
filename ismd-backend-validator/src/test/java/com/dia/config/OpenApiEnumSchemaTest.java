package com.dia.config;

import com.dia.ofn.ConformanceIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frontend generates its API client with orval from {@code /v3/api-docs}; enums must be published
 * as named schemas so the generated client exposes their values as a type.
 */
@SpringBootTest
@ActiveProfiles("localhost")
@AutoConfigureMockMvc
class OpenApiEnumSchemaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conformanceIssueKind_isPublishedAsNamedEnum() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode schemas = new ObjectMapper().readTree(json).path("components").path("schemas");

        List<String> values = new ArrayList<>();
        schemas.path("ConformanceIssueKind").path("enum").forEach(v -> values.add(v.asText()));
        assertThat(values).containsExactlyElementsOf(
                Arrays.stream(ConformanceIssue.Kind.values()).map(Enum::name).toList());

        assertThat(schemas.path("ConformanceIssueDto").path("properties").path("kind").path("$ref").asText())
                .isEqualTo("#/components/schemas/ConformanceIssueKind");
    }
}
