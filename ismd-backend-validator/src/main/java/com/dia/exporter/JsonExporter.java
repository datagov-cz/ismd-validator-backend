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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.MDC;

import java.util.*;

import static com.dia.constants.ArchiConstants.*;
import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;
import static com.dia.constants.ExportConstants.*;

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

        String[] orderedFields = {"iri", "typ", "název", "alternativní název", "popis", "definice"};

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
        Resource pojemType = ontModel.getResource(effectiveNamespace + POJEM);

        ontModel.listSubjectsWithProperty(RDF.type, pojemType)
                .forEachRemaining(concept -> {
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
        String namespace = effectiveNamespace;

        pojemObj.put("iri", concept.getURI());
        pojemObj.put("typ", getConceptTypes(concept));

        addMultilingualProperty(concept, RDFS.label, Json.NAZEV, pojemObj);

        addAlternativeNamesFromEitherNamespace(concept, pojemObj, namespace);

        addMultilingualPropertyFromEitherNamespace(concept, pojemObj, namespace, DEFINICE);

        addMultilingualPropertyFromEitherNamespace(concept, pojemObj, namespace, POPIS);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, USTANOVENI_NEVEREJNOST);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, ZDROJ);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, SOUVISEJICI_ZDROJ);

        addDomainAndRangeWithBothNamespaces(concept, pojemObj, namespace);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, NADRAZENA_TRIDA);

        addRppMetadataWithBothNamespaces(concept, pojemObj, namespace);

        return pojemObj;
    }

    private void addResourceArrayPropertyFromEitherNamespace(Resource concept,
                                                             JSONObject pojemObj,
                                                             String namespace,
                                                             String labelSupp) throws JSONException {
        Property suppDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + labelSupp);
        Property suppCustom = ontModel.getProperty(namespace + labelSupp);
        if (concept.hasProperty(suppDefault)) {
            addResourceArrayProperty(concept, suppDefault, labelSupp, pojemObj);
        } else if (concept.hasProperty(suppCustom)) {
            addResourceArrayProperty(concept, suppCustom, labelSupp, pojemObj);
        }
    }

    private void addMultilingualPropertyFromEitherNamespace(Resource concept,
                                                            JSONObject pojemObj,
                                                            String namespace,
                                                            String labelDef) throws JSONException {
        Property langDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + labelDef);
        Property langCustom = ontModel.getProperty(namespace + labelDef);
        if (concept.hasProperty(langDefault)) {
            addMultilingualProperty(concept, langDefault, labelDef, pojemObj);
        } else if (concept.hasProperty(langCustom)) {
            addMultilingualProperty(concept, langCustom, labelDef, pojemObj);
        }
    }

    private void addAlternativeNamesFromEitherNamespace(Resource concept, JSONObject pojemObj, String namespace) throws JSONException {
        Property anPropDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + ALTERNATIVNI_NAZEV);
        Property anPropCustom = ontModel.getProperty(namespace + ALTERNATIVNI_NAZEV);

        StmtIterator stmtIter = concept.listProperties(anPropDefault);
        if (!stmtIter.hasNext()) {
            stmtIter = concept.listProperties(anPropCustom);
        }

        if (stmtIter.hasNext()) {
            JSONArray altNamesArray = new JSONArray();

            while (stmtIter.hasNext()) {
                Statement stmt = stmtIter.next();
                String value = stmt.getString();
                if (value != null && !value.isEmpty()) {
                    altNamesArray.put(value);
                }
            }

            if (altNamesArray.length() > 0) {
                pojemObj.put(ALTERNATIVNI_NAZEV, altNamesArray);
            }
        }
    }

    private void addDomainAndRangeWithBothNamespaces(Resource concept, JSONObject pojemObj,
                                                     String namespace) throws JSONException {
        addSingleResourcePropertyFromEitherNamespace(concept, pojemObj, namespace, DEFINICNI_OBOR);

        addRangePropertyWithBothNamespaces(concept, pojemObj, namespace);
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

    private void addRangePropertyWithBothNamespaces(Resource concept, JSONObject pojemObj,
                                                    String namespace) throws JSONException {
        Property rangeDefault = ontModel.getProperty(ArchiConstants.DEFAULT_NS + OBOR_HODNOT);
        Property rangeCustom = ontModel.getProperty(namespace + OBOR_HODNOT);

        Statement rangeStmt = concept.getProperty(rangeDefault);
        if (rangeStmt == null) {
            rangeStmt = concept.getProperty(rangeCustom);
        }

        if (rangeStmt != null && rangeStmt.getObject().isResource()) {
            String rangeUri = rangeStmt.getObject().asResource().getURI();

            if (rangeUri.startsWith(ArchiConstants.XSD)) {
                pojemObj.put(OBOR_HODNOT, "xsd:" + rangeUri.substring(ArchiConstants.XSD.length()));
            } else {
                pojemObj.put(OBOR_HODNOT, rangeUri);
            }
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

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, USTANOVENI_NEVEREJNOST);
    }

    private JSONArray getConceptTypes(Resource concept) {
        JSONArray types = new JSONArray();
        types.put(POJEM);

        String[][] typeMapping = {
                {TRIDA, TRIDA},
                {VZTAH, VZTAH},
                {VLASTNOST, VLASTNOST},
                {TSP, TSP},
                {TOP, TOP},
                {VEREJNY_UDAJ, VEREJNY_UDAJ},
                {NEVEREJNY_UDAJ, NEVEREJNY_UDAJ}
        };

        for (String[] mapping : typeMapping) {
            if (concept.hasProperty(RDF.type, ontModel.getResource(effectiveNamespace + mapping[0]))) {
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
