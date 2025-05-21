package dia.ismd.common.models;

import lombok.Getter;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import static dia.ismd.validator.constants.ArchiOntologyConstants.*;

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

        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);
        pojemClass.addLabel(LABEL_POJEM, "cs");

        OntClass vlastnostClass = ontModel.createClass(NS + TYP_VLASTNOST);
        vlastnostClass.addLabel(LABEL_VLASTNOST, "cs");
        vlastnostClass.addSuperClass(pojemClass);

        OntClass vztahClass = ontModel.createClass(NS + TYP_VZTAH);
        vztahClass.addLabel(LABEL_VZTAH, "cs");
        vztahClass.addSuperClass(pojemClass);

        OntClass tridaClass = ontModel.createClass(NS + TYP_TRIDA);
        tridaClass.addLabel(LABEL_TRIDA, "cs");
        tridaClass.addSuperClass(pojemClass);

        OntClass datovyTyp = ontModel.createClass(NS + TYP_DT);
        datovyTyp.addLabel(LABEL_DT, "cs");

        OntClass typSubjektuClass = ontModel.createClass(NS + TYP_TSP);
        typSubjektuClass.addLabel(LABEL_TSP, "cs");
        typSubjektuClass.addSuperClass(tridaClass);

        OntClass typObjektuClass = ontModel.createClass(NS + TYP_TOP);
        typObjektuClass.addLabel(LABEL_TOP, "cs");
        typObjektuClass.addSuperClass(tridaClass);

        OntClass verejnyUdajClass = ontModel.createClass(NS + TYP_VEREJNY_UDAJ);
        verejnyUdajClass.addLabel(LABEL_VU, "cs");

        OntClass neverejnyUdajClass = ontModel.createClass(NS + TYP_NEVEREJNY_UDAJ);
        neverejnyUdajClass.addLabel(LABEL_NVU, "cs");

        OntProperty nazevProp = ontModel.createOntProperty(NS + LABEL_NAZEV);
        nazevProp.addLabel(LABEL_NAZEV, "cs");
        nazevProp.addDomain(pojemClass);
        nazevProp.addRange(xsdString);

        OntProperty popisProp = ontModel.createOntProperty(NS + LABEL_POPIS);
        popisProp.addLabel(LABEL_POPIS, "cs");
        popisProp.addDomain(pojemClass);
        popisProp.addRange(xsdString);

        OntProperty definiceProp = ontModel.createOntProperty(NS + LABEL_DEF);
        definiceProp.addLabel(LABEL_DEF, "cs");
        definiceProp.addDomain(pojemClass);
        definiceProp.addRange(xsdString);

        OntProperty zdrojProp = ontModel.createOntProperty(NS + LABEL_ZDROJ);
        zdrojProp.addLabel(LABEL_ZDROJ, "cs");
        zdrojProp.addDomain(pojemClass);
        zdrojProp.addRange(xsdAnyURI);

        OntProperty jeSdilenVPpdfProp = ontModel.createOntProperty(NS + LABEL_JE_PPDF);
        jeSdilenVPpdfProp.addLabel(LABEL_JE_PPDF, "cs");
        jeSdilenVPpdfProp.addDomain(pojemClass);
        jeSdilenVPpdfProp.addRange(xsdBoolean);

        OntProperty agendaProp = ontModel.createOntProperty(NS + LABEL_AGENDA);
        agendaProp.addLabel(LABEL_AGENDA, "cs");
        agendaProp.addDomain(pojemClass);
        agendaProp.addRange(RDFS.Resource);

        OntProperty aisProp = ontModel.createOntProperty(NS + LABEL_AIS);
        aisProp.addLabel(LABEL_AIS, "cs");
        aisProp.addDomain(pojemClass);
        aisProp.addRange(RDFS.Resource);

        OntProperty ustanoveniProp = ontModel.createOntProperty(NS + LABEL_UDN);
        ustanoveniProp.addLabel(LABEL_UDN, "cs");
        ustanoveniProp.addDomain(neverejnyUdajClass);
        ustanoveniProp.addRange(RDFS.Resource);

        OntProperty definicniOborProp = ontModel.createOntProperty(NS + LABEL_DEF_O);
        definicniOborProp.addLabel(LABEL_DEF_O, "cs");

        OntProperty oborHodnotProp = ontModel.createOntProperty(NS + LABEL_OBOR_HODNOT);
        oborHodnotProp.addLabel(LABEL_OBOR_HODNOT, "cs");

        OntProperty nadrazenaTrida = ontModel.createOntProperty(NS + LABEL_NT);
        nadrazenaTrida.addLabel(LABEL_NT, "cs");

        OntProperty souvisejiciUstanoveni = ontModel.createOntProperty(NS + LABEL_SUPP);
        souvisejiciUstanoveni.addLabel(LABEL_SUPP, "cs");

        OntProperty zpusobSdileniProp = ontModel.createOntProperty(NS + LABEL_ZPUSOB_SDILENI);
        zpusobSdileniProp.addLabel("Způsob sdílení údaje", "cs");
        zpusobSdileniProp.addDomain(pojemClass);
        zpusobSdileniProp.addRange(RDFS.Resource);

        OntProperty zpusobZiskaniProp = ontModel.createOntProperty(NS + LABEL_ZPUSOB_ZISKANI);
        zpusobZiskaniProp.addLabel("Způsob získání údaje", "cs");
        zpusobZiskaniProp.addDomain(pojemClass);
        zpusobZiskaniProp.addRange(RDFS.Resource);

        OntProperty typObsahuProp = ontModel.createOntProperty(NS + LABEL_TYP_OBSAHU);
        typObsahuProp.addLabel("Typ obsahu údaje", "cs");
        typObsahuProp.addDomain(pojemClass);
        typObsahuProp.addRange(RDFS.Resource);
    }
}
