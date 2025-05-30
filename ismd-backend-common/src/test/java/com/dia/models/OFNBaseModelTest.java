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
        assertEquals(LABEL_NAZEV, nazevProp.getLabel("cs"), "Vlastnost 'nazev' by měla mít český popisek");
        assertTrue(nazevProp.hasDomain(pojemClass), "Vlastnost 'nazev' by měla mít doménu TYP_POJEM");
        assertTrue(nazevProp.hasRange(ontModel.getOntClass(XSD + "string")), "Vlastnost 'nazev' by měla mít rozsah XSD:string");

        OntProperty popisProp = ontModel.getOntProperty(NS + LABEL_POPIS);
        assertNotNull(popisProp, "Vlastnost 'popis' by měla existovat");
        assertEquals(LABEL_POPIS, popisProp.getLabel("cs"), "Vlastnost 'popis' by měla mít český popisek");
        assertTrue(popisProp.hasDomain(pojemClass), "Vlastnost 'popis' by měla mít doménu TYP_POJEM");
        assertTrue(popisProp.hasRange(ontModel.getOntClass(XSD + "string")), "Vlastnost 'popis' by měla mít rozsah XSD:string");

        OntProperty definiceProp = ontModel.getOntProperty(NS + LABEL_DEF);
        assertNotNull(definiceProp, "Vlastnost 'definice' by měla existovat");
        assertEquals(LABEL_DEF, definiceProp.getLabel("cs"), "Vlastnost 'definice' by měla mít český popisek");
        assertTrue(definiceProp.hasDomain(pojemClass), "Vlastnost 'definice' by měla mít doménu TYP_POJEM");
        assertTrue(definiceProp.hasRange(ontModel.getOntClass(XSD + "string")), "Vlastnost 'definice' by měla mít rozsah XSD:string");

        OntProperty zdrojProp = ontModel.getOntProperty(NS + LABEL_ZDROJ);
        assertNotNull(zdrojProp, "Vlastnost 'zdroj' by měla existovat");
        assertEquals(LABEL_ZDROJ, zdrojProp.getLabel("cs"), "Vlastnost 'zdroj' by měla mít český popisek");
        assertTrue(zdrojProp.hasDomain(pojemClass), "Vlastnost 'zdroj' by měla mít doménu TYP_POJEM");
        assertTrue(zdrojProp.hasRange(ontModel.getOntClass(XSD + "anyURI")), "Vlastnost 'zdroj' by měla mít rozsah XSD:anyURI");

        OntProperty jeSdilenVPpdfProp = ontModel.getOntProperty(NS + LABEL_JE_PPDF);
        assertNotNull(jeSdilenVPpdfProp, "Vlastnost 'jeSdilenVPpdf' by měla existovat");
        assertEquals(LABEL_JE_PPDF, jeSdilenVPpdfProp.getLabel("cs"), "Vlastnost 'jeSdilenvPpdf' by měla mít český popisek");
        assertTrue(jeSdilenVPpdfProp.hasDomain(pojemClass), "Vlastnost 'jeSdilenVPpdf' by měla mít doménu TYP_POJEM");
        assertTrue(jeSdilenVPpdfProp.hasRange(ontModel.getOntClass(XSD + "boolean")), "Vlastnost 'jeSdilenVPpdf' by měla mít rozsah XSD:boolean");

        OntProperty agendaProp = ontModel.getOntProperty(NS + LABEL_AGENDA);
        assertNotNull(agendaProp, "Vlastnost 'agenda' by měla existovat");
        assertEquals(LABEL_AGENDA, agendaProp.getLabel("cs"), "Vlastnost 'agenda' by měla mít český popisek");
        assertTrue(agendaProp.hasDomain(pojemClass), "Vlastnost 'agenda' by měla mít doménu TYP_POJEM");
        assertTrue(agendaProp.hasRange(RDFS.Resource), "Vlastnost 'agenda' by měla mít rozsah RDFS:Resource");

        OntProperty aisProp = ontModel.getOntProperty(NS + LABEL_AIS);
        assertNotNull(aisProp, "Vlastnost 'ais' by měla existovat");
        assertEquals(LABEL_AIS, aisProp.getLabel("cs"), "Vlastnost 'ais' by měla mít český popisek");
        assertTrue(aisProp.hasDomain(pojemClass), "Vlastnost 'ais' by měla mít doménu TYP_POJEM");
        assertTrue(aisProp.hasRange(RDFS.Resource), "Vlastnost 'ais' by měla mít rozsah RDFS:Resource");

        OntProperty ustanoveniProp = ontModel.getOntProperty(NS + LABEL_UDN);
        assertNotNull(ustanoveniProp, "Vlastnost 'ustanoveni' by měla existovat");
        assertEquals(LABEL_UDN, ustanoveniProp.getLabel("cs"), "Vlastnost 'ustanoveni' by měla mít český popisek");
        assertTrue(ustanoveniProp.hasDomain(neverejnyUdajClass), "Vlastnost 'ustanoveni' by měla mít doménu TYP_NEVEREJNY_UDAJ");
        assertTrue(ustanoveniProp.hasRange(RDFS.Resource), "Vlastnost 'ustanoveni' by měla mít rozsah RDFS:Resource");

        OntProperty definicniOborProp = ontModel.getOntProperty(NS + LABEL_DEF_O);
        assertNotNull(definicniOborProp, "Vlastnost 'definicniObor' by měla existovat");
        assertEquals(LABEL_DEF_O, definicniOborProp.getLabel("cs"), "Vlastnost 'definicniObor' by měla mít český popisek");

        OntProperty oborHodnotProp = ontModel.getOntProperty(NS + LABEL_OBOR_HODNOT);
        assertNotNull(oborHodnotProp, "Vlastnost 'oborHodnot' by měla existovat");
        assertEquals(LABEL_OBOR_HODNOT, oborHodnotProp.getLabel("cs"), "Vlastnost 'oborHodnot' by měla mít český popisek");

        OntProperty nadrazenaTrida = ontModel.getOntProperty(NS + LABEL_NT);
        assertNotNull(nadrazenaTrida, "Vlastnost 'nadrazenaTrida' by měla existovat");
        assertEquals(LABEL_NT, nadrazenaTrida.getLabel("cs"), "Vlastnost 'nadrazenaTrida' by měla mít český popisek");

        OntProperty souvisejiciUstanoveni = ontModel.getOntProperty(NS + LABEL_SUPP);
        assertNotNull(souvisejiciUstanoveni, "Vlastnost 'souvisejiciUstanoveni' by měla existovat");
        assertEquals(LABEL_SUPP, souvisejiciUstanoveni.getLabel("cs"), "Vlastnost 'souvisejiciUstanoveni' by měla mít český popisek");

        OntProperty zpusobSdileniProp = ontModel.getOntProperty(NS + LABEL_ZPUSOB_SDILENI);
        assertNotNull(zpusobSdileniProp, "Vlastnost 'zpusobSdileni' by měla existovat");
        assertEquals("Způsob sdílení údaje", zpusobSdileniProp.getLabel("cs"), "Vlastnost 'zpusobSdileni' by měla mít český popisek");
        assertTrue(zpusobSdileniProp.hasDomain(pojemClass), "Vlastnost 'zpusobSdileni' by měla mít doménu TYP_POJEM");
        assertTrue(zpusobSdileniProp.hasRange(RDFS.Resource), "Vlastnost 'zpusobSdileni' by měla mít rozsah RDFS:Resource");

        OntProperty zpusobZiskaniProp = ontModel.getOntProperty(NS + LABEL_ZPUSOB_ZISKANI);
        assertNotNull(zpusobZiskaniProp, "Vlastnost 'zpusobZiskani' by měla existovat");
        assertEquals("Způsob získání údaje", zpusobZiskaniProp.getLabel("cs"), "Vlastnost 'zpusobZiskani' by měla mít český popisek");
        assertTrue(zpusobZiskaniProp.hasDomain(pojemClass), "Vlastnost 'zpusobZiskani' by měla mít doménu TYP_POJEM");
        assertTrue(zpusobZiskaniProp.hasRange(RDFS.Resource), "Vlastnost 'zpusobZiskani' by měla mít rozsah RDFS:Resource");

        OntProperty typObsahuProp = ontModel.getOntProperty(NS + LABEL_TYP_OBSAHU);
        assertNotNull(typObsahuProp, "Vlastnost 'typObsahu' by měla existovat");
        assertEquals("Typ obsahu údaje", typObsahuProp.getLabel("cs"), "Vlastnost 'typObsahu' by měla mít český popisek");
        assertTrue(typObsahuProp.hasDomain(pojemClass), "Vlastnost 'typObsahu' by měla mít doménu TYP_POJEM");
        assertTrue(typObsahuProp.hasRange(RDFS.Resource), "Vlastnost 'typObsahu' by měla mít rozsah RDFS:Resource");

    }

}


