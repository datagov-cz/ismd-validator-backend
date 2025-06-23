package com.dia.converter.reader.excel;

import com.dia.converter.data.*;
import com.dia.converter.reader.excel.mapper.ColumnMapping;
import com.dia.converter.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.converter.reader.excel.poi.*;
import com.dia.exceptions.ExcelReadingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

import static com.dia.constants.ExcelOntologyConstants.*;

/**
 * ExcelReader - Reads and pareses ontology data from Excel files
 * TODO: verify possibility of TypeMappings usage
 */
@Component
@Slf4j
public class ExcelReader {

    private final WorkbookProcessor workbookProcessor;
    private final ColumnMappingRegistry mappingRegistry;
    private final DataValidator validator;

    public ExcelReader() {
        this.workbookProcessor = new WorkbookProcessor();
        this.mappingRegistry = new ColumnMappingRegistry();
        this.validator = new DataValidator();
        initializeDefaultMappings();
    }

    public OntologyData readOntologyFromExcel(InputStream inputStream) throws ExcelReadingException {
        OntologyData.Builder builder;
        try (inputStream; Workbook workbook = workbookProcessor.openWorkbook(inputStream)) {
            builder = OntologyData.builder();

            builder.vocabularyMetadata(processVocabularySheet(workbook))
                    .classes(processClassesSheet(workbook))
                    .properties(processPropertiesSheet(workbook))
                    .relationships(processRelationshipsSheet(workbook));

            OntologyData data = builder.build();
            validator.validateOntologyData(data);
            return data;
        } catch (Exception e) {
            throw new ExcelReadingException("Failed to read ontology from Excel.", e);
        }
    }

    private VocabularyMetadata processVocabularySheet(Workbook workbook) throws ExcelReadingException {
        if (!workbookProcessor.hasSheet(workbook, SLOVNIK)) {
            throw new ExcelReadingException("Workbook does not have Slovník sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, SLOVNIK);
        return new VocabularySheetProcessor(mappingRegistry).process(sheet);
    }

    private List<ClassData> processClassesSheet(Workbook workbook) throws ExcelReadingException {
        if (!workbookProcessor.hasSheet(workbook, SUBJEKTY_OBJEKTY_PRAVA)) {
            throw new ExcelReadingException("Workbook does not have Subjekty a objekty práva sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, SUBJEKTY_OBJEKTY_PRAVA);
        return new ClassSheetProcessor(mappingRegistry).process(sheet);
    }

    private List<PropertyData> processPropertiesSheet(Workbook workbook) throws ExcelReadingException {
        if (!workbookProcessor.hasSheet(workbook, VLASTNOSTI)) {
            throw new ExcelReadingException("Workbook does not have Vlastnosti sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, VLASTNOSTI);
        return new PropertySheetProcessor(mappingRegistry).process(sheet);

    }

    private List<RelationshipData> processRelationshipsSheet(Workbook workbook) throws ExcelReadingException {
        if (!workbookProcessor.hasSheet(workbook, VZTAHY)) {
            throw new ExcelReadingException("Workbook does not have Vztahy sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, VZTAHY);
        return new RelationshipSheetProcessor(mappingRegistry).process(sheet);
    }

    private void initializeDefaultMappings() {
        setupClassesMappings();
        setupPropertiesMappings();
        setupRelationshipsMappings();
        setupVocabularyMappings();
    }

    private void setupClassesMappings() {
        ColumnMapping<ClassData> classMapping = ColumnMapping.<ClassData>builder()
                .withColumn(NAZEV, ClassData::setName)
                .withColumn(TYP, ClassData::setType)
                .withColumn(POPIS, ClassData::setDescription)
                .withColumn(DEFINICE, ClassData::setDefinition)
                .withColumn(ZDROJ, ClassData::setSource)
                .withColumn(SOUVISEJICI_ZDROJ, ClassData::setRelatedSource)
                .withColumn(NADRAZENY_POJEM, ClassData::setSuperClass)
                .withColumn(ALT_NAZEV, ClassData::setAlternativeName)
                .withColumn(EKVIVALENTNI_POJEM, ClassData::setEquivalentConcept)
                .withColumn(IDENTIFIKATOR, ClassData::setIdentifier)
                .withColumn(AGENDA, ClassData::setAgendaCode)
                .withColumn(AIS, ClassData::setAgendaSystemCode)
                .build();

        mappingRegistry.registerMapping(SUBJEKTY_OBJEKTY_PRAVA, classMapping);
    }

    private void setupPropertiesMappings() {
        ColumnMapping<PropertyData> propertyMapping = ColumnMapping.<PropertyData>builder()
                .withColumn(NAZEV, PropertyData::setName)
                .withColumn(SUBJEKTY_OBJEKTY_PRAVA, PropertyData::setDomain)
                .withColumn(POPIS, PropertyData::setDescription)
                .withColumn(DEFINICE, PropertyData::setDefinition)
                .withColumn(ZDROJ, PropertyData::setSource)
                .withColumn(SOUVISEJICI_ZDROJ, PropertyData::setRelatedSource)
                .withColumn(NADRAZENY_POJEM, PropertyData::setSuperProperty)
                .withColumn(ALT_NAZEV, PropertyData::setAlternativeName)
                .withColumn(EKVIVALENTNI_POJEM, PropertyData::setEquivalentConcept)
                .withColumn(IDENTIFIKATOR, PropertyData::setIdentifier)
                .withColumn(DATOVY_TYP, PropertyData::setDataType)
                .withColumn(JE_PPDF, PropertyData::setSharedInPPDF)
                .withColumn(JE_VEREJNY, PropertyData::setIsPublic)
                .withColumn(USTANOVENI_DOKLADAJICI_NEVEREJNOST, PropertyData::setPrivacyProvision)
                .withColumn(ZPUSOB_SDILENI_UDEJE, PropertyData::setSharingMethod)
                .withColumn(ZPUSOB_ZISKANI_UDEJE, PropertyData::setAcquisitionMethod)
                .withColumn(TYP_OBSAHU_UDAJE, PropertyData::setContentType)
                .build();

        mappingRegistry.registerMapping(VLASTNOSTI, propertyMapping);
    }

    private void setupRelationshipsMappings() {
        ColumnMapping<RelationshipData> relationMapping = ColumnMapping.<RelationshipData>builder()
                .withColumn(SUBJEKTY_OBJEKTY_PRAVA, RelationshipData::setDomain)
                .withColumn(NAZEV, RelationshipData::setName)
                .withColumn(SUBJEKTY_OBJEKTY_PRAVA, RelationshipData::setRange)
                .withColumn(POPIS, RelationshipData::setDescription)
                .withColumn(DEFINICE, RelationshipData::setDefinition)
                .withColumn(ZDROJ, RelationshipData::setSource)
                .withColumn(SOUVISEJICI_ZDROJ, RelationshipData::setRelatedSource)
                .withColumn(NADRAZENY_POJEM, RelationshipData::setSuperRelation)
                .withColumn(ALT_NAZEV, RelationshipData::setAlternativeName)
                .withColumn(EKVIVALENTNI_POJEM, RelationshipData::setEquivalentConcept)
                .withColumn(IDENTIFIKATOR, RelationshipData::setIdentifier)
                .withColumn(JE_PPDF, RelationshipData::setSharedInPPDF)
                .withColumn(JE_VEREJNY, RelationshipData::setIsPublic)
                .withColumn(USTANOVENI_DOKLADAJICI_NEVEREJNOST, RelationshipData::setPrivacyProvision)
                .withColumn(ZPUSOB_SDILENI_UDEJE, RelationshipData::setSharingMethod)
                .withColumn(ZPUSOB_ZISKANI_UDEJE, RelationshipData::setAcquisitionMethod)
                .withColumn(TYP_OBSAHU_UDAJE, RelationshipData::setContentType)
                .build();

        mappingRegistry.registerMapping(VZTAHY, relationMapping);
    }

    private void setupVocabularyMappings() {
        log.debug("Setting up vocabulary mappings...");

        ColumnMapping<VocabularyMetadata> vocabMapping = ColumnMapping.<VocabularyMetadata>builder()
                .withKeyValuePair("Název slovníku:", VocabularyMetadata::setName)
                .withKeyValuePair("Popis slovníku:", VocabularyMetadata::setDescription)
                .withKeyValuePair("Adresa lokálního katalogu dat, ve kterém bude slovník registrován:",
                        VocabularyMetadata::setNamespace)
                .build();

        log.debug("Registering mapping for 'Slovník' sheet");
        mappingRegistry.registerMapping("Slovník", vocabMapping);
        log.debug("Vocabulary mapping setup completed");
    }
}
