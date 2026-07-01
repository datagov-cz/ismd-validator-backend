package com.dia.validation.global;

import com.dia.validation.ValidationSeverity;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import java.util.Optional;

/**
 * The metadata of a single global (corpus) SHACL rule, parsed from its {@code .ttl} Model.
 *
 * <p>This exists because {@code RuleManager.extractRuleMetadata} only records a statement
 * count — it does not read {@code sh:name}/{@code sh:message}/{@code sh:severity}. For local
 * rules those values are read from Jena's {@code ReportEntry} at result time, but global
 * rules never run in Jena, so {@code GlobalValidationEngine} parses them here to synthesize
 * equivalent {@code ValidationResult}s.
 *
 * @param shapeIri  the {@code sh:NodeShape} subject IRI (used as the rule identity / ruleName).
 * @param name      {@code sh:name} (may be {@code null}).
 * @param message   {@code sh:message} (falls back to {@code name}/{@code shapeIri} if absent).
 * @param severity  mapped from {@code sh:severity} ({@code sh:Violation}→ERROR, {@code sh:Warning}
 *                  →WARNING, {@code sh:Info}→INFO; defaults to INFO).
 */
public record GlobalRuleMetadata(String shapeIri, String name, String message, ValidationSeverity severity) {

    private static final String SH = "http://www.w3.org/ns/shacl#";

    /**
     * Parse the (single) node shape out of a rule Model. Returns empty if the Model has no
     * {@code sh:NodeShape} subject.
     */
    public static Optional<GlobalRuleMetadata> from(Model model) {
        Property shName = model.createProperty(SH, "name");
        Property shMessage = model.createProperty(SH, "message");
        Property shSeverity = model.createProperty(SH, "severity");
        Resource nodeShape = model.createResource(SH + "NodeShape");

        return model.listStatements(null, RDF.type, nodeShape).toList().stream()
                .map(Statement::getSubject)
                .filter(Resource::isURIResource)
                .findFirst()
                .map(shape -> {
                    String name = literal(shape, shName);
                    String message = literal(shape, shMessage);
                    ValidationSeverity severity = mapSeverity(objectUri(shape, shSeverity));
                    String iri = shape.getURI();
                    String effectiveMessage = (message != null && !message.isBlank())
                            ? message
                            : (name != null && !name.isBlank() ? name : iri);
                    return new GlobalRuleMetadata(iri, name, effectiveMessage, severity);
                });
    }

    private static String literal(Resource subject, Property p) {
        Statement s = subject.getProperty(p);
        return (s != null && s.getObject().isLiteral()) ? s.getString() : null;
    }

    private static String objectUri(Resource subject, Property p) {
        Statement s = subject.getProperty(p);
        if (s == null) {
            return null;
        }
        RDFNode o = s.getObject();
        return o.isURIResource() ? o.asResource().getURI() : null;
    }

    private static ValidationSeverity mapSeverity(String severityUri) {
        if (severityUri == null) {
            return ValidationSeverity.INFO;
        }
        return switch (severityUri) {
            case SH + "Violation" -> ValidationSeverity.ERROR;
            case SH + "Warning" -> ValidationSeverity.WARNING;
            default -> ValidationSeverity.INFO;
        };
    }
}
