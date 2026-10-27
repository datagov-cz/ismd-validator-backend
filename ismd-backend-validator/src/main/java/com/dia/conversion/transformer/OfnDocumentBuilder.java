package com.dia.conversion.transformer;

import com.dia.constants.DataTypeConstants;
import com.dia.constants.ExportConstants;
import com.dia.conversion.data.*;
import com.dia.ofn.ConformanceIssue;
import com.dia.ofn.ConformanceIssue.Kind;
import com.dia.ofn.OfnDocumentOrder;
import com.dia.ofn.OfnSpecification;
import com.dia.utility.DataTypeConverter;
import com.dia.utility.URIGenerator;
import com.dia.utility.UtilityMethods;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.dia.ofn.OfnTerms.*;

/**
 * Builds the OFN slovníky document (JSON-LD in the terms of the bundled context) from the data the
 * readers extracted. The document is the single source for every output: the conformance gate
 * filters it, the JSON-LD export writes it, and the RDF model (Turtle, SHACL) is read from it.
 * <p>
 * This class holds the input knowledge (how a source cell becomes a legal provision or a digital
 * object, what "Ano" means, which agenda codes are valid). Whatever it cannot map is reported as a
 * {@link ConformanceIssue}, not silently lost.
 */
@Slf4j
@Component
public class OfnDocumentBuilder {

    private static final String OPENDATA_ESEL = "https://opendata.eselpoint.cz/esel-esb/";
    private static final String LANG = "cs";
    private static final List<String> GOVERNANCE_VALUES = List.of(
            "veřejně přístupné", "poskytované na žádost", "nesdílené", "zpřístupňované pro výkon agendy",
            "základních registrů", "jiných agend", "vlastní", "provozní", "identifikační", "evidenční",
            "statistické");
    private static final Map<String, String> CZECH_TO_XSD = Map.of(
            "Ano či ne", DataTypeConstants.XSD_BOOLEAN,
            "Datum", DataTypeConstants.XSD_DATE,
            "Čas", DataTypeConstants.XSD_TIME,
            "Datum a čas", DataTypeConstants.XSD_DATETIME_STAMP,
            "Celé číslo", DataTypeConstants.XSD_INTEGER,
            "Desetinné číslo", DataTypeConstants.XSD_DOUBLE,
            "URI, IRI, URL", DataTypeConstants.XSD_ANY_URI,
            "Řetězec", DataTypeConstants.XSD_STRING,
            "Text", DataTypeConstants.RDFS_LITERAL
    );

    private final ConceptFilterUtil conceptFilterUtil;
    private final OfnSpecification specification;

    public OfnDocumentBuilder(ConceptFilterUtil conceptFilterUtil, OfnSpecification specification) {
        this.conceptFilterUtil = conceptFilterUtil;
        this.specification = specification;
    }

    /** The document and what could not be mapped to it. */
    public record Result(ObjectNode document, List<ConformanceIssue> issues) {
    }

    public Result build(OntologyData data, String effectiveNamespace) {
        return new Run(data, effectiveNamespace).build();
    }

    private enum ConceptKind { CLASS, PROPERTY, RELATIONSHIP }

    /** State of one build; the builder itself is a stateless singleton. */
    private final class Run {

        private final OntologyData data;
        private final URIGenerator uriGenerator = new URIGenerator();
        private final List<ConformanceIssue> issues = new ArrayList<>();
        private final ConceptFilterUtil.FilterStatistics filterStatistics = new ConceptFilterUtil.FilterStatistics();
        private final Map<String, String> nameToIdentifier;
        /** Element name → concept IRI, for references by name (domain, range, hierarchy). */
        private final Map<String, String> nameToIri = new HashMap<>();
        /** Local concepts by IRI, in input order. */
        private final Map<String, ObjectNode> concepts = new LinkedHashMap<>();
        private final Map<String, ConceptKind> kinds = new HashMap<>();
        private final Map<String, String> owners = new HashMap<>();
        private String localPrefix;

        Run(OntologyData data, String effectiveNamespace) {
            this.data = data;
            uriGenerator.setEffectiveNamespace(effectiveNamespace);
            uriGenerator.setVocabularyName(data.getVocabularyMetadata().getName());
            this.nameToIdentifier = conceptFilterUtil.buildNameToIdentifierMap(data);
            conceptFilterUtil.buildFilteredConceptSet(data, filterStatistics);
        }

        Result build() {
            ObjectNode document = vocabulary(data.getVocabularyMetadata());
            data.getClasses().forEach(this::addClass);
            data.getProperties().forEach(this::addProperty);
            data.getRelationships().forEach(this::addRelationship);
            data.getHierarchies().forEach(this::addHierarchy);

            ArrayNode pojmy = document.putArray(OfnSpecification.CONCEPTS_KEY);
            concepts.values().forEach(pojmy::add);

            if (conceptFilterUtil.getConceptFilterPattern() != null) {
                filterStatistics.logStatistics();
            }
            log.info("OFN document built: {} concepts, {} input issue(s)", concepts.size(), issues.size());
            return new Result(new OfnDocumentOrder(specification.contextIndex()).apply(document), issues);
        }

        // ------------------------------------------------------------ vocabulary

        private ObjectNode vocabulary(VocabularyMetadata metadata) {
            String iri = uriGenerator.generateVocabularyURI(metadata.getName(), metadata.getIdentifier());
            localPrefix = iri + "/pojem/";

            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("@context", specification.contextUrl());
            node.put(IRI, iri);
            node.putArray(TYP).add(SLOVNIK).add(TEZAURUS).add(KONCEPTUALNI_MODEL);
            putText(node, NAZEV, metadata.getName());
            putText(node, POPIS, metadata.getDescription());
            putInstant(node, VYTVORENO, metadata.getDateOfCreation());
            putInstant(node, AKTUALIZOVANO, metadata.getDateOfModification());
            return node;
        }

        /** {"typ": "Časový okamžik", "datum_a_čas" | "datum": value}, as the context defines it. */
        private void putInstant(ObjectNode node, String key, String value) {
            if (isBlank(value)) {
                return;
            }
            String trimmed = value.trim();
            ObjectNode instant = node.putObject(key);
            instant.put(TYP, CASOVY_OKAMZIK);
            instant.put(trimmed.contains("T") ? DATUM_A_CAS : DATUM, trimmed);
        }

        // ------------------------------------------------------------ classes

        private void addClass(ClassData classData) {
            if (!classData.hasValidData()) {
                issues.add(new ConformanceIssue(Kind.CONCEPT_EXCLUDED, null, "třída " + classData.getId(),
                        "třída nemá název"));
                return;
            }
            if (conceptFilterUtil.shouldFilterConcept(classData.getIdentifier())) {
                log.info("FILTERED: Class '{}' with identifier '{}' matches filter regex",
                        classData.getName(), classData.getIdentifier());
                filterStatistics.filteredClasses++;
                return;
            }

            String iri = uriGenerator.generateConceptURI(classData.getName(), classData.getIdentifier());
            nameToIri.put(classData.getName(), iri);
            ObjectNode concept = newConcept(iri, ConceptKind.CLASS, "třída '" + classData.getName() + "'");
            if (concept == null) {
                return;
            }

            ArrayNode types = concept.putArray(TYP).add(POJEM).add(KONCEPT).add(TRIDA);
            if ("Subjekt práva".equals(classData.getType())) {
                types.add(TYP_SUBJEKTU_PRAVA);
            } else if ("Objekt práva".equals(classData.getType())) {
                types.add(TYP_OBJEKTU_PRAVA);
            }

            addCommon(concept, classData.getName(), classData.getDescription(), classData.getDefinition(),
                    classData.getSource(), classData.getRelatedSource(), classData.getAlternativeName(),
                    classData.getEquivalentConcept(), classData.getBroaderConcepts());
            addAgenda(concept, classData.getAgendaCode());
            addAgendaSystem(concept, classData.getAgendaSystemCode());
            addCodelist(concept, classData);
            addGovernance(concept, classData.getIsPublic(), classData.getPrivacyProvision(),
                    classData.getSharingMethod(), classData.getAcquisitionMethod(), classData.getContentType());
        }

        private void addCodelist(ObjectNode concept, ClassData classData) {
            String codelistIri = trimToNull(classData.getCodelistIri());
            String datasetIri = trimToNull(classData.getCodelistNkodDataset());
            if (codelistIri == null && datasetIri == null) {
                return;
            }
            String problem;
            if (codelistIri == null) {
                problem = "datová sada v NKOD '" + datasetIri + "' nemá číselník";
            } else if (isHttpIri(codelistIri)) {
                problem = "číselník '" + codelistIri + "' není http(s) IRI";
            } else if (datasetIri == null) {
                problem = "číselník '" + codelistIri + "' nemá datovou sadu v NKOD (povinná)";
            } else if (isHttpIri(datasetIri)) {
                problem = "datová sada v NKOD '" + datasetIri + "' není http(s) IRI";
            } else {
                ObjectNode codelist = concept.putObject(INSTANCE_DEFINOVANY_CISELNIKEM);
                codelist.put(IRI, codelistIri);
                codelist.put(TYP, CISELNIK);
                codelist.put(DATOVA_SADA_V_NKOD, datasetIri);
                return;
            }
            dropped(concept, INSTANCE_DEFINOVANY_CISELNIKEM, problem);
        }

        // ------------------------------------------------------------ properties

        private void addProperty(PropertyData propertyData) {
            if (conceptFilterUtil.shouldFilterConcept(propertyData.getIdentifier())) {
                log.info("FILTERED: Property '{}' with identifier '{}' matches filter regex",
                        propertyData.getName(), propertyData.getIdentifier());
                filterStatistics.filteredProperties++;
                return;
            }
            if (propertyData.getDomain() != null
                    && conceptFilterUtil.isConceptFiltered(propertyData.getDomain(), nameToIdentifier)) {
                log.info("FILTERED: Property '{}' because its domain '{}' is filtered",
                        propertyData.getName(), propertyData.getDomain());
                filterStatistics.filteredProperties++;
                filterStatistics.omittedDomains++;
                return;
            }

            String iri = uriGenerator.generateConceptURI(propertyData.getName(), propertyData.getIdentifier());
            ObjectNode concept = newConcept(iri, ConceptKind.PROPERTY, "vlastnost '" + propertyData.getName() + "'");
            nameToIri.put(propertyData.getName(), iri);
            if (concept == null) {
                return;
            }

            concept.putArray(TYP).add(POJEM).add(KONCEPT).add(VLASTNOST);
            addCommon(concept, propertyData.getName(), propertyData.getDescription(), propertyData.getDefinition(),
                    propertyData.getSource(), propertyData.getRelatedSource(), propertyData.getAlternativeName(),
                    propertyData.getEquivalentConcept(), propertyData.getBroaderConcepts());
            if (!isBlank(propertyData.getDomain())) {
                concept.put(DEFINICNI_OBOR, resolveReference(propertyData.getDomain()));
            }
            addPpdf(concept, propertyData.getSharedInPPDF());
            addAgenda(concept, propertyData.getAgendaCode());
            addAgendaSystem(concept, propertyData.getAgendaSystemCode());
            concept.put(OBOR_HODNOT, range(propertyData.getDataType()));
            addGovernance(concept, propertyData.getIsPublic(), propertyData.getPrivacyProvision(),
                    propertyData.getSharingMethod(), propertyData.getAcquisitionMethod(),
                    propertyData.getContentType());
            addSuper(concept, NADRAZENA_VLASTNOST, propertyData.getSuperProperty(), propertyData.getName(),
                    () -> filterStatistics.omittedSuperProperties++);
        }

        // ------------------------------------------------------------ relationships

        private void addRelationship(RelationshipData relationshipData) {
            String name = relationshipData.getName();
            if (conceptFilterUtil.shouldFilterConcept(relationshipData.getIdentifier())) {
                log.info("FILTERED: Relationship '{}' with identifier '{}' matches filter regex",
                        name, relationshipData.getIdentifier());
                filterStatistics.filteredRelationships++;
                return;
            }
            if (relationshipData.getDomain() != null
                    && conceptFilterUtil.isConceptFiltered(relationshipData.getDomain(), nameToIdentifier)) {
                log.info("FILTERED: Relationship '{}' because its domain '{}' is filtered",
                        name, relationshipData.getDomain());
                filterStatistics.filteredRelationships++;
                filterStatistics.omittedDomains++;
                return;
            }
            if (relationshipData.getRange() != null
                    && conceptFilterUtil.isConceptFiltered(relationshipData.getRange(), nameToIdentifier)) {
                log.info("FILTERED: Relationship '{}' because its range '{}' is filtered",
                        name, relationshipData.getRange());
                filterStatistics.filteredRelationships++;
                filterStatistics.omittedRanges++;
                return;
            }

            String iri = uriGenerator.generateConceptURI(name, relationshipData.getIdentifier());
            ObjectNode concept = newConcept(iri, ConceptKind.RELATIONSHIP, "vztah '" + name + "'");
            nameToIri.put(name, iri);
            if (concept == null) {
                return;
            }

            concept.putArray(TYP).add(POJEM).add(KONCEPT).add(VZTAH);
            addCommon(concept, name, relationshipData.getDescription(), relationshipData.getDefinition(),
                    relationshipData.getSource(), relationshipData.getRelatedSource(),
                    relationshipData.getAlternativeName(), relationshipData.getEquivalentConcept(),
                    relationshipData.getBroaderConcepts());
            addPpdf(concept, relationshipData.getSharedInPPDF());
            addAgenda(concept, relationshipData.getAgendaCode());
            addAgendaSystem(concept, relationshipData.getAgendaSystemCode());
            addGovernance(concept, relationshipData.getIsPublic(), relationshipData.getPrivacyProvision(),
                    relationshipData.getSharingMethod(), relationshipData.getAcquisitionMethod(),
                    relationshipData.getContentType());
            addSuper(concept, NADRAZENY_VZTAH, relationshipData.getSuperRelation(), name,
                    () -> filterStatistics.omittedSuperRelations++);
            if (!isBlank(relationshipData.getDomain())) {
                concept.put(DEFINICNI_OBOR, resolveReference(relationshipData.getDomain()));
            }
            if (!isBlank(relationshipData.getRange())) {
                concept.put(OBOR_HODNOT, resolveReference(relationshipData.getRange()));
            }
        }

        // ------------------------------------------------------------ hierarchies

        private void addHierarchy(HierarchyData hierarchy) {
            if (!hierarchy.hasValidData()) {
                log.warn("Skipping invalid hierarchy: {}", hierarchy);
                return;
            }
            if (conceptFilterUtil.isConceptFiltered(hierarchy.getSubClass(), nameToIdentifier)
                    || conceptFilterUtil.isConceptFiltered(hierarchy.getSuperClass(), nameToIdentifier)) {
                log.info("FILTERED: Hierarchy '{}' IS-A '{}' because one side is filtered",
                        hierarchy.getSubClass(), hierarchy.getSuperClass());
                filterStatistics.filteredHierarchies++;
                return;
            }

            String subIri = nameToIri.get(hierarchy.getSubClass());
            String superIri = nameToIri.get(hierarchy.getSuperClass());
            if (superIri == null && hierarchy.getSuperClassIRI() != null) {
                superIri = UtilityMethods.removeTrailingSlash(hierarchy.getSuperClassIRI());
            }
            if (subIri == null || superIri == null) {
                log.warn("Hierarchy '{}' IS-A '{}': {} not found", hierarchy.getSubClass(),
                        hierarchy.getSuperClass(), subIri == null ? "subclass" : "superclass");
                return;
            }
            ObjectNode sub = concepts.get(subIri);
            if (sub == null) {
                return; // external concept: not part of this vocabulary's output
            }
            String key = switch (kinds.get(subIri)) {
                case CLASS -> NADRAZENA_TRIDA;
                case PROPERTY -> NADRAZENA_VLASTNOST;
                case RELATIONSHIP -> NADRAZENY_VZTAH;
            };
            addToSet(sub, key, superIri);
        }

        // ------------------------------------------------------------ shared parts

        /**
         * Creates a local concept, or returns null for a concept outside this vocabulary (it is only
         * referenced) and for an IRI another input element already uses.
         */
        private ObjectNode newConcept(String iri, ConceptKind kind, String element) {
            if (localPrefix == null || !iri.startsWith(localPrefix)) {
                log.debug("External concept (referenced only): {} -> {}", element, iri);
                return null;
            }
            String previous = owners.putIfAbsent(iri, element);
            if (previous != null) {
                log.warn("IRI collision: {} and {} both map to {}", previous, element, iri);
                issues.add(new ConformanceIssue(Kind.CONCEPT_EXCLUDED, iri, location(iri),
                        element + " má stejné IRI jako " + previous + "; ponechán jen první"));
                return null;
            }
            ObjectNode concept = JsonNodeFactory.instance.objectNode();
            concept.put(IRI, iri);
            concepts.put(iri, concept);
            kinds.put(iri, kind);
            return concept;
        }

        private void addCommon(ObjectNode concept, String name, String description, String definition,
                               String source, String relatedSource, String alternativeNames,
                               String equivalentConcepts, List<String> broaderConcepts) {
            putText(concept, NAZEV, name);
            putText(concept, POPIS, description);
            putText(concept, DEFINICE, definition);
            addSources(concept, source, true);
            addSources(concept, relatedSource, false);
            addAlternativeNames(concept, alternativeNames);
            addEquivalents(concept, equivalentConcepts);
            addBroader(concept, broaderConcepts);
        }

        /** Broader concepts (skos:broader), valid for every concept type. */
        private void addBroader(ObjectNode concept, List<String> broaderConcepts) {
            if (broaderConcepts == null) {
                return;
            }
            for (String value : broaderConcepts) {
                String iri = value == null ? "" : UtilityMethods.removeTrailingSlash(value.trim());
                if (iri.isEmpty()) {
                    continue;
                }
                if (!UtilityMethods.isValidIRI(iri)) {
                    dropped(concept, NADRAZENY_POJEM, "'" + iri + "' není platné IRI");
                } else if (conceptFilterUtil.shouldFilterConcept(iri)) {
                    log.info("OMITTED: broader concept '{}' (it is filtered)", iri);
                } else {
                    addToSet(concept, NADRAZENY_POJEM, iri);
                }
            }
        }

        private void addAlternativeNames(ObjectNode concept, String value) {
            if (isBlank(value)) {
                return;
            }
            List<String> names = value.contains(";") ? splitList(value) : List.of(value);
            ArrayNode cs = JsonNodeFactory.instance.arrayNode();
            names.forEach(cs::add);
            concept.putObject(ALTERNATIVNI_NAZEV).set(LANG, cs);
        }

        private void addEquivalents(ObjectNode concept, String value) {
            if (isBlank(value)) {
                return;
            }
            for (String item : value.split(";")) {
                String iri = UtilityMethods.removeTrailingSlash(item.trim());
                if (iri.isEmpty()) {
                    continue;
                }
                if (!UtilityMethods.isValidIRI(iri)) {
                    dropped(concept, EKVIVALENTNI_POJEM, "'" + iri + "' není platné IRI");
                } else if (conceptFilterUtil.shouldFilterConcept(iri)) {
                    log.info("OMITTED: equivalentConcept '{}' (equivalentConcept is filtered)", iri);
                    filterStatistics.omittedEquivalentConcepts++;
                } else {
                    addToSet(concept, EKVIVALENTNI_POJEM, iri);
                }
            }
        }

        /** Source cells: ELI links become legal provisions, anything else a digital object. */
        private void addSources(ObjectNode concept, String value, boolean defining) {
            if (isBlank(value)) {
                return;
            }
            String provisionKey = defining ? DEFINUJICI_USTANOVENI : SOUVISEJICI_USTANOVENI;
            String documentKey = defining ? DEFINUJICI_NELEGISLATIVNI_ZDROJ : SOUVISEJICI_NELEGISLATIVNI_ZDROJ;

            for (String source : splitList(value)) {
                String eliPart = UtilityMethods.containsEliPattern(source)
                        ? UtilityMethods.extractEliPart(source)
                        : null;
                if (eliPart != null) {
                    String provision = OPENDATA_ESEL + eliPart;
                    if (UtilityMethods.isValidUrl(provision)) {
                        addToSet(concept, provisionKey, provision);
                    } else {
                        dropped(concept, provisionKey, "ustanovení '" + source + "' nelze převést na platné IRI");
                    }
                    continue;
                }
                ObjectNode document = JsonNodeFactory.instance.objectNode();
                document.put(TYP, DIGITALNI_OBJEKT);
                if (UtilityMethods.isValidUrl(source)) {
                    document.put(URL, source);
                } else {
                    document.putObject(NAZEV).put(LANG, source);
                }
                addToSet(concept, documentKey, document);
            }
        }

        private void addPpdf(ObjectNode concept, String value) {
            if (isBlank(value)) {
                return;
            }
            if (UtilityMethods.isBooleanValue(value)) {
                concept.put(JE_SDILEN_V_PPDF, UtilityMethods.normalizeCzechBoolean(value));
            } else {
                dropped(concept, JE_SDILEN_V_PPDF, "'" + value + "' není ano/ne");
            }
        }

        private void addAgenda(ObjectNode concept, String code) {
            if (isBlank(code)) {
                return;
            }
            String trimmed = code.trim();
            if (UtilityMethods.isValidAgendaValue(trimmed)) {
                concept.put(AGENDA, UtilityMethods.transformAgendaValue(trimmed));
            } else {
                dropped(concept, AGENDA, "neplatný kód agendy '" + trimmed + "'");
            }
        }

        private void addAgendaSystem(ObjectNode concept, String code) {
            if (isBlank(code)) {
                return;
            }
            String trimmed = code.trim();
            if (UtilityMethods.isValidAISValue(trimmed)) {
                concept.put(AGENDOVY_INFORMACNI_SYSTEM, UtilityMethods.transformAISValue(trimmed));
            } else {
                dropped(concept, AGENDOVY_INFORMACNI_SYSTEM, "neplatný kód AIS '" + trimmed + "'");
            }
        }

        // ------------------------------------------------------------ data governance

        private void addGovernance(ObjectNode concept, String isPublic, String privacyProvision,
                                   String sharingMethod, String acquisitionMethod, String contentType) {
            addPublicity(concept, isPublic, privacyProvision);
            addGovernanceValue(concept, ZPUSOBY_SDILENI_UDAJE, sharingMethod, ExportConstants.Turtle.NS_ZPUSOBY_SDILENI);
            addGovernanceValue(concept, ZPUSOB_ZISKANI_UDAJE, acquisitionMethod, ExportConstants.Turtle.NS_ZPUSOBY_ZISKANI);
            addGovernanceValue(concept, TYP_OBSAHU_UDAJE, contentType, ExportConstants.Turtle.NS_TYPY_OBSAHU);
        }

        /**
         * A privacy provision makes the data non-public; otherwise "veřejný údaj" decides.
         * Non-public data needs an ELI provision (the schema requires one).
         */
        private void addPublicity(ObjectNode concept, String isPublic, String privacyProvision) {
            if (!isBlank(privacyProvision)) {
                addNonPublic(concept, privacyProvision);
                return;
            }
            if (isBlank(isPublic)) {
                return;
            }
            if (!UtilityMethods.isBooleanValue(isPublic)) {
                dropped(concept, TYP, "neznámá hodnota veřejnosti údaje '" + isPublic + "'");
            } else if (Boolean.TRUE.equals(UtilityMethods.normalizeCzechBoolean(isPublic))) {
                ((ArrayNode) concept.get(TYP)).add(VEREJNY_UDAJ);
            } else {
                dropped(concept, TYP, "neveřejný údaj bez ustanovení dokládajícího neveřejnost");
            }
        }

        private void addNonPublic(ObjectNode concept, String provision) {
            String trimmed = provision.trim();
            String eliPart = UtilityMethods.containsEliPattern(trimmed) ? UtilityMethods.extractEliPart(trimmed) : null;
            if (eliPart == null) {
                dropped(concept, USTANOVENI_NEVEREJNOST,
                        "ustanovení '" + trimmed + "' neobsahuje odkaz ELI; údaj nelze označit jako neveřejný");
                return;
            }
            addToSet(concept, USTANOVENI_NEVEREJNOST, OPENDATA_ESEL + eliPart);
            ((ArrayNode) concept.get(TYP)).add(NEVEREJNY_UDAJ);
        }

        /** A codelist value ("vlastní") as its item IRI, compacted with the context's prefix. */
        private void addGovernanceValue(ObjectNode concept, String key, String value, String namespace) {
            if (isBlank(value)) {
                return;
            }
            if (!GOVERNANCE_VALUES.contains(value.toLowerCase())) {
                dropped(concept, key, "hodnota '" + value + "' není v číselníku");
                return;
            }
            String curie = specification.contextIndex().compactIri(namespace + UtilityMethods.sanitizeForIRI(value.trim()));
            if (ZPUSOBY_SDILENI_UDAJE.equals(key)) {
                addToSet(concept, key, curie);
            } else {
                concept.put(key, curie);
            }
        }

        // ------------------------------------------------------------ references and ranges

        private void addSuper(ObjectNode concept, String key, String superName, String name, Runnable onFiltered) {
            if (isBlank(superName)) {
                return;
            }
            if (conceptFilterUtil.isConceptFiltered(superName, nameToIdentifier)) {
                log.info("OMITTED: super concept '{}' for '{}' (it is filtered)", superName, name);
                onFiltered.run();
                return;
            }
            String trimmed = superName.trim();
            String iri = DataTypeConverter.isUri(trimmed)
                    ? UtilityMethods.removeTrailingSlash(trimmed)
                    : uriGenerator.generateConceptURI(trimmed, null);
            addToSet(concept, key, iri);
        }

        /** A domain or range given as an IRI, or by the name of an element converted so far. */
        private String resolveReference(String reference) {
            if (DataTypeConverter.isUri(reference)) {
                return UtilityMethods.removeTrailingSlash(reference);
            }
            String known = nameToIri.get(reference);
            return known != null ? known : uriGenerator.generateConceptURI(reference, null);
        }

        /** The range of a property: an XSD type compacted to {@code xsd:…}, or another IRI. */
        private String range(String dataType) {
            String iri = rangeIri(dataType);
            return iri.startsWith(ExportConstants.Json.XSD_NS) ? specification.contextIndex().compactIri(iri) : iri;
        }

        private String rangeIri(String dataType) {
            if (isBlank(dataType)) {
                return DataTypeConstants.RDFS_LITERAL;
            }
            String value = dataType.trim();
            if (value.startsWith("xsd:")) {
                String local = value.substring(4);
                return DataTypeConverter.isValidXSDType(local)
                        ? ExportConstants.Json.XSD_NS + local
                        : invalidRange(value);
            }
            if (value.startsWith("rdfs:")) {
                return ExportConstants.Json.RDFS_NS + value.substring("rdfs:".length());
            }
            if (value.startsWith(ExportConstants.Json.XSD_NS)) {
                return DataTypeConverter.isValidXSDType(value.substring(ExportConstants.Json.XSD_NS.length()))
                        ? value
                        : invalidRange(value);
            }
            if (DataTypeConverter.isUri(value)) {
                return UtilityMethods.removeTrailingSlash(value);
            }
            for (Map.Entry<String, String> entry : CZECH_TO_XSD.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(value)) {
                    return entry.getValue();
                }
            }
            String detected = detectDataType(value);
            return detected != null ? detected : DataTypeConstants.RDFS_LITERAL;
        }

        private String invalidRange(String value) {
            log.warn("Invalid XSD type '{}' - falling back to rdfs:Literal", value);
            return DataTypeConstants.RDFS_LITERAL;
        }

        private String detectDataType(String value) {
            if (DataTypeConverter.isBooleanValue(value)) {
                return DataTypeConstants.XSD_BOOLEAN;
            }
            if (DataTypeConverter.isInteger(value)) {
                return DataTypeConstants.XSD_INTEGER;
            }
            if (DataTypeConverter.isDouble(value)) {
                return DataTypeConstants.XSD_DOUBLE;
            }
            if (DataTypeConverter.isDate(value)) {
                return DataTypeConstants.XSD_DATE;
            }
            if (DataTypeConverter.isTime(value)) {
                return DataTypeConstants.XSD_TIME;
            }
            if (DataTypeConverter.isDateTime(value) || DataTypeConverter.isDateTimeStamp(value)) {
                return DataTypeConstants.XSD_DATETIME_STAMP;
            }
            return null;
        }

        // ------------------------------------------------------------ helpers

        private void dropped(ObjectNode concept, String key, String message) {
            String iri = concept.path(IRI).asText(null);
            log.warn("{}: {} - {}", iri, key, message);
            issues.add(new ConformanceIssue(Kind.VALUE_DROPPED, iri, location(iri) + "/" + key, message));
        }

        private String location(String iri) {
            return OfnSpecification.CONCEPTS_KEY + "[" + (iri == null ? "" : iri.substring(iri.lastIndexOf('/') + 1)) + "]";
        }
    }

    // ---------------------------------------------------------------- static helpers

    private static void putText(ObjectNode node, String key, String value) {
        if (!isBlank(value)) {
            node.putObject(key).put(LANG, value);
        }
    }

    private static void addToSet(ObjectNode node, String key, String value) {
        addToSet(node, key, JsonNodeFactory.instance.textNode(value));
    }

    private static void addToSet(ObjectNode node, String key, JsonNode value) {
        JsonNode existing = node.get(key);
        ArrayNode array = existing instanceof ArrayNode a ? a : node.putArray(key);
        for (JsonNode item : array) {
            if (item.equals(value)) {
                return;
            }
        }
        array.add(value);
    }

    private static List<String> splitList(String value) {
        List<String> items = new ArrayList<>();
        for (String item : value.split(";")) {
            if (!item.trim().isEmpty()) {
                items.add(item.trim());
            }
        }
        return items;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isHttpIri(String value) {
        return (!value.startsWith("https://") && !value.startsWith("http://")) || !UtilityMethods.isValidIRI(value);
    }
}
