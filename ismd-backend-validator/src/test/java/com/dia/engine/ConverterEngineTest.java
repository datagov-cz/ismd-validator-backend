package com.dia.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dia.converter.ArchiConverter;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConverterEngineTest {

    @Mock
    private ArchiConverter archiConverter;

    @InjectMocks
    private ConverterEngine converterEngine;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger testLogger;

    @BeforeEach
    void setUp() {
        // Vyčištění MDC a přidání ListAppender pro zachycení logů
        MDC.clear();
        testLogger = (Logger) LoggerFactory.getLogger(ConverterEngine.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        testLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        // Odebrání ListAppender a vyčištění MDC
        testLogger.detachAppender(listAppender);
        MDC.clear();
    }

    // ----- parseArchiFromString -------------------------------------------------------
    @Nested
    class ParseArchiFromStringTests {

        @Test
        void testParseArchiFromString_happyPath_logsAndVerify() throws Exception {
            // Nastavení MDC a stub pro parseFromString()
            MDC.put(LOG_REQUEST_ID, "req-1");
            String content = "<xml>valid content</xml>";
            doNothing().when(archiConverter).parseFromString(content);

            // Volání parseArchiFromString()
            converterEngine.parseArchiFromString(content);

            // Kontrola počtu logů (start + end)
            assertEquals(2, listAppender.list.size());
            ILoggingEvent start = listAppender.list.get(0);

            // Ověření formátu zpráv a přítomnost durationMs
            assertTrue(start.getFormattedMessage()
                    .contains("Starting Archi XML parsing: requestId=req-1, contentLength=" + content.length()));
            ILoggingEvent end = listAppender.list.get(1);

            // Ověření dokončení parsing
            assertTrue(end.getFormattedMessage()
                    .startsWith("Archi XML parsing completed: requestId=req-1, durationMs="));
        }

        @Test
        void testParseArchiFromString_fileParsingException_propagated() throws Exception {
            // Nastavení MDC a stub pro parseFromString() vyhazující FileParsingException
            MDC.put(LOG_REQUEST_ID, "req-2");
            String content = "<xml>invalid</xml>";
            FileParsingException fpe = new FileParsingException("chyba parsování");
            doThrow(fpe).when(archiConverter).parseFromString(content);

            // Volání parseArchiFromString() a zachycení FileParsingException
            FileParsingException thrown = assertThrows(FileParsingException.class,
                    () -> converterEngine.parseArchiFromString(content));

            // Ověření textu výjimky
            assertEquals("chyba parsování", thrown.getMessage());

            // Kontrola ERROR logu s requestId a chybovou zprávou
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to parse Archi XML: requestId=req-2, error=chyba parsování"));
        }

        @Test
        void testParseArchiFromString_unexpectedException_wrapped() throws Exception {
            // Nastavení MDC a stub pro parseFromString() vyhazující RuntimeException
            MDC.put(LOG_REQUEST_ID, "req-3");
            String content = "<xml>some content</xml>";
            RuntimeException re = new RuntimeException("nečekaná chyba");
            doThrow(re).when(archiConverter).parseFromString(content);

            // Volání parseArchiFromString() a zachycení FileParsingException
            FileParsingException thrown = assertThrows(FileParsingException.class,
                    () -> converterEngine.parseArchiFromString(content));

            // Ověření obalené výjimky a jejího cause
            assertEquals("Během čtení souboru došlo k nečekané chybě.", thrown.getMessage());
            assertSame(re, thrown.getCause());

            // Kontrola ERROR logu pro neočekávanou chybu
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Unexpected error during Archi XML parsing: requestId=req-3"));
        }

        @Test
        void testParseArchiFromString_nullContent_invoked() throws Exception {
            // Nastavení MDC a stub pro parseFromString(null)
            MDC.put(LOG_REQUEST_ID, "req-4");
            doNothing().when(archiConverter).parseFromString(null);

            // Volání parseArchiFromString(null)
            converterEngine.parseArchiFromString(null);

            // Ověření, že parseFromString(null) byl skutečně zavolán
            verify(archiConverter, times(1)).parseFromString(null);
        }
    }

    // ----- convertArchi -------------------------------------------------------
    @Nested
    class ConvertArchiTests {

        @Test
        void testConvertArchi_happyPath_logsAndVerify() {
            // Nastavení MDC a stub pro setRemoveELI() a convert()
            MDC.put(LOG_REQUEST_ID, "req-5");
            Boolean flag = true;
            doNothing().when(archiConverter).setRemoveELI(flag);
            doNothing().when(archiConverter).convert();

            // Volání convertArchi()
            converterEngine.convertArchi(flag);

            // Kontrola počtu logů (start + flag + end)
            assertEquals(3, listAppender.list.size());

            // Ověření start logu
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting Archi model conversion: requestId=req-5"));

            // Ověření logu flag
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("Invalid sources removal requested: true, requestId=req-5"));

            // Ověření dokončení conversion
            assertTrue(listAppender.list.get(2).getFormattedMessage()
                    .contains("Archi model conversion completed: requestId=req-5, durationMs="));
        }

        @Test
        void testConvertArchi_conversionException_propagated() {
            // Nastavení MDC a stub pro convert() vyhazující ConversionException
            MDC.put(LOG_REQUEST_ID, "req-6");
            Boolean flag = false;
            ConversionException ce = new ConversionException("chyba konverze");
            doNothing().when(archiConverter).setRemoveELI(flag);
            doThrow(ce).when(archiConverter).convert();

            // Volání convertArchi() a zachycení ConversionException
            ConversionException thrown = assertThrows(
                    ConversionException.class, () -> converterEngine.convertArchi(flag));

            // Ověření textu výjimky
            assertEquals("chyba konverze", thrown.getMessage());

            // Kontrola ERROR logu s requestId a chybou
            assertEquals(3, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(2);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to convert Archi model: requestId=req-6, error=chyba konverze"));
        }

        @Test
        void testConvertArchi_unexpectedException_wrapped() {
            // Nastavení MDC a stub pro convert() vyhazující RuntimeException
            MDC.put(LOG_REQUEST_ID, "req-7");
            Boolean flag = null;
            RuntimeException re = new RuntimeException("nečekaná chyba");
            doNothing().when(archiConverter).setRemoveELI(flag);
            doThrow(re).when(archiConverter).convert();

            // Volání convertArchi() a zachycení ConversionException
            ConversionException thrown = assertThrows(
                    ConversionException.class, () -> converterEngine.convertArchi(flag));

            // Ověření obalené výjimky a jejího cause
            assertEquals("Během konverze Archi souboru došlo k nečekané chybě.", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(3, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(2);
            assertTrue(error.getFormattedMessage()
                    .contains("Unexpected error during Archi model conversion: requestId=req-7"));
        }
    }

    // ----- exportToJson -------------------------------------------------------
    @Nested
    class ExportToJsonTests{

        @Test
        void testExportToJson_happyPath_logsAndVerify() {
            // Nastavení MDC a stub pro exportToJson()
            MDC.put(LOG_REQUEST_ID, "req-8");
            String result = "{\"k\":1}";
            when(archiConverter.exportToJson()).thenReturn(result);

            // Volání exportToJson()
            String out = converterEngine.exportToJson();

            // Kontrola návratové hodnoty
            assertEquals(result, out);

            // Ověření logů startu a dokončení s outputSize a durationMs
            assertEquals(2, listAppender.list.size());
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting JSON export: requestId=req-8"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("JSON export completed: requestId=req-8, outputSize=" + result.length() + ", durationMs="));
        }

        @Test
        void testExportToJson_jsonExportException_propagated() {
            // Nastavení MDC a stub pro exportToJson() vyhazující JsonExportException
            MDC.put(LOG_REQUEST_ID, "req-9");
            JsonExportException je = new JsonExportException("chyba JSON");
            when(archiConverter.exportToJson()).thenThrow(je);

            // Volání exportToJson() a zachycení JsonExportException
            JsonExportException thrown = assertThrows(
                    JsonExportException.class, () -> converterEngine.exportToJson());

            // Ověření textu výjimky
            assertEquals("chyba JSON", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to export to JSON: requestId=req-9, error=chyba JSON"));
        }

        @Test
        void testExportToJson_unexpectedException_wrapped() {
            // Nastavení MDC a stub pro exportToJson() vyhazující RuntimeException
            MDC.put(LOG_REQUEST_ID, "req-10");
            RuntimeException re = new RuntimeException("nečekaná chyba");
            when(archiConverter.exportToJson()).thenThrow(re);

            // Volání exportToJson() a zachycení JsonExportException
            JsonExportException thrown = assertThrows(
                    JsonExportException.class, () -> converterEngine.exportToJson());

            // Ověření obalené výjimky a jejího cause
            assertEquals("Během exportu do JSON došlo k nečekané chybě", thrown.getMessage());
            assertSame(re, thrown.getCause());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Unexpected error during JSON export: requestId=req-10"));
        }
    }

    // ----- exportToTurtle -------------------------------------------------------
    @Nested
    class ExportToTurtleTests{

        @Test
        void testExportToTurtle_happyPath_logsAndVerify() {
            // Nastavení MDC a stub pro exportToTurtle()
            MDC.put(LOG_REQUEST_ID, "req-11");
            String result = "@prefix";
            when(archiConverter.exportToTurtle()).thenReturn(result);

            // Volání exportToTurtle()
            String out = converterEngine.exportToTurtle();

            // Kontrola návratové hodnoty
            assertEquals(result, out);

            // Ověření logů startu a dokončení s outputSize a durationMs
            assertEquals(2, listAppender.list.size());
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting Turtle export: requestId=req-11"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("Turtle export completed: requestId=req-11, outputSize=" + result.length() + ", durationMs="));
        }

        @Test
        void testExportToTurtle_turtleExportException_propagated() {
            // Nastavení MDC a stub pro exportToTurtle() vyhazující TurtleExportException
            MDC.put(LOG_REQUEST_ID, "req-12");
            TurtleExportException te = new TurtleExportException("chyba Turtle");
            when(archiConverter.exportToTurtle()).thenThrow(te);

            // Volání exportToTurtle() и zachycení TurtleExportException
            TurtleExportException thrown = assertThrows(
                    TurtleExportException.class, () -> converterEngine.exportToTurtle());

            // Ověření textu výjimky
            assertEquals("chyba Turtle", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to export to Turtle: requestId=req-12, error=chyba Turtle"));
        }

        @Test
        void testExportToTurtle_unexpectedException_wrapped() {
            // Nastavení MDC a stub pro exportToTurtle() vyhazující RuntimeException
            MDC.put(LOG_REQUEST_ID, "req-13");
            RuntimeException re = new RuntimeException("nečekaná chyba");
            when(archiConverter.exportToTurtle()).thenThrow(re);

            // Volání exportToTurtle() и zachycení TurtleExportException
            TurtleExportException thrown = assertThrows(
                    TurtleExportException.class, () -> converterEngine.exportToTurtle());

            // Ověření obаlené výjimкy и jejího cause
            assertEquals("Během exportu do Turtle došlo k nečekané chybě.", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Unexpected error during Turtle export: requestId=req-13"));
        }
    }
}