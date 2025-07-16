package com.dia.models;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.impl.OntModelImpl;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.lang.reflect.Method;
import java.util.Set;

import static com.dia.constants.ArchiConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class OFNBaseModelTest {

    @Nested
    class ConstructorTests {

        @Test
        void testDefaultConstructor_initializeModelAndPrefixes() {
            // Test default constructor that creates minimal model with just POJEM
            OFNBaseModel baseModel = new OFNBaseModel();
            OntModel ontModel = baseModel.getOntModel();

            assertNotNull(ontModel, "OntModel should be initialized by constructor");
            assertInstanceOf(OntModelImpl.class, ontModel, "ontModel must be instance of OntModelImpl");
            assertEquals(OntModelSpec.OWL_MEM, ontModel.getSpecification(), "Model specification must be OWL_MEM");

            // Verify namespace prefixes
            assertEquals(DEFAULT_NS, ontModel.getNsPrefixURI("cz"), "Prefix 'cz' should correspond to DEFAULT_NS");
            assertEquals(RDF.getURI(), ontModel.getNsPrefixURI("rdf"), "Prefix 'rdf' should be registered");
            assertEquals(RDFS.getURI(), ontModel.getNsPrefixURI("rdfs"), "Prefix 'rdfs' should be registered");
            assertEquals(OWL2.getURI(), ontModel.getNsPrefixURI("owl"), "Prefix 'owl' should be registered");
            assertEquals(XSD, ontModel.getNsPrefixURI("xsd"), "Prefix 'xsd' should correspond to XSD");

            // Verify non-existent prefix returns null
            assertNull(ontModel.getNsPrefixURI("nonexistent"), "Non-existent prefix should return null");
        }

        @Test
        void testParameterizedConstructor_withRequiredSets() {
            Set<String> requiredClasses = Set.of(POJEM, TRIDA, VLASTNOST);
            Set<String> requiredProperties = Set.of(NAZEV, POPIS);

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            assertNotNull(ontModel, "OntModel should be initialized");

            // Verify only required classes are created
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + POJEM), "POJEM class should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + TRIDA), "TRIDA class should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + VLASTNOST), "VLASTNOST class should exist");

            // Verify non-required classes are not created
            assertNull(ontModel.getOntClass(DEFAULT_NS + TSP), "TSP class should not exist");
            assertNull(ontModel.getOntClass(DEFAULT_NS + TOP), "TOP class should not exist");
        }

        @Test
        void testConstructor_eachCallYieldsNewModel() {
            OntModel m1 = new OFNBaseModel().getOntModel();
            OntModel m2 = new OFNBaseModel().getOntModel();

            assertNotSame(m1, m2, "Each constructor call should return new model instance");
            assertEquals(m1.getNsPrefixURI("cz"), m2.getNsPrefixURI("cz"), "Prefixes 'cz' should be same");
            assertEquals(m1.getNsPrefixURI("rdf"), m2.getNsPrefixURI("rdf"), "Prefixes 'rdf' should be same");
        }
    }

    @Nested
    class DynamicModelCreationTests {

        @Test
        void testMinimalModel_onlyPOJEM() {
            Set<String> requiredClasses = Set.of(POJEM);
            Set<String> requiredProperties = Set.of();

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // Should have POJEM
            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);
            assertNotNull(pojemClass, "POJEM class should exist");
            assertEquals(POJEM, pojemClass.getLabel("cs"), "POJEM should have correct label");

            // Should not have other classes
            assertNull(ontModel.getOntClass(DEFAULT_NS + TRIDA), "TRIDA should not exist");
            assertNull(ontModel.getOntClass(DEFAULT_NS + VLASTNOST), "VLASTNOST should not exist");
            assertNull(ontModel.getOntClass(DEFAULT_NS + VZTAH), "VZTAH should not exist");
        }

        @Test
        void testFullModel_allClassesAndProperties() {
            Set<String> requiredClasses = Set.of(POJEM, TRIDA, TSP, TOP, VLASTNOST, VZTAH,
                    DATOVY_TYP, VEREJNY_UDAJ, NEVEREJNY_UDAJ);
            Set<String> requiredProperties = Set.of(NAZEV, POPIS, DEFINICE, ZDROJ, JE_PPDF,
                    AGENDA, AIS, USTANOVENI_NEVEREJNOST,
                    DEFINICNI_OBOR, OBOR_HODNOT, NADRAZENA_TRIDA,
                    ZPUSOB_SDILENI, ZPUSOB_ZISKANI, TYP_OBSAHU);

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // Verify all classes exist
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + POJEM), "POJEM should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + TRIDA), "TRIDA should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + TSP), "TSP should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + TOP), "TOP should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + VLASTNOST), "VLASTNOST should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + VZTAH), "VZTAH should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + DATOVY_TYP), "DATOVY_TYP should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + VEREJNY_UDAJ), "VEREJNY_UDAJ should exist");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + NEVEREJNY_UDAJ), "NEVEREJNY_UDAJ should exist");

            // Verify all properties exist
            assertNotNull(ontModel.getOntProperty(DEFAULT_NS + NAZEV), "NAZEV property should exist");
            assertNotNull(ontModel.getOntProperty(DEFAULT_NS + POPIS), "POPIS property should exist");
            assertNotNull(ontModel.getOntProperty(DEFAULT_NS + DEFINICE), "DEFINICE property should exist");
        }

        @Test
        void testClassHierarchy_subclassRelationships() {
            Set<String> requiredClasses = Set.of(POJEM, TRIDA, TSP, TOP, VLASTNOST, VZTAH);

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, Set.of());
            OntModel ontModel = baseModel.getOntModel();

            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);
            OntClass tridaClass = ontModel.getOntClass(DEFAULT_NS + TRIDA);
            OntClass tspClass = ontModel.getOntClass(DEFAULT_NS + TSP);
            OntClass topClass = ontModel.getOntClass(DEFAULT_NS + TOP);
            OntClass vlastnostClass = ontModel.getOntClass(DEFAULT_NS + VLASTNOST);
            OntClass vztahClass = ontModel.getOntClass(DEFAULT_NS + VZTAH);

            // Verify hierarchy relationships
            assertTrue(tridaClass.hasSuperClass(pojemClass), "TRIDA should be subclass of POJEM");
            assertTrue(tspClass.hasSuperClass(tridaClass), "TSP should be subclass of TRIDA");
            assertTrue(topClass.hasSuperClass(tridaClass), "TOP should be subclass of TRIDA");
            assertTrue(vlastnostClass.hasSuperClass(pojemClass), "VLASTNOST should be subclass of POJEM");
            assertTrue(vztahClass.hasSuperClass(pojemClass), "VZTAH should be subclass of POJEM");
        }

        @Test
        void testConditionalClassCreation_missingParentClass() {
            // Try to create TSP without TRIDA - TSP won't be created because TRIDA is missing
            Set<String> requiredClasses = Set.of(POJEM, TSP);

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, Set.of());
            OntModel ontModel = baseModel.getOntModel();

            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);
            OntClass tridaClass = ontModel.getOntClass(DEFAULT_NS + TRIDA);
            OntClass tspClass = ontModel.getOntClass(DEFAULT_NS + TSP);

            assertNotNull(pojemClass, "POJEM should exist");
            assertNull(tridaClass, "TRIDA should not be created when not explicitly requested");
            assertNull(tspClass, "TSP should not be created when parent TRIDA is missing");
        }

        @Test
        void testConditionalClassCreation_withParentClass() {
            // Create TSP with TRIDA explicitly included
            Set<String> requiredClasses = Set.of(POJEM, TRIDA, TSP);

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, Set.of());
            OntModel ontModel = baseModel.getOntModel();

            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);
            OntClass tridaClass = ontModel.getOntClass(DEFAULT_NS + TRIDA);
            OntClass tspClass = ontModel.getOntClass(DEFAULT_NS + TSP);

            assertNotNull(pojemClass, "POJEM should exist");
            assertNotNull(tridaClass, "TRIDA should exist when explicitly requested");
            assertNotNull(tspClass, "TSP should exist when parent TRIDA is available");
            assertTrue(tspClass.hasSuperClass(tridaClass), "TSP should be subclass of TRIDA");
        }
    }

    @Nested
    class PropertyCreationTests {

        @Test
        void testPropertyCreation_withCorrectDomainRange() {
            Set<String> requiredClasses = Set.of(POJEM, NEVEREJNY_UDAJ);
            Set<String> requiredProperties = Set.of(NAZEV, POPIS, DEFINICE, ZDROJ, JE_PPDF,
                    AGENDA, AIS, USTANOVENI_NEVEREJNOST);

            OFNBaseModel baseModel = new OFNBaseModel(requiredClasses, requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);
            OntClass neverejnyUdajClass = ontModel.getOntClass(DEFAULT_NS + NEVEREJNY_UDAJ);

            // Test string properties
            OntProperty nazevProp = ontModel.getOntProperty(DEFAULT_NS + NAZEV);
            assertNotNull(nazevProp, "NAZEV property should exist");
            assertEquals(NAZEV, nazevProp.getLabel("cs"), "NAZEV should have correct label");
            assertTrue(nazevProp.hasDomain(pojemClass), "NAZEV should have POJEM domain");
            assertTrue(nazevProp.hasRange(ontModel.getOntClass(XSD + "string")), "NAZEV should have string range");

            // Test URI property
            OntProperty zdrojProp = ontModel.getOntProperty(DEFAULT_NS + ZDROJ);
            assertNotNull(zdrojProp, "ZDROJ property should exist");
            assertTrue(zdrojProp.hasRange(ontModel.getOntClass(XSD + "anyURI")), "ZDROJ should have anyURI range");

            // Test boolean property
            OntProperty ppdfProp = ontModel.getOntProperty(DEFAULT_NS + JE_PPDF);
            assertNotNull(ppdfProp, "JE_PPDF property should exist");
            assertTrue(ppdfProp.hasRange(ontModel.getOntClass(XSD + "boolean")), "JE_PPDF should have boolean range");

            // Test resource properties
            OntProperty agendaProp = ontModel.getOntProperty(DEFAULT_NS + AGENDA);
            assertNotNull(agendaProp, "AGENDA property should exist");
            assertTrue(agendaProp.hasRange(RDFS.Resource), "AGENDA should have Resource range");

            // Test property with specific domain
            OntProperty ustanoveniProp = ontModel.getOntProperty(DEFAULT_NS + USTANOVENI_NEVEREJNOST);
            assertNotNull(ustanoveniProp, "USTANOVENI_NEVEREJNOST property should exist");
            assertTrue(ustanoveniProp.hasDomain(neverejnyUdajClass), "USTANOVENI should have NEVEREJNY_UDAJ domain");
        }

        @Test
        void testPropertyCreation_withoutDomainRange() {
            Set<String> requiredProperties = Set.of(DEFINICNI_OBOR, OBOR_HODNOT, NADRAZENA_TRIDA);

            OFNBaseModel baseModel = new OFNBaseModel(Set.of(POJEM), requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // These properties should exist but without specific domain/range
            OntProperty definicniOborProp = ontModel.getOntProperty(DEFAULT_NS + DEFINICNI_OBOR);
            assertNotNull(definicniOborProp, "DEFINICNI_OBOR property should exist");
            assertEquals(DEFINICNI_OBOR, definicniOborProp.getLabel("cs"), "Should have correct label");

            OntProperty oborHodnotProp = ontModel.getOntProperty(DEFAULT_NS + OBOR_HODNOT);
            assertNotNull(oborHodnotProp, "OBOR_HODNOT property should exist");

            OntProperty nadrazenaTrida = ontModel.getOntProperty(DEFAULT_NS + NADRAZENA_TRIDA);
            assertNotNull(nadrazenaTrida, "NADRAZENA_TRIDA property should exist");
        }

        @Test
        void testGovernanceProperties() {
            Set<String> requiredProperties = Set.of(ZPUSOB_SDILENI, ZPUSOB_ZISKANI, TYP_OBSAHU);

            OFNBaseModel baseModel = new OFNBaseModel(Set.of(POJEM), requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);

            OntProperty zpusobSdileniProp = ontModel.getOntProperty(DEFAULT_NS + ZPUSOB_SDILENI);
            assertNotNull(zpusobSdileniProp, "ZPUSOB_SDILENI property should exist");
            assertTrue(zpusobSdileniProp.hasDomain(pojemClass), "Should have POJEM domain");
            assertTrue(zpusobSdileniProp.hasRange(RDFS.Resource), "Should have Resource range");

            OntProperty zpusobZiskaniProp = ontModel.getOntProperty(DEFAULT_NS + ZPUSOB_ZISKANI);
            assertNotNull(zpusobZiskaniProp, "ZPUSOB_ZISKANI property should exist");

            OntProperty typObsahuProp = ontModel.getOntProperty(DEFAULT_NS + TYP_OBSAHU);
            assertNotNull(typObsahuProp, "TYP_OBSAHU property should exist");
        }

        @Test
        void testPropertyCreation_missingPrerequisites() {
            // Try to create properties that require POJEM class but don't provide it
            Set<String> requiredProperties = Set.of(NAZEV, POPIS);

            OFNBaseModel baseModel = new OFNBaseModel(Set.of(), requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // Properties should not be created because POJEM class is missing
            assertNull(ontModel.getOntProperty(DEFAULT_NS + NAZEV), "NAZEV should not exist without POJEM");
            assertNull(ontModel.getOntProperty(DEFAULT_NS + POPIS), "POPIS should not exist without POJEM");
        }

        @Test
        void testPropertyCreation_missingNEVEREJNY_UDAJ() {
            // Try to create USTANOVENI_NEVEREJNOST without NEVEREJNY_UDAJ class
            Set<String> requiredProperties = Set.of(USTANOVENI_NEVEREJNOST);

            OFNBaseModel baseModel = new OFNBaseModel(Set.of(POJEM), requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // Property should not be created because NEVEREJNY_UDAJ class is missing
            assertNull(ontModel.getOntProperty(DEFAULT_NS + USTANOVENI_NEVEREJNOST),
                    "USTANOVENI should not exist without NEVEREJNY_UDAJ");
        }
    }

    @Nested
    class XSDTypesTests {

        @Test
        void testXSDTypes_createdWhenRequired() {
            Set<String> requiredProperties = Set.of(NAZEV, POPIS, DEFINICE, ZDROJ, JE_PPDF);

            OFNBaseModel baseModel = new OFNBaseModel(Set.of(POJEM), requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // XSD types should be created because properties require them
            assertNotNull(ontModel.getOntClass(XSD + "string"), "XSD:string should be created");
            assertNotNull(ontModel.getOntClass(XSD + "boolean"), "XSD:boolean should be created");
            assertNotNull(ontModel.getOntClass(XSD + "anyURI"), "XSD:anyURI should be created");
        }

        @Test
        void testXSDTypes_notCreatedWhenNotRequired() {
            Set<String> requiredProperties = Set.of(AGENDA, AIS); // These don't require XSD types

            OFNBaseModel baseModel = new OFNBaseModel(Set.of(POJEM), requiredProperties);
            OntModel ontModel = baseModel.getOntModel();

            // XSD types should not be created
            assertNull(ontModel.getOntClass(XSD + "string"), "XSD:string should not be created");
            assertNull(ontModel.getOntClass(XSD + "boolean"), "XSD:boolean should not be created");
            assertNull(ontModel.getOntClass(XSD + "anyURI"), "XSD:anyURI should not be created");
        }

        @Test
        void testRequiresXSDTypes_logic() throws Exception {
            // Test the requiresXSDTypes method via reflection
            OFNBaseModel baseModel = new OFNBaseModel();

            Method requiresXSDTypesMethod = OFNBaseModel.class.getDeclaredMethod("requiresXSDTypes", Set.class);
            requiresXSDTypesMethod.setAccessible(true);

            // Test cases that should require XSD types
            assertTrue((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of(NAZEV)),
                    "NAZEV should require XSD types");
            assertTrue((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of(POPIS)),
                    "POPIS should require XSD types");
            assertTrue((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of(DEFINICE)),
                    "DEFINICE should require XSD types");
            assertTrue((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of(ZDROJ)),
                    "ZDROJ should require XSD types");
            assertTrue((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of(JE_PPDF)),
                    "JE_PPDF should require XSD types");

            // Test cases that should not require XSD types
            assertFalse((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of(AGENDA)),
                    "AGENDA should not require XSD types");
            assertFalse((Boolean) requiresXSDTypesMethod.invoke(baseModel, Set.of()),
                    "Empty set should not require XSD types");
        }
    }

    @Nested
    class ReflectionTests {

        @Test
        void testCreateDynamicBaseModel_reflection() throws Exception {
            // Create instance but don't use constructor to populate model
            OFNBaseModel baseModel = new OFNBaseModel(Set.of(), Set.of()); // Empty model

            // Use reflection to call createDynamicBaseModel with specific parameters
            Method createDynamicBaseModelMethod = OFNBaseModel.class.getDeclaredMethod(
                    "createDynamicBaseModel", Set.class, Set.class);
            createDynamicBaseModelMethod.setAccessible(true);

            Set<String> testClasses = Set.of(POJEM, TRIDA);
            Set<String> testProperties = Set.of(NAZEV);

            // Invoke the method
            createDynamicBaseModelMethod.invoke(baseModel, testClasses, testProperties);

            OntModel ontModel = baseModel.getOntModel();

            // Verify the method created the expected classes
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + POJEM), "POJEM should be created");
            assertNotNull(ontModel.getOntClass(DEFAULT_NS + TRIDA), "TRIDA should be created");
            assertNotNull(ontModel.getOntProperty(DEFAULT_NS + NAZEV), "NAZEV property should be created");
        }

        @Test
        void testCreateRequiredProperties_reflection() throws Exception {
            OFNBaseModel baseModel = new OFNBaseModel(Set.of(POJEM), Set.of());
            OntModel ontModel = baseModel.getOntModel();

            OntClass pojemClass = ontModel.getOntClass(DEFAULT_NS + POJEM);

            Method createRequiredPropertiesMethod = OFNBaseModel.class.getDeclaredMethod(
                    "createRequiredProperties", Set.class, OntClass.class, OntClass.class);
            createRequiredPropertiesMethod.setAccessible(true);

            Set<String> testProperties = Set.of(NAZEV, POPIS);

            // Invoke the method
            createRequiredPropertiesMethod.invoke(baseModel, testProperties, pojemClass, null);

            // Verify properties were created
            assertNotNull(ontModel.getOntProperty(DEFAULT_NS + NAZEV), "NAZEV should be created");
            assertNotNull(ontModel.getOntProperty(DEFAULT_NS + POPIS), "POPIS should be created");
        }
    }

    @Test
    void testUnknownProperty_loggedButIgnored() {
        // Test that unknown properties are logged but don't cause errors
        Set<String> requiredProperties = Set.of("unknown-property", NAZEV);

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            new OFNBaseModel(Set.of(POJEM), requiredProperties);
        }, "Unknown properties should not cause exceptions");
    }
}