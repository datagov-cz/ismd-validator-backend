package com.dia.constants;

public class ExportConstants {

    private ExportConstants() {
    }

    /**
     * JSON-LD export specific constants
     */
    public static final class Json {
        public static final String IRI = "iri";
        public static final String TYP = "typ";
        public static final String NAZEV = "název";
        public static final String POPIS = "popis";

        // Vocabulary dates: nested "Časový okamžik" nodes
        public static final String VYTVORENO = "vytvořeno";
        public static final String AKTUALIZOVANO = "aktualizováno";
        public static final String DATUM_A_CAS = "datum_a_čas";
        public static final String DATUM = "datum";

        public static final String TYPE_SLOVNIK = "Slovník";
        public static final String TYPE_TEZAURUS = "Tezaurus";
        public static final String TYPE_KM = "Konceptuální model";

        // =============== NAMESPACE CONSTANTS ===============
        public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";
        public static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";

        // =============== XSD DATA TYPE CONSTANTS ===============
        public static final String XSD_BOOLEAN = XSD_NS + "boolean";
        public static final String XSD_DATE = XSD_NS + "date";
        public static final String XSD_TIME = XSD_NS + "time";
        public static final String XSD_DATETIME_STAMP = XSD_NS + "dateTimeStamp";
        public static final String XSD_INTEGER = XSD_NS + "integer";
        public static final String XSD_DOUBLE = XSD_NS + "double";
        public static final String XSD_ANY_URI = XSD_NS + "anyURI";
        public static final String XSD_STRING = XSD_NS + "string";
        public static final String RDFS_LITERAL = RDFS_NS + "Literal";

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
        // Governance codelist namespaces (data governance: content type, sharing method, acquisition method).
        public static final String NS_TYPY_OBSAHU =
                "https://data.dia.gov.cz/zdroj/číselníky/typy-obsahu-údajů/položky/";
        public static final String NS_ZPUSOBY_SDILENI =
                "https://data.dia.gov.cz/zdroj/číselníky/způsoby-sdílení-údajů/položky/";
        public static final String NS_ZPUSOBY_ZISKANI =
                "https://data.dia.gov.cz/zdroj/číselníky/způsoby-získání-údajů/položky/";

        private Turtle() {
        }
    }
}
