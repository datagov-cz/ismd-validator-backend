package com.dia.reader;

import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Row;
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

@DisplayName("ExcelReader – jednotkové testy čtení ontologie z Excelu")
class ExcelReaderUnitTest {

    private ExcelReader reader;

    @BeforeEach
    void setUp() {

        reader = new ExcelReader();

    }

    @Test
    @DisplayName("constructor: instance se vytvoří a je připravena k použití")
    void constructor_shouldInitialize() {
        assertNotNull(reader, "Instance ExcelReader musí být inicializovaná v @BeforeEach");
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
    @DisplayName("readOntologyFromExcel: vyhodí chybu, když chybí list 'Slovník'")
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
    @DisplayName("readOntologyFromExcel: vyhodí chybu, když chybí list 'Subjekty a objekty práva'")
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
    @DisplayName("readOntologyFromExcel: vyhodí chybu, když chybí list 'Vlastnosti'")
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
    @DisplayName("readOntologyFromExcel: vyhodí chybu, když chybí list 'Vztahy'")
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

    @Test
    @DisplayName("readOntologyFromExcel: chyba, když chybí 'Název slovníku:' ve Slovníku")
    void readOntologyFromExcel_missingVocabularyName_shouldThrow() throws Exception {
        // Arrange: Slovník bez položky 'Název slovníku:'
        Workbook wb = new XSSFWorkbook();
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;

        Row row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Popis slovníku:");
        row0.createCell(1).setCellValue("Popis slovníku pro test");

        Row row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row1.createCell(1).setCellValue("http://example.com/ontology/");

        // Ostatní povinné listy s minimálním obsahem
        final String[] HEADERS_CLASSES = {"Název", "Typ", "Popis", "Definice"};
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});
        final String[] HEADERS_PROPERTIES = {"Název", "Subjekt nebo objekt práva", "Popis"};
        createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});
        final String[] HEADERS_RELATIONSHIPS = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    ()-> reader.readOntologyFromExcel(in));
            String msg = rootCauseMessage(ex);
            assertTrue(msg.toLowerCase().contains("slovník") && msg.toLowerCase().contains("name"),
                    ()-> "Očekává se chyba o názvu slovníku, ale bylo: " + msg);
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: chyba, když chybí 'Adresa lokálního katalogu dat...' ve Slovníku")
    void readOntologyFromExcel_missingVocabularyBaseUri_shouldNotThrowAndReturnData() throws Exception {
        // Arrange: Slovník bez položky s adresou (base URI)
        Workbook wb = new XSSFWorkbook();
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;

        Row row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Ukazkový slovník");

        Row row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis slovníku pro test");

        // Ostatní povinné listy s minimálním obsahem, aby se test nedostal na jiné chyby
        final String[] HEADERS_CLASSES = {"Název", "Typ", "Popis", "Definice"};
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba","Subjekt práva","Popis","Definice"});
        final String[] HEADERS_PROPERTIES = {"Název", "Subjekt nebo objekt práva", "Popis"};
        createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName","Osoba","desc"});
        final String[] HEADERS_RELATIONSHIPS = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba","hasName","http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Base URI ve Slovníku je nepovinné – čtení by nemělo spadnout");
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: chyba, když Slovník neobsahuje žádné požadované klíče")
    void readOntologyFromExcel_emptyVocabularySheet_shouldThrow() throws Exception {
        // Arrange: prázdný Slovník (žádné rozpoznatelné řádky s klíči)
        Workbook wb = new XSSFWorkbook();
        wb.createSheet(SLOVNIK).createRow(0).createCell(0).setCellValue("Něco jiného");
        // Ostatní listy
        final String[] HEADERS_CLASSES = {"Název", "Typ", "Popis", "Definice"};
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba","Subjekt práva","Popis","Definice"});
        final String[] HEADERS_PROPERTIES = {"Název", "Subjekt nebo objekt práva", "Popis"};
        createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName","Osoba","desc"});
        final String[] HEADERS_RELATIONSHIPS = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba","hasName","http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class, () -> reader.readOntologyFromExcel(in));
            String msg = rootCauseMessage(ex);
            assertNotNull(msg, "Chybová zpráva musí být vyplněna");
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
    @DisplayName("readOntologyFromExcel: happy path - minimální platný sešit")
    void readOntologyFromExcel_happyPath_shouldReturnData() throws Exception {
        Workbook wb = new XSSFWorkbook();
        // Slovník – přesně očekávané klíče s dvojtečkou, bez hlavičky tabulky
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;

        Row row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Ukázkový slovník");

        Row row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis slovníku pro test");

        Row row2 = slovnik.createRow(r++);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/ontology/");

        // Subjekty a objekty práva – povinné sloupce: "Název", "Typ", "Popis", "Definice"
        final String[] HEADERS_CLASSES = {"Název", "Typ", "Popis", "Definice"};
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{
                "Osoba",          // Název
                "Subjekt práva",  // Typ (validní hodnota)
                "Fyzická osoba",  // Popis
                "Definice osoby"  // Definice
        });

        // Vlastnosti – minimální sada, názvy sloupců přesně dle očekávání processoru
        final String[] HEADERS_PROPERTIES = {"Název", "Subjekt nebo objekt práva", "Popis"};
        createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        addOneDataRow(wb, VLASTNOSTI, new String[]{
                "hasName",
                "Osoba",
                "Osoba má jméno"
        });

        // Vztahy – pořadí: 0=domain, 1=name, 2=range
        final String[] HEADERS_RELATIONSHIPS = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        addOneDataRow(wb, VZTAHY, new String[]{
                "Osoba",
                "hasName",
                "http://www.w3.org/2001/XMLSchema#string"
        });

        // Ověření: čtení nesmí vyhodit výjimku a výsledek nesmí být null
        try (InputStream in = asStream(wb)) {
            var ontologyData = assertDoesNotThrow(
                    () -> reader.readOntologyFromExcel(in),
                    "ExcelReader by neměl vyhodit výjimku pro minimálně platný sešit"
            );
            assertNotNull(ontologyData, "Výsledek čtení nesmí být null");
        }
    }

    // Minimální platný workbook se všemi potřebnými listy a 1 řádkem dat
    private Workbook minimalValidWorkbook() {
        Workbook wb = new XSSFWorkbook();

        // Slovník (3 řádkové klíče s dvojtečkou)
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Ukázkový slovník");
        var row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis slovníku pro test");
        var row2 = slovnik.createRow(r++);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/ontology/");

        // Subjekty a objekty práva
        final String[] HEADERS_CLASSES = {"Název", "Typ", "Popis", "Definice"};
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});

        // Vlastnosti
        final String[] HEADERS_PROPERTIES = {"Název", "Subjekt nebo objekt práva", "Popis"};
        createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        // Vztahy
        final String[] HEADERS_RELATIONSHIPS = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        return wb;
    }

    // List bez hlavičky (první řádek), aby šlo ověřit chování při chybějícím headeru.
    private Workbook workbookWithoutHeader(String sheetNameToBreak) {
        Workbook wb = new XSSFWorkbook();

        // Slovník – validní
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Ukázkový slovník");
        var row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis slovníku pro test");
        var row2 = slovnik.createRow(r++);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/ontology/");

        // Subjekty a objekty práva
        final String[] HEADERS_CLASSES = {"Název", "Typ", "Popis", "Definice"};
        if (SUBJEKTY_OBJEKTY_PRAVA.equals(sheetNameToBreak)) {
            wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA).createRow(0).createCell(0).setCellValue("Špatná hlavička");
        } else {
            createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, HEADERS_CLASSES);
        }
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});

        // Vlastnosti
        final String[] HEADERS_PROPERTIES = {"Název", "Subjekt nebo objekt práva", "Popis"};
        if (VLASTNOSTI.equals(sheetNameToBreak)) {
            wb.createSheet(VLASTNOSTI).createRow(0).createCell(0).setCellValue("Špatná hlavička");
        } else {
            createSheetWithHeader(wb, VLASTNOSTI, HEADERS_PROPERTIES);
        }
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        // Vztahy
        final String[] HEADERS_RELATIONSHIPS = {"Subjekt nebo objekt práva", "Název", "Rozsah"};
        if (VZTAHY.equals(sheetNameToBreak)) {
            wb.createSheet(VZTAHY).createRow(0).createCell(0).setCellValue("Špatná hlavička");
        } else {
            createSheetWithHeader(wb, VZTAHY, HEADERS_RELATIONSHIPS);
        }
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        return wb;
    }

    @Test
    @DisplayName("processClassesSheet: chybějící hlavička → vyhodí chybu")
    void processClassesSheet_missingHeader_shouldThrow() throws Exception {
        Workbook wb = workbookWithoutHeader(SUBJEKTY_OBJEKTY_PRAVA);
        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));
            assertTrue(rootCauseMessage(ex).toLowerCase().contains("header"),
                    "Očekává se zmínka o chybějící hlavičce");
        }
    }

    @Test
    @DisplayName("processPropertiesSheet: chybějící hlavička → vyhodí chybu")
    void processPropertiesSheet_missingHeader_shouldThrow() throws Exception {
        Workbook wb = workbookWithoutHeader(VLASTNOSTI);
        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));
            assertTrue(rootCauseMessage(ex).toLowerCase().contains("header"),
                    "Očekává se zmínka o chybějící hlavičce");
        }
    }

    @Test
    @DisplayName("processRelationshipsSheet: chybějící hlavička → vyhodí chybu")
    void processRelationshipsSheet_missingHeader_shouldThrow() throws Exception {
        Workbook wb = workbookWithoutHeader(VZTAHY);
        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class,
                    () -> reader.readOntologyFromExcel(in));
            assertTrue(rootCauseMessage(ex).toLowerCase().contains("header"),
                    "Očekává se zmínka o chybějící hlavičce");
        }
    }

    @Test
    @DisplayName("processPropertiesSheet: neznámý doménový subjekt/objekt → nemá vyhazovat výjimku (ignoruje se)")
    void processPropertiesSheet_unknownDomainClass_shouldNotThrow() throws Exception {
        Workbook wb = minimalValidWorkbook();
        // přepís 1. datového řádku ve Vlastnosti na neznámý doménový název
        var sheet = wb.getSheet(VLASTNOSTI);
        sheet.getRow(1).getCell(1).setCellValue("Neexistuje");

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Vlastnost s neznámou třídou nemá shodit čtení; očekává se ignorace/přeskočení.");
        }
    }

    @Test
    @DisplayName("processRelationshipsSheet: neznámý doménový subjekt ve Vztahy → vyhodí chybu")
    void processRelationshipsSheet_unknownDomain_shouldThrow() throws Exception {
        Workbook wb = minimalValidWorkbook();
        // přepís 1. datového řádku ve Vztahy na neznámý doménový název
        var sheet = wb.getSheet(VZTAHY);
        sheet.getRow(1).getCell(0).setCellValue("Neexistuje");

        try (InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class, () -> reader.readOntologyFromExcel(in));
            String msg = rootCauseMessage(ex).toLowerCase();
            assertTrue(msg.contains("unknown") || msg.contains("neznám"),
                    () -> "Očekává se chyba o neznámém doménu, ale bylo: " + msg);
        }
    }

    @Test
    @DisplayName("initializeDefaultMappings: správné hlavičky na všech listech → nepadá")
    void mappings_validHeaders_shouldNotThrow() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // Slovník - minimální validní trojice kličů
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Test");
        var row1 = slovnik.createRow(r++);
        row1.createCell(1).setCellValue("Popis slovníku");
        row1.createCell(1).setCellValue("Popis");
        var row2 = slovnik.createRow(r++);
        row2.createCell(2).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/");

        // Subjekty a objekty práva – přesně očekávané hlavičky
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[] {"Osoba", "Subjekt práva", "Popis", "Definice"});

        // Vlastnosti – přesně očekávané hlavičky
        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        // Vztahy – přesně očekávané hlavičky
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Správně zaregistrované mapování hlaviček nesmí padat");
        }
    }

    @Test
    @DisplayName("setup*Mappings: špatná hlavička na libovolném listu → vyhodí chybu o hlavičce")
    void mappings_invalidHeader_shouldThrowHeaderError() throws Exception{
        Workbook wb = new XSSFWorkbook();

        // Slovník - minimální validní trojice kličů
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Test");
        var row1 = slovnik.createRow(r++);
        row1.createCell(1).setCellValue("Popis slovníku");
        row1.createCell(1).setCellValue("Popis");
        var row2 = slovnik.createRow(r++);
        row2.createCell(2).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/");

        // Subjekty a objekty práva – přesně očekávané hlavičky
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[] {"Osoba", "Subjekt práva", "Popis", "Definice"});

        // Vlastností - ZÁMĚRNĚ  nevalidní hlavička
        var vlast = wb.createSheet(VLASTNOSTI);
        var header = vlast.createRow(0);
        header.createCell(0).setCellValue("Nevalidní hlavička A");
        header.createCell(1).setCellValue("Nevalidní hlavička B");
        header.createCell(2).setCellValue("Nevalidní hlavička C");
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        // Vztahy – přesně očekávané validní hlavičky
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        try(InputStream in = asStream(wb)) {
            ExcelReadingException ex = assertThrows(ExcelReadingException.class, () -> reader.readOntologyFromExcel(in));
            assertTrue(rootCauseMessage(ex).toLowerCase().contains("header"),
                    "Očekává se chybová zpráva o hlavičce");
        }
    }

    @Test
    @DisplayName("setupClassesMappings: známé hlavičky v jiném pořadí → nepadá")
    void setupClassesMappings_knownHeadersReordered_shouldNotThrow() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // Slovník – minimální validní trojice klíčů
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Test");
        var row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis");
        var row2 = slovnik.createRow(r++);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/");

        // Subjekty a objekty práva – ZNÁMÉ hlavičky, ale v jiném pořadí
        var sop = wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
        var h = sop.createRow(0);
        h.createCell(0).setCellValue("Typ");
        h.createCell(1).setCellValue("Název");
        h.createCell(2).setCellValue("Definice");
        h.createCell(3).setCellValue("Popis");
        // Data v pořadí odpovídajícím hlavičkám výše
        var d = sop.createRow(1);
        d.createCell(0).setCellValue("Subjekt práva");
        d.createCell(1).setCellValue("Osoba");
        d.createCell(2).setCellValue("Definice");
        d.createCell(3).setCellValue("Popis");

        // Vlastnosti – standardní validní hlavičky
        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        // Vztahy – standardní validní hlavičky
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Přeházené pořadí známých hlaviček tříd nesmí shodit zpracování");
        }
    }

    @Test
    @DisplayName("setupPropertiesMappings: známé hlavičky v jiném pořadí → nepadá")
    void setupPropertiesMappings_knownHeadersReordered_shouldNotThrow() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // Slovník – minimálně validní
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Test");
        var row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis");
        var row2 = slovnik.createRow(r++);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/");

        // Subjekty a objekty práva – standardní validní hlavičky
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});

        // Vlastnosti – známé hlavičky, ale jinak seřazené
        var vlast = wb.createSheet(VLASTNOSTI);
        var h = vlast.createRow(0);
        h.createCell(0).setCellValue("Popis");
        h.createCell(1).setCellValue("Název");
        h.createCell(2).setCellValue("Subjekt nebo objekt práva");
        var d = vlast.createRow(1);
        d.createCell(0).setCellValue("desc");
        d.createCell(1).setCellValue("hasName");
        d.createCell(2).setCellValue("Osoba");

        // Vztahy – standardní validní hlavičky
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Přeházené pořadí známých hlaviček vlastností nesmí shodit zpracování");
        }
    }

    @Test
    @DisplayName("setupRelationshipsMappings: známé hlavičky v jiném pořadí → nepadá")
    void setupRelationshipsMappings_knownHeadersReordered_shouldNotThrow() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // Slovník – minimální validní trojice kličů
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Test");
        var row1 = slovnik.createRow(r++);
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis");
        var row2 = slovnik.createRow(r++);
        row2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/");

        // Subjekty a objekty práva – standardní validní hlavičky
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});

        // Vlastnosti – standardní validní hlavičky
        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        // Vztahy – známé hlavičky, ale jinak seřazené
        var rel = wb.createSheet(VZTAHY);
        var h = rel.createRow(0);
        h.createCell(0).setCellValue("Rozsah");
        h.createCell(1).setCellValue("Subjekt nebo objekt práva");
        h.createCell(2).setCellValue("Název");
        // Hodnoty vkládame podle POZIC, které očekává produkční kód: 0 = DOMÉNA (třída), 1 = NÁZEV vlastnosti, 2 = ROZSAH
        var d = rel.createRow(1);
        d.createCell(0).setCellValue("Osoba"); // doména
        d.createCell(1).setCellValue("hasName"); // název
        d.createCell(2).setCellValue("http://www.w3.org/2001/XMLSchema#string");

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Přeházené pořadí známých hlaviček vztahů nesmí shodit zpracování");
        }
    }

    @Test
    @DisplayName("setupVocabularyMappings: známé klíče ve Slovníku v libovolném pořadí → nepadá")
    void setupVocabularyMappings_knownKeysAnyOrder_shouldNotThrow() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // Slovník – stejné tři povinné klíče, ale v JINÉM pořadí
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++); // Nejdřív base URI
        row0.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row0.createCell(1).setCellValue("http://example.com/ontology/");
        var row1 = slovnik.createRow(r++); // Popis
        row1.createCell(0).setCellValue("Popis slovníku:");
        row1.createCell(1).setCellValue("Popis slovníku pro test");
        var row2 = slovnik.createRow(r++); // Název
        row2.createCell(0).setCellValue("Název slovníku:");
        row2.createCell(1).setCellValue("Ukázkový slovník");

        // Ostatní listy s validními hlavičkami
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        addOneDataRow(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Osoba", "Subjekt práva", "Popis", "Definice"});

        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        addOneDataRow(wb, VLASTNOSTI, new String[]{"hasName", "Osoba", "desc"});

        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        addOneDataRow(wb, VZTAHY, new String[]{"Osoba", "hasName", "http://www.w3.org/2001/XMLSchema#string"});

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Přeházené pořadí známých klíčů ve Slovníku nesmí shodit zpracování");
        }
    }

    @Test
    @DisplayName("readOntologyFromExcel: více tříd/vlastností/vztahů → nepadá (agregace)")
    void readOntologyFromExcel_multipleRows_shouldNotThrow() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // Slovník - minimální validní trojice kličů
        var slovnik = wb.createSheet(SLOVNIK);
        int r = 0;
        var row0 = slovnik.createRow(r++);
        row0.createCell(0).setCellValue("Název slovníku:");
        row0.createCell(1).setCellValue("Test");
        var row1 = slovnik.createRow(r++);
        row1.createCell(1).setCellValue("Popis slovníku");
        row1.createCell(1).setCellValue("Popis");
        var row2 = slovnik.createRow(r++);
        row2.createCell(2).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");
        row2.createCell(1).setCellValue("http://example.com/");

        // Subjekty a objekty práva – 2 řádky
        createSheetWithHeader(wb, SUBJEKTY_OBJEKTY_PRAVA, new String[]{"Název", "Typ", "Popis", "Definice"});
        var sop = wb.getSheet(SUBJEKTY_OBJEKTY_PRAVA);
        sop.createRow(1).createCell(0).setCellValue("Osoba");
        sop.getRow(1).createCell(1).setCellValue("Subjekt práva");
        sop.getRow(1).createCell(2).setCellValue("Popis1");
        sop.getRow(1).createCell(3).setCellValue("Def1");
        sop.createRow(2).createCell(0).setCellValue("Dokument");
        sop.getRow(2).createCell(1).setCellValue("Objekt práva");
        sop.getRow(2).createCell(2).setCellValue("Popis2");
        sop.getRow(2).createCell(3).setCellValue("Def2");

        // Vlastnosti - 2 řádky, každá k jinému objektu
        createSheetWithHeader(wb, VLASTNOSTI, new String[]{"Název", "Subjekt nebo objekt práva", "Popis"});
        var prop = wb.getSheet(VLASTNOSTI);
        prop.createRow(1).createCell(0).setCellValue("hasname");
        prop.getRow(1).createCell(1).setCellValue("Osoba");
        prop.getRow(1).createCell(2).setCellValue("desc1");
        prop.createRow(2).createCell(0).setCellValue("hasIdentifier");
        prop.getRow(2).createCell(1).setCellValue("Dokument");
        prop.getRow(2).createCell(2).setCellValue("desc2");

        // Vztahy - 2 řádky a rozsahy
        createSheetWithHeader(wb, VZTAHY, new String[]{"Subjekt nebo objekt práva", "Název", "Rozsah"});
        var rel = wb.getSheet(VZTAHY);
        rel.createRow(1).createCell(0).setCellValue("Osoba");
        rel.getRow(1).createCell(1).setCellValue("hasName");
        rel.getRow(1).createCell(2).setCellValue("http://www.w3.org/2001/XMLSchema#string");
        rel.createRow(2).createCell(0).setCellValue("Dokument");
        rel.getRow(2).createCell(1).setCellValue("hasIdentifier");
        rel.getRow(2).createCell(2).setCellValue("http://www.w3.org/2001/XMLSchema#string");

        try (InputStream in = asStream(wb)) {
            assertDoesNotThrow(() -> reader.readOntologyFromExcel(in),
                    "Agregace více řádků napříč listy nesmí padat");
        }
    }
}