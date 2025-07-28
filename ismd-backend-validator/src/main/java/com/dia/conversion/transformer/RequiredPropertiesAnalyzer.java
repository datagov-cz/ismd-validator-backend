package com.dia.conversion.transformer;

import com.dia.conversion.data.ClassData;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.PropertyData;
import com.dia.conversion.data.RelationshipData;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.dia.constants.ArchiConstants.*;

@Slf4j
public class RequiredPropertiesAnalyzer {

    private final OntologyData ontologyData;
    private final Set<String> requiredProperties = new HashSet<>();

    public RequiredPropertiesAnalyzer(OntologyData ontologyData) {
        this.ontologyData = ontologyData;
    }

    public Set<String> analyze() {
        checkBasicFields();
        checkSpecializedFields();
        checkGovernanceFields();
        checkHierarchies();

        log.debug("Analysis found {} required properties", requiredProperties.size());
        return requiredProperties;
    }

    private void checkBasicFields() {
        if (hasNames()) requiredProperties.add(NAZEV);
        if (hasDescriptions()) requiredProperties.add(POPIS);
        if (hasDefinitions()) requiredProperties.add(DEFINICE);
        if (hasSources()) requiredProperties.add(ZDROJ);
        if (hasAlternativeNames()) requiredProperties.add(ALTERNATIVNI_NAZEV);
        if (hasEquivalentConcepts()) requiredProperties.add(EKVIVALENTNI_POJEM);
    }

    private void checkSpecializedFields() {
        if (hasPPDFInfo()) requiredProperties.add(JE_PPDF);
        if (hasAgendaCodes()) requiredProperties.add(AGENDA);
        if (hasAISCodes()) requiredProperties.add(AIS);
        if (hasPrivacyProvisions()) requiredProperties.add(USTANOVENI_NEVEREJNOST);

        if (hasDataTypesOrDomainRange()) {
            requiredProperties.add(DEFINICNI_OBOR);
            requiredProperties.add(OBOR_HODNOT);
        }
    }

    private void checkGovernanceFields() {
        if (hasGovernanceProps()) {
            requiredProperties.add(ZPUSOB_SDILENI);
            requiredProperties.add(ZPUSOB_ZISKANI);
            requiredProperties.add(TYP_OBSAHU);
        }
    }

    private void checkHierarchies() {
        if (!ontologyData.getHierarchies().isEmpty()) {
            requiredProperties.add(NADRAZENA_TRIDA);
        }
    }

    private boolean hasNames() {
        return hasAnyFieldAcrossAllEntities(ontologyData,
                ClassData::getName, PropertyData::getName, RelationshipData::getName);
    }

    private boolean hasDescriptions() {
        return hasAnyFieldAcrossAllEntities(ontologyData,
                ClassData::getDescription, PropertyData::getDescription, RelationshipData::getDescription);
    }

    private boolean hasDefinitions() {
        return hasAnyFieldAcrossAllEntities(ontologyData,
                ClassData::getDefinition, PropertyData::getDefinition, RelationshipData::getDefinition);
    }

    private boolean hasSources() {
        return hasAnyFieldAcrossAllEntities(ontologyData,
                ClassData::getSource, PropertyData::getSource, RelationshipData::getSource) ||
                hasAnyFieldAcrossAllEntities(ontologyData,
                        ClassData::getRelatedSource, PropertyData::getRelatedSource, RelationshipData::getRelatedSource);
    }

    private boolean hasAlternativeNames() {
        return hasAnyFieldAcrossAllEntities(ontologyData,
                ClassData::getAlternativeName, PropertyData::getAlternativeName, RelationshipData::getAlternativeName);
    }

    private boolean hasEquivalentConcepts() {
        return hasAnyFieldAcrossAllEntities(ontologyData,
                ClassData::getEquivalentConcept, PropertyData::getEquivalentConcept, RelationshipData::getEquivalentConcept);
    }

    private boolean hasPPDFInfo() {
        return hasAnyPropertyFieldValue(PropertyData::getSharedInPPDF, ontologyData.getProperties());
    }

    private boolean hasAgendaCodes() {
        return hasAnyFieldValue(ClassData::getAgendaCode, ontologyData.getClasses());
    }

    private boolean hasAISCodes() {
        return hasAnyFieldValue(ClassData::getAgendaSystemCode, ontologyData.getClasses());
    }

    private boolean hasPrivacyProvisions() {
        return hasAnyPropertyFieldValue(PropertyData::getPrivacyProvision, ontologyData.getProperties());
    }

    private boolean hasDataTypesOrDomainRange() {
        boolean hasDataTypes = hasAnyPropertyFieldValue(PropertyData::getDataType, ontologyData.getProperties());
        boolean hasDomainRangeInfo = hasAnyPropertyFieldValue(PropertyData::getDomain, ontologyData.getProperties()) ||
                hasAnyRelationshipFieldValue(RelationshipData::getDomain, ontologyData.getRelationships()) ||
                hasAnyRelationshipFieldValue(RelationshipData::getRange, ontologyData.getRelationships());
        return hasDataTypes || hasDomainRangeInfo;
    }

    private boolean hasGovernanceProps() {
        return ontologyData.getProperties().stream().anyMatch(p ->
                hasNonEmptyValue(p.getSharingMethod()) ||
                        hasNonEmptyValue(p.getAcquisitionMethod()) ||
                        hasNonEmptyValue(p.getContentType()));
    }

    private boolean hasAnyFieldValue(Function<ClassData, String> fieldExtractor, List<ClassData> classes) {
        return classes.stream().anyMatch(c -> hasNonEmptyValue(fieldExtractor.apply(c)));
    }

    private boolean hasAnyPropertyFieldValue(Function<PropertyData, String> fieldExtractor, List<PropertyData> properties) {
        return properties.stream().anyMatch(p -> hasNonEmptyValue(fieldExtractor.apply(p)));
    }

    private boolean hasAnyRelationshipFieldValue(Function<RelationshipData, String> fieldExtractor, List<RelationshipData> relationships) {
        return relationships.stream().anyMatch(r -> hasNonEmptyValue(fieldExtractor.apply(r)));
    }

    private boolean hasAnyFieldAcrossAllEntities(OntologyData ontologyData,
                                                 Function<ClassData, String> classFieldExtractor,
                                                 Function<PropertyData, String> propertyFieldExtractor,
                                                 Function<RelationshipData, String> relationshipFieldExtractor) {
        return hasAnyFieldValue(classFieldExtractor, ontologyData.getClasses()) ||
                hasAnyPropertyFieldValue(propertyFieldExtractor, ontologyData.getProperties()) ||
                hasAnyRelationshipFieldValue(relationshipFieldExtractor, ontologyData.getRelationships());
    }

    private boolean hasNonEmptyValue(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
