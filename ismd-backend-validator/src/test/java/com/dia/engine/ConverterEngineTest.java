package com.dia.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.engine.ConverterEngine;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.reader.ssp.SSPReader;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConverterEngineTest {

    @Mock
    private ArchiReader archiReader;

    @Mock
    private EnterpriseArchitectReader eaReader;

    @Mock
    private ExcelReader excelReader;

    @Mock
    private SSPReader sspReader;

    @Mock
    private OFNDataTransformer ofnDataTransformer;

    @InjectMocks
    private ConverterEngine converterEngine;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger testLogger;

    @BeforeEach
    void setUp() {
        // Clear MDC and add ListAppender for log capture
        MDC.clear();
        testLogger = (Logger) LoggerFactory.getLogger(ConverterEngine.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        testLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        // Remove ListAppender and clear MDC
        testLogger.detachAppender(listAppender);
        MDC.clear();
    }

    // ========== processArchiFile ==========
    @Nested
    class ProcessArchiFileTests {

        @Test
        void testProcessArchiFile_happyPath_logsAndVerify() throws Exception {
            // Setup MDC and mock objects
            MDC.put(LOG_REQUEST_ID, "req-1");
            String content = "<xml>valid content</xml>";
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(archiReader.readArchiFromString(content)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);

            // Call processArchiFile()
            ConversionResult result = converterEngine.processArchiFile(content);

            // Verify result
            assertNotNull(result);
            assertEquals(mockOntologyData, result.getOntologyData());
            assertEquals(mockTransformationResult, result.getTransformationResult());

            // Check log count (processing start + parsing start + parsing end + conversion start + conversion end)
            assertTrue(listAppender.list.size() >= 5);

            // Verify log messages contain expected information
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Archi file processing: requestId=req-1")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Archi file parsing: requestId=req-1")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Archi file parsing completed: requestId=req-1, durationMs=")));

            // Verify service calls
            verify(archiReader, times(1)).readArchiFromString(content);
            verify(ofnDataTransformer, times(1)).transform(mockOntologyData);
        }

        @Test
        void testProcessArchiFile_fileParsingException_propagated() throws Exception {
            // Setup with parsing exception
            MDC.put(LOG_REQUEST_ID, "req-3");
            String content = "<xml>invalid</xml>";

            when(archiReader.readArchiFromString(content)).thenThrow(new RuntimeException("Parsing failed"));

            // Call processArchiFile() and expect FileParsingException
            FileParsingException thrown = assertThrows(FileParsingException.class,
                    () -> converterEngine.processArchiFile(content));

            // Verify exception message
            assertEquals("Během čtení souboru došlo k chybě.", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to parse Archi file: requestId=req-3")));
        }

        @Test
        void testProcessArchiFile_conversionException_propagated() throws Exception {
            // Setup with conversion exception
            MDC.put(LOG_REQUEST_ID, "req-4");
            String content = "<xml>content</xml>";
            OntologyData mockOntologyData = mock(OntologyData.class);
            ConversionException ce = new ConversionException("Conversion error");

            when(archiReader.readArchiFromString(content)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenThrow(ce);

            // Call processArchiFile() and expect ConversionException
            ConversionException thrown = assertThrows(ConversionException.class,
                    () -> converterEngine.processArchiFile(content));

            // Verify exception message
            assertEquals("Během konverze Archi souboru došlo k nečekané chybě.", thrown.getMessage());
        }

        @Test
        void testProcessArchiFile_withNullContent() throws Exception {
            // Test with null content
            MDC.put(LOG_REQUEST_ID, "req-5");
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(archiReader.readArchiFromString(null)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);

            ConversionResult result = converterEngine.processArchiFile(null);

            assertNotNull(result);
            verify(archiReader, times(1)).readArchiFromString(null);
        }
    }

    // ========== processExcelFile ==========
    @Nested
    class ProcessExcelFileTests {

        @Test
        void testProcessExcelFile_happyPath_logsAndVerify() throws Exception {
            // Setup MDC and mock objects
            MDC.put(LOG_REQUEST_ID, "req-6");
            MultipartFile mockFile = mock(MultipartFile.class);
            InputStream mockInputStream = mock(InputStream.class);
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(mockFile.getInputStream()).thenReturn(mockInputStream);
            when(excelReader.readOntologyFromExcel(mockInputStream)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);

            // Call processExcelFile()
            ConversionResult result = converterEngine.processExcelFile(mockFile);

            // Verify result
            assertNotNull(result);
            assertEquals(mockOntologyData, result.getOntologyData());
            assertEquals(mockTransformationResult, result.getTransformationResult());

            // Check logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Excel file processing: requestId=req-6")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Excel file parsing: requestId=req-6")));

            // Verify service calls
            verify(mockFile).getInputStream();
            verify(excelReader).readOntologyFromExcel(mockInputStream);
            verify(ofnDataTransformer).transform(mockOntologyData);
        }

        @Test
        void testProcessExcelFile_ioException_propagated() throws Exception {
            // Setup with IO exception
            MDC.put(LOG_REQUEST_ID, "req-7");
            MultipartFile mockFile = mock(MultipartFile.class);
            IOException ioException = new IOException("IO error");

            when(mockFile.getInputStream()).thenThrow(ioException);

            // Call processExcelFile() and expect IOException
            IOException thrown = assertThrows(IOException.class,
                    () -> converterEngine.processExcelFile(mockFile));

            // Verify exception message
            assertEquals("IO error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to parse Excel file: requestId=req-7, error=IO error")));
        }

        @Test
        void testProcessExcelFile_excelReadingException_wrapped() throws Exception {
            // Setup with reading exception
            MDC.put(LOG_REQUEST_ID, "req-8");
            MultipartFile mockFile = mock(MultipartFile.class);
            InputStream mockInputStream = mock(InputStream.class);
            RuntimeException runtimeException = new RuntimeException("Reading error");

            when(mockFile.getInputStream()).thenReturn(mockInputStream);
            when(excelReader.readOntologyFromExcel(mockInputStream)).thenThrow(runtimeException);

            // Call processExcelFile() and expect ExcelReadingException
            ExcelReadingException thrown = assertThrows(ExcelReadingException.class,
                    () -> converterEngine.processExcelFile(mockFile));

            // Verify wrapped exception
            assertEquals("Během čtení souboru došlo k nečekané chybě.", thrown.getMessage());
            assertSame(runtimeException, thrown.getCause());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Unexpected error during Excel file parsing: requestId=req-8")));
        }

        @Test
        void testProcessExcelFile_conversionException_propagated() throws Exception {
            // Setup with conversion exception
            MDC.put(LOG_REQUEST_ID, "req-9");
            MultipartFile mockFile = mock(MultipartFile.class);
            InputStream mockInputStream = mock(InputStream.class);
            OntologyData mockOntologyData = mock(OntologyData.class);
            ConversionException conversionException = new ConversionException("Excel conversion error");

            when(mockFile.getInputStream()).thenReturn(mockInputStream);
            when(excelReader.readOntologyFromExcel(mockInputStream)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenThrow(conversionException);

            // Call processExcelFile() and expect ConversionException
            ConversionException thrown = assertThrows(ConversionException.class,
                    () -> converterEngine.processExcelFile(mockFile));

            // Verify exception message
            assertEquals("Excel conversion error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to convert Excel model: requestId=req-9, error=Excel conversion error")));
        }
    }

    // ========== processEAFile ==========
    @Nested
    class ProcessEAFileTests {

        @Test
        void testProcessEAFile_happyPath_logsAndVerify() throws Exception {
            // Setup MDC and mock objects
            MDC.put(LOG_REQUEST_ID, "req-10");
            MultipartFile mockFile = mock(MultipartFile.class);
            byte[] mockBytes = "xmi content".getBytes();
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(mockFile.getBytes()).thenReturn(mockBytes);
            when(eaReader.readXmiFromBytes(mockBytes)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);

            // Call processEAFile()
            ConversionResult result = converterEngine.processEAFile(mockFile);

            // Verify result
            assertNotNull(result);
            assertEquals(mockOntologyData, result.getOntologyData());
            assertEquals(mockTransformationResult, result.getTransformationResult());

            // Check logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting EA file processing: requestId=req-10")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting EA file parsing: requestId=req-10")));

            // Verify service calls
            verify(mockFile).getBytes();
            verify(eaReader).readXmiFromBytes(mockBytes);
            verify(ofnDataTransformer).transform(mockOntologyData);
        }

        @Test
        void testProcessEAFile_ioException_propagated() throws Exception {
            // Setup with IO exception
            MDC.put(LOG_REQUEST_ID, "req-11");
            MultipartFile mockFile = mock(MultipartFile.class);
            IOException ioException = new IOException("EA IO error");

            when(mockFile.getBytes()).thenThrow(ioException);

            // Call processEAFile() and expect IOException
            IOException thrown = assertThrows(IOException.class,
                    () -> converterEngine.processEAFile(mockFile));

            // Verify exception message
            assertEquals("EA IO error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to parse EA file: requestId=req-11")));
        }

        @Test
        void testProcessEAFile_fileParsingException_wrapped() throws Exception {
            // Setup with parsing exception
            MDC.put(LOG_REQUEST_ID, "req-12");
            MultipartFile mockFile = mock(MultipartFile.class);
            byte[] mockBytes = "invalid xmi".getBytes();
            RuntimeException runtimeException = new RuntimeException("EA parsing error");

            when(mockFile.getBytes()).thenReturn(mockBytes);
            when(eaReader.readXmiFromBytes(mockBytes)).thenThrow(runtimeException);

            // Call processEAFile() and expect FileParsingException
            FileParsingException thrown = assertThrows(FileParsingException.class,
                    () -> converterEngine.processEAFile(mockFile));

            // Verify wrapped exception
            assertEquals("Během čtení souboru došlo k chybě.", thrown.getMessage());
            assertSame(runtimeException, thrown.getCause());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to parse EA file: requestId=req-12")));
        }

        @Test
        void testProcessEAFile_conversionException_propagated() throws Exception {
            // Setup with conversion exception
            MDC.put(LOG_REQUEST_ID, "req-13");
            MultipartFile mockFile = mock(MultipartFile.class);
            byte[] mockBytes = "xmi content".getBytes();
            OntologyData mockOntologyData = mock(OntologyData.class);
            ConversionException conversionException = new ConversionException("EA conversion error");

            when(mockFile.getBytes()).thenReturn(mockBytes);
            when(eaReader.readXmiFromBytes(mockBytes)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenThrow(conversionException);

            // Call processEAFile() and expect ConversionException
            ConversionException thrown = assertThrows(ConversionException.class,
                    () -> converterEngine.processEAFile(mockFile));

            // Verify exception message
            assertEquals("EA conversion error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to convert EA model: requestId=req-13, error=EA conversion error")));
        }
    }

    // ========== processSSPOntology ==========
    @Nested
    class ProcessSSPOntologyTests {

        @Test
        void testProcessSSPOntology_happyPath_logsAndVerify() throws Exception {
            // Setup MDC and mock objects
            MDC.put(LOG_REQUEST_ID, "req-30");
            String iri = "http://example.org/ontology";
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(sspReader.readOntology(iri)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);

            // Call processSSPOntology()
            ConversionResult result = converterEngine.processSSPOntology(iri);

            // Verify result
            assertNotNull(result);
            assertEquals(mockOntologyData, result.getOntologyData());
            assertEquals(mockTransformationResult, result.getTransformationResult());

            // Check logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting SSP ontology processing: requestId=req-30")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting SSP ontology parsing: requestId=req-30")));

            // Verify service calls
            verify(sspReader).readOntology(iri);
            verify(ofnDataTransformer).transform(mockOntologyData);
        }

        @Test
        void testProcessSSPOntology_conversionException_propagated() throws Exception {
            // Setup with parsing exception
            MDC.put(LOG_REQUEST_ID, "req-31");
            String iri = "http://example.org/invalid";
            RuntimeException runtimeException = new RuntimeException("SSP parsing error");

            when(sspReader.readOntology(iri)).thenThrow(runtimeException);

            // Call processSSPOntology() and expect ConversionException
            ConversionException thrown = assertThrows(ConversionException.class,
                    () -> converterEngine.processSSPOntology(iri));

            // Verify wrapped exception
            assertEquals("Během čtení slovníku došlo k chybě.", thrown.getMessage());
            assertSame(runtimeException, thrown.getCause());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to parse SSP ontology: requestId=req-31")));
        }

        @Test
        void testProcessSSPOntology_conversionTransformException_propagated() throws Exception {
            // Setup with conversion exception
            MDC.put(LOG_REQUEST_ID, "req-32");
            String iri = "http://example.org/ontology";
            OntologyData mockOntologyData = mock(OntologyData.class);
            ConversionException conversionException = new ConversionException("SSP conversion error");

            when(sspReader.readOntology(iri)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenThrow(conversionException);

            // Call processSSPOntology() and expect ConversionException
            ConversionException thrown = assertThrows(ConversionException.class,
                    () -> converterEngine.processSSPOntology(iri));

            // Verify exception message
            assertEquals("SSP conversion error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to convert SSP model: requestId=req-32, error=SSP conversion error")));
        }
    }

    // ========== exportToJson ==========
    @Nested
    class ExportToJsonTests {

        @Test
        void testExportToJson_archiFormat_happyPath_logsAndVerify() throws JsonExportException {
            // Setup MDC and mock objects
            MDC.put(LOG_REQUEST_ID, "req-14");
            String expectedResult = "{\"status\":\"ok\"}";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToJson() with ARCHI_XML format
            String result = converterEngine.exportToJson(FileFormat.ARCHI_XML, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting JSON export using registry: requestId=req-14, fileFormat=ARCHI_XML")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("JSON export completed using registry: requestId=req-14, fileFormat=ARCHI_XML, outputSize=" + expectedResult.length() + ", durationMs=")));

            // Verify service call
            verify(ofnDataTransformer).exportToJson(mockTransformationResult);
        }

        @Test
        void testExportToJson_xlsxFormat_happyPath_logsAndVerify() throws JsonExportException {
            // Setup for XLSX format
            MDC.put(LOG_REQUEST_ID, "req-15");
            String expectedResult = "{\"excel\":\"data\"}";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToJson() with XLSX format
            String result = converterEngine.exportToJson(FileFormat.XLSX, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting JSON export using registry: requestId=req-15, fileFormat=XLSX")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("JSON export completed using registry: requestId=req-15, fileFormat=XLSX, outputSize=" + expectedResult.length())));

            // Verify service call
            verify(ofnDataTransformer).exportToJson(mockTransformationResult);
        }

        @Test
        void testExportToJson_xmiFormat_happyPath_logsAndVerify() throws JsonExportException {
            // Setup for XMI format
            MDC.put(LOG_REQUEST_ID, "req-16");
            String expectedResult = "{\"xmi\":\"data\"}";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToJson() with XMI format
            String result = converterEngine.exportToJson(FileFormat.XMI, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting JSON export using registry: requestId=req-16, fileFormat=XMI")));

            // Verify service call
            verify(ofnDataTransformer).exportToJson(mockTransformationResult);
        }

        @Test
        void testExportToJson_sspFormat_happyPath_logsAndVerify() throws JsonExportException {
            // Setup for SSP format
            MDC.put(LOG_REQUEST_ID, "req-33");
            String expectedResult = "{\"ssp\":\"data\"}";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToJson() with SSP format
            String result = converterEngine.exportToJson(FileFormat.SSP, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting JSON export using registry: requestId=req-33, fileFormat=SSP")));

            // Verify service call
            verify(ofnDataTransformer).exportToJson(mockTransformationResult);
        }

        @Test
        void testExportToJson_nullTransformationResult_throwsException() {
            // Setup with null transformation result
            MDC.put(LOG_REQUEST_ID, "req-17");

            // Call exportToJson() with null transformation result
            JsonExportException thrown = assertThrows(JsonExportException.class,
                    () -> converterEngine.exportToJson(FileFormat.ARCHI_XML, null));

            // Verify exception message
            assertEquals("Archi transformation result is not available.", thrown.getMessage());
        }

        @Test
        void testExportToJson_unsupportedFormat_throwsException() {
            // Setup with unsupported format
            MDC.put(LOG_REQUEST_ID, "req-18");
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            // Call exportToJson() with unsupported format
            JsonExportException thrown = assertThrows(JsonExportException.class,
                    () -> converterEngine.exportToJson(FileFormat.TURTLE, mockTransformationResult));

            // Verify exception message
            assertEquals("Unsupported file format for JSON export: TURTLE", thrown.getMessage());
        }

        @Test
        void testExportToJson_jsonExportException_propagated() throws JsonExportException {
            // Setup with export exception
            MDC.put(LOG_REQUEST_ID, "req-19");
            TransformationResult mockTransformationResult = mock(TransformationResult.class);
            JsonExportException je = new JsonExportException("JSON export error");

            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenThrow(je);

            // Call exportToJson() and expect JsonExportException
            JsonExportException thrown = assertThrows(JsonExportException.class,
                    () -> converterEngine.exportToJson(FileFormat.ARCHI_XML, mockTransformationResult));

            // Verify exception message
            assertEquals("JSON export error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to export to JSON using registry: requestId=req-19, fileFormat=ARCHI_XML, error=JSON export error")));
        }

        @Test
        void testExportToJson_unexpectedException_wrapped() throws JsonExportException {
            // Setup with unexpected exception
            MDC.put(LOG_REQUEST_ID, "req-20");
            TransformationResult mockTransformationResult = mock(TransformationResult.class);
            RuntimeException re = new RuntimeException("Unexpected error");

            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenThrow(re);

            // Call exportToJson() and expect JsonExportException
            JsonExportException thrown = assertThrows(JsonExportException.class,
                    () -> converterEngine.exportToJson(FileFormat.ARCHI_XML, mockTransformationResult));

            // Verify wrapped exception
            assertEquals("Během exportu do JSON došlo k nečekané chybě.", thrown.getMessage());
            assertSame(re, thrown.getCause());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Unexpected error during JSON export using registry: requestId=req-20, fileFormat=ARCHI_XML")));
        }
    }

    // ========== exportToTurtle ==========
    @Nested
    class ExportToTurtleTests {

        @Test
        void testExportToTurtle_archiFormat_happyPath_logsAndVerify() throws TurtleExportException {
            // Setup MDC and mock objects
            MDC.put(LOG_REQUEST_ID, "req-21");
            String expectedResult = "@prefix : <http://example.org/>";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToTurtle() with ARCHI_XML format
            String result = converterEngine.exportToTurtle(FileFormat.ARCHI_XML, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Turtle export: requestId=req-21")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Turtle export completed using registry: requestId=req-21, fileFormat=ARCHI_XML, outputSize=" + expectedResult.length() + ", durationMs=")));

            // Verify service call
            verify(ofnDataTransformer).exportToTurtle(mockTransformationResult);
        }

        @Test
        void testExportToTurtle_xlsxFormat_happyPath_logsAndVerify() throws TurtleExportException {
            // Setup for XLSX format
            MDC.put(LOG_REQUEST_ID, "req-22");
            String expectedResult = "@prefix excel: <http://example.org/excel#>";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToTurtle() with XLSX format
            String result = converterEngine.exportToTurtle(FileFormat.XLSX, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Turtle export: requestId=req-22")));
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Turtle export completed using registry: requestId=req-22, fileFormat=XLSX, outputSize=" + expectedResult.length())));

            // Verify service call
            verify(ofnDataTransformer).exportToTurtle(mockTransformationResult);
        }

        @Test
        void testExportToTurtle_sspFormat_happyPath_logsAndVerify() throws TurtleExportException {
            // Setup for SSP format
            MDC.put(LOG_REQUEST_ID, "req-34");
            String expectedResult = "@prefix ssp: <http://example.org/ssp#>";
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenReturn(expectedResult);

            // Call exportToTurtle() with SSP format
            String result = converterEngine.exportToTurtle(FileFormat.SSP, mockTransformationResult);

            // Verify result
            assertEquals(expectedResult, result);

            // Verify logs
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Starting Turtle export: requestId=req-34")));

            // Verify service call
            verify(ofnDataTransformer).exportToTurtle(mockTransformationResult);
        }

        @Test
        void testExportToTurtle_nullTransformationResult_throwsException() {
            // Setup with null transformation result
            MDC.put(LOG_REQUEST_ID, "req-23");

            // Call exportToTurtle() with null transformation result
            TurtleExportException thrown = assertThrows(TurtleExportException.class,
                    () -> converterEngine.exportToTurtle(FileFormat.ARCHI_XML, null));

            // Verify exception message
            assertEquals("Archi transformation result is not available.", thrown.getMessage());
        }

        @Test
        void testExportToTurtle_unsupportedFormat_throwsException() {
            // Setup with unsupported format
            MDC.put(LOG_REQUEST_ID, "req-24");
            TransformationResult mockTransformationResult = mock(TransformationResult.class);

            // Call exportToTurtle() with unsupported format
            TurtleExportException thrown = assertThrows(TurtleExportException.class,
                    () -> converterEngine.exportToTurtle(FileFormat.TURTLE, mockTransformationResult));

            // Verify exception message
            assertEquals("Unsupported file format for Turtle export: TURTLE", thrown.getMessage());
        }

        @Test
        void testExportToTurtle_turtleExportException_propagated() throws TurtleExportException {
            // Setup with export exception
            MDC.put(LOG_REQUEST_ID, "req-25");
            TransformationResult mockTransformationResult = mock(TransformationResult.class);
            TurtleExportException te = new TurtleExportException("Turtle export error");

            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenThrow(te);

            // Call exportToTurtle() and expect TurtleExportException
            TurtleExportException thrown = assertThrows(TurtleExportException.class,
                    () -> converterEngine.exportToTurtle(FileFormat.ARCHI_XML, mockTransformationResult));

            // Verify exception message
            assertEquals("Turtle export error", thrown.getMessage());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Failed to export to Turtle using registry: requestId=req-25, fileFormat=ARCHI_XML, error=Turtle export error")));
        }

        @Test
        void testExportToTurtle_unexpectedException_wrapped() throws TurtleExportException {
            // Setup with unexpected exception
            MDC.put(LOG_REQUEST_ID, "req-26");
            TransformationResult mockTransformationResult = mock(TransformationResult.class);
            RuntimeException re = new RuntimeException("Unexpected error");

            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenThrow(re);

            // Call exportToTurtle() and expect TurtleExportException
            TurtleExportException thrown = assertThrows(TurtleExportException.class,
                    () -> converterEngine.exportToTurtle(FileFormat.ARCHI_XML, mockTransformationResult));

            // Verify wrapped exception
            assertEquals("Během exportu do Turtle došlo k nečekané chybě.", thrown.getMessage());
            assertSame(re, thrown.getCause());

            // Check error log
            assertTrue(listAppender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("Unexpected error during Turtle export using registry: requestId=req-26, fileFormat=ARCHI_XML")));
        }
    }

    // ========== Integration and Edge Cases ==========
    @Nested
    class IntegrationAndEdgeCaseTests {

        @Test
        void testFullWorkflow_archiFile() throws Exception {
            // Test complete workflow: process -> export JSON -> export Turtle
            MDC.put(LOG_REQUEST_ID, "req-27");
            String content = "<xml>content</xml>";
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);
            String jsonResult = "{\"data\":\"json\"}";
            String turtleResult = "@prefix test: <http://test.org/>";

            // Setup mocks
            when(archiReader.readArchiFromString(content)).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);
            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenReturn(jsonResult);
            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenReturn(turtleResult);

            // Execute full workflow
            ConversionResult conversionResult = converterEngine.processArchiFile(content);
            String json = converterEngine.exportToJson(FileFormat.ARCHI_XML, conversionResult.getTransformationResult());
            String turtle = converterEngine.exportToTurtle(FileFormat.ARCHI_XML, conversionResult.getTransformationResult());

            // Verify results
            assertNotNull(conversionResult);
            assertEquals(jsonResult, json);
            assertEquals(turtleResult, turtle);

            // Verify all service calls
            verify(archiReader).readArchiFromString(content);
            verify(ofnDataTransformer).transform(mockOntologyData);
            verify(ofnDataTransformer).exportToJson(mockTransformationResult);
            verify(ofnDataTransformer).exportToTurtle(mockTransformationResult);
        }

        @Test
        void testAllFileFormats_processAndExport() throws Exception {
            // Test that all supported file formats work
            MDC.put(LOG_REQUEST_ID, "req-28");

            // Mock common objects
            OntologyData mockOntologyData = mock(OntologyData.class);
            TransformationResult mockTransformationResult = mock(TransformationResult.class);
            MultipartFile mockFile = mock(MultipartFile.class);
            InputStream mockInputStream = mock(InputStream.class);
            byte[] mockBytes = "content".getBytes();

            // Setup mocks for all file types
            when(archiReader.readArchiFromString(anyString())).thenReturn(mockOntologyData);
            when(mockFile.getInputStream()).thenReturn(mockInputStream);
            when(mockFile.getBytes()).thenReturn(mockBytes);
            when(excelReader.readOntologyFromExcel(mockInputStream)).thenReturn(mockOntologyData);
            when(eaReader.readXmiFromBytes(mockBytes)).thenReturn(mockOntologyData);
            when(sspReader.readOntology(anyString())).thenReturn(mockOntologyData);
            when(ofnDataTransformer.transform(mockOntologyData)).thenReturn(mockTransformationResult);
            when(ofnDataTransformer.exportToJson(mockTransformationResult)).thenReturn("{}");
            when(ofnDataTransformer.exportToTurtle(mockTransformationResult)).thenReturn("@prefix");

            // Test Archi
            ConversionResult archiResult = converterEngine.processArchiFile("content");
            String archiJson = converterEngine.exportToJson(FileFormat.ARCHI_XML, archiResult.getTransformationResult());
            String archiTurtle = converterEngine.exportToTurtle(FileFormat.ARCHI_XML, archiResult.getTransformationResult());

            // Test Excel
            ConversionResult excelResult = converterEngine.processExcelFile(mockFile);
            String excelJson = converterEngine.exportToJson(FileFormat.XLSX, excelResult.getTransformationResult());
            String excelTurtle = converterEngine.exportToTurtle(FileFormat.XLSX, excelResult.getTransformationResult());

            // Test EA
            ConversionResult eaResult = converterEngine.processEAFile(mockFile);
            String eaJson = converterEngine.exportToJson(FileFormat.XMI, eaResult.getTransformationResult());
            String eaTurtle = converterEngine.exportToTurtle(FileFormat.XMI, eaResult.getTransformationResult());

            // Test SSP
            ConversionResult sspResult = converterEngine.processSSPOntology("http://example.org/ontology");
            String sspJson = converterEngine.exportToJson(FileFormat.SSP, sspResult.getTransformationResult());
            String sspTurtle = converterEngine.exportToTurtle(FileFormat.SSP, sspResult.getTransformationResult());

            // Verify all results
            assertNotNull(archiResult);
            assertNotNull(excelResult);
            assertNotNull(eaResult);
            assertNotNull(sspResult);
            assertEquals("{}", archiJson);
            assertEquals("{}", excelJson);
            assertEquals("{}", eaJson);
            assertEquals("{}", sspJson);
            assertEquals("@prefix", archiTurtle);
            assertEquals("@prefix", excelTurtle);
            assertEquals("@prefix", eaTurtle);
            assertEquals("@prefix", sspTurtle);
        }
    }
}