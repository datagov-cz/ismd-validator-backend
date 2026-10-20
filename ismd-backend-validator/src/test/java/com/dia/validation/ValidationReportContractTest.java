package com.dia.validation;

import com.dia.controller.dto.ApiResponseDto;
import com.dia.enums.ErrorCode;
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
 * Pins the JSON bodies of {@code /validate} (success and error) — the API contract with the tool
 * backend, which keeps its own copies of {@code contract/*.json}. If this test fails, the response
 * shape changed: update the fixtures here AND the tool backend's copies (and its DTOs) together.
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

    /** Every 4xx/5xx body; the tool reads {@code message} (and {@code errorCode}) from it. */
    @Test
    void errorResponseMatchesContractFixture() throws Exception {
        ApiResponseDto<Void> error = ApiResponseDto.error(ErrorCode.UNSUPPORTED_FORMAT,
                "Nepodporovaný typ souboru: slovnik.txt");

        JSONAssert.assertEquals(fixture("error-response.json"), objectMapper.writeValueAsString(error),
                JSONCompareMode.STRICT);
    }

    private static String fixture() throws IOException {
        return fixture("validation-report.json");
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = ValidationReportContractTest.class.getResourceAsStream("/contract/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}