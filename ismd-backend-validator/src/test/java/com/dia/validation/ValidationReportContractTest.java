package com.dia.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Pins the JSON body of {@code /validate} — the API contract with the tool backend, which keeps its own
 * copy of {@code contract/validation-report.json}. If this test fails, the response shape changed:
 * update the fixture here AND the tool backend's copy (and its DTOs) in the same release.
 */
@JsonTest
class ValidationReportContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validationReportMatchesContractFixture() throws Exception {
        ValidationReportDto report = new ValidationReportDto(List.of(
                new ValidationResult(ValidationSeverity.ERROR, "Pojem nemá název.", "local-rule",
                        "https://example.org/slovnik/pojem/a", "http://www.w3.org/2004/02/skos/core#prefLabel",
                        null),
                new ValidationResult(ValidationSeverity.WARNING, "Pojem již existuje v NKD.", "global-rule",
                        "https://example.org/slovnik/pojem/b", null, "b",
                        new NkdResource("https://slovník.gov.cz/pojem/b", "Pojem B"))),
                "https://example.org/slovnik", Instant.parse("2026-01-01T00:00:00Z"));

        String actual = objectMapper.writeValueAsString(report);

        // id is random per response; only its presence is part of the contract.
        JSONAssert.assertEquals(fixture(), actual, new CustomComparator(JSONCompareMode.STRICT,
                new Customization("id", (expected, value) -> value instanceof Number)));
    }

    private static String fixture() throws IOException {
        try (InputStream in = ValidationReportContractTest.class.getResourceAsStream("/contract/validation-report.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}