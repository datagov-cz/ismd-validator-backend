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
        // Vyčištění MDC a nastavení ListAppender pro zachycení logů
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

    //Test parseArchiFromString: pozitivní scénář, správné logování startu a dokončení s timingem
    @Nested
    class ParseArchiFromStringTests {
        @Test
        void testParseArchiFromString_happyPath() throws Exception {
            // Nastavení MDC a stub pro parseFromString()
            MDC.put(LOG_REQUEST_ID, "req-1");
            String content = "<xml>data</xml>";
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
            assertTrue(end.getFormattedMessage()
                    .startsWith("Archi XML parsing completed: requestId=req-1, durationMs="));
        }


        @Test
        void testParseArchiFromString_fileParsingExceptionIsPropagated() throws Exception {
            String xmlContent = "<xml>invalid content</xml>";

            // Připravíme, aby archiConverter.parseFromString vyhodil FileParsingException
            FileParsingException fpe = new FileParsingException("Chyba při parsování!");
            doThrow(fpe).when(archiConverter).parseFromString(xmlContent);

            // Očekává se, že se propadne stejná FileParsingException
            FileParsingException thrown = assertThrows(
                    FileParsingException.class, () -> converterEngine.parseArchiFromString(xmlContent));

            // Oveření textu výjimky a že metoda parseFromString byla zavolána právě jednou
            assertEquals("Chyba při parsování!", thrown.getMessage());
            verify(archiConverter, times(1)).parseFromString(xmlContent);
        }

        @Test
        void testParseArchiFromString_genericExceptionIsWrapped() throws Exception {
            String xmlContent = "<xml>some content</xml>";

            // Připravíme, aby archiConverter.parseFromString vyhodil obecnou RuntimeException
            RuntimeException re = new RuntimeException("Neočekávaná chyba");
            doThrow(re).when(archiConverter).parseFromString(xmlContent);

            // Očekává se, že se chytí a vloží do FileParsingException se zprávou
            FileParsingException thrown = assertThrows(
                    FileParsingException.class, () -> converterEngine.parseArchiFromString(xmlContent));

            // Ověření, že zpráva odpovídá implementaci v ConverterEngine a že důvod (cause) je původní RuntimeException
            assertEquals("Během čtení souboru došlo k nečekané chybě.", thrown.getMessage());
            assertSame(re, thrown.getCause());
            verify(archiConverter, times(1)).parseFromString(xmlContent);
        }

        @Test
        void testParseArchiFromString_nullContent() throws Exception {
            String xmlContent = null;

            // ArchiConverter.parseFromString(null) nevyhodí výjimku
            doNothing().when(archiConverter).parseFromString(null);

            // Volání testované metody s 'null'
            converterEngine.parseArchiFromString(xmlContent);

            // Ověření, že parseFromString byl zavolán s null
            verify(archiConverter, times(1)).parseFromString(null);
        }
    }

    //---- convertArchi -------------------------------------------------------
    @Nested
    class ConvertArchiTests {

        @Test
        void testConvertArchi_successTrue() throws Exception {
            Boolean removeInvalid = true;

            // ArchiConverter.setRemoveELI (true) a convert() nevyhodí výjimku
            doNothing().when(archiConverter).setRemoveELI(removeInvalid);
            doNothing().when(archiConverter).convert();

            // Volání testováné metody
            converterEngine.convertArchi(removeInvalid);

            // Ověření, že nejprve byl nastaven flag removeInvalidSources a pak convert()
            verify(archiConverter, times(1)).setRemoveELI(removeInvalid);
            verify(archiConverter, times(1)).convert();
        }

        @Test
        void testConvertArchi_successFalse() throws Exception {
            Boolean removeInvalid = false;

            doNothing().when(archiConverter).setRemoveELI(removeInvalid);
            doNothing().when(archiConverter).convert();

            converterEngine.convertArchi(removeInvalid);

            verify(archiConverter, times(1)).setRemoveELI(removeInvalid);
            verify(archiConverter, times(1)).convert();
        }

        @Test
        void testConvertArchi_nullFlag() throws Exception {
            Boolean removeInvalid = null;

            // Ověření, že při removeInvalidSources = null se volá setRemoveELI(null) a poté convert()
            doNothing().when(archiConverter).setRemoveELI(null);
            doNothing().when(archiConverter).convert();

            converterEngine.convertArchi(removeInvalid);

            verify(archiConverter, times(1)).setRemoveELI(null);
            verify(archiConverter, times(1)).convert();
        }

        @Test
        void testConvertArchi_conversionExceptionIsPropagated() throws Exception {
            Boolean removeInvalid = true;
            ConversionException ce = new ConversionException("Chyba při konverzi!");

            // Konfigurace, že convert() vyhodí ConversionException
            doNothing().when(archiConverter).setRemoveELI(removeInvalid);
            doThrow(ce).when(archiConverter).convert();

            // Očekává se, že propadne stejná ConversionException
            ConversionException thrown = assertThrows(
                    ConversionException.class, () -> converterEngine.convertArchi(removeInvalid));

            assertEquals("Chyba při konverzi!", thrown.getMessage());
            verify(archiConverter, times(1)).setRemoveELI(removeInvalid);
            verify(archiConverter, times(1)).convert();
        }

        @Test
        void testConvertArchi_genericExceptionIsWrapped() throws Exception {
            Boolean removeInvalid = false;
            RuntimeException re = new RuntimeException("Něco se pokazilo!");

            // Konfigurace, že convert() vyhodí obecnou RuntimeException
            doNothing().when(archiConverter).setRemoveELI(removeInvalid);
            doThrow(re).when(archiConverter).convert();

            // Očekává se, že bude vyhozena ConversionException s příslušným textem
            ConversionException thrown = assertThrows(
                    ConversionException.class, () -> converterEngine.convertArchi(removeInvalid));

            assertEquals("Během konverze Archi souboru došlo k nečekané chybě.", thrown.getMessage());
            assertNull(thrown.getCause());

            verify(archiConverter, times(1)).setRemoveELI(removeInvalid);
            verify(archiConverter, times(1)).convert();
        }
    }

        //---- exportToJson -------------------------------------------------------
        @Nested
        class ExportToJsonTests{

            @Test
            void testExportToJson_successful() throws Exception {
                String jsonResult = "{\"key\": \"value\"}";

                // ArchiConverter.exportToJson() vrátí připravený řetězec JSON
                when(archiConverter.exportToJson()).thenReturn(jsonResult);

                // Volání testované metody
                String returned = converterEngine.exportToJson();

                // Ověření, že vrácená hodnota odpovídá vrácené hodnotě z ArchiConverter
                assertEquals(jsonResult, returned);
                verify(archiConverter, times(1)).exportToJson();
            }

            @Test
            void testExportToJson_jsonExportExceptionIsPropagated() throws Exception {
                JsonExportException jee = new JsonExportException("Chyba při exportu do JSON");
                when(archiConverter.exportToJson()).thenThrow(jee);

                // Očekavá se, že propadne stejná JsonExportException
                JsonExportException thrown = assertThrows(
                        JsonExportException.class, () -> converterEngine.exportToJson());

                assertEquals("Chyba při exportu do JSON", thrown.getMessage());
                verify(archiConverter, times(1)).exportToJson();
            }

            @Test
            void testExportToJson_genericExceptionIsWrapped() throws Exception {
                RuntimeException re = new RuntimeException("Neočekávaná chyba");

                when(archiConverter.exportToJson()).thenThrow(re);

                // Očekává se, že se obalí do JsonExportException s textem z ConverterEngine
                JsonExportException thrown = assertThrows(
                        JsonExportException.class, () -> converterEngine.exportToJson());

                assertEquals("Během exportu do JSON došlo k nečekané chybě", thrown.getMessage());
                verify(archiConverter, times(1)).exportToJson();
            }
        }

        @Nested
        class ExportToTurtleTests{

            @Test
            void testExportToTurtle_successful() throws Exception {
                String turtleResult = "@prefix ex: <http://example.org/ .>";

                // ArchiConverter.exportToTurtle() vrátí připravený řetězec Turtle
                when(archiConverter.exportToTurtle()).thenReturn(turtleResult);

                // Volání testováné metody
                String returned = converterEngine.exportToTurtle();

                // Ověření, že vrácený řetězec odpovídá tomu z ArchiConverter
                assertEquals(turtleResult, returned);
                verify(archiConverter, times(1)).exportToTurtle();
            }

            @Test
            void testExportToTurtle_turtleExportExceptionIsPropagated() throws Exception{
                TurtleExportException tee = new TurtleExportException("Chyba při exportu do Turtle");
                when(archiConverter.exportToTurtle()).thenThrow(tee);

                // Očekává se, že se propadne stejná TurtleExportException
                TurtleExportException thrown = assertThrows(
                        TurtleExportException.class, () -> converterEngine.exportToTurtle());

                assertEquals("Chyba při exportu do Turtle", thrown.getMessage());
                verify(archiConverter, times(1)).exportToTurtle();
            }

            @Test
            void testExportToTurtle_genericExceptionIsWrapped() throws Exception {
                RuntimeException re = new RuntimeException("Neočekávaná chyba");
                when(archiConverter.exportToTurtle()).thenThrow(re);

                // Očekává se, že bude vyhozena TurtleExportException se zprávou z ConverterEngine
                TurtleExportException thrown = assertThrows(
                        TurtleExportException.class, () -> converterEngine.exportToTurtle());

                assertEquals("Během exportu do Turtle došlo k nečekané chybě.", thrown.getMessage());
                verify(archiConverter, times(1)).exportToTurtle();
            }
    }
}
