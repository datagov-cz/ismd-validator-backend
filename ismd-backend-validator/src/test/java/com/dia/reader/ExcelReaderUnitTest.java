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

    private InputStream asStream(Workbook wb) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: throws when 'Slovník' sheet is missing")
    void readOntologyFromExcel_missingVocabularySheet_shouldThrow() throws Exception {
        // Arrange: workbook WITHOUT the 'Slovník' sheet
        Workbook wb = new XSSFWorkbook();
        wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
        wb.createSheet(VLASTNOSTI);
        wb.createSheet(VZTAHY);

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));

            assertTrue(ex.getMessage() != null && ex.getMessage().contains("Slovník"),
                    "Exception message should mention the missing 'Slovník' sheet");
        }
    }

    // @Test void readOntologyFromExcel_happyPath_shouldReturnDataAndValidate() { }
    // @Test void readOntologyFromExcel_missingClassesSheet_shouldThrow() { }
    // @Test void readOntologyFromExcel_missingPropertiesSheet_shouldThrow() { }
    // @Test void readOntologyFromExcel_missingRelationshipsSheet_shouldThrow() { }
}

