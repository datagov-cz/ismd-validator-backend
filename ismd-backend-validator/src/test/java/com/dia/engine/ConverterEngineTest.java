package com.dia.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dia.converter.archi.ArchiConverter;
import com.dia.converter.excel.data.OntologyData;
import com.dia.converter.excel.reader.ExcelReader;
import com.dia.converter.excel.transformer.ExcelDataTransformer;
import com.dia.converter.excel.transformer.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConverterEngineTest {

    @Mock
    private ArchiConverter archiConverter;

    @Mock
    private ExcelReader excelReader;

    @Mock
    private ExcelDataTransformer excelDataTransformer;

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
    class ExportToJsonTests {

        @Test
        void testExportToJson_archiFormat_happyPath_logsAndVerify() throws JsonExportException {
            // Nastavení MDC a stub pro exportToJson()
            MDC.put(LOG_REQUEST_ID, "req-8");
            String result = "{\"k\":1}";
            when(archiConverter.exportToJson()).thenReturn(result);

            // Volání exportToJson() s ARCHI_XML formátem
            String out = converterEngine.exportToJson(FileFormat.ARCHI_XML);

            // Kontrola návratové hodnoty
            assertEquals(result, out);

            // Ověření logů startu a dokončení s outputSize a durationMs
            assertEquals(2, listAppender.list.size());
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting JSON export using registry: requestId=req-8, fileFormat=ARCHI_XML"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("JSON export completed using registry: requestId=req-8, fileFormat=ARCHI_XML, outputSize=" + result.length() + ", durationMs="));
        }

        @Test
        void testExportToJson_xlsxFormat_happyPath_logsAndVerify() throws JsonExportException {
            // Nastavení MDC a příprava Excel transformation result
            MDC.put(LOG_REQUEST_ID, "req-8b");
            String result = "{\"excel\":true}";
            TransformationResult mockResult = mock(TransformationResult.class);

            // Použití reflection pro nastavení excelTransformationResult
            setExcelTransformationResult(mockResult);

            when(excelDataTransformer.exportToJson(mockResult)).thenReturn(result);

            // Volání exportToJson() s XLSX formátem
            String out = converterEngine.exportToJson(FileFormat.XLSX);

            // Kontrola návratové hodnoty
            assertEquals(result, out);

            // Ověření logů
            assertEquals(2, listAppender.list.size());
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting JSON export using registry: requestId=req-8b, fileFormat=XLSX"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("JSON export completed using registry: requestId=req-8b, fileFormat=XLSX, outputSize=" + result.length()));
        }

        @Test
        void testExportToJson_xlsxFormat_noTransformationResult_throwsException() {
            // Nastavení MDC
            MDC.put(LOG_REQUEST_ID, "req-8c");

            // Volání exportToJson() s XLSX formátem bez transformation result
            JsonExportException thrown = assertThrows(JsonExportException.class,
                    () -> converterEngine.exportToJson(FileFormat.XLSX));

            // Ověření textu výjimky
            assertEquals("Excel transformation result is not available.", thrown.getMessage());
        }

        @Test
        void testExportToJson_unsupportedFormat_throwsException() {
            // Nastavení MDC
            MDC.put(LOG_REQUEST_ID, "req-8d");

            // Simulace nepodporovaného formátu
            FileFormat unsupportedFormat = null; // nebo jiný nepodporovaný formát

            // Volání exportToJson() s nepodporovaným formátem
            assertThrows(JsonExportException.class,
                    () -> converterEngine.exportToJson(unsupportedFormat));
        }

        @Test
        void testExportToJson_jsonExportException_propagated() throws JsonExportException {
            // Nastavení MDC a stub pro exportToJson() vyhazující JsonExportException
            MDC.put(LOG_REQUEST_ID, "req-9");
            JsonExportException je = new JsonExportException("chyba JSON");
            when(archiConverter.exportToJson()).thenThrow(je);

            // Volání exportToJson() a zachycení JsonExportException
            JsonExportException thrown = assertThrows(
                    JsonExportException.class, () -> converterEngine.exportToJson(FileFormat.ARCHI_XML));

            // Ověření textu výjimky
            assertEquals("chyba JSON", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to export to JSON using registry: requestId=req-9, fileFormat=ARCHI_XML, error=chyba JSON"));
        }

        @Test
        void testExportToJson_unexpectedException_wrapped() throws JsonExportException {
            // Nastavení MDC a stub pro exportToJson() vyhazující RuntimeException
            MDC.put(LOG_REQUEST_ID, "req-10");
            RuntimeException re = new RuntimeException("nečekaná chyba");
            when(archiConverter.exportToJson()).thenThrow(re);

            // Volání exportToJson() a zachycení JsonExportException
            JsonExportException thrown = assertThrows(
                    JsonExportException.class, () -> converterEngine.exportToJson(FileFormat.ARCHI_XML));

            // Ověření obalené výjimky a jejího cause
            assertEquals("Během exportu do JSON došlo k nečekané chybě.", thrown.getMessage());
            assertSame(re, thrown.getCause());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Unexpected error during JSON export using registry: requestId=req-10, fileFormat=ARCHI_XML"));
        }
    }

    // ----- exportToTurtle -------------------------------------------------------
    @Nested
    class ExportToTurtleTests {

        @Test
        void testExportToTurtle_archiFormat_happyPath_logsAndVerify() throws TurtleExportException {
            // Nastavení MDC a stub pro exportToTurtle()
            MDC.put(LOG_REQUEST_ID, "req-11");
            String result = "@prefix";
            when(archiConverter.exportToTurtle()).thenReturn(result);

            // Volání exportToTurtle()
            String out = converterEngine.exportToTurtle(FileFormat.ARCHI_XML);

            // Kontrola návratové hodnoty
            assertEquals(result, out);

            // Ověření logů startu a dokončení s outputSize a durationMs
            assertEquals(2, listAppender.list.size());
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting Turtle export: requestId=req-11"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("Turtle export completed using registry: requestId=req-11, fileFormat=ARCHI_XML, outputSize=" + result.length() + ", durationMs="));
        }

        @Test
        void testExportToTurtle_xlsxFormat_happyPath_logsAndVerify() throws TurtleExportException {
            // Nastavení MDC a příprava Excel transformation result
            MDC.put(LOG_REQUEST_ID, "req-11b");
            String result = "@prefix excel: <http://example.com/excel#>";
            TransformationResult mockResult = mock(TransformationResult.class);

            // Použití reflection pro nastavení excelTransformationResult
            setExcelTransformationResult(mockResult);

            when(excelDataTransformer.exportToTurtle(mockResult)).thenReturn(result);

            // Volání exportToTurtle() s XLSX formátem
            String out = converterEngine.exportToTurtle(FileFormat.XLSX);

            // Kontrola návratové hodnoty
            assertEquals(result, out);

            // Ověření logů
            assertEquals(2, listAppender.list.size());
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting Turtle export: requestId=req-11b"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("Turtle export completed using registry: requestId=req-11b, fileFormat=XLSX, outputSize=" + result.length()));
        }

        @Test
        void testExportToTurtle_xlsxFormat_noTransformationResult_throwsException() {
            // Nastavení MDC
            MDC.put(LOG_REQUEST_ID, "req-11c");

            // Volání exportToTurtle() s XLSX formátem bez transformation result
            TurtleExportException thrown = assertThrows(TurtleExportException.class,
                    () -> converterEngine.exportToTurtle(FileFormat.XLSX));

            // Ověření textu výjimky
            assertEquals("Excel transformation result is not available.", thrown.getMessage());
        }

        @Test
        void testExportToTurtle_unsupportedFormat_throwsException() {
            // Nastavení MDC
            MDC.put(LOG_REQUEST_ID, "req-11d");

            // Simulace nepodporovaného formátu
            FileFormat unsupportedFormat = null; // nebo jiný nepodporovaný formát

            // Volání exportToTurtle() s nepodporovaným formátem
            assertThrows(TurtleExportException.class,
                    () -> converterEngine.exportToTurtle(unsupportedFormat));
        }

        @Test
        void testExportToTurtle_turtleExportException_propagated() throws TurtleExportException {
            // Nastavení MDC a stub pro exportToTurtle() vyhazující TurtleExportException
            MDC.put(LOG_REQUEST_ID, "req-12");
            TurtleExportException te = new TurtleExportException("chyba Turtle");
            when(archiConverter.exportToTurtle()).thenThrow(te);

            // Volání exportToTurtle() и zachycení TurtleExportException
            TurtleExportException thrown = assertThrows(
                    TurtleExportException.class, () -> converterEngine.exportToTurtle(FileFormat.ARCHI_XML));

            // Ověření textu výjimky
            assertEquals("chyba Turtle", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to export to Turtle using registry: requestId=req-12, fileFormat=ARCHI_XML, error=chyba Turtle"));
        }

        @Test
        void testExportToTurtle_unexpectedException_wrapped() throws TurtleExportException {
            // Nastavení MDC a stub pro exportToTurtle() vyhazující RuntimeException
            MDC.put(LOG_REQUEST_ID, "req-13");
            RuntimeException re = new RuntimeException("nečekaná chyba");
            when(archiConverter.exportToTurtle()).thenThrow(re);

            // Volání exportToTurtle() и zachycení JsonExportException (bug in original - should be TurtleExportException)
            TurtleExportException thrown = assertThrows(
                    TurtleExportException.class, () -> converterEngine.exportToTurtle(FileFormat.ARCHI_XML));

            // Ověření obаlené výjimкy и jejího cause
            assertEquals("Během exportu do Turtle došlo k nečekané chybě.", thrown.getMessage());

            // Kontrola ERROR logu neočekávané chyby
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Unexpected error during Turtle export using registry: requestId=req-13, fileFormat=ARCHI_XML"));
        }
    }

    // ----- parseExcelFromFile -------------------------------------------------------
    @Nested
    class ParseExcelFromFileTests {

        @Test
        void testParseExcelFromFile_happyPath_logsAndVerify() throws Exception {
            // Nastavení MDC a mock pro MultipartFile
            MDC.put(LOG_REQUEST_ID, "req-14");
            MultipartFile mockFile = mock(MultipartFile.class);
            InputStream mockInputStream = mock(InputStream.class);
            OntologyData mockOntologyData = mock(OntologyData.class);

            when(mockFile.getInputStream()).thenReturn(mockInputStream);
            when(excelReader.readOntologyFromExcel(mockInputStream)).thenReturn(mockOntologyData);

            // Volání parseExcelFromFile()
            converterEngine.parseExcelFromFile(mockFile);

            // Kontrola počtu logů (start + end)
            assertEquals(2, listAppender.list.size());

            // Ověření formátu zpráv
            assertTrue(listAppender.list.get(0).getFormattedMessage()
                    .contains("Starting Excel file parsing: requestId=req-14"));
            assertTrue(listAppender.list.get(1).getFormattedMessage()
                    .contains("Excel file parsing completed: requestId=req-14, durationMs="));

            // Ověření volání dependencies
            verify(mockFile).getInputStream();
            verify(excelReader).readOntologyFromExcel(mockInputStream);
        }

        @Test
        void testParseExcelFromFile_ioException_propagated() throws Exception {
            // Nastavení MDC a mock pro MultipartFile vyhazující IOException
            MDC.put(LOG_REQUEST_ID, "req-15");
            MultipartFile mockFile = mock(MultipartFile.class);
            IOException ioException = new IOException("IO chyba");

            when(mockFile.getInputStream()).thenThrow(ioException);

            // Volání parseExcelFromFile() a zachycení IOException
            IOException thrown = assertThrows(IOException.class,
                    () -> converterEngine.parseExcelFromFile(mockFile));

            // Ověření textu výjimky
            assertEquals("IO chyba", thrown.getMessage());

            // Kontrola ERROR logu
            assertEquals(2, listAppender.list.size());
            ILoggingEvent error = listAppender.list.get(1);
            assertTrue(error.getFormattedMessage()
                    .contains("Failed to parse Excel file: requestId=req-15, error=IO chyba"));
        }

        // ----- convertExcel -------------------------------------------------------
        @Nested
        class ConvertExcelTests {

            @Test
            void testConvertExcel_happyPath_logsAndVerify() {
                // Nastavení MDC a mock ontology data
                MDC.put(LOG_REQUEST_ID, "req-17");
                boolean removeInvalidSources = true;
                OntologyData mockOntologyData = mock(OntologyData.class);
                TransformationResult mockResult = mock(TransformationResult.class);

                // Nastavení mock ontology data
                setExcelOntologyData(mockOntologyData);

                doNothing().when(excelDataTransformer).setRemoveELI(removeInvalidSources);
                when(excelDataTransformer.transform(mockOntologyData)).thenReturn(mockResult);

                // Volání convertExcel()
                converterEngine.convertExcel(removeInvalidSources);

                // Kontrola počtu logů (start + flag + end)
                assertEquals(3, listAppender.list.size());

                // Ověření start logu
                assertTrue(listAppender.list.get(0).getFormattedMessage()
                        .contains("Starting Excel model conversion: requestId=req-17"));

                // Ověření logu flag
                assertTrue(listAppender.list.get(1).getFormattedMessage()
                        .contains("Invalid sources removal requested: true, requestId=req-17"));

                // Ověření dokončení conversion
                assertTrue(listAppender.list.get(2).getFormattedMessage()
                        .contains("Excel model conversion completed: requestId=req-17, durationMs="));

                // Ověření volání dependencies
                verify(excelDataTransformer).setRemoveELI(removeInvalidSources);
                verify(excelDataTransformer).transform(mockOntologyData);
            }

            @Test
            void testConvertExcel_conversionException_propagated() {
                // Nastavení MDC a mock
                MDC.put(LOG_REQUEST_ID, "req-18");
                boolean removeInvalidSources = false;
                OntologyData mockOntologyData = mock(OntologyData.class);
                ConversionException conversionException = new ConversionException("Excel konverze chyba");

                setExcelOntologyData(mockOntologyData);

                doNothing().when(excelDataTransformer).setRemoveELI(removeInvalidSources);
                when(excelDataTransformer.transform(mockOntologyData)).thenThrow(conversionException);

                // Volání convertExcel() a zachycení ConversionException
                ConversionException thrown = assertThrows(ConversionException.class,
                        () -> converterEngine.convertExcel(removeInvalidSources));

                // Ověření textu výjimky
                assertEquals("Excel konverze chyba", thrown.getMessage());

                // Kontrola ERROR logu
                assertEquals(3, listAppender.list.size());
                ILoggingEvent error = listAppender.list.get(2);
                assertTrue(error.getFormattedMessage()
                        .contains("Failed to convert Excel model: requestId=req-18, error=Excel konverze chyba"));
            }

            @Test
            void testConvertExcel_unexpectedException_wrapped() {
                // Nastavení MDC a mock
                MDC.put(LOG_REQUEST_ID, "req-19");
                boolean removeInvalidSources = true;
                OntologyData mockOntologyData = mock(OntologyData.class);
                RuntimeException runtimeException = new RuntimeException("nečekaná chyba");

                setExcelOntologyData(mockOntologyData);

                doNothing().when(excelDataTransformer).setRemoveELI(removeInvalidSources);
                when(excelDataTransformer.transform(mockOntologyData)).thenThrow(runtimeException);

                // Volání convertExcel() a zachycení ConversionException
                ConversionException thrown = assertThrows(ConversionException.class,
                        () -> converterEngine.convertExcel(removeInvalidSources));

                // Ověření obalené výjimky
                assertEquals("Během konverze Excel souboru došlo k nečekané chybě.", thrown.getMessage());

                // Kontrola ERROR logu
                assertEquals(3, listAppender.list.size());
                ILoggingEvent error = listAppender.list.get(2);
                assertTrue(error.getFormattedMessage()
                        .contains("Unexpected error during Excel model conversion: requestId=req-19"));
            }
        }
    }

    // Helper methods for private fields setup using reflection
    private void setExcelTransformationResult(TransformationResult result) {
        try {
            var field = ConverterEngine.class.getDeclaredField("excelTransformationResult");
            field.setAccessible(true);
            field.set(converterEngine, result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set excelTransformationResult", e);
        }
    }

    private void setExcelOntologyData(OntologyData data) {
        try {
            var field = ConverterEngine.class.getDeclaredField("excelOntologyData");
            field.setAccessible(true);
            field.set(converterEngine, data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set excelOntologyData", e);
        }
    }
}