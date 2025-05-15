package dia.ismd.validator.constants;

public class ArchiOntologyConstants {
    public static final String NS = "https://slovník.gov.cz/";
    public static final String ARCHI_NS = "http://www.opengroup.org/xsd/archimate/3.0/";
    public static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    public static final String IDENT = "identifier";
    public static final String CONTEXT = "https://ofn.gov.cz/slovníky/draft/kontexty/slovníky.jsonld";
    public static final String LANG = "lang=";

    public static final String TYP_TSP = "Typ subjektu práva";
    public static final String TYP_TOP = "Typ objektu práva";
    public static final String TYP_POJEM = "Pojem";
    public static final String TYP_TRIDA = "Třída";
    public static final String TYP_VZTAH = "Vztah";
    public static final String TYP_VEREJNY_UDAJ = "Veřejný údaj";
    public static final String TYP_NEVEREJNY_UDAJ = "Neveřejný údaj";
    public static final String TYP_VLASTNOST = "Vlastnost";
    public static final String TYP_DT = "Datový typ";

    public static final String LABEL_TYP = "typ";
    public static final String LABEL_POPIS = "popis";
    public static final String LABEL_DEF = "definice";
    public static final String LABEL_ZDROJ = "zdroj";
    public static final String LABEL_SZ = "související-zdroj";
    public static final String LABEL_AN = "alternativní-název";
    public static final String LABEL_EP = "ekvivalentní-pojem";
    public static final String LABEL_ID = "identifikátor";
    public static final String LABEL_AIS = "agendový-informační-systém";
    public static final String LABEL_UDAJE_AIS = "údaje-jsou-v-ais";
    public static final String LABEL_AGENDA = "agenda";
    public static final String AGENDA_LONG = "sdružuje-údaje-vedené-nebo-vytvářené-v-rámci-agendy";
    public static final String LABEL_DT = "datový-typ";
    public static final String LABEL_JE_PPDF = "je-sdílen-v-ppdf";
    public static final String LABEL_JE_PPDF_LONG = "je-sdílen-v-propojeném-datovém-fondu";
    public static final String LABEL_JE_VEREJNY = "je-pojem-veřejný";
    public static final String LABEL_UDN = "ustanovení-dokládající-neveřejnost";
    public static final String LABEL_ALKD = "adresa-lokálního-katalogu-dat-ve-kterém-bude-slovník-registrován";
    public static final String LABEL_DEF_O = "definiční-obor";
    public static final String LABEL_OBOR_HODNOT = "obor-hodnot";
    public static final String LABEL_VU = "veřejný-údaj";
    public static final String LABEL_NVU = "neveřejný-údaj";
    public static final String LABEL_NT = "nadřazená-třída";
    public static final String LABEL_NAZEV = "název";
    public static final String LABEL_POJEM = "pojem";
    public static final String LABEL_SUPP = "související-ustanovení-právního-předpisu";
    public static final String LABEL_SUPP_LONG = "je-vymezen-ustanovení-stanovujícím-jeho-neveřejnost";
    public static final String LABEL_TOP = "typ-objektu-práva";
    public static final String LABEL_TSP = "typ-subjektu-práva";
    public static final String LABEL_VZTAH = "vztah";
    public static final String LABEL_VLASTNOST = "vlastnost";
    public static final String LABEL_TRIDA = "třída";
    public static final String AGENDOVY_104 = "agendový/104/pojem/";
    public static final String LEGISLATIVNI_111 = "legislativní/sbírka/111/2009/pojem/";
    public static final String LEGISLATIVNI_111_VU = "legislativní/sbírka/111/2009/pojem/veřejný-údaj";
    public static final String LEGISLATIVNI_111_NVU = "legislativní/sbírka/111/2009/pojem/neveřejný-údaj";
    public static final String VS_POJEM = "veřejný-sektor/pojem/";

    public static final String[] LABELS = {
            LABEL_TYP, LABEL_POPIS, LABEL_DEF, LABEL_ZDROJ, LABEL_SZ, LABEL_AN, LABEL_EP, LABEL_ID, LABEL_AIS,
            LABEL_UDAJE_AIS, LABEL_AGENDA, AGENDA_LONG,LABEL_DT, LABEL_JE_PPDF, LABEL_JE_PPDF_LONG,LABEL_JE_VEREJNY,
            LABEL_UDN, LABEL_ALKD, LABEL_DEF_O, LABEL_OBOR_HODNOT, LABEL_VU, LABEL_NVU, LABEL_NT, LABEL_NAZEV,
            LABEL_POJEM, LABEL_SUPP, LABEL_SUPP_LONG,LABEL_TOP, LABEL_TSP, LABEL_VZTAH, LABEL_VLASTNOST, LABEL_TRIDA,
            AGENDOVY_104, LEGISLATIVNI_111, LEGISLATIVNI_111_VU,
            LEGISLATIVNI_111_NVU, VS_POJEM
    };

    private ArchiOntologyConstants() {
    }
}
