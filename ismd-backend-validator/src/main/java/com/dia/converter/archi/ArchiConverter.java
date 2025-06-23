package com.dia.converter.archi;

import com.dia.converter.reader.archi.ArchiReader;
import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import com.dia.exporter.JsonExporter;
import com.dia.exporter.TurtleExporter;
import com.dia.models.OFNBaseModel;
import com.dia.utility.DataTypeConverter;
import com.dia.utility.UtilityMethods;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
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

import static com.dia.constants.ArchiConstants.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;

@Deprecated(forRemoval = true)
@Component
@Slf4j
public class ArchiConverter {

    private String ontologyNamespace;

    private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();

    static {
        TYPE_MAPPINGS.put("typ subjektu", TSP);
        TYPE_MAPPINGS.put("typ objektu", TOP);
        TYPE_MAPPINGS.put("typ vlastnosti", VLASTNOST);
    }

    private final Map<String, String> propertyMapping = new HashMap<>();
    private final OntModel ontModel;
    private final Map<String, Resource> resourceMap;

    private Document archiDoc;
    private String modelName;
    @Getter
    @Setter
    private Boolean removeELI;

    @Deprecated
    public ArchiConverter() {
        this.resourceMap = new HashMap<>();
        OFNBaseModel ofnBaseModel = new OFNBaseModel();
        this.ontModel = ofnBaseModel.getOntModel();
    }

    @Deprecated
    public void parseFromString(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        int contentLength = content != null ? content.length() : 0;

        log.info("Starting XML parsing: requestId={}, contentLength={}", requestId, contentLength);
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
            log.info("XML parsing completed successfully: requestId={}", requestId);
        } catch (ParserConfigurationException e) {
            log.error("XML parser configuration failed: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new FileParsingException("Během konfigurace XML parseru došlo k chybě.", e);
        } catch (SAXException e) {
            log.error("XML parsing error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new FileParsingException("Při zpracovávání XML došlo k chybě.", e);
        } catch (IOException e) {
            log.error("IO error while parsing XML: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new FileParsingException("Při čtení XML došlo k chybě.", e);
        } catch (Exception e) {
            log.error("Unexpected error during XML parsing: requestId={}", requestId, e);
            throw new FileParsingException("Neočekávaná chyba při zpracování XML.", e);
        }
    }

    @Deprecated
    public void convert() throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi model conversion: requestId={}", requestId);

        try {
            if (archiDoc == null) {
                log.error("Document not found for conversion: requestId={}", requestId);
                throw new ConversionException("Dokument ke konverzi nebyl nalezen.");
            }

            log.debug("Extracting model name: requestId={}", requestId);
            NodeList nameNodes = archiDoc.getElementsByTagNameNS(ARCHI_NS, "name");
            if (nameNodes.getLength() > 0) {
                modelName = nameNodes.item(0).getTextContent();
                log.debug("Model name extracted: requestId={}, modelName={}", requestId, modelName);
            } else {
                modelName = "Untitled Model";
                log.warn("No model name found, using default: requestId={}, modelName={}", requestId, modelName);
            }

            log.debug("Processing model name properties: requestId={}", requestId);
            processModelNameProperty();

            log.debug("Setting model IRI: requestId={}", requestId);
            setModelIRI();

            log.debug("Initializing type classes: requestId={}", requestId);
            initializeTypeClasses();

            log.debug("Processing elements: requestId={}", requestId);
            processElements();

            log.debug("Processing relationships: requestId={}", requestId);
            processRelationships();

            if (Boolean.TRUE.equals(removeELI)) {
                log.debug("Removing invalid source URLs: requestId={}", requestId);
            }

            log.info("Archi model conversion completed successfully: requestId={}, modelName={}",
                    requestId, modelName);
        } catch (ConversionException e) {
            log.error("Conversion error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during conversion: requestId={}", requestId, e);
            throw new ConversionException("Neočekávaná chyba při konverzi.", e);
        }
    }

    @Deprecated
    public String exportToJson() throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting JSON export: requestId={}, modelName={}", requestId, modelName);

        try {
            log.debug("Creating JSON exporter: requestId={}", requestId);
            JsonExporter exporter = new JsonExporter(
                    ontModel,
                    resourceMap,
                    modelName,
                    getModelProperties(),
                    getEffectiveOntologyNamespace()
            );

            String result = exporter.exportToJson();
            log.info("JSON export completed: requestId={}, outputSize={}", requestId, result.length());
            return result;
        } catch (JsonExportException e) {
            log.error("JSON export error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during JSON export: requestId={}", requestId, e);
            throw new JsonExportException("Neočekávaná chyba při exportu do JSON.", e);
        }
    }

    @Deprecated
    public String exportToTurtle() throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}, modelName={}", requestId, modelName);

        try {
            log.debug("Creating Turtle exporter: requestId={}", requestId);
            TurtleExporter exporter = new TurtleExporter(
                    ontModel,
                    resourceMap,
                    modelName,
                    getModelProperties(),
                    getEffectiveOntologyNamespace()
            );

            String result = exporter.exportToTurtle();
            log.info("Turtle export completed: requestId={}, outputSize={}", requestId, result.length());
            return result;
        } catch (TurtleExportException e) {
            log.error("Turtle export error: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Turtle export: requestId={}", requestId, e);
            throw new TurtleExportException("Neočekávaná chyba při exportu do formátu Turtle.", e);
        }
    }

    private void processModelNameProperty() {
        Map<String, String> properties = getModelProperties();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.equals(LOKALNI_KATALOG) ||
                    key.contains("adresa") && (key.contains("lokální") || key.contains("lokálního"))) {
                String ns = entry.getValue();
                if (ns != null && !ns.isEmpty() && UtilityMethods.isValidUrl(ns)) {
                    this.ontologyNamespace = ns;
                    break;
                }
            }
        }
    }

    private void setModelIRI() {
        String sanitisedIri = assembleIri(modelName);

        ontModel.createOntology(sanitisedIri);

        Resource ontologyResource = ontModel.getResource(sanitisedIri);
        if (ontologyResource != null) {
            ontologyResource.addProperty(RDF.type, SKOS.ConceptScheme);
            ontologyResource.addProperty(SKOS.prefLabel, modelName, "cs");

            Map<String, String> properties = getModelProperties();
            String description = properties.getOrDefault(POPIS, "");
            if (description != null && !description.isEmpty()) {
                ontologyResource.addProperty(DCTerms.description, description, "cs");
            }

            resourceMap.put("ontology", ontologyResource);
        }
    }

    private String assembleIri(String iri) {
        String effectiveNamespace = getEffectiveOntologyNamespace();

        return effectiveNamespace + UtilityMethods.sanitizeForIRI(iri);
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

    private Map<String, String> getModelProperties() {
        Map<String, String> modelProperties = new HashMap<>();

        Element modelPropertiesElement = findModelPropertiesElement();
        if (modelPropertiesElement == null) {
            return modelProperties;
        }

        extractPropertiesFromElement(modelPropertiesElement, modelProperties);

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

    private void initializeTypeClasses() {
        createNamespacedResource(POJEM);
        createNamespacedResource(TRIDA);
        createNamespacedResource(VZTAH);
        createNamespacedResource(VLASTNOST);
        createNamespacedResource(TSP);
        createNamespacedResource(TOP);
        createNamespacedResource(VEREJNY_UDAJ);
        createNamespacedResource(NEVEREJNY_UDAJ);
    }

    private void processElements() throws ConversionException {
        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");
        if (elements.getLength() < 0) {
            throw new ConversionException("Soubor neobsahuje žádné elementy.");
        }

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);

            String name = getElementName(element);
            if (name.equals("Subjekt") || name.equals("Objekt") || name.equals("Vlastnost")) {
                continue;
            }

            String id = element.getAttribute(IDENT);

            Map<String, String> properties = getElementProperties(element);

            String elementType = properties.getOrDefault("typ", "").trim();
            String ontologyClass = TYPE_MAPPINGS.getOrDefault(elementType, POJEM);

            Resource resource = createResourceFromElement(id, name, ontologyClass, properties);
            resourceMap.put(id, resource);
        }
    }

    private void processRelationships() {
        NodeList relationships = archiDoc.getElementsByTagNameNS(ARCHI_NS, "relationship");

        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            processIndividualRelationship(relationship);
        }
    }

    private void mapStandardizedLabel(String propId, String propName) {
        Map<String, String> labelPatterns = new LinkedHashMap<>();

        if (propName.equals("související zdroj")) {
            propertyMapping.put(propId, SOUVISEJICI_ZDROJ);
            return;
        }
        if (propName.equals("zdroj")) {
            propertyMapping.put(propId, ZDROJ);
            return;
        }

        labelPatterns.put("popis", POPIS);
        labelPatterns.put("definice", DEFINICE);
        labelPatterns.put("identifikátor", IDENTIFIKATOR);
        labelPatterns.put("ustanovení dokládající neveřejnost", USTANOVENI_NEVEREJNOST);
        labelPatterns.put("agenda", AGENDA);
        labelPatterns.put("agendový informační systém", AIS);
        labelPatterns.put("je pojem sdílen v PPDF?", JE_PPDF);
        labelPatterns.put("je pojem veřejný?", JE_VEREJNY);
        labelPatterns.put("alternativní název", ALTERNATIVNI_NAZEV);
        labelPatterns.put("datový typ", DATOVY_TYP);
        labelPatterns.put("typ", TYP);
        labelPatterns.put("adresa lokálního katalogu dat", LOKALNI_KATALOG);

        for (Map.Entry<String, String> pattern : labelPatterns.entrySet()) {
            if (propName.contains(pattern.getKey())) {
                propertyMapping.put(propId, pattern.getValue());
                break;
            }
        }
    }

    private void logPropertyMappings() {
        log.debug("Property mappings built:");
        for (Map.Entry<String, String> entry : propertyMapping.entrySet()) {
            log.debug("  {} -> {}", entry.getKey(), entry.getValue());
        }
    }

    private void createNamespacedResource(String resourceName) {
        ontModel.createResource(resolveNamespacedUri(resourceName));
    }

    private String resolveNamespacedUri(String name) {
        return getEffectiveOntologyNamespace() + name;
    }

    private String getEffectiveOntologyNamespace() {
        if (ontologyNamespace != null && !ontologyNamespace.isEmpty() && UtilityMethods.isValidUrl(ontologyNamespace)) {
            if (!ontologyNamespace.endsWith("/")) {
                return ontologyNamespace + "/";
            }
            return ontologyNamespace;
        }

        return DEFAULT_NS;
    }

    private String extractPropName(Element propDef) {
        NodeList nodeList = propDef.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    private void processIndividualRelationship(Element relationship) {
        String id = relationship.getAttribute(IDENT);
        String sourceId = relationship.getAttribute("source");
        String targetId = relationship.getAttribute("target");
        String type = relationship.getAttribute("xsi:type");

        if (!resourceMap.containsKey(sourceId) || !resourceMap.containsKey(targetId)) {
            return;
        }

        Resource source = resourceMap.get(sourceId);
        Resource target = resourceMap.get(targetId);

        switch (type) {
            case "Specialization":
                processSpecializationRelationship(source, target);
                break;
            case "Composition":
                processCompositionRelationship(source, target);
                break;
            case "Association":
                processAssociationRelationship(relationship, id, source, target);
                break;
            default:
        }
    }

    private void processSpecializationRelationship(Resource source, Resource target) {
        source.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + NADRAZENA_TRIDA), target);

        if (source instanceof OntClass sourceClass && target instanceof OntClass targetClass) {
            sourceClass.addSuperClass(targetClass);
        }
    }

    private void processCompositionRelationship(Resource source, Resource target) {
        String relName = getLocalName(target);
        String propertyUri = getEffectiveOntologyNamespace() + relName;

        OntProperty property = ontModel.createObjectProperty(propertyUri);

        property.addDomain(source);
        property.addRange(target);

        addCompositionLabels(property, target);
    }

    private void addCompositionLabels(OntProperty property, Resource target) {
        StmtIterator labelStatements = target.listProperties(RDFS.label);

        while (labelStatements.hasNext()) {
            Statement labelStmt = labelStatements.next();

            if (labelStmt.getObject().isLiteral()) {
                Literal labelLiteral = labelStmt.getObject().asLiteral();
                String label = labelLiteral.getString();
                String language = labelLiteral.getLanguage();

                if (language != null && !language.isEmpty()) {
                    property.addLabel(label, language);

                    log.debug("Added label '{}' to property {}",
                            language + label, property.getURI());
                }
            }
        }

        if (!property.hasProperty(RDFS.label)) {
            String defaultName = getLocalName(target);
            property.addLabel(defaultName, "cs");
            log.debug("Added default label '{}' to property {}",
                    defaultName, property.getURI());
        }
    }

    private void processAssociationRelationship(Element relationship, String id, Resource source, Resource target) {
        String relName = getRelationshipName(relationship);
        if (relName == null || relName.isEmpty()) {
            return;
        }

        Map<String, String> relProps = getElementProperties(relationship);

        String baseVocabularyIri = getEffectiveOntologyNamespace();
        if (baseVocabularyIri.endsWith("/")) {
            baseVocabularyIri = baseVocabularyIri.substring(0, baseVocabularyIri.length() - 1);
        }

        String iri = baseVocabularyIri + "/pojem/" + UtilityMethods.sanitizeForIRI(relName);

        if (relProps.containsKey(IDENTIFIKATOR)) {
            String explicitIri = relProps.get(IDENTIFIKATOR);
            if (explicitIri != null && !explicitIri.isEmpty()) {
                iri = explicitIri;
            }
        }

        Resource relResource = createRelationshipResource(iri, relName, source, target);
        addRelationshipProperties(relResource, relProps);

        resourceMap.put(id, relResource);
    }

    private Resource createRelationshipResource(String iri, String relName, Resource source, Resource target) {
        String namespace = getEffectiveOntologyNamespace();
        Resource relResource = ontModel.createResource(iri);

        relResource.addProperty(RDF.type, OWL2.ObjectProperty);
        relResource.addProperty(RDF.type, ontModel.getResource(namespace + VZTAH));
        relResource.addProperty(RDF.type, ontModel.getResource(namespace + POJEM));

        relResource.addProperty(RDFS.label, relName, "cs");

        relResource.addProperty(ontModel.getProperty(namespace + DEFINICNI_OBOR), source);
        relResource.addProperty(ontModel.getProperty(namespace + OBOR_HODNOT), target);

        addSchemeRelationship(relResource);

        return relResource;
    }

    private void addRelationshipProperties(Resource relResource, Map<String, String> relProps) {
        addPropertyIfExists(relResource, relProps, POPIS, POPIS);

        addPropertyIfExists(relResource, relProps, DEFINICE, DEFINICE);

        if (relProps.containsKey(ZDROJ)) {
            String sourceUrl = relProps.get(ZDROJ);
            if (!sourceUrl.isEmpty()) {
                Property zdrojProp = ontModel.getProperty(getEffectiveOntologyNamespace() + ZDROJ);
                DataTypeConverter.addTypedProperty(relResource, zdrojProp, sourceUrl, null, ontModel);
            }
        }

        if (relProps.containsKey(SUPP)) {
            String provision = relProps.get(SUPP);
            if (!provision.isEmpty()) {
                relResource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + SUPP),
                        ontModel.createResource(provision));
            }
        }
    }

    private void addPropertyIfExists(Resource resource, Map<String, String> props,
                                     String propKey, String ontPropLabel) {
        if (props.containsKey(propKey)) {
            String value = props.get(propKey);
            if (!value.isEmpty()) {
                String namespace = getEffectiveOntologyNamespace();
                Property prop = ontModel.getProperty(namespace + ontPropLabel);

                if (isResourceProperty(prop)) {
                    try {
                        resource.addProperty(prop, ontModel.createResource(value));
                        log.debug("Added resource property {} with value {}", prop.getLocalName(), value);
                    } catch (Exception e) {
                        log.warn("Failed to add resource property '{}': {}. Adding as literal.", value, e.getMessage());
                        resource.addProperty(prop, value);
                    }
                } else {
                    DataTypeConverter.addTypedProperty(resource, prop, value, "cs", ontModel);
                }
            }
        }
    }

    private Resource createResourceFromElement(String id, String name, String ontologyClass,
                                               Map<String, String> properties) {
        Resource resource = createResourceWithIri(id, name, properties);

        addRdfTypesAndClasses(resource, ontologyClass);

        addLabels(resource, name, properties);

        addDescriptionAndDefinition(resource, properties);

        addAlternativeNames(resource, properties);

        addSourceReferences(resource, properties);

        addDomainAndRange(resource, properties);

        addSuperclasses(resource, properties);

        addLegalSources(resource, properties);

        addDataProperties(resource, properties);

        addSchemeRelationship(resource);

        return resource;
    }

    private Resource createResourceWithIri(String id, String name, Map<String, String> properties) {
        if (properties.containsKey(IDENTIFIKATOR)) {
            String iri = properties.get(IDENTIFIKATOR);
            if (iri != null && !iri.isEmpty() && UtilityMethods.isValidUrl(iri)) {
                return ontModel.createResource(iri);
            }

        }

        String namespace = getEffectiveOntologyNamespace();

        if (name != null && !name.isEmpty() && !UtilityMethods.looksLikeId(name)) {
            if (modelName.equals(name)) {
                return ontModel.createResource(namespace + UtilityMethods.sanitizeForIRI(name));
            } else {
                String baseVocabularyIri = namespace;
                if (baseVocabularyIri.endsWith("/")) {
                    baseVocabularyIri = baseVocabularyIri.substring(0, baseVocabularyIri.length() - 1);
                }

                return ontModel.createResource(baseVocabularyIri + "/pojem/" + UtilityMethods.sanitizeForIRI(name));
            }
        }

        return ontModel.createResource(namespace + id);
    }

    private void addRdfTypesAndClasses(Resource resource, String ontologyClass) {
        String namespace = getEffectiveOntologyNamespace();

        resource.addProperty(RDF.type, ontModel.getResource(namespace + POJEM));

        switch (ontologyClass) {
            case TSP -> {
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TRIDA));
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TSP));
            }
            case TOP -> {
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TRIDA));
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TOP));
            }
            case VLASTNOST -> resource.addProperty(RDF.type, ontModel.getResource(namespace + VLASTNOST));
            default -> resource.addProperty(RDF.type, ontModel.getResource(namespace + ontologyClass));
        }
    }

    private void addLabels(Resource resource, String name, Map<String, String> properties) {
        resource.addProperty(RDFS.label, name, "cs");

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(LANG) && !entry.getKey().equals("lang=cs")) {
                String lang = entry.getKey().substring(5);
                String langLabel = entry.getValue();
                if (langLabel != null && !langLabel.isEmpty()) {
                    resource.addProperty(RDFS.label, langLabel, lang);
                    log.debug("Adding {} label to resource {}: {}", lang, resource.getURI(), langLabel);
                }
            }
        }
    }

    private void addDescriptionAndDefinition(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        if (properties.containsKey(POPIS)) {
            Property popisProp = ontModel.getProperty(namespace + POPIS);
            DataTypeConverter.addTypedProperty(resource, popisProp, properties.get(POPIS), "cs", ontModel);
        }

        if (properties.containsKey(DEFINICE)) {
            Property defProp = ontModel.getProperty(namespace + DEFINICE);
            DataTypeConverter.addTypedProperty(resource, defProp, properties.get(DEFINICE), "cs", ontModel);

        }

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.contains(":") && !key.endsWith(":cs")) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    String propName = parts[0];
                    String lang = parts[1];

                    if (propName.equals(POPIS) || propName.equals(DEFINICE)) {
                        Property defProp = ontModel.getProperty(namespace + propName);
                        DataTypeConverter.addTypedProperty(resource, defProp, entry.getValue(), lang, ontModel);
                    }
                }
            }
        }
    }

    private void addAlternativeNames(Resource resource, Map<String, String> properties) {
        if (!properties.containsKey(ALTERNATIVNI_NAZEV)) {
            return;
        }

        String altNamesValue = properties.get(ALTERNATIVNI_NAZEV);
        if (altNamesValue == null || altNamesValue.isEmpty()) {
            return;
        }

        Property altNameProperty = ontModel.getProperty(getEffectiveOntologyNamespace() + ALTERNATIVNI_NAZEV);
        if (!altNamesValue.contains(";")) {
            DataTypeConverter.addTypedProperty(resource, altNameProperty, altNamesValue, "cs", ontModel);
            return;
        }

        Arrays.stream(altNamesValue.split(";"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(name -> DataTypeConverter.addTypedProperty(resource, altNameProperty, name, "cs", ontModel));
    }

    private void addSourceReferences(Resource resource, Map<String, String> properties) {
        addMainSourceReferences(resource, properties);

        addRelatedSourceReferences(resource, properties);
    }

    private void addMainSourceReferences(Resource resource, Map<String, String> properties) {
        if (!properties.containsKey(ZDROJ)) {
            return;
        }

        String sourceUrl = properties.get(ZDROJ);
        if (sourceUrl.contains(";")) {
            addMultipleSourceUrls(resource, sourceUrl);
        } else {
            addSingleSourceUrl(resource, sourceUrl);
        }
    }

    private void addMultipleSourceUrls(Resource resource, String sourceUrlString) {
        String[] urls = sourceUrlString.split(";");
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                addSingleSourceUrl(resource, url.trim());
            }
        }
    }

    private void addSingleSourceUrl(Resource resource, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        try {
            String transformedUrl = UtilityMethods.transformEliUrl(url, removeELI);
            Property sourceProp = ontModel.getProperty(getEffectiveOntologyNamespace() + ZDROJ);
            resource.addProperty(sourceProp, ontModel.createResource(transformedUrl));
        } catch (Exception e) {
            log.warn("Failed to add source URL '{}': {}. Adding as plain literal.", url, e.getMessage());
            Property sourceProp = ontModel.getProperty(getEffectiveOntologyNamespace() + ZDROJ);
            resource.addProperty(sourceProp, url);
        }
    }

    private void addRelatedSourceReferences(Resource resource, Map<String, String> properties) {
        if (!properties.containsKey(SOUVISEJICI_ZDROJ)) {
            return;
        }

        String relatedSourceUrl = properties.get(SOUVISEJICI_ZDROJ);
        if (relatedSourceUrl != null && !relatedSourceUrl.isEmpty()) {
            String transformedUrl = UtilityMethods.transformEliUrl(relatedSourceUrl, removeELI);
            Property relatedSourceProp = ontModel.getProperty(getEffectiveOntologyNamespace() + SOUVISEJICI_ZDROJ);
            resource.addProperty(relatedSourceProp, ontModel.createResource(transformedUrl));
        }
    }

    private void addDomainAndRange(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(DEFINICNI_OBOR)) {
            resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + DEFINICNI_OBOR),
                    ontModel.createResource(properties.get(DEFINICNI_OBOR)));
        }

        if (properties.containsKey(OBOR_HODNOT)) {
            addRangeProperty(resource, properties.get(OBOR_HODNOT));
        }
    }

    private void addRangeProperty(Resource resource, String rangeValue) {
        String namespace = getEffectiveOntologyNamespace();

        if (rangeValue.startsWith("xsd:")) {
            String xsdType = XSD + rangeValue.substring(4);
            resource.addProperty(ontModel.getProperty(namespace + OBOR_HODNOT),
                    ontModel.createResource(xsdType));
        } else {
            resource.addProperty(ontModel.getProperty(namespace + OBOR_HODNOT),
                    ontModel.createResource(rangeValue));
        }
    }

    private void addSuperclasses(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(NADRAZENA_TRIDA)) {
            String superClass = properties.get(NADRAZENA_TRIDA);
            resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + NADRAZENA_TRIDA),
                    ontModel.createResource(superClass));
        }
    }

    private void addLegalSources(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(SUPP)) {
            String provision = properties.get(SUPP);
            if (provision != null && !provision.trim().isEmpty()) {
                String transformedProvision = UtilityMethods.transformEliUrl(provision, removeELI);
                resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + SUPP),
                        ontModel.createResource(transformedProvision));
            }
        }
    }

    private void addDataProperties(Resource resource, Map<String, String> properties) {
        addPpdfSharing(resource, properties);
        addPublicFlag(resource, properties);
        addNonPublicData(resource, properties);
        addAgendaSystem(resource, properties);
        addDataSharingWays(resource, properties);
        addDataAcquisitionWay(resource, properties);
        addDataContentType(resource, properties);
    }

    private void addDataSharingWays(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(ZPUSOB_SDILENI)) {
            String sharingWays = properties.get(ZPUSOB_SDILENI);
            if (sharingWays != null && !sharingWays.isEmpty()) {
                if (sharingWays.contains(";")) {
                    String[] ways = sharingWays.split(";");
                    for (String way : ways) {
                        addSingleSharingWay(resource, way.trim());
                    }
                } else {
                    addSingleSharingWay(resource, sharingWays.trim());
                }
            }
        }
    }

    private void addSingleSharingWay(Resource resource, String sharingWay) {
        String formattedSharingWay;
        if (sharingWay.startsWith("http")) {
            formattedSharingWay = sharingWay;
        } else {
            formattedSharingWay = "https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/"
                    + sharingWay;
        }

        Property sdileniProp = ontModel.getProperty(getEffectiveOntologyNamespace() + ZPUSOB_SDILENI);
        DataTypeConverter.addTypedProperty(resource, sdileniProp, formattedSharingWay, null, ontModel);
    }

    private void addDataAcquisitionWay(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(ZPUSOB_ZISKANI)) {
            String acquisitionWay = properties.get(ZPUSOB_ZISKANI);
            if (acquisitionWay != null && !acquisitionWay.isEmpty()) {
                String formattedAcquisitionWay;
                if (acquisitionWay.startsWith("http")) {
                    formattedAcquisitionWay = acquisitionWay;
                } else {
                    formattedAcquisitionWay = "https://data.dia.gov.cz/zdroj/číselníky/způsoby-získání-údajů/položky/"
                            + acquisitionWay;
                }

                Property acquisitionProp = ontModel.getProperty(getEffectiveOntologyNamespace() + ZPUSOB_ZISKANI);
                DataTypeConverter.addTypedProperty(resource, acquisitionProp, formattedAcquisitionWay, null, ontModel);
            }
        }
    }

    private void addDataContentType(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(TYP_OBSAHU)) {
            String contentType = properties.get(TYP_OBSAHU);
            if (contentType != null && !contentType.isEmpty()) {
                String formattedContentType;
                if (contentType.startsWith("http")) {
                    formattedContentType = contentType;
                } else {
                    formattedContentType = "https://data.dia.gov.cz/zdroj/číselníky/typy-obsahu-údajů/položky/"
                            + contentType;
                }
                Property contentTypeProp = ontModel.getProperty(getEffectiveOntologyNamespace() + TYP_OBSAHU);
                DataTypeConverter.addTypedProperty(resource, contentTypeProp, formattedContentType, null, ontModel);
            }
        }
    }

    private void addPpdfSharing(Resource resource, Map<String, String> properties) {
        if (!properties.containsKey(JE_PPDF)) {
            return;
        }

        String value = properties.get(JE_PPDF);
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String namespace = getEffectiveOntologyNamespace();
        Property ppdfProp = ontModel.getProperty(namespace + JE_PPDF);

        if (isResourceProperty(ppdfProp)) {
            try {
                resource.addProperty(ppdfProp, ontModel.createResource(value));
                log.debug("Added PPDF resource: {} to resource {}", value, resource.getURI());
            } catch (Exception e) {
                log.warn("Failed to add PPDF value '{}': {}. Adding as literal.", value, e.getMessage());
                resource.addProperty(ppdfProp, value);
            }
        } else {
            if (DataTypeConverter.isBooleanValue(value)) {
                boolean boolValue = "true".equalsIgnoreCase(value) ||
                        "ano".equalsIgnoreCase(value) ||
                        "yes".equalsIgnoreCase(value);
                DataTypeConverter.addTypedProperty(resource, ppdfProp,
                        boolValue ? "true" : "false", null, ontModel);
            } else {
                log.warn("Unrecognized boolean value for {} property: '{}'. Expected true/false, ano/ne, or yes/no.",
                        JE_PPDF, value);
            }
        }
    }

    private void addPublicFlag(Resource resource, Map<String, String> properties) {
        if (!properties.containsKey(JE_VEREJNY)) {
            return;
        }

        String value = properties.get(JE_VEREJNY);
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        if (DataTypeConverter.isBooleanValue(value)) {
            boolean isPublic = "true".equalsIgnoreCase(value) ||
                    "ano".equalsIgnoreCase(value) ||
                    "yes".equalsIgnoreCase(value);

            if (isPublic) {
                resource.addProperty(RDF.type, ontModel.getResource(getEffectiveOntologyNamespace() + VEREJNY_UDAJ));
            } else {
                resource.addProperty(RDF.type, ontModel.getResource(getEffectiveOntologyNamespace() + NEVEREJNY_UDAJ));
            }
        } else {
            log.warn("Unrecognized boolean value for {} property: '{}'. Expected true/false, ano/ne, or yes/no.",
                    JE_VEREJNY, value);
        }
    }

    private void addNonPublicData(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        if (properties.containsKey(USTANOVENI_NEVEREJNOST) && !properties.get(USTANOVENI_NEVEREJNOST).isBlank()) {
            resource.addProperty(RDF.type, ontModel.getResource(namespace + NEVEREJNY_UDAJ));
            String legalProvision = properties.get(USTANOVENI_NEVEREJNOST);
            Property udnProp = ontModel.getProperty(namespace + USTANOVENI_NEVEREJNOST);
            DataTypeConverter.addTypedProperty(resource, udnProp, legalProvision, null, ontModel);
        }
    }

    private void addAgendaSystem(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        addAgendaInformationSystem(resource, properties, namespace);
        addAgenda(resource, properties, namespace);
    }

    private void addAgendaInformationSystem(Resource resource, Map<String, String> properties, String namespace) {
        if (properties.containsKey(AIS)) {
            String ais = properties.get(AIS);
            if (ais != null && !ais.isEmpty()) {
                String formattedAis;
                if (ais.matches("^\\d+$")) {
                    formattedAis = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/" + ais;
                } else if (ais.matches("^https://rpp-opendata.egon.gov.cz/odrpp/zdroj/isvs/\\d+$")) {
                    formattedAis = ais;
                } else {
                    formattedAis = ais;
                }

                resource.addProperty(
                        ontModel.getProperty(namespace + AIS),
                        ontModel.createResource(formattedAis)
                );
            }
        }
    }

    private void addAgenda(Resource resource, Map<String, String> properties, String namespace) {
        if (properties.containsKey(AGENDA)) {
            String agenda = properties.get(AGENDA);
            if (agenda != null && !agenda.isEmpty()) {

                String formattedAgenda = getFormattedAgenda(agenda);

                resource.addProperty(
                        ontModel.getProperty(namespace + AGENDA),
                        ontModel.createResource(formattedAgenda)
                );
            }
        }
    }

    private static String getFormattedAgenda(String agenda) {
        String formattedAgenda;
        if (agenda.matches("^\\d+$")) {
            formattedAgenda = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A" + agenda;
        } else if (agenda.matches("^A\\d+$")) {
            formattedAgenda = "https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/" + agenda;
        } else if (agenda.matches("^https://rpp-opendata.egon.gov.cz/odrpp/zdroj/agenda/A\\d+$")) {
            formattedAgenda = agenda;
        } else {
            formattedAgenda = agenda;
        }
        return formattedAgenda;
    }

    private void addSchemeRelationship(Resource resource) {
        Resource ontologyResource = resourceMap.get("ontology");
        if (ontologyResource != null && resource.hasProperty(RDF.type,
                ontModel.getResource(getEffectiveOntologyNamespace() + POJEM))) {
            resource.addProperty(SKOS.inScheme, ontologyResource);
        }
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
        ArchiReader.getNameNodes(element, result, log);
    }

    private String getLocalName(Resource resource) {
        String uri = resource.getURI();
        int lastHash = uri.lastIndexOf('#');
        int lastSlash = uri.lastIndexOf('/');
        int pos = Math.max(lastHash, lastSlash);

        if (pos > 0 && pos < uri.length() - 1) {
            return uri.substring(pos + 1);
        }
        return uri;
    }

    private boolean isResourceProperty(Property property) {
        StmtIterator rangeStmts = property.listProperties(RDFS.range);
        while (rangeStmts.hasNext()) {
            Statement stmt = rangeStmts.next();
            if (stmt.getObject().equals(RDFS.Resource)) {
                return true;
            }
        }
        return false;
    }
}