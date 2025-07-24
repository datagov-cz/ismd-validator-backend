package com.dia.constants;

public class ExportConstants {

    private ExportConstants() {
    }

    /**
     * JSON-LD export specific constants
     */
    public static final class Json {
        public static final String CONTEXT = "@context";
        public static final String IRI = "iri";
        public static final String TYP = "typ";
        public static final String NAZEV = "název";
        public static final String POPIS = "popis";
        public static final String POJMY = "pojmy";

        public static final String TYPE_SLOVNIK = "Slovník";
        public static final String TYPE_TEZAURUS = "Tezaurus";
        public static final String TYPE_KM = "Konceptuální model";

        // =============== JSON-LD TYPE MAPPINGS ===============
        public static final String POJEM_JSON_LD = "Pojem";
        public static final String TRIDA_JSON_LD = "Třída";
        public static final String VZTAH_JSON_LD = "Vztah";
        public static final String VLASTNOST_JSON_LD = "Vlastnost";
        public static final String TSP_JSON_LD = "Typ subjektu práva";
        public static final String TOP_JSON_LD = "Typ objektu práva";
        public static final String VEREJNY_UDAJ_JSON_LD = "Veřejný údaj";
        public static final String NEVEREJNY_UDAJ_JSON_LD = "Neveřejný údaj";

        private Json() {
        }
    }

    /**
     * Turtle/RDF export specific constants
     */
    public static final class Turtle {
        public static final String PREFIX_DCT = "dct";
        public static final String PREFIX_OWL = "owl";
        public static final String PREFIX_RDF = "rdf";
        public static final String PREFIX_RDFS = "rdfs";
        public static final String PREFIX_SKOS = "skos";
        public static final String PREFIX_XSD = "xsd";

        public static final String DEFAULT_PREFIX = "domain";

        private Turtle() {
        }
    }

    /**
     * Common export utilities and shared constants
     */
    public static final class Common {
        public static final String DEFAULT_LANG = "cs";

        public static final String FORMAT_JSON = "application/json";
        public static final String FORMAT_JSONLD = "application/ld+json";
        public static final String FORMAT_TURTLE = "text/turtle";
        public static final String FORMAT_RDFXML = "application/rdf+xml";

        private Common() {
        }
    }
}
