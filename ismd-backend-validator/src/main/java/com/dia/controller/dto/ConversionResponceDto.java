package com.dia.controller.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@Builder
public class ConversionResponceDto {
    private String response;
    private String errorMessage;
}
