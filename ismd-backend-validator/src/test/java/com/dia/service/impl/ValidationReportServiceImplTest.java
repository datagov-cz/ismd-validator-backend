package com.dia.service.impl;

import com.dia.controller.dto.SeverityGroupDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.enums.ValidationSeverity;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.validation.data.ValidationResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class ValidationReportServiceImplTest {

    @InjectMocks
    private ValidationReportServiceImpl validationReportService;

    private ValidationResult errorResult1;
    private ValidationResult errorResult2;
    private ValidationResult warningResult;
    private ValidationResult infoResult;
    private ValidationResult duplicateErrorResult;

    @BeforeEach
    void setUp() {
        errorResult1 = new ValidationResult(
                ValidationSeverity.ERROR,
                "Required property is missing",
                "RequiredPropertyRule",
                "http://example.org/node1",
                "http://example.org/property1",
                "value1"
        );

        errorResult2 = new ValidationResult(
                ValidationSeverity.ERROR,
                "Invalid format",
                "FormatRule",
                "http://example.org/node2",
                "http://example.org/property2",
                "value2"
        );

        duplicateErrorResult = new ValidationResult(
                ValidationSeverity.ERROR,
                "Required property is missing",
                "RequiredPropertyRule",
                "http://example.org/node3",
                "http://example.org/property3",
                "value3"
        );

        warningResult = new ValidationResult(
                ValidationSeverity.WARNING,
                "Deprecated property used",
                "DeprecatedPropertyRule",
                "http://example.org/node4",
                "http://example.org/property4",
                "value4"
        );

        infoResult = new ValidationResult(
                ValidationSeverity.INFO,
                "Additional information",
                "InfoRule",
                "http://example.org/node5",
                "http://example.org/property5",
                "value5"
        );
    }

    @Test
    void testConvertToDto_SingleReport_WithResults() {
        // Arrange
        List<ValidationResult> results = Arrays.asList(errorResult1, warningResult, infoResult, duplicateErrorResult);
        ISMDValidationReport report = new ISMDValidationReport(results, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getSeverityGroups());
        Assertions.assertEquals(3, dto.getSeverityGroups().size());

        List<SeverityGroupDto> groups = dto.getSeverityGroups();

        SeverityGroupDto firstGroup = groups.get(0);
        Assertions.assertEquals("Chyba", firstGroup.getSeverity());

        boolean hasGroupWithTwoErrors = groups.stream()
                .anyMatch(group -> group.getSeverity().equals("Chyba") && group.getCount() == 2);
        Assertions.assertTrue(hasGroupWithTwoErrors, "Should have a group with 2 error occurrences");
    }

    @Test
    void testConvertToDto_SingleReport_EmptyResults() {
        // Arrange
        ISMDValidationReport report = new ISMDValidationReport(Collections.emptyList(), true, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getSeverityGroups());
        Assertions.assertTrue(dto.getSeverityGroups().isEmpty());
    }

    @Test
    void testConvertToDto_CombinedReports() {
        // Arrange
        List<ValidationResult> localResults = Arrays.asList(errorResult1, warningResult);
        List<ValidationResult> globalResults = Arrays.asList(errorResult2, infoResult);

        ISMDValidationReport localReport = new ISMDValidationReport(localResults, false, Instant.now());
        ISMDValidationReport globalReport = new ISMDValidationReport(globalResults, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(localReport, globalReport);

        // Assert
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getSeverityGroups());
        Assertions.assertEquals(4, dto.getSeverityGroups().size()); // 4 unique message groups

        boolean hasGlobalPrefix = dto.getSeverityGroups().stream()
                .anyMatch(group -> group.getDescription().startsWith("[GLOBAL]"));
        Assertions.assertTrue(hasGlobalPrefix, "Should have groups with [GLOBAL] prefix");

        boolean hasGlobalError = dto.getSeverityGroups().stream()
                .anyMatch(group -> group.getDescription().equals("[GLOBAL] Invalid format"));
        Assertions.assertTrue(hasGlobalError, "Should have global error message");

        boolean hasGlobalInfo = dto.getSeverityGroups().stream()
                .anyMatch(group -> group.getDescription().equals("[GLOBAL] Additional information"));
        Assertions.assertTrue(hasGlobalInfo, "Should have global info message");
    }

    @Test
    void testConvertToDto_CombinedReports_ValidityLogic() {
        // Test case 1: Both reports valid
        ISMDValidationReport validLocalReport = new ISMDValidationReport(Collections.emptyList(), true, Instant.now());
        ISMDValidationReport validGlobalReport = new ISMDValidationReport(Collections.emptyList(), true, Instant.now());

        ValidationResultsDto dto1 = validationReportService.convertToDto(validLocalReport, validGlobalReport);

        // Test case 2: Local invalid, global valid
        ISMDValidationReport invalidLocalReport = new ISMDValidationReport(Collections.singletonList(errorResult1), false, Instant.now());
        ValidationResultsDto dto2 = validationReportService.convertToDto(invalidLocalReport, validGlobalReport);

        // Test case 3: Local valid, global invalid
        ISMDValidationReport invalidGlobalReport = new ISMDValidationReport(Collections.singletonList(errorResult2), false, Instant.now());
        ValidationResultsDto dto3 = validationReportService.convertToDto(validLocalReport, invalidGlobalReport);

        // Assert
        Assertions.assertNotNull(dto1);
        Assertions.assertNotNull(dto2);
        Assertions.assertNotNull(dto3);
    }

    @Test
    void testCzechSeverityMapping() {
        // Arrange
        ValidationResult errorResult = new ValidationResult(ValidationSeverity.ERROR, "Error message", "rule", "node", "path", "value");
        ValidationResult warningResult = new ValidationResult(ValidationSeverity.WARNING, "Warning message", "rule", "node", "path", "value");
        ValidationResult infoResult = new ValidationResult(ValidationSeverity.INFO, "Info message", "rule", "node", "path", "value");

        List<ValidationResult> results = Arrays.asList(errorResult, warningResult, infoResult);
        ISMDValidationReport report = new ISMDValidationReport(results, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(3, groups.size());

        Assertions.assertTrue(groups.stream().anyMatch(group -> group.getSeverity().equals("Chyba")));
        Assertions.assertTrue(groups.stream().anyMatch(group -> group.getSeverity().equals("Varování")));
        Assertions.assertTrue(groups.stream().anyMatch(group -> group.getSeverity().equals("Informace")));
    }

    @Test
    void testSeverityOrdering() {
        // Arrange
        ValidationResult infoResult = new ValidationResult(ValidationSeverity.INFO, "AAA Info message", "rule", "node", "path", "value");
        ValidationResult warningResult = new ValidationResult(ValidationSeverity.WARNING, "BBB Warning message", "rule", "node", "path", "value");
        ValidationResult errorResult = new ValidationResult(ValidationSeverity.ERROR, "CCC Error message", "rule", "node", "path", "value");

        List<ValidationResult> results = Arrays.asList(infoResult, warningResult, errorResult);
        ISMDValidationReport report = new ISMDValidationReport(results, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(3, groups.size());

        Assertions.assertEquals("Chyba", groups.get(0).getSeverity());
        Assertions.assertEquals("Varování", groups.get(1).getSeverity());
        Assertions.assertEquals("Informace", groups.get(2).getSeverity());
    }

    @Test
    void testMessageSorting_WithinSameSeverity() {
        // Arrange
        ValidationResult errorResult1 = new ValidationResult(ValidationSeverity.ERROR, "ZZZ Last message", "rule", "node", "path", "value");
        ValidationResult errorResult2 = new ValidationResult(ValidationSeverity.ERROR, "AAA First message", "rule", "node", "path", "value");
        ValidationResult errorResult3 = new ValidationResult(ValidationSeverity.ERROR, "MMM Middle message", "rule", "node", "path", "value");

        List<ValidationResult> results = Arrays.asList(errorResult1, errorResult2, errorResult3);
        ISMDValidationReport report = new ISMDValidationReport(results, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(3, groups.size());

        Assertions.assertEquals("Chyba", groups.get(0).getSeverity());
        Assertions.assertEquals("AAA First message", groups.get(0).getDescription());

        Assertions.assertEquals("Chyba", groups.get(1).getSeverity());
        Assertions.assertEquals("MMM Middle message", groups.get(1).getDescription());

        Assertions.assertEquals("Chyba", groups.get(2).getSeverity());
        Assertions.assertEquals("ZZZ Last message", groups.get(2).getDescription());
    }

    @Test
    void testMessageGrouping_SameMessageDifferentSeverity() {
        // Arrange
        ValidationResult errorResult = new ValidationResult(ValidationSeverity.ERROR, "Same message", "rule", "node1", "path", "value");
        ValidationResult warningResult = new ValidationResult(ValidationSeverity.WARNING, "Same message", "rule", "node2", "path", "value");

        List<ValidationResult> results = Arrays.asList(errorResult, warningResult);
        ISMDValidationReport report = new ISMDValidationReport(results, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(2, groups.size());

        Assertions.assertEquals("Chyba", groups.get(0).getSeverity());
        Assertions.assertEquals("Same message", groups.get(0).getDescription());
        Assertions.assertEquals(1, groups.get(0).getCount());

        Assertions.assertEquals("Varování", groups.get(1).getSeverity());
        Assertions.assertEquals("Same message", groups.get(1).getDescription());
        Assertions.assertEquals(1, groups.get(1).getCount());
    }

    @Test
    void testMessageGrouping_DuplicateMessages() {
        // Arrange
        ValidationResult errorResult1 = new ValidationResult(ValidationSeverity.ERROR, "Duplicate message", "rule", "node1", "path", "value1");
        ValidationResult errorResult2 = new ValidationResult(ValidationSeverity.ERROR, "Duplicate message", "rule", "node2", "path", "value2");
        ValidationResult errorResult3 = new ValidationResult(ValidationSeverity.ERROR, "Duplicate message", "rule", "node3", "path", "value3");

        List<ValidationResult> results = Arrays.asList(errorResult1, errorResult2, errorResult3);
        ISMDValidationReport report = new ISMDValidationReport(results, false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(report);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(1, groups.size());

        SeverityGroupDto group = groups.get(0);
        Assertions.assertEquals("Chyba", group.getSeverity());
        Assertions.assertEquals("Duplicate message", group.getDescription());
        Assertions.assertEquals(3, group.getCount());
    }

    @Test
    void testGlobalReportPrefixing() {
        // Arrange
        ValidationResult globalResult1 = new ValidationResult(ValidationSeverity.ERROR, "Global error", "rule", "node", "path", "value");
        ValidationResult globalResult2 = new ValidationResult(ValidationSeverity.WARNING, "Global warning", "rule", "node", "path", "value");

        ISMDValidationReport localReport = new ISMDValidationReport(Collections.emptyList(), true, Instant.now());
        ISMDValidationReport globalReport = new ISMDValidationReport(Arrays.asList(globalResult1, globalResult2), false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(localReport, globalReport);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(2, groups.size());

        Assertions.assertTrue(groups.stream().allMatch(group -> group.getDescription().startsWith("[GLOBAL]")));
        Assertions.assertTrue(groups.stream().anyMatch(group -> group.getDescription().equals("[GLOBAL] Global error")));
        Assertions.assertTrue(groups.stream().anyMatch(group -> group.getDescription().equals("[GLOBAL] Global warning")));
    }

    @Test
    void testCombinedReporting_ComplexScenario() {
        // Arrange
        ValidationResult localError = new ValidationResult(ValidationSeverity.ERROR, "Common error message", "rule", "node1", "path", "value");
        ValidationResult localWarning = new ValidationResult(ValidationSeverity.WARNING, "Local warning", "rule", "node2", "path", "value");

        ValidationResult globalError = new ValidationResult(ValidationSeverity.ERROR, "Common error message", "rule", "node3", "path", "value");
        ValidationResult globalInfo = new ValidationResult(ValidationSeverity.INFO, "Global info", "rule", "node4", "path", "value");

        ISMDValidationReport localReport = new ISMDValidationReport(Arrays.asList(localError, localWarning), false, Instant.now());
        ISMDValidationReport globalReport = new ISMDValidationReport(Arrays.asList(globalError, globalInfo), false, Instant.now());

        // Act
        ValidationResultsDto dto = validationReportService.convertToDto(localReport, globalReport);

        // Assert
        List<SeverityGroupDto> groups = dto.getSeverityGroups();
        Assertions.assertEquals(4, groups.size());

        long errorGroups = groups.stream()
                .filter(group -> group.getSeverity().equals("Chyba"))
                .count();
        Assertions.assertEquals(2, errorGroups);

        Assertions.assertTrue(groups.stream().anyMatch(group ->
                group.getSeverity().equals("Chyba") &&
                        group.getDescription().equals("Common error message") &&
                        group.getCount() == 1));

        Assertions.assertTrue(groups.stream().anyMatch(group ->
                group.getSeverity().equals("Chyba") &&
                        group.getDescription().equals("[GLOBAL] Common error message") &&
                        group.getCount() == 1));
    }
}
