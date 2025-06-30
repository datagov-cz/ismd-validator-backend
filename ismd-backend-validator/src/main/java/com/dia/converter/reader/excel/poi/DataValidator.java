package com.dia.converter.reader.excel.poi;

import com.dia.converter.data.*;
import com.dia.exceptions.ExcelReadingException;

import java.util.List;
import java.util.Set;

/**
 * DataValidator - Ensures data integrity and completeness
 * <p>
 * This class validates the parsed data to ensure it meets the requirements
 * for successful transformation to the OFN model.
 */
public class DataValidator {

    public void validateOntologyData(OntologyData data) throws ExcelReadingException {
        validateVocabularyMetadata(data.getVocabularyMetadata());
        validateClasses(data.getClasses());
        validateProperties(data.getProperties());
        validateRelationships(data.getRelationships());
        validateReferences(data);
    }

    private void validateVocabularyMetadata(VocabularyMetadata metadata) throws ExcelReadingException {
        if (metadata.getName() == null || metadata.getName().trim().isEmpty()) {
            throw new ExcelReadingException("Slovník name is required.");
        }
    }

    private void validateClasses(List<ClassData> classes) throws ExcelReadingException {
        for (ClassData classData : classes) {
            if (classData.getName() == null || classData.getName().trim().isEmpty()) {
                throw new ExcelReadingException("Class name is required for all classes.");
            }
        }
    }

    private void validateProperties(List<PropertyData> properties) throws ExcelReadingException {
        for (PropertyData property : properties) {
            if (property.getName() == null || property.getName().trim().isEmpty()) {
                throw new ExcelReadingException("Property name is required for all properties.");
            }
        }
    }

    private void validateRelationships(List<RelationshipData> relationships) throws ExcelReadingException {
        for (RelationshipData relationship : relationships) {
            if (relationship.getName() == null || relationship.getName().trim().isEmpty()) {
                throw new ExcelReadingException("Vztah name is required");
            }
            if (relationship.getDomain() == null || relationship.getDomain().trim().isEmpty()) {
                throw new ExcelReadingException("Vztah domain is required: " + relationship.getName());
            }
            if (relationship.getRange() == null || relationship.getRange().trim().isEmpty()) {
                throw new ExcelReadingException("Vztah range is required: " + relationship.getName());
            }
        }
    }

    private void validateReferences(OntologyData data) throws ExcelReadingException {
        Set<String> classNames = data.getClasses().stream()
                .map(ClassData::getName)
                .collect(java.util.stream.Collectors.toSet());

        for (PropertyData property : data.getProperties()) {
            if (property.getDomain() != null && !property.getDomain().isEmpty() && !classNames.contains(property.getDomain())) {
                throw new ExcelReadingException(
                        "Vlastnost '" + property.getName() + "' references unknown class: " + property.getDomain());
            }
        }

        for (RelationshipData relationship : data.getRelationships()) {
            if (!classNames.contains(relationship.getDomain())) {
                throw new ExcelReadingException(
                        "Vztah '" + relationship.getName() + "' references unknown domain class: " + relationship.getDomain());
            }
        }
    }
}
