package com.dia.converter.reader.archi;

import com.dia.converter.data.*;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import com.dia.utility.UtilityMethods;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static com.dia.constants.OntologyConstants.*;
import static com.dia.constants.TypeMappings.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;

@Component
@Slf4j
@Getter
public class ArchiReader {

    private final Map<String, String> propertyMapping = new HashMap<>();

    private Document archiDoc;
    private String modelName;

    public OntologyData readArchiFromString(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        int contentLength = content != null ? content.length() : 0;

        log.info("Starting Archi XML parsing: requestId={}, contentLength={}", requestId, contentLength);
        try {
            parseXmlDocument(content);

            OntologyData ontologyData = extractOntologyData();

            log.info("Archi parsing completed successfully: requestId={}, modelName={}", requestId, modelName);
            return ontologyData;

        } catch (FileParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Archi parsing: requestId={}", requestId, e);
            throw new FileParsingException("Neočekávaná chyba při zpracování Archi XML.", e);
        }
    }

    private void parseXmlDocument(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();

            log.debug("Configuring XML parser: requestId={}, namespaceAware=true", requestId);
            factory.setNamespaceAware(true);

            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            log.debug("XML parser security features enabled: requestId={}", requestId);

            DocumentBuilder builder = factory.newDocumentBuilder();
            log.debug("Parsing XML content: requestId={}", requestId);
            archiDoc = builder.parse(new ByteArrayInputStream(Objects.requireNonNull(content).getBytes()));
            log.debug("XML document successfully parsed: requestId={}", requestId);

            log.debug("Building property mappings: requestId={}", requestId);
            buildPropertyMapping();

        } catch (ParserConfigurationException e) {
            log.error("XML parser configuration failed: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new FileParsingException("Během konfigurace XML parseru došlo k chybě.", e);
        } catch (SAXException e) {
            log.error("XML parsing error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new FileParsingException("Při zpracovávání XML došlo k chybě.", e);
        } catch (IOException e) {
            log.error("IO error while parsing XML: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new FileParsingException("Při čtení XML došlo k chybě.", e);
        }
    }

    private OntologyData extractOntologyData() throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.debug("Extracting ontology data from Archi document: requestId={}", requestId);

        if (archiDoc == null) {
            throw new ConversionException("Dokument ke zpracování nebyl nalezen.");
        }

        extractModelName();

        VocabularyMetadata metadata = extractVocabularyMetadata();
        List<ClassData> classes = extractClasses();
        List<PropertyData> properties = extractProperties();
        List<RelationshipData> relationships = extractRelationships();

        log.info("Extracted {} classes, {} properties, {} relationships",
                classes.size(), properties.size(), relationships.size());

        return OntologyData.builder()
                .vocabularyMetadata(metadata)
                .classes(classes)
                .properties(properties)
                .relationships(relationships)
                .build();
    }

    private void extractModelName() {
        NodeList nameNodes = archiDoc.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nameNodes.getLength() > 0) {
            modelName = nameNodes.item(0).getTextContent();
            log.debug("Model name extracted: {}", modelName);
        } else {
            modelName = "Untitled Model";
            log.warn("No model name found, using default: {}", modelName);
        }
    }

    private VocabularyMetadata extractVocabularyMetadata() {
        VocabularyMetadata metadata = new VocabularyMetadata();
        metadata.setName(modelName);

        Map<String, String> modelProperties = getModelProperties();

        String description = modelProperties.getOrDefault(POPIS, "");
        if (!description.isEmpty()) {
            metadata.setDescription(description);
        }

        String namespace = extractNamespaceFromProperties(modelProperties);
        if (namespace != null) {
            metadata.setNamespace(namespace);
        }

        log.debug("Extracted vocabulary metadata: name={}, namespace={}",
                metadata.getName(), metadata.getNamespace());
        return metadata;
    }

    private String extractNamespaceFromProperties(Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.equals(LOKALNI_KATALOG) ||
                    (key.contains("adresa") && (key.contains("lokální") || key.contains("lokálního")))) {
                String ns = entry.getValue();
                if (ns != null && !ns.isEmpty() && UtilityMethods.isValidUrl(ns)) {
                    return ns;
                }
            }
        }
        return null;
    }

    private List<ClassData> extractClasses() {
        List<ClassData> classes = new ArrayList<>();
        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");

        log.debug("Processing {} elements for class extraction", elements.getLength());

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String name = getElementName(element);

            if ("Subjekt".equals(name) || "Objekt".equals(name) || "Vlastnost".equals(name)) {
                continue;
            }

            String id = element.getAttribute(IDENT);
            Map<String, String> elementProperties = getElementProperties(element);

            String elementType = elementProperties.getOrDefault(TYP, "").trim();
            if (isClassType(elementType)) {
                ClassData classData = createClassData(name, id, elementType, elementProperties);
                if (classData.hasValidData()) {
                    classes.add(classData);
                    log.debug("Extracted class: {}", classData.getName());
                }
            }
        }

        processInheritanceRelationships(classes);

        log.debug("Extracted {} classes", classes.size());
        return classes;
    }

    private void processInheritanceRelationships(List<ClassData> classes) {
        Map<String, ClassData> classMap = new HashMap<>();
        Map<String, String> idToNameMap = new HashMap<>();

        for (ClassData classData : classes) {
            classMap.put(classData.getName(), classData);
        }

        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String id = element.getAttribute(IDENT);
            String name = getElementName(element);
            idToNameMap.put(id, name);
        }

        NodeList relationships = archiDoc.getElementsByTagNameNS(ARCHI_NS, "relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            String type = relationship.getAttribute("xsi:type");

            if ("Specialization".equals(type)) {
                String sourceId = relationship.getAttribute("source");
                String targetId = relationship.getAttribute("target");

                String childName = idToNameMap.get(sourceId);
                String parentName = idToNameMap.get(targetId);

                if (childName != null && parentName != null) {
                    ClassData childClass = classMap.get(childName);
                    if (childClass != null) {
                        childClass.setSuperClass(parentName);
                        log.debug("Set inheritance: {} extends {}", childName, parentName);
                    }
                }
            }
        }
    }

    private ClassData createClassData(String name, String id, String elementType, Map<String, String> properties) {
        ClassData classData = new ClassData();
        classData.setName(name);
        classData.setIdentifier(id);

        classData.setType(mapArchiType(elementType));

        classData.setDescription(properties.get(POPIS));
        classData.setDefinition(properties.get(DEFINICE));
        classData.setSource(properties.get(ZDROJ));
        classData.setRelatedSource(properties.get(SOUVISEJICI_ZDROJ));
        classData.setAlternativeName(properties.get(ALTERNATIVNI_NAZEV));

        classData.setAgendaCode(properties.get(AGENDA));
        classData.setAgendaSystemCode(properties.get(AIS));

        return classData;
    }

    private List<PropertyData> extractProperties() {
        List<PropertyData> properties = new ArrayList<>();
        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");

        log.debug("Processing {} elements for property extraction", elements.getLength());

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String name = getElementName(element);

            if ("Subjekt".equals(name) || "Objekt".equals(name) || "Vlastnost".equals(name)) {
                continue;
            }

            String id = element.getAttribute(IDENT);
            Map<String, String> elementProperties = getElementProperties(element);

            String elementType = elementProperties.getOrDefault(TYP, "").trim();
            if (isPropertyType(elementType)) {
                PropertyData propertyData = createPropertyData(name, id, elementProperties);
                if (propertyData.hasValidData()) {
                    properties.add(propertyData);
                    log.debug("Extracted property: {}", propertyData.getName());
                }
            }
        }

        processPropertyDomains(properties);

        log.debug("Extracted {} properties", properties.size());
        return properties;
    }

    private void processPropertyDomains(List<PropertyData> properties) {
        Map<String, PropertyData> propertyMap = new HashMap<>();
        Map<String, String> idToNameMap = new HashMap<>();

        for (PropertyData propertyData : properties) {
            propertyMap.put(propertyData.getName(), propertyData);
        }

        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String id = element.getAttribute(IDENT);
            String name = getElementName(element);
            idToNameMap.put(id, name);
        }

        NodeList relationships = archiDoc.getElementsByTagNameNS(ARCHI_NS, "relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            String type = relationship.getAttribute("xsi:type");

            if ("Composition".equals(type)) {
                String sourceId = relationship.getAttribute("source");
                String targetId = relationship.getAttribute("target");

                String sourceName = idToNameMap.get(sourceId);
                String targetName = idToNameMap.get(targetId);

                PropertyData property = propertyMap.get(targetName);
                if (property != null && sourceName != null) {
                    property.setDomain(sourceName);
                    log.debug("Set property domain: {} belongs to {}", targetName, sourceName);
                }
            }
        }
    }

    private PropertyData createPropertyData(String name, String id, Map<String, String> properties) {
        PropertyData propertyData = new PropertyData();
        propertyData.setName(name);
        propertyData.setIdentifier(id);

        propertyData.setDescription(properties.get(POPIS));
        propertyData.setDefinition(properties.get(DEFINICE));
        propertyData.setSource(properties.get(ZDROJ));
        propertyData.setRelatedSource(properties.get(SOUVISEJICI_ZDROJ));
        propertyData.setAlternativeName(properties.get(ALTERNATIVNI_NAZEV));
        propertyData.setDataType(properties.get(DATOVY_TYP));

        propertyData.setSharedInPPDF(properties.get(JE_PPDF));
        propertyData.setIsPublic(properties.get(JE_VEREJNY));
        propertyData.setPrivacyProvision(properties.get(USTANOVENI_NEVEREJNOST));
        propertyData.setSharingMethod(properties.get(ZPUSOB_SDILENI));
        propertyData.setAcquisitionMethod(properties.get(ZPUSOB_ZISKANI));
        propertyData.setContentType(properties.get(TYP_OBSAHU));

        return propertyData;
    }

    private List<RelationshipData> extractRelationships() {
        List<RelationshipData> relationships = new ArrayList<>();
        NodeList relationshipElements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "relationship");

        log.debug("Processing {} relationship elements", relationshipElements.getLength());

        Map<String, String> idToNameMap = new HashMap<>();
        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String id = element.getAttribute(IDENT);
            String name = getElementName(element);
            idToNameMap.put(id, name);
        }

        for (int i = 0; i < relationshipElements.getLength(); i++) {
            Element relationship = (Element) relationshipElements.item(i);
            String type = relationship.getAttribute("xsi:type");

            if ("Association".equals(type)) {
                RelationshipData relationshipData = createRelationshipData(relationship, idToNameMap);
                if (relationshipData != null && relationshipData.hasValidData()) {
                    relationships.add(relationshipData);
                    log.debug("Extracted relationship: {}", relationshipData.getName());
                }
            }
        }

        log.debug("Extracted {} relationships", relationships.size());
        return relationships;
    }

    private RelationshipData createRelationshipData(Element relationship, Map<String, String> idToNameMap) {
        String name = getRelationshipName(relationship);
        if (name == null || name.isEmpty()) {
            return null;
        }

        String id = relationship.getAttribute(IDENT);
        String sourceId = relationship.getAttribute("source");
        String targetId = relationship.getAttribute("target");

        String sourceName = idToNameMap.get(sourceId);
        String targetName = idToNameMap.get(targetId);

        if (sourceName == null || targetName == null) {
            log.warn("Could not resolve source or target names for relationship: {}", name);
            return null;
        }

        RelationshipData relationshipData = new RelationshipData();
        relationshipData.setName(name);
        relationshipData.setIdentifier(id);
        relationshipData.setDomain(sourceName);
        relationshipData.setRange(targetName);

        Map<String, String> properties = getElementProperties(relationship);
        relationshipData.setDescription(properties.get(POPIS));
        relationshipData.setDefinition(properties.get(DEFINICE));
        relationshipData.setSource(properties.get(ZDROJ));
        relationshipData.setRelatedSource(properties.get(SOUVISEJICI_ZDROJ));
        relationshipData.setAlternativeName(properties.get(ALTERNATIVNI_NAZEV));

        return relationshipData;
    }

    private void buildPropertyMapping() {
        propertyMapping.clear();
        NodeList propertyDefs = archiDoc.getElementsByTagNameNS(ARCHI_NS, "propertyDefinition");

        for (int i = 0; i < propertyDefs.getLength(); i++) {
            Element propDef = (Element) propertyDefs.item(i);
            String propId = propDef.getAttribute(IDENT);
            String propName = extractPropName(propDef);

            if (propName == null) continue;

            propertyMapping.put(propId, propName);
            mapStandardizedLabel(propId, propName);
        }

        logPropertyMappings();
    }

    private void mapStandardizedLabel(String propId, String propName) {
        if ("související zdroj".equals(propName)) {
            propertyMapping.put(propId, SOUVISEJICI_ZDROJ);
            return;
        }
        if ("zdroj".equals(propName)) {
            propertyMapping.put(propId, ZDROJ);
            return;
        }

        Map<String, String> labelPatterns = Map.of(
                "popis", POPIS,
                "definice", DEFINICE,
                "identifikátor", IDENTIFIKATOR,
                "ustanovení dokládající neveřejnost", USTANOVENI_NEVEREJNOST,
                "agenda", AGENDA,
                "agendový informační systém", AIS,
                "je pojem sdílen v PPDF?", JE_PPDF,
                "je pojem veřejný?", JE_VEREJNY,
                "alternativní název", ALTERNATIVNI_NAZEV,
                "datový typ", DATOVY_TYP
        );

        for (Map.Entry<String, String> pattern : labelPatterns.entrySet()) {
            if (propName.contains(pattern.getKey())) {
                propertyMapping.put(propId, pattern.getValue());
                break;
            }
        }

        if (propName.contains("typ")) {
            propertyMapping.put(propId, TYP);
        } else if (propName.contains("adresa lokálního katalogu dat")) {
            propertyMapping.put(propId, LOKALNI_KATALOG);
        }
    }

    private void logPropertyMappings() {
        log.debug("Property mappings built:");
        for (Map.Entry<String, String> entry : propertyMapping.entrySet()) {
            log.debug("  {} -> {}", entry.getKey(), entry.getValue());
        }
    }

    private Map<String, String> getModelProperties() {
        Map<String, String> modelProperties = new HashMap<>();
        Element modelPropertiesElement = findModelPropertiesElement();

        if (modelPropertiesElement != null) {
            extractPropertiesFromElement(modelPropertiesElement, modelProperties);
        }

        return modelProperties;
    }

    private Element findModelPropertiesElement() {
        NodeList propertiesNodes = archiDoc.getElementsByTagNameNS(ARCHI_NS, "properties");
        for (int p = 0; p < propertiesNodes.getLength(); p++) {
            Element propertiesElement = (Element) propertiesNodes.item(p);
            if (propertiesElement.getParentNode().getNodeName().endsWith("model")) {
                return propertiesElement;
            }
        }
        return null;
    }

    private void extractPropertiesFromElement(Element propertiesElement, Map<String, String> modelProperties) {
        NodeList propertyNodes = propertiesElement.getElementsByTagNameNS(ARCHI_NS, "property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element property = (Element) propertyNodes.item(i);
            processProperty(property, modelProperties);
        }
    }

    private void processProperty(Element property, Map<String, String> modelProperties) {
        String propRef = property.getAttribute("propertyDefinitionRef");
        String propName = propertyMapping.getOrDefault(propRef, propRef);
        String value = getPropertyValue(property);

        if (value != null && !value.isEmpty()) {
            modelProperties.put(propName, value);
        }
    }

    private String getPropertyValue(Element property) {
        NodeList valueNodes = property.getElementsByTagNameNS(ARCHI_NS, "value");
        if (valueNodes.getLength() > 0) {
            return valueNodes.item(0).getTextContent();
        }
        return null;
    }

    private String extractPropName(Element propDef) {
        NodeList nodeList = propDef.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    private String getElementName(Element element) {
        NodeList nameNodes = element.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nameNodes.getLength() > 0) {
            return nameNodes.item(0).getTextContent();
        }
        return "";
    }

    private String getRelationshipName(Element relationship) {
        NodeList nameNodes = relationship.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nameNodes.getLength() > 0) {
            return nameNodes.item(0).getTextContent();
        }
        return "";
    }

    private Map<String, String> getElementProperties(Element element) {
        Map<String, String> result = new HashMap<>();
        NodeList propertiesNodes = element.getElementsByTagNameNS(ARCHI_NS, "properties");

        if (propertiesNodes.getLength() > 0) {
            Element propertiesElement = (Element) propertiesNodes.item(0);
            extractPropertiesFromElement(propertiesElement, result);
        }

        processNameNodesForLabels(element, result);
        return result;
    }

    private void processNameNodesForLabels(Element element, Map<String, String> result) {
        getNameNodes(element, result, log);
    }

    public static void getNameNodes(Element element, Map<String, String> result, Logger log) {
        NodeList nameNodes = element.getElementsByTagNameNS(ARCHI_NS, "name");
        for (int i = 0; i < nameNodes.getLength(); i++) {
            Element nameElement = (Element) nameNodes.item(i);
            String lang = nameElement.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");

            if (!lang.isEmpty() && !lang.equals("cs")) {
                result.put("lang=" + lang, nameElement.getTextContent());
                log.debug("Found {} name for multilingual labeling: {}", lang, nameElement.getTextContent());
            }
        }
    }
}