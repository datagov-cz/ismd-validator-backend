package com.dia.models;

import lombok.Getter;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import static com.dia.constants.ArchiConstants.*;

@Getter
public class OFNBaseModel {

    private final OntModel ontModel;

    public OFNBaseModel() {
        ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        ontModel.setNsPrefix("cz", DEFAULT_NS);
        ontModel.setNsPrefix("rdf", RDF.getURI());
        ontModel.setNsPrefix("rdfs", RDFS.getURI());
        ontModel.setNsPrefix("owl", OWL2.getURI());
        ontModel.setNsPrefix("xsd", XSD);

        createBaseModel();
    }

    private void createBaseModel() {
        OntClass xsdString = ontModel.createClass(XSD + "string");
        OntClass xsdBoolean = ontModel.createClass(XSD + "boolean");
        OntClass xsdAnyURI = ontModel.createClass(XSD + "anyURI");

        OntClass pojemClass = ontModel.createClass(DEFAULT_NS + POJEM);
        pojemClass.addLabel(POJEM, "cs");

        OntClass vlastnostClass = ontModel.createClass(DEFAULT_NS + VLASTNOST);
        vlastnostClass.addLabel(VLASTNOST, "cs");
        vlastnostClass.addSuperClass(pojemClass);

        OntClass vztahClass = ontModel.createClass(DEFAULT_NS + VZTAH);
        vztahClass.addLabel(VZTAH, "cs");
        vztahClass.addSuperClass(pojemClass);

        OntClass tridaClass = ontModel.createClass(DEFAULT_NS + TRIDA);
        tridaClass.addLabel(TRIDA, "cs");
        tridaClass.addSuperClass(pojemClass);

        OntClass datovyTyp = ontModel.createClass(DEFAULT_NS + DATOVY_TYP);
        datovyTyp.addLabel(DATOVY_TYP, "cs");

        OntClass typSubjektuClass = ontModel.createClass(DEFAULT_NS + TSP);
        typSubjektuClass.addLabel(TSP, "cs");
        typSubjektuClass.addSuperClass(tridaClass);

        OntClass typObjektuClass = ontModel.createClass(DEFAULT_NS + TOP);
        typObjektuClass.addLabel(TOP, "cs");
        typObjektuClass.addSuperClass(tridaClass);

        OntClass verejnyUdajClass = ontModel.createClass(DEFAULT_NS + VEREJNY_UDAJ);
        verejnyUdajClass.addLabel(VEREJNY_UDAJ, "cs");

        OntClass neverejnyUdajClass = ontModel.createClass(DEFAULT_NS + NEVEREJNY_UDAJ);
        neverejnyUdajClass.addLabel(NEVEREJNY_UDAJ, "cs");

        OntProperty nazevProp = ontModel.createOntProperty(DEFAULT_NS + NAZEV);
        nazevProp.addLabel(NAZEV, "cs");
        nazevProp.addDomain(pojemClass);
        nazevProp.addRange(xsdString);

        OntProperty popisProp = ontModel.createOntProperty(DEFAULT_NS + POPIS);
        popisProp.addLabel(POPIS, "cs");
        popisProp.addDomain(pojemClass);
        popisProp.addRange(xsdString);

        OntProperty definiceProp = ontModel.createOntProperty(DEFAULT_NS + DEFINICE);
        definiceProp.addLabel(DEFINICE, "cs");
        definiceProp.addDomain(pojemClass);
        definiceProp.addRange(xsdString);

        OntProperty zdrojProp = ontModel.createOntProperty(DEFAULT_NS + ZDROJ);
        zdrojProp.addLabel(ZDROJ, "cs");
        zdrojProp.addDomain(pojemClass);
        zdrojProp.addRange(xsdAnyURI);

        OntProperty jeSdilenVPpdfProp = ontModel.createOntProperty(DEFAULT_NS + JE_PPDF);
        jeSdilenVPpdfProp.addLabel(JE_PPDF, "cs");
        jeSdilenVPpdfProp.addDomain(pojemClass);
        jeSdilenVPpdfProp.addRange(xsdBoolean);

        OntProperty agendaProp = ontModel.createOntProperty(DEFAULT_NS + AGENDA);
        agendaProp.addLabel(AGENDA, "cs");
        agendaProp.addDomain(pojemClass);
        agendaProp.addRange(RDFS.Resource);

        OntProperty aisProp = ontModel.createOntProperty(DEFAULT_NS + AIS);
        aisProp.addLabel(AIS, "cs");
        aisProp.addDomain(pojemClass);
        aisProp.addRange(RDFS.Resource);

        OntProperty ustanoveniProp = ontModel.createOntProperty(DEFAULT_NS + USTANOVENI_NEVEREJNOST);
        ustanoveniProp.addLabel(USTANOVENI_NEVEREJNOST, "cs");
        ustanoveniProp.addDomain(neverejnyUdajClass);
        ustanoveniProp.addRange(RDFS.Resource);

        OntProperty definicniOborProp = ontModel.createOntProperty(DEFAULT_NS + DEFINICNI_OBOR);
        definicniOborProp.addLabel(DEFINICNI_OBOR, "cs");

        OntProperty oborHodnotProp = ontModel.createOntProperty(DEFAULT_NS + OBOR_HODNOT);
        oborHodnotProp.addLabel(OBOR_HODNOT, "cs");

        OntProperty nadrazenaTrida = ontModel.createOntProperty(DEFAULT_NS + NADRAZENA_TRIDA);
        nadrazenaTrida.addLabel(NADRAZENA_TRIDA, "cs");

        OntProperty souvisejiciUstanoveni = ontModel.createOntProperty(DEFAULT_NS + SUPP);
        souvisejiciUstanoveni.addLabel(SUPP, "cs");

        OntProperty zpusobSdileniProp = ontModel.createOntProperty(DEFAULT_NS + ZPUSOB_SDILENI);
        zpusobSdileniProp.addLabel(ZPUSOB_SDILENI, "cs");
        zpusobSdileniProp.addDomain(pojemClass);
        zpusobSdileniProp.addRange(RDFS.Resource);

        OntProperty zpusobZiskaniProp = ontModel.createOntProperty(DEFAULT_NS + ZPUSOB_ZISKANI);
        zpusobZiskaniProp.addLabel(ZPUSOB_ZISKANI, "cs");
        zpusobZiskaniProp.addDomain(pojemClass);
        zpusobZiskaniProp.addRange(RDFS.Resource);

        OntProperty typObsahuProp = ontModel.createOntProperty(DEFAULT_NS + TYP_OBSAHU);
        typObsahuProp.addLabel(TYP_OBSAHU, "cs");
        typObsahuProp.addDomain(pojemClass);
        typObsahuProp.addRange(RDFS.Resource);
    }
}
