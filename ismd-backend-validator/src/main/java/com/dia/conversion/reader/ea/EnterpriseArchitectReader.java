package com.dia.conversion.reader.ea;

import com.dia.conversion.data.*;
import com.dia.exceptions.FileParsingException;
import com.dia.utility.UtilityMethods;
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
import java.util.regex.Pattern;

import static com.dia.constants.VocabularyConstants.DEFAULT_NS;
import static com.dia.constants.FormatConstants.EnterpriseArchitect.*;
import static com.dia.conversion.reader.ea.AttributePatterns.ATTRIBUTE_PATTERNS;

@Component
@Slf4j
public class EnterpriseArchitectReader {

    public OntologyData readXmiFromBytes(byte[] fileBytes) throws FileParsingException {
        Document document = parseWithEncodingDetection(fileBytes);
        return parseDocument(document);
    }

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

    private OntologyData parseDocument(Document document) throws FileParsingException {
        XmiIndex index = new XmiIndex(document);

        Element vocabularyPackage = findVocabularyPackageInMainModel(index);
        if (vocabularyPackage == null) {
            throw new FileParsingException("No package with stereotype 'slovnikyPackage' found in the document");
        }

        String vocabularyPackageId = vocabularyPackage.getAttribute(XMI_ID);
        log.info("Found vocabulary package: {} with ID: {}", vocabularyPackage.getAttribute("name"), vocabularyPackageId);

        VocabularyMetadata vocabularyMetadata = extractVocabularyMetadata(index, vocabularyPackageId);

        Set<String> vocabularyPackageIds = getVocabularyPackageIds(index, vocabularyPackageId);
        log.info("Vocabulary package IDs: {}", vocabularyPackageIds);

        List<ClassData> classes = new ArrayList<>();
        List<PropertyData> properties = new ArrayList<>();
        List<RelationshipData> relationships = new ArrayList<>();

        parseElements(index, vocabularyPackageIds, classes, properties);
        parseConnectors(index, vocabularyPackageIds, relationships, classes, properties);

        List<Element> generalizations = findGeneralizationConnectors(index, vocabularyPackageIds);

        List<HierarchyData> hierarchies = new ArrayList<>();
        hierarchies.addAll(extractHierarchiesFromClasses(classes, index, generalizations));
        hierarchies.addAll(extractHierarchiesFromProperties(properties, index, generalizations));

        inferSubPropertyDomains(properties, hierarchies);

        log.info("Parsed {} classes, {} properties, {} relationships, {} hierarchies",
                classes.size(), properties.size(), relationships.size(), hierarchies.size());

        return OntologyData.builder()
                .vocabularyMetadata(vocabularyMetadata)
                .classes(classes)
                .properties(properties)
                .relationships(relationships)
                .hierarchies(hierarchies)
                .build();
    }

    private Element findVocabularyPackageInMainModel(XmiIndex index) {
        for (Element umlPackage : index.packagedElements) {
            if (UML_PACKAGE.equals(umlPackage.getAttribute(XMI_TYPE))) {
                String packageId = umlPackage.getAttribute(XMI_ID);

                Element extensionElement = findExtensionElement(index, packageId);
                if (extensionElement != null) {
                    String stereotype = getStereotype(extensionElement);
                    if (STEREOTYPE_SLOVNIKY_PACKAGE.equals(stereotype)) {
                        return umlPackage;
                    }
                }

                if (hasNamespacePrefixedStereotype(index, packageId)) {
                    return umlPackage;
                }
            }
        }
        return null;
    }

    private Element findExtensionElement(XmiIndex index, String elementId) {
        return index.extensionsByIdRef.get(elementId);
    }

    private boolean hasNamespacePrefixedStereotype(XmiIndex index, String elementId) {
        if (index.vocabularyPackageBaseIds.contains(elementId)) {
            log.debug("Found namespace-prefixed stereotype '{}' for element ID: {}", STEREOTYPE_SLOVNIKY_PACKAGE, elementId);
            return true;
        }

        return false;
    }

    private String getNamespacePrefixedStereotype(XmiIndex index, String elementId) {
        Element element = index.namedStereotypesByBaseId.get(elementId);
        if (element == null) {
            return null;
        }

        String localName = element.getLocalName();
        log.debug("Found namespace-prefixed stereotype '{}' for element ID: {}", localName, elementId);
        return localName;
    }

    private Element findNamespacePrefixedStereotypeElement(XmiIndex index, String elementId) {
        return index.stereotypesByBaseId.get(elementId);
    }

    private VocabularyMetadata extractVocabularyMetadata(XmiIndex index, String vocabularyPackageId) {
        VocabularyMetadata metadata = new VocabularyMetadata();

        Element mainPackage = findMainModelElement(index, vocabularyPackageId);
        if (mainPackage != null) {
            metadata.setName(mainPackage.getAttribute("name"));
        }

        Element extensionElement = findExtensionElement(index, vocabularyPackageId);
        if (extensionElement != null) {
            metadata.setDescription(getTagValueByPattern(extensionElement, "POPIS_SLOVNIKU"));
        }

        return metadata;
    }

    private Element findMainModelElement(XmiIndex index, String elementId) {
        return index.elementsById.get(elementId);
    }

    private Set<String> getVocabularyPackageIds(XmiIndex index, String mainPackageId) {
        Set<String> packageIds = new HashSet<>();
        packageIds.add(mainPackageId);

        addSubPackages(index, mainPackageId, packageIds);

        return packageIds;
    }

    private void addSubPackages(XmiIndex index, String parentPackageId, Set<String> packageIds) {
        for (Element packageElement : index.packagedElements) {
            if (UML_PACKAGE.equals(packageElement.getAttribute(XMI_TYPE))) {
                Element parent = (Element) packageElement.getParentNode();
                if (parent != null && parentPackageId.equals(parent.getAttribute(XMI_ID))) {
                    String subPackageId = packageElement.getAttribute(XMI_ID);

                    Element extensionElement = findExtensionElement(index, subPackageId);
                    String stereotype = null;
                    if (extensionElement != null) {
                        stereotype = getStereotype(extensionElement);
                    }

                    if (!STEREOTYPE_SLOVNIKY_PACKAGE.equals(stereotype)) {
                        packageIds.add(subPackageId);
                        addSubPackages(index, subPackageId, packageIds);
                    }
                }
            }
        }
    }

    private void parseElements(XmiIndex index, Set<String> vocabularyPackageIds,
                               List<ClassData> classes, List<PropertyData> properties) {
        for (Element umlClass : index.packagedElements) {
            if (!isValidElementToProcess(umlClass, vocabularyPackageIds)) {
                continue;
            }

            String elementId = umlClass.getAttribute(XMI_ID);

            parseExtensions(index, elementId, classes, properties, umlClass);
        }
    }

    private boolean isValidElementToProcess(Element umlClass, Set<String> vocabularyPackageIds) {
        if (!UML_CLASS.equals(umlClass.getAttribute(XMI_TYPE))) {
            return false;
        }

        String packageId = getElementPackageId(umlClass);
        return vocabularyPackageIds.contains(packageId);
    }

    private void parseExtensions(XmiIndex index, String elementId, List<ClassData> classes,
                                 List<PropertyData> properties, Element umlClass) {
        Element extensionElement = findExtensionElement(index, elementId);
        String stereotype = null;

        if (extensionElement != null) {
            stereotype = getStereotype(extensionElement);
        }

        if (stereotype == null) {
            stereotype = getNamespacePrefixedStereotype(index, elementId);
            extensionElement = findNamespacePrefixedStereotypeElement(index, elementId);
        }

        if (stereotype != null) {
            String elementType = getTagValueByPattern(extensionElement, "TYP");

            if (elementType == null && extensionElement != null) {
                elementType = extensionElement.getAttribute("typ");
                if (elementType.trim().isEmpty()) {
                    elementType = null;
                }
            }

            log.debug("Found element {} with stereotype: {}, type: {}", umlClass.getAttribute("name"), stereotype, elementType);

            if (STEREOTYPE_TYP_OBJEKTU.equals(stereotype) || STEREOTYPE_TYP_SUBJEKTU.equals(stereotype)) {
                if (isPropertyType(elementType)) {
                    log.debug("Skipping '{}' from class extraction - it's a property type (type: '{}')", umlClass.getAttribute("name"), elementType);
                    return;
                }

                ClassData classData = parseClassData(umlClass, extensionElement, stereotype);
                if (classData.hasValidData()) {
                    classes.add(classData);
                    log.debug("Added class: {}", classData.getName());
                }
            } else if (STEREOTYPE_TYP_VLASTNOSTI.equals(stereotype)) {
                PropertyData propertyData = parsePropertyData(umlClass, extensionElement);
                properties.add(propertyData);
                log.debug("Added property: {}", propertyData.getName());
            }
        }
    }

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

    private ClassData parseClassData(Element umlElement, Element extensionElement, String stereotype) {
        ClassData classData = new ClassData();

        classData.setName(umlElement.getAttribute("name").trim());
        classData.setType(STEREOTYPE_TYP_SUBJEKTU.equals(stereotype) ? "Subjekt práva" : "Objekt práva");
        classData.setDescription(getTagValueByPattern(extensionElement, "POPIS"));
        classData.setDefinition(getTagValueByPattern(extensionElement, "DEFINICE"));


        String rawSource = getTagValueByPattern(extensionElement, "ZDROJ");
        String validatedSource = validateAndCleanSourceValue(rawSource);
        classData.setSource(validatedSource);

        String rawRelatedSource = getTagValueByPattern(extensionElement, "SOUVISEJICI_ZDROJ");
        String validatedRelatedSource = validateAndCleanSourceValue(rawRelatedSource);
        classData.setRelatedSource(validatedRelatedSource);

        classData.setAlternativeName(getTagValueByPattern(extensionElement, "ALTERNATIVNI_NAZEV"));
        classData.setEquivalentConcept(getTagValueByPattern(extensionElement, "EKVIVALENTNI_POJEM"));
        setValidatedIdentifier(classData, getTagValueByPattern(extensionElement, "IDENTIFIKATOR"));
        classData.setSharedInPPDF(getBooleanTagValueByPattern(extensionElement, "JE_POJEM_SDILEN_V_PPDF"));
        classData.setAgendaCode(getTagValueByPattern(extensionElement, "AGENDA"));
        classData.setAgendaSystemCode(getTagValueByPattern(extensionElement, "AGENDOVY_INFORMACNI_SYSTEM"));

        classData.setIsPublic(getBooleanTagValueByPattern(extensionElement, "JE_POJEM_VEREJNY"));
        classData.setPrivacyProvision(getTagValueByPattern(extensionElement, "USTANOVENI_DOKLADAJICI_NEVEREJNOST"));
        classData.setSharingMethod(getTagValueByPattern(extensionElement, "ZPUSOB_SDILENI_UDAJE"));
        classData.setAcquisitionMethod(getTagValueByPattern(extensionElement, "ZPUSOB_ZISKANI_UDAJE"));
        classData.setContentType(getTagValueByPattern(extensionElement, "TYP_OBSAHU_UDAJE"));

        return classData;
    }

    private PropertyData parsePropertyData(Element umlElement, Element extensionElement) {
        PropertyData propertyData = new PropertyData();

        propertyData.setName(umlElement.getAttribute("name").trim());
        propertyData.setDescription(getTagValueByPattern(extensionElement, "POPIS"));
        propertyData.setDefinition(getTagValueByPattern(extensionElement, "DEFINICE"));

        String rawSource = getTagValueByPattern(extensionElement, "ZDROJ");
        String validatedSource = validateAndCleanSourceValue(rawSource);
        propertyData.setSource(validatedSource);

        String rawRelatedSource = getTagValueByPattern(extensionElement, "SOUVISEJICI_ZDROJ");
        String validatedRelatedSource = validateAndCleanSourceValue(rawRelatedSource);
        propertyData.setRelatedSource(validatedRelatedSource);

        propertyData.setAlternativeName(getTagValueByPattern(extensionElement, "ALTERNATIVNI_NAZEV"));
        propertyData.setEquivalentConcept(getTagValueByPattern(extensionElement, "EKVIVALENTNI_POJEM"));
        setValidatedIdentifier(propertyData, getTagValueByPattern(extensionElement, "IDENTIFIKATOR"));
        propertyData.setDataType(getTagValueByPattern(extensionElement, "DATOVY_TYP"));
        propertyData.setSharedInPPDF(getBooleanTagValueByPattern(extensionElement, "JE_POJEM_SDILEN_V_PPDF"));

        propertyData.setAgendaCode(getTagValueByPattern(extensionElement, "AGENDA"));
        propertyData.setAgendaSystemCode(getTagValueByPattern(extensionElement, "AGENDOVY_INFORMACNI_SYSTEM"));

        propertyData.setIsPublic(getBooleanTagValueByPattern(extensionElement, "JE_POJEM_VEREJNY"));
        propertyData.setPrivacyProvision(getTagValueByPattern(extensionElement, "USTANOVENI_DOKLADAJICI_NEVEREJNOST"));
        propertyData.setSharingMethod(getTagValueByPattern(extensionElement, "ZPUSOB_SDILENI_UDAJE"));
        propertyData.setAcquisitionMethod(getTagValueByPattern(extensionElement, "ZPUSOB_ZISKANI_UDAJE"));
        propertyData.setContentType(getTagValueByPattern(extensionElement, "TYP_OBSAHU_UDAJE"));

        return propertyData;
    }

    private void parseConnectors(XmiIndex index, Set<String> vocabularyPackageIds,
                                 List<RelationshipData> relationships, List<ClassData> classes,
                                 List<PropertyData> properties) {
        Map<String, ClassData> classMap = createClassMap(classes);
        Map<String, PropertyData> propertyMap = createPropertyMap(properties);

        for (Element connector : index.connectors) {
            if (isConnectorInVocabulary(index, connector, vocabularyPackageIds)) {
                continue;
            }

            String connectorType = getConnectorType(connector);
            log.debug("Processing connector: {} of type: {}", connector.getAttribute("name"), connectorType);

            switch (connectorType) {
                case "Association":
                    if (isValidAssociationConnector(connector)) {
                        RelationshipData relationshipData = parseRelationshipData(index, connector);
                        relationships.add(relationshipData);
                        log.debug("Added association: {}", relationshipData.getName());
                    }
                    break;
                case "Generalization":
                    processGeneralizationConnector(index, connector, classMap, propertyMap);
                    break;
                case "Aggregation":
                    processAggregationConnector(index, connector, propertyMap, classMap);
                    break;
                default:
                    log.debug("Skipping connector type: {}", connectorType);
            }
        }
    }

    private Map<String, ClassData> createClassMap(List<ClassData> classes) {
        Map<String, ClassData> classMap = new HashMap<>();
        for (ClassData classData : classes) {
            classMap.put(classData.getName(), classData);
        }
        return classMap;
    }

    private Map<String, PropertyData> createPropertyMap(List<PropertyData> properties) {
        Map<String, PropertyData> propertyMap = new HashMap<>();
        for (PropertyData propertyData : properties) {
            propertyMap.put(propertyData.getName(), propertyData);
        }
        return propertyMap;
    }

    private String getConnectorType(Element connector) {
        NodeList properties = connector.getElementsByTagName("properties");
        if (properties.getLength() > 0) {
            Element property = (Element) properties.item(0);
            String eaType = property.getAttribute("ea_type");
            if (!eaType.trim().isEmpty()) {
                return eaType;
            }
        }

        String stereotype = getStereotype(connector);
        if (STEREOTYPE_TYP_VZTAHU.equals(stereotype)) {
            return "Association";
        }

        return "Unknown";
    }

    private boolean isValidAssociationConnector(Element connector) {
        String stereotype = getStereotype(connector);
        return STEREOTYPE_TYP_VZTAHU.equals(stereotype);
    }

    private void processGeneralizationConnector(XmiIndex index, Element connector,
                                               Map<String, ClassData> classMap,
                                               Map<String, PropertyData> propertyMap) {
        NodeList sources = connector.getElementsByTagName(SOURCE);
        NodeList targets = connector.getElementsByTagName(TARGET);

        if (sources.getLength() > 0 && targets.getLength() > 0) {
            Element source = (Element) sources.item(0);
            Element target = (Element) targets.item(0);

            String childId = source.getAttribute(XMI_IDREF);
            String parentId = target.getAttribute(XMI_IDREF);

            String childName = getElementName(index, childId);
            String parentName = getElementName(index, parentId);

            if (childName != null && parentName != null) {
                ClassData childClass = classMap != null ? classMap.get(childName) : null;
                if (childClass != null) {
                    childClass.setSuperClass(parentName);
                    log.debug("Established class inheritance: {} extends {}", childName, parentName);
                } else if (propertyMap != null) {
                    PropertyData childProperty = propertyMap.get(childName);
                    if (childProperty != null) {
                        childProperty.setSuperProperty(parentName);
                        log.debug("Established property inheritance: {} extends {}", childName, parentName);
                    } else {
                        log.warn("Child element not found for inheritance: {}", childName);
                    }
                } else {
                    log.warn("Child class not found for inheritance: {}", childName);
                }
            } else {
                log.warn("Could not resolve names for inheritance relationship: child={}, parent={}", childName, parentName);
            }
        }
    }

    private void processAggregationConnector(XmiIndex index, Element connector,
                                             Map<String, PropertyData> propertyMap,
                                             Map<String, ClassData> classMap) {
        NodeList sources = connector.getElementsByTagName(SOURCE);
        NodeList targets = connector.getElementsByTagName(TARGET);

        if (sources.getLength() > 0 && targets.getLength() > 0) {
            Element source = (Element) sources.item(0);
            Element target = (Element) targets.item(0);

            String propertyId = source.getAttribute(XMI_IDREF);
            String classId = target.getAttribute(XMI_IDREF);

            String propertyName = getElementName(index, propertyId);
            String className = getElementName(index, classId);

            if (propertyName != null && className != null) {
                PropertyData property = propertyMap.get(propertyName);
                ClassData ownerClass = classMap.get(className);

                if (property != null && ownerClass != null) {
                    property.setDomain(className);
                    log.debug("Set property domain: {} belongs to {}", propertyName, className);
                } else {
                    log.warn("Could not resolve aggregation: property={}, class={}", propertyName, className);
                }
            }
        }
    }

    private boolean isConnectorInVocabulary(XmiIndex index, Element connector, Set<String> vocabularyPackageIds) {
        NodeList sources = connector.getElementsByTagName(SOURCE);
        NodeList targets = connector.getElementsByTagName(TARGET);

        if (sources.getLength() == 0 || targets.getLength() == 0) {
            return true;
        }

        Element source = (Element) sources.item(0);
        Element target = (Element) targets.item(0);

        String sourceId = source.getAttribute(XMI_IDREF);
        String targetId = target.getAttribute(XMI_IDREF);

        boolean sourceInVocab = isElementInVocabulary(index, sourceId, vocabularyPackageIds);
        boolean targetInVocab = isElementInVocabulary(index, targetId, vocabularyPackageIds);

        log.debug("Connector {} - source {} in vocab: {}, target {} in vocab: {}",
                connector.getAttribute("name"), sourceId, sourceInVocab, targetId, targetInVocab);

        return !sourceInVocab || !targetInVocab;
    }

    private boolean isElementInVocabulary(XmiIndex index, String elementId, Set<String> vocabularyPackageIds) {
        Element mainElement = findMainModelElement(index, elementId);
        if (mainElement != null) {
            String packageId = getElementPackageId(mainElement);
            boolean inVocab = vocabularyPackageIds.contains(packageId);
            log.debug("Element {} (package: {}) in vocabulary: {}", elementId, packageId, inVocab);
            return inVocab;
        }
        log.debug("Element {} not found in main model", elementId);
        return false;
    }

    private List<Element> findGeneralizationConnectors(XmiIndex index, Set<String> vocabularyPackageIds) {
        List<Element> generalizations = new ArrayList<>();

        for (Element connector : index.connectors) {
            if (isConnectorInVocabulary(index, connector, vocabularyPackageIds)) {
                continue;
            }

            if ("Generalization".equals(getConnectorType(connector))) {
                generalizations.add(connector);
            }
        }

        return generalizations;
    }

    private List<HierarchyData> extractHierarchiesFromClasses(List<ClassData> classes, XmiIndex index,
                                                              List<Element> generalizations) {
        List<HierarchyData> hierarchies = new ArrayList<>();

        log.debug("Extracting hierarchies from {} classes", classes.size());

        for (ClassData classData : classes) {
            if (classData.getSuperClass() != null && !classData.getSuperClass().trim().isEmpty()) {
                HierarchyData hierarchy = new HierarchyData();
                hierarchy.setSubClass(classData.getName());
                hierarchy.setSuperClass(classData.getSuperClass());

                enrichHierarchyWithConnectorData(hierarchy, index, generalizations);

                hierarchies.add(hierarchy);
                log.debug("Created hierarchy: {} -> {}", classData.getName(), classData.getSuperClass());
            }
        }

        log.info("Extracted {} hierarchical relationships from classes", hierarchies.size());
        return hierarchies;
    }

    private List<HierarchyData> extractHierarchiesFromProperties(List<PropertyData> properties, XmiIndex index,
                                                                 List<Element> generalizations) {
        List<HierarchyData> hierarchies = new ArrayList<>();

        log.debug("Extracting hierarchies from {} properties", properties.size());

        for (PropertyData propertyData : properties) {
            if (propertyData.getSuperProperty() != null && !propertyData.getSuperProperty().trim().isEmpty()) {
                HierarchyData hierarchy = new HierarchyData();
                hierarchy.setSubClass(propertyData.getName());
                hierarchy.setSuperClass(propertyData.getSuperProperty());

                enrichHierarchyWithConnectorData(hierarchy, index, generalizations);

                hierarchies.add(hierarchy);
                log.debug("Created property hierarchy: {} -> {}", propertyData.getName(), propertyData.getSuperProperty());
            }
        }

        log.info("Extracted {} hierarchical relationships from properties", hierarchies.size());
        return hierarchies;
    }

    private void enrichHierarchyWithConnectorData(HierarchyData hierarchy, XmiIndex index,
                                                  List<Element> generalizations) {
        for (Element connector : generalizations) {
            if (isConnectorMatchingHierarchy(connector, hierarchy, index)) {
                String connectorName = connector.getAttribute("name");
                if (!connectorName.trim().isEmpty()) {
                    hierarchy.setRelationshipName(connectorName);
                } else {
                    String defaultRelName = isPropertyHierarchy(hierarchy, index)
                            ? "rdfs:subPropertyOf"
                            : "rdfs:subClassOf";
                    hierarchy.setRelationshipName(defaultRelName);
                }

                String connectorId = connector.getAttribute("xmi:id");
                if (!connectorId.trim().isEmpty()) {
                    hierarchy.setRelationshipId(connectorId);
                }

                String description = getTagValueByPattern(connector, "POPIS");
                if (description != null && !description.trim().isEmpty()) {
                    hierarchy.setDescription(description);
                }

                String definition = getTagValueByPattern(connector, "DEFINICE");
                if (definition != null && !definition.trim().isEmpty()) {
                    hierarchy.setDefinition(definition);
                }

                String source = getTagValueByPattern(connector, "ZDROJ");
                if (source != null && !source.trim().isEmpty()) {
                    hierarchy.setSource(validateAndCleanSourceValue(source));
                }

                log.debug("Enriched hierarchy {} -> {} with connector data",
                        hierarchy.getSubClass(), hierarchy.getSuperClass());
                break;
            }
        }
    }

    private boolean isConnectorMatchingHierarchy(Element connector, HierarchyData hierarchy, XmiIndex index) {
        NodeList sources = connector.getElementsByTagName(SOURCE);
        NodeList targets = connector.getElementsByTagName(TARGET);

        if (sources.getLength() == 0 || targets.getLength() == 0) {
            return false;
        }

        Element source = (Element) sources.item(0);
        Element target = (Element) targets.item(0);

        String childId = source.getAttribute(XMI_IDREF);
        String parentId = target.getAttribute(XMI_IDREF);

        String childName = getElementName(index, childId);
        String parentName = getElementName(index, parentId);

        return hierarchy.getSubClass().equals(childName) && hierarchy.getSuperClass().equals(parentName);
    }

    private boolean isPropertyHierarchy(HierarchyData hierarchy, XmiIndex index) {
        // Check if the subClass element is a property by looking for its stereotype
        for (Element element : index.packagedElements) {
            String name = element.getAttribute("name");

            if (hierarchy.getSubClass().equals(name)) {
                String elementId = element.getAttribute(XMI_ID);
                Element extensionElement = findExtensionElement(index, elementId);

                if (extensionElement != null) {
                    String stereotype = getStereotype(extensionElement);
                    return STEREOTYPE_TYP_VLASTNOSTI.equals(stereotype);
                }
            }
        }

        return false;
    }

    private RelationshipData parseRelationshipData(XmiIndex index, Element connector) {
        RelationshipData relationshipData = new RelationshipData();

        relationshipData.setName(connector.getAttribute("name").trim());
        relationshipData.setDescription(getTagValueByPattern(connector, "POPIS"));
        relationshipData.setDefinition(getTagValueByPattern(connector, "DEFINICE"));
        relationshipData.setSource(getTagValueByPattern(connector, "ZDROJ"));
        relationshipData.setRelatedSource(getTagValueByPattern(connector, "SOUVISEJICI_ZDROJ"));
        relationshipData.setAlternativeName(getTagValueByPattern(connector, "ALTERNATIVNI_NAZEV"));
        relationshipData.setEquivalentConcept(getTagValueByPattern(connector, "EKVIVALENTNI_POJEM"));
        setValidatedIdentifier(relationshipData, getTagValueByPattern(connector, "IDENTIFIKATOR"));
        relationshipData.setSharedInPPDF(getBooleanTagValueByPattern(connector, "JE_POJEM_SDILEN_V_PPDF"));
        relationshipData.setAgendaCode(getTagValueByPattern(connector, "AGENDA"));
        relationshipData.setAgendaSystemCode(getTagValueByPattern(connector, "AGENDOVY_INFORMACNI_SYSTEM"));
        relationshipData.setIsPublic(getBooleanTagValueByPattern(connector, "JE_POJEM_VEREJNY"));
        relationshipData.setPrivacyProvision(getTagValueByPattern(connector, "USTANOVENI_DOKLADAJICI_NEVEREJNOST"));
        relationshipData.setSharingMethod(getTagValueByPattern(connector, "ZPUSOB_SDILENI_UDAJE"));
        relationshipData.setAcquisitionMethod(getTagValueByPattern(connector, "ZPUSOB_ZISKANI_UDAJE"));
        relationshipData.setContentType(getTagValueByPattern(connector, "TYP_OBSAHU_UDAJE"));

        NodeList sources = connector.getElementsByTagName(SOURCE);
        NodeList targets = connector.getElementsByTagName(TARGET);

        if (sources.getLength() > 0 && targets.getLength() > 0) {
            Element source = (Element) sources.item(0);
            Element target = (Element) targets.item(0);

            String sourceId = source.getAttribute(XMI_IDREF);
            String targetId = target.getAttribute(XMI_IDREF);

            relationshipData.setDomain(getElementName(index, sourceId));
            relationshipData.setRange(getElementName(index, targetId));

            log.debug("Relationship {} connects {} -> {}",
                    relationshipData.getName(), relationshipData.getDomain(), relationshipData.getRange());
        }

        return relationshipData;
    }

    private String getElementName(XmiIndex index, String elementId) {
        Element mainElement = findMainModelElement(index, elementId);
        if (mainElement != null) {
            String name = mainElement.getAttribute("name").trim();
            log.debug("Resolved element {} to name: {}", elementId, name);
            return name;
        }
        log.warn("Could not resolve element name for ID: {}", elementId);
        return null;
    }

    private String getStereotype(Element element) {
        NodeList properties = element.getElementsByTagName("properties");
        if (properties.getLength() > 0) {
            Element property = (Element) properties.item(0);
            return property.getAttribute("stereotype");
        }
        return null;
    }

    private String getTagValueByPattern(Element element, String logicalAttributeName) {
        if (element == null) {
            return null;
        }

        Pattern[] patterns = ATTRIBUTE_PATTERNS.get(logicalAttributeName);
        if (patterns == null) {
            log.warn("No patterns defined for logical attribute: {}", logicalAttributeName);
            return null;
        }

        List<String> availableTags = getAllTagNames(element);
        log.debug("Looking for '{}' among available tags: {}", logicalAttributeName, availableTags);

        for (int patternIndex = 0; patternIndex < patterns.length; patternIndex++) {
            Pattern pattern = patterns[patternIndex];
            log.debug("Trying pattern #{}: '{}'", patternIndex, pattern.pattern());

            for (String tagName : availableTags) {
                boolean matches = pattern.matcher(tagName).matches();
                log.debug("Pattern '{}' vs tag '{}': {}", pattern.pattern(), tagName, matches);

                if (matches) {
                    String rawValue = getTagValue(element, tagName);
                    log.debug("Raw value from tag '{}': '{}'", tagName, rawValue);

                    if (rawValue != null && !rawValue.trim().isEmpty()) {
                        String cleanedValue = cleanTagValue(rawValue);
                        log.debug("Cleaned value: '{}'", cleanedValue);

                        if (cleanedValue != null && !cleanedValue.trim().isEmpty()) {
                            log.debug("Found value for '{}' using pattern #{} '{}' -> tag '{}': {}",
                                    logicalAttributeName, patternIndex, pattern.pattern(), tagName, cleanedValue);
                            return cleanedValue;
                        } else {
                            log.debug("Cleaned value is empty for tag '{}', continuing search", tagName);
                        }
                    } else {
                        log.debug("Raw value is null/empty for tag '{}', continuing search", tagName);
                    }
                }
            }
        }

        for (Pattern pattern : patterns) {
            for (String candidate : getElementAttributeNames(element)) {
                if (pattern.matcher(candidate).matches()) {
                    String rawValue = element.getAttribute(candidate);
                    if (!rawValue.trim().isEmpty()) {
                        String cleanedValue = cleanTagValue(rawValue);
                        if (cleanedValue != null && !cleanedValue.trim().isEmpty()) {
                            log.debug("Found value for '{}' as direct attribute '{}': {}",
                                    logicalAttributeName, candidate, cleanedValue);
                            return cleanedValue;
                        }
                    }
                }
            }
        }

        log.debug("No matching tag or attribute with non-empty value found for logical attribute '{}' with patterns: {}",
                logicalAttributeName, Arrays.toString(patterns));
        return null;
    }

    private List<String> getElementAttributeNames(Element element) {
        List<String> attributeNames = new ArrayList<>();
        if (element != null && element.hasAttributes()) {
            org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                attributeNames.add(attributes.item(i).getNodeName());
            }
        }
        return attributeNames;
    }

    private String getBooleanTagValueByPattern(Element element, String logicalAttributeName) {
        String value = getTagValueByPattern(element, logicalAttributeName);
        if (value == null) {
            return null;
        }

        if ("Yes".equals(value) || "No".equals(value) || "Ano".equals(value) || "Ne".equals(value)) {
            return value;
        }

        log.debug("Boolean field '{}': raw='{}' -> converted='{}'", logicalAttributeName, value, value);
        return value;
    }

    private List<String> getAllTagNames(Element element) {
        List<String> tagNames = new ArrayList<>();
        if (element == null) {
            return tagNames;
        }

        NodeList tags = element.getElementsByTagName("tag");
        for (int i = 0; i < tags.getLength(); i++) {
            Element tag = (Element) tags.item(i);
            String tagName = tag.getAttribute("name");
            if (!tagName.trim().isEmpty()) {
                tagNames.add(tagName);
            }
        }
        return tagNames;
    }

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

    public String cleanTagValue(String value) {
        if (value == null) {
            return null;
        }

        log.debug("cleanTagValue input: '{}'", value);

        String cleanedValue = value;

        if (cleanedValue.contains("#NOTES#")) {
            log.debug("Processing #NOTES# format");

            int notesIndex = cleanedValue.indexOf("#NOTES#");
            cleanedValue = cleanedValue.substring(0, notesIndex).trim();
            log.debug("Extracted value before '#NOTES#': '{}'", cleanedValue);
        }

        cleanedValue = cleanedValue.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#xA;", "")
                .replace("&#x0A;", "");

        cleanedValue = cleanedValue.trim();

        log.debug("cleanTagValue output: '{}'", cleanedValue);

        return cleanedValue.isEmpty() ? null : cleanedValue;
    }

    private String validateAndCleanSourceValue(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }

        source = source.trim();

        if (!UtilityMethods.isValidSource(source)) {
            log.debug("Filtering out invalid source: '{}'",
                    source.length() > 50 ? source.substring(0, 50) + "..." : source);
            return null;
        }

        if (source.contains(";")) {
            String[] parts = source.split(";");
            List<String> validParts = new ArrayList<>();

            for (String part : parts) {
                part = part.trim();
                if (UtilityMethods.isValidSource(part)) {
                    validParts.add(part);
                } else {
                    log.debug("Filtering out invalid source part: '{}'", part);
                }
            }

            return validParts.isEmpty() ? null : String.join(";", validParts);
        }

        return source;
    }

    private static boolean isPropertyType(String elementType) {
        if (elementType == null || elementType.trim().isEmpty()) {
            return false;
        }
        String trimmedType = elementType.trim();
        return "typ vlastnosti".equals(trimmedType) ||
                trimmedType.toLowerCase().contains("vlastnost");
    }

    private void inferSubPropertyDomains(List<PropertyData> properties, List<HierarchyData> hierarchies) {
        Map<String, PropertyData> propertyMap = new HashMap<>();
        for (PropertyData property : properties) {
            propertyMap.put(property.getName(), property);
        }

        for (HierarchyData hierarchy : hierarchies) {
            String subPropertyName = hierarchy.getSubClass();
            String superPropertyName = hierarchy.getSuperClass();

            PropertyData subProperty = propertyMap.get(subPropertyName);
            PropertyData superProperty = propertyMap.get(superPropertyName);

            if (subProperty != null && superProperty != null) {
                String subDomain = subProperty.getDomain();
                String superDomain = superProperty.getDomain();

                if ((subDomain == null || subDomain.trim().isEmpty()) &&
                    superDomain != null && !superDomain.trim().isEmpty()) {

                    subProperty.setDomain(superDomain);
                    log.debug("Inferred domain '{}' for sub-property '{}' from super-property '{}'",
                            superDomain, subPropertyName, superPropertyName);
                }
            }
        }
    }

    /**
     * Lookup tables built in one pass over the document, so that resolving an element by ID
     * does not rescan the whole DOM. Every lookup keeps the first match in document order.
     */
    private static final class XmiIndex {
        private static final String[] BASE_ATTRIBUTES = {"base_Package", "base_Class", "base_Association"};

        private final Map<String, Element> elementsById = new HashMap<>();
        private final Map<String, Element> extensionsByIdRef = new HashMap<>();
        private final Map<String, Element> stereotypesByBaseId = new HashMap<>();
        private final Map<String, Element> namedStereotypesByBaseId = new HashMap<>();
        private final Set<String> vocabularyPackageBaseIds = new HashSet<>();
        private final List<Element> packagedElements = new ArrayList<>();
        private final List<Element> connectors = new ArrayList<>();

        private XmiIndex(Document xmi) {
            NodeList allElements = xmi.getElementsByTagName("*");

            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String tagName = element.getTagName();
                String localName = element.getLocalName();

                elementsById.putIfAbsent(element.getAttribute(XMI_ID), element);

                if ("element".equals(tagName)) {
                    extensionsByIdRef.putIfAbsent(element.getAttribute(XMI_IDREF), element);
                } else if (PACKAGED_ELEMENT.equals(tagName)) {
                    packagedElements.add(element);
                } else if ("connector".equals(tagName)) {
                    connectors.add(element);
                }

                for (String baseAttribute : BASE_ATTRIBUTES) {
                    String baseId = element.getAttribute(baseAttribute);

                    stereotypesByBaseId.putIfAbsent(baseId, element);
                    if (localName != null) {
                        namedStereotypesByBaseId.putIfAbsent(baseId, element);
                        if (STEREOTYPE_SLOVNIKY_PACKAGE.equals(localName)) {
                            vocabularyPackageBaseIds.add(baseId);
                        }
                    }
                }
            }
        }
    }

    private void setValidatedIdentifier(ClassData data, String value) {
        if (value != null && !value.trim().isEmpty()) {
            String trimmed = value.trim();
            if (UtilityMethods.isValidIRI(trimmed)) {
                data.setIdentifier(trimmed);
            } else {
                log.warn("Invalid identifier '{}' for class '{}' - not a valid IRI/URI, ignoring",
                        trimmed, data.getName());
            }
        }
    }

    private void setValidatedIdentifier(PropertyData data, String value) {
        if (value != null && !value.trim().isEmpty()) {
            String trimmed = value.trim();
            if (UtilityMethods.isValidIRI(trimmed)) {
                data.setIdentifier(trimmed);
            } else {
                log.warn("Invalid identifier '{}' for property '{}' - not a valid IRI/URI, ignoring",
                        trimmed, data.getName());
            }
        }
    }

    private void setValidatedIdentifier(RelationshipData data, String value) {
        if (value != null && !value.trim().isEmpty()) {
            String trimmed = value.trim();
            if (UtilityMethods.isValidIRI(trimmed)) {
                data.setIdentifier(trimmed);
            } else {
                log.warn("Invalid identifier '{}' for relationship '{}' - not a valid IRI/URI, ignoring",
                        trimmed, data.getName());
            }
        }
    }
}