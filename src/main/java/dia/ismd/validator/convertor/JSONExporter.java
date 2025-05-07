package dia.ismd.validator.convertor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dia.ismd.validator.convertor.constants.ArchiOntologyConstants.*;

@Slf4j
class JSONExporter {

    private static final String FIELD_CONTEXT = "@context";
    private static final String FIELD_IRI = "iri";
    private static final String FIELD_TYP = "typ";
    private static final String FIELD_NAZEV = "název";
    private static final String FIELD_POPIS = "popis";
    private static final String FIELD_POJMY = "pojmy";

    private final OntModel ontModel;
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;

    public JSONExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties) {
        this.ontModel = ontModel;
        this.resourceMap = new HashMap<>();
        this.modelName = modelName;
        this.modelProperties = modelProperties;
    }

    public String exportToJson() throws JSONException {
        JSONObject unorderedRoot = new JSONObject();

        addModelMetadata(unorderedRoot);

        unorderedRoot.put(FIELD_POJMY, createConceptsArray());

        return formatJsonWithOrderedFields(unorderedRoot);
    }

    private String formatJsonWithOrderedFields(JSONObject unorderedRoot) throws JSONException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            Map<String, Object> originalMap = jsonToMap(unorderedRoot);

            Map<String, Object> orderedMap = new LinkedHashMap<>();

            addFieldIfExists(originalMap, orderedMap, FIELD_CONTEXT);
            addFieldIfExists(originalMap, orderedMap, FIELD_IRI);
            addFieldIfExists(originalMap, orderedMap, FIELD_TYP);

            containsFieldInMap(originalMap, orderedMap, FIELD_NAZEV);
            containsFieldInMap(originalMap, orderedMap, FIELD_POPIS);

            if (originalMap.containsKey(FIELD_POJMY)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pojmyList = (List<Map<String, Object>>) originalMap.get(FIELD_POJMY);
                List<Map<String, Object>> orderedPojmyList = new ArrayList<>();

                for (Map<String, Object> pojem : pojmyList) {
                    orderedPojmyList.add(orderPojemFields(pojem));
                }

                orderedMap.put(FIELD_POJMY, orderedPojmyList);
            } else {
                orderedMap.put(FIELD_POJMY, new ArrayList<>());
            }

            for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
                if (!orderedMap.containsKey(entry.getKey())) {
                    orderedMap.put(entry.getKey(), entry.getValue());
                }
            }

            return mapper.writeValueAsString(orderedMap);
        } catch (Exception e) {
            log.error("Error formatting JSON: {}", e.getMessage(), e);
            throw new JSONException("Error formatting JSON: " + e.getMessage());
        }
    }

    private void containsFieldInMap(Map<String, Object> originalMap, Map<String, Object> orderedMap, String fieldNazev) {
        if (originalMap.containsKey(fieldNazev)) {
            orderedMap.put(fieldNazev, originalMap.get(fieldNazev));
        } else {
            Map<String, String> emptyNazev = new LinkedHashMap<>();
            emptyNazev.put("cs", "");
            emptyNazev.put("en", "");
            orderedMap.put(fieldNazev, emptyNazev);
        }
    }

    private Map<String, Object> orderPojemFields(Map<String, Object> pojemMap) {
        Map<String, Object> orderedPojem = new LinkedHashMap<>();

        addFieldIfExists(pojemMap, orderedPojem, "iri");
        addFieldIfExists(pojemMap, orderedPojem, "typ");
        addFieldIfExists(pojemMap, orderedPojem, "název");
        addFieldIfExists(pojemMap, orderedPojem, "popis");
        addFieldIfExists(pojemMap, orderedPojem, "definice");

        for (Map.Entry<String, Object> entry : pojemMap.entrySet()) {
            if (!orderedPojem.containsKey(entry.getKey())) {
                orderedPojem.put(entry.getKey(), entry.getValue());
            }
        }

        return orderedPojem;
    }

    private Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
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
        root.put(FIELD_CONTEXT, CONTEXT);

        String modelIri = modelProperties.getOrDefault(LABEL_ALKD, NS);
        root.put(FIELD_IRI, modelIri);

        root.put(FIELD_TYP, addJSONtypes());

        JSONObject nameObj = new JSONObject();
        nameObj.put("cs", modelName);
        nameObj.put("en", "");
        root.put(FIELD_NAZEV, nameObj);

        JSONObject descObj = new JSONObject();
        descObj.put("cs", modelProperties.getOrDefault(LABEL_POPIS, ""));
        descObj.put("en", "");
        root.put(FIELD_POPIS, descObj);
    }

    private JSONArray createConceptsArray() throws JSONException {
        JSONArray pojmy = new JSONArray();

        ResIterator iter = ontModel.listSubjectsWithProperty(RDF.type, ontModel.getResource(NS + TYP_POJEM));

        while (iter.hasNext()) {
            Resource concept = iter.nextResource();

            if (concept.getURI().startsWith(NS)) {
                continue;
            }

            pojmy.put(createConceptObject(concept));
        }

        return pojmy;
    }

    private JSONObject createConceptObject(Resource concept) throws JSONException {
        JSONObject pojemObj = new JSONObject();

        pojemObj.put("iri", concept.getURI());

        pojemObj.put("typ", getConceptTypes(concept));

        addMultilingualProperty(concept, RDFS.label, FIELD_NAZEV, pojemObj);
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

        String[] typeNames = {TYP_TRIDA, TYP_VZTAH, TYP_VLASTNOST, TYP_TSP,
                TYP_TOP, TYP_VEREJNY_UDAJ, TYP_NEVEREJNY_UDAJ};
        String[] displayNames = {TYP_TRIDA, TYP_VZTAH, TYP_VLASTNOST, TYP_TSP,
                TYP_TOP, TYP_VEREJNY_UDAJ, TYP_NEVEREJNY_UDAJ};

        for (int i = 0; i < typeNames.length; i++) {
            if (concept.hasProperty(RDF.type, ontModel.getResource(NS + typeNames[i]))) {
                types.put(displayNames[i]);
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
        Property domainProp = ontModel.getProperty(NS + LABEL_DEF_O);
        Statement domainStmt = concept.getProperty(domainProp);
        if (domainStmt != null && domainStmt.getObject().isResource()) {
            pojemObj.put(LABEL_DEF_O, domainStmt.getObject().asResource().getURI());
        }

        Property rangeProp = ontModel.getProperty(NS + LABEL_OBOR_HODNOT);
        Statement rangeStmt = concept.getProperty(rangeProp);
        if (rangeStmt != null && rangeStmt.getObject().isResource()) {
            String rangeUri = rangeStmt.getObject().asResource().getURI();
            if (rangeUri.startsWith(XSD)) {
                String datatype = rangeUri.substring(XSD.length());
                pojemObj.put(LABEL_OBOR_HODNOT, "xsd:" + datatype);
            } else {
                pojemObj.put(LABEL_OBOR_HODNOT, rangeUri);
            }
        }
    }

    private void addRppMetadata(Resource concept, JSONObject pojemObj) throws JSONException {
        Property sharedProp = ontModel.getProperty(NS + LABEL_JE_PPDF);
        Statement sharedStmt = concept.getProperty(sharedProp);
        if (sharedStmt != null && sharedStmt.getObject().isLiteral()) {
            boolean isShared = sharedStmt.getBoolean();
            pojemObj.put("je-sdílen-v-ppdf", isShared);
        }

        Property aisProp = ontModel.getProperty(NS + LABEL_AIS);
        Statement aisStmt = concept.getProperty(aisProp);
        if (aisStmt != null && aisStmt.getObject().isResource()) {
            pojemObj.put(LABEL_AIS, aisStmt.getObject().asResource().getURI());
        }

        Property agendaProp = ontModel.getProperty(NS + LABEL_AGENDA);
        Statement agendaStmt = concept.getProperty(agendaProp);
        if (agendaStmt != null && agendaStmt.getObject().isResource()) {
            pojemObj.put(LABEL_AGENDA, agendaStmt.getObject().asResource().getURI());
        }

        addResourceArrayProperty(concept, ontModel.getProperty(NS + LABEL_UDN),
                LABEL_UDN, pojemObj);
    }
}
