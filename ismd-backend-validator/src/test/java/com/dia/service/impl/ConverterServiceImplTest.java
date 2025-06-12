package com.dia.service.impl;

import com.dia.engine.ConverterEngine;
import com.dia.enums.FileFormat;
import com.dia.exceptions.ExcelReadingException;
import com.dia.exceptions.FileParsingException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Ověření, že metody služby správně delegují volání ConverterEngine,
 * vracejí výsledky a propagují výjimky.
 */
@ExtendWith(MockitoExtension.class)
class ConverterServiceImplTest {

    @Mock
    private ConverterEngine converterEngine;

    @InjectMocks
    private ConverterServiceImpl converterService;

    //---- parseArchiFromString(String) ------------------------------------------------

    @Test
    void testParseArchiFromString_delegatesToEngineWithoutError() throws FileParsingException {
        // Platný obsah, který má engine rozpoznat jako spravný
        String sampleContent = "<archi>platný obsah</archi>";

        // Engine negeneruje výjimku; volá se metoda služby
        converterService.parseArchiFromString(sampleContent);

        // Ověření, že engine obdržel přesně ten samý obsah
        verify(converterEngine, times(1)).parseArchiFromString(sampleContent);
    }

    @Test
    void testParseArchiFromString_propagatesFileParsingException() throws FileParsingException {
        // Neplatný obsah, který má engine rozpoznat jako chybný
        String invalidContent = "Neplatný obsah";

        // Nastavujeme mock: při volání parseArchiFromString(invalidContent) má engine vyhodit FileParsingException
        doThrow(new FileParsingException("Chyba při parsování")).when(converterEngine).parseArchiFromString(invalidContent);

        // Ověření, že služba také vyhazuje FileParsingException
        assertThrows(FileParsingException.class, () -> converterService.parseArchiFromString(invalidContent),
                "Při neplatném vstupu by měla služba vyhodit FileParsingException");
    }

    @Test
    void testParseArchiFromString_withNullInput() throws FileParsingException {
        // Test behavior with null input
        converterService.parseArchiFromString(null);
        verify(converterEngine, times(1)).parseArchiFromString(null);
    }

    @Test
    void testParseArchiFromString_withEmptyString() throws FileParsingException {
        // Test behavior with empty string
        String emptyContent = "";
        converterService.parseArchiFromString(emptyContent);
        verify(converterEngine, times(1)).parseArchiFromString(emptyContent);
    }

    //---- convertArchi(Boolean) -------------------------------------------------------

    @Test
    void testConvertArchi_withTrue_delegatesToEngine() throws com.dia.exceptions.ConversionException {
        // Voláme se vstupem true — očekáváme, že engine bude volán se stejnou hodnotou
        converterService.convertArchi(true);
        verify(converterEngine, times(1)).convertArchi(true);
    }

    @Test
    void testConvertArchi_withFalse_delegatesToEngine() throws com.dia.exceptions.ConversionException {
        // Voláme se vstupem false — očekáváme, že engine bude volán se stejnou hodnotou
        converterService.convertArchi(false);
        verify(converterEngine, times(1)).convertArchi(false);
    }

    @Test
    void testConvertArchi_withNullInput() throws com.dia.exceptions.ConversionException {
        // Test behavior with null Boolean
        converterService.convertArchi(null);
        verify(converterEngine, times(1)).convertArchi(null);
    }

    @Test
    void testConvertArchi_propagatesConversionException() throws com.dia.exceptions.ConversionException {
        // Nastavujeme engine, aby házel ConversionException při removeInvalidSources = true
        doThrow(new com.dia.exceptions.ConversionException("Chyba konverze")).when(converterEngine).convertArchi(true);

        // Ověření, že služba hází ConversionException
        assertThrows(com.dia.exceptions.ConversionException.class, () -> converterService.convertArchi(true),
                "Při chybě konverze by měla služba propagovat ConversionException");
    }

    //---- exportToJson(FileFormat) ---------------------------------------------------------

    @Test
    void testExportToJson_archiFormat_returnsEngineValue() throws JsonExportException {
        // Nastavujeme engine, aby vracel předem známý JSON pro ARCHI_XML formát
        String expectedJson = "{\"status\":\"ok\"}";
        when(converterEngine.exportToJson(FileFormat.ARCHI_XML)).thenReturn(expectedJson);

        // Voláme službu s ARCHI_XML formátem
        String actualJson = converterService.exportToJson(FileFormat.ARCHI_XML);

        // Ověření, že vrácený JSON odpovídá tomu od engine a engine byl zavolán
        assertEquals(expectedJson, actualJson, "Vrácený JSON by měl odpovídat hodnotě z engine");
        verify(converterEngine, times(1)).exportToJson(FileFormat.ARCHI_XML);
    }

    @Test
    void testExportToJson_xlsxFormat_returnsEngineValue() throws JsonExportException {
        // Nastavujeme engine, aby vracel předem známý JSON pro XLSX formát
        String expectedJson = "{\"excel\":\"data\"}";
        when(converterEngine.exportToJson(FileFormat.XLSX)).thenReturn(expectedJson);

        // Voláme službu s XLSX formátem
        String actualJson = converterService.exportToJson(FileFormat.XLSX);

        // Ověření, že vrácený JSON odpovídá tomu od engine a engine byl zavolán
        assertEquals(expectedJson, actualJson, "Vrácený JSON by měl odpovídat hodnotě z engine");
        verify(converterEngine, times(1)).exportToJson(FileFormat.XLSX);
    }

    @Test
    void testExportToJson_propagatesJsonExportException() throws JsonExportException {
        // Nastavujeme, aby engine házel JsonExportException
        doThrow(new JsonExportException("Chyba exportu JSON")).when(converterEngine).exportToJson(FileFormat.ARCHI_XML);

        // Ověření, že služba hází JsonExportException
        assertThrows(JsonExportException.class, () -> converterService.exportToJson(FileFormat.ARCHI_XML),
                "Při chybě exportu JSON by měla služba propagovat JsonExportException");
    }

    @Test
    void testExportToJson_withNullReturn() throws JsonExportException {
        // Test when engine returns null
        when(converterEngine.exportToJson(FileFormat.ARCHI_XML)).thenReturn(null);

        String result = converterService.exportToJson(FileFormat.ARCHI_XML);

        assertNull(result, "Service should return null when engine returns null");
        verify(converterEngine, times(1)).exportToJson(FileFormat.ARCHI_XML);
    }

    //---- exportToTurtle(FileFormat) -------------------------------------------------------

    @Test
    void testExportToTurtle_archiFormat_returnsEngineValue() throws TurtleExportException {
        // Nastavujeme engine, aby vracel předem známý Turtle pro ARCHI_XML formát
        String expectedTurtle = "@prefix : <http://example.org/>";
        when(converterEngine.exportToTurtle(FileFormat.ARCHI_XML)).thenReturn(expectedTurtle);

        // Voláme službu s ARCHI_XML formátem
        String actualTurtle = converterService.exportToTurtle(FileFormat.ARCHI_XML);

        // Ověření, že vrácený Turtle odpovídá tomu od engine a engine byl zavolán
        assertEquals(expectedTurtle, actualTurtle, "Vrácený Turtle by měl odpovídat hodnotě z engine");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.ARCHI_XML);
    }

    @Test
    void testExportToTurtle_xlsxFormat_returnsEngineValue() throws TurtleExportException {
        // Nastavujeme engine, aby vracel předem známý Turtle pro XLSX formát
        String expectedTurtle = "@prefix excel: <http://example.org/excel#>";
        when(converterEngine.exportToTurtle(FileFormat.XLSX)).thenReturn(expectedTurtle);

        // Voláme službu s XLSX formátem
        String actualTurtle = converterService.exportToTurtle(FileFormat.XLSX);

        // Ověření, že vrácený Turtle odpovídá tomu od engine a engine byl zavolán
        assertEquals(expectedTurtle, actualTurtle, "Vrácený Turtle by měl odpovídat hodnotě z engine");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.XLSX);
    }

    @Test
    void testExportToTurtle_propagatesTurtleExportException() throws TurtleExportException {
        // Nastavujeme engine, aby házel TurtleExportException
        doThrow(new TurtleExportException("Chyba exportu Turtle")).when(converterEngine).exportToTurtle(FileFormat.ARCHI_XML);

        // Ověření, že služba hází TurtleExportException
        assertThrows(TurtleExportException.class, () -> converterService.exportToTurtle(FileFormat.ARCHI_XML),
                "Při chybě exportu Turtle by měla služba propagovat TurtleExportException");
    }

    @Test
    void testExportToTurtle_withEmptyReturn() throws TurtleExportException {
        // Test when engine returns empty string
        when(converterEngine.exportToTurtle(FileFormat.ARCHI_XML)).thenReturn("");

        String result = converterService.exportToTurtle(FileFormat.ARCHI_XML);

        assertEquals("", result, "Service should return empty string when engine returns empty string");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.ARCHI_XML);
    }

    //---- parseExcelFromFile(MultipartFile) -----------------------------------------------

    @Test
    void testParseExcelFromFile_delegatesToEngineWithoutError() throws ExcelReadingException, IOException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);

        // Engine negeneruje výjimku; volá se metoda služby
        converterService.parseExcelFromFile(mockFile);

        // Ověření, že engine obdržel přesně ten samý soubor
        verify(converterEngine, times(1)).parseExcelFromFile(mockFile);
    }

    @Test
    void testParseExcelFromFile_propagatesIOException() throws ExcelReadingException, IOException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);

        // Nastavujeme mock: při volání parseExcelFromFile má engine vyhodit IOException
        doThrow(new IOException("IO chyba")).when(converterEngine).parseExcelFromFile(mockFile);

        // Ověření, že služba také vyhazuje IOException
        assertThrows(IOException.class, () -> converterService.parseExcelFromFile(mockFile),
                "Při IO chybě by měla služba vyhodit IOException");
    }

    @Test
    void testParseExcelFromFile_propagatesExcelReadingException() throws ExcelReadingException, IOException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);

        // Nastavujeme mock: při volání parseExcelFromFile má engine vyhodit ExcelReadingException
        doThrow(new ExcelReadingException("Excel reading chyba")).when(converterEngine).parseExcelFromFile(mockFile);

        // Ověření, že služba také vyhazuje ExcelReadingException
        assertThrows(ExcelReadingException.class, () -> converterService.parseExcelFromFile(mockFile),
                "Při chybě čtení Excel souboru by měla služba vyhodit ExcelReadingException");
    }

    //---- convertExcel(boolean) -------------------------------------------------------

    @Test
    void testConvertExcel_withTrue_delegatesToEngine() throws com.dia.exceptions.ConversionException {
        // Voláme se vstupem true — očekáváme, že engine bude volán se stejnou hodnotou
        converterService.convertExcel(true);
        verify(converterEngine, times(1)).convertExcel(true);
    }

    @Test
    void testConvertExcel_withFalse_delegatesToEngine() throws com.dia.exceptions.ConversionException {
        // Voláme se vstupem false — očekáváme, že engine bude volán se stejnou hodnotou
        converterService.convertExcel(false);
        verify(converterEngine, times(1)).convertExcel(false);
    }

    @Test
    void testConvertExcel_propagatesConversionException() throws com.dia.exceptions.ConversionException {
        // Nastavujeme engine, aby házel ConversionException při removeInvalidSources = true
        doThrow(new com.dia.exceptions.ConversionException("Chyba Excel konverze")).when(converterEngine).convertExcel(true);

        // Ověření, že služba hází ConversionException
        assertThrows(com.dia.exceptions.ConversionException.class, () -> converterService.convertExcel(true),
                "Při chybě Excel konverze by měla služba propagovat ConversionException");
    }

    @Test
    void testConvertExcel_runtimeException_propagated() {
        // Nastavujeme engine, aby házel RuntimeException
        doThrow(new RuntimeException("Neočekávaná chyba")).when(converterEngine).convertExcel(false);

        // Ověření, že služba propaguje RuntimeException
        assertThrows(RuntimeException.class, () -> converterService.convertExcel(false),
                "Při neočekávané chybě by měla služba propagovat RuntimeException");
    }
}