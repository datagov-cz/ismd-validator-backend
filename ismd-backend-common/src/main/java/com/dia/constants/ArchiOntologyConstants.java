package com.dia.constants;

import static com.dia.constants.ArchiConstants.*;

@Deprecated(forRemoval = true)
public class ArchiOntologyConstants {

    // Core namespaces - MIGRATED to ArchiConstants
    @Deprecated(forRemoval = true)
    public static final String NS = ArchiConstants.DEFAULT_NS;
    @Deprecated(forRemoval = true)
    public static final String ARCHI_NS = ArchiConstants.ARCHI_NS;
    @Deprecated(forRemoval = true)
    public static final String XSD = ArchiConstants.XSD;
    @Deprecated(forRemoval = true)
    public static final String IDENT = ArchiConstants.IDENT;
    @Deprecated(forRemoval = true)
    public static final String CONTEXT = ArchiConstants.CONTEXT;
    @Deprecated(forRemoval = true)
    public static final String LANG = ArchiConstants.LANG;

    // Type constants - CONSOLIDATED to single constants in ArchiConstants
    @Deprecated(forRemoval = true)
    public static final String TYP_TSP = TSP;
    @Deprecated(forRemoval = true)
    public static final String TYP_TOP = TOP;
    @Deprecated(forRemoval = true)
    public static final String TYP_POJEM = POJEM;
    @Deprecated(forRemoval = true)
    public static final String TYP_TRIDA = TRIDA;
    @Deprecated(forRemoval = true)
    public static final String TYP_VZTAH = VZTAH;
    @Deprecated(forRemoval = true)
    public static final String TYP_VEREJNY_UDAJ = VEREJNY_UDAJ;
    @Deprecated(forRemoval = true)
    public static final String TYP_NEVEREJNY_UDAJ = NEVEREJNY_UDAJ;
    @Deprecated(forRemoval = true)
    public static final String TYP_VLASTNOST = VLASTNOST;
    @Deprecated(forRemoval = true)
    public static final String TYP_DT = DATOVY_TYP;

    // Label constants - DUPLICATES REMOVED, use core constants from ArchiConstants
    @Deprecated(forRemoval = true)
    public static final String LABEL_TYP = TYP;
    @Deprecated(forRemoval = true)
    public static final String LABEL_POPIS = POPIS;
    @Deprecated(forRemoval = true)
    public static final String LABEL_DEF = DEFINICE;
    @Deprecated(forRemoval = true)
    public static final String LABEL_ZDROJ = ZDROJ;
    @Deprecated(forRemoval = true)
    public static final String LABEL_SZ = SOUVISEJICI_ZDROJ;
    @Deprecated(forRemoval = true)
    public static final String LABEL_AN = ALTERNATIVNI_NAZEV;
    @Deprecated(forRemoval = true)
    public static final String LABEL_EP = EKVIVALENTNI_POJEM;
    @Deprecated(forRemoval = true)
    public static final String LABEL_ID = IDENTIFIKATOR;
    @Deprecated(forRemoval = true)
    public static final String LABEL_AIS = AIS;
    @Deprecated(forRemoval = true)
    public static final String LABEL_UDAJE_AIS = UDAJE_AIS;
    @Deprecated(forRemoval = true)
    public static final String LABEL_AGENDA = AGENDA;
    @Deprecated(forRemoval = true)
    public static final String AGENDA_LONG = ArchiConstants.AGENDA_LONG;
    @Deprecated(forRemoval = true)
    public static final String LABEL_DT = DATOVY_TYP;
    @Deprecated(forRemoval = true)
    public static final String LABEL_JE_PPDF = JE_PPDF;
    @Deprecated(forRemoval = true)
    public static final String LABEL_JE_PPDF_LONG = JE_PPDF_LONG;
    @Deprecated(forRemoval = true)
    public static final String LABEL_JE_VEREJNY = JE_VEREJNY;
    @Deprecated(forRemoval = true)
    public static final String LABEL_UDN = USTANOVENI_NEVEREJNOST;
    @Deprecated(forRemoval = true)
    public static final String LABEL_ALKD = LOKALNI_KATALOG;
    @Deprecated(forRemoval = true)
    public static final String LABEL_DEF_O = DEFINICNI_OBOR;
    @Deprecated(forRemoval = true)
    public static final String LABEL_OBOR_HODNOT = OBOR_HODNOT;
    @Deprecated(forRemoval = true)
    public static final String LABEL_VU = VEREJNY_UDAJ;
    @Deprecated(forRemoval = true)
    public static final String LABEL_NVU = NEVEREJNY_UDAJ;
    @Deprecated(forRemoval = true)
    public static final String LABEL_NT = NADRAZENA_TRIDA;
    @Deprecated(forRemoval = true)
    public static final String LABEL_NAZEV = NAZEV;
    @Deprecated(forRemoval = true)
    public static final String LABEL_POJEM = POJEM;
    @Deprecated(forRemoval = true)
    public static final String LABEL_SUPP = USTANOVENI_NEVEREJNOST;
    @Deprecated(forRemoval = true)
    public static final String LABEL_SUPP_LONG = USTANOVENI_LONG;
    @Deprecated(forRemoval = true)
    public static final String LABEL_TOP = TOP;
    @Deprecated(forRemoval = true)
    public static final String LABEL_TSP = TSP;
    @Deprecated(forRemoval = true)
    public static final String LABEL_VZTAH = VZTAH;
    @Deprecated(forRemoval = true)
    public static final String LABEL_VLASTNOST = VLASTNOST;
    @Deprecated(forRemoval = true)
    public static final String LABEL_TRIDA = TRIDA;
    @Deprecated(forRemoval = true)
    public static final String LABEL_ZPUSOB_SDILENI = ZPUSOB_SDILENI;
    @Deprecated(forRemoval = true)
    public static final String LABEL_ZPUSOB_ZISKANI = ZPUSOB_ZISKANI;
    @Deprecated(forRemoval = true)
    public static final String LABEL_TYP_OBSAHU = TYP_OBSAHU;

    // Namespace paths - MIGRATED to ArchiConstants
    @Deprecated(forRemoval = true)
    public static final String AGENDOVY_104 = ArchiConstants.AGENDOVY_104;
    @Deprecated(forRemoval = true)
    public static final String LEGISLATIVNI_111 = ArchiConstants.LEGISLATIVNI_111;
    @Deprecated(forRemoval = true)
    public static final String LEGISLATIVNI_111_VU = ArchiConstants.LEGISLATIVNI_111_VU;
    @Deprecated(forRemoval = true)
    public static final String LEGISLATIVNI_111_NVU = ArchiConstants.LEGISLATIVNI_111_NVU;
    @Deprecated(forRemoval = true)
    public static final String VS_POJEM = ArchiConstants.VS_POJEM;

    // LABELS array - REPLACED with organized PropertySets in ArchiConstants
    @Deprecated(forRemoval = true)
    public static final String[] LABELS = ArchiConstants.PropertySets.ALL_PROPERTIES;

    private ArchiOntologyConstants() {
    }
}