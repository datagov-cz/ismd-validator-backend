package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ConversionException;
import dia.ismd.common.exceptions.FileParsingException;
import dia.ismd.common.exceptions.JsonExportException;
import dia.ismd.common.exceptions.TurtleExportException;
import dia.ismd.common.models.OFNBaseModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dia.ismd.validator.constants.ArchiOntologyConstants.*;
import static dia.ismd.validator.constants.ConvertorControllerConstants.*;

@Component
@Slf4j
class ArchiConvertor {

    private String ontologyNamespace;

    private static final Set<String> COMMON_PROPERTY_NAMES = new HashSet<>(Arrays.asList(
            LABEL_TYP, LABEL_POPIS, LABEL_DEF, LABEL_ZDROJ, LABEL_SZ, LABEL_AN,
            LABEL_EP, LABEL_ID, LABEL_AIS, LABEL_AGENDA,
            LABEL_DT, LABEL_JE_PPDF, LABEL_JE_VEREJNY,
            LABEL_UDN, LABEL_ALKD, LABEL_DEF_O, LABEL_OBOR_HODNOT, LABEL_VU, LABEL_NVU, LABEL_NT
    ));

    private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();

    static {
        TYPE_MAPPINGS.put("typ subjektu", TYP_TSP);
        TYPE_MAPPINGS.put("typ objektu", TYP_TOP);
        TYPE_MAPPINGS.put("typ vlastnosti", TYP_VLASTNOST);
    }

    private final Map<String, String> propertyMapping = new HashMap<>();
    private final OntModel ontModel;
    private final Map<String, Resource> resourceMap;

    private Document archiDoc;
    private String modelName;

    public ArchiConvertor() {
        this.resourceMap = new HashMap<>();
        OFNBaseModel ofnBaseModel = new OFNBaseModel();
        this.ontModel = ofnBaseModel.getOntModel();
    }

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
            archiDoc = builder.parse(new ByteArrayInputStream(content.getBytes()));
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


    public String exportToJson() throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting JSON export: requestId={}, modelName={}", requestId, modelName);

        try {
            log.debug("Creating JSON exporter: requestId={}", requestId);
            JSONExporter exporter = new JSONExporter(
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

    public String exportToTurtle() throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}, modelName={}", requestId, modelName);

        try {
            log.debug("Creating Turtle exporter: requestId={}", requestId);
            TurtleExporter exporter = new TurtleExporter(
                    ontModel,
                    resourceMap,
                    modelName,
                    getModelProperties()
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
            if (entry.getKey().contains("adresa lokálního katalogu dat")) {
                String ns = entry.getValue();
                if (ns != null && !ns.isEmpty() && isValidUrl(ns)) {
                    this.ontologyNamespace = ns;
                }
            }
        }
    }

    private void setModelIRI() {
        String iri = modelName;
        String sanitisedIri = assembleIri(iri);
        ontModel.createOntology(sanitisedIri);

        Resource ontologyResource = ontModel.getResource(sanitisedIri);
        if (ontologyResource != null) {
            ontologyResource.addProperty(OWL2.versionInfo, "Vygenerováno z Archi modelu: " + archiDoc.getDocumentURI());
        }
    }

    private String assembleIri(String iri) {
        String effectiveNamespace = getEffectiveOntologyNamespace();

        return effectiveNamespace + sanitizeForIRI(iri);
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

            createNormalizedNameMapping(propId, propName);
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
        if (value != null) {
            modelProperties.put(propName, value);

            if (isOntologyNamespaceProperty(propRef)) {
                ontologyNamespace = value;
            }
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
        createNamespacedResource(TYP_POJEM);
        createNamespacedResource(TYP_TRIDA);
        createNamespacedResource(TYP_VZTAH);
        createNamespacedResource(TYP_VLASTNOST);
        createNamespacedResource(TYP_TSP);
        createNamespacedResource(TYP_TOP);
        createNamespacedResource(TYP_VEREJNY_UDAJ);
        createNamespacedResource(TYP_NEVEREJNY_UDAJ);
    }

    private void processElements() throws ConversionException {
        NodeList elements = archiDoc.getElementsByTagNameNS(ARCHI_NS, "element");
        if (elements.getLength() < 0) {
            throw new ConversionException("Soubor neobsahuje žádné elementy.");
        }

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);

            String name = getElementName(element);
            if (name.equals("Subjekt") || name.equals("Objekt") || name.equals(TYP_VLASTNOST)) {
                continue;
            }

            String id = element.getAttribute(IDENT);

            Map<String, String> properties = getElementProperties(element);

            String elementType = properties.getOrDefault("typ", "").trim();
            String ontologyClass = TYPE_MAPPINGS.getOrDefault(elementType, TYP_POJEM);

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
        Map<String, String> labelPatterns = new HashMap<>();
        labelPatterns.put(LABEL_POPIS, LABEL_POPIS);
        labelPatterns.put(LABEL_DEF, LABEL_DEF);
        labelPatterns.put(LABEL_ZDROJ, LABEL_ZDROJ);
        labelPatterns.put(LABEL_ID, LABEL_ID);
        labelPatterns.put("ustanovení dokládající neveřejnost", LABEL_SUPP);
        labelPatterns.put(LABEL_AGENDA, LABEL_AGENDA);
        labelPatterns.put("agendový informační systém", LABEL_AIS);
        labelPatterns.put("je pojem sdílen v PPDF?", LABEL_JE_PPDF);
        labelPatterns.put("je pojem veřejný?", LABEL_JE_VEREJNY);
        labelPatterns.put("alternativní název", LABEL_AN);
        labelPatterns.put("datový typ", LABEL_DT);
        labelPatterns.put("typ", LABEL_TYP);

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

    private String sanitizeForIRI(String input) {
        if (input == null || input.isEmpty()) {
            return "unnamed";
        }

        String result = input.toLowerCase();
        result = result.replaceAll("[^\\p{L}\\p{N}\\-._~!$&'()*+,;=/?#@%]", "-");
        result = result.replaceAll("-+", "-");
        result = result.replaceAll("^-?-$", "");

        return result;
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createNormalizedNameMapping(String propId, String propName) {
        String normalizedName = propName.toLowerCase().replace(" ", "-");
        propertyMapping.put("name:" + normalizedName, propId);
    }

    private void createNamespacedResource(String resourceName) {
        ontModel.createResource(resolveNamespacedUri(resourceName));
    }

    private String resolveNamespacedUri(String name) {
        return getEffectiveOntologyNamespace() + name;
    }

    private String getEffectiveOntologyNamespace() {
        if (ontologyNamespace != null && !ontologyNamespace.isEmpty()) {
            if (!ontologyNamespace.endsWith("/") && !ontologyNamespace.endsWith("#")) {
                return ontologyNamespace + "/";
            }
            return ontologyNamespace;
        }

        return NS;
    }

    private String extractPropName(Element propDef) {
        NodeList nodeList = propDef.getElementsByTagNameNS(ARCHI_NS, "name");
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    private boolean isOntologyNamespaceProperty(String propRef) {
        String propertyName = propertyMapping.getOrDefault(propRef, "");
        return propertyName.contains("adresa lokálního katalogu dat") ||
                propertyName.equals("adresa lokálního katalogu dat, ve kterém bude slovník registrován");
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
        if (source instanceof OntClass sourceClass && target instanceof OntClass targetClass) {
            sourceClass.addSuperClass(targetClass);
            source.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_NT), target);
        }
    }

    private void processCompositionRelationship(Resource source, Resource target) {
        String relName = "ma" + capitalize(getLocalName(target));
        OntProperty property = ontModel.createOntProperty(getEffectiveOntologyNamespace() + relName);
        property.addDomain(source);
        property.addRange(target);

        String targetName = target.getProperty(RDFS.label).getString();
        property.addLabel("má " + targetName.toLowerCase(), "cs");
        property.addLabel("has " + targetName.toLowerCase(), "en");
    }

    private void processAssociationRelationship(Element relationship, String id, Resource source, Resource target) {
        String relName = getRelationshipName(relationship);
        if (relName == null || relName.isEmpty()) {
            return;
        }

        Map<String, String> relProps = getElementProperties(relationship);
        String iri = determineIri(relProps, id);
        Resource relResource = createRelationshipResource(iri, relName, source, target);
        addRelationshipProperties(relResource, relProps);

        resourceMap.put(id, relResource);
    }

    private String determineIri(Map<String, String> relProps, String id) {
        String iri = null;
        if (relProps.containsKey(LABEL_ID)) {
            iri = relProps.get(LABEL_ID);
        }

        String identPropId = propertyMapping.getOrDefault("name:identifikátor", null);
        if (identPropId != null && relProps.containsKey("propRef:" + identPropId)) {
            iri = relProps.get("propRef:" + identPropId);
        }

        if (iri == null || iri.isEmpty()) {
            iri = getEffectiveOntologyNamespace() + id;
        }

        return iri;
    }

    private Resource createRelationshipResource(String iri, String relName, Resource source, Resource target) {
        String namespace = getEffectiveOntologyNamespace();
        Resource relResource = ontModel.createResource(iri);

        relResource.addProperty(RDF.type, OWL2.ObjectProperty);
        relResource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_VZTAH));
        relResource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM));

        relResource.addProperty(RDFS.label, relName, "cs");

        relResource.addProperty(ontModel.getProperty(namespace + LABEL_DEF_O), source);
        relResource.addProperty(ontModel.getProperty(namespace + LABEL_OBOR_HODNOT), target);

        return relResource;
    }

    private void addRelationshipProperties(Resource relResource, Map<String, String> relProps) {
        addPropertyIfExists(relResource, relProps, LABEL_POPIS, LABEL_POPIS);

        addPropertyIfExists(relResource, relProps, LABEL_DEF, LABEL_DEF);

        if (relProps.containsKey(LABEL_ZDROJ)) {
            String sourceUrl = relProps.get(LABEL_ZDROJ);
            if (!sourceUrl.isEmpty()) {
                relResource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_ZDROJ),
                        ontModel.createResource(sourceUrl));
            }
        }

        if (relProps.containsKey(LABEL_SUPP)) {
            String provision = relProps.get(LABEL_SUPP);
            if (!provision.isEmpty()) {
                relResource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_SUPP),
                        ontModel.createResource(provision));
            }
        }
    }

    private void addPropertyIfExists(Resource resource, Map<String, String> props,
                                     String propKey, String ontPropLabel) {
        if (props.containsKey(propKey)) {
            String value = props.get(propKey);
            if (!value.isEmpty()) {
                resource.addProperty(ontModel.getProperty(NS + ontPropLabel), value, "cs");
            }
        }
    }

    private Resource createResourceFromElement(String id, String name, String ontologyClass, Map<String, String> properties) {
        Resource resource = createResourceWithIri(id, name, properties);

        addRdfTypesAndClasses(resource, ontologyClass);

        addLabels(resource, name, properties);

        addDescriptionAndDefinition(resource, properties);

        addSourceReferences(resource, properties);

        addDomainAndRange(resource, properties);

        addSuperclasses(resource, properties);

        addLegalSources(resource, properties);

        addPurpleLayerProperties(resource, properties);

        return resource;
    }

    private Resource createResourceWithIri(String id, String name, Map<String, String> properties) {
        if (properties.containsKey(LABEL_ID)) {
            String iri = properties.get(LABEL_ID);
            if (iri != null && !iri.isEmpty()) {
                return ontModel.createResource(iri);
            }
        }

        String namespace = getEffectiveOntologyNamespace();

        if (id != null && !id.isEmpty()) {
            return ontModel.createResource(namespace + id);
        }

        return ontModel.createResource(namespace + toValidResourceName(name));
    }

    private void addRdfTypesAndClasses(Resource resource, String ontologyClass) {
        String namespace = getEffectiveOntologyNamespace();

        resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_POJEM));

        switch (ontologyClass) {
            case TYP_TSP -> {
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_TRIDA));
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_TSP));
            }
            case TYP_TOP -> {
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_TRIDA));
                resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_TOP));
            }
            case TYP_VLASTNOST -> resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_VLASTNOST));
            default -> resource.addProperty(RDF.type, ontModel.getResource(namespace + ontologyClass));
        }
    }

    private void addLabels(Resource resource, String name, Map<String, String> properties) {
        resource.addProperty(RDFS.label, name, "cs");

        if (properties.containsKey("název-en") || properties.containsKey("name-en")) {
            String enName = properties.getOrDefault("název-en", properties.getOrDefault("name-en", null));
            if (enName != null && !enName.isEmpty()) {
                resource.addProperty(RDFS.label, enName, "en");
            }
        }
    }

    private void addDescriptionAndDefinition(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        if (properties.containsKey(LABEL_POPIS)) {
            resource.addProperty(ontModel.getProperty(namespace + LABEL_POPIS), properties.get(LABEL_POPIS), "cs");
        }

        if (properties.containsKey(LABEL_DEF)) {
            resource.addProperty(ontModel.getProperty(namespace + LABEL_DEF), properties.get(LABEL_DEF), "cs");
        }
    }

    private void addSourceReferences(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(LABEL_ZDROJ)) {
            resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_ZDROJ),
                    ontModel.createResource(properties.get(LABEL_ZDROJ)));
        }
    }

    private void addDomainAndRange(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(LABEL_DEF_O)) {
            resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_DEF_O),
                    ontModel.createResource(properties.get(LABEL_DEF_O)));
        }

        if (properties.containsKey(LABEL_OBOR_HODNOT)) {
            addRangeProperty(resource, properties.get(LABEL_OBOR_HODNOT));
        }
    }

    private void addRangeProperty(Resource resource, String rangeValue) {
        String namespace = getEffectiveOntologyNamespace();

        if (rangeValue.startsWith("xsd:")) {
            String xsdType = XSD + rangeValue.substring(4);
            resource.addProperty(ontModel.getProperty(namespace + LABEL_OBOR_HODNOT),
                    ontModel.createResource(xsdType));
        } else {
            resource.addProperty(ontModel.getProperty(namespace + LABEL_OBOR_HODNOT),
                    ontModel.createResource(rangeValue));
        }
    }

    private void addSuperclasses(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(LABEL_NT)) {
            String superClass = properties.get(LABEL_NT);
            resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_NT),
                    ontModel.createResource(superClass));
        }
    }

    private void addLegalSources(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(LABEL_SUPP)) {
            String provision = properties.get(LABEL_SUPP);
            resource.addProperty(ontModel.getProperty(getEffectiveOntologyNamespace() + LABEL_SUPP),
                    ontModel.createResource(provision));
        }
    }

    private void addPurpleLayerProperties(Resource resource, Map<String, String> properties) {
        addPpdfSharing(resource, properties);
        addPublicFlag(resource, properties);
        addNonPublicData(resource, properties);
        addAgendaSystem(resource, properties);
    }

    private void addPpdfSharing(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        if (properties.containsKey(LABEL_JE_PPDF)) {
            String value = properties.get(LABEL_JE_PPDF);
            if ("true".equalsIgnoreCase(value) || "ano".equalsIgnoreCase(value)) {
                resource.addProperty(ontModel.getProperty(namespace + LABEL_JE_PPDF), "true", XSDDatatype.XSDboolean);
            } else if ("false".equalsIgnoreCase(value) || "ne".equalsIgnoreCase(value)) {
                resource.addProperty(ontModel.getProperty(namespace + LABEL_JE_PPDF), "false", XSDDatatype.XSDboolean);
            }
        }
    }

    private void addPublicFlag(Resource resource, Map<String, String> properties) {
        if (properties.containsKey(LABEL_JE_VEREJNY)) {
            String value = properties.get(LABEL_JE_VEREJNY);
            if ("true".equalsIgnoreCase(value) || "ano".equalsIgnoreCase(value)) {
                resource.addProperty(RDF.type, ontModel.getResource(getEffectiveOntologyNamespace() + LABEL_VU));
            }
        }
    }

    private void addNonPublicData(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        if (properties.containsKey(LABEL_UDN)) {
            resource.addProperty(RDF.type, ontModel.getResource(namespace + TYP_NEVEREJNY_UDAJ));
            String legalProvision = properties.get(LABEL_UDN);
            if (legalProvision != null && !legalProvision.isEmpty()) {
                resource.addProperty(
                        ontModel.getProperty(namespace + LABEL_UDN),
                        ontModel.createResource(legalProvision)
                );
            }
        }
    }

    private void addAgendaSystem(Resource resource, Map<String, String> properties) {
        String namespace = getEffectiveOntologyNamespace();

        if (properties.containsKey(LABEL_AIS)) {
            String ais = properties.get(LABEL_AIS);
            if (ais != null && !ais.isEmpty()) {
                resource.addProperty(
                        ontModel.getProperty(namespace + LABEL_AIS),
                        ontModel.createResource(ais)
                );
            }
        }

        if (properties.containsKey(LABEL_AGENDA)) {
            String agenda = properties.get(LABEL_AGENDA);
            if (agenda != null && !agenda.isEmpty()) {
                resource.addProperty(
                        ontModel.getProperty(namespace + LABEL_AGENDA),
                        ontModel.createResource(agenda)
                );
            }
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
        if (propertiesNodes.getLength() == 0) {
            return result;
        }

        Element propertiesElement = (Element) propertiesNodes.item(0);
        NodeList propertyNodes = propertiesElement.getElementsByTagNameNS(ARCHI_NS, "property");

        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element property = (Element) propertyNodes.item(i);
            String propRef = property.getAttribute("propertyDefinitionRef");

            String propName = propertyMapping.getOrDefault(propRef, propRef);

            NodeList valueNodes = property.getElementsByTagNameNS(ARCHI_NS, "value");
            if (valueNodes.getLength() > 0) {
                String value = valueNodes.item(0).getTextContent();
                result.put(propName, value);

                if (COMMON_PROPERTY_NAMES.contains(propName)) {
                    String dashedName = propName.replace(" ", "-");
                    if (!dashedName.equals(propName)) {
                        result.put(dashedName, value);
                    }
                }
            }
        }

        return result;
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

    private String toValidResourceName(String name) {
        return name.replaceAll("\\s+", "")
                .replaceAll("[^a-zA-Z0-9]", "");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}