package com.dia.utility;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class Transliterator {
    private static final Map<Character, String> TRANSLITERATION_MAP = new HashMap<>();

    static {
        // Czech diacritics
        TRANSLITERATION_MAP.put('á', "a");
        TRANSLITERATION_MAP.put('Á', "A");
        TRANSLITERATION_MAP.put('č', "c");
        TRANSLITERATION_MAP.put('Č', "C");
        TRANSLITERATION_MAP.put('ď', "d");
        TRANSLITERATION_MAP.put('Ď', "D");
        TRANSLITERATION_MAP.put('é', "e");
        TRANSLITERATION_MAP.put('É', "E");
        TRANSLITERATION_MAP.put('ě', "e");
        TRANSLITERATION_MAP.put('Ě', "E");
        TRANSLITERATION_MAP.put('í', "i");
        TRANSLITERATION_MAP.put('Í', "I");
        TRANSLITERATION_MAP.put('ň', "n");
        TRANSLITERATION_MAP.put('Ň', "N");
        TRANSLITERATION_MAP.put('ó', "o");
        TRANSLITERATION_MAP.put('Ó', "O");
        TRANSLITERATION_MAP.put('ř', "r");
        TRANSLITERATION_MAP.put('Ř', "R");
        TRANSLITERATION_MAP.put('š', "s");
        TRANSLITERATION_MAP.put('Š', "S");
        TRANSLITERATION_MAP.put('ť', "t");
        TRANSLITERATION_MAP.put('Ť', "T");
        TRANSLITERATION_MAP.put('ú', "u");
        TRANSLITERATION_MAP.put('Ú', "U");
        TRANSLITERATION_MAP.put('ů', "u");
        TRANSLITERATION_MAP.put('Ů', "U");
        TRANSLITERATION_MAP.put('ý', "y");
        TRANSLITERATION_MAP.put('Ý', "Y");
        TRANSLITERATION_MAP.put('ž', "z");
        TRANSLITERATION_MAP.put('Ž', "Z");

        // Common European diacritics
        TRANSLITERATION_MAP.put('à', "a");
        TRANSLITERATION_MAP.put('À', "A");
        TRANSLITERATION_MAP.put('â', "a");
        TRANSLITERATION_MAP.put('Â', "A");
        TRANSLITERATION_MAP.put('ä', "ae");
        TRANSLITERATION_MAP.put('Ä', "AE");
        TRANSLITERATION_MAP.put('ã', "a");
        TRANSLITERATION_MAP.put('Ã', "A");
        TRANSLITERATION_MAP.put('å', "a");
        TRANSLITERATION_MAP.put('Å', "A");
        TRANSLITERATION_MAP.put('æ', "ae");
        TRANSLITERATION_MAP.put('Æ', "AE");
        TRANSLITERATION_MAP.put('ç', "c");
        TRANSLITERATION_MAP.put('Ç', "C");
        TRANSLITERATION_MAP.put('è', "e");
        TRANSLITERATION_MAP.put('È', "E");
        TRANSLITERATION_MAP.put('ê', "e");
        TRANSLITERATION_MAP.put('Ê', "E");
        TRANSLITERATION_MAP.put('ë', "e");
        TRANSLITERATION_MAP.put('Ë', "E");
        TRANSLITERATION_MAP.put('ì', "i");
        TRANSLITERATION_MAP.put('Ì', "I");
        TRANSLITERATION_MAP.put('î', "i");
        TRANSLITERATION_MAP.put('Î', "I");
        TRANSLITERATION_MAP.put('ï', "i");
        TRANSLITERATION_MAP.put('Ï', "I");
        TRANSLITERATION_MAP.put('ñ', "n");
        TRANSLITERATION_MAP.put('Ñ', "N");
        TRANSLITERATION_MAP.put('ò', "o");
        TRANSLITERATION_MAP.put('Ò', "O");
        TRANSLITERATION_MAP.put('ô', "o");
        TRANSLITERATION_MAP.put('Ô', "O");
        TRANSLITERATION_MAP.put('ö', "oe");
        TRANSLITERATION_MAP.put('Ö', "OE");
        TRANSLITERATION_MAP.put('õ', "o");
        TRANSLITERATION_MAP.put('Õ', "O");
        TRANSLITERATION_MAP.put('ø', "o");
        TRANSLITERATION_MAP.put('Ø', "O");
        TRANSLITERATION_MAP.put('ù', "u");
        TRANSLITERATION_MAP.put('Ù', "U");
        TRANSLITERATION_MAP.put('û', "u");
        TRANSLITERATION_MAP.put('Û', "U");
        TRANSLITERATION_MAP.put('ü', "ue");
        TRANSLITERATION_MAP.put('Ü', "UE");
        TRANSLITERATION_MAP.put('ÿ', "y");
        TRANSLITERATION_MAP.put('Ÿ', "Y");
        TRANSLITERATION_MAP.put('ß', "ss");
    }

    /**
     * Transliterates text by converting diacritics to their ASCII equivalents.
     * First tries custom mappings, then falls back to Unicode normalization.
     *
     * @param text the text to transliterate
     * @return transliterated text with ASCII characters only
     */
    public String transliterate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (TRANSLITERATION_MAP.containsKey(c)) {
                result.append(TRANSLITERATION_MAP.get(c));
            } else if (c > 127) {
                String normalized = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                String ascii = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
                result.append(ascii.isEmpty() ? "" : ascii);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
