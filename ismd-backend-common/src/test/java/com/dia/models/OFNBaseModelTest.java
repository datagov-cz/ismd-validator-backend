package com.dia.models;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.impl.OntModelImpl;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.dia.constants.ArchiOntologyConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class OFNBaseModelTest {

    private OntModel ontModel;

    @BeforeEach
    void setUp() {
        OFNBaseModel baseModel = new OFNBaseModel();
        ontModel = baseModel.getOntModel();
    }

    // Test konstruktora: ověření inicializace modelu, OWL-MEM a namespace prefixů
    @Test
    void testConstructor_initializeModelAndPrefixes() {
        assertNotNull(ontModel, "OntModel by měl být inicializován konstruktorem");

        assertInstanceOf(OntModelImpl.class, ontModel, "ontModel musí být instancí OntModelImpl");
        assertEquals(OntModelSpec.OWL_MEM, ontModel.getSpecification(), "Specifikace modelu musí být OWL_MEM");

        assertEquals(NS, ontModel.getNsPrefixURI("cz"), "Prefix 'cz' by měl odpovídat NS");
        assertEquals(RDF.getURI(), ontModel.getNsPrefixURI("rdf"), "Prefix 'rdf' by měl být registrován");
        assertEquals(RDFS.getURI(), ontModel.getNsPrefixURI("rdfs"), "Prefix 'rdfs' by měl být registrován");
        assertEquals(OWL2.getURI(), ontModel.getNsPrefixURI("owl"), "Prefix 'owl' by měl být registrován");
        assertEquals(XSD, ontModel.getNsPrefixURI("xsd"), "Prefix 'xsd' by měl odpovídat XSD");

        // Ověření neexistujicího prefixu vrací null
        assertNull(ontModel.getNsPrefixURI("neexistujici"), "Neexistující prefix by měl vracel null");
    }

    // Ověření, že každé volání konstruktorem vrací novou instanci modelu se stejnými prefixy
    @Test
    void testConstructor_eachCallYieldsNewModel() {
        OntModel m1 = new OFNBaseModel().getOntModel();
        OntModel m2 = new OFNBaseModel().getOntModel();
        assertNotSame(m1, m2, "Každé volání konstruktorem by mělo vracet novou instanci modelu");

        assertEquals(m1.getNsPrefixURI("cz"), m2.getNsPrefixURI("cz"), "Prefixy 'cz' by měli být stejné");
        assertEquals(m1.getNsPrefixURI("rdf"), m2.getNsPrefixURI("rdf"), "Prefixy 'rdf' by měli být stejné");
    }

    // Test createBaseModel(): ověření existence XSD tříd a základních ontologických tříd s popisky a hierarchií
    @Test
    void testCreateBaseModel_classesWithLabels() {
        assertNotNull(ontModel.getOntClass(XSD + "string"), "XSD:string by měl existovat");
        assertNotNull(ontModel.getOntClass(XSD + "boolean"), "XSD:boolean by měl existovat");
        assertNotNull(ontModel.getOntClass(XSD + "anyURI"), "XSD:anyURI by měl existovat");

        // Ověření, že třída 'pojem' existuje a má správný český popisek
        OntClass pojemClass = ontModel.getOntClass(NS + TYP_POJEM);
        assertNotNull(pojemClass, "Třída TYP_POJEM by měla existovat");
        assertEquals("pojem", pojemClass.getLabel("cs"), "Třída TYP_POJEM by měla mít správný popisek");
        assertEquals(NS + TYP_POJEM, pojemClass.getURI(), "URI třídy 'pojem' musí odpovídat NS+TYP_POJEM");

        // Ověření, že třída 'vlastnost' existuje, má správný label a je podtřídou 'pojem'
        OntClass vlastnostClass = ontModel.getOntClass(NS + TYP_VLASTNOST);
        assertNotNull(vlastnostClass, "Třída TYP_VLASTNOST by měla existovat");
        assertEquals("vlastnost", vlastnostClass.getLabel("cs"), "Třída TYP_VLASTNOST by měla mít správný popisek");
        assertTrue(vlastnostClass.hasSuperClass(pojemClass), "Třída TYP_VLASTNOST by měla rozšiřovat TYP_POJEM");

        // Ověření, že třída 'vztah' existuje, má správný label a je podtřídou 'pojem'
        OntClass vztahClass = ontModel.getOntClass(NS + TYP_VZTAH);
        assertNotNull(vztahClass, "Třída TYP_VZTAH by měla existovat");
        assertEquals("vztah", vztahClass.getLabel("cs"), "Třída TYP_VZTAH by měla mít správný popisek");
        assertTrue(vztahClass.hasSuperClass(pojemClass), "Třída TYP_VZTAH by měla rozšiřovat TYP_POJEM");

        // Ověření, že třída 'trida' existuje, má správný label a je podtřídou 'pojem'
        OntClass tridaClass = ontModel.getOntClass(NS + TYP_TRIDA);
        assertNotNull(tridaClass, "Třída TYP_TRIDA by měla existovat");
        assertEquals("třída", tridaClass.getLabel("cs"), "Třída TYP_TRIDA by měla mít správný popisek");
        assertTrue(tridaClass.hasSuperClass(pojemClass), "Třída TYP_TRIDA by měla rozšiřovat TYP_POJEM");

        // Ověření, že třída 'datovyTyp' (TYP_DT) existuje a má správný český popisek
        OntClass datovyTyp = ontModel.getOntClass(NS + TYP_DT);
        assertNotNull(datovyTyp, "Třída TYP_DT by měla existovat");
        assertEquals(LABEL_DT, datovyTyp.getLabel("cs"), "Třída TYP_DT by měla mít správný popisek");

        // Ověření, že třída 'typSubjektu' (TYP_TSP) existuje a je podtřídou 'trida'
        OntClass typSubjektuClass = ontModel.getOntClass(NS + TYP_TSP);
        assertNotNull(typSubjektuClass, "Třída TYP_TSP by měla existovat");
        assertTrue(typSubjektuClass.hasSuperClass(tridaClass), "Třída TYP_TSP by měla rozšiřovat TYP_TRIDA");

        // Ověření, že třída 'typObjektu' (TYP_TOP) existuje a je podtřídou 'trida'
        OntClass typObjektuClass = ontModel.getOntClass(NS + TYP_TOP);
        assertNotNull(typObjektuClass, "Třída TOP by měla existovat");
        assertTrue(typObjektuClass.hasSuperClass(tridaClass), "Třída TYP_TOP by měla rozšiřovat TYP_TRIDA");

        // Ověření, že třída 'verejnyUdaj' (TYP_VEREJNY_UDAJ) existuje a má správný český popisek
        OntClass verejnyUdajClass = ontModel.getOntClass(NS + TYP_VEREJNY_UDAJ);
        assertNotNull(verejnyUdajClass, "Třída TYP_VEREJNY_UDAJ by měla existovat");
        assertEquals(LABEL_VU, verejnyUdajClass.getLabel("cs"), "Třída TYP_VEREJNY_UDAJ by měla mít správný popisek");

        // Ověření, že třída 'neverejnyUdaj' (TYP_NEVEREJNY_UDAJ) existuje a má správný český popisek
        OntClass neverejnyUdajClass = ontModel.getOntClass(NS + TYP_NEVEREJNY_UDAJ);
        assertNotNull(neverejnyUdajClass, "Třída TYP_NEVEREJNY_UDAJ by měla existovat");
        assertEquals(LABEL_NVU, neverejnyUdajClass.getLabel("cs"), "Třída TYP_NEVEREJNY_UDAJ by měla mít správný popisek");
    }

    // Test createBaseModel(): ověření existence a charakteristiky vlastností (label, domain, range)
    @Test
    void testCreateBaseModel_properties() {
        OntClass pojemClass = ontModel.getOntClass(NS + TYP_POJEM);
        OntClass neverejnyUdajClass = ontModel.getOntClass(NS + TYP_NEVEREJNY_UDAJ);

        // Ověření, že vlastnost 'nazev' existuje, má správný label, doménu TYP_POJEM a rozsah XSD:string
        OntProperty nazevProp = ontModel.getOntProperty(NS + LABEL_NAZEV);
        assertNotNull(nazevProp, "Vlastnost 'nazev' by měla existovat");
        assertEquals(LABEL_NAZEV, nazevProp.getLabel("cs"), "Vlastnost 'nazev' by měla mít český popisek");
        assertTrue(nazevProp.hasDomain(pojemClass), "Vlastnost 'nazev' by měla mít doménu TYP_POJEM");
        assertTrue(nazevProp.hasRange(ontModel.getOntClass(XSD + "string")), "Vlastnost 'nazev' by měla mít rozsah XSD:string");
        assertEquals(NS + LABEL_NAZEV, nazevProp.getURI(), "URI vlastnosti 'nazev' musí odpovídat NS+LABEL_NAZEV");

        // Ověření, že vlastnost 'popis' existuje, má správný label, doménu TYP_POJEM a rozsah XSD:string
        OntProperty popisProp = ontModel.getOntProperty(NS + LABEL_POPIS);
        assertNotNull(popisProp, "Vlastnost 'popis' by měla existovat");
        assertEquals(LABEL_POPIS, popisProp.getLabel("cs"), "Vlastnost 'popis' by měla mít český popisek");
        assertTrue(popisProp.hasDomain(pojemClass), "Vlastnost 'popis' by měla mít doménu TYP_POJEM");
        assertTrue(popisProp.hasRange(ontModel.getOntClass(XSD + "string")), "Vlastnost 'popis' by měla mít rozsah XSD:string");

        // Ověření, že vlastnost 'definice' existuje, má správný label, doménu TYP_POJEM a rozsah XSD:string
        OntProperty definiceProp = ontModel.getOntProperty(NS + LABEL_DEF);
        assertNotNull(definiceProp, "Vlastnost 'definice' by měla existovat");
        assertEquals(LABEL_DEF, definiceProp.getLabel("cs"), "Vlastnost 'definice' by měla mít český popisek");
        assertTrue(definiceProp.hasDomain(pojemClass), "Vlastnost 'definice' by měla mít doménu TYP_POJEM");
        assertTrue(definiceProp.hasRange(ontModel.getOntClass(XSD + "string")), "Vlastnost 'definice' by měla mít rozsah XSD:string");

        // Ověření, že vlastnost 'zdroj' existuje, má správný label, doménu TYP_POJEM a rozsah XSD:anyURI
        OntProperty zdrojProp = ontModel.getOntProperty(NS + LABEL_ZDROJ);
        assertNotNull(zdrojProp, "Vlastnost 'zdroj' by měla existovat");
        assertEquals(LABEL_ZDROJ, zdrojProp.getLabel("cs"), "Vlastnost 'zdroj' by měla mít český popisek");
        assertTrue(zdrojProp.hasDomain(pojemClass), "Vlastnost 'zdroj' by měla mít doménu TYP_POJEM");
        assertTrue(zdrojProp.hasRange(ontModel.getOntClass(XSD + "anyURI")), "Vlastnost 'zdroj' by měla mít rozsah XSD:anyURI");

        // Ověření, že vlastnost 'jeSdilenVPpdf' existuje, má správný label, doménu TYP_POJEM a rozsah XSD:boolean
        OntProperty jeSdilenVPpdfProp = ontModel.getOntProperty(NS + LABEL_JE_PPDF);
        assertNotNull(jeSdilenVPpdfProp, "Vlastnost 'jeSdilenVPpdf' by měla existovat");
        assertEquals(LABEL_JE_PPDF, jeSdilenVPpdfProp.getLabel("cs"), "Vlastnost 'jeSdilenvPpdf' by měla mít český popisek");
        assertTrue(jeSdilenVPpdfProp.hasDomain(pojemClass), "Vlastnost 'jeSdilenVPpdf' by měla mít doménu TYP_POJEM");
        assertTrue(jeSdilenVPpdfProp.hasRange(ontModel.getOntClass(XSD + "boolean")), "Vlastnost 'jeSdilenVPpdf' by měla mít rozsah XSD:boolean");

        // Ověření, že vlastnost 'agenda' existuje, má správný label, doménu TYP_POJEM a rozsah RDFS:Resource
        OntProperty agendaProp = ontModel.getOntProperty(NS + LABEL_AGENDA);
        assertNotNull(agendaProp, "Vlastnost 'agenda' by měla existovat");
        assertEquals(LABEL_AGENDA, agendaProp.getLabel("cs"), "Vlastnost 'agenda' by měla mít český popisek");
        assertTrue(agendaProp.hasDomain(pojemClass), "Vlastnost 'agenda' by měla mít doménu TYP_POJEM");
        assertTrue(agendaProp.hasRange(RDFS.Resource), "Vlastnost 'agenda' by měla mít rozsah RDFS:Resource");

        // Ověření, že vlastnost 'ais' existuje, má správný label, doménu TYP_POJEM a rozsah RDFS:Resource
        OntProperty aisProp = ontModel.getOntProperty(NS + LABEL_AIS);
        assertNotNull(aisProp, "Vlastnost 'ais' by měla existovat");
        assertEquals(LABEL_AIS, aisProp.getLabel("cs"), "Vlastnost 'ais' by měla mít český popisek");
        assertTrue(aisProp.hasDomain(pojemClass), "Vlastnost 'ais' by měla mít doménu TYP_POJEM");
        assertTrue(aisProp.hasRange(RDFS.Resource), "Vlastnost 'ais' by měla mít rozsah RDFS:Resource");

        // Ověření, že vlastnost 'ustanoveni' existuje, má správný label, doménu TYP_NEVEREJNY_UDAJ a rozsah RDFS:Resource
        OntProperty ustanoveniProp = ontModel.getOntProperty(NS + LABEL_UDN);
        assertNotNull(ustanoveniProp, "Vlastnost 'ustanoveni' by měla existovat");
        assertEquals(LABEL_UDN, ustanoveniProp.getLabel("cs"), "Vlastnost 'ustanoveni' by měla mít český popisek");
        assertTrue(ustanoveniProp.hasDomain(neverejnyUdajClass), "Vlastnost 'ustanoveni' by měla mít doménu TYP_NEVEREJNY_UDAJ");
        assertTrue(ustanoveniProp.hasRange(RDFS.Resource), "Vlastnost 'ustanoveni' by měla mít rozsah RDFS:Resource");

        // Ověření, že property 'definicniOborProp' existuje a má správný label, bez domény a rozsahu
        OntProperty definicniOborProp = ontModel.getOntProperty(NS + LABEL_DEF_O);
        assertNotNull(definicniOborProp, "Vlastnost 'definicniObor' by měla existovat");
        assertEquals(LABEL_DEF_O, definicniOborProp.getLabel("cs"), "Vlastnost 'definicniObor' by měla mít český popisek");

        // Ověření, že property 'oborHodnotProp' existuje a má správný label, bez domény a rozsahu
        OntProperty oborHodnotProp = ontModel.getOntProperty(NS + LABEL_OBOR_HODNOT);
        assertNotNull(oborHodnotProp, "Vlastnost 'oborHodnot' by měla existovat");
        assertEquals(LABEL_OBOR_HODNOT, oborHodnotProp.getLabel("cs"), "Vlastnost 'oborHodnot' by měla mít český popisek");

        // Ověření, že property 'nadrazenaTrida' existuje a má správný label, bez domény a rozsahu
        OntProperty nadrazenaTrida = ontModel.getOntProperty(NS + LABEL_NT);
        assertNotNull(nadrazenaTrida, "Vlastnost 'nadrazenaTrida' by měla existovat");
        assertEquals(LABEL_NT, nadrazenaTrida.getLabel("cs"), "Vlastnost 'nadrazenaTrida' by měla mít český popisek");

        // Ověření, že property 'souvisejiciUstanoveni' existuje a má správný label, bez domény a rozsahu
        OntProperty souvisejiciUstanoveni = ontModel.getOntProperty(NS + LABEL_SUPP);
        assertNotNull(souvisejiciUstanoveni, "Vlastnost 'souvisejiciUstanoveni' by měla existovat");
        assertEquals(LABEL_SUPP, souvisejiciUstanoveni.getLabel("cs"), "Vlastnost 'souvisejiciUstanoveni' by měla mít český popisek");

        // Ověření, že vlastnost 'zpusobSdileni' existuje, má správný label, doménu TYP_POJEM a rozsah RDFS:Resource
        OntProperty zpusobSdileniProp = ontModel.getOntProperty(NS + LABEL_ZPUSOB_SDILENI);
        assertNotNull(zpusobSdileniProp, "Vlastnost 'zpusobSdileni' by měla existovat");
        assertEquals("Způsob sdílení údaje", zpusobSdileniProp.getLabel("cs"), "Vlastnost 'zpusobSdileni' by měla mít český popisek");
        assertTrue(zpusobSdileniProp.hasDomain(pojemClass), "Vlastnost 'zpusobSdileni' by měla mít doménu TYP_POJEM");
        assertTrue(zpusobSdileniProp.hasRange(RDFS.Resource), "Vlastnost 'zpusobSdileni' by měla mít rozsah RDFS:Resource");

        // Ověření, že property 'typObsahuProp' existuje a má správný label, bez domény a rozsahu
        OntProperty zpusobZiskaniProp = ontModel.getOntProperty(NS + LABEL_ZPUSOB_ZISKANI);
        assertNotNull(zpusobZiskaniProp, "Vlastnost 'zpusobZiskani' by měla existovat");
        assertEquals("Způsob získání údaje", zpusobZiskaniProp.getLabel("cs"), "Vlastnost 'zpusobZiskani' by měla mít český popisek");
        assertTrue(zpusobZiskaniProp.hasDomain(pojemClass), "Vlastnost 'zpusobZiskani' by měla mít doménu TYP_POJEM");
        assertTrue(zpusobZiskaniProp.hasRange(RDFS.Resource), "Vlastnost 'zpusobZiskani' by měla mít rozsah RDFS:Resource");

        // Ověření, že property 'typObsahuProp' existuje a má správný label, bez domény a rozsahu
        OntProperty typObsahuProp = ontModel.getOntProperty(NS + LABEL_TYP_OBSAHU);
        assertNotNull(typObsahuProp, "Vlastnost 'typObsahu' by měla existovat");
        assertEquals("Typ obsahu údaje", typObsahuProp.getLabel("cs"), "Vlastnost 'typObsahu' by měla mít český popisek");
        assertTrue(typObsahuProp.hasDomain(pojemClass), "Vlastnost 'typObsahu' by měla mít doménu TYP_POJEM");
        assertTrue(typObsahuProp.hasRange(RDFS.Resource), "Vlastnost 'typObsahu' by měla mít rozsah RDFS:Resource");

        // Negative cases pro neexistující resources
        assertNull(ontModel.getOntProperty(NS + "neexistujiciVlastnost"), "Neexistující vlastnost by měla vracel null");
        assertNull(ontModel.getOntClass(NS + "NeexistujiciTrida"), "Neexistující třída by měla vracet null");
    }

    @Test
    void testCreateBaseModel_Isolated() throws Exception {
        // Create an instance of OFNBaseModel but we'll manually invoke createBaseModel
        OFNBaseModel model = new OFNBaseModel();

        // Get a blank OntModel to replace the one created and populated in the constructor
        OntModel freshOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        // Use reflection to access the private ontModel field and set it to our fresh model
        java.lang.reflect.Field ontModelField = OFNBaseModel.class.getDeclaredField("ontModel");
        ontModelField.setAccessible(true);
        ontModelField.set(model, freshOntModel);

        // Use reflection to access the private createBaseModel method
        Method createBaseModelMethod = OFNBaseModel.class.getDeclaredMethod("createBaseModel");
        createBaseModelMethod.setAccessible(true);

        // Assert that model is empty before calling createBaseModel
        assertEquals(0, freshOntModel.listClasses().toList().size(),
                "OntModel should be empty before calling createBaseModel");

        // Invoke the createBaseModel method
        createBaseModelMethod.invoke(model);

        // Verify that the model was populated correctly
        assertNotNull(freshOntModel.getOntClass(XSD + "string"),
                "XSD:string should be created by createBaseModel");

        // Verify one class to prove the method executed successfully
        OntClass pojemClass = freshOntModel.getOntClass(NS + TYP_POJEM);
        assertNotNull(pojemClass, "Class TYP_POJEM should be created by createBaseModel");
        assertEquals("pojem", pojemClass.getLabel("cs"),
                "Class TYP_POJEM should have the correct Czech label");
    }
}


