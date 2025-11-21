package com.dia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogRecordDto {

    @JsonProperty("@context")
    private String context;

    private String iri;

    private String typ;

    @JsonProperty("název")
    private Map<String, String> nazev;

    private Map<String, String> popis;

    @JsonProperty("prvek_rúian")
    private List<String> prvekRuian;

    @JsonProperty("geografické_území")
    private List<String> geografickeUzemi;

    @JsonProperty("prostorové_pokrytí")
    private List<String> prostorovePokryti;

    @JsonProperty("klíčové_slovo")
    private Map<String, List<String>> klicoveSlovo;

    @JsonProperty("periodicita_aktualizace")
    private String periodicitaAktualizace;

    @JsonProperty("téma")
    private List<String> tema;

    @JsonProperty("koncept_euroVoc")
    private List<String> konceptEuroVoc;

    private List<String> specifikace;

    @JsonProperty("kontaktní_bod")
    private Map<String, Object> kontaktniBod;

    private List<DistribuceDto> distribuce;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistribuceDto {
        private String typ;

        @JsonProperty("podmínky_užití")
        private PodminkyUzitiDto podminkyUziti;

        @JsonProperty("soubor_ke_stažení")
        private String souborKeStazeni;

        @JsonProperty("přístupové_url")
        private String pristupoveUrl;

        @JsonProperty("typ_média")
        private String typMedia;

        @JsonProperty("formát")
        private String format;

        @JsonProperty("schéma")
        private String schema;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PodminkyUzitiDto {
        private String typ;

        @JsonProperty("autorské_dílo")
        private String autorskeDilo;

        @JsonProperty("databáze_jako_autorské_dílo")
        private String databizeJakoAutorskeDilo;

        @JsonProperty("databáze_chráněná_zvláštními_právy")
        private String databizeChranenaZvlastnimiPravy;

        @JsonProperty("osobní_údaje")
        private String osobniUdaje;
    }
}
