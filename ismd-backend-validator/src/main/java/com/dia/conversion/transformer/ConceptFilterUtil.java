package com.dia.conversion.transformer;

import com.dia.conversion.data.ClassData;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.PropertyData;
import com.dia.conversion.data.RelationshipData;
import com.dia.utility.DataTypeConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ConceptFilterUtil {

    @Value("${concept.filter.enabled:false}")
    private boolean conceptFilterEnabled;

    @Value("${concept.filter.regex:}")
    private String conceptFilterRegex;

    @Getter
    private Pattern conceptFilterPattern;

    public static class FilterStatistics {
        int filteredClasses = 0;
        int filteredProperties = 0;
        int filteredRelationships = 0;
        int filteredHierarchies = 0;
        int omittedSuperClasses = 0;
        int omittedSuperProperties = 0;
        int omittedSuperRelations = 0;
        int omittedEquivalentConcepts = 0;
        int omittedDomains = 0;
        int omittedRanges = 0;
        Set<String> filteredConceptIdentifiers = new HashSet<>();
        Set<String> filteredConceptNames = new HashSet<>();

        void logStatistics() {
            log.info("=== Concept Filtering Statistics ===");
            log.info("Filtered concepts: {} classes, {} properties, {} relationships",
                    filteredClasses, filteredProperties, filteredRelationships);
            log.info("Filtered hierarchies: {}", filteredHierarchies);
            log.info("Omitted references: {} superClasses, {} superProperties, {} superRelations, {} equivalentConcepts",
                    omittedSuperClasses, omittedSuperProperties, omittedSuperRelations, omittedEquivalentConcepts);
            log.info("Omitted domain/range references: {} domains, {} ranges", omittedDomains, omittedRanges);
            log.info("Total filtered concept identifiers: {}", filteredConceptIdentifiers.size());
            if (!filteredConceptIdentifiers.isEmpty()) {
                log.info("Filtered concept identifiers: {}", filteredConceptIdentifiers);
            }
            if (!filteredConceptNames.isEmpty()) {
                log.info("Filtered concept names: {}", filteredConceptNames);
            }
            log.info("====================================");
        }
    }

    @PostConstruct
    public void initializeConceptFilter() {
        if (!conceptFilterEnabled) {
            conceptFilterPattern = null;
            log.info("Concept filtering is disabled (concept.filter.enabled=false)");
            return;
        }

        if (conceptFilterRegex != null && !conceptFilterRegex.trim().isEmpty()) {
            try {
                conceptFilterPattern = Pattern.compile(conceptFilterRegex);
                log.info("Concept filter enabled with regex pattern: {}", conceptFilterRegex);
            } catch (Exception e) {
                log.error("Failed to compile concept filter regex '{}': {}. Filtering disabled.",
                        conceptFilterRegex, e.getMessage());
                conceptFilterPattern = null;
            }
        } else {
            conceptFilterPattern = null;
            log.warn("Concept filtering is enabled but no regex pattern configured (concept.filter.regex is empty). Filtering disabled.");
        }
    }

    public boolean shouldFilterConcept(String identifier) {
        if (identifier == null || conceptFilterPattern == null) {
            return false;
        }
        return conceptFilterPattern.matcher(identifier).matches();
    }

    public boolean isConceptFiltered(String nameOrIdentifier, Map<String, String> nameToIdentifierMap) {
        if (nameOrIdentifier == null || conceptFilterPattern == null) {
            return false;
        }

        if (DataTypeConverter.isUri(nameOrIdentifier)) {
            return shouldFilterConcept(nameOrIdentifier);
        }

        String identifier = nameToIdentifierMap.get(nameOrIdentifier);
        return shouldFilterConcept(Objects.requireNonNullElse(identifier, nameOrIdentifier));
    }

    public Map<String, String> buildNameToIdentifierMap(OntologyData ontologyData) {
        Map<String, String> nameToIdentifierMap = new HashMap<>();

        for (ClassData classData : ontologyData.getClasses()) {
            if (classData.getName() != null && classData.getIdentifier() != null) {
                nameToIdentifierMap.put(classData.getName(), classData.getIdentifier());
            }
        }

        for (PropertyData propertyData : ontologyData.getProperties()) {
            if (propertyData.getName() != null && propertyData.getIdentifier() != null) {
                nameToIdentifierMap.put(propertyData.getName(), propertyData.getIdentifier());
            }
        }

        for (RelationshipData relationshipData : ontologyData.getRelationships()) {
            if (relationshipData.getName() != null && relationshipData.getIdentifier() != null) {
                nameToIdentifierMap.put(relationshipData.getName(), relationshipData.getIdentifier());
            }
        }

        log.debug("Built name-to-identifier mapping with {} entries", nameToIdentifierMap.size());
        return nameToIdentifierMap;
    }

    public Set<String> buildFilteredConceptSet(OntologyData ontologyData, FilterStatistics stats) {
        Set<String> filteredIdentifiers = new HashSet<>();

        if (conceptFilterPattern == null) {
            return filteredIdentifiers;
        }

        for (ClassData classData : ontologyData.getClasses()) {
            if (shouldFilterConcept(classData.getIdentifier())) {
                filteredIdentifiers.add(classData.getIdentifier());
                stats.filteredConceptIdentifiers.add(classData.getIdentifier());
                stats.filteredConceptNames.add(classData.getName());
            }
        }

        for (PropertyData propertyData : ontologyData.getProperties()) {
            if (shouldFilterConcept(propertyData.getIdentifier())) {
                filteredIdentifiers.add(propertyData.getIdentifier());
                stats.filteredConceptIdentifiers.add(propertyData.getIdentifier());
                stats.filteredConceptNames.add(propertyData.getName());
            }
        }

        for (RelationshipData relationshipData : ontologyData.getRelationships()) {
            if (shouldFilterConcept(relationshipData.getIdentifier())) {
                filteredIdentifiers.add(relationshipData.getIdentifier());
                stats.filteredConceptIdentifiers.add(relationshipData.getIdentifier());
                stats.filteredConceptNames.add(relationshipData.getName());
            }
        }

        if (!filteredIdentifiers.isEmpty()) {
            log.info("Pre-scan found {} concepts matching filter regex", filteredIdentifiers.size());
        }

        return filteredIdentifiers;
    }
}
