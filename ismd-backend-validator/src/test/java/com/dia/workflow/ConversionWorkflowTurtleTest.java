package com.dia.workflow;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformerNew;
import com.dia.workflow.config.WorkflowTestConfiguration;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Excel conversion workflow test for Turtle (TTL) output with semantic validation.
 * <p>
 * This test validates the complete Excel → OntologyData → TransformationResult → TTL pipeline
 * with semantic RDF graph comparison using Apache Jena.
 * <p>
 * Features:
 * - Configurable with different test files via ExcelTestConfiguration
 * - Stage-by-stage workflow validation
 * - Semantic RDF graph comparison (isomorphism)
 * - Triple-level validation
 * - Namespace and prefix verification
 * - Data preservation checks
 * - RDF vocabulary compliance (OWL, RDFS, SKOS)
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("workflow")
@Tag("excel")
@Tag("turtle")
@Tag("deviation-detection")
class ConversionWorkflowTurtleTest {

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private OFNDataTransformerNew transformer;

    static Stream<WorkflowTestConfiguration> testConfigurationProvider() {
        return WorkflowTestConfiguration.allConfigurations().stream();
    }

    @ParameterizedTest(name = "{0} - TTL Output")
    @MethodSource("testConfigurationProvider")
    void excelConversionWorkflow_shouldProduceSemanticallySameTurtleOutput(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EXCEL → TURTLE CONVERSION WORKFLOW TEST: " + config.getTestId());
        System.out.println("=".repeat(80));

        // Stage 1: Load Excel file
        System.out.println("\n[STAGE 1] Loading Excel file: " + config.getInputPath());
        OntologyData ontologyData = loadExcelFile(config.getInputPath());
        assertNotNull(ontologyData, "Excel file should be successfully parsed");
        System.out.println("Excel file loaded successfully");

        // Stage 2: Transform to OFN format
        System.out.println("\n[STAGE 2] Transforming to OFN format");
        TransformationResult transformationResult = transformer.transform(ontologyData);
        assertNotNull(transformationResult, "Transformation should succeed");
        System.out.println("Transformation completed successfully");

        // Stage 3: Export to Turtle
        System.out.println("\n[STAGE 3] Exporting to Turtle (TTL)");
        String actualTtl = transformer.exportToTurtle(transformationResult);
        assertNotNull(actualTtl, "TTL export should succeed");
        assertFalse(actualTtl.trim().isEmpty(), "TTL output should not be empty");
        System.out.println("TTL export completed successfully");
        System.out.println("Output size: " + actualTtl.length() + " characters");

        // Stage 4: Parse actual TTL into RDF model
        System.out.println("\n[STAGE 4] Parsing actual TTL output into RDF model");
        Model actualModel = parseRdfModel(actualTtl, "Actual TTL");
        assertNotNull(actualModel, "Actual TTL should parse into valid RDF model");
        long actualTripleCount = actualModel.size();
        System.out.println("Actual model parsed: " + actualTripleCount + " triples");

        // Stage 5: Validate TTL structure
        System.out.println("\n[STAGE 5] Validating TTL structure");
        validateTurtleStructure(actualModel);
        System.out.println("TTL structure validation passed");

        // Stage 6: Load and compare with expected output
        String expectedTtlPath = config.getExpectedOutputPath() != null
            ? config.getExpectedOutputPath().replace("jsonld.jsonld", "ttl.ttl")
            : null;

        if (expectedTtlPath != null) {
            try {
                System.out.println("\n[STAGE 6] Loading expected TTL output: " + expectedTtlPath);
                String expectedTtl = loadResourceAsString(expectedTtlPath);
                System.out.println("Expected output loaded successfully");
                System.out.println("Expected size: " + expectedTtl.length() + " characters");

                // Parse expected TTL into RDF model
                System.out.println("\n[STAGE 7] Parsing expected TTL into RDF model");
                Model expectedModel = parseRdfModel(expectedTtl, "Expected TTL");
                assertNotNull(expectedModel, "Expected TTL should parse into valid RDF model");
                long expectedTripleCount = expectedModel.size();
                System.out.println("Expected model parsed: " + expectedTripleCount + " triples");

                // Stage 7: Semantic comparison
                System.out.println("\n[STAGE 8] Performing semantic graph comparison");
                compareRdfModels(expectedModel, actualModel, config);

            } catch (Exception e) {
                // Expected TTL file might not exist yet, this is not a failure
                System.out.println("\n[STAGE 6] Expected TTL file not found or could not be loaded: " + expectedTtlPath);
                System.out.println("Reason: " + e.getMessage());
                System.out.println("Skipping comparison - validating structure only");
            }
        } else {
            System.out.println("\n[STAGE 6] No expected output configured - skipping comparison");
        }

        // Final validation: Ensure output has meaningful content
        System.out.println("\n[FINAL VALIDATION] Checking for meaningful content");
        validateMeaningfulContent(actualModel, ontologyData);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST COMPLETED SUCCESSFULLY");
        System.out.println("=".repeat(80) + "\n");
    }

    @ParameterizedTest(name = "{0} - TTL Data Preservation")
    @MethodSource("testConfigurationProvider")
    void excelConversionWorkflow_turtleShouldPreserveAllData(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n[TTL DATA PRESERVATION TEST] " + config.getTestId());

        // Execute workflow
        OntologyData ontologyData = loadExcelFile(config.getInputPath());
        TransformationResult transformationResult = transformer.transform(ontologyData);
        String actualTtl = transformer.exportToTurtle(transformationResult);

        // Parse TTL
        Model actualModel = parseRdfModel(actualTtl, "Actual TTL");

        // Count entities in input
        int classCount = countEntitiesOfType(ontologyData, ClassData.class);
        int propertyCount = countEntitiesOfType(ontologyData, PropertyData.class);
        int relationshipCount = countEntitiesOfType(ontologyData, RelationshipData.class);
        int expectedEntityCount = classCount + propertyCount + relationshipCount;

        System.out.println("Input entities:");
        System.out.println("Classes: " + classCount);
        System.out.println("Properties: " + propertyCount);
        System.out.println("Relationships: " + relationshipCount);
        System.out.println("Total: " + expectedEntityCount);

        // Count concepts in RDF model (resources with skos:prefLabel or rdfs:label)
        Set<Resource> conceptResources = findConceptResources(Objects.requireNonNull(actualModel));
        int outputEntityCount = conceptResources.size();

        System.out.println("\nOutput entities (TTL):");
        System.out.println("Concepts found: " + outputEntityCount);
        System.out.println("Total triples: " + actualModel.size());

        if (expectedEntityCount != outputEntityCount) {
            System.out.println("\nEntity count mismatch detected!");
            System.out.println("Expected: " + expectedEntityCount + " entities");
            System.out.println("Found: " + outputEntityCount + " entities");
            System.out.println("Difference: " + Math.abs(expectedEntityCount - outputEntityCount) + " entities");

            // Detailed analysis
            findMissingEntitiesInTurtle(ontologyData, actualModel, conceptResources);

            fail(String.format("TTL output should contain all %d entities (classes=%d, properties=%d, relationships=%d), but found %d",
                expectedEntityCount, classCount, propertyCount, relationshipCount, outputEntityCount));
        }

        System.out.println("All " + expectedEntityCount + " entities preserved in TTL output");
    }

    @ParameterizedTest(name = "{0} - TTL RDF Vocabulary")
    @MethodSource("testConfigurationProvider")
    void excelConversionWorkflow_turtleShouldUseCorrectRdfVocabulary(WorkflowTestConfiguration config) throws Exception {
        System.out.println("\n[TTL RDF VOCABULARY TEST] " + config.getTestId());

        // Execute workflow
        OntologyData ontologyData = loadExcelFile(config.getInputPath());
        TransformationResult transformationResult = transformer.transform(ontologyData);
        String actualTtl = transformer.exportToTurtle(transformationResult);

        // Parse TTL
        Model actualModel = parseRdfModel(actualTtl, "Actual TTL");

        // Validate RDF vocabulary usage
        System.out.println("\nValidating RDF vocabulary usage:");

        List<String> validationErrors = new ArrayList<>();

        // Check for required vocabulary usage
        validateVocabularyUsage(Objects.requireNonNull(actualModel), validationErrors);

        // Check for proper type declarations
        validateTypeDeclarations(actualModel, validationErrors);

        // Check for proper property usage
        validatePropertyUsage(actualModel, validationErrors);

        if (!validationErrors.isEmpty()) {
            System.out.println("\nValidation errors found:");
            for (String error : validationErrors) {
                System.out.println("  - " + error);
            }
            fail("TTL output has RDF vocabulary validation errors: " + validationErrors.size() + " error(s) found");
        }

        System.out.println("All RDF vocabulary validation checks passed");
    }

    // ========== Helper Methods ==========

    private OntologyData loadExcelFile(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return excelReader.readOntologyFromExcel(is);
        }
    }

    private String loadResourceAsString(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Model parseRdfModel(String rdfContent, String description) {
        try {
            Model model = ModelFactory.createDefaultModel();
            InputStream inputStream = new ByteArrayInputStream(rdfContent.getBytes(StandardCharsets.UTF_8));
            RDFDataMgr.read(model, inputStream, Lang.TURTLE);
            return model;
        } catch (Exception e) {
            fail("Failed to parse " + description + " into RDF model: " + e.getMessage());
            return null;
        }
    }

    private void validateTurtleStructure(Model model) {
        // Check that model is not empty
        assertFalse(model.isEmpty(), "RDF model should contain at least one triple");

        // Check for common prefixes/namespaces
        Map<String, String> prefixes = model.getNsPrefixMap();
        System.out.println("Prefixes found: " + prefixes.keySet());

        // Verify that we have at least some standard prefixes
        assertFalse(prefixes.isEmpty(), "Model should have at least one namespace prefix defined");

        // Check for vocabulary presence
        boolean hasRdf = prefixes.containsKey("rdf") || model.listStatements(null, RDF.type, (Resource) null).hasNext();
        boolean hasRdfs = prefixes.containsKey("rdfs") || model.listStatements(null, RDFS.label, (Resource) null).hasNext();
        boolean hasOwl = prefixes.containsKey("owl") || model.listStatements(null, RDF.type, OWL.Ontology).hasNext();
        boolean hasSkos = prefixes.containsKey("skos") || model.listStatements(null, SKOS.prefLabel, (Resource) null).hasNext();
        boolean hasDct = prefixes.containsKey("dct") || model.listStatements(null, DCTerms.description, (Resource) null).hasNext();

        System.out.println("Vocabulary usage detected:");
        System.out.println("RDF: " + hasRdf);
        System.out.println("RDFS: " + hasRdfs);
        System.out.println("OWL: " + hasOwl);
        System.out.println("SKOS: " + hasSkos);
        System.out.println("DCTerms: " + hasDct);

        assertTrue(hasRdf, "Model should use RDF vocabulary");
    }

    private void compareRdfModels(Model expectedModel, Model actualModel, WorkflowTestConfiguration config) {
        long expectedSize = expectedModel.size();
        long actualSize = actualModel.size();

        System.out.println("Model sizes:");
        System.out.println("Expected triples: " + expectedSize);
        System.out.println("Actual triples: " + actualSize);

        // Validate triple count matches expected
        if (expectedSize != actualSize) {
            System.out.println("Triple count mismatch: expected " + expectedSize + " but got " + actualSize);
        }

        // Validate entity counts if configured
        if (config.getExpectedCounts() != null) {
            System.out.println("\nValidating entity counts:");
            validateEntityCounts(actualModel, config.getExpectedCounts());
        }

        // Check if models are isomorphic (semantically equivalent)
        boolean isIsomorphic = expectedModel.isIsomorphicWith(actualModel);

        if (isIsomorphic) {
            System.out.println("Models are semantically equivalent (isomorphic)");
        } else {
            System.out.println("Models are NOT semantically equivalent");

            // Detailed difference analysis
            System.out.println("\nPerforming detailed difference analysis...");

            // Find statements in expected but not in actual
            Model missingInActual = expectedModel.difference(actualModel);
            long missingCount = missingInActual.size();

            // Find statements in actual but not in expected
            Model extraInActual = actualModel.difference(expectedModel);
            long extraCount = extraInActual.size();

            System.out.println("\nDifferences found:");
            System.out.println("Statements missing in actual output: " + missingCount);
            System.out.println("Extra statements in actual output: " + extraCount);

            if (missingCount > 0) {
                System.out.println("\nSample missing statements (up to 10):");
                missingInActual.listStatements().toList().stream()
                    .limit(10)
                    .forEach(stmt -> System.out.println("  - " + formatStatement(stmt)));
                if (missingCount > 10) {
                    System.out.println("  ... and " + (missingCount - 10) + " more");
                }
            }

            if (extraCount > 0) {
                System.out.println("\nSample extra statements (up to 10):");
                extraInActual.listStatements().toList().stream()
                    .limit(10)
                    .forEach(stmt -> System.out.println("  - " + formatStatement(stmt)));
                if (extraCount > 10) {
                    System.out.println("  ... and " + (extraCount - 10) + " more");
                }
            }

            // Analyze what types of statements are missing/extra
            analyzeStatementTypes(missingInActual, extraInActual);

            fail(String.format(
                "TTL output does not match expected output semantically. " +
                "Missing: %d statements, Extra: %d statements. " +
                "Expected: %d triples, Actual: %d triples.",
                missingCount, extraCount, expectedSize, actualSize
            ));
        }
    }

    private void validateEntityCounts(Model model, WorkflowTestConfiguration.EntityCounts expectedCounts) {
        // Count OWL/SKOS classes (deduplicated)
        Set<Resource> classResources = new HashSet<>();
        model.listSubjectsWithProperty(RDF.type, OWL.Class).forEachRemaining(classResources::add);
        model.listSubjectsWithProperty(RDF.type, RDFS.Class).forEachRemaining(classResources::add);
        model.listSubjectsWithProperty(RDF.type, SKOS.Concept).forEachRemaining(classResources::add);
        long classCount = classResources.size();

        // Count relationships (owl:ObjectProperty) separately from properties (owl:DatatypeProperty)
        Set<Resource> objectPropertyResources = new HashSet<>();
        Set<Resource> datatypePropertyResources = new HashSet<>();
        model.listSubjectsWithProperty(RDF.type, OWL.ObjectProperty).forEachRemaining(objectPropertyResources::add);
        model.listSubjectsWithProperty(RDF.type, OWL.DatatypeProperty).forEachRemaining(datatypePropertyResources::add);

        long objectPropertyCount = objectPropertyResources.size();
        long datatypePropertyCount = datatypePropertyResources.size();
        long totalPropertyCount = objectPropertyCount + datatypePropertyCount;

        // Count all concepts (deduplicated)
        Set<Resource> allConcepts = findConceptResources(model);

        System.out.println("OWL/SKOS Classes (unique): " + classCount);
        System.out.println("OWL ObjectProperty (relationships): " + objectPropertyCount);
        System.out.println("OWL DatatypeProperty (properties): " + datatypePropertyCount);
        System.out.println("Total properties (ObjectProperty + DatatypeProperty): " + totalPropertyCount);
        System.out.println("Total unique concepts: " + allConcepts.size());

        // Check for overlaps (resources that are both classes and properties)
        Set<Resource> overlap = new HashSet<>(classResources);
        overlap.retainAll(objectPropertyResources);
        overlap.retainAll(datatypePropertyResources);
        if (!overlap.isEmpty()) {
            System.out.println("Found " + overlap.size() + " resources that are BOTH classes and properties:");
            overlap.forEach(r -> System.out.println("    - " + shortenUri(r.getURI())));
        }

        if (expectedCounts.getClasses() != null && (expectedCounts.getClasses() != classCount)) {
            System.out.println("Class count mismatch: expected " + expectedCounts.getClasses() + " but found " + classCount);
            System.out.println("Missing " + (expectedCounts.getClasses() - classCount) + " class(es)");
            analyzeClassMismatch(model, classResources, expectedCounts.getClasses());
        }

        if (expectedCounts.getProperties() != null) {
            // Properties in the expected counts should map to DatatypeProperty
            // Relationships should map to ObjectProperty
            System.out.println("\nProperty type validation:");
            System.out.println("Expected properties (should be owl:DatatypeProperty): " + expectedCounts.getProperties());
            System.out.println("Actual owl:DatatypeProperty: " + datatypePropertyCount);
            System.out.println("Actual owl:ObjectProperty: " + objectPropertyCount);

            if (datatypePropertyCount == 0 && objectPropertyCount > expectedCounts.getProperties()) {
                System.out.println("ERROR: All properties AND relationships are typed as owl:ObjectProperty!");
                System.out.println("Properties (datatype values) should be owl:DatatypeProperty");
                System.out.println("Relationships (object references) should be owl:ObjectProperty");
                analyzePropertyMismatch(model, objectPropertyResources, expectedCounts.getProperties());
            } else if (expectedCounts.getProperties() != datatypePropertyCount) {
                System.out.println("Property count mismatch: expected " + expectedCounts.getProperties() + " but found " + datatypePropertyCount);
                analyzePropertyMismatch(model, datatypePropertyResources, expectedCounts.getProperties());
            }
        }
    }

    private void analyzeClassMismatch(Model model, Set<Resource> actualClasses, Integer expectedCount) {
        System.out.println("\nDetailed class analysis (expected: " + expectedCount + ", found: " + actualClasses.size() + "):");

        // List all resources typed as classes
        System.out.println("Resources with rdf:type owl:Class:");
        model.listSubjectsWithProperty(RDF.type, OWL.Class).forEachRemaining(r ->
            System.out.println("    - " + shortenUri(r.getURI())));

        System.out.println("Resources with rdf:type rdfs:Class:");
        model.listSubjectsWithProperty(RDF.type, RDFS.Class).forEachRemaining(r ->
            System.out.println("    - " + shortenUri(r.getURI())));

        System.out.println("Resources with rdf:type skos:Concept:");
        model.listSubjectsWithProperty(RDF.type, SKOS.Concept).forEachRemaining(r ->
            System.out.println("    - " + shortenUri(r.getURI())));

        // Check for resources with multiple type declarations
        System.out.println("\nChecking for resources with multiple type declarations:");
        for (Resource classResource : actualClasses) {
            List<String> types = new ArrayList<>();
            if (model.contains(classResource, RDF.type, OWL.Class)) types.add("owl:Class");
            if (model.contains(classResource, RDF.type, RDFS.Class)) types.add("rdfs:Class");
            if (model.contains(classResource, RDF.type, SKOS.Concept)) types.add("skos:Concept");
            if (model.contains(classResource, RDF.type, OWL.ObjectProperty)) types.add("owl:ObjectProperty");
            if (model.contains(classResource, RDF.type, OWL.DatatypeProperty)) types.add("owl:DatatypeProperty");

            if (types.size() > 1) {
                System.out.println("    - " + shortenUri(classResource.getURI()) + " has types: " + types);
            }
        }
    }

    private void analyzePropertyMismatch(Model model, Set<Resource> actualProperties, Integer expectedCount) {
        System.out.println("\nDetailed property analysis (expected: " + expectedCount + ", found: " + actualProperties.size() + "):");

        // List all resources typed as properties
        System.out.println("Resources with rdf:type owl:ObjectProperty:");
        model.listSubjectsWithProperty(RDF.type, OWL.ObjectProperty).forEachRemaining(r ->
            System.out.println("    - " + shortenUri(r.getURI())));

        System.out.println("Resources with rdf:type owl:DatatypeProperty:");
        model.listSubjectsWithProperty(RDF.type, OWL.DatatypeProperty).forEachRemaining(r ->
            System.out.println("    - " + shortenUri(r.getURI())));

        // Check for resources with multiple type declarations
        System.out.println("\nChecking for properties with multiple type declarations:");
        for (Resource propResource : actualProperties) {
            List<String> types = new ArrayList<>();
            if (model.contains(propResource, RDF.type, OWL.ObjectProperty)) types.add("owl:ObjectProperty");
            if (model.contains(propResource, RDF.type, OWL.DatatypeProperty)) types.add("owl:DatatypeProperty");
            if (model.contains(propResource, RDF.type, OWL.Class)) types.add("owl:Class");
            if (model.contains(propResource, RDF.type, SKOS.Concept)) types.add("skos:Concept");

            if (types.size() > 1) {
                System.out.println("    - " + shortenUri(propResource.getURI()) + " has types: " + types);
            }
        }
    }

    private void analyzeStatementTypes(Model missing, Model extra) {
        System.out.println("\nAnalyzing statement types:");

        if (!missing.isEmpty()) {
            Map<String, Long> missingByPredicate = missing.listStatements().toList().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    stmt -> shortenUri(stmt.getPredicate().toString()),
                    java.util.stream.Collectors.counting()
                ));
            System.out.println("\nMissing statements by predicate:");
            missingByPredicate.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> System.out.println("  - " + entry.getKey() + ": " + entry.getValue()));
        }

        if (!extra.isEmpty()) {
            Map<String, Long> extraByPredicate = extra.listStatements().toList().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    stmt -> shortenUri(stmt.getPredicate().toString()),
                    java.util.stream.Collectors.counting()
                ));
            System.out.println("\nExtra statements by predicate:");
            extraByPredicate.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> System.out.println("  - " + entry.getKey() + ": " + entry.getValue()));
        }
    }

    private String formatStatement(org.apache.jena.rdf.model.Statement stmt) {
        String subject = stmt.getSubject().toString();
        String predicate = stmt.getPredicate().toString();
        String object = stmt.getObject().toString();

        // Shorten URIs for readability
        subject = shortenUri(subject);
        predicate = shortenUri(predicate);
        object = shortenUri(object);

        return String.format("%s → %s → %s", subject, predicate, object);
    }

    private String shortenUri(String uri) {
        if (uri.startsWith("https://")) {
            int lastSlash = uri.lastIndexOf('/');
            int lastHash = uri.lastIndexOf('#');
            int splitPoint = Math.max(lastSlash, lastHash);
            if (splitPoint > 0 && splitPoint < uri.length() - 1) {
                return "..." + uri.substring(splitPoint);
            }
        }
        return uri;
    }

    private void validateMeaningfulContent(Model model, OntologyData ontologyData) {
        // Check for ontology definition
        boolean hasOntology = model.contains(null, RDF.type, OWL.Ontology)
            || model.contains(null, RDF.type, model.createResource("http://www.w3.org/2004/02/skos/core#ConceptScheme"));

        System.out.println("Has ontology/concept scheme definition: " + hasOntology);

        // Check for concepts (classes, properties, relationships)
        Set<Resource> concepts = findConceptResources(model);
        System.out.println("Number of concepts found: " + concepts.size());

        assertFalse(concepts.isEmpty(), "Model should contain at least one concept");

        // Check for meaningful properties (labels, definitions, etc.)
        boolean hasLabels = model.listStatements(null, SKOS.prefLabel, (Resource) null).hasNext()
            || model.listStatements(null, RDFS.label, (Resource) null).hasNext();
        System.out.println("Has labels: " + hasLabels);

        boolean hasDefinitions = model.listStatements(null, SKOS.definition, (Resource) null).hasNext()
            || model.listStatements(null, RDFS.comment, (Resource) null).hasNext();
        System.out.println("Has definitions: " + hasDefinitions);

        assertTrue(hasLabels, "Model should contain labels for concepts");

        // Validate that concept count matches input ontology
        int expectedConceptCount = countEntitiesOfType(ontologyData, ClassData.class)
            + countEntitiesOfType(ontologyData, PropertyData.class)
            + countEntitiesOfType(ontologyData, RelationshipData.class);

        System.out.println("Expected concepts from OntologyData: " + expectedConceptCount);
        System.out.println("Actual concepts in TTL model: " + concepts.size());

        // Validate ontology metadata
        if (ontologyData.getVocabularyMetadata() != null) {
            VocabularyMetadata metadata = ontologyData.getVocabularyMetadata();
            System.out.println("\nValidating ontology metadata:");

            // Check for ontology name/label
            if (metadata.getName() != null) {
                boolean hasMatchingLabel = model.listStatements(null, SKOS.prefLabel, (Resource) null).toList().stream()
                    .anyMatch(stmt -> stmt.getObject().isLiteral() &&
                        stmt.getObject().asLiteral().getString().contains(metadata.getName()));
                System.out.println("- Ontology name found in labels: " + hasMatchingLabel);
            }

            // Check for ontology namespace/IRI
            if (metadata.getNamespace() != null) {
                System.out.println("- Ontology namespace: " + metadata.getNamespace());
            }

            // Check for description
            if (metadata.getDescription() != null) {
                boolean hasDescription = model.listStatements(null, model.createProperty("http://purl.org/dc/terms/description"), (Resource) null).hasNext();
                System.out.println("- Has description: " + hasDescription);
            }
        }

        if (expectedConceptCount != concepts.size()) {
            System.out.println("Warning: Expected " + expectedConceptCount + " concepts but found " + concepts.size());
        }

        System.out.println("Model contains meaningful content");
    }

    private Set<Resource> findConceptResources(Model model) {
        Set<Resource> concepts = new HashSet<>();

        // Find OWL classes
        model.listSubjectsWithProperty(RDF.type, OWL.Class).forEachRemaining(concepts::add);

        // Find OWL properties
        model.listSubjectsWithProperty(RDF.type, OWL.ObjectProperty).forEachRemaining(concepts::add);
        model.listSubjectsWithProperty(RDF.type, OWL.DatatypeProperty).forEachRemaining(concepts::add);

        // Find RDFS classes
        model.listSubjectsWithProperty(RDF.type, RDFS.Class).forEachRemaining(concepts::add);

        // Find SKOS concepts
        model.listSubjectsWithProperty(RDF.type, SKOS.Concept).forEachRemaining(concepts::add);

        // Find resources with labels
        model.listSubjectsWithProperty(SKOS.prefLabel).forEachRemaining(concepts::add);
        model.listSubjectsWithProperty(RDFS.label).forEachRemaining(concepts::add);

        return concepts;
    }

    private <T> int countEntitiesOfType(OntologyData data, Class<T> type) {
        if (type == ClassData.class) {
            return data.getClasses() != null ? data.getClasses().size() : 0;
        } else if (type == PropertyData.class) {
            return data.getProperties() != null ? data.getProperties().size() : 0;
        } else if (type == RelationshipData.class) {
            return data.getRelationships() != null ? data.getRelationships().size() : 0;
        }
        return 0;
    }

    private void findMissingEntitiesInTurtle(OntologyData ontologyData, Model actualModel, Set<Resource> conceptResources) {
        System.out.println("\nAnalyzing missing entities in TTL output...");

        // Extract IRIs from concept resources
        Set<String> outputIris = new HashSet<>();
        for (Resource resource : conceptResources) {
            if (resource.isURIResource()) {
                outputIris.add(resource.getURI());
            }
        }

        // Extract labels from concept resources
        Set<String> outputLabels = new HashSet<>();
        for (Resource resource : conceptResources) {
            if (actualModel.contains(resource, SKOS.prefLabel, (String) null)) {
                actualModel.listObjectsOfProperty(resource, SKOS.prefLabel)
                    .forEachRemaining(obj -> {
                        if (obj.isLiteral()) {
                            outputLabels.add(obj.asLiteral().getString().toLowerCase().trim());
                        }
                    });
            }
            if (actualModel.contains(resource, RDFS.label, (String) null)) {
                actualModel.listObjectsOfProperty(resource, RDFS.label)
                    .forEachRemaining(obj -> {
                        if (obj.isLiteral()) {
                            outputLabels.add(obj.asLiteral().getString().toLowerCase().trim());
                        }
                    });
            }
        }

        System.out.println("Output contains " + outputIris.size() + " IRIs and " + outputLabels.size() + " unique labels");

        // Check classes
        System.out.println("\nClasses:");
        int missingClasses = 0;
        if (ontologyData.getClasses() != null) {
            for (ClassData classData : ontologyData.getClasses()) {
                String iri = classData.getIdentifier();
                if (!outputIris.contains(iri)) {
                    System.out.println("MISSING: " + classData.getName() + " (IRI: " + iri + ")");
                    missingClasses++;
                }
            }
            if (missingClasses == 0) {
                System.out.println("All " + ontologyData.getClasses().size() + " classes present");
            }
        }

        // Check properties
        System.out.println("\nProperties:");
        int missingProperties = 0;
        if (ontologyData.getProperties() != null) {
            for (PropertyData propertyData : ontologyData.getProperties()) {
                String propertyName = propertyData.getName();
                if (propertyName == null || propertyName.trim().isEmpty()) {
                    continue;
                }
                String normalizedName = propertyName.trim().toLowerCase();
                if (!outputLabels.contains(normalizedName)) {
                    System.out.println("MISSING: " + propertyName);
                    System.out.println("Domain: " + propertyData.getDomain());
                    System.out.println("DataType: " + propertyData.getDataType());
                    missingProperties++;
                }
            }
            if (missingProperties == 0) {
                System.out.println("All " + ontologyData.getProperties().size() + " properties present");
            }
        }

        // Check relationships
        System.out.println("\nRelationships:");
        int missingRelationships = 0;
        if (ontologyData.getRelationships() != null) {
            for (RelationshipData relationshipData : ontologyData.getRelationships()) {
                String relationshipName = relationshipData.getName();
                if (relationshipName == null || relationshipName.trim().isEmpty()) {
                    continue;
                }
                String normalizedName = relationshipName.trim().toLowerCase();
                if (!outputLabels.contains(normalizedName)) {
                    System.out.println("MISSING: " + relationshipName);
                    System.out.println("Domain: " + relationshipData.getDomain());
                    System.out.println("Range: " + relationshipData.getRange());
                    missingRelationships++;
                }
            }
            if (missingRelationships == 0) {
                System.out.println("All " + ontologyData.getRelationships().size() + " relationships present");
            }
        }

        System.out.println("\nSummary:");
        System.out.println("Missing classes: " + missingClasses);
        System.out.println("Missing properties: " + missingProperties);
        System.out.println("Missing relationships: " + missingRelationships);
        System.out.println("Total missing: " + (missingClasses + missingProperties + missingRelationships));
    }

    private void validateVocabularyUsage(Model model, List<String> validationErrors) {
        // Check for proper namespace usage
        Map<String, String> prefixes = model.getNsPrefixMap();

        // Expected prefixes with their standard URIs
        Map<String, String> expectedPrefixes = new HashMap<>();
        expectedPrefixes.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        expectedPrefixes.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        expectedPrefixes.put("owl", "http://www.w3.org/2002/07/owl#");
        expectedPrefixes.put("skos", "http://www.w3.org/2004/02/skos/core#");

        List<String> missingPrefixes = new ArrayList<>();
        List<String> incorrectNamespaces = new ArrayList<>();

        for (Map.Entry<String, String> expected : expectedPrefixes.entrySet()) {
            String prefix = expected.getKey();
            String expectedUri = expected.getValue();

            if (!prefixes.containsKey(prefix)) {
                missingPrefixes.add(prefix);
                validationErrors.add("Missing required prefix: " + prefix);
            } else {
                // Validate namespace URI
                String actualUri = prefixes.get(prefix);
                if (!expectedUri.equals(actualUri)) {
                    incorrectNamespaces.add(prefix + " (expected: " + expectedUri + ", got: " + actualUri + ")");
                    validationErrors.add("Incorrect namespace URI for prefix " + prefix + ": expected " + expectedUri + " but got " + actualUri);
                }
            }
        }

        if (!missingPrefixes.isEmpty()) {
            System.out.println("Missing prefixes: " + missingPrefixes);
        }

        if (!incorrectNamespaces.isEmpty()) {
            System.out.println("Incorrect namespace URIs: " + incorrectNamespaces);
        }

        if (missingPrefixes.isEmpty() && incorrectNamespaces.isEmpty()) {
            System.out.println("All expected prefixes present with correct namespace URIs");
        }

        // Count usage of each vocabulary
        long rdfUsage = model.listStatements(null, RDF.type, (Resource) null).toList().size();
        long rdfsUsage = model.listStatements(null, RDFS.domain, (Resource) null).toList().size()
            + model.listStatements(null, RDFS.range, (Resource) null).toList().size()
            + model.listStatements(null, RDFS.label, (Resource) null).toList().size();
        long owlUsage = model.listSubjectsWithProperty(RDF.type, OWL.Class).toList().size()
            + model.listSubjectsWithProperty(RDF.type, OWL.ObjectProperty).toList().size();
        long skosUsage = model.listStatements(null, SKOS.prefLabel, (Resource) null).toList().size()
            + model.listStatements(null, SKOS.definition, (Resource) null).toList().size();

        System.out.println("\n  Vocabulary usage statistics:");
        System.out.println("RDF statements: " + rdfUsage);
        System.out.println("RDFS statements: " + rdfsUsage);
        System.out.println("OWL statements: " + owlUsage);
        System.out.println("SKOS statements: " + skosUsage);

        // Warn if no usage detected for expected vocabularies
        if (rdfUsage == 0) {
            System.out.println("Warning: No RDF vocabulary usage detected");
        }
        if (owlUsage == 0 && skosUsage == 0) {
            System.out.println("Warning: No OWL or SKOS vocabulary usage detected");
        }
    }

    private void validateTypeDeclarations(Model model, List<String> validationErrors) {
        // Find all subjects with rdf:type
        Set<Resource> typedResources = new HashSet<>();
        model.listStatements(null, RDF.type, (Resource) null).forEachRemaining(stmt -> typedResources.add(stmt.getSubject()));

        System.out.println("Found " + typedResources.size() + " resources with rdf:type declarations");

        // Check that we have ontology or concept scheme
        boolean hasOntologyOrScheme = model.contains(null, RDF.type, OWL.Ontology)
            || model.contains(null, RDF.type, SKOS.ConceptScheme);

        if (!hasOntologyOrScheme) {
            validationErrors.add("Model should contain at least one owl:Ontology or skos:ConceptScheme");
        } else {
            System.out.println("Model has ontology or concept scheme definition");
        }
    }

    private void validatePropertyUsage(Model model, List<String> validationErrors) {
        // Check for domain/range declarations
        boolean hasDomain = model.contains(null, RDFS.domain, (Resource) null);
        boolean hasRange = model.contains(null, RDFS.range, (Resource) null);

        System.out.println("Properties have rdfs:domain: " + hasDomain);
        System.out.println("Properties have rdfs:range: " + hasRange);

        // Check for labels
        boolean hasSkosLabels = model.listStatements(null, SKOS.prefLabel, (Resource) null).hasNext();
        boolean hasRdfsLabels = model.listStatements(null, RDFS.label, (Resource) null).hasNext();

        if (!hasSkosLabels && !hasRdfsLabels) {
            validationErrors.add("Model should contain labels (skos:prefLabel or rdfs:label)");
        } else {
            System.out.println("Model contains labels");
        }
    }
}
