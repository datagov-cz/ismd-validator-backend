package com.dia.constants;

import java.util.Set;

public class ArchiConstants {
    // =============== CORE NAMESPACES ===============
    public static final String DEFAULT_NS = "https://slovník.gov.cz/";
    public static final String OFN_NAMESPACE = "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/";
    public static final String ARCHI_NS = "http://www.opengroup.org/xsd/archimate/3.0/";
    public static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    public static final String CONTEXT = "https://ofn.gov.cz/slovníky/draft/kontexty/slovníky.jsonld";
    public static final String IDENT = "identifier";
    public static final String CAS_NS = "https://slovník.gov.cz/generický/čas/pojem/";
    public static final String SLOVNIKY_NS = "https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/";
    public static final String SCHEMA_URL = "http://schema.org/url";

    // =============== CORE VOCABULARY TERMS ===============
    public static final String POJEM = "pojem";
    public static final String TRIDA = "třída";
    public static final String VZTAH = "vztah";
    public static final String VLASTNOST = "vlastnost";
    public static final String TSP = "typ-subjektu-práva";
    public static final String TOP = "typ-objektu-práva";
    public static final String UDAJ = "údaj";
    public static final String VEREJNY_UDAJ = "veřejný-údaj";
    public static final String NEVEREJNY_UDAJ = "neveřejný-údaj";
    public static final String DATOVY_TYP = "datový-typ";
    public static final String POLOZKA_CISELNIKU = "položka-číselníku";
    public static final String ZPUSOB_SDILENI_UDAJE = "způsob-sdílení-údaje";
    public static final String ZPUSOB_ZISKANI_UDAJE = "způsob-získání-údaje";
    public static final String CASOVY_OKAMZIK = "časový-okamžik";
    public static final String SLOVNIK = "slovník";
    public static final String DIGITALNI_DOKUMENT = "digitální-dokument";
    public static final String CISELNIK = "číselník";
    public static final String TYP_VLASTNOSTI = "typ-vlastnosti";

    // =============== PROPERTY NAMES ===============
    public static final String TYP = "typ";
    public static final String POPIS = "popis";
    public static final String DEFINICE = "definice";
    public static final String ALTERNATIVNI_NAZEV = "alternativní-název";
    public static final String EKVIVALENTNI_POJEM = "ekvivalentní-pojem";
    public static final String IDENTIFIKATOR = "identifikátor";
    public static final String NAZEV = "název";
    public static final String OKAMZIK_POSLEDNI_ZMENY = "okamžik-poslední-změny";
    public static final String OKAMZIK_VYTVORENI = "okamžik-vytvoření";
    public static final String DATUM = "datum";
    public static final String DATUM_A_CAS = "datum-a-čas";
    public static final String ZDROJ = "zdroj";
    public static final String SOUVISEJICI_ZDROJ = "související-zdroj";
    public static final String DEFINUJICI_USTANOVENI = "definující-ustanovení-právního-předpisu";
    public static final String SOUVISEJICI_USTANOVENI = "související-ustanovení-právního-předpisu";
    public static final String DEFINUJICI_NELEGISLATIVNI_ZDROJ = "definující-nelegislativní-zdroj";
    public static final String SOUVISEJICI_NELEGISLATIVNI_ZDROJ = "související-nelegislativní-zdroj";

    // =============== DATA GOVERNANCE PROPERTIES ===============
    public static final String AIS = "agendový-informační-systém";
    public static final String UDAJE_AIS = "údaje-jsou-v-ais";
    public static final String AGENDA = "agenda";
    public static final String JE_PPDF = "je-sdílen-v-ppdf";
    public static final String JE_VEREJNY = "je-pojem-veřejný";
    public static final String USTANOVENI_NEVEREJNOST = "ustanovení-dokládající-neveřejnost-údaje";
    public static final String LOKALNI_KATALOG = "adresa-lokálního-katalogu-dat-ve-kterém-bude-slovník-registrován";
    public static final String DEFINICNI_OBOR = "definiční-obor";
    public static final String OBOR_HODNOT = "obor-hodnot";
    public static final String NADRAZENA_TRIDA = "nadřazená-třída";
    public static final String SUPP = "související-ustanovení-právního-předpisu";
    public static final String ZPUSOB_SDILENI = "má-způsob-sdílení-údajů";
    public static final String ZPUSOB_ZISKANI = "má-kategorii-údajů";
    public static final String TYP_OBSAHU = "má-typ-obsahu-údajů";

    // =============== LONG FORM PROPERTIES ===============
    public static final String JE_PPDF_LONG = "je-sdílen-v-propojeném-datovém-fondu";
    public static final String AGENDA_LONG = "sdružuje-údaje-vedené-nebo-vytvářené-v-rámci-agendy";
    public static final String USTANOVENI_LONG = "je-vymezen-ustanovení-stanovujícím-jeho-neveřejnost";

    // =============== NAMESPACE PATHS ===============
    public static final String AGENDOVY_104 = "agendový/104/pojem/";
    public static final String LEGISLATIVNI_111 = "legislativní/sbírka/111/2009/pojem/";
    public static final String LEGISLATIVNI_111_VU = "legislativní/sbírka/111/2009/pojem/veřejný-údaj";
    public static final String LEGISLATIVNI_111_NVU = "legislativní/sbírka/111/2009/pojem/neveřejný-údaj";
    public static final String VS_POJEM = "veřejný-sektor/pojem/";

    private ArchiConstants() {
    }

    /**
     * Organized property sets replacing the monolithic LABELS array.
     * Groups constants by their logical function and usage.
     */
    public static final class PropertySets {

        public static final String[] CORE_METADATA = {
                TYP, POPIS, DEFINICE, DEFINUJICI_USTANOVENI, SOUVISEJICI_USTANOVENI,
                ALTERNATIVNI_NAZEV, EKVIVALENTNI_POJEM, IDENTIFIKATOR, NAZEV
        };

        public static final String[] DATA_GOVERNANCE = {
                AIS, UDAJE_AIS, AGENDA, AGENDA_LONG, JE_PPDF, JE_PPDF_LONG,
                JE_VEREJNY, USTANOVENI_NEVEREJNOST, LOKALNI_KATALOG,
                ZPUSOB_SDILENI, ZPUSOB_ZISKANI, TYP_OBSAHU, SUPP
        };

        public static final String[] STRUCTURAL = {
                DEFINICNI_OBOR, OBOR_HODNOT, NADRAZENA_TRIDA
        };

        public static final String[] VOCABULARY_TYPES = {
                POJEM, TRIDA, VZTAH, VLASTNOST, TSP, TOP, UDAJ,
                VEREJNY_UDAJ, NEVEREJNY_UDAJ, DATOVY_TYP, POLOZKA_CISELNIKU,
                ZPUSOB_SDILENI_UDAJE, ZPUSOB_ZISKANI_UDAJE
        };

        public static final String[] NAMESPACE_PATHS = {
                AGENDOVY_104, LEGISLATIVNI_111, LEGISLATIVNI_111_VU,
                LEGISLATIVNI_111_NVU, VS_POJEM
        };

        public static final String[] ALL_PROPERTIES;

        static {
            java.util.List<String> allProps = new java.util.ArrayList<>();
            java.util.Collections.addAll(allProps, CORE_METADATA);
            java.util.Collections.addAll(allProps, DATA_GOVERNANCE);
            java.util.Collections.addAll(allProps, STRUCTURAL);
            java.util.Collections.addAll(allProps, VOCABULARY_TYPES);
            java.util.Collections.addAll(allProps, NAMESPACE_PATHS);

            ALL_PROPERTIES = allProps.toArray(new String[0]);
        }

        private PropertySets() {
        }
    }

    /**
     * Organized sets for OFN class and property identification
     */
    public static final class OFNSets {

        public static final Set<String> OFN_CLASSES = Set.of(
                POJEM,
                TRIDA,
                TSP,
                TOP,
                UDAJ,
                VEREJNY_UDAJ,
                NEVEREJNY_UDAJ,
                DATOVY_TYP,
                POLOZKA_CISELNIKU,
                ZPUSOB_SDILENI_UDAJE,
                ZPUSOB_ZISKANI_UDAJE,
                CASOVY_OKAMZIK,
                SLOVNIK,
                DIGITALNI_DOKUMENT,
                CISELNIK,
                TYP_VLASTNOSTI
        );

        public static final Set<String> OFN_PROPERTIES = Set.of(
                NAZEV,
                ALTERNATIVNI_NAZEV,
                POPIS,
                DEFINICE,
                DEFINUJICI_USTANOVENI,
                SOUVISEJICI_USTANOVENI,
                DEFINUJICI_NELEGISLATIVNI_ZDROJ,
                SOUVISEJICI_NELEGISLATIVNI_ZDROJ,
                JE_PPDF,
                AGENDA,
                AIS,
                USTANOVENI_NEVEREJNOST,
                DEFINICNI_OBOR,
                OBOR_HODNOT,
                NADRAZENA_TRIDA,
                ZPUSOB_SDILENI,
                ZPUSOB_ZISKANI,
                TYP_OBSAHU,
                OKAMZIK_POSLEDNI_ZMENY,
                OKAMZIK_VYTVORENI,
                DATUM,
                DATUM_A_CAS
        );

        public static final Set<String> OFN_SPECIAL_PROPERTIES = Set.of(
                SCHEMA_URL
        );

        public static final Set<String> OFN_NAMESPACES = Set.of(
                DEFAULT_NS,
                CAS_NS,
                SLOVNIKY_NS
        );

        private OFNSets() {}
    }
}
