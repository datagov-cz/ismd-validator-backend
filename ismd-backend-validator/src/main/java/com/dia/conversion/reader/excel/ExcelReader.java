package com.dia.conversion.reader.excel;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.excel.mapper.ColumnMapping;
import com.dia.conversion.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.conversion.reader.excel.poi.*;
import com.dia.exceptions.ExcelReadingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.dia.constants.FormatConstants.Excel.*;

/**
 * ExcelReader - Reads and pareses ontology data from Excel files
 */
@Component
@Slf4j
public class ExcelReader {

    private static final Set<String> VALID_CLASS_TYPES = Set.of(
            "Subjekt práva",
            "Objekt práva"
    );

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

            VocabularyMetadata metadata = processVocabularySheet(workbook);
            List<ClassData> classes = processClassesSheet(workbook);
            List<PropertyData> properties = processPropertiesSheet(workbook);
            List<RelationshipData> relationships = processRelationshipsSheet(workbook);
            List<HierarchyData> hierarchies = extractHierarchiesFromData(classes, properties);

            builder.vocabularyMetadata(metadata)
                    .classes(classes)
                    .properties(properties)
                    .relationships(relationships)
                    .hierarchies(hierarchies);

            OntologyData data = builder.build();
            validator.validateOntologyData(data);
            return data;
        } catch (Exception e) {
            throw new ExcelReadingException("Failed to read ontology from Excel.", e);
        }
    }

    private VocabularyMetadata processVocabularySheet(Workbook workbook) throws ExcelReadingException {
        if (workbookProcessor.hasSheet(workbook, SLOVNIK)) {
            throw new ExcelReadingException("Workbook does not have Slovník sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, SLOVNIK);
        return new VocabularySheetProcessor(mappingRegistry).process(sheet);
    }

    private List<ClassData> processClassesSheet(Workbook workbook) throws ExcelReadingException {
        if (workbookProcessor.hasSheet(workbook, SUBJEKTY_OBJEKTY_PRAVA)) {
            log.debug("Available sheets in workbook:");
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String sheetName = workbook.getSheetName(i);
                log.debug("  Sheet {}: '{}'", i, sheetName);
            }
            throw new ExcelReadingException("Workbook does not have Subjekty a objekty práva sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, SUBJEKTY_OBJEKTY_PRAVA);
        List<ClassData> classes = new ClassSheetProcessor(mappingRegistry).process(sheet);

        log.debug("ClassSheetProcessor returned {} classes", classes.size());

        List<ClassData> validClasses = validateAndFilterClassTypes(classes);

        log.debug("After validation, {} valid classes remain", validClasses.size());

        return validClasses;
    }

    private List<ClassData> validateAndFilterClassTypes(List<ClassData> classes) {
        log.debug("Validating and filtering types for {} classes", classes.size());

        List<ClassData> validClasses = new ArrayList<>();
        List<String> skippedClasses = new ArrayList<>();
        int validCount = 0;
        int emptyTypeCount = 0;
        int invalidTypeCount = 0;

        for (ClassData classData : classes) {
            String className = classData.getName();
            String type = classData.getType();

            if (className == null || className.trim().isEmpty()) {
                log.debug("Skipping class with empty name");
                continue;
            }

            ValidationResult result = validateClassType(className, type);

            switch (result.status()) {
                case VALID -> {
                    validCount++;
                    validClasses.add(classData);
                    log.debug("Valid type '{}' for class '{}' - including in results", type, className);
                }
                case EMPTY_TYPE -> {
                    emptyTypeCount++;
                    skippedClasses.add(String.format("'%s' (empty type)", className));
                    log.warn("Skipping class '{}' with empty type. Expected: {}",
                            className, String.join(" or ", VALID_CLASS_TYPES));
                }
                case INVALID_TYPE -> {
                    invalidTypeCount++;
                    skippedClasses.add(String.format("'%s' (invalid type: '%s')", className, type));
                    log.warn("Skipping class '{}' with invalid type '{}'. Expected: {}",
                            className, type, String.join(" or ", VALID_CLASS_TYPES));
                }
            }
        }

        log.info("Class type validation summary: {} valid, {} empty types, {} invalid types",
                validCount, emptyTypeCount, invalidTypeCount);

        if (!skippedClasses.isEmpty()) {
            log.warn("Skipped {} invalid classes: {}", skippedClasses.size(), String.join(", ", skippedClasses));
        }

        if (validCount == 0) {
            log.warn("No valid classes found after type validation");
        } else {
            log.info("Proceeding with {} valid classes", validCount);
        }

        return validClasses;
    }

    private ValidationResult validateClassType(String className, String type) {
        if (type == null || type.trim().isEmpty()) {
            return new ValidationResult(ValidationStatus.EMPTY_TYPE,
                    String.format("Class '%s' has empty type", className));
        }

        String trimmedType = type.trim();
        if (!VALID_CLASS_TYPES.contains(trimmedType)) {
            return new ValidationResult(ValidationStatus.INVALID_TYPE,
                    String.format("Class '%s' has invalid type '%s'", className, trimmedType));
        }

        return new ValidationResult(ValidationStatus.VALID,
                String.format("Class '%s' has valid type '%s'", className, trimmedType));
    }

    private List<PropertyData> processPropertiesSheet(Workbook workbook) throws ExcelReadingException {
        if (workbookProcessor.hasSheet(workbook, VLASTNOSTI)) {
            throw new ExcelReadingException("Workbook does not have Vlastnosti sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, VLASTNOSTI);
        return new PropertySheetProcessor(mappingRegistry).process(sheet);
    }

    private List<RelationshipData> processRelationshipsSheet(Workbook workbook) throws ExcelReadingException {
        if (workbookProcessor.hasSheet(workbook, VZTAHY)) {
            throw new ExcelReadingException("Workbook does not have Vztahy sheet.");
        }
        Sheet sheet = workbookProcessor.getSheet(workbook, VZTAHY);
        return new RelationshipSheetProcessor(mappingRegistry).process(sheet);
    }

    private List<HierarchyData> extractHierarchiesFromData(List<ClassData> classes, List<PropertyData> properties) {
        List<HierarchyData> hierarchies = new ArrayList<>();

        log.debug("Extracting hierarchies from {} classes and {} properties", classes.size(), properties.size());

        for (ClassData classData : classes) {
            if (classData.getSuperClass() != null && !classData.getSuperClass().trim().isEmpty()) {
                String superClass = classData.getSuperClass().trim();

                if (isValidForHierarchy(classData.getName(), superClass)) {
                    HierarchyData hierarchyData = createHierarchyData(
                            classData.getName(),
                            superClass,
                            "CLASS-HIER-" + classData.getName(),
                            "IS-A relationship",
                            classData.getDescription()
                    );
                    hierarchies.add(hierarchyData);

                    log.debug("Extracted class hierarchy: {} -> {}", classData.getName(), superClass);
                }
            }
        }

        for (PropertyData propertyData : properties) {
            if (propertyData.getSuperProperty() != null && !propertyData.getSuperProperty().trim().isEmpty()) {
                String superProperty = propertyData.getSuperProperty().trim();

                if (isValidForHierarchy(propertyData.getName(), superProperty)) {
                    HierarchyData hierarchyData = createHierarchyData(
                            propertyData.getName(),
                            superProperty,
                            "PROP-HIER-" + propertyData.getName(),
                            "subPropertyOf relationship",
                            propertyData.getDescription()
                    );
                    hierarchies.add(hierarchyData);

                    log.debug("Extracted property hierarchy: {} -> {}", propertyData.getName(), superProperty);
                }
            }
        }

        log.info("Extracted {} hierarchical relationships from Excel data", hierarchies.size());
        return hierarchies;
    }

    private HierarchyData createHierarchyData(String childName, String parentName, String relationshipId,
                                              String relationshipName, String description) {
        HierarchyData hierarchyData = new HierarchyData(childName, parentName, relationshipId, relationshipName);
        hierarchyData.setDescription(description);
        return hierarchyData;
    }

    private boolean isValidForHierarchy(String childName, String parentName) {
        if (childName == null || childName.trim().isEmpty() ||
                parentName == null || parentName.trim().isEmpty()) {
            return false;
        }

        if ("Subjekt".equals(childName) || "Objekt".equals(childName) || "Vlastnost".equals(childName) ||
                "Subjekt".equals(parentName) || "Objekt".equals(parentName) || "Vlastnost".equals(parentName)) {
            log.debug("Skipping template concept hierarchy: {} -> {}", childName, parentName);
            return false;
        }

        if (parentName.startsWith("http://") || parentName.startsWith("https://")) {
            log.debug("Skipping external URI parent: {} -> {}", childName, parentName);
            return false;
        }

        if (childName.equals(parentName)) {
            log.debug("Skipping self-referential hierarchy: {}", childName);
            return false;
        }

        return true;
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
                .withColumn(ALT_NAZEV, ClassData::setAlternativeName)
                .withColumn(TYP, ClassData::setType)
                .withColumn(POPIS, ClassData::setDescription)
                .withColumn(DEFINICE, ClassData::setDefinition)
                .withColumn(ZDROJ, ClassData::setSource)
                .withColumn(SOUVISEJICI_ZDROJ, ClassData::setRelatedSource)
                .withColumn(NADRAZENY_POJEM, ClassData::setSuperClass)
                .withColumn(EKVIVALENTNI_POJEM, ClassData::setEquivalentConcept)
                .withColumn(IDENTIFIKATOR, ClassData::setIdentifier)
                .withColumn(AGENDA, ClassData::setAgendaCode)
                .withColumn(AIS, ClassData::setAgendaSystemCode)
                .withColumn(JE_VEREJNY, ClassData::setIsPublic)
                .withColumn(USTANOVENI_DOKLADAJICI_NEVEREJNOST, ClassData::setPrivacyProvision)
                .withColumn(ZPUSOB_SDILENI_UDEJE, ClassData::setSharingMethod)
                .withColumn(ZPUSOB_ZISKANI_UDEJE, ClassData::setAcquisitionMethod)
                .withColumn(TYP_OBSAHU_UDAJE, ClassData::setContentType)
                .build();

        mappingRegistry.registerMapping(SUBJEKTY_OBJEKTY_PRAVA, classMapping);
    }

    private void setupPropertiesMappings() {
        ColumnMapping<PropertyData> propertyMapping = ColumnMapping.<PropertyData>builder()
                .withColumn(NAZEV, PropertyData::setName)
                .withColumn(ALT_NAZEV, PropertyData::setAlternativeName)
                .withColumn(SUBJEKT_OBJEKT_PRAVA, PropertyData::setDomain)
                .withColumn(POPIS, PropertyData::setDescription)
                .withColumn(DEFINICE, PropertyData::setDefinition)
                .withColumn(ZDROJ, PropertyData::setSource)
                .withColumn(SOUVISEJICI_ZDROJ, PropertyData::setRelatedSource)
                .withColumn(NADRAZENY_POJEM, PropertyData::setSuperProperty)
                .withColumn(EKVIVALENTNI_POJEM, PropertyData::setEquivalentConcept)
                .withColumn(IDENTIFIKATOR, PropertyData::setIdentifier)
                .withColumn(DATOVY_TYP, PropertyData::setDataType)
                .withColumn(JE_PPDF, PropertyData::setSharedInPPDF)
                .withColumn(AGENDA, PropertyData::setAgendaCode)
                .withColumn(AIS, PropertyData::setAgendaSystemCode)
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
                .withColumn(ALT_NAZEV, RelationshipData::setAlternativeName)
                .withColumn(SUBJEKTY_OBJEKTY_PRAVA, RelationshipData::setRange)
                .withColumn(POPIS, RelationshipData::setDescription)
                .withColumn(DEFINICE, RelationshipData::setDefinition)
                .withColumn(ZDROJ, RelationshipData::setSource)
                .withColumn(SOUVISEJICI_ZDROJ, RelationshipData::setRelatedSource)
                .withColumn(NADRAZENY_POJEM, RelationshipData::setSuperRelation)
                .withColumn(EKVIVALENTNI_POJEM, RelationshipData::setEquivalentConcept)
                .withColumn(IDENTIFIKATOR, RelationshipData::setIdentifier)
                .withColumn(JE_PPDF, RelationshipData::setSharedInPPDF)
                .withColumn(AGENDA, RelationshipData::setAgendaCode)
                .withColumn(AIS, RelationshipData::setAgendaSystemCode)
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

    private enum ValidationStatus {
        VALID,
        EMPTY_TYPE,
        INVALID_TYPE
    }

    private record ValidationResult(ValidationStatus status, String message) {
    }
}