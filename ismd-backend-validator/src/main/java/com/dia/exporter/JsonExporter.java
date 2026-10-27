package com.dia.exporter;

import com.dia.exception.JsonExportException;
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
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.MDC;

import java.util.*;

import com.dia.constants.ExportConstants;
import com.dia.constants.VocabularyConstants;

import static com.dia.constants.ExportConstants.Json.POPIS;
import static com.dia.constants.FormatConstants.Excel.*;
import static com.dia.constants.VocabularyConstants.*;
import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;
import static com.dia.constants.VocabularyConstants.AGENDA;
import static com.dia.constants.VocabularyConstants.AIS;
import static com.dia.constants.VocabularyConstants.DEFINICE;
import static com.dia.constants.VocabularyConstants.EKVIVALENTNI_POJEM;
import static com.dia.constants.VocabularyConstants.IDENTIFIKATOR;
import static com.dia.constants.VocabularyConstants.JE_PPDF;

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
            JSONArray conceptsArray = createConceptsArray();
            unorderedRoot.put(ExportConstants.Json.POJMY, conceptsArray);

            log.debug("Formatting and ordering JSON: requestId={}", requestId);
            return formatJsonWithOrderedFields(unorderedRoot);
        });
    }

    private void addModelMetadata(JSONObject root) throws JSONException {
        root.put(ExportConstants.Json.CONTEXT, CONTEXT);
        root.put(ExportConstants.Json.IRI, getOntologyIRI());
        root.put(ExportConstants.Json.TYP, addJSONtypes());

        if (modelName != null && !modelName.isEmpty()) {
            addMultilingualModelProperty(root, ExportConstants.Json.NAZEV, modelName);
        }

        String description = modelProperties.getOrDefault(POPIS, "");
        if (description != null && !description.isEmpty()) {
            addMultilingualModelProperty(root, POPIS, description);
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
        putTemporalNode(vocabularyResource.getProperty(creationProperty), ExportConstants.Json.VYTVORENO, root);
    }

    private void handleModificationDate(Resource vocabularyResource, Property modificationProperty, JSONObject root) {
        putTemporalNode(vocabularyResource.getProperty(modificationProperty), ExportConstants.Json.AKTUALIZOVANO, root);
    }

    /** Writes {"typ": "Časový okamžik", "datum_a_čas" | "datum": value}, as the context defines it. */
    private void putTemporalNode(Statement statement, String jsonKey, JSONObject root) {
        if (statement == null || !statement.getObject().isResource()) {
            return;
        }
        Resource instant = statement.getObject().asResource();
        JSONObject node = new JSONObject();
        node.put(ExportConstants.Json.TYP, ExportConstants.Json.CASOVY_OKAMZIK_JSON_LD);

        Statement dateTime = instant.getProperty(ontModel.getProperty(CAS_NS + VocabularyConstants.DATUM_A_CAS));
        Statement date = instant.getProperty(ontModel.getProperty(CAS_NS + VocabularyConstants.DATUM));
        if (dateTime != null && dateTime.getObject().isLiteral()) {
            node.put(ExportConstants.Json.DATUM_A_CAS, dateTime.getLiteral().getLexicalForm());
        } else if (date != null && date.getObject().isLiteral()) {
            node.put(ExportConstants.Json.DATUM, date.getLiteral().getLexicalForm());
        } else {
            return;
        }
        root.put(jsonKey, node);
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

        return effectiveNamespace;
    }

    private Map<String, Object> createOrderedModelMap(Map<String, Object> originalMap) {
        Map<String, Object> orderedMap = new LinkedHashMap<>();

        addFieldIfExists(originalMap, orderedMap, ExportConstants.Json.CONTEXT);
        addFieldIfExists(originalMap, orderedMap, ExportConstants.Json.IRI);
        addFieldIfExists(originalMap, orderedMap, ExportConstants.Json.TYP);

        addFieldWithDefault(originalMap, orderedMap, ExportConstants.Json.NAZEV, createEmptyMultilingualField());
        addFieldWithDefault(originalMap, orderedMap, POPIS, createEmptyMultilingualField());

        addFieldIfExists(originalMap, orderedMap, ExportConstants.Json.VYTVORENO);
        addFieldIfExists(originalMap, orderedMap, ExportConstants.Json.AKTUALIZOVANO);

        return orderedMap;
    }

    private void processConceptsArray(Map<String, Object> originalMap, Map<String, Object> orderedMap) {
        if (originalMap.containsKey(ExportConstants.Json.POJMY)) {
            Object pojmyObj = originalMap.get(ExportConstants.Json.POJMY);

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

                orderedMap.put(ExportConstants.Json.POJMY, orderedPojmyList);
            } else {
                log.warn("Expected pojmy to be a List but was: {}", pojmyObj.getClass().getName());
                orderedMap.put(ExportConstants.Json.POJMY, new ArrayList<>());
            }
        } else {
            orderedMap.put(ExportConstants.Json.POJMY, new ArrayList<>());
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

        for (String field : CONCEPT_FIELD_ORDER) {
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
        typArray.put(ExportConstants.Json.TYPE_SLOVNIK);
        typArray.put(ExportConstants.Json.TYPE_TEZAURUS);
        typArray.put(ExportConstants.Json.TYPE_KM);
        return typArray;
    }

    private JSONArray createConceptsArray() {
        if (ontModel == null || ontModel.isEmpty()) {
            throw new JsonExportException("Ontology model is null or empty.");
        }
        JSONArray pojmy = new JSONArray();
        Resource pojemType = ontModel.getResource(OFN_NAMESPACE + POJEM);

        ontModel.listSubjectsWithProperty(RDF.type, pojemType)
                .forEachRemaining(concept -> {
                    try {
                        if (belongsToCurrentVocabulary(concept.getURI())) {
                            pojmy.put(createConceptObject(concept));
                            log.debug("Exported concept to JSON: {}", concept.getURI());
                        } else {
                            log.debug("Skipped external reference concept in JSON export: {}", concept.getURI());
                        }
                    } catch (JSONException e) {
                        log.warn("Could not process concept: {}", concept.getURI(), e);
                    }
                });

        return pojmy;
    }

    private JSONObject createConceptObject(Resource concept) throws JSONException {
        JSONObject pojemObj = new JSONObject();

        pojemObj.put(JSON_IRI, concept.getURI());
        pojemObj.put(ExportConstants.Json.TYP, getConceptTypes(concept));

        addMultilingualProperty(concept, SKOS.prefLabel, ExportConstants.Json.NAZEV, pojemObj);

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

        addGovernanceProperties(concept, pojemObj);

        addRppMetadataWithBothNamespaces(concept, pojemObj, effectiveNamespace);

        addCodelist(concept, pojemObj);

        return pojemObj;
    }

    /** "instance-definovány-číselníkem": {"iri": …, "typ": "Číselník", "datová-sada-v-nkod": …} */
    private void addCodelist(Resource concept, JSONObject pojemObj) throws JSONException {
        Statement codelistStmt = concept.getProperty(ontModel.createProperty(SLOVNIKY_NS + MA_INSTANCE_DEFINOVANE_CISELNIKEM));
        if (codelistStmt == null || !codelistStmt.getObject().isURIResource()) {
            return;
        }
        Resource codelist = codelistStmt.getObject().asResource();
        JSONObject codelistObj = new JSONObject();
        codelistObj.put(ExportConstants.Json.IRI, codelist.getURI());
        codelistObj.put(ExportConstants.Json.TYP, CISELNIK_JSON_LD);

        Statement datasetStmt = codelist.getProperty(
                ontModel.createProperty(OFN_NAMESPACE_LEGAL + MA_V_NKOD_ZASTRESUJICI_DATOVOU_SADU));
        if (datasetStmt != null && datasetStmt.getObject().isURIResource()) {
            codelistObj.put(VocabularyConstants.DATOVA_SADA_V_NKOD, datasetStmt.getObject().asResource().getURI());
        }
        pojemObj.put(VocabularyConstants.INSTANCE_DEFINOVANY_CISELNIKEM, codelistObj);
    }

    private void addNewSourceProperties(Resource concept, JSONObject pojemObj) throws JSONException {
        addSourcePropertyFromEitherNamespace(concept, pojemObj, effectiveNamespace,
                DEFINUJICI_USTANOVENI, DEFINUJICI_USTANOVENI_PRAVNIHO_PREDPISU);

        addSourcePropertyFromEitherNamespace(concept, pojemObj, effectiveNamespace,
                SOUVISEJICI_USTANOVENI, SOUVISEJICI_USTANOVENI_PRAVNIHO_PREDPISU);

        addNonLegislativeSourceProperty(concept, pojemObj, effectiveNamespace,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ, DEFINUJICI_NELEGISLATIVNI_ZDROJ);

        addNonLegislativeSourceProperty(concept, pojemObj, effectiveNamespace,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ, SOUVISEJICI_NELEGISLATIVNI_ZDROJ);
    }

    private void addSourcePropertyFromEitherNamespace(Resource concept, JSONObject pojemObj, String namespace,
                                                      String propertyName, String jsonFieldName) throws JSONException {
        Property customProperty = ontModel.getProperty(namespace + propertyName);
        Property defaultProperty = ontModel.getProperty(DEFAULT_NS + propertyName);

        if (concept.hasProperty(customProperty)) {
            addResourceArrayProperty(concept, customProperty, jsonFieldName, pojemObj);
        } else if (concept.hasProperty(defaultProperty)) {
            addResourceArrayProperty(concept, defaultProperty, jsonFieldName, pojemObj);
        }
    }

    private void addNonLegislativeSourceProperty(Resource concept, JSONObject pojemObj, String namespace,
                                                 String propertyName, String jsonFieldName) throws JSONException {
        Property customProperty = ontModel.getProperty(namespace + propertyName);
        Property defaultProperty = ontModel.getProperty(DEFAULT_NS + propertyName);

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

            if (!propStmt.getObject().isResource()) {
                continue;
            }

            Resource digitalDoc = propStmt.getObject().asResource();
            JSONObject docObj = createDigitalDocumentObject(digitalDoc);

            if (docObj.length() > 0) {
                sourceArray.put(docObj);
            }
        }
    }

    private JSONObject createDigitalDocumentObject(Resource digitalDoc) {
        JSONObject docObj = new JSONObject();

        addDigitalObjectType(digitalDoc, docObj);
        addDocumentText(digitalDoc, DCTerms.title, ExportConstants.Json.NAZEV, docObj);
        addDocumentText(digitalDoc, DCTerms.description, ExportConstants.Json.POPIS, docObj);
        addDocumentUrl(digitalDoc, docObj);

        return docObj;
    }

    private void addDigitalObjectType(Resource digitalDoc, JSONObject docObj) {
        Resource digitalObjectType = ontModel.createResource(DIGITALNI_OBJEKT);
        if (digitalDoc.hasProperty(RDF.type, digitalObjectType)) {
            docObj.put(VocabularyConstants.TYP, "Digitální objekt");
        }
    }

    /**
     * Writes a digital object's title (název) or description (popis) as a language map;
     * values without a language tag are taken as Czech. A document without a URL is
     * described by these alone (see the OFN slovníky tezaurus example).
     */
    private void addDocumentText(Resource digitalDoc, Property property, String jsonKey, JSONObject docObj) {
        JSONObject langMap = new JSONObject();
        digitalDoc.listProperties(property).forEach(stmt -> {
            if (!stmt.getObject().isLiteral() || stmt.getString().isBlank()) {
                return;
            }
            String lang = stmt.getLanguage();
            langMap.put(lang == null || lang.isEmpty() ? "cs" : lang, stmt.getString());
        });
        if (langMap.length() > 0) {
            docObj.put(jsonKey, langMap);
        }
    }

    private void addDocumentUrl(Resource digitalDoc, JSONObject docObj) {
        Property schemaUrlProperty = ontModel.createProperty(SCHEMA_URL);
        Statement urlStmt = digitalDoc.getProperty(schemaUrlProperty);

        if (urlStmt == null) {
            return;
        }

        if (urlStmt.getObject().isResource()) {
            docObj.put("url", urlStmt.getObject().asResource().getURI());
        } else if (urlStmt.getObject().isLiteral()) {
            docObj.put("url", urlStmt.getString());
        }
    }

    private void addGovernanceProperties(Resource concept, JSONObject pojemObj) throws JSONException {
        addGovernancePropertyArrayWithFallback(concept, pojemObj);

        addGovernancePropertySingleWithFallback(concept, pojemObj, OFN_NAMESPACE + ZPUSOB_ZISKANI, ZPUSOB_ZISKANI_ALT);

        addGovernancePropertySingleWithFallback(concept, pojemObj, OFN_NAMESPACE + TYP_OBSAHU, TYP_OBSAHU_ALT);
    }

    private void addGovernancePropertyArrayWithFallback(Resource concept, JSONObject pojemObj) throws JSONException {

        Property excelProperty = ontModel.getProperty(OFN_NAMESPACE + ZPUSOB_SDILENI);
        if (concept.hasProperty(excelProperty)) {
            addGovernancePropertyArray(concept, excelProperty, pojemObj);
        }
    }

    private void addGovernancePropertySingleWithFallback(Resource concept, JSONObject pojemObj, String property, String jsonFieldName) throws JSONException {
        Property excelProperty = ontModel.getProperty(property);
        if (concept.hasProperty(excelProperty)) {
            addGovernancePropertySingle(concept, excelProperty, jsonFieldName, pojemObj);
        }
    }

    private void addGovernancePropertyArray(Resource concept, Property property,
                                            JSONObject pojemObj) throws JSONException {
        List<String> allValues = extractGovernancePropertyValues(concept, property);

        if (!allValues.isEmpty()) {
            JSONArray propArray = createJsonArray(allValues);
            pojemObj.put(ZPUSOBY_SDILENI_ALT, propArray);
        }
    }

    private void addGovernancePropertySingle(Resource concept, Property property, String jsonFieldName,
                                            JSONObject pojemObj) throws JSONException {
        Statement stmt = concept.getProperty(property);
        if (stmt != null) {
            String value = extractStatementValue(stmt);
            if (value != null && !value.trim().isEmpty()) {
                pojemObj.put(jsonFieldName, value);
            }
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
            return toGovernanceCurie(statement.getString());
        } else if (statement.getObject().isResource()) {
            return toGovernanceCurie(statement.getObject().asResource().getURI());
        }
        return null;
    }

    /**
     * Compacts a governance codelist IRI to its JSON-LD CURIE form
     * (e.g. {@code https://data.dia.gov.cz/.../způsoby-získání-údajů/položky/vlastní}
     * becomes {@code způsoby-získání:vlastní}). Prefixes match the JSON-LD context
     * (kontext.jsonld). Values that do not start with a known governance namespace
     * are returned unchanged.
     */
    private String toGovernanceCurie(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith(ExportConstants.Turtle.NS_ZPUSOBY_ZISKANI)) {
            return ExportConstants.Json.PREFIX_ZPUSOBY_ZISKANI + ":"
                    + value.substring(ExportConstants.Turtle.NS_ZPUSOBY_ZISKANI.length());
        }
        if (value.startsWith(ExportConstants.Turtle.NS_ZPUSOBY_SDILENI)) {
            return ExportConstants.Json.PREFIX_ZPUSOBY_SDILENI + ":"
                    + value.substring(ExportConstants.Turtle.NS_ZPUSOBY_SDILENI.length());
        }
        if (value.startsWith(ExportConstants.Turtle.NS_TYPY_OBSAHU)) {
            return ExportConstants.Json.PREFIX_TYPY_OBSAHU + ":"
                    + value.substring(ExportConstants.Turtle.NS_TYPY_OBSAHU.length());
        }
        return value;
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
        Property suppDefault = ontModel.getProperty(DEFAULT_NS + VocabularyConstants.USTANOVENI_NEVEREJNOST);
        Property suppCustom = ontModel.getProperty(namespace + VocabularyConstants.USTANOVENI_NEVEREJNOST);
        if (concept.hasProperty(suppDefault)) {
            addResourceArrayProperty(concept, suppDefault, VocabularyConstants.USTANOVENI_NEVEREJNOST, pojemObj);
        } else if (concept.hasProperty(suppCustom)) {
            addResourceArrayProperty(concept, suppCustom, VocabularyConstants.USTANOVENI_NEVEREJNOST, pojemObj);
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
                    exactMatchArray.put(exactMatchStmt.getObject().asResource().getURI());
                } else if (exactMatchStmt.getObject().isLiteral()) {
                    String literalValue = exactMatchStmt.getString();
                    if (literalValue != null && !literalValue.trim().isEmpty()) {
                        exactMatchArray.put(literalValue);
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

            if (rangeUri.startsWith(XSD)) {
                pojemObj.put(OBOR_HODNOT, "xsd:" + rangeUri.substring(XSD.length()));
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
        StmtIterator stmtIter = getAlternativeNamesIterator(concept);

        if (!stmtIter.hasNext()) {
            return;
        }

        JSONObject altNamesObj = new JSONObject();
        boolean hasNonEmptyValue = false;

        while (stmtIter.hasNext()) {
            Statement stmt = stmtIter.next();
            String value = stmt.getString();

            if (value == null || value.isEmpty()) {
                continue;
            }

            String lang = getLanguageOrDefault(stmt);
            addValueToAltNamesObject(altNamesObj, lang, value);
            hasNonEmptyValue = true;
        }

        if (hasNonEmptyValue && altNamesObj.length() > 0) {
            pojemObj.put(ALTERNATIVNI_NAZEV, altNamesObj);
        }
    }

    private StmtIterator getAlternativeNamesIterator(Resource concept) {
        Property anPropDefault = ontModel.getProperty(DEFAULT_NS + ALTERNATIVNI_NAZEV);
        StmtIterator stmtIter = concept.listProperties(anPropDefault);

        if (!stmtIter.hasNext()) {
            Property anPropCustom = ontModel.getProperty(effectiveNamespace + ALTERNATIVNI_NAZEV);
            stmtIter = concept.listProperties(anPropCustom);
        }

        return stmtIter;
    }

    private String getLanguageOrDefault(Statement stmt) {
        String lang = stmt.getLanguage();
        return (lang == null || lang.isEmpty()) ? "cs" : lang;
    }

    private void addValueToAltNamesObject(JSONObject altNamesObj, String lang, String value) throws JSONException {
        if (!altNamesObj.has(lang)) {
            JSONArray langArray = new JSONArray();
            langArray.put(value);
            altNamesObj.put(lang, langArray);
            return;
        }

        Object existingValue = altNamesObj.get(lang);
        if (existingValue instanceof JSONArray jsonArray) {
            jsonArray.put(value);
        }
    }

    private void addSingleResourcePropertyFromEitherNamespace(Resource concept, JSONObject pojemObj,
                                                              String namespace, String labelDefO) throws JSONException {
        Property domainDefault = ontModel.getProperty(DEFAULT_NS + labelDefO);
        Property domainCustom = ontModel.getProperty(namespace + labelDefO);

        if (concept.hasProperty(domainDefault)) {
            addResourceProperty(concept, DEFAULT_NS + labelDefO, labelDefO, pojemObj);
        } else if (concept.hasProperty(domainCustom)) {
            addResourceProperty(concept, namespace + labelDefO, labelDefO, pojemObj);
        }
    }

    private void addRppMetadataWithBothNamespaces(Resource concept, JSONObject pojemObj,
                                                  String namespace) throws JSONException {
        Property ppdfDefault = ontModel.getProperty(DEFAULT_NS + JE_PPDF);
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
        types.put(ExportConstants.Json.POJEM_JSON_LD);
        types.put(ExportConstants.Json.KONCEPT_JSON_LD);

        String[][] typeMapping = {
                {TRIDA, ExportConstants.Json.TRIDA_JSON_LD},
                {VZTAH, ExportConstants.Json.VZTAH_JSON_LD},
                {VLASTNOST, ExportConstants.Json.VLASTNOST_JSON_LD},
                {TSP, ExportConstants.Json.TSP_JSON_LD},
                {TOP, ExportConstants.Json.TOP_JSON_LD},
                {VEREJNY_UDAJ, ExportConstants.Json.VEREJNY_UDAJ_JSON_LD},
                {NEVEREJNY_UDAJ, ExportConstants.Json.NEVEREJNY_UDAJ_JSON_LD}
        };

        for (String[] mapping : typeMapping) {
            if (concept.hasProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE + mapping[0]))) {
                types.put(mapping[1]);
            }
            if (concept.hasProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_VS + mapping[0]))) {
                types.put(mapping[1]);
            }
            if (concept.hasProperty(RDF.type, ontModel.getResource(OFN_NAMESPACE_LEGAL + mapping[0]))) {
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

    private boolean belongsToCurrentVocabulary(String conceptURI) {
        if (conceptURI == null) {
            return false;
        }
        String ontologyIRI = getOntologyIRI();
        if (ontologyIRI == null) {
            return false;
        }
        String localPrefix = ontologyIRI + "/pojem/";
        boolean belongs = conceptURI.startsWith(localPrefix);
        log.debug("Local-concept check for {}: belongs = {} (local prefix: {})",
                conceptURI, belongs, localPrefix);
        return belongs;
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
