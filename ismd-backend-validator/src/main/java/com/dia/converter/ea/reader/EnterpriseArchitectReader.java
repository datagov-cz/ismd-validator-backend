package com.dia.converter.ea.reader;

import com.dia.converter.data.*;
import com.dia.exceptions.FileParsingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dia.constants.EnterpriseArchitectConstants.*;

@Component
@Slf4j
public class EnterpriseArchitectReader {

    /**
     * Reads and parses an Enterprise Architect XMI file from multipart file bytes.
     * Automatically detects encoding (windows-1252, utf-8, iso-8859-2).
     *
     * @param fileBytes byte array from multipart file
     * @return parsed OntologyData
     * @throws FileParsingException if parsing fails
     */
    public OntologyData readXmiFromBytes(byte[] fileBytes) throws FileParsingException {
        Document document = parseWithEncodingDetection(fileBytes);
        return parseDocument(document);
    }

    /**
     * Attempts to parse XML content with different encodings.
     */
    private Document parseWithEncodingDetection(byte[] content) throws FileParsingException {
        List<Charset> encodings = Arrays.asList(
                StandardCharsets.UTF_8,
                Charset.forName(WINDOWS_1252),
                Charset.forName(ISO_8859_2)
        );

        Exception lastException = null;

        for (Charset charset : encodings) {
            try {
                log.info("Trying to parse XML with encoding: {}", charset.name());
                String xmlContent = new String(content, charset);

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();

                return builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            } catch (Exception e) {
                log.warn("Failed to parse with encoding {}: {}", charset.name(), e.getMessage());
                lastException = e;
            }
        }

        throw new FileParsingException("Failed to parse XML with any supported encoding", lastException);
    }

    /**
     * Parses the XML document and extracts ontology data.
     */
    private OntologyData parseDocument(Document document) throws FileParsingException {
        Element vocabularyPackage = findVocabularyPackageInMainModel(document);
        if (vocabularyPackage == null) {
            throw new FileParsingException("No package with stereotype 'slovnikyPackage' found in the document");
        }

        String vocabularyPackageId = vocabularyPackage.getAttribute(XMI_ID);
        log.info("Found vocabulary package: {} with ID: {}", vocabularyPackage.getAttribute("name"), vocabularyPackageId);

        VocabularyMetadata vocabularyMetadata = extractVocabularyMetadata(document, vocabularyPackageId);

        Set<String> vocabularyPackageIds = getVocabularyPackageIds(document, vocabularyPackageId);
        log.info("Vocabulary package IDs: {}", vocabularyPackageIds);

        List<ClassData> classes = new ArrayList<>();
        List<PropertyData> properties = new ArrayList<>();
        List<RelationshipData> relationships = new ArrayList<>();

        parseElements(document, vocabularyPackageIds, classes, properties);
        parseConnectors(document, vocabularyPackageIds, relationships);

        log.info("Parsed {} classes, {} properties, {} relationships", classes.size(), properties.size(), relationships.size());

        return OntologyData.builder()
                .vocabularyMetadata(vocabularyMetadata)
                .classes(classes)
                .properties(properties)
                .relationships(relationships)
                .build();
    }

    /**
     * Finds the main vocabulary package in the UML model (not in extension).
     */
    private Element findVocabularyPackageInMainModel(Document document) {
        NodeList umlPackages = document.getElementsByTagName(PACKAGED_ELEMENT);

        for (int i = 0; i < umlPackages.getLength(); i++) {
            Element umlPackage = (Element) umlPackages.item(i);
            if (UML_PACKAGE.equals(umlPackage.getAttribute(XMI_TYPE))) {
                String packageId = umlPackage.getAttribute(XMI_ID);

                Element extensionElement = findExtensionElement(document, packageId);
                if (extensionElement != null) {
                    String stereotype = getStereotype(extensionElement);
                    if (STEREOTYPE_SLOVNIKY_PACKAGE.equals(stereotype)) {
                        return umlPackage;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the extension element that corresponds to a main model element.
     */
    private Element findExtensionElement(Document document, String elementId) {
        NodeList extensionElements = document.getElementsByTagName("element");

        for (int i = 0; i < extensionElements.getLength(); i++) {
            Element element = (Element) extensionElements.item(i);
            if (elementId.equals(element.getAttribute(XMI_IDREF))) {
                return element;
            }
        }
        return null;
    }

    /**
     * Extracts vocabulary metadata from the main package and its extension.
     */
    private VocabularyMetadata extractVocabularyMetadata(Document document, String vocabularyPackageId) {
        VocabularyMetadata metadata = new VocabularyMetadata();

        Element mainPackage = findMainModelElement(document, vocabularyPackageId);
        if (mainPackage != null) {
            metadata.setName(mainPackage.getAttribute("name"));
        }

        Element extensionElement = findExtensionElement(document, vocabularyPackageId);
        if (extensionElement != null) {
            metadata.setDescription(getTagValue(extensionElement, TAG_POPIS));
        }

        String namespace = getTagValue(extensionElement, "namespace");
        if (namespace == null || namespace.trim().isEmpty()) {
            String name = metadata.getName();
            if (name != null && !name.trim().isEmpty()) {
                namespace = "https://data.dia.gov.cz/" +
                        name.toLowerCase();
            }
        }
        metadata.setNamespace(namespace);

        return metadata;
    }

    /**
     * Finds a main model element by its ID.
     */
    private Element findMainModelElement(Document document, String elementId) {
        NodeList elements = document.getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (elementId.equals(element.getAttribute(XMI_ID))) {
                return element;
            }
        }
        return null;
    }

    /**
     * Gets all package IDs that belong to the vocabulary (including sub-packages without slovnikyPackage stereotype).
     */
    private Set<String> getVocabularyPackageIds(Document document, String mainPackageId) {
        Set<String> packageIds = new HashSet<>();
        packageIds.add(mainPackageId);

        addSubPackages(document, mainPackageId, packageIds);

        return packageIds;
    }

    /**
     * Recursively adds sub-packages that don't have slovnikyPackage stereotype.
     */
    private void addSubPackages(Document document, String parentPackageId, Set<String> packageIds) {
        NodeList packages = document.getElementsByTagName(PACKAGED_ELEMENT);

        for (int i = 0; i < packages.getLength(); i++) {
            Element packageElement = (Element) packages.item(i);
            if (UML_PACKAGE.equals(packageElement.getAttribute(XMI_TYPE))) {
                Element parent = (Element) packageElement.getParentNode();
                if (parent != null && parentPackageId.equals(parent.getAttribute(XMI_ID))) {
                    String subPackageId = packageElement.getAttribute(XMI_ID);

                    Element extensionElement = findExtensionElement(document, subPackageId);
                    String stereotype = null;
                    if (extensionElement != null) {
                        stereotype = getStereotype(extensionElement);
                    }

                    if (!STEREOTYPE_SLOVNIKY_PACKAGE.equals(stereotype)) {
                        packageIds.add(subPackageId);
                        addSubPackages(document, subPackageId, packageIds);
                    }
                }
            }
        }
    }

    /**
     * Parses elements and categorizes them into classes and properties.
     */
    private void parseElements(Document document, Set<String> vocabularyPackageIds,
                               List<ClassData> classes, List<PropertyData> properties) {
        NodeList umlClasses = document.getElementsByTagName(PACKAGED_ELEMENT);

        for (int i = 0; i < umlClasses.getLength(); i++) {
            Element umlClass = (Element) umlClasses.item(i);

            if (!isValidElementToProcess(umlClass, vocabularyPackageIds)) {
                continue;
            }

            String elementId = umlClass.getAttribute(XMI_ID);

            parseExtensions(document, elementId, classes, properties, umlClass);
        }
    }

    private boolean isValidElementToProcess(Element umlClass, Set<String> vocabularyPackageIds) {
        if (!UML_CLASS.equals(umlClass.getAttribute(XMI_TYPE))) {
            return false;
        }

        String packageId = getElementPackageId(umlClass);
        return vocabularyPackageIds.contains(packageId);
    }

    /**
     * Parses elements from Extension tag and categorizes them into classes and properties.
     */
    private void parseExtensions(Document document, String elementId, List<ClassData> classes,
                                 List<PropertyData> properties, Element umlClass) {
        Element extensionElement = findExtensionElement(document, elementId);
        if (extensionElement != null) {
            String stereotype = getStereotype(extensionElement);
            log.debug("Found element {} with stereotype: {}", umlClass.getAttribute("name"), stereotype);

            if (STEREOTYPE_TYP_OBJEKTU.equals(stereotype) || STEREOTYPE_TYP_SUBJEKTU.equals(stereotype)) {
                ClassData classData = parseClassData(umlClass, extensionElement, stereotype);
                if (classData.hasValidData()) {
                    classes.add(classData);
                    log.debug("Added class: {}", classData.getName());
                }
            } else if (STEREOTYPE_TYP_VLASTNOSTI.equals(stereotype)) {
                PropertyData propertyData = parsePropertyData(umlClass, extensionElement);
                if (propertyData.hasValidData()) {
                    properties.add(propertyData);
                    log.debug("Added property: {}", propertyData.getName());
                }
            }
        }
    }

    /**
     * Gets the package ID that contains an element.
     */
    private String getElementPackageId(Element element) {
        Element parent = (Element) element.getParentNode();
        while (parent != null) {
            if (UML_PACKAGE.equals(parent.getAttribute(XMI_TYPE))) {
                return parent.getAttribute(XMI_ID);
            }
            parent = (Element) parent.getParentNode();
        }
        return null;
    }

    /**
     * Parses class data from UML element and extension element.
     */
    private ClassData parseClassData(Element umlElement, Element extensionElement, String stereotype) {
        ClassData classData = new ClassData();

        classData.setName(umlElement.getAttribute("name"));
        classData.setType(STEREOTYPE_TYP_SUBJEKTU.equals(stereotype) ? "Subjekt práva" : "Objekt práva");
        classData.setDescription(getTagValue(extensionElement, TAG_POPIS));
        classData.setDefinition(getTagValue(extensionElement, TAG_DEFINICE));
        classData.setSource(getTagValue(extensionElement, TAG_ZDROJ));
        classData.setRelatedSource(getTagValue(extensionElement, TAG_SOUVISEJICI_ZDROJ));
        classData.setAlternativeName(getTagValue(extensionElement, TAG_ALTERNATIVNI_NAZEV));
        classData.setEquivalentConcept(getTagValue(extensionElement, TAG_EKVIVALENTNI_POJEM));
        classData.setIdentifier(getTagValue(extensionElement, TAG_IDENTIFIKATOR));
        classData.setAgendaCode(getTagValue(extensionElement, TAG_AGENDA));
        classData.setAgendaSystemCode(getTagValue(extensionElement, TAG_AGENDOVY_INFORMACNI_SYSTEM));

        return classData;
    }

    /**
     * Parses property data from UML element and extension element.
     */
    private PropertyData parsePropertyData(Element umlElement, Element extensionElement) {
        PropertyData propertyData = new PropertyData();

        propertyData.setName(umlElement.getAttribute("name"));
        propertyData.setDescription(getTagValue(extensionElement, TAG_POPIS));
        propertyData.setDefinition(getTagValue(extensionElement, TAG_DEFINICE));
        propertyData.setSource(getTagValue(extensionElement, TAG_ZDROJ));
        propertyData.setRelatedSource(getTagValue(extensionElement, TAG_SOUVISEJICI_ZDROJ));
        propertyData.setAlternativeName(getTagValue(extensionElement, TAG_ALTERNATIVNI_NAZEV));
        propertyData.setEquivalentConcept(getTagValue(extensionElement, TAG_EKVIVALENTNI_POJEM));
        propertyData.setIdentifier(getTagValue(extensionElement, TAG_IDENTIFIKATOR));
        propertyData.setDataType(getTagValue(extensionElement, TAG_DATOVY_TYP));

        propertyData.setSharedInPPDF(getBooleanTagValue(extensionElement, TAG_JE_POJEM_SDILEN_V_PPDF));
        propertyData.setIsPublic(getBooleanTagValue(extensionElement, TAG_JE_POJEM_VEREJNY));

        propertyData.setPrivacyProvision(getTagValue(extensionElement, TAG_USTANOVENI_DOKLADAJICI_NEVEREJNOST));
        propertyData.setSharingMethod(getTagValue(extensionElement, TAG_ZPUSOB_SDILENI_UDAJE));
        propertyData.setAcquisitionMethod(getTagValue(extensionElement, TAG_ZPUSOB_ZISKANI_UDAJE));
        propertyData.setContentType(getTagValue(extensionElement, TAG_TYP_OBSAHU_UDAJE));

        propertyData.setDomain("Subjekt nebo objekt práva");

        return propertyData;
    }

    /**
     * Parses connectors (relationships).
     */
    private void parseConnectors(Document document, Set<String> vocabularyPackageIds,
                                 List<RelationshipData> relationships) {
        NodeList connectors = document.getElementsByTagName("connector");

        for (int i = 0; i < connectors.getLength(); i++) {
            Element connector = (Element) connectors.item(i);

            if (!isValidConnectorToProcess(document, connector, vocabularyPackageIds)) {
                continue;
            }

            RelationshipData relationshipData = parseRelationshipData(document, connector);
            if (relationshipData.hasValidData()) {
                relationships.add(relationshipData);
                log.debug("Added relationship: {}", relationshipData.getName());
            }
        }
    }

    /**
     * Validates if a connector should be processed (has correct stereotype and belongs to vocabulary).
     */
    private boolean isValidConnectorToProcess(Document document, Element connector, Set<String> vocabularyPackageIds) {
        if (!isValidConnectorStereotype(connector)) {
            return false;
        }

        return isConnectorInVocabulary(document, connector, vocabularyPackageIds);
    }

    /**
     * Checks if connector has the correct stereotype for relationships.
     */
    private boolean isValidConnectorStereotype(Element connector) {
        String stereotype = getStereotype(connector);
        return STEREOTYPE_TYP_VZTAHU.equals(stereotype);
    }

    /**
     * Checks if a connector belongs to the vocabulary by verifying its connected elements.
     */
    private boolean isConnectorInVocabulary(Document document, Element connector, Set<String> vocabularyPackageIds) {
        NodeList sources = connector.getElementsByTagName("source");
        NodeList targets = connector.getElementsByTagName("target");

        if (sources.getLength() == 0 || targets.getLength() == 0) {
            return false;
        }

        Element source = (Element) sources.item(0);
        Element target = (Element) targets.item(0);

        String sourceId = source.getAttribute(XMI_IDREF);
        String targetId = target.getAttribute(XMI_IDREF);

        return isElementInVocabulary(document, sourceId, vocabularyPackageIds) &&
                isElementInVocabulary(document, targetId, vocabularyPackageIds);
    }

    /**
     * Checks if an element with given ID belongs to the vocabulary packages.
     */
    private boolean isElementInVocabulary(Document document, String elementId, Set<String> vocabularyPackageIds) {
        Element mainElement = findMainModelElement(document, elementId);
        if (mainElement != null) {
            String packageId = getElementPackageId(mainElement);
            return vocabularyPackageIds.contains(packageId);
        }
        return false;
    }

    /**
     * Parses relationship data from a connector.
     */
    private RelationshipData parseRelationshipData(Document document, Element connector) {
        RelationshipData relationshipData = new RelationshipData();

        relationshipData.setName(connector.getAttribute("name"));
        relationshipData.setDescription(getTagValue(connector, TAG_POPIS));
        relationshipData.setDefinition(getTagValue(connector, TAG_DEFINICE));
        relationshipData.setSource(getTagValue(connector, TAG_ZDROJ));
        relationshipData.setRelatedSource(getTagValue(connector, TAG_SOUVISEJICI_ZDROJ));
        relationshipData.setAlternativeName(getTagValue(connector, TAG_ALTERNATIVNI_NAZEV));
        relationshipData.setEquivalentConcept(getTagValue(connector, TAG_EKVIVALENTNI_POJEM));
        relationshipData.setIdentifier(getTagValue(connector, TAG_IDENTIFIKATOR));

        relationshipData.setSharedInPPDF(getBooleanTagValue(connector, TAG_JE_POJEM_SDILEN_V_PPDF));
        relationshipData.setIsPublic(getBooleanTagValue(connector, TAG_JE_POJEM_VEREJNY));

        relationshipData.setPrivacyProvision(getTagValue(connector, TAG_USTANOVENI_DOKLADAJICI_NEVEREJNOST));
        relationshipData.setSharingMethod(getTagValue(connector, TAG_ZPUSOB_SDILENI_UDAJE));
        relationshipData.setAcquisitionMethod(getTagValue(connector, TAG_ZPUSOB_ZISKANI_UDAJE));
        relationshipData.setContentType(getTagValue(connector, TAG_TYP_OBSAHU_UDAJE));

        NodeList sources = connector.getElementsByTagName("source");
        NodeList targets = connector.getElementsByTagName("target");

        if (sources.getLength() > 0 && targets.getLength() > 0) {
            Element source = (Element) sources.item(0);
            Element target = (Element) targets.item(0);

            String sourceId = source.getAttribute(XMI_IDREF);
            String targetId = target.getAttribute(XMI_IDREF);

            relationshipData.setDomain(getElementName(document, sourceId));
            relationshipData.setRange(getElementName(document, targetId));
        }

        return relationshipData;
    }

    /**
     * Gets element name by its ID.
     */
    private String getElementName(Document document, String elementId) {
        Element mainElement = findMainModelElement(document, elementId);
        if (mainElement != null) {
            return mainElement.getAttribute("name");
        }
        return null;
    }

    /**
     * Gets stereotype of an element.
     */
    private String getStereotype(Element element) {
        NodeList properties = element.getElementsByTagName("properties");
        if (properties.getLength() > 0) {
            Element property = (Element) properties.item(0);
            return property.getAttribute("stereotype");
        }
        return null;
    }

    /**
     * Gets tag value by tag name from an element.
     */
    private String getTagValue(Element element, String tagName) {
        if (element == null) {
            return null;
        }

        NodeList tags = element.getElementsByTagName("tag");

        for (int i = 0; i < tags.getLength(); i++) {
            Element tag = (Element) tags.item(i);
            if (tagName.equals(tag.getAttribute("name"))) {
                String value = tag.getAttribute("value");
                if (!value.trim().isEmpty()) {
                    return cleanTagValue(value.trim());
                }
            }
        }
        return null;
    }

    /**
     * Cleans tag values by removing EA metadata.
     */
    private String cleanTagValue(String value) {
        if (value == null) {
            return null;
        }

        if (value.startsWith("#NOTES#")) {
            if (value.contains("Values:") && !value.contains("=")) {
                return null;
            }
            if (value.contains("=")) {
                value = value.substring(value.lastIndexOf("=") + 1);
            } else {
                return null;
            }
        }

        value = value.replace("&#xA;", "").trim();

        return value;
    }

    /**
     * Gets boolean tag value.
     */
    private String getBooleanTagValue(Element element, String tagName) {
        String value = getTagValue(element, tagName);
        if (value == null) {
            return null;
        }

        if ("Yes".equals(value) || "No".equals(value)) {
            return value;
        }

        log.debug("Boolean field '{}': raw='{}' -> converted='{}'", tagName, value, value);
        return value;
    }
}