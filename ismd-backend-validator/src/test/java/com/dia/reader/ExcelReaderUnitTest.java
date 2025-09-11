package com.dia.reader;

import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static com.dia.constants.ExcelConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ExcelReaderUnitTest {

    private ExcelReader reader;

    @BeforeEach
    void setUp() {

        reader = new ExcelReader();

    }

    // ---------- Negativní scénáře (chybějící listy) ----------

    private InputStream asStream(Workbook wb) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }

    @Test
    void readOntologyFromExcel_missingVocabularySheet_shouldThrow() throws Exception {
        // Arrange: workbook WITHOUT the 'Slovník' sheet
        Workbook wb = new XSSFWorkbook();
        wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
        wb.createSheet(VLASTNOSTI);
        wb.createSheet(VZTAHY);

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));

            String msg = rootCauseMessage(ex);
            assertNotNull(msg, "Root cause message must not be null");
            assertTrue(msg.contains("Slovník"),
                    () -> "Expected root cause to mention 'Slovník', but was: " + msg);
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: throws when 'Subjekty a objekty práva' sheet is missing")
    void readOntologyFromExcel_missingClassesSheet_shouldThrow() throws Exception {
        // Arrange: workbook WITHOUT the 'Subjekty a objekty práva' sheet
        Workbook wb = new XSSFWorkbook();
        wb.createSheet(SLOVNIK);
        wb.createSheet(VLASTNOSTI);
        wb.createSheet(VZTAHY);

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));

            String msg = rootCauseMessage(ex);
            assertNotNull(msg);
            assertTrue(msg.contains(SUBJEKTY_OBJEKTY_PRAVA),
                    () -> "Expected root cause to mention '" + SUBJEKTY_OBJEKTY_PRAVA + "', but was: " + msg);
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: throws when 'Vlastnosti' sheet is missing")
    void readOntologyFromExcel_missingPropertiesSheet_shouldThrow() throws Exception {
        // Arrange: workbook WITHOUT the 'Vlastnosti' sheet
        Workbook wb = new XSSFWorkbook();
        wb.createSheet(SLOVNIK);
        wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
        wb.createSheet(VZTAHY);

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));

            String msg = rootCauseMessage(ex);
            assertNotNull(msg, "Root cause message must not be null");
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: throws when 'Vztahy' sheet is missing")
    void readOntologyFromExcel_missingRelationshipsSheet_shouldThrow() throws Exception {
        // Arrange: workbook WITHOUT the 'Vztahy' sheet
        Workbook wb = new XSSFWorkbook();
        wb.createSheet(SLOVNIK);
        wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
        wb.createSheet(VLASTNOSTI);

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));

            String msg = rootCauseMessage(ex);
            assertNotNull(msg, "Root cause message must not be null");
        }
    }

    // ---------- Pozitivní scénář (happy path) ----------

    private void createSheetWithHeader(Workbook wb, String sheetName, String[] headers) {
        var sheet = wb.createSheet(sheetName);
        var header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    private void addOneDataRow(Workbook wb, String sheetName, String[] values) {
        var sheet = wb.getSheet(sheetName);
        var row = sheet.createRow(1);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    @Test
    @DisplayName("happy path with minimal valid workbook")
    void readOntologyFromExcel_happyPath_shouldReturnData() throws Exception {
        // Slovník – pokud Vocabulary processor vyžaduje jiné názvy, uprav tady.
        final String[] HEADERS_SLOVNIK = {
                "prefix", "baseIRI", "label_cs", "label_en", "description_cs", "version"
        };

        // Subjekty a objekty práva – podle ClassSheetProcessor:
        // povinné sloupce: "Název", "Typ", "Popis", "Definice"
        final String[] HEADERS_CLASSES = {
                "Název", "Typ", "Popis", "Definice"
        };

        // Vlastnosti – rozumný minimální set (pokud procesor vyžaduje jiné názvy, přepiš na jeho přesné):
        // "Název" (name), "Doména" (domain), "Rozsah" (range), "Popis", "Definice"
        final String[] HEADERS_PROPERTIES = {
                "Název", "Doména", "Rozsah", "Popis", "Definice"
        };

        // Vztahy – podle RelationshipSheetProcessor:
        // hlavička obsahuje minimálně "Subjekt nebo objekt práva" a "Název";
        // data na indexech 0/1/2 = domain/name/range.
        final String[] HEADERS_RELATIONSHIPS = {
                "Subjekt nebo objekt práva", // col 0 (domain)
                "Název",                     // col 1 (name)
                "Rozsah"                     // col 2 (range)
        };

        Workbook wb = new XSSFWorkbook();

        // Slovník
        createSheetWithHeader(wb, SLOVNIK, HEADERS_SLOVNIK);
        addOneDataRow(wb, SLOVNIK, new String[]{
                "ex",
                "http://example.com/ontology/",
                "Ukázkový slovník",
                "Sample vocabulary",
                "Popis slovníku",
                "1.0.0"
        });

        // Subjekty a objekty práva – POZOR na hodnotu ve sloupci Typ
        // ExcelReader ve tvém logu očekává přesně „Subjekt práva“ nebo „Objekt práva“,
        // proto nepoužij „Třída“.
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{
                "Osoba",          // Název
                "Subjekt práva",  // Typ (validní hodnota)
                "Fyzická osoba",  // Popis
                "Definice osoby"  // Definice
        });

        // Vlastnosti – jméno vlastnosti + doména + rozsah + popis + definice
        createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        addOneDataRow(wb, VLASTNOSTI, new String[]{
                "hasName",                                        // Název
                "http://example.com/ontology/Osoba",              // Doména (můžeš použít i URI Person, pokud to procesor očekává)
                "http://www.w3.org/2001/XMLSchema#string",        // Rozsah
                "Osoba má jméno",                                 // Popis
                "Definice vlastnosti hasName"                     // Definice
        });

        // Vztahy – správné pořadí pro domain/name/range (indexy 0/1/2)
        createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        addOneDataRow(wb, VZTAHY, new String[]{
                "http://example.com/ontology/Osoba",              // domain (col 0)
                "http://example.com/ontology/hasName",            // name   (col 1)
                "http://www.w3.org/2001/XMLSchema#string"         // range  (col 2)
        });

        try (InputStream in = asStream(wb)) {
            var ontologyData = assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "ExcelReader should parse minimal valid workbook without throwing");
            assertNotNull(ontologyData);
        }
    }
}

