package com.dia.conversion.transformer;

import com.dia.conversion.data.ClassData;
import com.dia.conversion.data.PropertyData;
import com.dia.conversion.data.RelationshipData;
import com.dia.constants.FormatConstants;
import com.dia.utility.DataTypeConverter;
import com.dia.utility.URIGenerator;
import com.dia.utility.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.util.List;

import static com.dia.constants.VocabularyConstants.*;
import static com.dia.constants.ExportConstants.Common.DEFAULT_LANG;

/**
 * Handles data governance metadata processing for ontology resources.
 * This includes:
 * - Public/private data classification
 * - PPDF metadata
 * - Agenda and AIS codes
 * - Privacy provisions
 * - Source field processing (legislative and non-legislative)
 * - Governance properties (sharing method, acquisition method, content type)
 */
@Slf4j
public class DataGovernanceProcessor {

    private final OntModel ontModel;
    private final URIGenerator uriGenerator;

    private static final String CONTENT_TYPE = "content-type";
    private static final String ACQUISITION_METHOD = "acquisition-method";
    private static final String SHARING_METHOD = "sharing-method";
    private static final String OPENDATA_ESEL = "https://opendata.eselpoint.cz/esel-esb/";

    public DataGovernanceProcessor(OntModel ontModel, URIGenerator uriGenerator) {
        this.ontModel = ontModel;
        this.uriGenerator = uriGenerator;
    }

    public void addClassDataGovernanceMetadata(Resource classResource, ClassData classData) {
        addClassPublicOrNonPublicData(classResource, classData);
        handleGovernanceProperty(classResource, classData.getSharingMethod(), SHARING_METHOD);
        handleGovernanceProperty(classResource, classData.getAcquisitionMethod(), ACQUISITION_METHOD);
        handleGovernanceProperty(classResource, classData.getContentType(), CONTENT_TYPE);
    }

    private void addClassPublicOrNonPublicData(Resource classResource, ClassData classData) {
        String isPublicValue = classData.getIsPublic();
        String privacyProvision = classData.getPrivacyProvision();

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            handleClassNonPublicData(classResource, classData, privacyProvision);
            return;
        }

        if (isPublicValue != null && !isPublicValue.trim().isEmpty()) {
            handleClassPublicData(classResource, classData, isPublicValue, privacyProvision);
        }
    }

    private void handleClassNonPublicData(Resource classResource, ClassData classData, String privacyProvision) {
        classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + NEVEREJNY_UDAJ));
        log.debug("Added non-public data annotation and RDF type for class: {}", classData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddClassPrivacyProvision(classResource, classData, privacyProvision);
        }
    }

    private void handleClassPublicData(Resource classResource, ClassData classData, String isPublicValue, String privacyProvision) {
        if (UtilityMethods.isBooleanValue(isPublicValue)) {
            Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

            if (Boolean.TRUE.equals(isPublic)) {
                if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                    log.warn("Class '{}' marked as public but has privacy provision '{}' - treating as non-public",
                            classData.getName(), privacyProvision);
                    handleClassNonPublicData(classResource, classData, privacyProvision);
                } else {
                    classResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + VEREJNY_UDAJ));
                    log.debug("Added public data annotation and RDF type for class: {}", classData.getName());
                }
            } else {
                handleClassNonPublicData(classResource, classData, privacyProvision);
            }
        } else {
            log.warn("Unrecognized boolean value for public class property: '{}' for class '{}'",
                    isPublicValue, classData.getName());
        }
    }

    private void validateAndAddClassPrivacyProvision(Resource classResource, ClassData classData, String provision) {
        String trimmedProvision = provision.trim();

        if (UtilityMethods.containsEliPattern(trimmedProvision)) {
            String eliPart = UtilityMethods.extractEliPart(trimmedProvision);
            if (eliPart != null) {
                String transformedProvision = OPENDATA_ESEL + eliPart;
                Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + USTANOVENI_NEVEREJNOST);

                if (DataTypeConverter.isUri(transformedProvision)) {
                    classResource.addProperty(provisionProperty, ontModel.createResource(transformedProvision));
                    log.debug("Added privacy provision as URI for class '{}': {} -> {}",
                            classData.getName(), trimmedProvision, transformedProvision);
                } else {
                    DataTypeConverter.addTypedProperty(classResource, provisionProperty, transformedProvision, null, ontModel);
                    log.debug("Added privacy provision as literal for class '{}': {} -> {}",
                            classData.getName(), trimmedProvision, transformedProvision);
                }
            } else {
                log.warn("Failed to extract ELI part from privacy provision for class '{}': '{}'",
                        classData.getName(), trimmedProvision);
            }
        } else {
            log.debug("Privacy provision does not contain ELI pattern for class '{}': '{}' - skipping",
                    classData.getName(), trimmedProvision);
        }
    }

    public void addPropertyDataGovernanceMetadata(Resource propertyResource, PropertyData propertyData) {
        addPropertyPublicOrNonPublicData(propertyResource, propertyData);
        handleGovernanceProperty(propertyResource, propertyData.getSharingMethod(), SHARING_METHOD);
        handleGovernanceProperty(propertyResource, propertyData.getAcquisitionMethod(), ACQUISITION_METHOD);
        handleGovernanceProperty(propertyResource, propertyData.getContentType(), CONTENT_TYPE);
    }

    private void addPropertyPublicOrNonPublicData(Resource propertyResource, PropertyData propertyData) {
        String isPublicValue = propertyData.getIsPublic();
        String privacyProvision = propertyData.getPrivacyProvision();

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            handlePropertyNonPublicData(propertyResource, propertyData, privacyProvision);
            return;
        }

        if (isPublicValue != null && !isPublicValue.trim().isEmpty()) {
            handlePropertyPublicData(propertyResource, propertyData, isPublicValue, privacyProvision);
        }
    }

    private void handlePropertyNonPublicData(Resource propertyResource, PropertyData propertyData, String privacyProvision) {
        propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + NEVEREJNY_UDAJ));
        log.debug("Added non-public data annotation and RDF type for property: {}", propertyData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddPropertyPrivacyProvision(propertyResource, propertyData, privacyProvision);
        }
    }

    private void handlePropertyPublicData(Resource propertyResource, PropertyData propertyData, String isPublicValue, String privacyProvision) {
        if (UtilityMethods.isBooleanValue(isPublicValue)) {
            Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

            if (Boolean.TRUE.equals(isPublic)) {
                if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                    log.warn("Property '{}' marked as public but has privacy provision '{}' - treating as non-public",
                            propertyData.getName(), privacyProvision);
                    handlePropertyNonPublicData(propertyResource, propertyData, privacyProvision);
                } else {
                    propertyResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + VEREJNY_UDAJ));
                    log.debug("Added public data annotation and RDF type for property: {}", propertyData.getName());
                }
            } else {
                handlePropertyNonPublicData(propertyResource, propertyData, privacyProvision);
            }
        } else {
            log.warn("Unrecognized boolean value for public property property: '{}' for property '{}'",
                    isPublicValue, propertyData.getName());
        }
    }

    private void validateAndAddPropertyPrivacyProvision(Resource propertyResource, PropertyData propertyData, String provision) {
        String trimmedProvision = provision.trim();

        if (UtilityMethods.containsEliPattern(trimmedProvision)) {
            String eliPart = UtilityMethods.extractEliPart(trimmedProvision);
            if (eliPart != null) {
                String transformedProvision = OPENDATA_ESEL + eliPart;
                Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + USTANOVENI_NEVEREJNOST);

                if (DataTypeConverter.isUri(transformedProvision)) {
                    propertyResource.addProperty(provisionProperty, ontModel.createResource(transformedProvision));
                    log.debug("Added privacy provision as URI for property '{}': {} -> {}",
                            propertyData.getName(), trimmedProvision, transformedProvision);
                } else {
                    DataTypeConverter.addTypedProperty(propertyResource, provisionProperty, transformedProvision, null, ontModel);
                    log.debug("Added privacy provision as literal for property '{}': {} -> {}",
                            propertyData.getName(), trimmedProvision, transformedProvision);
                }
            } else {
                log.warn("Failed to extract ELI part from privacy provision for property '{}': '{}'",
                        propertyData.getName(), trimmedProvision);
            }
        } else {
            log.debug("Privacy provision does not contain ELI pattern for property '{}': '{}' - skipping",
                    propertyData.getName(), trimmedProvision);
        }
    }


    public void addRelationshipDataGovernanceMetadata(Resource relationshipResource, RelationshipData relationshipData) {
        addRelationshipPublicOrNonPublicData(relationshipResource, relationshipData);
        handleGovernanceProperty(relationshipResource, relationshipData.getSharingMethod(), SHARING_METHOD);
        handleGovernanceProperty(relationshipResource, relationshipData.getAcquisitionMethod(), ACQUISITION_METHOD);
        handleGovernanceProperty(relationshipResource, relationshipData.getContentType(), CONTENT_TYPE);
    }

    private void addRelationshipPublicOrNonPublicData(Resource relationshipResource, RelationshipData relationshipData) {
        String isPublicValue = relationshipData.getIsPublic();
        String privacyProvision = relationshipData.getPrivacyProvision();

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            handleRelationshipNonPublicData(relationshipResource, relationshipData, privacyProvision);
            return;
        }

        if (isPublicValue != null && !isPublicValue.trim().isEmpty()) {
            handleRelationshipPublicData(relationshipResource, relationshipData, isPublicValue, privacyProvision);
        }
    }

    private void handleRelationshipNonPublicData(Resource relationshipResource, RelationshipData relationshipData, String privacyProvision) {
        relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + NEVEREJNY_UDAJ));
        log.debug("Added non-public data annotation and RDF type for relationship: {}", relationshipData.getName());

        if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
            validateAndAddRelationshipPrivacyProvision(relationshipResource, relationshipData, privacyProvision);
        }
    }

    private void handleRelationshipPublicData(Resource relationshipResource, RelationshipData relationshipData, String isPublicValue, String privacyProvision) {
        if (UtilityMethods.isBooleanValue(isPublicValue)) {
            Boolean isPublic = UtilityMethods.normalizeCzechBoolean(isPublicValue);

            if (Boolean.TRUE.equals(isPublic)) {
                if (privacyProvision != null && !privacyProvision.trim().isEmpty()) {
                    log.warn("Relationship '{}' marked as public but has privacy provision '{}' - treating as non-public",
                            relationshipData.getName(), privacyProvision);
                    handleRelationshipNonPublicData(relationshipResource, relationshipData, privacyProvision);
                } else {
                    relationshipResource.addProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + VEREJNY_UDAJ));
                    log.debug("Added public data annotation and RDF type for relationship: {}", relationshipData.getName());
                }
            } else {
                handleRelationshipNonPublicData(relationshipResource, relationshipData, privacyProvision);
            }
        } else {
            log.warn("Unrecognized boolean value for public relationship property: '{}' for relationship '{}'",
                    isPublicValue, relationshipData.getName());
        }
    }

    private void validateAndAddRelationshipPrivacyProvision(Resource relationshipResource, RelationshipData relationshipData, String provision) {
        String trimmedProvision = provision.trim();

        if (UtilityMethods.containsEliPattern(trimmedProvision)) {
            String eliPart = UtilityMethods.extractEliPart(trimmedProvision);
            if (eliPart != null) {
                String transformedProvision = OPENDATA_ESEL + eliPart;
                Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + USTANOVENI_NEVEREJNOST);

                if (DataTypeConverter.isUri(transformedProvision)) {
                    relationshipResource.addProperty(provisionProperty, ontModel.createResource(transformedProvision));
                    log.debug("Added privacy provision as URI for relationship '{}': {} -> {}",
                            relationshipData.getName(), trimmedProvision, transformedProvision);
                } else {
                    DataTypeConverter.addTypedProperty(relationshipResource, provisionProperty, transformedProvision, null, ontModel);
                    log.debug("Added privacy provision as literal for relationship '{}': {} -> {}",
                            relationshipData.getName(), trimmedProvision, transformedProvision);
                }
            } else {
                log.warn("Failed to extract ELI part from privacy provision for relationship '{}': '{}'",
                        relationshipData.getName(), trimmedProvision);
            }
        } else {
            log.debug("Privacy provision does not contain ELI pattern for relationship '{}': '{}' - skipping",
                    relationshipData.getName(), trimmedProvision);
        }
    }

    public void addPropertyPPDFData(Resource propertyResource, PropertyData propertyData) {
        addPPDFData(propertyResource, propertyData.getSharedInPPDF(), "property");
    }

    public void addRelationshipPPDFData(Resource relationshipResource, RelationshipData relationshipData) {
        addPPDFData(relationshipResource, relationshipData.getSharedInPPDF(), "relationship");
    }

    private void addPPDFData(Resource resource, String sharedInPPDF, String entityType) {
        if (sharedInPPDF != null && !sharedInPPDF.trim().isEmpty()) {
            Property ppdfProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + JE_PPDF);

            if (UtilityMethods.isBooleanValue(sharedInPPDF)) {
                Boolean boolValue = UtilityMethods.normalizeCzechBoolean(sharedInPPDF);
                DataTypeConverter.addTypedProperty(resource, ppdfProperty,
                        boolValue.toString(), null, ontModel);
                log.debug("Added normalized PPDF boolean for {}: {} -> {}", entityType, sharedInPPDF, boolValue);
            } else {
                log.warn("Unrecognized boolean value for PPDF {} property: '{}'", entityType, sharedInPPDF);
                DataTypeConverter.addTypedProperty(resource, ppdfProperty, sharedInPPDF, null, ontModel);
            }
        }
    }

    public void addAgenda(Resource resource, String agendaCode, String entityType, String entityName) {
        if (agendaCode != null && !agendaCode.trim().isEmpty()) {
            String trimmedCode = agendaCode.trim();

            if (UtilityMethods.isValidAgendaValue(trimmedCode)) {
                String transformedAgenda = UtilityMethods.transformAgendaValue(trimmedCode);
                Property agendaProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + AGENDA);

                if (DataTypeConverter.isUri(transformedAgenda)) {
                    resource.addProperty(agendaProperty, ontModel.createResource(transformedAgenda));
                    log.debug("Added valid agenda as URI for {}: {} -> {}", entityType, trimmedCode, transformedAgenda);
                } else {
                    DataTypeConverter.addTypedProperty(resource, agendaProperty, transformedAgenda, null, ontModel);
                    log.debug("Added valid agenda as literal for {}: {} -> {}", entityType, trimmedCode, transformedAgenda);
                }
            } else {
                log.warn("Invalid agenda code '{}' for {} '{}' - skipping", trimmedCode, entityType, entityName);
            }
        }
    }

    public void addAgendaInformationSystem(Resource resource, String aisCode, String entityType, String entityName) {
        if (aisCode != null && !aisCode.trim().isEmpty()) {
            String trimmedCode = aisCode.trim();

            if (UtilityMethods.isValidAISValue(trimmedCode)) {
                String transformedAIS = UtilityMethods.transformAISValue(trimmedCode);
                Property aisProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + AIS);

                if (DataTypeConverter.isUri(transformedAIS)) {
                    resource.addProperty(aisProperty, ontModel.createResource(transformedAIS));
                    log.debug("Added valid AIS as URI for {}: {} -> {}", entityType, trimmedCode, transformedAIS);
                } else {
                    DataTypeConverter.addTypedProperty(resource, aisProperty, transformedAIS, null, ontModel);
                    log.debug("Added valid AIS as literal for {}: {} -> {}", entityType, trimmedCode, transformedAIS);
                }
            } else {
                log.warn("Invalid AIS code '{}' for {} '{}' - skipping", trimmedCode, entityType, entityName);
            }
        }
    }

    private void handleGovernanceProperty(Resource resource, String value, String propertyType) {
        if (value == null || value.trim().isEmpty()) {
            log.debug("Skipping empty governance property '{}' for resource: {}", propertyType, resource.getLocalName());
            return;
        }

        List<String> allowedValues = List.of("veřejně přístupné", "poskytované na žádost", "nesdílené", "zpřístupňované pro výkon agendy", "základních registrů", "jiných agend", "vlastní", "provozní", "identifikační", "evidenční", "statistické");
        if (!allowedValues.contains(value.toLowerCase())) {
            log.debug("Skipping unsupported governance property value '{}' for resource: {}", propertyType, resource.getLocalName());
            return;
        }

        String propertyConstant = getGovernancePropertyConstant(propertyType);

        log.debug("Using constant '{}' for governance property type '{}'", propertyConstant, propertyType);
        addGovernanceProperty(resource, value, propertyConstant);
    }

    private String getGovernancePropertyConstant(String propertyType) {
        return switch (propertyType) {
            case SHARING_METHOD -> (OFN_NAMESPACE + ZPUSOB_SDILENI);
            case ACQUISITION_METHOD -> (OFN_NAMESPACE + ZPUSOB_ZISKANI);
            case CONTENT_TYPE -> (OFN_NAMESPACE + TYP_OBSAHU);
            default -> {
                log.warn("Unknown governance property type: {}", propertyType);
                yield null;
            }
        };
    }

    private void addGovernanceProperty(Resource resource, String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String trimmedValue = value.trim();
        Property property = ontModel.createProperty(propertyName);

        String governanceIRI = transformToGovernanceIRI(propertyName, trimmedValue);

        if (governanceIRI != null) {
            resource.addProperty(property, ontModel.createResource(governanceIRI));
            log.debug("Added governance property {} as governance IRI: {} -> {}", propertyName, trimmedValue, governanceIRI);
        } else if (DataTypeConverter.isUri(trimmedValue)) {
            resource.addProperty(property, ontModel.createResource(trimmedValue));
            log.debug("Added governance property {} as URI: {}", propertyName, trimmedValue);
        } else {
            DataTypeConverter.addTypedProperty(resource, property, trimmedValue, null, ontModel);
            log.debug("Added governance property {} as typed literal: {}", propertyName, trimmedValue);
        }
    }

    private String transformToGovernanceIRI(String propertyName, String value) {
        if (propertyName == null || value == null) {
            return null;
        }

        String sanitizedValue = UtilityMethods.sanitizeForIRI(value);

        return switch (propertyName) {
            case OFN_NAMESPACE + TYP_OBSAHU ->
                    "https://data.dia.gov.cz/zdroj/číselníky/typy-obsahu-údajů/položky/" + sanitizedValue;
            case OFN_NAMESPACE + ZPUSOB_SDILENI ->
                    "https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/" + sanitizedValue;
            case OFN_NAMESPACE + ZPUSOB_ZISKANI ->
                    "https://data.dia.gov.cz/zdroj/číselníky/způsoby-získání-údajů/položky/" + sanitizedValue;
            default -> null;
        };
    }

    public void processSourceField(Resource resource, String sourceUrls, boolean isDefining) {
        if (sourceUrls == null || sourceUrls.trim().isEmpty()) {
            log.debug("Skipping empty source field for resource: {}", resource.getLocalName());
            return;
        }

        String trimmedUrls = sourceUrls.trim();
        log.debug("Processing source field for resource {}: '{}'", resource.getLocalName(),
                trimmedUrls.length() > 100 ? trimmedUrls.substring(0, 100) + "..." : trimmedUrls);

        if (trimmedUrls.contains(";")) {
            String[] urls = trimmedUrls.split(";");
            log.debug("Found {} sources separated by semicolons for resource: {}", urls.length, resource.getLocalName());

            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (!url.isEmpty()) {
                    log.debug("Processing source part {}/{}: '{}'", i+1, urls.length, url);
                    processSingleSource(resource, url, isDefining);
                }
            }
        } else {
            processSingleSource(resource, trimmedUrls, isDefining);
        }
    }

    private void processSingleSource(Resource resource, String url, boolean isDefining) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String trimmedUrl = url.trim();
        log.debug("Processing single source for resource {}: '{}'", resource.getLocalName(), trimmedUrl);

        if (UtilityMethods.containsEliPattern(trimmedUrl)) {
            handleEliPart(trimmedUrl, resource, isDefining);
            log.debug("Processed as ELI (legislative) source: {}", trimmedUrl);
        } else {
            handleNonEliPart(trimmedUrl, resource, isDefining);
            log.debug("Processed as non-ELI (non-legislative) source: {}", trimmedUrl);
        }
    }

    private void handleEliPart(String trimmedUrl, Resource resource, boolean isDefining) {
        String eliPart = UtilityMethods.extractEliPart(trimmedUrl);
        if (eliPart != null) {
            String transformedUrl = OPENDATA_ESEL + eliPart;
            String propertyName = isDefining ? DEFINUJICI_USTANOVENI : SOUVISEJICI_USTANOVENI;

            Property provisionProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);

            if (UtilityMethods.isValidUrl(transformedUrl)) {
                resource.addProperty(provisionProperty, ontModel.createResource(transformedUrl));
                log.debug("Added {} as URI: {} -> {}", propertyName, trimmedUrl, transformedUrl);
            } else {
                log.debug("Skipped {}, invalid URL value: {} -> {}", propertyName, trimmedUrl, transformedUrl);
            }
        } else {
            log.warn("Failed to extract ELI part from URL: {}", trimmedUrl);
            handleNonEliPart(trimmedUrl, resource, isDefining);
        }
    }

    private void handleNonEliPart(String trimmedUrl, Resource resource, boolean isDefining) {
        String propertyName = isDefining ? DEFINUJICI_NELEGISLATIVNI_ZDROJ : SOUVISEJICI_NELEGISLATIVNI_ZDROJ;

        Resource digitalDocument = ontModel.createResource();

        digitalDocument.addProperty(RDF.type, ontModel.createResource("https://slovník.gov.cz/generický/digitální-objekty/pojem/digitální-objekt"));

        if (UtilityMethods.isValidUrl(trimmedUrl)) {
            Property schemaUrlProperty = ontModel.createProperty("http://schema.org/url");
            Literal urlLiteral = ontModel.createTypedLiteral(trimmedUrl, "http://www.w3.org/2001/XMLSchema#anyURI");
            digitalDocument.addProperty(schemaUrlProperty, urlLiteral);
            log.debug("Added digital document with schema:url (xsd:anyURI): {}", trimmedUrl);
        } else {
            Property dctermsTitle = ontModel.createProperty("http://purl.org/dc/terms/title");
            digitalDocument.addProperty(dctermsTitle, trimmedUrl, DEFAULT_LANG);
            log.debug("Added digital document with dcterms:title (@cs): {}", trimmedUrl);
        }

        Property nonLegislativeProperty = ontModel.createProperty(uriGenerator.getEffectiveNamespace() + propertyName);
        resource.addProperty(nonLegislativeProperty, digitalDocument);

        log.debug("Added as digital document: {}", propertyName);
    }
}