package dia.ismd.validator.convertor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dia.ismd.common.exceptions.JsonExportException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dia.ismd.validator.convertor.constants.ArchiOntologyConstants.*;
import static dia.ismd.validator.convertor.constants.JsonExportConstants.*;

@Slf4j
class JSONExporter {

    private final OntModel ontModel;
    @Getter
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;
    private final String effectiveNamespace;

    public JSONExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties, String effectiveNamespace) {
        this.ontModel = ontModel;
        this.resourceMap = new HashMap<>(resourceMap);
        this.modelName = modelName;
        this.modelProperties = modelProperties;
        this.effectiveNamespace = effectiveNamespace;
    }

    public String exportToJson() {
        return handleJsonOperation(() -> {
            JSONObject unorderedRoot = new JSONObject();
            addModelMetadata(unorderedRoot);
            unorderedRoot.put(JSON_FIELD_POJMY, createConceptsArray());
            return formatJsonWithOrderedFields(unorderedRoot);
        });
    }

    private void addModelMetadata(JSONObject root) throws JSONException {
        root.put(JSON_FIELD_CONTEXT, CONTEXT);
        root.put(JSON_FIELD_IRI, modelProperties.getOrDefault(LABEL_ALKD, effectiveNamespace));

        root.put(JSON_FIELD_TYP, addJSONtypes());

        addMultilingualModelProperty(root, JSON_FIELD_NAZEV, modelName);
        addMultilingualModelProperty(root, JSON_FIELD_POPIS,
                modelProperties.getOrDefault(LABEL_POPIS, null));
    }

    private void addMultilingualModelProperty(JSONObject root, String propertyName,
                                              String csValue) throws JSONException {
        if (csValue == null || csValue.isEmpty()) {
            return;
        }

        JSONObject propObj = new JSONObject();
        propObj.put("cs", csValue);
        root.put(propertyName, propObj);
    }

    private String formatJsonWithOrderedFields(JSONObject unorderedRoot) throws JsonExportException {
        try {
            Map<String, Object> originalMap = jsonToMap(unorderedRoot);

            Map<String, Object> orderedMap = createOrderedModelMap(originalMap);

            processConceptsArray(originalMap, orderedMap);

            addRemainingFields(originalMap, orderedMap);

            return convertMapToFormattedJson(orderedMap);
        } catch (JSONException e) {
            log.error("Error parsing JSON: {}", e.getMessage(), e);
            throw new JsonExportException("Error parsing JSON: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error formatting JSON: {}", e.getMessage(), e);
            throw new JsonExportException("Error formatting JSON: " + e.getMessage());
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

    private Map<String, Object> createOrderedModelMap(Map<String, Object> originalMap) {
        Map<String, Object> orderedMap = new LinkedHashMap<>();

        addFieldIfExists(originalMap, orderedMap, JSON_FIELD_CONTEXT);
        addFieldIfExists(originalMap, orderedMap, JSON_FIELD_IRI);
        addFieldIfExists(originalMap, orderedMap, JSON_FIELD_TYP);

        addFieldWithDefault(originalMap, orderedMap, JSON_FIELD_NAZEV, createEmptyMultilingualField());
        addFieldWithDefault(originalMap, orderedMap, JSON_FIELD_POPIS, createEmptyMultilingualField());

        return orderedMap;
    }

    private void processConceptsArray(Map<String, Object> originalMap, Map<String, Object> orderedMap) {
        if (originalMap.containsKey(JSON_FIELD_POJMY)) {
            Object pojmyObj = originalMap.get(JSON_FIELD_POJMY);

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

                orderedMap.put(JSON_FIELD_POJMY, orderedPojmyList);
            } else {
                log.warn("Expected pojmy to be a List but was: {}", pojmyObj.getClass().getName());
                orderedMap.put(JSON_FIELD_POJMY, new ArrayList<>());
            }
        } else {
            orderedMap.put(JSON_FIELD_POJMY, new ArrayList<>());
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
            Map<String, Object> filteredMap = filterEmptyValues(map);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(filteredMap);
        } catch (Exception e) {
            throw new JsonExportException("Error converting map to JSON: " + e.getMessage());
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

        String[] orderedFields = {"iri", "typ", "název", "popis", "definice"};

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
        typArray.put("Slovník");
        typArray.put("Tezaurus");
        typArray.put("Konceptuální model");
        return typArray;
    }

    private JSONArray createConceptsArray() {
        JSONArray pojmy = new JSONArray();
        Resource pojemType = ontModel.getResource(effectiveNamespace + TYP_POJEM);

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

        addMultilingualProperty(concept, RDFS.label, JSON_FIELD_NAZEV, pojemObj);

        addMultilingualPropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_DEF);

        addMultilingualPropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_POPIS);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_SUPP);

        addDomainAndRangeWithBothNamespaces(concept, pojemObj, namespace);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_NT);

        addRppMetadataWithBothNamespaces(concept, pojemObj, namespace);

        return pojemObj;
    }

    private void addResourceArrayPropertyFromEitherNamespace(Resource concept, JSONObject pojemObj, String namespace, String labelSupp) throws JSONException {
        Property suppDefault = ontModel.getProperty(NS + labelSupp);
        Property suppCustom = ontModel.getProperty(namespace + labelSupp);
        if (concept.hasProperty(suppDefault)) {
            addResourceArrayProperty(concept, suppDefault, labelSupp, pojemObj);
        } else if (concept.hasProperty(suppCustom)) {
            addResourceArrayProperty(concept, suppCustom, labelSupp, pojemObj);
        }
    }

    private void addMultilingualPropertyFromEitherNamespace(Resource concept, JSONObject pojemObj, String namespace, String labelDef) throws JSONException {
        Property langDefault = ontModel.getProperty(NS + labelDef);
        Property langCustom = ontModel.getProperty(namespace + labelDef);
        if (concept.hasProperty(langDefault)) {
            addMultilingualProperty(concept, langDefault, labelDef, pojemObj);
        } else if (concept.hasProperty(langCustom)) {
            addMultilingualProperty(concept, langCustom, labelDef, pojemObj);
        }
    }

    private void addDomainAndRangeWithBothNamespaces(Resource concept, JSONObject pojemObj, String namespace) throws JSONException {
        addSingleResourcePropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_DEF_O);

        addRangePropertyWithBothNamespaces(concept, pojemObj, namespace);
    }

    private void addSingleResourcePropertyFromEitherNamespace(Resource concept, JSONObject pojemObj, String namespace, String labelDefO) throws JSONException {
        Property domainDefault = ontModel.getProperty(NS + labelDefO);
        Property domainCustom = ontModel.getProperty(namespace + labelDefO);

        if (concept.hasProperty(domainDefault)) {
            addResourceProperty(concept, NS + labelDefO, labelDefO, pojemObj);
        } else if (concept.hasProperty(domainCustom)) {
            addResourceProperty(concept, namespace + labelDefO, labelDefO, pojemObj);
        }
    }

    private void addRangePropertyWithBothNamespaces(Resource concept, JSONObject pojemObj, String namespace) throws JSONException {
        Property rangeDefault = ontModel.getProperty(NS + LABEL_OBOR_HODNOT);
        Property rangeCustom = ontModel.getProperty(namespace + LABEL_OBOR_HODNOT);

        Statement rangeStmt = concept.getProperty(rangeDefault);
        if (rangeStmt == null) {
            rangeStmt = concept.getProperty(rangeCustom);
        }

        if (rangeStmt != null && rangeStmt.getObject().isResource()) {
            String rangeUri = rangeStmt.getObject().asResource().getURI();

            if (rangeUri.startsWith(XSD)) {
                pojemObj.put(LABEL_OBOR_HODNOT, "xsd:" + rangeUri.substring(XSD.length()));
            } else {
                pojemObj.put(LABEL_OBOR_HODNOT, rangeUri);
            }
        }
    }

    private void addRppMetadataWithBothNamespaces(Resource concept, JSONObject pojemObj, String namespace) throws JSONException {
        Property ppdfDefault = ontModel.getProperty(NS + LABEL_JE_PPDF);
        Property ppdfCustom = ontModel.getProperty(namespace + LABEL_JE_PPDF);

        Statement stmt = concept.getProperty(ppdfDefault);
        if (stmt == null) {
            stmt = concept.getProperty(ppdfCustom);
        }

        if (stmt != null && stmt.getObject().isLiteral()) {
            boolean value = stmt.getBoolean();
            pojemObj.put(LABEL_JE_PPDF, value);
        }

        addSingleResourcePropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_AIS);

        addSingleResourcePropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_AGENDA);

        addResourceArrayPropertyFromEitherNamespace(concept, pojemObj, namespace, LABEL_UDN);
    }

    private JSONArray getConceptTypes(Resource concept) {
        JSONArray types = new JSONArray();
        types.put(TYP_POJEM);

        String[][] typeMapping = {
                {TYP_TRIDA, TYP_TRIDA},
                {TYP_VZTAH, TYP_VZTAH},
                {TYP_VLASTNOST, TYP_VLASTNOST},
                {TYP_TSP, TYP_TSP},
                {TYP_TOP, TYP_TOP},
                {TYP_VEREJNY_UDAJ, TYP_VEREJNY_UDAJ},
                {TYP_NEVEREJNY_UDAJ, TYP_NEVEREJNY_UDAJ}
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

    private void addResourceProperty(Resource concept, String propertyUri, String jsonProperty, JSONObject targetObj) throws JSONException {
        Property property = ontModel.getProperty(propertyUri);
        Statement stmt = concept.getProperty(property);
        if (stmt != null && stmt.getObject().isResource()) {
            targetObj.put(jsonProperty, stmt.getObject().asResource().getURI());
        }
    }

    private Map<String, Object> filterEmptyValues(Map<String, Object> map) {
        Map<String, Object> filteredMap = new LinkedHashMap<>();

        map.forEach((key, value) -> {
            if (value != null) {
                if (value instanceof String str && !str.isEmpty()) {
                    filteredMap.put(key, value);
                } else if (value instanceof Map<?, ?> innerMap) {
                    Map<String, Object> filteredInnerMap = filterEmptyValues((Map<String, Object>) innerMap);
                    if (!filteredInnerMap.isEmpty()) {
                        filteredMap.put(key, filteredInnerMap);
                    }
                } else if (value instanceof List<?> list) {
                    List<Object> filteredList = filterEmptyList(list);
                    if (!filteredList.isEmpty()) {
                        filteredMap.put(key, filteredList);
                    }
                } else {
                    filteredMap.put(key, value);
                }
            }
        });

        return filteredMap;
    }

    private List<Object> filterEmptyList(List<?> list) {
        List<Object> filteredList = new ArrayList<>();

        for (Object item : list) {
            if (item != null) {
                if (item instanceof String str && !str.isEmpty()) {
                    filteredList.add(item);
                } else if (item instanceof Map<?, ?> map) {
                    Map<String, Object> filteredMap = filterEmptyValues((Map<String, Object>) map);
                    if (!filteredMap.isEmpty()) {
                        filteredList.add(filteredMap);
                    }
                } else if (item instanceof List<?> innerList) {
                    List<Object> filteredInnerList = filterEmptyList(innerList);
                    if (!filteredInnerList.isEmpty()) {
                        filteredList.add(filteredInnerList);
                    }
                } else {
                    filteredList.add(item);
                }
            }
        }

        return filteredList;
    }

    private <T> T handleJsonOperation(JsonSupplier<T> operation) throws JsonExportException {
        try {
            return operation.get();
        } catch (JsonExportException e) {
            log.error("{}: {}", "Error exporting to JSON", e.getMessage(), e);
            throw new JsonExportException("Error exporting to JSON" + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during JSON operation: {}", e.getMessage(), e);
            throw new JsonExportException("Error during JSON processing: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface JsonSupplier<T> {
        T get() throws JsonExportException, JSONException;
    }
}
