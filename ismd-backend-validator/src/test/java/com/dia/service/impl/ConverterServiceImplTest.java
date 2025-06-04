package com.dia.service.impl;

import com.dia.engine.ConverterEngine;
import com.dia.exceptions.FileParsingException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import org.apache.jena.ontology.ConversionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ověření, že metody služby správně delegují volání ConverterEngine,
 * vracejí výsledky a propagují výjimky.
 */
@ExtendWith(MockitoExtension.class)
public class ConverterServiceImplTest {

    @Mock
    private ConverterEngine converterEngine;

    @InjectMocks
    private ConverterServiceImpl converterService;

    //---- parseArchiFromString(String) ------------------------------------------------

    @Test
    void testParseArchiFromString_delegatesToEngineWithoutError () throws FileParsingException {
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

    //---- convertArchi(Boolean) -------------------------------------------------------

    @Test
    void testConvertArchi_withTrue_delegatesToEngine () throws ConversionException {
        // Voláme se vstupem true — očekáváme, že engine bude volán se stejnou hodnotou
        converterService.convertArchi(true);
        verify(converterEngine, times(1)).convertArchi(true);
    }

    @Test
    void testConvertArchi_withFalse_delegatesToEngine () throws ConversionException {
        // Voláme se vstupem false — očekáváme, že engine bude volán se stejnou hodnotou
        converterService.convertArchi(false);
        verify(converterEngine, times(1)).convertArchi(false);
    }

    @Test
    void testConvertArchi_propagatesConversionException () throws ConversionException {
        // Nastavujeme engine, aby házel ConversionException při removeInvalidSources = true
        doThrow(new ConversionException("Chyba konverze")).when(converterEngine).convertArchi(true);

        // Ověření, že služba hází ConversionException
        assertThrows(ConversionException.class, () -> converterService.convertArchi(true),
                "Při chybě konverze by měla služba propagovat ConversionException");
    }

    //---- exportArchiToJson() ---------------------------------------------------------

    @Test
    void testExportArchiToJson_returnsEngineValue () throws JsonExportException {
        // Nastavujeme engine, aby vracel předem známý JSON
        String expectedJson = "{\"status\":\"ok\"}";
        when(converterEngine.exportToJson()).thenReturn(expectedJson);

        // Voláme službu
        String actualJson = converterService.exportArchiToJson();

        // Ověření, že vrácený JSON odpovídá tomu od engine a engine byl zavolán
        assertEquals(expectedJson, actualJson, "Vrácený JSON by měl odpovídat hodnotě z engine");
        verify(converterEngine, times(1)).exportToJson();
    }

    @Test
    void testExportArchiToJson_propagatesJsonExportException () throws JsonExportException {
        // Nastavujeme, aby engine házel JsonExportException
        doThrow(new JsonExportException("Chyba exportu JSON")).when(converterEngine).exportToJson();

        // Ověření, že služba hází JsonExportException
        assertThrows(JsonExportException.class, () -> converterService.exportArchiToJson(),
                "Při chybě exportu JSON by měla služba propagovat JsonExportException");
    }

    //---- exportArchiToTurtle() -------------------------------------------------------

    @Test
    void testExportArchiToTurtle_returnsEngineValue () throws TurtleExportException {
        // Nastavujeme engine, aby vracel předem známý Turtle
        String expectedTurtle = "@prefix : <http://example.org/>";
        when(converterEngine.exportToTurtle()).thenReturn(expectedTurtle);

        // Voláme službu
        String actualTurtle = converterService.exportArchiToTurtle();

        // Ověření, že vrácený Turtle odpovídá tomu od engine a engine byl zavolán
        assertEquals(expectedTurtle, actualTurtle, "Vrácený Turtle by měl odpovídat hodnotě z engine");
        verify(converterEngine, times(1)).exportToTurtle();
    }

    @Test
    void testExportArchiToTurtle_propagatesTurtleExportException () throws TurtleExportException {
        // Nastavujeme engine, aby házel TurtleExportException
        doThrow(new TurtleExportException("Chyba exportu Turtle")).when(converterEngine).exportToTurtle();

        // Ověření, že služba hází TurtleExportException
        assertThrows(TurtleExportException.class, () -> converterService.exportArchiToTurtle(),
                "Při chybě exportu Turtle by měla služba propagovat TurtleExportException");
    }

}