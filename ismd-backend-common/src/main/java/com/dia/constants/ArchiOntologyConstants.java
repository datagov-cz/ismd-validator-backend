package com.dia.constants;

import static com.dia.constants.OntologyConstants.*;

/**
 * @deprecated This class has been refactored and split into multiple organized classes.
 *
 * Migration guide:
 * - Use {@link OntologyConstants} for core vocabulary and property constants
 * - Use {@link ExportConstants} for export format specific constants
 * - Use {@link TypeMappings} for type conversion utilities
 * - Use {@link OntologyConstants.PropertySets} for organized constant groups
 *
 * This class will be removed in version 3.0. Please migrate to the new structure.
 *
 * @see OntologyConstants
 * @see ExportConstants
 * @see TypeMappings
 */
@Deprecated(forRemoval = true)
public class ArchiOntologyConstants {

    // Core namespaces - MIGRATED to OntologyConstants
    @Deprecated(forRemoval = true)
    public static final String NS = OntologyConstants.NS;
    @Deprecated(forRemoval = true)
    public static final String ARCHI_NS = OntologyConstants.ARCHI_NS;
    @Deprecated(forRemoval = true)
    public static final String XSD = OntologyConstants.XSD;
    @Deprecated(forRemoval = true)
    public static final String IDENT = OntologyConstants.IDENT;
    @Deprecated(forRemoval = true)
    public static final String CONTEXT = OntologyConstants.CONTEXT;
    @Deprecated(forRemoval = true)
    public static final String LANG = OntologyConstants.LANG;

    // Type constants - CONSOLIDATED to single constants in OntologyConstants
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

    // Label constants - DUPLICATES REMOVED, use core constants from OntologyConstants
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
    public static final String AGENDA_LONG = OntologyConstants.AGENDA_LONG;
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

    // Namespace paths - MIGRATED to OntologyConstants
    @Deprecated(forRemoval = true)
    public static final String AGENDOVY_104 = OntologyConstants.AGENDOVY_104;
    @Deprecated(forRemoval = true)
    public static final String LEGISLATIVNI_111 = OntologyConstants.LEGISLATIVNI_111;
    @Deprecated(forRemoval = true)
    public static final String LEGISLATIVNI_111_VU = OntologyConstants.LEGISLATIVNI_111_VU;
    @Deprecated(forRemoval = true)
    public static final String LEGISLATIVNI_111_NVU = OntologyConstants.LEGISLATIVNI_111_NVU;
    @Deprecated(forRemoval = true)
    public static final String VS_POJEM = OntologyConstants.VS_POJEM;

    // LABELS array - REPLACED with organized PropertySets in OntologyConstants
    @Deprecated(forRemoval = true)
    public static final String[] LABELS = OntologyConstants.PropertySets.ALL_PROPERTIES;

    private ArchiOntologyConstants() {
    }
}