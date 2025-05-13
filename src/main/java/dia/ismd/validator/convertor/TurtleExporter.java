package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ExportException;
import dia.ismd.common.exceptions.TurtleExportException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
class TurtleExporter {
    // TODO potřebuje refactor

    private final OntModel ontModel;
    private final Map<String, Resource> resourceMap;
    private final String modelName;
    private final Map<String, String> modelProperties;

    private static final Map<String, String> STANDARD_PREFIXES = new HashMap<>();

    static {
        STANDARD_PREFIXES.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        STANDARD_PREFIXES.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        STANDARD_PREFIXES.put("owl", "http://www.w3.org/2002/07/owl#");
        STANDARD_PREFIXES.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        STANDARD_PREFIXES.put("skos", "http://www.w3.org/2004/02/skos/core#");
        STANDARD_PREFIXES.put("dct", "http://purl.org/dc/terms/");
    }

    public TurtleExporter(OntModel ontModel, Map<String, Resource> resourceMap, String modelName, Map<String, String> modelProperties) {
        this.ontModel = ontModel;
        this.resourceMap = resourceMap;
        this.modelName = modelName;
        this.modelProperties = modelProperties;
    }

    public String exportToTurtle() throws TurtleExportException {
        try {
            addStandardPrefixes();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            RDFDataMgr.write(outputStream, ontModel, RDFFormat.TURTLE_PRETTY);

            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (TurtleExportException e) {
            log.error("Error exporting to Turtle format: {}", e.getMessage(), e);
            throw new TurtleExportException("Při exportu do Turtle došlo k chybě: " + e.getMessage());
        }
    }

    private void addStandardPrefixes() {
        Map<String, String> existingPrefixes = ontModel.getNsPrefixMap();

        for (Map.Entry<String, String> entry : STANDARD_PREFIXES.entrySet()) {
            String prefix = entry.getKey();
            String uri = entry.getValue();

            if (!existingPrefixes.containsKey(prefix) || !existingPrefixes.get(prefix).equals(uri)) {
                ontModel.setNsPrefix(prefix, uri);
            }
        }
    }

    public String exportToTurtle(boolean prettyPrint, boolean includeBaseUri) {
        try {
            addStandardPrefixes();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            RDFFormat format = prettyPrint ? RDFFormat.TURTLE_PRETTY : RDFFormat.TURTLE_BLOCKS;

            RDFDataMgr.write(outputStream, ontModel, format);

            String output = outputStream.toString(StandardCharsets.UTF_8);
            if (!includeBaseUri) {
                output = output.replaceAll("@base\\s+<[^>]*>\\s*\\.", "");
            }

            return output;
        } catch (ExportException e) {
            log.error("Error exporting to Turtle format: {}", e.getMessage(), e);
            throw new ExportException("Error exporting to Turtle format: " + e.getMessage());
        }
    }

    public String exportToTurtleWithPrefixes(Map<String, String> customPrefixes) {
        try {
            addStandardPrefixes();

            for (Map.Entry<String, String> entry : customPrefixes.entrySet()) {
                ontModel.setNsPrefix(entry.getKey(), entry.getValue());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            RDFDataMgr.write(outputStream, ontModel, RDFFormat.TURTLE_PRETTY);

            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (ExportException e) {
            log.error("Error exporting to Turtle format: {}", e.getMessage(), e);
            throw new ExportException("Error exporting to Turtle format: " + e.getMessage());
        }
    }
}
