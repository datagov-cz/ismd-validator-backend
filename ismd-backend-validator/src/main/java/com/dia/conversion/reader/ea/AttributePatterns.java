package com.dia.conversion.reader.ea;

import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Data
@Getter
public class AttributePatterns {

    protected static final Map<String, Pattern[]> ATTRIBUTE_PATTERNS = new HashMap<>();

    static {
        ATTRIBUTE_PATTERNS.put("POPIS", new Pattern[]{
                Pattern.compile(".*z[aá]klad.*popis.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*\\b1\\.\\s*z[aá]klad\\s*-\\s*popis.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*popis.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*description.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("POPIS_SLOVNIKU", new Pattern[]{
                Pattern.compile(".*popis\\s+slovn[ií]ku.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*popis.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("DEFINICE", new Pattern[]{
                Pattern.compile(".*z[aá]klad.*definice.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*definice.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*definition.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("ZDROJ", new Pattern[]{
                Pattern.compile(".*z[aá]klad.*zdroj.*", Pattern.UNICODE_CASE),
                Pattern.compile("^(?!.*souvisej).*zdroj.*", Pattern.UNICODE_CASE),
                Pattern.compile("^(?!.*related).*source.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("ALTERNATIVNI_NAZEV", new Pattern[]{
                Pattern.compile(".*alternativn[ií]\\s+n[aá]zev.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*alternative.*name.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("DATOVY_TYP", new Pattern[]{
                Pattern.compile(".*datov[ýy]\\s+typ.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*data.*type.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("EKVIVALENTNI_POJEM", new Pattern[]{
                Pattern.compile(".*ekvivalentn[ií]\\s+pojem.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*equivalent.*concept.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("SOUVISEJICI_ZDROJ", new Pattern[]{
                Pattern.compile(".*souvisej[ií]c[ií][\\s_-]*zdroj.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*related[\\s_-]*source.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("JE_POJEM_SDILEN_V_PPDF", new Pattern[]{
                Pattern.compile(".*je\\s+pojem\\s+sd[ií]len.*ppdf.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*pojem\\s+sd[ií]len.*ppdf.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*sd[ií]len.*ppdf.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*shared.*ppdf.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("JE_POJEM_VEREJNY", new Pattern[]{
                Pattern.compile(".*je\\s+pojem\\s+ve[řr]ejn[ýy].*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*pojem\\s+ve[řr]ejn[ýy].*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*ve[řr]ejn[ýy].*\\?.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*ve[řr]ejn[ýy].*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*public.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("USTANOVENI_DOKLADAJICI_NEVEREJNOST", new Pattern[]{
                Pattern.compile(".*ustanoven[ií].*dokl[aá]daj[ií]c[ií].*neve[řr]ejnost.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*dokl[aá]daj[ií]c[ií].*neve[řr]ejnost.*pojmu.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*neve[řr]ejnost.*pojmu.*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*privacy.*provision.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("IDENTIFIKATOR", new Pattern[]{
                Pattern.compile(".*identifik[aá]tor.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*identifier.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("AGENDA", new Pattern[]{
                Pattern.compile(".*agenda.*k[oó]d.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*agenda(?!.*syst[eé]m).*", Pattern.UNICODE_CASE)
        });

        ATTRIBUTE_PATTERNS.put("AGENDOVY_INFORMACNI_SYSTEM", new Pattern[]{
                Pattern.compile(".*agendov[ýy]\\s+informa[čc]n[ií]\\s+syst[eé]m.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*ais.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("TYP_OBSAHU_UDAJE", new Pattern[]{
                Pattern.compile(".*typ\\s+obsahu\\s+[uú]daje.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*content.*type.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("ZPUSOB_SDILENI_UDAJE", new Pattern[]{
                Pattern.compile(".*zp[ůu]sob\\s+sd[ií]len[ií]\\s+[uú]daje.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*sharing.*method.*", Pattern.CASE_INSENSITIVE)
        });

        ATTRIBUTE_PATTERNS.put("ZPUSOB_ZISKANI_UDAJE", new Pattern[]{
                Pattern.compile(".*zp[ůu]sob\\s+z[ií]sk[aá]n[ií]\\s+[uú]daje.*", Pattern.UNICODE_CASE),
                Pattern.compile(".*acquisition.*method.*", Pattern.CASE_INSENSITIVE)
        });
    }

    private AttributePatterns() {}
}