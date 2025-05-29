package com.dia.models;

import static com.dia.constants.ArchiOntologyConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.impl.OntModelImpl;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OFNBaseModelTest {

    private OFNBaseModel baseModel;
    private OntModel ontModel;

    @BeforeEach
    void setUp () {
        baseModel = new OFNBaseModel();
        ontModel = baseModel.getOntModel();
    }

    @Test
    void testConstructor_initializeModelAndPrefixes() {
        assertNotNull(ontModel, "OntModel by měl být inicializován konstruktorem" );

        assertTrue(ontModel instanceof OntModelImpl, "ontModel musí být instancí OntModelImpl");
        assertEquals(OntModelSpec.OWL_MEM, ((OntModelImpl) ontModel).getSpecification(), "Specifikace modelu musí být OWL_MEM");

        assertEquals(NS, ontModel.getNsPrefixURI("cz"), "Prefix 'cz' by měl odpovídat NS");
        assertEquals(RDF.getURI(), ontModel.getNsPrefixURI("rdf"), "Prefix 'rdf' by měl být registrován");
        assertEquals(RDFS.getURI(), ontModel.getNsPrefixURI("rdfs"), "Prefix 'rdfs' by měl být registrován");
        assertEquals(OWL2.getURI(), ontModel.getNsPrefixURI("owl"), "Prefix 'owl' by měl být registrován");
        assertEquals(XSD, ontModel.getNsPrefixURI("xsd"), "Prefix 'xsd' by měl odpovídat XSD");
    }

    @Test
    void testConstructor_eachCallYieldsNewModel() {
        OntModel m1 = new OFNBaseModel().getOntModel();
        OntModel m2 = new OFNBaseModel().getOntModel();
        assertNotSame(m1, m2, "Každé volání konstruktorem by mělo vracet novou instanci modelu");
    }

    @Test
    void testCreateBaseModel_classesWithLabels() {
        assertNotNull(ontModel.getOntClass(XSD + "string"), "XSD:string by měl existovat");
        assertNotNull(ontModel.getOntClass(XSD + "boolean"), "XSD:boolean by měl existovat");
        assertNotNull(ontModel.getOntClass(XSD + "anyURI"), "XSD:anyURI by měl existovat");

        OntClass pojemClass = ontModel.getOntClass(NS + TYP_POJEM);
        assertNotNull(pojemClass, "Třída TYP_POJEM by měla existovat");
        assertEquals("pojem", pojemClass.getLabel("cs"), "Třída TYP_POJEM by měla mít správný popisek");

        OntClass vlastnostClass = ontModel.getOntClass(NS + TYP_VLASTNOST);
        assertNotNull(vlastnostClass, "Třída TYP_VLASTNOST by měla existovat");
        assertEquals("vlastnost", vlastnostClass.getLabel("cs"), "Třída TYP_VLASTNOST by měla mít správný popisek");
        assertTrue(vlastnostClass.hasSuperClass(pojemClass), "Třída TYP_VLASTNOST by měla rozšiřovat TYP_POJEM");

        OntClass vztahClass = ontModel.getOntClass(NS + TYP_VZTAH);
        assertNotNull(vztahClass, "Třída TYP_VZTAH by měla existovat");
        assertEquals("vztah", vztahClass.getLabel("cs"), "Třída TYP_VZTAH by měla mít správný popisek");
        assertTrue(vztahClass.hasSuperClass(pojemClass), "Třída TYP_VZTAH by měla rozšiřovat TYP_POJEM");

        OntClass tridaClass = ontModel.getOntClass(NS + TYP_TRIDA);
        assertNotNull(tridaClass, "Třída TYP_TRIDA by měla existovat");
        assertEquals("třída", tridaClass.getLabel("cs"), "Třída TYP_TRIDA by měla mít správný popisek");
        assertTrue(tridaClass.hasSuperClass(pojemClass), "Třída TYP_TRIDA by měla rozšiřovat TYP_POJEM");

        OntClass datovyTyp = ontModel.getOntClass(NS + TYP_DT);
        assertNotNull(datovyTyp, "Třída TYP_DT by měla existovat");
        assertEquals(LABEL_DT, datovyTyp.getLabel("cs"), "Třída TYP_DT by měla mít správný popisek");

        OntClass typSubjektuClass = ontModel.getOntClass(NS + TYP_TSP);
        assertNotNull(typSubjektuClass, "Třída TYP_TSP by měla existovat");
        assertTrue(typSubjektuClass.hasSuperClass(tridaClass), "Třída TYP_TSP by měla rozšiřovat TYP_TRIDA");

        OntClass typObjektuClass = ontModel.getOntClass(NS + TYP_TOP);
        assertNotNull(typObjektuClass, "Třída TOP by měla existovat");
        assertTrue(typObjektuClass.hasSuperClass(tridaClass), "Třída TYP_TOP by měla rozšiřovat TYP_TRIDA");

        OntClass verejnyUdajClass = ontModel.getOntClass(NS + TYP_VEREJNY_UDAJ);
        assertNotNull(verejnyUdajClass, "Třída TYP_VEREJNY_UDAJ by měla existovat");
        assertEquals(LABEL_VU, verejnyUdajClass.getLabel("cs"), "Třída TYP_VEREJNY_UDAJ by měla mít správný popisek");

        OntClass neverejnyUdajClass = ontModel.getOntClass(NS + TYP_NEVEREJNY_UDAJ);
        assertNotNull(neverejnyUdajClass, "Třída TYP_NEVEREJNY_UDAJ by měla existovat");
        assertEquals(LABEL_NVU, neverejnyUdajClass.getLabel("cs"), "Třída TYP_NEVEREJNY_UDAJ by měla mít správný popisek");
    }

    @Test
    void testCreateBaseModel_properties() {
        OntClass pojemClass     = ontModel.getOntClass(NS + TYP_POJEM);
        OntClass neverejnyUdajClass  = ontModel.getOntClass(NS + TYP_NEVEREJNY_UDAJ);

        OntProperty nazevProp = ontModel.getOntProperty(NS + LABEL_NAZEV);
        assertNotNull(nazevProp, "Vlastnost 'nazev' by měla existovat");
        assertEquals(LABEL_NAZEV, nazevProp.getLabel("cs"), "Property 'nazev' by měla mít český popisek");
        assertTrue(nazevProp.hasDomain(pojemClass), "Property 'nazev' by měla mít doménu TYP_POJEM");
        assertTrue(nazevProp.hasRange(ontModel.getOntClass(XSD + "string")), "Property 'nazev' by měla mít rozsah XSD:string");

        OntProperty popisProp = ontModel.getOntProperty(NS + LABEL_POPIS);
        assertNotNull(popisProp, "Vlastnost 'popis' by měla existovat");
        assertEquals(LABEL_POPIS, popisProp.getLabel("cs"), "Property 'popis' by měla mít český popisek");
        assertTrue(popisProp.hasDomain(pojemClass), "Property 'popis' by měla mít doménu TYP_POPIS");
        assertTrue(popisProp.hasRange(ontModel.getOntClass(XSD + "string")), "Property 'popis' by měla mít rozsah XSD:string");

        OntProperty definiceProp = ontModel.getOntProperty(NS + LABEL_DEF);
        assertNotNull(definiceProp, "Vlastnost 'definice' by měla existovat");
        assertEquals(LABEL_DEF, definiceProp.getLabel("cs"), "Property 'definice' by měla mít český popisek");
        assertTrue(definiceProp.hasDomain(pojemClass), "Property 'definice' by měla mít doménu TYP_POPIS");
        assertTrue(definiceProp.hasRange(ontModel.getOntClass(XSD + "string")), "Property 'definice' by měla mít rozsah XSD:string");

        OntProperty zdrojProp = ontModel.getOntProperty(NS + LABEL_ZDROJ);
        assertNotNull(zdrojProp, "Vlastnost 'zdroj' by měla existovat");
        assertEquals(LABEL_ZDROJ, zdrojProp.getLabel("cs"), "Property 'zdroj' by měla mít český popisek");
        assertTrue(zdrojProp.hasDomain(pojemClass), "Property 'zdroj' by měla mít doménu TYP_POJEM");
        assertTrue(zdrojProp.hasRange(ontModel.getOntClass(XSD + "anyURI")), "Property 'zdroj' by měla mít rozsah XSD:anyURI");

        OntProperty jeSdilenVPpdfProp = ontModel.getOntProperty(NS + LABEL_JE_PPDF);
        assertNotNull(jeSdilenVPpdfProp, "Vlastnost 'jeSdilenVPpdf' by měla existovat");
        assertTrue(jeSdilenVPpdfProp.hasDomain(pojemClass), "Property 'jeSdilenVPpdf' by měla mít doménu TYP_POJEM");
        assertTrue(jeSdilenVPpdfProp.hasRange(ontModel.getOntClass(XSD + "boolean")), "Property 'jeSdilenVPpdf' by měla mít rozsah XSD:boolean");

        OntProperty ustanoveniProp = ontModel.getOntProperty(NS + LABEL_UDN);
        assertNotNull(ustanoveniProp, "Vlastnost 'ustanoveni' by měla existovat");
        assertTrue(ustanoveniProp.hasDomain(neverejnyUdajClass), "Property 'ustanoveni' by měla mít doménu TYP_NEVEREJNY_UDAJ");
        assertTrue(ustanoveniProp.hasRange(RDFS.Resource), "Property 'ustanoveni' by měla mít rozsah RDFS:Resource");
    }

}


