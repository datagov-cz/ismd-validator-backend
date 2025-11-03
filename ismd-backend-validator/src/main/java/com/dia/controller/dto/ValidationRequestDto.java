package com.dia.controller.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class ValidationRequestDto {
    private String ontologyContent;
    private String iri;
}
