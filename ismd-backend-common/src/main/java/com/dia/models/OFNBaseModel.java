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

import static com.dia.constants.OntologyConstants.*;

@Getter
public class OFNBaseModel {

    private final OntModel ontModel;

    public OFNBaseModel() {
        ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        ontModel.setNsPrefix("cz", NS);
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

        OntClass pojemClass = ontModel.createClass(NS + POJEM);
        pojemClass.addLabel(POJEM, "cs");

        OntClass vlastnostClass = ontModel.createClass(NS + VLASTNOST);
        vlastnostClass.addLabel(VLASTNOST, "cs");
        vlastnostClass.addSuperClass(pojemClass);

        OntClass vztahClass = ontModel.createClass(NS + VZTAH);
        vztahClass.addLabel(VZTAH, "cs");
        vztahClass.addSuperClass(pojemClass);

        OntClass tridaClass = ontModel.createClass(NS + TRIDA);
        tridaClass.addLabel(TRIDA, "cs");
        tridaClass.addSuperClass(pojemClass);

        OntClass datovyTyp = ontModel.createClass(NS + DATOVY_TYP);
        datovyTyp.addLabel(DATOVY_TYP, "cs");

        OntClass typSubjektuClass = ontModel.createClass(NS + TSP);
        typSubjektuClass.addLabel(TSP, "cs");
        typSubjektuClass.addSuperClass(tridaClass);

        OntClass typObjektuClass = ontModel.createClass(NS + TOP);
        typObjektuClass.addLabel(TOP, "cs");
        typObjektuClass.addSuperClass(tridaClass);

        OntClass verejnyUdajClass = ontModel.createClass(NS + VEREJNY_UDAJ);
        verejnyUdajClass.addLabel(VEREJNY_UDAJ, "cs");

        OntClass neverejnyUdajClass = ontModel.createClass(NS + NEVEREJNY_UDAJ);
        neverejnyUdajClass.addLabel(NEVEREJNY_UDAJ, "cs");

        OntProperty nazevProp = ontModel.createOntProperty(NS + NAZEV);
        nazevProp.addLabel(NAZEV, "cs");
        nazevProp.addDomain(pojemClass);
        nazevProp.addRange(xsdString);

        OntProperty popisProp = ontModel.createOntProperty(NS + POPIS);
        popisProp.addLabel(POPIS, "cs");
        popisProp.addDomain(pojemClass);
        popisProp.addRange(xsdString);

        OntProperty definiceProp = ontModel.createOntProperty(NS + DEFINICE);
        definiceProp.addLabel(DEFINICE, "cs");
        definiceProp.addDomain(pojemClass);
        definiceProp.addRange(xsdString);

        OntProperty zdrojProp = ontModel.createOntProperty(NS + ZDROJ);
        zdrojProp.addLabel(ZDROJ, "cs");
        zdrojProp.addDomain(pojemClass);
        zdrojProp.addRange(xsdAnyURI);

        OntProperty jeSdilenVPpdfProp = ontModel.createOntProperty(NS + JE_PPDF);
        jeSdilenVPpdfProp.addLabel(JE_PPDF, "cs");
        jeSdilenVPpdfProp.addDomain(pojemClass);
        jeSdilenVPpdfProp.addRange(xsdBoolean);

        OntProperty agendaProp = ontModel.createOntProperty(NS + AGENDA);
        agendaProp.addLabel(AGENDA, "cs");
        agendaProp.addDomain(pojemClass);
        agendaProp.addRange(RDFS.Resource);

        OntProperty aisProp = ontModel.createOntProperty(NS + AIS);
        aisProp.addLabel(AIS, "cs");
        aisProp.addDomain(pojemClass);
        aisProp.addRange(RDFS.Resource);

        OntProperty ustanoveniProp = ontModel.createOntProperty(NS + USTANOVENI_NEVEREJNOST);
        ustanoveniProp.addLabel(USTANOVENI_NEVEREJNOST, "cs");
        ustanoveniProp.addDomain(neverejnyUdajClass);
        ustanoveniProp.addRange(RDFS.Resource);

        OntProperty definicniOborProp = ontModel.createOntProperty(NS + DEFINICNI_OBOR);
        definicniOborProp.addLabel(DEFINICNI_OBOR, "cs");

        OntProperty oborHodnotProp = ontModel.createOntProperty(NS + OBOR_HODNOT);
        oborHodnotProp.addLabel(OBOR_HODNOT, "cs");

        OntProperty nadrazenaTrida = ontModel.createOntProperty(NS + NADRAZENA_TRIDA);
        nadrazenaTrida.addLabel(NADRAZENA_TRIDA, "cs");

        OntProperty souvisejiciUstanoveni = ontModel.createOntProperty(NS + SUPP);
        souvisejiciUstanoveni.addLabel(SUPP, "cs");

        OntProperty zpusobSdileniProp = ontModel.createOntProperty(NS + ZPUSOB_SDILENI);
        zpusobSdileniProp.addLabel(ZPUSOB_SDILENI, "cs");
        zpusobSdileniProp.addDomain(pojemClass);
        zpusobSdileniProp.addRange(RDFS.Resource);

        OntProperty zpusobZiskaniProp = ontModel.createOntProperty(NS + ZPUSOB_ZISKANI);
        zpusobZiskaniProp.addLabel(ZPUSOB_ZISKANI, "cs");
        zpusobZiskaniProp.addDomain(pojemClass);
        zpusobZiskaniProp.addRange(RDFS.Resource);

        OntProperty typObsahuProp = ontModel.createOntProperty(NS + TYP_OBSAHU);
        typObsahuProp.addLabel(TYP_OBSAHU, "cs");
        typObsahuProp.addDomain(pojemClass);
        typObsahuProp.addRange(RDFS.Resource);
    }
}
