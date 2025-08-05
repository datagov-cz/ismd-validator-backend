package com.dia.service.impl;

import com.dia.controller.dto.CatalogRecordDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.service.CatalogReportService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogReportServiceImpl implements CatalogReportService {

    private static final String CONTEXT_URL = "https://ofn.gov.cz/dcat-ap-cz-rozhraní-katalogů-otevřených-dat/2024-05-28/kontexty/rozhraní-katalogů-otevřených-dat.jsonld";
    private static final String DEFAULT_TYPE = "Datová sada";
    private static final String PERIODICITY_IRREGULAR = "http://publications.europa.eu/resource/authority/frequency/IRREG";
    private static final String EUROVOC_CONCEPT = "http://eurovoc.europa.eu/438";
    private static final String SPECIFICATION = "https://ofn.gov.cz/slovníky/draft/";
    private static final String RUIAN = "https://linked.cuzk.cz/resource/ruian/stat/1";

    @Override
    public Optional<CatalogRecordDto> generateCatalogReport(ConversionResult conversionResult, ValidationResultsDto validationResults) {
        if (!shouldGenerateCatalogReport(validationResults)) {
            log.info("Catalog report generation skipped - validation contains ERROR severity findings");
            return Optional.empty();
        }

        log.info("Generating catalog report - no ERROR severity findings detected");

        try {
            OntModel ontModel = conversionResult.getTransformationResult().getOntModel();
            VocabularyMetadata metadata = extractVocabularyMetadata(ontModel);

            CatalogRecordDto catalogReport = buildCatalogReport(metadata);

            log.info("Catalog report generated successfully for vocabulary: {}", metadata.getIri());
            return Optional.of(catalogReport);

        } catch (Exception e) {
            log.error("Failed to generate catalog report", e);
            return Optional.empty();
        }
    }

    private boolean shouldGenerateCatalogReport(ValidationResultsDto validationResults) {
        if (validationResults == null || validationResults.getSeverityGroups() == null) {
            return true;
        }

        return validationResults.getSeverityGroups().stream()
                .noneMatch(group -> "ERROR".equalsIgnoreCase(group.getSeverity()) && group.getCount() > 0);
    }

    private VocabularyMetadata extractVocabularyMetadata(OntModel ontModel) {
        VocabularyMetadata.VocabularyMetadataBuilder builder = VocabularyMetadata.builder();

        Resource vocabularyResource = findVocabularyResource(ontModel);

        if (vocabularyResource != null) {
            String iri = vocabularyResource.getURI();
            builder.iri(iri != null ? iri : "_:ds");

            Map<String, String> nazev = extractMultilingualProperty(ontModel, vocabularyResource, RDFS.label);
            if (nazev.isEmpty()) {
                nazev = createDefaultNazev(iri);
            }
            builder.nazev(nazev);

            Map<String, String> popis = extractMultilingualProperty(ontModel, vocabularyResource, DCTerms.description);
            if (popis.isEmpty()) {
                popis = createDefaultPopis();
            }
            builder.popis(popis);

        } else {
            builder.iri("_:ds")
                    .nazev(createDefaultNazev(null))
                    .popis(createDefaultPopis());
        }

        return builder.build();
    }

    private Resource findVocabularyResource(OntModel ontModel) {
        var ontologies = ontModel.listOntologies();
        if (ontologies.hasNext()) {
            return ontologies.next();
        }

        var resources = ontModel.listResourcesWithProperty(RDFS.label);
        if (resources.hasNext()) {
            return resources.next();
        }

        return null;
    }

    private Map<String, String> extractMultilingualProperty(OntModel ontModel, Resource resource, Property property) {
        Map<String, String> result = new HashMap<>();

        var statements = ontModel.listStatements(resource, property, (String) null);
        while (statements.hasNext()) {
            Statement stmt = statements.next();
            if (stmt.getObject().isLiteral()) {
                String language = stmt.getObject().asLiteral().getLanguage();
                String value = stmt.getObject().asLiteral().getString();

                if ("cs".equals(language) || "en".equals(language)) {
                    result.put(language, value);
                } else if (language == null || language.isEmpty()) {
                    result.putIfAbsent("cs", value);
                    result.putIfAbsent("en", value);
                }
            }
        }

        return result;
    }

    private Map<String, String> createDefaultNazev(String iri) {
        String defaultName = iri != null ? "Vocabulary " + extractLocalName(iri) : "Converted Vocabulary";
        Map<String, String> nazev = new HashMap<>();
        nazev.put("cs", "Název slovníku");
        nazev.put("en", defaultName);
        return nazev;
    }

    private Map<String, String> createDefaultPopis() {
        Map<String, String> popis = new HashMap<>();
        popis.put("cs", "Popis slovníku");
        popis.put("en", "Vocabulary description");
        return popis;
    }

    private String extractLocalName(String uri) {
        if (uri == null) return "Unknown";
        int lastSlash = uri.lastIndexOf('/');
        int lastHash = uri.lastIndexOf('#');
        int index = Math.max(lastSlash, lastHash);
        return index >= 0 ? uri.substring(index + 1) : uri;
    }

    private CatalogRecordDto buildCatalogReport(VocabularyMetadata metadata) {
        return CatalogRecordDto.builder()
                .context(CONTEXT_URL)
                .iri(metadata.getIri())
                .typ(DEFAULT_TYPE)
                .nazev(metadata.getNazev())
                .popis(metadata.getPopis())
                .prvekRuian(List.of(RUIAN))
                .geografickeUzemi(new ArrayList<>())
                .prostorovePokryti(new ArrayList<>())
                .klicoveSlovo(createDefaultKeywords())
                .periodicitaAktualizace(PERIODICITY_IRREGULAR)
                .tema(new ArrayList<>())
                .konceptEuroVoc(List.of(EUROVOC_CONCEPT))
                .specifikace(List.of(SPECIFICATION))
                .kontaktniBod(new HashMap<>())
                .distribuce(List.of(createDefaultDistribution()))
                .build();
    }

    private Map<String, List<String>> createDefaultKeywords() {
        Map<String, List<String>> keywords = new HashMap<>();
        keywords.put("cs", List.of("slovník"));
        keywords.put("en", List.of("vocabulary"));
        return keywords;
    }

    private CatalogRecordDto.DistribuceDto createDefaultDistribution() {
        return CatalogRecordDto.DistribuceDto.builder()
                .typ("Distribuce")
                .podminkyUziti(createDefaultUsageConditions())
                .souborKeStazeni("")
                .pristupoveUrl("")
                .typMedia("http://www.iana.org/assignments/media-types/text/turtle")
                .format("http://publications.europa.eu/resource/authority/file-type/RDF_TURTLE")
                .schema("https://ofn.gov.cz/slovníky/draft/schémata/konceptuální-model.json")
                .build();
    }

    private CatalogRecordDto.PodminkyUzitiDto createDefaultUsageConditions() {
        return CatalogRecordDto.PodminkyUzitiDto.builder()
                .typ("Specifikace podmínek užití")
                .autorskeDilo("https://data.gov.cz/podmínky-užití/neobsahuje-autorská-díla/")
                .databizeJakoAutorskeDilo("https://data.gov.cz/podmínky-užití/není-autorskoprávně-chráněnou-databází/")
                .databizeChranenaZvlastnimiPravy("https://data.gov.cz/podmínky-užití/není-chráněna-zvláštním-právem-pořizovatele-databáze/")
                .osobniUdaje("https://data.gov.cz/podmínky-užití/neobsahuje-osobní-údaje/")
                .build();
    }

    @Data
    @Builder
    private static class VocabularyMetadata {
        private String iri;
        private Map<String, String> nazev;
        private Map<String, String> popis;
    }
}

