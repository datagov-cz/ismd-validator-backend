package com.dia.models;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.Set;

import static com.dia.constants.ArchiConstants.*;

@Getter
@Slf4j
public class OFNBaseModel {

    private final OntModel ontModel;

    public OFNBaseModel() {
        this(Set.of(POJEM), Set.of());
    }

    public OFNBaseModel(Set<String> requiredBaseClasses, Set<String> requiredProperties) {
        ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        ontModel.setNsPrefix("cz", DEFAULT_NS);
        ontModel.setNsPrefix("rdf", RDF.getURI());
        ontModel.setNsPrefix("rdfs", RDFS.getURI());
        ontModel.setNsPrefix("owl", OWL2.getURI());
        ontModel.setNsPrefix("xsd", XSD);

        createDynamicBaseModel(requiredBaseClasses, requiredProperties);
    }

    private void createDynamicBaseModel(Set<String> requiredBaseClasses, Set<String> requiredProperties) {
        if (requiresXSDTypes(requiredProperties)) {
            ontModel.createClass(XSD + "string");
            ontModel.createClass(XSD + "boolean");
            ontModel.createClass(XSD + "anyURI");
        }

        OntClass pojemClass = null;
        if (requiredBaseClasses.contains(POJEM)) {
            pojemClass = ontModel.createClass(DEFAULT_NS + POJEM);
            pojemClass.addLabel(POJEM, "cs");
        }

        OntClass tridaClass = null;
        if (requiredBaseClasses.contains(TRIDA) && pojemClass != null) {
            tridaClass = ontModel.createClass(DEFAULT_NS + TRIDA);
            tridaClass.addLabel(TRIDA, "cs");
            tridaClass.addSuperClass(pojemClass);
        }

        if (requiredBaseClasses.contains(TSP) && tridaClass != null) {
            OntClass typSubjektuClass = ontModel.createClass(DEFAULT_NS + TSP);
            typSubjektuClass.addLabel(TSP, "cs");
            typSubjektuClass.addSuperClass(tridaClass);
        }

        if (requiredBaseClasses.contains(TOP) && tridaClass != null) {
            OntClass typObjektuClass = ontModel.createClass(DEFAULT_NS + TOP);
            typObjektuClass.addLabel(TOP, "cs");
            typObjektuClass.addSuperClass(tridaClass);
        }

        if (requiredBaseClasses.contains(VLASTNOST) && pojemClass != null) {
            OntClass vlastnostClass = ontModel.createClass(DEFAULT_NS + VLASTNOST);
            vlastnostClass.addLabel(VLASTNOST, "cs");
            vlastnostClass.addSuperClass(pojemClass);
        }

        if (requiredBaseClasses.contains(VZTAH) && pojemClass != null) {
            OntClass vztahClass = ontModel.createClass(DEFAULT_NS + VZTAH);
            vztahClass.addLabel(VZTAH, "cs");
            vztahClass.addSuperClass(pojemClass);
        }

        if (requiredBaseClasses.contains(DATOVY_TYP)) {
            OntClass datovyTyp = ontModel.createClass(DEFAULT_NS + DATOVY_TYP);
            datovyTyp.addLabel(DATOVY_TYP, "cs");
        }

        if (requiredBaseClasses.contains(VEREJNY_UDAJ)) {
            OntClass verejnyUdajClass = ontModel.createClass(DEFAULT_NS + VEREJNY_UDAJ);
            verejnyUdajClass.addLabel(VEREJNY_UDAJ, "cs");
        }

        if (requiredBaseClasses.contains(NEVEREJNY_UDAJ)) {
            OntClass neverejnyUdajClass = ontModel.createClass(DEFAULT_NS + NEVEREJNY_UDAJ);
            neverejnyUdajClass.addLabel(NEVEREJNY_UDAJ, "cs");
        }

        createRequiredProperties(requiredProperties, pojemClass,
                requiredBaseClasses.contains(NEVEREJNY_UDAJ) ?
                        ontModel.createClass(DEFAULT_NS + NEVEREJNY_UDAJ) : null);
    }

    private void createRequiredProperties(Set<String> requiredProperties, OntClass pojemClass, OntClass neverejnyUdajClass) {
        OntClass xsdString = requiresXSDTypes(requiredProperties) ? ontModel.createClass(XSD + "string") : null;
        OntClass xsdBoolean = requiresXSDTypes(requiredProperties) ? ontModel.createClass(XSD + "boolean") : null;
        OntClass xsdAnyURI = requiresXSDTypes(requiredProperties) ? ontModel.createClass(XSD + "anyURI") : null;

        for (String propertyName : requiredProperties) {
            switch (propertyName) {
                case NAZEV:
                    if (pojemClass != null && xsdString != null) {
                        OntProperty nazevProp = ontModel.createOntProperty(DEFAULT_NS + NAZEV);
                        nazevProp.addLabel(NAZEV, "cs");
                        nazevProp.addDomain(pojemClass);
                        nazevProp.addRange(xsdString);
                    }
                    break;

                case POPIS:
                    if (pojemClass != null && xsdString != null) {
                        OntProperty popisProp = ontModel.createOntProperty(DEFAULT_NS + POPIS);
                        popisProp.addLabel(POPIS, "cs");
                        popisProp.addDomain(pojemClass);
                        popisProp.addRange(xsdString);
                    }
                    break;

                case DEFINICE:
                    if (pojemClass != null && xsdString != null) {
                        OntProperty definiceProp = ontModel.createOntProperty(DEFAULT_NS + DEFINICE);
                        definiceProp.addLabel(DEFINICE, "cs");
                        definiceProp.addDomain(pojemClass);
                        definiceProp.addRange(xsdString);
                    }
                    break;

                case ZDROJ:
                    if (pojemClass != null && xsdAnyURI != null) {
                        OntProperty zdrojProp = ontModel.createOntProperty(DEFAULT_NS + ZDROJ);
                        zdrojProp.addLabel(ZDROJ, "cs");
                        zdrojProp.addDomain(pojemClass);
                        zdrojProp.addRange(xsdAnyURI);
                    }
                    break;

                case JE_PPDF:
                    if (pojemClass != null && xsdBoolean != null) {
                        OntProperty jeSdilenVPpdfProp = ontModel.createOntProperty(DEFAULT_NS + JE_PPDF);
                        jeSdilenVPpdfProp.addLabel(JE_PPDF, "cs");
                        jeSdilenVPpdfProp.addDomain(pojemClass);
                        jeSdilenVPpdfProp.addRange(xsdBoolean);
                    }
                    break;

                case AGENDA:
                    if (pojemClass != null) {
                        OntProperty agendaProp = ontModel.createOntProperty(DEFAULT_NS + AGENDA);
                        agendaProp.addLabel(AGENDA, "cs");
                        agendaProp.addDomain(pojemClass);
                        agendaProp.addRange(RDFS.Resource);
                    }
                    break;

                case AIS:
                    if (pojemClass != null) {
                        OntProperty aisProp = ontModel.createOntProperty(DEFAULT_NS + AIS);
                        aisProp.addLabel(AIS, "cs");
                        aisProp.addDomain(pojemClass);
                        aisProp.addRange(RDFS.Resource);
                    }
                    break;

                case USTANOVENI_NEVEREJNOST:
                    if (neverejnyUdajClass != null) {
                        OntProperty ustanoveniProp = ontModel.createOntProperty(DEFAULT_NS + USTANOVENI_NEVEREJNOST);
                        ustanoveniProp.addLabel(USTANOVENI_NEVEREJNOST, "cs");
                        ustanoveniProp.addDomain(neverejnyUdajClass);
                        ustanoveniProp.addRange(RDFS.Resource);
                    }
                    break;

                case DEFINICNI_OBOR:
                    OntProperty definicniOborProp = ontModel.createOntProperty(DEFAULT_NS + DEFINICNI_OBOR);
                    definicniOborProp.addLabel(DEFINICNI_OBOR, "cs");
                    break;

                case OBOR_HODNOT:
                    OntProperty oborHodnotProp = ontModel.createOntProperty(DEFAULT_NS + OBOR_HODNOT);
                    oborHodnotProp.addLabel(OBOR_HODNOT, "cs");
                    break;

                case NADRAZENA_TRIDA:
                    OntProperty nadrazenaTrida = ontModel.createOntProperty(DEFAULT_NS + NADRAZENA_TRIDA);
                    nadrazenaTrida.addLabel(NADRAZENA_TRIDA, "cs");
                    break;

                case ZPUSOB_SDILENI:
                    if (pojemClass != null) {
                        OntProperty zpusobSdileniProp = ontModel.createOntProperty(DEFAULT_NS + ZPUSOB_SDILENI);
                        zpusobSdileniProp.addLabel(ZPUSOB_SDILENI, "cs");
                        zpusobSdileniProp.addDomain(pojemClass);
                        zpusobSdileniProp.addRange(RDFS.Resource);
                    }
                    break;

                case ZPUSOB_ZISKANI:
                    if (pojemClass != null) {
                        OntProperty zpusobZiskaniProp = ontModel.createOntProperty(DEFAULT_NS + ZPUSOB_ZISKANI);
                        zpusobZiskaniProp.addLabel(ZPUSOB_ZISKANI, "cs");
                        zpusobZiskaniProp.addDomain(pojemClass);
                        zpusobZiskaniProp.addRange(RDFS.Resource);
                    }
                    break;

                case TYP_OBSAHU:
                    if (pojemClass != null) {
                        OntProperty typObsahuProp = ontModel.createOntProperty(DEFAULT_NS + TYP_OBSAHU);
                        typObsahuProp.addLabel(TYP_OBSAHU, "cs");
                        typObsahuProp.addDomain(pojemClass);
                        typObsahuProp.addRange(RDFS.Resource);
                    }
                    break;

                default:
                    log.debug("Unknown property requested: {}", propertyName);
                    break;
            }
        }
    }

    private boolean requiresXSDTypes(Set<String> requiredProperties) {
        return requiredProperties.contains(NAZEV) ||
                requiredProperties.contains(POPIS) ||
                requiredProperties.contains(DEFINICE) ||
                requiredProperties.contains(ZDROJ) ||
                requiredProperties.contains(JE_PPDF);
    }
}