package com.dia.service.impl;

import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.engine.ConverterEngine;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
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
 * Test for {@link ConverterServiceImpl}.
 * Verifies that service methods properly delegate calls to ConverterEngine,
 * return results, and propagate exceptions.
 */
@ExtendWith(MockitoExtension.class)
class ConverterServiceImplTest {

    @Mock
    private ConverterEngine converterEngine;

    @InjectMocks
    private ConverterServiceImpl converterService;

    // ========== processArchiFile(String) ==========
    @Test
    void testProcessArchiFile_delegatesToEngineWithoutError() throws FileParsingException, ConversionException {
        // Valid content that the engine should recognize as correct
        String sampleContent = "<archi>valid content</archi>";
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processArchiFile(sampleContent)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(sampleContent);

        // Verify that engine received exactly the same content
        verify(converterEngine, times(1)).processArchiFile(sampleContent);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessArchiFile_propagatesFileParsingException() throws FileParsingException, ConversionException {
        // Invalid content that should cause the engine to throw FileParsingException
        String invalidContent = "Invalid content";

        // Set up mock: when calling processArchiFile(invalidContent) engine should throw FileParsingException
        doThrow(new FileParsingException("Parsing error")).when(converterEngine).processArchiFile(invalidContent);

        // Verify that service also throws FileParsingException
        assertThrows(FileParsingException.class, () -> converterService.processArchiFile(invalidContent),
                "Service should throw FileParsingException for invalid input");
    }

    @Test
    void testProcessArchiFile_propagatesConversionException() throws FileParsingException, ConversionException {
        String content = "<archi>content</archi>";

        doThrow(new ConversionException("Conversion error")).when(converterEngine).processArchiFile(content);

        assertThrows(ConversionException.class, () -> converterService.processArchiFile(content),
                "Service should propagate ConversionException from engine");
    }

    @Test
    void testProcessArchiFile_withNullInput() throws FileParsingException, ConversionException {
        // Test behavior with null input
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processArchiFile(null)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(null);

        verify(converterEngine, times(1)).processArchiFile(null);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testProcessArchiFile_withEmptyString() throws FileParsingException, ConversionException {
        // Test behavior with empty string
        String emptyContent = "";
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processArchiFile(emptyContent)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(emptyContent);

        verify(converterEngine, times(1)).processArchiFile(emptyContent);
        assertEquals(expectedResult, actualResult);
    }

    // ========== processExcelFile(MultipartFile) ==========

    @Test
    void testProcessExcelFile_delegatesToEngineWithoutError() throws ExcelReadingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processExcelFile(mockFile)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processExcelFile(mockFile);

        // Verify that engine received exactly the same file
        verify(converterEngine, times(1)).processExcelFile(mockFile);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessExcelFile_propagatesIOException() throws ExcelReadingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);

        // Set up mock: when calling processExcelFile engine should throw IOException
        doThrow(new IOException("IO error")).when(converterEngine).processExcelFile(mockFile);

        // Verify that service also throws IOException
        assertThrows(IOException.class, () -> converterService.processExcelFile(mockFile),
                "Service should throw IOException for IO errors");
    }

    @Test
    void testProcessExcelFile_propagatesExcelReadingException() throws ExcelReadingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);

        // Set up mock: when calling processExcelFile engine should throw ExcelReadingException
        doThrow(new ExcelReadingException("Excel reading error")).when(converterEngine).processExcelFile(mockFile);

        // Verify that service also throws ExcelReadingException
        assertThrows(ExcelReadingException.class, () -> converterService.processExcelFile(mockFile),
                "Service should throw ExcelReadingException for Excel reading errors");
    }

    @Test
    void testProcessExcelFile_propagatesConversionException() throws ExcelReadingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);

        doThrow(new ConversionException("Conversion error")).when(converterEngine).processExcelFile(mockFile);

        assertThrows(ConversionException.class, () -> converterService.processExcelFile(mockFile),
                "Service should propagate ConversionException from engine");
    }

    @Test
    void testProcessExcelFile_withNullFile() throws ExcelReadingException, IOException, ConversionException {
        // Test behavior with null file
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processExcelFile(null)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processExcelFile(null);

        verify(converterEngine, times(1)).processExcelFile(null);
        assertEquals(expectedResult, actualResult);
    }

    // ========== processEAFile(MultipartFile) ==========

    @Test
    void testProcessEAFile_delegatesToEngineWithoutError() throws FileParsingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processEAFile(mockFile)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processEAFile(mockFile);

        // Verify that engine received exactly the same file
        verify(converterEngine, times(1)).processEAFile(mockFile);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessEAFile_propagatesFileParsingException() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);

        doThrow(new FileParsingException("EA file parsing error")).when(converterEngine).processEAFile(mockFile);

        assertThrows(FileParsingException.class, () -> converterService.processEAFile(mockFile),
                "Service should throw FileParsingException for EA file parsing errors");
    }

    @Test
    void testProcessEAFile_propagatesIOException() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);

        doThrow(new IOException("IO error")).when(converterEngine).processEAFile(mockFile);

        assertThrows(IOException.class, () -> converterService.processEAFile(mockFile),
                "Service should throw IOException for IO errors");
    }

    @Test
    void testProcessEAFile_propagatesConversionException() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);

        doThrow(new ConversionException("Conversion error")).when(converterEngine).processEAFile(mockFile);

        assertThrows(ConversionException.class, () -> converterService.processEAFile(mockFile),
                "Service should propagate ConversionException from engine");
    }

    @Test
    void testProcessEAFile_withNullFile() throws FileParsingException, IOException, ConversionException {
        // Test behavior with null file
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processEAFile(null)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processEAFile(null);

        verify(converterEngine, times(1)).processEAFile(null);
        assertEquals(expectedResult, actualResult);
    }

    // ========== processSSPOntology(String) ==========

    @Test
    void testProcessSSPOntology_delegatesToEngineWithoutError() throws ConversionException {
        // Valid IRI that the engine should process correctly
        String sampleIri = "http://example.org/ontology";
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processSSPOntology(sampleIri)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processSSPOntology(sampleIri);

        // Verify that engine received exactly the same IRI
        verify(converterEngine, times(1)).processSSPOntology(sampleIri);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessSSPOntology_propagatesConversionException() throws ConversionException {
        String invalidIri = "invalid://iri";

        doThrow(new ConversionException("SSP conversion error")).when(converterEngine).processSSPOntology(invalidIri);

        assertThrows(ConversionException.class, () -> converterService.processSSPOntology(invalidIri),
                "Service should propagate ConversionException from engine");
    }

    @Test
    void testProcessSSPOntology_withNullIri() throws ConversionException {
        // Test behavior with null IRI
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processSSPOntology(null)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processSSPOntology(null);

        verify(converterEngine, times(1)).processSSPOntology(null);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testProcessSSPOntology_withEmptyIri() throws ConversionException {
        // Test behavior with empty IRI
        String emptyIri = "";
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processSSPOntology(emptyIri)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processSSPOntology(emptyIri);

        verify(converterEngine, times(1)).processSSPOntology(emptyIri);
        assertEquals(expectedResult, actualResult);
    }

    // ========== exportToJson(FileFormat, TransformationResult) ==========

    @Test
    void testExportToJson_archiFormat_returnsEngineValue() throws JsonExportException {
        // Set up engine to return predefined JSON for ARCHI_XML format
        String expectedJson = "{\"status\":\"ok\"}";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToJson(FileFormat.ARCHI_XML, transformationResult)).thenReturn(expectedJson);

        // Call service with ARCHI_XML format
        String actualJson = converterService.exportToJson(FileFormat.ARCHI_XML, transformationResult);

        // Verify that returned JSON matches engine value and engine was called
        assertEquals(expectedJson, actualJson, "Returned JSON should match engine value");
        verify(converterEngine, times(1)).exportToJson(FileFormat.ARCHI_XML, transformationResult);
    }

    @Test
    void testExportToJson_xlsxFormat_returnsEngineValue() throws JsonExportException {
        // Set up engine to return predefined JSON for XLSX format
        String expectedJson = "{\"excel\":\"data\"}";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToJson(FileFormat.XLSX, transformationResult)).thenReturn(expectedJson);

        // Call service with XLSX format
        String actualJson = converterService.exportToJson(FileFormat.XLSX, transformationResult);

        // Verify that returned JSON matches engine value and engine was called
        assertEquals(expectedJson, actualJson, "Returned JSON should match engine value");
        verify(converterEngine, times(1)).exportToJson(FileFormat.XLSX, transformationResult);
    }

    @Test
    void testExportToJson_xmiFormat_returnsEngineValue() throws JsonExportException {
        // Set up engine to return predefined JSON for XMI format
        String expectedJson = "{\"xmi\":\"data\"}";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToJson(FileFormat.XMI, transformationResult)).thenReturn(expectedJson);

        // Call service with XMI format
        String actualJson = converterService.exportToJson(FileFormat.XMI, transformationResult);

        // Verify that returned JSON matches engine value and engine was called
        assertEquals(expectedJson, actualJson, "Returned JSON should match engine value");
        verify(converterEngine, times(1)).exportToJson(FileFormat.XMI, transformationResult);
    }

    @Test
    void testExportToJson_sspFormat_returnsEngineValue() throws JsonExportException {
        // Set up engine to return predefined JSON for SSP format
        String expectedJson = "{\"ssp\":\"data\"}";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToJson(FileFormat.SSP, transformationResult)).thenReturn(expectedJson);

        // Call service with SSP format
        String actualJson = converterService.exportToJson(FileFormat.SSP, transformationResult);

        // Verify that returned JSON matches engine value and engine was called
        assertEquals(expectedJson, actualJson, "Returned JSON should match engine value");
        verify(converterEngine, times(1)).exportToJson(FileFormat.SSP, transformationResult);
    }

    @Test
    void testExportToJson_propagatesJsonExportException() throws JsonExportException {
        // Set up engine to throw JsonExportException
        TransformationResult transformationResult = mock(TransformationResult.class);
        doThrow(new JsonExportException("JSON export error")).when(converterEngine).exportToJson(FileFormat.ARCHI_XML, transformationResult);

        // Verify that service throws JsonExportException
        assertThrows(JsonExportException.class, () -> converterService.exportToJson(FileFormat.ARCHI_XML, transformationResult),
                "Service should propagate JsonExportException for JSON export errors");
    }

    @Test
    void testExportToJson_withNullReturn() throws JsonExportException {
        // Test when engine returns null
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToJson(FileFormat.ARCHI_XML, transformationResult)).thenReturn(null);

        String result = converterService.exportToJson(FileFormat.ARCHI_XML, transformationResult);

        assertNull(result, "Service should return null when engine returns null");
        verify(converterEngine, times(1)).exportToJson(FileFormat.ARCHI_XML, transformationResult);
    }

    @Test
    void testExportToJson_withNullTransformationResult() throws JsonExportException {
        // Test with null transformation result
        String expectedJson = "{\"status\":\"ok\"}";
        when(converterEngine.exportToJson(FileFormat.ARCHI_XML, null)).thenReturn(expectedJson);

        String actualJson = converterService.exportToJson(FileFormat.ARCHI_XML, null);

        assertEquals(expectedJson, actualJson);
        verify(converterEngine, times(1)).exportToJson(FileFormat.ARCHI_XML, null);
    }

    // ========== exportToTurtle(FileFormat, TransformationResult) ==========

    @Test
    void testExportToTurtle_archiFormat_returnsEngineValue() throws TurtleExportException {
        // Set up engine to return predefined Turtle for ARCHI_XML format
        String expectedTurtle = "@prefix : <http://example.org/>";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToTurtle(FileFormat.ARCHI_XML, transformationResult)).thenReturn(expectedTurtle);

        // Call service with ARCHI_XML format
        String actualTurtle = converterService.exportToTurtle(FileFormat.ARCHI_XML, transformationResult);

        // Verify that returned Turtle matches engine value and engine was called
        assertEquals(expectedTurtle, actualTurtle, "Returned Turtle should match engine value");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.ARCHI_XML, transformationResult);
    }

    @Test
    void testExportToTurtle_xlsxFormat_returnsEngineValue() throws TurtleExportException {
        // Set up engine to return predefined Turtle for XLSX format
        String expectedTurtle = "@prefix excel: <http://example.org/excel#>";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToTurtle(FileFormat.XLSX, transformationResult)).thenReturn(expectedTurtle);

        // Call service with XLSX format
        String actualTurtle = converterService.exportToTurtle(FileFormat.XLSX, transformationResult);

        // Verify that returned Turtle matches engine value and engine was called
        assertEquals(expectedTurtle, actualTurtle, "Returned Turtle should match engine value");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.XLSX, transformationResult);
    }

    @Test
    void testExportToTurtle_xmiFormat_returnsEngineValue() throws TurtleExportException {
        // Set up engine to return predefined Turtle for XMI format
        String expectedTurtle = "@prefix xmi: <http://example.org/xmi#>";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToTurtle(FileFormat.XMI, transformationResult)).thenReturn(expectedTurtle);

        // Call service with XMI format
        String actualTurtle = converterService.exportToTurtle(FileFormat.XMI, transformationResult);

        // Verify that returned Turtle matches engine value and engine was called
        assertEquals(expectedTurtle, actualTurtle, "Returned Turtle should match engine value");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.XMI, transformationResult);
    }

    @Test
    void testExportToTurtle_sspFormat_returnsEngineValue() throws TurtleExportException {
        // Set up engine to return predefined Turtle for SSP format
        String expectedTurtle = "@prefix ssp: <http://example.org/ssp#>";
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToTurtle(FileFormat.SSP, transformationResult)).thenReturn(expectedTurtle);

        // Call service with SSP format
        String actualTurtle = converterService.exportToTurtle(FileFormat.SSP, transformationResult);

        // Verify that returned Turtle matches engine value and engine was called
        assertEquals(expectedTurtle, actualTurtle, "Returned Turtle should match engine value");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.SSP, transformationResult);
    }

    @Test
    void testExportToTurtle_propagatesTurtleExportException() throws TurtleExportException {
        // Set up engine to throw TurtleExportException
        TransformationResult transformationResult = mock(TransformationResult.class);
        doThrow(new TurtleExportException("Turtle export error")).when(converterEngine).exportToTurtle(FileFormat.ARCHI_XML, transformationResult);

        // Verify that service throws TurtleExportException
        assertThrows(TurtleExportException.class, () -> converterService.exportToTurtle(FileFormat.ARCHI_XML, transformationResult),
                "Service should propagate TurtleExportException for Turtle export errors");
    }

    @Test
    void testExportToTurtle_withEmptyReturn() throws TurtleExportException {
        // Test when engine returns empty string
        TransformationResult transformationResult = mock(TransformationResult.class);
        when(converterEngine.exportToTurtle(FileFormat.ARCHI_XML, transformationResult)).thenReturn("");

        String result = converterService.exportToTurtle(FileFormat.ARCHI_XML, transformationResult);

        assertEquals("", result, "Service should return empty string when engine returns empty string");
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.ARCHI_XML, transformationResult);
    }

    @Test
    void testExportToTurtle_withNullTransformationResult() throws TurtleExportException {
        // Test with null transformation result
        String expectedTurtle = "@prefix : <http://example.org/>";
        when(converterEngine.exportToTurtle(FileFormat.ARCHI_XML, null)).thenReturn(expectedTurtle);

        String actualTurtle = converterService.exportToTurtle(FileFormat.ARCHI_XML, null);

        assertEquals(expectedTurtle, actualTurtle);
        verify(converterEngine, times(1)).exportToTurtle(FileFormat.ARCHI_XML, null);
    }

    // ========== Edge cases and integration scenarios ==========

    @Test
    void testExportToJson_runtimeException_propagated() throws JsonExportException {
        // Set up engine to throw RuntimeException
        TransformationResult transformationResult = mock(TransformationResult.class);
        doThrow(new RuntimeException("Unexpected error")).when(converterEngine).exportToJson(FileFormat.ARCHI_XML, transformationResult);

        // Verify that service propagates RuntimeException
        assertThrows(RuntimeException.class, () -> converterService.exportToJson(FileFormat.ARCHI_XML, transformationResult),
                "Service should propagate RuntimeException for unexpected errors");
    }

    @Test
    void testExportToTurtle_runtimeException_propagated() throws TurtleExportException {
        // Set up engine to throw RuntimeException
        TransformationResult transformationResult = mock(TransformationResult.class);
        doThrow(new RuntimeException("Unexpected error")).when(converterEngine).exportToTurtle(FileFormat.ARCHI_XML, transformationResult);

        // Verify that service propagates RuntimeException
        assertThrows(RuntimeException.class, () -> converterService.exportToTurtle(FileFormat.ARCHI_XML, transformationResult),
                "Service should propagate RuntimeException for unexpected errors");
    }

    @Test
    void testProcessSSPOntology_runtimeException_propagated() throws ConversionException {
        // Set up engine to throw RuntimeException
        String iri = "http://example.org/ontology";
        doThrow(new RuntimeException("Unexpected SSP error")).when(converterEngine).processSSPOntology(iri);

        // Verify that service propagates RuntimeException
        assertThrows(RuntimeException.class, () -> converterService.processSSPOntology(iri),
                "Service should propagate RuntimeException for unexpected SSP errors");
    }

    // ========== Integration workflow test ==========

    @Test
    void testFullWorkflow_processAndExport() throws Exception {
        // Test a complete workflow: process file -> export to JSON -> export to Turtle
        String content = "<archi>test content</archi>";
        ConversionResult mockConversionResult = mock(ConversionResult.class);
        TransformationResult mockTransformationResult = mock(TransformationResult.class);
        String jsonResult = "{\"test\":\"data\"}";
        String turtleResult = "@prefix test: <http://test.org/>";

        // Setup mocks
        when(converterEngine.processArchiFile(content)).thenReturn(mockConversionResult);
        when(mockConversionResult.getTransformationResult()).thenReturn(mockTransformationResult);
        when(converterEngine.exportToJson(FileFormat.ARCHI_XML, mockTransformationResult)).thenReturn(jsonResult);
        when(converterEngine.exportToTurtle(FileFormat.ARCHI_XML, mockTransformationResult)).thenReturn(turtleResult);

        // Execute full workflow through service
        ConversionResult conversionResult = converterService.processArchiFile(content);
        String json = converterService.exportToJson(FileFormat.ARCHI_XML, conversionResult.getTransformationResult());
        String turtle = converterService.exportToTurtle(FileFormat.ARCHI_XML, conversionResult.getTransformationResult());

        // Verify results
        assertEquals(mockConversionResult, conversionResult);
        assertEquals(jsonResult, json);
        assertEquals(turtleResult, turtle);

        // Verify engine was called correctly
        verify(converterEngine).processArchiFile(content);
        verify(converterEngine).exportToJson(FileFormat.ARCHI_XML, mockTransformationResult);
        verify(converterEngine).exportToTurtle(FileFormat.ARCHI_XML, mockTransformationResult);
    }
}