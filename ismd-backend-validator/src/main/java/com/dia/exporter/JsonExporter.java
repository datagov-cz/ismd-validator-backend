package com.dia.exporter;

import com.dia.constants.ArchiConstants;
import com.dia.exceptions.JsonExportException;
import com.dia.utility.UtilityMethods;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.MDC;

import java.util.*;

import static com.dia.constants.ArchiConstants.POPIS;
import static com.dia.constants.ArchiConstants.*;
import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;
import static com.dia.constants.ExcelConstants.TYP_OBSAHU_UDAJE;
import static com.dia.constants.ExcelConstants.ZPUSOB_ZISKANI_UDEJE;
import static com.dia.constants.ExportConstants.Json;
import static com.dia.constants.ExportConstants.Json.*;

@Slf4j
public class JsonExporter {

    private final OntModel ontModel;
    @Getter
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;
    private final String effectiveNamespace;


    public JsonExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName,
                        Map<String, String> modelProperties, String effectiveNamespace) {
        this.ontModel = ontModel;
        this.resourceMap = new HashMap<>(resourceMap);
        this.modelName = modelName;
        this.modelProperties = modelProperties;
        this.effectiveNamespace = effectiveNamespace;
    }

    public String exportToJson() {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting JSON export operation: requestId={}, modelName={}", requestId, modelName);
        return handleJsonOperation(() -> {
            log.debug("Building JSON structure: requestId={}", requestId);
            JSONObject unorderedRoot = new JSONObject();

            log.debug("Adding model metadata: requestId={}", requestId);
            addModelMetadata(unorderedRoot);

            log.debug("Creating concepts array: requestId={}", requestId);
            unorderedRoot.put(Json.POJMY, createConceptsArray());

            log.debug("Formatting and ordering JSON: requestId={}", requestId);
            return formatJsonWithOrderedFields(unorderedRoot);
        });
    }

    private void addModelMetadata(JSONObject root) throws JSONException {
        root.put(Json.CONTEXT, ArchiConstants.CONTEXT);
        root.put(Json.IRI, getOntologyIRI());
        root.put(Json.TYP, addJSONtypes());

        if (modelName != null && !modelName.isEmpty()) {
            addMultilingualModelProperty(root, Json.NAZEV, modelName);
        }

        String description = modelProperties.getOrDefault(POPIS, "");
        if (description != null && !description.isEmpty()) {
            addMultilingualModelProperty(root, Json.POPIS, description);
        }

        addTemporalMetadata(root);
    }

    private void addTemporalMetadata(JSONObject root) throws JSONException {
        Resource vocabularyResource = resourceMap.get("ontology");
        if (vocabularyResource != null) {
            Property creationProperty = ontModel.getProperty(SLOVNIKY_NS + OKAMZIK_VYTVORENI);
            if (vocabularyResource.hasProperty(creationProperty)) {
                handleCreationDate(vocabularyResource, creationProperty, root);
            }
            Property modificationProperty = ontModel.getProperty(SLOVNIKY_NS + OKAMZIK_POSLEDNI_ZMENY);
            if (vocabularyResource.hasProperty(modificationProperty)) {
                handleModificationDate(vocabularyResource, modificationProperty, root);
            }
        }
    }

    private void handleCreationDate(Resource vocabularyResource, Property creationProperty, JSONObject root) {
        Statement creationStmt = vocabularyResource.getProperty(creationProperty);
        if (creationStmt.getObject().isResource()) {
            Resource instantResource = creationStmt.getObject().asResource();
            String dateValue = extractTemporalValue(instantResource);
            if (dateValue != null) {
                root.put(OKAMZIK_VYTVORENI, dateValue);
            }
        }
    }

    private void handleModificationDate(Resource vocabularyResource, Property modificationProperty, JSONObject root) {
        Statement modificationStmt = vocabularyResource.getProperty(modificationProperty);
        if (modificationStmt.getObject().isResource()) {
            Resource instantResource = modificationStmt.getObject().asResource();
            String dateValue = extractTemporalValue(instantResource);
            if (dateValue != null) {
                root.put(OKAMZIK_POSLEDNI_ZMENY, dateValue);
            }
        }
    }

    private String extractTemporalValue(Resource instantResource) {
        Property dateTimeProperty = ontModel.getProperty(CAS_NS + DATUM_A_CAS);
        if (instantResource.hasProperty(dateTimeProperty)) {
            Statement dateTimeStmt = instantResource.getProperty(dateTimeProperty);
            if (dateTimeStmt.getObject().isLiteral()) {
                return dateTimeStmt.getString();
            }
        }

        Property dateProperty = ontModel.getProperty(CAS_NS + DATUM);
        if (instantResource.hasProperty(dateProperty)) {
            Statement dateStmt = instantResource.getProperty(dateProperty);
            if (dateStmt.getObject().isLiteral()) {
                return dateStmt.getString();
            }
        }

        return null;
    }

    private void addMultilingualModelProperty(JSONObject root, String propertyName,
                                              String csValue) throws JSONException {
        JSONObject propObj = new JSONObject();
        if (csValue != null && !csValue.isEmpty()) {
            propObj.put("cs", csValue);
        }
        if (propObj.length() > 0) {
            root.put(propertyName, propObj);
        }
    }

    private String formatJsonWithOrderedFields(JSONObject unorderedRoot) throws JsonExportException {
        try {
            Map<String, Object> originalMap = jsonToMap(unorderedRoot);

            Map<String, Object> orderedMap = createOrderedModelMap(originalMap);

            processConceptsArray(originalMap, orderedMap);

            addRemainingFields(originalMap, orderedMap);

            Map<String, Object> filteredMap = UtilityMethods.filterMap(orderedMap);

            return convertMapToFormattedJson(filteredMap);
        } catch (JSONException e) {
            log.error("Error parsing JSON: {}", e.getMessage(), e);
            throw new JsonExportException("Při čtení JSON došlo k chybě: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error formatting JSON: {}", e.getMessage(), e);
            throw new JsonExportException("Při formátování JSON došlo k chybě: " + e.getMessage());
        }
    }

    private Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<>();
        JSONArray names = json.names();

        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                Object value = json.get(key);

                if (value instanceof JSONObject jsonObject) {
                    map.put(key, jsonToMap(jsonObject));
                } else if (value instanceof JSONArray jsonArray) {
                    map.put(key, jsonToArray(jsonArray));
                } else if (value == JSONObject.NULL) {
                    map.put(key, null);
                } else {
                    map.put(key, value);
                }
            }
        }

        return map;
    }

    private String getOntologyIRI() {
        Resource ontologyResource;
        StmtIterator iter = ontModel.listStatements(null, RDF.type, OWL2.Ontology);
        if (iter.hasNext()) {
            ontologyResource = iter.next().getSubject();
            return ontologyResource.getURI();
        }

        return modelProperties.getOrDefault(LOKALNI_KATALOG, effectiveNamespace);
    }

    private Map<String, Object> createOrderedModelMap(Map<String, Object> originalMap) {
        Map<String, Object> orderedMap = new LinkedHashMap<>();

        addFieldIfExists(originalMap, orderedMap, Json.CONTEXT);
        addFieldIfExists(originalMap, orderedMap, Json.IRI);
        addFieldIfExists(originalMap, orderedMap, Json.TYP);

        addFieldWithDefault(originalMap, orderedMap, Json.NAZEV, createEmptyMultilingualField());
        addFieldWithDefault(originalMap, orderedMap, Json.POPIS, createEmptyMultilingualField());

        addFieldIfExists(originalMap, orderedMap, OKAMZIK_VYTVORENI);
        addFieldIfExists(originalMap, orderedMap, OKAMZIK_POSLEDNI_ZMENY);

        return orderedMap;
    }

    private void processConceptsArray(Map<String, Object> originalMap, Map<String, Object> orderedMap) {
        if (originalMap.containsKey(Json.POJMY)) {
            Object pojmyObj = originalMap.get(Json.POJMY);

            if (pojmyObj instanceof List<?> rawList) {
                List<Map<String, Object>> orderedPojmyList = new ArrayList<>();

                for (Object item : rawList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapItem = (Map<String, Object>) item;
                        orderedPojmyList.add(orderPojemFields(mapItem));
                    } else {
                        log.warn("Unexpected non-map object in pojmy list: {}", item);
                    }
                }

                orderedMap.put(Json.POJMY, orderedPojmyList);
            } else {
                log.warn("Expected pojmy to be a List but was: {}", pojmyObj.getClass().getName());
                orderedMap.put(Json.POJMY, new ArrayList<>());
            }
        } else {
            orderedMap.put(Json.POJMY, new ArrayList<>());
        }
    }

    private void addRemainingFields(Map<String, Object> originalMap, Map<String, Object> orderedMap) {
        originalMap.forEach((key, value) -> {
            if (!orderedMap.containsKey(key)) {
                orderedMap.put(key, value);
            }
        });
    }

    private String convertMapToFormattedJson(Map<String, Object> map) throws JsonExportException {
        try {
            Map<String, Object> filteredMap = UtilityMethods.filterMap(map);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(filteredMap);
        } catch (Exception e) {
            throw new JsonExportException("Při konverzi do JSON došlo k chybě: " + e.getMessage());
        }
    }

    private Map<String, String> createEmptyMultilingualField() {
        Map<String, String> emptyField = new LinkedHashMap<>();
        emptyField.put("cs", "");
        return emptyField;
    }

    private void addFieldWithDefault(Map<String, Object> source, Map<String, Object> target,
                                     String fieldName, Object defaultValue) {
        target.put(fieldName, source.getOrDefault(fieldName, defaultValue));
    }

    private Map<String, Object> orderPojemFields(Map<String, Object> pojemMap) {
        Map<String, Object> orderedPojem = new LinkedHashMap<>();

        String[] orderedFields = {
                "iri", "typ", "název", "alternativní název", "identifikátor", "popis", "definice", "ekvivalentní pojem",
                DEFINUJICI_USTANOVENI, SOUVISEJICI_USTANOVENI,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ, SOUVISEJICI_NELEGISLATIVNI_ZDROJ,
                "definiční-obor", "obor-hodnot", "nadřazený-vztah", "nadřazená-vlastnost", "nadřazená-třída",
                "způsob-sdílení-údajů", "způsob-získání-údajů", "typ-obsahu-údajů"
        };

        for (String field : orderedFields) {
            addFieldIfExists(pojemMap, orderedPojem, field);
        }

        pojemMap.forEach((key, value) -> {
            if (!orderedPojem.containsKey(key)) {
                orderedPojem.put(key, value);
            }
        });

        return orderedPojem;
    }

    private List<Object> jsonToArray(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);

            if (value instanceof JSONObject jsonObject) {
                list.add(jsonToMap(jsonObject));
            } else if (value instanceof JSONArray jsonArray) {
                list.add(jsonToArray(jsonArray));
            } else if (value == JSONObject.NULL) {
                list.add(null);
            } else {
                list.add(value);
            }
        }

        return list;
    }

    private void addFieldIfExists(Map<String, Object> source, Map<String, Object> target, String field) {
        if (source.containsKey(field)) {
            target.put(field, source.get(field));
        }
    }

    private JSONArray addJSONtypes() {
        JSONArray typArray = new JSONArray();
        typArray.put(Json.TYPE_SLOVNIK);
        typArray.put(Json.TYPE_TEZAURUS);
        typArray.put(Json.TYPE_KM);
        return typArray;
    }

    private JSONArray createConceptsArray() {
        if (ontModel == null || ontModel.isEmpty()) {
            throw new JsonExportException("Ontology model is null or empty.");
        }
        JSONArray pojmy = new JSONArray();

        Set<Resource> conceptTypes = Set.of(
                ontModel.getResource(OFN_NAMESPACE + POJEM),
                ontModel.getResource(OFN_NAMESPACE + VZTAH),
                ontModel.getResource(OFN_NAMESPACE + VLASTNOST),
                ontModel.getResource(OFN_NAMESPACE + TRIDA),
                ontModel.getResource(OFN_NAMESPACE + TSP),
                ontModel.getResource(OFN_NAMESPACE + TOP),
                ontModel.getResource(OFN_NAMESPACE + VEREJNY_UDAJ),
                ontModel.getResource(OFN_NAMESPACE + NEVEREJNY_UDAJ)
        );

        resourceMap.values().stream()
                .filter(resource -> conceptTypes.stream().anyMatch(type -> resource.hasProperty(RDF.type, type)))
                .forEach(concept -> {
                    try {
                        pojmy.put(createConceptObject(concept));
                    } catch (JSONException e) {
                        log.warn("Could not process concept: {}", concept.getURI(), e);
                    }
                });

        return pojmy;
    }

    private JSONObject createConceptObject(Resource concept) throws JSONException {
        JSONObject pojemObj = new JSONObject();

        pojemObj.put("iri", concept.getURI());
        pojemObj.put("typ", getConceptTypes(concept));

        addMultilingualProperty(concept, SKOS.prefLabel, Json.NAZEV, pojemObj);

        addAlternativeNamesFromStandardProperty(concept, pojemObj);

        Property definitionProperty = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#definition");
        addMultilingualProperty(concept, definitionProperty, DEFINICE, pojemObj);

        Property identifierProperty = ontModel.createProperty("http://purl.org/dc/terms/identifier");
        addResourceArrayProperty(concept, identifierProperty, IDENTIFIKATOR, pojemObj);

        Property descriptionProperty = ontModel.createProperty("http://purl.org/dc/terms/description");
        addMultilingualProperty(concept, descriptionProperty, POPIS, pojemObj);

        addExactMatchProperty(concept, pojemObj);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, effectiveNamespace);

        addNewSourceProperties(concept, pojemObj);

        addDomainAndRangeFromStandardProperties(concept, pojemObj);

        addHierarchyFromStandardProperty(concept, pojemObj);

        addSuperPropertyHierarchy(concept, pojemObj);

        addGovernanceProperties(concept, pojemObj, effectiveNamespace);

        addRppMetadataWithBothNamespaces(concept, pojemObj, effectiveNamespace);

        return pojemObj;
    }

    private void addNewSourceProperties(Resource concept, JSONObject pojemObj) throws JSONException {
        addSourcePropertyFromEitherNamespace(concept, pojemObj, effectiveNamespace,
                DEFINUJICI_USTANOVENI, DEFINUJICI_USTANOVENI);

        addSourcePropertyFromEitherNamespace(concept, pojemObj, effectiveNamespace,
                SOUVISEJICI_USTANOVENI, SOUVISEJICI_USTANOVENI);

        addNonLegislativeSourceProperty(concept, pojemObj, effectiveNamespace,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ, DEFINUJICI_NELEGISLATIVNI_ZDROJ);

        addNonLegislativeSourceProperty(concept, pojemObj, effectiveNamespace,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ, SOUVISEJICI_NELEGISLATIVNI_ZDROJ);
    }

    private void addSourcePropertyFromEitherNamespace(Resource concept, JSONObject pojemObj, String namespace,
                                                      String propertyName, String jsonFieldName) throws JSONException {
        Property customProperty = ontModel.getProperty(namespace + propertyName);
        Property defaultProperty = ontModel.getProperty(ArchiConstants.DEFAULT_NS + propertyName);

        if (concept.hasProperty(customProperty)) {
            addResourceArrayProperty(concept, customProperty, jsonFieldName, pojemObj);
        } else if (concept.hasProperty(defaultProperty)) {
            addResourceArrayProperty(concept, defaultProperty, jsonFieldName, pojemObj);
        }
    }

    private void addNonLegislativeSourceProperty(Resource concept, JSONObject pojemObj, String namespace,
                                                 String propertyName, String jsonFieldName) throws JSONException {
        Property customProperty = ontModel.getProperty(namespace + propertyName);
        Property defaultProperty = ontModel.getProperty(ArchiConstants.DEFAULT_NS + propertyName);

        Property sourceProperty = null;
        if (concept.hasProperty(customProperty)) {
            sourceProperty = customProperty;
        } else if (concept.hasProperty(defaultProperty)) {
            sourceProperty = defaultProperty;
        }

        if (sourceProperty != null) {
            handleSourceProperty(concept, pojemObj, sourceProperty, jsonFieldName);
        }
    }

    private void handleSourceProperty(Resource concept, JSONObject pojemObj, Property sourceProperty, String jsonFieldName) throws JSONException {
        StmtIterator propIter = concept.listProperties(sourceProperty);
        if (propIter.hasNext()) {
            JSONArray sourceArray = new JSONArray();

            addSourceProperty(propIter, sourceArray);

            if (sourceArray.length() > 0) {
                pojemObj.put(jsonFieldName, sourceArray);
            }
        }
    }

    private void addSourceProperty(StmtIterator propIter, JSONArray sourceArray) {
        while (propIter.hasNext()) {
            Statement propStmt = propIter.next();
            if (propStmt.getObject().isResource()) {
                Resource digitalDoc = propStmt.getObject().asResource();

                Property schemaUrlProperty = ontModel.createProperty("http://schema.org/url");
                Statement urlStmt = digitalDoc.getProperty(schemaUrlProperty);

                if (urlStmt != null) {
                    JSONObject docObj = new JSONObject();
                    if (urlStmt.getObject().isResource()) {
                        docObj.put("url", urlStmt.getObject().asResource().getURI());
                    } else if (urlStmt.getObject().isLiteral()) {
                        docObj.put("url", urlStmt.getString());
                    }

                    if (docObj.has("url")) {
                        sourceArray.put(docObj);
                    }
                }
            }
        }
    }

    private void addGovernanceProperties(Resource concept, JSONObject pojemObj, String namespace) throws JSONException {
        addGovernancePropertyWithFallback(concept, pojemObj, namespace,
                "Způsob sdílení údajů", ZPUSOB_SDILENI, "způsob-sdílení-údajů");

        addGovernancePropertyWithFallback(concept, pojemObj, namespace,
                ZPUSOB_ZISKANI_UDEJE, ZPUSOB_ZISKANI, "způsob-získání-údajů");

        addGovernancePropertyWithFallback(concept, pojemObj, namespace,
                TYP_OBSAHU_UDAJE, TYP_OBSAHU, "typ-obsahu-údajů");
    }

    private void addGovernancePropertyWithFallback(Resource concept, JSONObject pojemObj, String namespace,
                                                   String excelConstant, String originalConstant, String jsonFieldName) throws JSONException {

        Property excelProperty = ontModel.getProperty(namespace + excelConstant);
        if (concept.hasProperty(excelProperty)) {
            addGovernancePropertyArray(concept, excelProperty, jsonFieldName, pojemObj);
            return;
        }

        Property excelDefaultProperty = ontModel.getProperty(ArchiConstants.DEFAULT_NS + excelConstant);
        if (concept.hasProperty(excelDefaultProperty)) {
            addGovernancePropertyArray(concept, excelDefaultProperty, jsonFieldName, pojemObj);
            return;
        }

        Property originalProperty = ontModel.getProperty(namespace + originalConstant);
        if (concept.hasProperty(originalProperty)) {
            addGovernancePropertyArray(concept, originalProperty, jsonFieldName, pojemObj);
            return;
        }

        Property originalDefaultProperty = ontModel.getProperty(ArchiConstants.DEFAULT_NS + originalConstant);
        if (concept.hasProperty(originalDefaultProperty)) {
            addGovernancePropertyArray(concept, originalDefaultProperty, jsonFieldName, pojemObj);
            return;
        }

        Property hyphenatedProperty = ontModel.getProperty(namespace + jsonFieldName);
        if (concept.hasProperty(hyphenatedProperty)) {
            addGovernancePropertyArray(concept, hyphenatedProperty, jsonFieldName, pojemObj);
        }
    }

    private void addGovernancePropertyArray(Resource concept, Property property, String jsonFieldName,
                                            JSONObject pojemObj) throws JSONException {
        List<String> allValues = extractGovernancePropertyValues(concept, property);

        if (!allValues.isEmpty()) {
            JSONArray propArray = createJsonArray(allValues);
            pojemObj.put(jsonFieldName, propArray);
        }
    }

    private List<String> extractGovernancePropertyValues(Resource concept, Property property) {
        List<String> allValues = new ArrayList<>();

        StmtIterator propIter = concept.listProperties(property);
        while (propIter.hasNext()) {
            Statement propStmt = propIter.next();
            String value = extractStatementValue(propStmt);

            if (value != null && !value.trim().isEmpty()) {
                allValues.addAll(splitMultipleValues(value));
            }
        }

        return allValues;
    }

    private String extractStatementValue(Statement statement) {
        if (statement.getObject().isLiteral()) {
            return statement.getString();
        } else if (statement.getObject().isResource()) {
            return statement.getObject().asResource().getURI();
        }
        return null;
    }

    private List<String> splitMultipleValues(String value) {
        List<String> values = new ArrayList<>();

        if (value.contains(";")) {
            String[] splitValues = value.split(";");
            for (String singleValue : splitValues) {
                String trimmedValue = singleValue.trim();
                if (!trimmedValue.isEmpty()) {
                    values.add(trimmedValue);
                }
            }
        } else {
            values.add(value.trim());
        }

        return values;
    }

    private JSONArray createJsonArray(List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    private void addResourceArrayPropertyFromEitherNamespace(Resource concept,
                                                             JSONObject pojemObj,
                                                             String namespace) throws JSONException {
        Property suppDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + ArchiConstants.USTANOVENI_NEVEREJNOST);
        Property suppCustom = ontModel.getProperty(namespace + ArchiConstants.USTANOVENI_NEVEREJNOST);
        if (concept.hasProperty(suppDefault)) {
            addResourceArrayProperty(concept, suppDefault, ArchiConstants.USTANOVENI_NEVEREJNOST, pojemObj);
        } else if (concept.hasProperty(suppCustom)) {
            addResourceArrayProperty(concept, suppCustom, ArchiConstants.USTANOVENI_NEVEREJNOST, pojemObj);
        }
    }

    private void addExactMatchProperty(Resource concept, JSONObject pojemObj) throws JSONException {
        Property exactMatchProperty = ontModel.createProperty("http://www.w3.org/2004/02/skos/core#exactMatch");

        StmtIterator exactMatchIter = concept.listProperties(exactMatchProperty);
        if (exactMatchIter.hasNext()) {
            JSONArray exactMatchArray = new JSONArray();

            while (exactMatchIter.hasNext()) {
                Statement exactMatchStmt = exactMatchIter.next();

                if (exactMatchStmt.getObject().isResource()) {
                    JSONObject exactMatchObj = new JSONObject();
                    exactMatchObj.put("id", exactMatchStmt.getObject().asResource().getURI());
                    exactMatchArray.put(exactMatchObj);
                } else if (exactMatchStmt.getObject().isLiteral()) {
                    String literalValue = exactMatchStmt.getString();
                    if (literalValue != null && !literalValue.trim().isEmpty()) {
                        JSONObject exactMatchObj = new JSONObject();
                        exactMatchObj.put("id", literalValue);
                        exactMatchArray.put(exactMatchObj);
                    }
                }
            }

            if (exactMatchArray.length() > 0) {
                pojemObj.put(EKVIVALENTNI_POJEM, exactMatchArray);
            }
        }
    }

    private void addDomainAndRangeFromStandardProperties(Resource concept, JSONObject pojemObj) throws JSONException {
        Statement domainStmt = concept.getProperty(RDFS.domain);
        if (domainStmt != null && domainStmt.getObject().isResource()) {
            pojemObj.put(DEFINICNI_OBOR, domainStmt.getObject().asResource().getURI());
        }

        Statement rangeStmt = concept.getProperty(RDFS.range);
        if (rangeStmt != null && rangeStmt.getObject().isResource()) {
            String rangeUri = rangeStmt.getObject().asResource().getURI();

            if (rangeUri.startsWith(ArchiConstants.XSD)) {
                pojemObj.put(OBOR_HODNOT, "xsd:" + rangeUri.substring(ArchiConstants.XSD.length()));
            } else {
                pojemObj.put(OBOR_HODNOT, rangeUri);
            }
        }
    }

    private void addHierarchyFromStandardProperty(Resource concept, JSONObject pojemObj) throws JSONException {
        StmtIterator subClassIter = concept.listProperties(RDFS.subClassOf);
        if (subClassIter.hasNext()) {
            JSONArray hierarchyArray = new JSONArray();

            while (subClassIter.hasNext()) {
                Statement stmt = subClassIter.next();
                if (stmt.getObject().isResource()) {
                    hierarchyArray.put(stmt.getObject().asResource().getURI());
                }
            }

            if (hierarchyArray.length() > 0) {
                pojemObj.put(NADRAZENA_TRIDA, hierarchyArray);
            }
        }
    }

    private void addSuperPropertyHierarchy(Resource concept, JSONObject pojemObj) throws JSONException {
        StmtIterator subPropertyIter = concept.listProperties(RDFS.subPropertyOf);
        if (subPropertyIter.hasNext()) {
            JSONArray superPropertyArray = new JSONArray();

            while (subPropertyIter.hasNext()) {
                Statement stmt = subPropertyIter.next();
                if (stmt.getObject().isResource()) {
                    superPropertyArray.put(stmt.getObject().asResource().getURI());
                }
            }

            if (superPropertyArray.length() > 0) {
                if (concept.hasProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VZTAH))) {
                    pojemObj.put("nadřazený-vztah", superPropertyArray);
                }
                else if (concept.hasProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + VLASTNOST))) {
                    pojemObj.put("nadřazená-vlastnost", superPropertyArray);
                }
            }
        }
    }

    private void addAlternativeNamesFromStandardProperty(Resource concept, JSONObject pojemObj) throws JSONException {
        Property anPropDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + ALTERNATIVNI_NAZEV);
        Property anPropCustom = ontModel.getProperty(effectiveNamespace + ALTERNATIVNI_NAZEV);

        StmtIterator stmtIter = concept.listProperties(anPropDefault);
        if (!stmtIter.hasNext()) {
            stmtIter = concept.listProperties(anPropCustom);
        }

        if (stmtIter.hasNext()) {
            JSONObject altNamesObj = new JSONObject();
            boolean hasNonEmptyValue = false;

            while (stmtIter.hasNext()) {
                Statement stmt = stmtIter.next();
                String value = stmt.getString();
                if (value == null || value.isEmpty()) {
                    continue;
                }

                String lang = stmt.getLanguage();
                if (lang == null || lang.isEmpty()) {
                    lang = "cs";
                }

                if (altNamesObj.has(lang)) {
                    Object existingValue = altNamesObj.get(lang);
                    if (existingValue instanceof JSONArray) {
                        ((JSONArray) existingValue).put(value);
                    } else {
                        JSONArray langArray = new JSONArray();
                        langArray.put(existingValue);
                        langArray.put(value);
                        altNamesObj.put(lang, langArray);
                    }
                } else {
                    altNamesObj.put(lang, value);
                }
                hasNonEmptyValue = true;
            }

            if (hasNonEmptyValue && altNamesObj.length() > 0) {
                pojemObj.put(ALTERNATIVNI_NAZEV, altNamesObj);
            }
        }
    }

    private void addSingleResourcePropertyFromEitherNamespace(Resource concept, JSONObject pojemObj,
                                                              String namespace, String labelDefO) throws JSONException {
        Property domainDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + labelDefO);
        Property domainCustom = ontModel.getProperty(namespace + labelDefO);

        if (concept.hasProperty(domainDefault)) {
            addResourceProperty(concept, ArchiConstants.DEFAULT_NS + labelDefO, labelDefO, pojemObj);
        } else if (concept.hasProperty(domainCustom)) {
            addResourceProperty(concept, namespace + labelDefO, labelDefO, pojemObj);
        }
    }

    private void addRppMetadataWithBothNamespaces(Resource concept, JSONObject pojemObj,
                                                  String namespace) throws JSONException {
        Property ppdfDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + JE_PPDF);
        Property ppdfCustom = ontModel.getProperty(namespace + JE_PPDF);

        Statement stmt = concept.getProperty(ppdfDefault);
        if (stmt == null) {
            stmt = concept.getProperty(ppdfCustom);
        }

        if (stmt != null && stmt.getObject().isLiteral()) {
            boolean value = stmt.getBoolean();
            pojemObj.put(JE_PPDF, value);
        }

        addSingleResourcePropertyFromEitherNamespace(concept, pojemObj, namespace, AIS);

        addSingleResourcePropertyFromEitherNamespace(concept, pojemObj, namespace, AGENDA);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace);
    }

    private JSONArray getConceptTypes(Resource concept) {
        JSONArray types = new JSONArray();
        types.put(POJEM_JSON_LD);

        String[][] typeMapping = {
                {TRIDA, TRIDA_JSON_LD},
                {VZTAH, VZTAH_JSON_LD},
                {VLASTNOST, VLASTNOST_JSON_LD},
                {TSP, TSP_JSON_LD},
                {TOP, TOP_JSON_LD},
                {VEREJNY_UDAJ, VEREJNY_UDAJ_JSON_LD},
                {NEVEREJNY_UDAJ, NEVEREJNY_UDAJ_JSON_LD}
        };

        for (String[] mapping : typeMapping) {
            if (concept.hasProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + mapping[0]))) {
                types.put(mapping[1]);
            }
        }

        return types;
    }

    private void addMultilingualProperty(Resource concept, Property property,
                                         String jsonProperty, JSONObject pojemObj) throws JSONException {
        StmtIterator propIter = concept.listProperties(property);
        if (propIter.hasNext()) {
            JSONObject propObj = new JSONObject();
            boolean hasNonEmptyValue = false;

            while (propIter.hasNext()) {
                Statement propStmt = propIter.next();
                String value = propStmt.getString();
                if (value == null || value.isEmpty()) {
                    continue;
                }

                String lang = propStmt.getLanguage();
                if (lang != null && !lang.isEmpty()) {
                    propObj.put(lang, propStmt.getString());
                } else {
                    propObj.put("cs", propStmt.getString());
                }
                hasNonEmptyValue = true;
            }
            if (hasNonEmptyValue && propObj.length() > 0) {
                pojemObj.put(jsonProperty, propObj);
            }
        }
    }

    private void addResourceArrayProperty(Resource concept, Property property,
                                          String jsonProperty, JSONObject pojemObj) throws JSONException {
        StmtIterator propIter = concept.listProperties(property);
        if (propIter.hasNext()) {
            JSONArray propArray = new JSONArray();
            while (propIter.hasNext()) {
                Statement propStmt = propIter.next();
                if (propStmt.getObject().isResource()) {
                    propArray.put(propStmt.getObject().asResource().getURI());
                } else if (propStmt.getObject().isLiteral()) {
                    String literalValue = propStmt.getString();
                    if (literalValue != null && !literalValue.trim().isEmpty()) {
                        propArray.put(literalValue);
                    }
                }
            }
            if (propArray.length() > 0) {
                pojemObj.put(jsonProperty, propArray);
            }
        }
    }

    private void addResourceProperty(Resource concept, String propertyUri, String jsonProperty,
                                     JSONObject targetObj) throws JSONException {
        Property property = ontModel.getProperty(propertyUri);
        Statement stmt = concept.getProperty(property);
        if (stmt != null && stmt.getObject().isResource()) {
            targetObj.put(jsonProperty, stmt.getObject().asResource().getURI());
        }
    }

    private <T> T handleJsonOperation(JsonSupplier<T> operation) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        try {
            return operation.get();
        } catch (JsonExportException e) {
            log.error("JSON export error: requestId={}, message={}", requestId, e.getMessage(), e);
            throw new JsonExportException("Při exportu do JSON došlo k chybě" + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during JSON operation: requestId={}, error={}, type={}",
                    requestId, e.getMessage(), e.getClass().getName(), e);
            throw new JsonExportException("Během zpracovávání JSON došlo k chybě: " + e.getMessage());
        }
    }

    @FunctionalInterface
    interface JsonSupplier<T> {
        T get() throws JsonExportException, JSONException;
    }
}