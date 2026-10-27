package com.dia.validation;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.ofn.ConformanceIssue;
import com.dia.service.ValidationService;
import com.dia.validation.data.ISMDValidationReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A vocabulary that fails the OFN schema itself (here: no name) is still converted, but is reported
 * both as a conformance issue and as a SHACL violation, so the controller withholds the download.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvalidVocabularyTest {

    @Autowired
    private OFNDataTransformer transformer;
    @Autowired
    private ValidationService validationService;

    @Test
    void vocabularyWithoutName_isReportedAsViolation() throws Exception {
        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(new VocabularyMetadata())
                .classes(List.of())
                .properties(List.of())
                .relationships(List.of())
                .hierarchies(List.of())
                .build();

        TransformationResult result = transformer.transform(data);
        ISMDValidationReport report = validationService.validate(result);

        assertThat(result.conformanceIssues())
                .anyMatch(issue -> issue.kind() == ConformanceIssue.Kind.VOCABULARY_INVALID);
        assertThat(report.results())
                .anyMatch(r -> r.isError() && r.ruleName().endsWith("/název-slovníku"));
    }
}
