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

import static dia.ismd.validator.constants.ArchiOntologyConstants.LABEL_SUPP;

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
        OntClass pojemClass = ontModel.createClass(NS + TYP_POJEM);
        pojemClass.addLabel(LABEL_POJEM, "cs");
        pojemClass.addLabel(LABEL_POJEM, "en");

        OntClass vlastnostClass = ontModel.createClass(NS + TYP_VLASTNOST);
        vlastnostClass.addLabel(LABEL_VLASTNOST, "cs");
        vlastnostClass.addLabel(LABEL_VLASTNOST, "en");
        vlastnostClass.addSuperClass(pojemClass);

        OntClass vztahClass = ontModel.createClass(NS + TYP_VZTAH);
        vztahClass.addLabel(LABEL_VZTAH, "cs");
        vztahClass.addLabel(LABEL_VZTAH, "en");
        vztahClass.addSuperClass(pojemClass);

        OntClass tridaClass = ontModel.createClass(NS + TYP_TRIDA);
        tridaClass.addLabel(LABEL_TRIDA, "cs");
        tridaClass.addLabel(LABEL_TRIDA, "en");
        tridaClass.addSuperClass(pojemClass);

        OntClass datovyTyp = ontModel.createClass(NS + TYP_DT);
        datovyTyp.addLabel(LABEL_DT, "cs");
        datovyTyp.addLabel(LABEL_DT, "en");

        OntClass typSubjektuClass = ontModel.createClass(NS + TYP_TSP);
        typSubjektuClass.addLabel(LABEL_TSP, "cs");
        typSubjektuClass.addLabel(LABEL_TSP, "en");
        typSubjektuClass.addSuperClass(tridaClass);

        OntClass typObjektuClass = ontModel.createClass(NS + TYP_TOP);
        typObjektuClass.addLabel(LABEL_TOP, "cs");
        typObjektuClass.addLabel(LABEL_TOP, "en");
        typObjektuClass.addSuperClass(tridaClass);

        OntClass verejnyUdajClass = ontModel.createClass(NS + TYP_VEREJNY_UDAJ);
        verejnyUdajClass.addLabel(LABEL_VU, "cs");
        verejnyUdajClass.addLabel(LABEL_VU, "en");

        OntClass neverejnyUdajClass = ontModel.createClass(NS + TYP_NEVEREJNY_UDAJ);
        neverejnyUdajClass.addLabel(LABEL_NVU, "cs");
        neverejnyUdajClass.addLabel(LABEL_NVU, "en");

        OntProperty nazevProp = ontModel.createOntProperty(NS + LABEL_NAZEV);
        nazevProp.addLabel(LABEL_NAZEV, "cs");
        nazevProp.addLabel(LABEL_NAZEV, "en");
        nazevProp.addDomain(pojemClass);
        nazevProp.addRange(ontModel.createClass(XSD));

        OntProperty popisProp = ontModel.createOntProperty(NS + LABEL_POPIS);
        popisProp.addLabel(LABEL_POPIS, "cs");
        popisProp.addLabel(LABEL_POPIS, "en");
        popisProp.addDomain(pojemClass);
        popisProp.addRange(ontModel.createClass("http://www.w3.org/2001/XMLSchema#string"));

        OntProperty definiceProp = ontModel.createOntProperty(NS + LABEL_DEF);
        definiceProp.addLabel(LABEL_DEF, "cs");
        definiceProp.addLabel(LABEL_DEF, "en");
        definiceProp.addDomain(pojemClass);
        definiceProp.addRange(ontModel.createClass("http://www.w3.org/2001/XMLSchema#string"));

        OntProperty zdrojProp = ontModel.createOntProperty(NS + LABEL_ZDROJ);
        zdrojProp.addLabel(LABEL_ZDROJ, "cs");
        zdrojProp.addLabel(LABEL_ZDROJ, "en");
        zdrojProp.addDomain(pojemClass);
        zdrojProp.addRange(ontModel.createClass("http://www.w3.org/2001/XMLSchema#anyURI"));

        OntProperty jeSdilenVPpdfProp = ontModel.createOntProperty(NS + LABEL_JE_PPDF);
        jeSdilenVPpdfProp.addLabel(LABEL_JE_PPDF, "cs");
        jeSdilenVPpdfProp.addLabel(LABEL_JE_PPDF, "en");
        jeSdilenVPpdfProp.addDomain(pojemClass);
        jeSdilenVPpdfProp.addRange(ontModel.createClass("http://www.w3.org/2001/XMLSchema#boolean"));

        OntProperty agendaProp = ontModel.createOntProperty(NS + LABEL_AGENDA);
        agendaProp.addLabel(LABEL_AGENDA, "cs");
        agendaProp.addLabel(LABEL_AGENDA, "en");
        agendaProp.addDomain(pojemClass);
        agendaProp.addRange(RDFS.Resource);

        OntProperty aisProp = ontModel.createOntProperty(NS + LABEL_AIS);
        aisProp.addLabel(LABEL_AIS, "cs");
        aisProp.addLabel(LABEL_AIS, "en");
        aisProp.addDomain(pojemClass);
        aisProp.addRange(RDFS.Resource);

        OntProperty ustanoveniProp = ontModel.createOntProperty(NS + LABEL_UDN);
        ustanoveniProp.addLabel(LABEL_UDN, "cs");
        ustanoveniProp.addLabel(LABEL_UDN, "en");
        ustanoveniProp.addDomain(neverejnyUdajClass);
        ustanoveniProp.addRange(RDFS.Resource);

        OntProperty definicniOborProp = ontModel.createOntProperty(NS + LABEL_DEF_O);
        definicniOborProp.addLabel(LABEL_DEF_O, "cs");
        definicniOborProp.addLabel(LABEL_DEF_O, "en");

        OntProperty oborHodnotProp = ontModel.createOntProperty(NS + LABEL_OBOR_HODNOT);
        oborHodnotProp.addLabel(LABEL_OBOR_HODNOT, "cs");
        oborHodnotProp.addLabel(LABEL_OBOR_HODNOT, "en");

        OntProperty nadrazenaTrida = ontModel.createOntProperty(NS + LABEL_NT);
        nadrazenaTrida.addLabel(LABEL_NT, "cs");
        nadrazenaTrida.addLabel(LABEL_NT, "en");

        OntProperty souvisejiciUstanoveni = ontModel.createOntProperty(NS + LABEL_SUPP);
        souvisejiciUstanoveni.addLabel(LABEL_SUPP, "cs");
        souvisejiciUstanoveni.addLabel(LABEL_SUPP, "en");
    }
}
