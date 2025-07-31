package com.dia.validation.report;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SHACLRuleMetadataExtractor {

    private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";
    private static final Property SHACL_NAME = ResourceFactory.createProperty(SHACL_NS + "name");
    private static final Property SHACL_DESCRIPTION = ResourceFactory.createProperty(SHACL_NS + "description");
    private static final Property SHACL_MESSAGE = ResourceFactory.createProperty(SHACL_NS + "message");
    private static final Property SHACL_SEVERITY = ResourceFactory.createProperty(SHACL_NS + "severity");
    private static final Resource SHACL_NODESHAPE = ResourceFactory.createResource(SHACL_NS + "NodeShape");
    private static final Resource SHACL_PROPERTYSHAPE = ResourceFactory.createResource(SHACL_NS + "PropertyShape");

    public Map<String, RuleMetadata> extractAllRuleMetadata(Model shaclModel) {
        Map<String, RuleMetadata> ruleMetadata = new HashMap<>();

        ResIterator nodeShapes = shaclModel.listResourcesWithProperty(RDF.type, SHACL_NODESHAPE);
        ResIterator propertyShapes = shaclModel.listResourcesWithProperty(RDF.type, SHACL_PROPERTYSHAPE);

        while (nodeShapes.hasNext()) {
            Resource shape = nodeShapes.next();
            RuleMetadata metadata = extractRuleMetadata(shape);
            if (metadata != null) {
                String ruleKey = extractRuleKey(shape);
                ruleMetadata.put(ruleKey, metadata);
            }
        }

        while (propertyShapes.hasNext()) {
            Resource shape = propertyShapes.next();
            RuleMetadata metadata = extractRuleMetadata(shape);
            if (metadata != null) {
                String ruleKey = extractRuleKey(shape);
                ruleMetadata.put(ruleKey, metadata);
            }
        }

        log.info("Extracted metadata for {} SHACL rules", ruleMetadata.size());
        return ruleMetadata;
    }

    public RuleMetadata extractRuleMetadata(Model shaclModel, String ruleUri) {
        Resource ruleResource = shaclModel.getResource(ruleUri);
        return extractRuleMetadata(ruleResource);
    }

    private RuleMetadata extractRuleMetadata(Resource shape) {
        if (shape == null || !shape.hasProperty(RDF.type)) {
            return null;
        }

        String name = extractLiteralValue(shape, SHACL_NAME);
        String description = extractLiteralValue(shape, SHACL_DESCRIPTION);
        String message = extractLiteralValue(shape, SHACL_MESSAGE);
        String severity = extractSeverity(shape);

        if (name == null || name.trim().isEmpty()) {
            name = shape.getURI() != null ? extractNameFromURI(shape.getURI()) : "Unknown Rule";
        }

        return new RuleMetadata(name, description, message, severity, shape.getURI());
    }

    private String extractLiteralValue(Resource resource, Property property) {
        Statement stmt = resource.getProperty(property);
        if (stmt != null && stmt.getObject().isLiteral()) {
            return stmt.getString();
        }
        return null;
    }

    private String extractSeverity(Resource shape) {
        Statement severityStmt = shape.getProperty(SHACL_SEVERITY);
        if (severityStmt != null && severityStmt.getObject().isResource()) {
            String severityUri = severityStmt.getResource().getURI();
            return mapSeverityUriToReadable(severityUri);
        }
        return "Unknown";
    }

    private String mapSeverityUriToReadable(String severityUri) {
        if (severityUri == null) return "Unknown";

        return switch (severityUri) {
            case "http://www.w3.org/ns/shacl#Violation" -> "Error";
            case "http://www.w3.org/ns/shacl#Warning" -> "Warning";
            case "http://www.w3.org/ns/shacl#Info" -> "Info";
            default -> "Unknown";
        };
    }

    private String extractNameFromURI(String uri) {
        if (uri == null) return "Unknown";

        int lastSlash = uri.lastIndexOf('/');
        int lastHash = uri.lastIndexOf('#');
        int splitIndex = Math.max(lastSlash, lastHash);

        if (splitIndex >= 0 && splitIndex < uri.length() - 1) {
            return uri.substring(splitIndex + 1);
        }

        return uri;
    }

    private String extractRuleKey(Resource shape) {
        String name = extractLiteralValue(shape, SHACL_NAME);
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }

        if (shape.getURI() != null) {
            return extractNameFromURI(shape.getURI());
        }

        return "unknown-rule-" + shape.getId();
    }
    
    public record RuleMetadata(String name, String description, String message, String severity, String uri) {

        @Override
        public String name() {
            return name != null ? name : "Unknown";
        }

        @Override
        public String description() {
            return description != null ? description : "Popis není k dispozici";
        }

        @Override
        public String severity() {
            return severity != null ? severity : "Unknown";
        }

        @Override
        public String toString() {
            return String.format("RuleMetadata{name='%s', severity='%s', uri='%s'}", name, severity, uri);
        }
    }
}
