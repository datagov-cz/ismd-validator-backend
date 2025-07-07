package com.dia.service.impl;

import com.dia.converter.data.ConversionResult;
import com.dia.converter.data.TransformationResult;
import com.dia.engine.ConverterEngine;
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

    // ========== processArchiFile(String, Boolean) ==========

    @Test
    void testProcessArchiFile_delegatesToEngineWithoutError() throws FileParsingException, ConversionException {
        // Valid content that the engine should recognize as correct
        String sampleContent = "<archi>valid content</archi>";
        Boolean removeInvalidSources = false;
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processArchiFile(sampleContent, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(sampleContent, removeInvalidSources);

        // Verify that engine received exactly the same content and parameter
        verify(converterEngine, times(1)).processArchiFile(sampleContent, removeInvalidSources);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessArchiFile_withRemoveInvalidSourcesTrue() throws FileParsingException, ConversionException {
        String sampleContent = "<archi>content</archi>";
        Boolean removeInvalidSources = true;
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processArchiFile(sampleContent, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(sampleContent, removeInvalidSources);

        verify(converterEngine, times(1)).processArchiFile(sampleContent, removeInvalidSources);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testProcessArchiFile_propagatesFileParsingException() throws FileParsingException, ConversionException {
        // Invalid content that should cause the engine to throw FileParsingException
        String invalidContent = "Invalid content";
        Boolean removeInvalidSources = false;

        // Set up mock: when calling processArchiFile(invalidContent) engine should throw FileParsingException
        doThrow(new FileParsingException("Parsing error")).when(converterEngine).processArchiFile(invalidContent, removeInvalidSources);

        // Verify that service also throws FileParsingException
        assertThrows(FileParsingException.class, () -> converterService.processArchiFile(invalidContent, removeInvalidSources),
                "Service should throw FileParsingException for invalid input");
    }

    @Test
    void testProcessArchiFile_propagatesConversionException() throws FileParsingException, ConversionException {
        String content = "<archi>content</archi>";
        Boolean removeInvalidSources = false;

        doThrow(new ConversionException("Conversion error")).when(converterEngine).processArchiFile(content, removeInvalidSources);

        assertThrows(ConversionException.class, () -> converterService.processArchiFile(content, removeInvalidSources),
                "Service should propagate ConversionException from engine");
    }

    @Test
    void testProcessArchiFile_withNullInput() throws FileParsingException, ConversionException {
        // Test behavior with null input
        Boolean removeInvalidSources = false;
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processArchiFile(null, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(null, removeInvalidSources);

        verify(converterEngine, times(1)).processArchiFile(null, removeInvalidSources);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testProcessArchiFile_withEmptyString() throws FileParsingException, ConversionException {
        // Test behavior with empty string
        String emptyContent = "";
        Boolean removeInvalidSources = null;
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processArchiFile(emptyContent, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processArchiFile(emptyContent, removeInvalidSources);

        verify(converterEngine, times(1)).processArchiFile(emptyContent, removeInvalidSources);
        assertEquals(expectedResult, actualResult);
    }

    // ========== processExcelFile(MultipartFile, Boolean) ==========

    @Test
    void testProcessExcelFile_delegatesToEngineWithoutError() throws ExcelReadingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processExcelFile(mockFile, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processExcelFile(mockFile, removeInvalidSources);

        // Verify that engine received exactly the same file and parameter
        verify(converterEngine, times(1)).processExcelFile(mockFile, removeInvalidSources);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessExcelFile_withRemoveInvalidSourcesTrue() throws ExcelReadingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = true;
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processExcelFile(mockFile, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processExcelFile(mockFile, removeInvalidSources);

        verify(converterEngine, times(1)).processExcelFile(mockFile, removeInvalidSources);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testProcessExcelFile_propagatesIOException() throws ExcelReadingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;

        // Set up mock: when calling processExcelFile engine should throw IOException
        doThrow(new IOException("IO error")).when(converterEngine).processExcelFile(mockFile, removeInvalidSources);

        // Verify that service also throws IOException
        assertThrows(IOException.class, () -> converterService.processExcelFile(mockFile, removeInvalidSources),
                "Service should throw IOException for IO errors");
    }

    @Test
    void testProcessExcelFile_propagatesExcelReadingException() throws ExcelReadingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;

        // Set up mock: when calling processExcelFile engine should throw ExcelReadingException
        doThrow(new ExcelReadingException("Excel reading error")).when(converterEngine).processExcelFile(mockFile, removeInvalidSources);

        // Verify that service also throws ExcelReadingException
        assertThrows(ExcelReadingException.class, () -> converterService.processExcelFile(mockFile, removeInvalidSources),
                "Service should throw ExcelReadingException for Excel reading errors");
    }

    @Test
    void testProcessExcelFile_propagatesConversionException() throws ExcelReadingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;

        doThrow(new ConversionException("Conversion error")).when(converterEngine).processExcelFile(mockFile, removeInvalidSources);

        assertThrows(ConversionException.class, () -> converterService.processExcelFile(mockFile, removeInvalidSources),
                "Service should propagate ConversionException from engine");
    }

    // ========== processEAFile(MultipartFile, Boolean) ==========

    @Test
    void testProcessEAFile_delegatesToEngineWithoutError() throws FileParsingException, IOException, ConversionException {
        // Mock MultipartFile
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;
        ConversionResult expectedResult = mock(ConversionResult.class);

        // Engine returns a conversion result; call the service method
        when(converterEngine.processEAFile(mockFile, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processEAFile(mockFile, removeInvalidSources);

        // Verify that engine received exactly the same file and parameter
        verify(converterEngine, times(1)).processEAFile(mockFile, removeInvalidSources);
        assertEquals(expectedResult, actualResult, "Service should return the result from engine");
    }

    @Test
    void testProcessEAFile_withRemoveInvalidSourcesTrue() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = true;
        ConversionResult expectedResult = mock(ConversionResult.class);

        when(converterEngine.processEAFile(mockFile, removeInvalidSources)).thenReturn(expectedResult);

        ConversionResult actualResult = converterService.processEAFile(mockFile, removeInvalidSources);

        verify(converterEngine, times(1)).processEAFile(mockFile, removeInvalidSources);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void testProcessEAFile_propagatesFileParsingException() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;

        doThrow(new FileParsingException("EA file parsing error")).when(converterEngine).processEAFile(mockFile, removeInvalidSources);

        assertThrows(FileParsingException.class, () -> converterService.processEAFile(mockFile, removeInvalidSources),
                "Service should throw FileParsingException for EA file parsing errors");
    }

    @Test
    void testProcessEAFile_propagatesIOException() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;

        doThrow(new IOException("IO error")).when(converterEngine).processEAFile(mockFile, removeInvalidSources);

        assertThrows(IOException.class, () -> converterService.processEAFile(mockFile, removeInvalidSources),
                "Service should throw IOException for IO errors");
    }

    @Test
    void testProcessEAFile_propagatesConversionException() throws FileParsingException, IOException, ConversionException {
        MultipartFile mockFile = mock(MultipartFile.class);
        Boolean removeInvalidSources = false;

        doThrow(new ConversionException("Conversion error")).when(converterEngine).processEAFile(mockFile, removeInvalidSources);

        assertThrows(ConversionException.class, () -> converterService.processEAFile(mockFile, removeInvalidSources),
                "Service should propagate ConversionException from engine");
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
}