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

    public JSONExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties) {
        this.ontModel = ontModel;
        this.resourceMap = new HashMap<>(resourceMap);
        this.modelName = modelName;
        this.modelProperties = modelProperties;
    }

    public String exportToJson() {
        return handleJsonOperation(() -> {
            JSONObject unorderedRoot = new JSONObject();
            addModelMetadata(unorderedRoot);
            unorderedRoot.put(JSON_FIELD_POJMY, createConceptsArray());
            return formatJsonWithOrderedFields(unorderedRoot);
        });
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

    private Map<String, Object> createOrderedModelMap(Map<String, Object> originalMap) {
        Map<String, Object> orderedMap = new LinkedHashMap<>();

        addFieldIfExists(originalMap, orderedMap, JSON_FIELD_CONTEXT);
        addFieldIfExists(originalMap, orderedMap, JSON_FIELD_IRI);
        addFieldIfExists(originalMap, orderedMap, JSON_FIELD_TYP);

        addFieldWithDefault(originalMap, orderedMap, JSON_FIELD_NAZEV, createEmptyMultilingualField());
        addFieldWithDefault(originalMap, orderedMap, JSON_FIELD_POPIS, createEmptyMultilingualField());

        return orderedMap;
    }

    private Map<String, String> createEmptyMultilingualField() {
        Map<String, String> emptyField = new LinkedHashMap<>();
        emptyField.put("cs", "");
        emptyField.put("sk", "");
        emptyField.put("en", "");
        return emptyField;
    }

    private void addFieldWithDefault(Map<String, Object> source, Map<String, Object> target,
                                     String fieldName, Object defaultValue) {
        target.put(fieldName, source.getOrDefault(fieldName, defaultValue));
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
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new JsonExportException("Error converting map to JSON: " + e.getMessage());
        }
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

    private void addModelMetadata(JSONObject root) throws JSONException {
        root.put(JSON_FIELD_CONTEXT, CONTEXT);
        root.put(JSON_FIELD_IRI, modelProperties.getOrDefault(LABEL_ALKD, NS));

        root.put(JSON_FIELD_TYP, addJSONtypes());

        addMultilingualModelProperty(root, JSON_FIELD_NAZEV, modelName);
        addMultilingualModelProperty(root, JSON_FIELD_POPIS,
                modelProperties.getOrDefault(LABEL_POPIS, ""));
    }

    private void addMultilingualModelProperty(JSONObject root, String propertyName,
                                              String csValue) throws JSONException {
        JSONObject propObj = new JSONObject();
        propObj.put("cs", csValue);
        propObj.put("en", "");
        root.put(propertyName, propObj);
    }

    private JSONArray createConceptsArray() {
        JSONArray pojmy = new JSONArray();
        Resource pojemType = ontModel.getResource(NS + TYP_POJEM);

        ontModel.listSubjectsWithProperty(RDF.type, pojemType)
                .filterKeep(concept -> !concept.getURI().startsWith(NS))
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

        pojemObj.put("iri", concept.getURI());

        pojemObj.put("typ", getConceptTypes(concept));

        addMultilingualProperty(concept, RDFS.label, JSON_FIELD_NAZEV, pojemObj);
        addMultilingualProperty(concept, ontModel.getProperty(NS + LABEL_DEF), LABEL_DEF, pojemObj);
        addMultilingualProperty(concept, ontModel.getProperty(NS + LABEL_POPIS), LABEL_POPIS, pojemObj);

        addResourceArrayProperty(concept, ontModel.getProperty(NS + LABEL_SUPP),
                LABEL_SUPP, pojemObj);

        addDomainAndRange(concept, pojemObj);

        addResourceArrayProperty(concept, ontModel.getProperty(NS + LABEL_NT),
                LABEL_NT, pojemObj);

        addRppMetadata(concept, pojemObj);

        return pojemObj;
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
            if (concept.hasProperty(RDF.type, ontModel.getResource(NS + mapping[0]))) {
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
            while (propIter.hasNext()) {
                Statement propStmt = propIter.next();
                String lang = propStmt.getLanguage();
                if (lang != null && !lang.isEmpty()) {
                    propObj.put(lang, propStmt.getString());
                } else {
                    propObj.put("cs", propStmt.getString());
                }
            }
            if (propObj.length() != 0) {
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

    private void addDomainAndRange(Resource concept, JSONObject pojemObj) throws JSONException {
        addResourceProperty(concept, NS + LABEL_DEF_O, LABEL_DEF_O, pojemObj);
        addRangeProperty(concept, pojemObj);
    }

    private void addRangeProperty(Resource concept, JSONObject pojemObj) throws JSONException {
        Property rangeProp = ontModel.getProperty(NS + LABEL_OBOR_HODNOT);
        Statement rangeStmt = concept.getProperty(rangeProp);

        if (rangeStmt != null && rangeStmt.getObject().isResource()) {
            String rangeUri = rangeStmt.getObject().asResource().getURI();

            if (rangeUri.startsWith(XSD)) {
                pojemObj.put(LABEL_OBOR_HODNOT, "xsd:" + rangeUri.substring(XSD.length()));
            } else {
                pojemObj.put(LABEL_OBOR_HODNOT, rangeUri);
            }
        }
    }

    private void addRppMetadata(Resource concept, JSONObject pojemObj) throws JSONException {
        addBooleanProperty(concept, pojemObj);
        addResourceProperty(concept, NS + LABEL_AIS, LABEL_AIS, pojemObj);
        addResourceProperty(concept, NS + LABEL_AGENDA, LABEL_AGENDA, pojemObj);
        addResourceArrayProperty(concept, ontModel.getProperty(NS + LABEL_UDN), LABEL_UDN, pojemObj);
    }

    private void addBooleanProperty(Resource concept, JSONObject targetObj) throws JSONException {
        Property property = ontModel.getProperty(LABEL_JE_PPDF);
        Statement stmt = concept.getProperty(property);
        if (stmt != null && stmt.getObject().isLiteral()) {
            boolean value = stmt.getBoolean();
            targetObj.put(LABEL_JE_PPDF, value);
        }
    }

    private void addResourceProperty(Resource concept, String propertyUri, String jsonProperty, JSONObject targetObj) throws JSONException {
        Property property = ontModel.getProperty(propertyUri);
        Statement stmt = concept.getProperty(property);
        if (stmt != null && stmt.getObject().isResource()) {
            targetObj.put(jsonProperty, stmt.getObject().asResource().getURI());
        }
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
