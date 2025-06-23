package com.dia.constants;

import static com.dia.constants.ExportConstants.Json.*;

@Deprecated(forRemoval = true)
public class JsonExportConstants {

    // JSON-LD structure fields - MIGRATED to ExportConstants.Json
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String JSON_FIELD_CONTEXT = CONTEXT;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String JSON_FIELD_IRI = IRI;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String JSON_FIELD_TYP = TYP;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String JSON_FIELD_NAZEV = NAZEV;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String JSON_FIELD_POPIS = POPIS;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String JSON_FIELD_POJMY = POJMY;

    // Vocabulary classification types - MIGRATED to ExportConstants.Json
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String TYPE_SLOVNIK = ExportConstants.Json.TYPE_SLOVNIK;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String TYPE_TEZAURUS = ExportConstants.Json.TYPE_TEZAURUS;
    @Deprecated(since = "2.0", forRemoval = true)
    public static final String TYPE_KM = ExportConstants.Json.TYPE_KM;

    private JsonExportConstants() {
    }
}