package com.dia.conversion.transformer;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.data.VocabularyMetadata;
import com.dia.exception.ConversionException;
import com.dia.exception.JsonExportException;
import com.dia.exception.MissingVocabularyNameException;
import com.dia.exception.OfnConformanceException;
import com.dia.exception.TurtleExportException;
import com.dia.ofn.ConformanceIssue;
import com.dia.ofn.GateResult;
import com.dia.ofn.OfnConformanceGate;
import com.dia.ofn.OfnRdf;
import com.dia.utility.TemporalValues;
import com.dia.utility.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;
import static com.dia.constants.VocabularyConstants.DEFAULT_NS;

/**
 * Converts reader output to OFN slovníky:
 * <ol>
 *   <li>{@link OfnDocumentBuilder} maps the input to a document in OFN context terms,</li>
 *   <li>{@link OfnConformanceGate} drops what does not conform to the OFN context and schema,</li>
 *   <li>{@link OfnRdf} reads the gated document as RDF (for Turtle and SHACL).</li>
 * </ol>
 * JSON-LD is the gated document as is; Turtle is the same graph. Stateless: safe for concurrent requests.
 */
@Component
@Slf4j
public class OFNDataTransformer {

    private final OfnDocumentBuilder documentBuilder;
    private final OfnConformanceGate gate;
    private final OfnRdf rdf;
    private final Clock clock;

    public OFNDataTransformer(OfnDocumentBuilder documentBuilder, OfnConformanceGate gate, OfnRdf rdf,
                              Clock clock) {
        this.documentBuilder = documentBuilder;
        this.gate = gate;
        this.rdf = rdf;
        this.clock = clock;
    }

    public TransformationResult transform(OntologyData ontologyData) throws ConversionException {
        if (ontologyData == null || ontologyData.getVocabularyMetadata() == null) {
            throw new ConversionException("Invalid ontology data");
        }
        String name = ontologyData.getVocabularyMetadata().getName();
        if (name == null || name.isBlank()) {
            throw new MissingVocabularyNameException("Slovník nemá vyplněný název.");
        }
        try {
            VocabularyMetadata metadata = ontologyData.getVocabularyMetadata();
            applyConversionTimeFallback(metadata);
            String effectiveNamespace = determineEffectiveNamespace(metadata);

            OfnDocumentBuilder.Result built = documentBuilder.build(ontologyData, effectiveNamespace);
            GateResult gated = gate.apply(built.document());

            List<ConformanceIssue> issues = new ArrayList<>(built.issues());
            issues.addAll(gated.issues());
            if (!gated.isVocabularyValid()) {
                List<ConformanceIssue> vocabularyIssues = gated.issuesOfKind(ConformanceIssue.Kind.VOCABULARY_INVALID);
                log.warn("Vocabulary does not conform to the OFN schema: {}", vocabularyIssues);
                throw new OfnConformanceException(gated.document().path("iri").asText(null), vocabularyIssues);
            }

            Model model = rdf.toModel(gated.document());
            log.info("Transformation completed: {} concepts, {} triples, {} conformance issue(s)",
                    gated.document().path("pojmy").size(), model.size(), issues.size());

            return new TransformationResult(gated.document(), model, issues, metadata.getName(), effectiveNamespace);
        } catch (OfnConformanceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Transformation failed", e);
            throw new ConversionException("Failed to transform ontology data", e);
        }
    }

    public String exportToJson(TransformationResult transformationResult) throws JsonExportException {
        log.info("Starting JSON export: requestId={}", MDC.get(LOG_REQUEST_ID));
        try {
            return rdf.toJsonLd(transformationResult.document());
        } catch (Exception e) {
            log.error("Unexpected error during JSON export: requestId={}", MDC.get(LOG_REQUEST_ID), e);
            throw new JsonExportException("Neočekávaná chyba při exportu do JSON.", e);
        }
    }

    public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
        log.info("Starting Turtle export: requestId={}", MDC.get(LOG_REQUEST_ID));
        try {
            return rdf.toTurtle(transformationResult.model());
        } catch (Exception e) {
            log.error("Unexpected error during Turtle export: requestId={}", MDC.get(LOG_REQUEST_ID), e);
            throw new TurtleExportException("Neočekávaná chyba při exportu do Turtle.", e);
        }
    }

    /** Fills vocabulary dates the input file does not provide with the conversion start time. */
    private void applyConversionTimeFallback(VocabularyMetadata metadata) {
        String conversionStart = TemporalValues.toDateTimeStamp(clock.instant());
        if (metadata.getDateOfCreation() == null || metadata.getDateOfCreation().isBlank()) {
            metadata.setDateOfCreation(conversionStart);
        }
        if (metadata.getDateOfModification() == null || metadata.getDateOfModification().isBlank()) {
            metadata.setDateOfModification(conversionStart);
        }
    }

    /** The vocabulary's namespace if it is a valid URL, otherwise {@code DEFAULT_NS}. */
    private String determineEffectiveNamespace(VocabularyMetadata metadata) {
        String namespace = metadata.getNamespace();
        if (namespace != null && !namespace.trim().isEmpty() && UtilityMethods.isValidUrl(namespace)) {
            return UtilityMethods.ensureNamespaceEndsWithDelimiter(namespace);
        }
        return DEFAULT_NS;
    }
}
