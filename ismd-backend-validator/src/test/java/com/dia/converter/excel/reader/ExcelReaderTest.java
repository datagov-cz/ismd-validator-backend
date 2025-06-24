package com.dia.converter.excel.reader;

import com.dia.converter.excel.data.*;
import com.dia.converter.excel.mapper.ColumnMappingRegistry;
import com.dia.converter.excel.reader.poi.*;
import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Row;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

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
                    VocabularySheetProcessor.class,
                    (proc, ctx) -> when(proc.process(any())).thenReturn(new VocabularyMetadata()))) {

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
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> m.invoke(reader, wb));
            assertTrue(ex.getTargetException() instanceof ExcelReadingException);
        }

        @Test
        void testProcessClassesSheet_shouldUseClassSheetProcessor() throws Exception {
            XSSFWorkbook wb = new XSSFWorkbook();
            wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
            try (MockedConstruction<ClassSheetProcessor> mock = mockConstruction(
                    ClassSheetProcessor.class,
                    (proc, ctx) -> when(proc.process(any())).thenReturn(List.of()))) {

                Method m = ExcelReader.class.getDeclaredMethod("processClassesSheet", Workbook.class);
                m.setAccessible(true);
                @SuppressWarnings("unchecked")
                        List<ClassData> list = (List<ClassData>) m.invoke(reader, wb);

                assertNotNull(list);
                ClassSheetProcessor proc = mock.constructed().get(0);
                verify(proc).process(any(Sheet.class));
            }
        }

        @Test
        void testProcessClassesSheet_missingSheet_shouldThrowAndCloseStream() throws Exception {
            WorkbookProcessor wp = mock(WorkbookProcessor.class);
            setField(reader, "workbookProcessor", wp);
            Workbook wb = mock(Workbook.class);
            when(wp.hasSheet(wb, SUBJEKTY_OBJEKTY_PRAVA)).thenReturn(false);

            Method m = ExcelReader.class.getDeclaredMethod("processClassesSheet", Workbook.class);
            m.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> m.invoke(reader, wb));
            assertTrue(ex.getTargetException() instanceof ExcelReadingException);
        }

        @Test
        void testProcessPropertiesSheet_shouldUsePropertySheetProcessor() throws Exception {
            XSSFWorkbook wb = new XSSFWorkbook();
            wb.createSheet(VLASTNOSTI);
            try (MockedConstruction<PropertySheetProcessor> mock = mockConstruction(
                    PropertySheetProcessor.class,
                    (proc, ctx) -> when(proc.process(any())).thenReturn(List.of()))) {

                Method m = ExcelReader.class.getDeclaredMethod("processPropertiesSheet", Workbook.class);
                m.setAccessible(true);
                @SuppressWarnings("unchecked")
                        List<PropertyData> list = (List<PropertyData>) m.invoke(reader, wb);

                assertNotNull(list);
                PropertySheetProcessor proc = mock.constructed().get(0);
                verify(proc).process(any(Sheet.class));
            }
        }

        @Test
        void testProcessPropertiesSheet_missingSheet_shouldThrowAndCloseStream() throws Exception {
            WorkbookProcessor wp = mock(WorkbookProcessor.class);
            setField(reader, "workbookProcessor", wp);
            Workbook wb = mock(Workbook.class);
            when(wp.hasSheet(wb, VLASTNOSTI)).thenReturn(false);

            Method m = ExcelReader.class.getDeclaredMethod("processPropertiesSheet", Workbook.class);
            m.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> m.invoke(reader, wb));
            assertTrue(ex.getTargetException() instanceof ExcelReadingException);
        }

        @Test
        void testProcessRelationshipsSheet_shouldUseRelationshipSheetProcessor() throws Exception {
            XSSFWorkbook wb = new XSSFWorkbook();
            wb.createSheet(VZTAHY);
            try (MockedConstruction<RelationshipSheetProcessor> mock = mockConstruction(
                    RelationshipSheetProcessor.class,
                    (proc, ctx) -> when(proc.process(any())).thenReturn(List.of()))) {

                Method m = ExcelReader.class.getDeclaredMethod("processRelationshipsSheet", Workbook.class);
                m.setAccessible(true);
                @SuppressWarnings("unchecked")
                        List<RelationshipData> list = (List<RelationshipData>) m.invoke(reader, wb);

                assertNotNull(list);
                RelationshipSheetProcessor proc = mock.constructed().get(0);
                verify(proc).process(any(Sheet.class));
            }
        }

        @Test
        void testProcessRelationshipsSheet_missingSheet_shouldThrowAndCloseStream() throws Exception {
            WorkbookProcessor wp = mock(WorkbookProcessor.class);
            setField(reader, "workbookProcessor", wp);
            Workbook wb = mock(Workbook.class);
            when(wp.hasSheet(wb, VZTAHY)).thenReturn(false);

            Method m = ExcelReader.class.getDeclaredMethod("processRelationshipsSheet", Workbook.class);
            m.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> m.invoke(reader, wb));
            assertTrue(ex.getTargetException() instanceof ExcelReadingException);
        }

    }

    @Nested
    class ReadOntologyTests {

        @Test
        void testReadOntologyFromExcel_happyPath_shouldReturnDataAndCloseStream() throws Exception {

            // Připrava in-memory Excelu s 4 listy
            XSSFWorkbook wb = new XSSFWorkbook();

            // Slovnik
            Sheet voc = wb.createSheet(SLOVNIK);
            Row v0 = voc.createRow(0);
            v0.createCell(0).setCellValue("Název slovníku:");
            v0.createCell(1).setCellValue("MyVocab");
            Row v1 = voc.createRow(1);
            v1.createCell(0).setCellValue("Popis slovníku:");
            v1.createCell(1).setCellValue("Desc");
            Row v2 = voc.createRow(2);
            v2.createCell(0).setCellValue("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:");

            // Subjekty a objekty práva – header na řádku 0, data na řádku 1
            Sheet cls = wb.createSheet(SUBJEKTY_OBJEKTY_PRAVA);
            Row headerCls = cls.createRow(0);
            headerCls.createCell(0).setCellValue(NAZEV);
            headerCls.createCell(1).setCellValue(TYP);
            headerCls.createCell(2).setCellValue(POPIS);
            headerCls.createCell(3).setCellValue(DEFINICE);
            headerCls.createCell(4).setCellValue(ZDROJ);
            headerCls.createCell(5).setCellValue(SOUVISEJICI_ZDROJ);
            headerCls.createCell(6).setCellValue(NADRAZENY_POJEM);
            headerCls.createCell(7).setCellValue(ALT_NAZEV);
            headerCls.createCell(8).setCellValue(EKVIVALENTNI_POJEM);
            headerCls.createCell(9).setCellValue(IDENTIFIKATOR);
            headerCls.createCell(10).setCellValue(AGENDA);
            headerCls.createCell(11).setCellValue(AIS);
            Row dataCls = cls.createRow(1);
            dataCls.createCell(0).setCellValue("MyClass");

            // Vlastnosti – header na řádku 0, data na řádku 1
            Sheet pr = wb.createSheet(VLASTNOSTI);
            Row headerPr = pr.createRow(0);
            headerPr.createCell(0).setCellValue(NAZEV);
            headerPr.createCell(1).setCellValue(SUBJEKTY_OBJEKTY_PRAVA);
            headerPr.createCell(2).setCellValue(POPIS);
            headerPr.createCell(3).setCellValue(DEFINICE);
            headerPr.createCell(4).setCellValue(ZDROJ);
            headerPr.createCell(5).setCellValue(SOUVISEJICI_ZDROJ);
            headerPr.createCell(6).setCellValue(NADRAZENY_POJEM);
            headerPr.createCell(7).setCellValue(ALT_NAZEV);
            headerPr.createCell(8).setCellValue(EKVIVALENTNI_POJEM);
            headerPr.createCell(9).setCellValue(IDENTIFIKATOR);
            headerPr.createCell(10).setCellValue(DATOVY_TYP);
            headerPr.createCell(11).setCellValue(JE_PPDF);
            headerPr.createCell(12).setCellValue(JE_VEREJNY);
            headerPr.createCell(13).setCellValue(USTANOVENI_DOKLADAJICI_NEVEREJNOST);
            headerPr.createCell(14).setCellValue(ZPUSOB_SDILENI_UDEJE);
            headerPr.createCell(15).setCellValue(ZPUSOB_ZISKANI_UDEJE);
            headerPr.createCell(16).setCellValue(TYP_OBSAHU_UDAJE);
            Row dataPr = pr.createRow(1);
            dataPr.createCell(0).setCellValue("MyProp");

            // Vztahy – header na řádku 0, data na řádku 1
            Sheet rel = wb.createSheet(VZTAHY);
            Row headerRel = rel.createRow(0);
            headerRel.createCell(0).setCellValue(SUBJEKTY_OBJEKTY_PRAVA);
            headerRel.createCell(1).setCellValue(NAZEV);
            headerRel.createCell(2).setCellValue(SUBJEKTY_OBJEKTY_PRAVA);
            headerRel.createCell(3).setCellValue(POPIS);
            headerRel.createCell(4).setCellValue(DEFINICE);
            headerRel.createCell(5).setCellValue(ZDROJ);
            headerRel.createCell(6).setCellValue(SOUVISEJICI_ZDROJ);
            headerRel.createCell(7).setCellValue(NADRAZENY_POJEM);
            headerRel.createCell(8).setCellValue(ALT_NAZEV);
            headerRel.createCell(9).setCellValue(EKVIVALENTNI_POJEM);
            headerRel.createCell(10).setCellValue(IDENTIFIKATOR);
            headerRel.createCell(11).setCellValue(JE_PPDF);
            headerRel.createCell(12).setCellValue(JE_VEREJNY);
            headerRel.createCell(13).setCellValue(USTANOVENI_DOKLADAJICI_NEVEREJNOST);
            headerRel.createCell(14).setCellValue(ZPUSOB_SDILENI_UDEJE);
            headerRel.createCell(15).setCellValue(ZPUSOB_ZISKANI_UDEJE);
            headerRel.createCell(16).setCellValue(TYP_OBSAHU_UDAJE);
            Row dataRel = rel.createRow(1);
            dataRel.createCell(0).setCellValue("MyDomain");
            dataRel.createCell(1).setCellValue("MyRel");
            dataRel.createCell(2).setCellValue("MyRange");

            // Zápis a volání
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();
            SledovatelnyStream in = new SledovatelnyStream(baos.toByteArray());
            OntologyData data = reader.readOntologyFromExcel(in);

            // Ověření
            assertEquals("MyVocab", data.getVocabularyMetadata().getName());
            assertEquals("MyClass", data.getClasses().get(0).getName());
            assertEquals("MyProp", data.getProperties().get(0).getName());
            assertEquals("MyRel", data.getRelationships().get(0).getName());
            verify(validator).validateOntologyData(data);
            assertTrue(in.isZavreno());
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
