package com.dia.ofn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Serialises the OFN document. The RDF model is the document read as JSON-LD with the bundled
 * context, so JSON-LD and Turtle are the same graph by construction.
 */
@Component
public class OfnRdf {

    private final OfnSpecification specification;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public OfnRdf(OfnSpecification specification) {
        this.specification = specification;
    }

    /** The document as RDF. The context is inlined from the bundled file: no network access. */
    public Model toModel(ObjectNode document) {
        ObjectNode local = document.deepCopy();
        local.set("@context", specification.contextDocument().get("@context"));
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(write(local), Lang.JSONLD).parse(model);
        specification.contextIndex().prefixes().forEach((prefix, namespace) ->
                model.setNsPrefix(turtlePrefix(prefix), namespace));
        model.setNsPrefix("rdf", RDF.getURI());
        return model;
    }

    /** Turtle prefix names must start with a letter: "111-2009" is written as "l111-2009". */
    private static String turtlePrefix(String prefix) {
        return Character.isLetter(prefix.codePointAt(0)) ? prefix : "l" + prefix;
    }

    public String toJsonLd(ObjectNode document) {
        return write(document);
    }

    public String toTurtle(Model model) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, RDFFormat.TURTLE_PRETTY);
        return out.toString(StandardCharsets.UTF_8);
    }

    private String write(ObjectNode document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
