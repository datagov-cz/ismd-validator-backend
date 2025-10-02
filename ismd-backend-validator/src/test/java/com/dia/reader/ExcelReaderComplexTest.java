package com.dia.reader;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.excel.ExcelReader;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static com.dia.constants.ExcelConstants.*;
import static com.dia.constants.ExcelConstants.VZTAHY;
import static org.junit.jupiter.api.Assertions.*;

class ExcelReaderComplexTest {

    private ExcelReader reader;

    @BeforeEach
    void setUp() {
        reader = new ExcelReader();
    }

    private InputStream asStream(Workbook wb) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createSheetWithHeader(Workbook wb, String sheetName, String[] headers) {
        var sheet = wb.createSheet(sheetName);
        var header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: multiple classes - verify all are read correctly")
    void readOntologyFromExcel_multipleClasses_shouldReturnAllClassesWithCorrectData() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        var row2 = classSheet.createRow(2);
        row2.createCell(0).setCellValue("Dokument");
        row2.createCell(1).setCellValue("Objekt práva");
        row2.createCell(2).setCellValue("Písemný dokument");
        row2.createCell(3).setCellValue("Definice dokumentu");

        var row3 = classSheet.createRow(3);
        row3.createCell(0).setCellValue("Smlouva");
        row3.createCell(1).setCellValue("Objekt práva");
        row3.createCell(2).setCellValue("Smluvní dokument");
        row3.createCell(3).setCellValue("Definice smlouvy");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(3, data.getClasses().size(), "Should have exactly 3 classes");

            List<String> classNames = data.getClasses().stream()
                    .map(ClassData::getName)
                    .toList();
            Assertions.assertTrue(classNames.contains("Osoba"));
            Assertions.assertTrue(classNames.contains("Dokument"));
            Assertions.assertTrue(classNames.contains("Smlouva"));
        }
    }

    @Test
    @DisplayName("validateClassType: valid type 'Subjekt práva' - class is included")
    void validateClassType_validSubjektPrava_shouldIncludeClass() throws Exception {
        Workbook wb = minimalValidWorkbook();
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("Subjekt práva");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertEquals("Osoba", data.getClasses().get(0).getName());
            assertEquals("Subjekt práva", data.getClasses().get(0).getType());
        }
    }

    @Test
    @DisplayName("validateClassType: valid type 'Objekt práva' - class is included")
    void validateClassType_validObjektPrava_shouldIncludeClass() throws Exception {
        Workbook wb = minimalValidWorkbook();
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("Objekt práva");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertEquals("Osoba", data.getClasses().get(0).getName());
            assertEquals("Objekt práva", data.getClasses().get(0).getType());
        }
    }

    @Test
    @DisplayName("validateClassType: empty type - class is skipped")
    void validateClassType_emptyType_shouldSkipClass() throws Exception {
        Workbook wb = minimalValidWorkbook();
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("");  // Empty type

        var relSheet = wb.getSheet(VZTAHY);
        wb.removeSheetAt(wb.getSheetIndex(VZTAHY));
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getClasses().size(), "Class with empty type should be skipped");
        }
    }

    @Test
    @DisplayName("validateClassType: whitespace-only type - class is skipped")
    void validateClassType_whitespaceOnlyType_shouldSkipClass() throws Exception {
        Workbook wb = minimalValidWorkbook();
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("   ");

        var relSheet = wb.getSheet(VZTAHY);
        wb.removeSheetAt(wb.getSheetIndex(VZTAHY));
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getClasses().size(), "Class with whitespace-only type should be skipped");
        }
    }

    @Test
    @DisplayName("validateClassType: invalid type - class is skipped")
    void validateClassType_invalidType_shouldSkipClass() throws Exception {
        Workbook wb = minimalValidWorkbook();
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("Neplatný typ");

        wb.removeSheetAt(wb.getSheetIndex(VZTAHY));
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getClasses().size(), "Class with invalid type should be skipped");
        }
    }

    @Test
    @DisplayName("validateClassType: type with extra whitespace - should trim and accept")
    void validateClassType_typeWithWhitespace_shouldTrimAndAccept() throws Exception {
        Workbook wb = minimalValidWorkbook();
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("  Subjekt práva  ");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertEquals("Subjekt práva", data.getClasses().get(0).getType().trim());
        }
    }

    @Test
    @DisplayName("validateClassType: mixed valid and invalid types - only valid classes included")
    void validateClassType_mixedValidInvalid_shouldIncludeOnlyValid() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(1).setCellValue("Subjekt práva");

        var row2 = classSheet.createRow(2);
        row2.createCell(0).setCellValue("InvalidClass");
        row2.createCell(1).setCellValue("Špatný typ");
        row2.createCell(2).setCellValue("Popis");
        row2.createCell(3).setCellValue("Definice");

        var row3 = classSheet.createRow(3);
        row3.createCell(0).setCellValue("EmptyTypeClass");
        row3.createCell(1).setCellValue("");
        row3.createCell(2).setCellValue("Popis");
        row3.createCell(3).setCellValue("Definice");

        var row4 = classSheet.createRow(4);
        row4.createCell(0).setCellValue("Dokument");
        row4.createCell(1).setCellValue("Objekt práva");
        row4.createCell(2).setCellValue("Popis");
        row4.createCell(3).setCellValue("Definice");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(2, data.getClasses().size(), "Only 2 classes with valid types should be included");
            List<String> classNames = data.getClasses().stream()
                    .map(ClassData::getName)
                    .toList();
            Assertions.assertTrue(classNames.contains("Osoba"));
            Assertions.assertTrue(classNames.contains("Dokument"));
            assertFalse(classNames.contains("InvalidClass"));
            assertFalse(classNames.contains("EmptyTypeClass"));
        }
    }

    @Test
    @DisplayName("extractHierarchies: class with superClass - hierarchy is extracted")
    void extractHierarchies_classWithSuperClass_shouldCreateHierarchy() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(0).createCell(8).setCellValue("Nadřazený pojem");
        classSheet.getRow(1).createCell(8).setCellValue("Entita");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertNotNull(data.getHierarchies());
            assertEquals(1, data.getHierarchies().size(), "Should extract 1 class hierarchy");

            HierarchyData hierarchy = data.getHierarchies().get(0);
            assertEquals("Osoba", hierarchy.getSubClass());
            assertEquals("Entita", hierarchy.getSuperClass());
            Assertions.assertTrue(hierarchy.getRelationshipId().contains("CLASS-HIER"));
        }
    }

    @Test
    @DisplayName("extractHierarchies: property with superProperty - hierarchy is extracted")
    void extractHierarchies_propertyWithSuperProperty_shouldCreateHierarchy() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var propSheet = wb.getSheet(VLASTNOSTI);
        propSheet.getRow(0).createCell(7).setCellValue("Nadřazený pojem");
        propSheet.getRow(1).createCell(7).setCellValue("identifier");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertNotNull(data.getHierarchies());
            assertEquals(1, data.getHierarchies().size(), "Should extract 1 property hierarchy");

            HierarchyData hierarchy = data.getHierarchies().get(0);
            assertEquals("hasName", hierarchy.getSubClass());
            assertEquals("identifier", hierarchy.getSuperClass());
            Assertions.assertTrue(hierarchy.getRelationshipId().contains("PROP-HIER"));
        }
    }

    @Test
    @DisplayName("extractHierarchies: multiple hierarchies - all are extracted")
    void extractHierarchies_multipleHierarchies_shouldExtractAll() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(0).createCell(8).setCellValue("Nadřazený pojem");
        classSheet.getRow(1).createCell(8).setCellValue("Entita");

        var row2 = classSheet.createRow(2);
        row2.createCell(0).setCellValue("Dokument");
        row2.createCell(1).setCellValue("Objekt práva");
        row2.createCell(2).setCellValue("Popis");
        row2.createCell(3).setCellValue("Definice");
        row2.createCell(8).setCellValue("Zdroj");

        var propSheet = wb.getSheet(VLASTNOSTI);
        propSheet.getRow(0).createCell(7).setCellValue("Nadřazený pojem");
        propSheet.getRow(1).createCell(7).setCellValue("identifier");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(3, data.getHierarchies().size(), "Should extract 2 class + 1 property hierarchy");
        }
    }

    @Test
    @DisplayName("extractHierarchies: template concepts (Subjekt, Objekt) - should skip")
    void extractHierarchies_templateConcepts_shouldSkip() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(0).createCell(8).setCellValue("Nadřazený pojem");

        classSheet.getRow(1).createCell(8).setCellValue("Subjekt");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getHierarchies().size(),
                    "Template concept 'Subjekt' should be skipped from hierarchy");
        }
    }

    @Test
    @DisplayName("extractHierarchies: parent is URI - should skip")
    void extractHierarchies_parentIsURI_shouldSkip() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(0).createCell(8).setCellValue("Nadřazený pojem");
        classSheet.getRow(1).createCell(8).setCellValue("http://www.w3.org/2002/07/owl#Thing");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getHierarchies().size(),
                    "External URI parent should be skipped from hierarchy");
        }
    }

    @Test
    @DisplayName("extractHierarchies: self-referential hierarchy - should skip")
    void extractHierarchies_selfReferential_shouldSkip() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(0).createCell(8).setCellValue("Nadřazený pojem");
        classSheet.getRow(1).createCell(8).setCellValue("Osoba");  // Same as child name

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getHierarchies().size(),
                    "Self-referential hierarchy should be skipped");
        }
    }

    @Test
    @DisplayName("extractHierarchies: empty superClass - should skip")
    void extractHierarchies_emptySuperClass_shouldSkip() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(0).createCell(8).setCellValue("Nadřazený pojem");
        classSheet.getRow(1).createCell(8).setCellValue("   ");  // Whitespace only

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(0, data.getHierarchies().size(),
                    "Empty/whitespace superClass should be skipped");
        }
    }

    private void addOneDataRow(Workbook wb, String sheetName, String[] values) {
        var sheet = wb.getSheet(sheetName);
        var row = sheet.createRow(1);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private Workbook minimalValidWorkbook() {
        Workbook wb = new XSSFWorkbook();

        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Ukázkový slovník");
        var row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis slovníku pro test");
        var row2 = slovnik.createRow(r);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/ontology/");

        final String[] headersClasses = {"Název", "Typ", "Popis", "Definice"};
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, headersClasses);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});

        final String[] headersProperties = {"Název", "Subjekt nebo objekt práva", "Popis"};
        createSheetWithHeader(wb, VLASTNOSTI, headersProperties);
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        final String[] headersRelationships = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        createSheetWithHeader(wb, VZTAHY, headersRelationships);
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        return wb;
    }

    @Test
    @DisplayName("edgeCase: null values in data fields - should handle gracefully")
    void edgeCase_nullValues_shouldHandleGracefully() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(2).setCellValue("");  // Empty description
        classSheet.getRow(1).getCell(3).setCellValue("");  // Empty definition

        try (InputStream in = asStream(wb)) {
            OntologyData data = assertDoesNotThrow(() -> reader.readOntologyFromExcel(in));
            assertNotNull(data);
            assertEquals(1, data.getClasses().size());
        }
    }

    @Test
    @DisplayName("edgeCase: very long strings - should read correctly")
    void edgeCase_veryLongStrings_shouldReadCorrectly() throws Exception {
        Workbook wb = minimalValidWorkbook();

        String longDescription = "A".repeat(5000);
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(2).setCellValue(longDescription);

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertEquals(longDescription, data.getClasses().get(0).getDescription());
        }
    }

    @Test
    @DisplayName("edgeCase: special characters in data - should preserve correctly")
    void edgeCase_specialCharacters_shouldPreserve() throws Exception {
        Workbook wb = minimalValidWorkbook();

        String specialDesc = "Popis-ěščřžýáíé@#$%&*()[]{}";
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(2).setCellValue(specialDesc);

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertEquals(specialDesc, data.getClasses().get(0).getDescription());
        }
    }

    @Test
    @DisplayName("edgeCase: leading/trailing whitespace - should be preserved or trimmed appropriately")
    void edgeCase_whitespaceHandling_shouldBeConsistent() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(0).setCellValue("  Osoba  ");
        classSheet.getRow(1).getCell(2).setCellValue("  Popis s mezerami  ");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertNotNull(data.getClasses().get(0).getName());
            assertNotNull(data.getClasses().get(0).getDescription());
        }
    }

    @Test
    @DisplayName("edgeCase: numeric values in text fields - should convert to string")
    void edgeCase_numericValues_shouldConvertToString() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(2).setCellValue(12345);

        try (InputStream in = asStream(wb)) {
            OntologyData data = assertDoesNotThrow(() -> reader.readOntologyFromExcel(in));

            assertNotNull(data);
            assertEquals(1, data.getClasses().size());
            assertNotNull(data.getClasses().get(0).getDescription());
        }
    }

    @Test
    @DisplayName("edgeCase: empty class name - should skip or handle")
    void edgeCase_emptyClassName_shouldHandle() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(0).setCellValue("");  // Empty name

        wb.removeSheetAt(wb.getSheetIndex(VZTAHY));
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});

        try (InputStream in = asStream(wb)) {
            OntologyData data = assertDoesNotThrow(() -> reader.readOntologyFromExcel(in));

            assertEquals(0, data.getClasses().size());
        }
    }

    @Test
    @DisplayName("edgeCase: sheet with only header, no data - should return empty lists")
    void edgeCase_onlyHeader_shouldReturnEmptyLists() throws Exception {
        Workbook wb = new XSSFWorkbook();

        var slovnik = wb.createSheet(SLOVNIK);
        slovnik.createRow(0).createCell(0).setCellValue("Název slovníku:");
        slovnik.getRow(0).createCell(1).setCellValue("Test");
        slovnik.createRow(1).createCell(0).setCellValue("Popis slovníku:");
        slovnik.getRow(1).createCell(1).setCellValue("Popis");
        slovnik.createRow(2).createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        slovnik.getRow(2).createCell(1).setCellValue("http://example.com/");

        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});

        try (InputStream in = asStream(wb)) {
            OntologyData data = assertDoesNotThrow(() -> reader.readOntologyFromExcel(in));

            assertNotNull(data);
            assertEquals(0, data.getClasses().size());
            assertEquals(0, data.getProperties().size());
            assertEquals(0, data.getRelationships().size());
        }
    }

    @Test
    @DisplayName("edgeCase: duplicate class names - should include both")
    void edgeCase_duplicateClassNames_shouldIncludeBoth() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        var row2 = classSheet.createRow(2);
        row2.createCell(0).setCellValue("Osoba");  // Duplicate
        row2.createCell(1).setCellValue("Subjekt práva");
        row2.createCell(2).setCellValue("Jiný popis");
        row2.createCell(3).setCellValue("Jiná definice");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertFalse(data.getClasses().isEmpty());
        }
    }

    @Test
    @DisplayName("edgeCase: special characters in class name with matching relationships")
    void edgeCase_specialCharactersInName_shouldPreserve() throws Exception {
        Workbook wb = new XSSFWorkbook();

        var slovnik = wb.createSheet(SLOVNIK);
        slovnik.createRow(0).createCell(0).setCellValue("Název slovníku:");
        slovnik.getRow(0).createCell(1).setCellValue("Test");
        slovnik.createRow(1).createCell(0).setCellValue("Popis slovníku:");
        slovnik.getRow(1).createCell(1).setCellValue("Popis");
        slovnik.createRow(2).createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        slovnik.getRow(2).createCell(1).setCellValue("http://example.com/");

        String specialName = "Osoba-ěščř";

        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        var classRow = classSheet.createRow(1);
        classRow.createCell(0).setCellValue(specialName);
        classRow.createCell(1).setCellValue("Subjekt práva");
        classRow.createCell(2).setCellValue("Popis");
        classRow.createCell(3).setCellValue("Definice");

        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        var propSheet = wb.getSheet(VLASTNOSTI);
        var propRow = propSheet.createRow(1);
        propRow.createCell(0).setCellValue("hasName");
        propRow.createCell(1).setCellValue(specialName);  // Match the class name
        propRow.createCell(2).setCellValue("desc");

        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        var relSheet = wb.getSheet(VZTAHY);
        var relRow = relSheet.createRow(1);
        relRow.createCell(0).setCellValue(specialName);
        relRow.createCell(1).setCellValue("hasName");
        relRow.createCell(2).setCellValue("http://www.w3.org/2001/XMLSchema#string");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertEquals(1, data.getClasses().size());
            assertEquals(specialName, data.getClasses().get(0).getName());
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: happy path - verify complete data structure")
    void readOntologyFromExcel_happyPath_shouldReturnCompleteValidData() throws Exception {
        Workbook wb = minimalValidWorkbook();

        var classSheet = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        classSheet.getRow(1).getCell(2).setCellValue("Fyzická osoba v právním smyslu");
        classSheet.getRow(1).getCell(3).setCellValue("Definice dle zákona");

        var propSheet = wb.getSheet(VLASTNOSTI);
        propSheet.getRow(1).getCell(0).setCellValue("jméno");
        propSheet.getRow(1).getCell(2).setCellValue("Křestní jméno osoby");

        try (InputStream in = asStream(wb)) {
            OntologyData data = reader.readOntologyFromExcel(in);

            assertNotNull(data.getVocabularyMetadata());
            assertEquals("Ukázkový slovník", data.getVocabularyMetadata().getName());

            assertEquals(1, data.getClasses().size());
            ClassData classData = data.getClasses().get(0);
            assertEquals("Osoba", classData.getName());

            assertEquals(1, data.getProperties().size());
            PropertyData propData = data.getProperties().get(0);
            assertEquals("jméno", propData.getName());
            assertEquals("Osoba", propData.getDomain());

            assertEquals(1, data.getRelationships().size());
            RelationshipData relData = data.getRelationships().get(0);
            assertEquals("Osoba", relData.getDomain());
        }
    }
}
