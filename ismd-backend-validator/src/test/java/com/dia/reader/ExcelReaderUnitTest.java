package com.dia.reader;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.exceptions.ExcelReadingException;
import com.dia.utility.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;

class ExcelReaderUnitTest {

    private ExcelReader reader;

    @BeforeEach
    void setUp() {
        MDC.put(LOG_REQUEST_ID, "test-request-123");
        reader = new ExcelReader();
    }

    @Test
    void readOntologyFromExcel_ShouldOnlyPreserveValidIRIIdentifiers() throws Exception {
        OntologyData result;
        try (InputStream is = new ClassPathResource("com/dia/canonical/complete/testExcelProject.xlsx").getInputStream()) {
            result = reader.readOntologyFromExcel(is);
        }

        assertNotNull(result);

        result.getClasses().forEach(classData -> {
            if (classData.getIdentifier() != null) {
                assertTrue(UtilityMethods.isValidIRI(classData.getIdentifier()),
                        "Class identifier should be a valid IRI: '" + classData.getIdentifier()
                                + "' for class '" + classData.getName() + "'");
            }
        });

        result.getProperties().forEach(property -> {
            if (property.getIdentifier() != null) {
                assertTrue(UtilityMethods.isValidIRI(property.getIdentifier()),
                        "Property identifier should be a valid IRI: '" + property.getIdentifier()
                                + "' for property '" + property.getName() + "'");
            }
        });

        result.getRelationships().forEach(relationship -> {
            if (relationship.getIdentifier() != null) {
                assertTrue(UtilityMethods.isValidIRI(relationship.getIdentifier()),
                        "Relationship identifier should be a valid IRI: '" + relationship.getIdentifier()
                                + "' for relationship '" + relationship.getName() + "'");
            }
        });
    }
}
