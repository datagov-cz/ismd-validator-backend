package com.dia.conversion.reader;

import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Readers warn about input they do not convert (unknown Excel columns, Archi properties, EA tags),
 * and stay quiet on the template fixtures.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UnknownInputWarningTest {

    private static final String DIR = "com/dia/canonical/complete/";
    private static final String UNKNOWN = "Moje poznámka";

    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ArchiReader archiReader;
    @Autowired
    private EnterpriseArchitectReader eaReader;

    @Test
    void templateFixtures_produceNoWarnings(CapturedOutput output) throws Exception {
        try (InputStream in = new ClassPathResource(DIR + "testExcelProject_no-lkod.xlsx").getInputStream()) {
            excelReader.readOntologyFromExcel(in);
        }
        archiReader.readArchiFromString(text("testArchiInput_no-lkod.xml"));
        eaReader.readXmiFromBytes(text("testEAInput_no-lkod.xml").getBytes(StandardCharsets.UTF_8));

        assertThat(output.getOut()).doesNotContain("is not a known");
    }

    @Test
    void excel_unknownColumnWithValues_isWarned(CapturedOutput output) throws Exception {
        byte[] workbook;
        try (InputStream in = new ClassPathResource(DIR + "testExcelProject_no-lkod.xlsx").getInputStream();
             Workbook wb = new XSSFWorkbook(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheet("Subjekty a objekty práva");
            Row header = findRowStartingWith(sheet, "Název");
            int column = header.getLastCellNum();
            header.createCell(column).setCellValue(UNKNOWN);
            sheet.getRow(header.getRowNum() + 1).createCell(column).setCellValue("nějaká hodnota");
            wb.write(out);
            workbook = out.toByteArray();
        }

        excelReader.readOntologyFromExcel(new ByteArrayInputStream(workbook));

        assertThat(output.getOut())
                .contains("column '" + UNKNOWN + "' is not a known template column; its values in 1 row(s) are ignored");
    }

    @Test
    void archi_unknownPropertyWithValue_isWarned(CapturedOutput output) throws Exception {
        String xml = text("testArchiInput_no-lkod.xml")
                .replace("<propertyDefinitions>", """
                        <propertyDefinitions>
                            <propertyDefinition identifier="propid-unknown" type="string"><name>%s</name></propertyDefinition>
                        """.formatted(UNKNOWN))
                .replaceFirst("<properties>\\s*<property ", """
                        <properties>
                                <property propertyDefinitionRef="propid-unknown"><value xml:lang="cs">x</value></property>
                                <property\s"""); // the first <properties> block is the model's

        archiReader.readArchiFromString(xml);

        assertThat(output.getOut()).contains("Archi property '" + UNKNOWN + "' is not a known template property");
    }

    @Test
    void ea_unknownTagWithValue_isWarned(CapturedOutput output) throws Exception {
        String xmi = text("testEAInput_no-lkod.xml").replaceFirst("<tag ",
                "<tag xmi:id=\"EAID_UNKNOWN\" name=\"" + UNKNOWN + "\" value=\"x\"/><tag ");

        eaReader.readXmiFromBytes(xmi.getBytes(StandardCharsets.UTF_8));

        assertThat(output.getOut()).contains("EA tag '" + UNKNOWN + "' is not a known template attribute; its 1 value(s) are ignored");
    }

    private static Row findRowStartingWith(Sheet sheet, String text) {
        for (Row row : sheet) {
            Cell first = row.getCell(0);
            if (first != null && first.getCellType() == CellType.STRING && text.equals(first.getStringCellValue().trim())) {
                return row;
            }
        }
        throw new AssertionError("no header row in " + sheet.getSheetName());
    }

    private static String text(String fixture) throws Exception {
        try (InputStream in = new ClassPathResource(DIR + fixture).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
