package com.dia.validation;

import com.dia.conversion.data.*;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.exception.MissingVocabularyNameException;
import com.dia.exception.OfnConformanceException;
import com.dia.ofn.ConformanceIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A vocabulary without a name is an invalid request: it is rejected before conversion, for every
 * input format, instead of producing an output with no concepts.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvalidVocabularyTest {

    @Autowired
    private OFNDataTransformer transformer;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void vocabularyWithoutName_isRejected(String name) {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName(name);
        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(List.of())
                .properties(List.of())
                .relationships(List.of())
                .hierarchies(List.of())
                .build();

        assertThatThrownBy(() -> transformer.transform(data))
                .isInstanceOf(MissingVocabularyNameException.class)
                .hasMessage("Slovník nemá vyplněný název.");
    }

    /** T1 as in ismd-tool: a vocabulary failing the OFN schema itself refuses the whole conversion. */
    @Test
    void vocabularyFailingTheOfnSchema_isRejected_withItsIssues() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName("Slovník");
        metadata.setIdentifier("https://slovník.gov.cz/test/vadný%zz");
        ClassData řidič = new ClassData();
        řidič.setName("Řidič");
        OntologyData data = OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(List.of(řidič))
                .properties(List.of())
                .relationships(List.of())
                .hierarchies(List.of())
                .build();

        assertThatThrownBy(() -> transformer.transform(data))
                .isInstanceOfSatisfying(OfnConformanceException.class, e -> {
                    assertThat(e.getVocabularyIri()).isEqualTo("https://slovník.gov.cz/test/vadný%zz");
                    assertThat(e.getIssues()).isNotEmpty()
                            .allMatch(issue -> issue.kind() == ConformanceIssue.Kind.VOCABULARY_INVALID
                                    && issue.conceptIri() == null);
                });
    }
}
