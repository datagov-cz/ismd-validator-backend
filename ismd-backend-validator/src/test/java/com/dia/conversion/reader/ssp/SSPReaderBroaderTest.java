package com.dia.conversion.reader.ssp;

import com.dia.conversion.reader.ssp.data.ConceptData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** skos:broader read from SSP reaches every concept kind (nadřazený-pojem is valid for all). */
class SSPReaderBroaderTest {

    private static final String IRI = "https://slovník.gov.cz/test/pojem/a";
    private static final String B1 = "https://slovník.gov.cz/test/pojem/b";
    private static final String B2 = "https://slovník.gov.cz/jiný/pojem/c";

    private final SSPReader reader = new SSPReader(null);

    @Test
    void broader_accumulatesAcrossResultRows_withoutDuplicates() {
        ConceptData concept = concept();

        assertThat(concept.getBroader()).containsExactly(B1, B2);
    }

    @Test
    void broader_isPassedToClassesPropertiesAndRelationships() {
        assertThat(reader.convertToClassData(concept(), IRI).getBroaderConcepts()).containsExactly(B1, B2);
        assertThat(reader.convertToPropertyData(concept(), IRI, null).getBroaderConcepts()).containsExactly(B1, B2);
        assertThat(reader.convertToRelationshipData(concept(), IRI, null).getBroaderConcepts()).containsExactly(B1, B2);
    }

    private static ConceptData concept() {
        ConceptData concept = new ConceptData();
        concept.setIri(IRI);
        concept.setName("a");
        for (String broader : List.of(B1, B2, B1)) { // one row per OPTIONAL combination
            concept.addBroader(broader);
        }
        concept.addBroader(null);
        return concept;
    }
}
