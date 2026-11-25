package com.dia.validation;

/** Outcome of the global (corpus) validation pass, as returned to API clients. */
public record GlobalValidationSummary(GlobalValidationStatus status, String message) {

    public static GlobalValidationSummary ok() {
        return new GlobalValidationSummary(GlobalValidationStatus.OK,
                "Kontrola vůči NKD proběhla.");
    }

    public static GlobalValidationSummary notConfigured() {
        return new GlobalValidationSummary(GlobalValidationStatus.SKIPPED,
                "Kontrola vůči NKD není nakonfigurována, globální pravidla nebyla ověřena.");
    }

    public static GlobalValidationSummary noRulesEnabled() {
        return new GlobalValidationSummary(GlobalValidationStatus.SKIPPED,
                "Žádné globální pravidlo není zapnuto, kontrola vůči NKD nebyla provedena.");
    }

    public static GlobalValidationSummary unavailable() {
        return new GlobalValidationSummary(GlobalValidationStatus.UNAVAILABLE,
                "NKD není dostupné, globální pravidla nebyla ověřena (nebo jen částečně).");
    }
}
