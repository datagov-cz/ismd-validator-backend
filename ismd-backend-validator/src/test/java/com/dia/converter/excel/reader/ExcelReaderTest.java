package com.dia.converter.excel.reader;

import com.dia.converter.excel.data.VocabularyMetadata;
import com.dia.converter.excel.mapper.ColumnMappingRegistry;
import com.dia.converter.excel.reader.poi.DataValidator;
import com.dia.converter.excel.reader.poi.VocabularySheetProcessor;
import com.dia.converter.excel.reader.poi.WorkbookProcessor;
import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static com.dia.constants.ExcelOntologyConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelReaderTest {

    @Mock
    private DataValidator validator;
    private ExcelReader reader;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        reader = new ExcelReader();
        setField(reader, "validator", validator);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // Pomocná metoda pro nastavení privátního pole pomocí reflexe
    private static void setField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // InputStream, který sleduje, jestli bylo zavřeno
    private static class SledovatelnyStream extends ByteArrayInputStream {
        private boolean zavreno = false;
        SledovatelnyStream(byte[] buf) { super(buf); }
        @Override public void close() throws IOException {
            super.close();
            zavreno = true;
        }
        boolean isZavreno() { return zavreno; }
    }

    @Nested
    class InitializationTests {

        @Test
        void testConstructor_shouldInitializeDependencies() throws Exception {
            Field wp = ExcelReader.class.getDeclaredField("workbookProcessor");
            Field mr = ExcelReader.class.getDeclaredField("mappingRegistry");
            Field dv = ExcelReader.class.getDeclaredField("validator");
            wp.setAccessible(true);
            mr.setAccessible(true);
            dv.setAccessible(true);

            assertNotNull(wp.get(reader));
            assertNotNull(mr.get(reader));
            assertNotNull(dv.get(reader));
        }

        @Test
        void testInitializeDefaultMappings_shouldRegisterAllSheetMappings() throws Exception {
            ColumnMappingRegistry spyRegistry = spy(new ColumnMappingRegistry());
            setField(reader, "mappingRegistry", spyRegistry);
            Method init = ExcelReader.class.getDeclaredMethod("initializeDefaultMappings");
            init.setAccessible(true);
            init.invoke(reader);

            verify(spyRegistry).registerMapping(eq(SLOVNIK), any());
            verify(spyRegistry).registerMapping(eq(SUBJEKTY_OBJEKTY_PRAVA), any());
            verify(spyRegistry).registerMapping(eq(VLASTNOSTI), any());
            verify(spyRegistry).registerMapping(eq(VZTAHY), any());
        }

        @Test
        void testSetupClassesMappings_shouldRegisterClassMapping() throws Exception {
            ColumnMappingRegistry spyRegistry = spy(new ColumnMappingRegistry());
            setField(reader, "mappingRegistry", spyRegistry);
            Method m = ExcelReader.class.getDeclaredMethod("setupClassesMappings");
            m.setAccessible(true);
            m.invoke(reader);

            verify(spyRegistry, times(1)).registerMapping(eq(SUBJEKTY_OBJEKTY_PRAVA), any());
            verifyNoMoreInteractions(spyRegistry);
        }

        @Test
        void testSetupPropertiesMappings_shouldRegisterPropertyMappings() throws Exception {
            ColumnMappingRegistry spyRegistry = spy(new ColumnMappingRegistry());
            setField(reader, "mappingRegistry", spyRegistry);
            Method m = ExcelReader.class.getDeclaredMethod("setupPropertiesMappings");
            m.setAccessible(true);
            m.invoke(reader);

            verify(spyRegistry, times(1)).registerMapping(eq(VLASTNOSTI), any());
            verifyNoMoreInteractions(spyRegistry);
        }

        @Test
        void testSetupRelationshipsMappings_shouldRegisterRelationshipMappings() throws Exception {
            ColumnMappingRegistry spyRegistry = spy(new ColumnMappingRegistry());
            setField(reader, "mappingRegistry", spyRegistry);
            Method m = ExcelReader.class.getDeclaredMethod("setupRelationshipsMappings");
            m.setAccessible(true);
            m.invoke(reader);

            verify(spyRegistry, times(1)).registerMapping(eq(VZTAHY), any());
            verifyNoMoreInteractions(spyRegistry);
        }

        @Test
        void testSetupVocabularyMappings_shouldRegisterVocabularyMappings() throws Exception {
            ColumnMappingRegistry spyRegistry = spy(new ColumnMappingRegistry());
            setField(reader, "mappingRegistry", spyRegistry);
            Method m = ExcelReader.class.getDeclaredMethod("setupVocabularyMappings");
            m.setAccessible(true);
            m.invoke(reader);

            verify(spyRegistry, times(1)).registerMapping(eq(SLOVNIK), any());
            verifyNoMoreInteractions(spyRegistry);
        }
    }

    @Nested
    class ProcessSheetTests {

        @Test
        void testProcessVocabularySheet_shouldUseVocabularySheetProcessor() throws Exception {
            XSSFWorkbook wb = new XSSFWorkbook();
            wb.createSheet(SLOVNIK);
            try (MockedConstruction<VocabularySheetProcessor> mock = mockConstruction(
                    VocabularySheetProcessor.class, (proc, ctx) -> when(proc.process(any())).thenReturn(new VocabularyMetadata()))) {

                Method m = ExcelReader.class.getDeclaredMethod("processVocabularySheet", Workbook.class);
                m.setAccessible(true);
                VocabularyMetadata meta = (VocabularyMetadata) m.invoke(reader, wb);

                assertNotNull(meta);
                VocabularySheetProcessor proc = mock.constructed().get(0);
                verify(proc).process(any(Sheet.class));
            }
        }

        @Test
        void testProcessVocabularySheet_missingSheet_shouldThrowException() throws Exception {
            WorkbookProcessor wp = mock(WorkbookProcessor.class);
            setField(reader, "workbookProcessor", wp);
            Workbook wb = mock(Workbook.class);
            when(wp.hasSheet(wb, SLOVNIK)).thenReturn(false);

            Method m = ExcelReader.class.getDeclaredMethod("processVocabularySheet", Workbook.class);
            m.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> m.invoke(reader, wb));
            assertTrue(ex.getTargetException() instanceof ExcelReadingException);
        }

        @Test
        void testProcessClassesSheet_shouldUseClassSheetProcessor() throws Exception {

        }

        @Test
        void testProcessClassesSheet_missingSheet_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testProcessPropertiesSheet_shouldUsePropertySheetProcessor() throws Exception {

        }

        @Test
        void testProcessPropertiesSheet_missingSheet_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testProcessRelationshipsSheet_shouldUseRelationshipSheetProcessor() throws Exception {

        }

        @Test
        void testProcessRelationshipsSheet_missingSheet_shouldThrowAndCloseStream() throws Exception {

        }

    }

    @Nested
    class ReadOntologyTests {

        @Test
        void testReadOntologyFromExcel_happyPath_shouldReturnDataAndCloseStream() throws Exception {

        }

        @Test
        void testReadOntologyFromExcel_missingSlovnik_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testReadOntologyFromExcel_missingClasses_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testReadOntologyFromExcel_missingProperties_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testReadOntologyFromExcel_missingRelationships_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testReadOntologyFromExcel_validatorThrows_shouldThrowAndCloseStream() throws Exception {

        }

        @Test
        void testReadOntologyFromExcel_shouldUseAllSheetProcessors() throws Exception {

        }
    }


}
