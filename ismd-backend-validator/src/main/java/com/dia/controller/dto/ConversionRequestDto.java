package com.dia.controller.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@Data
public class ConversionRequestDto {
    private MultipartFile file;
    private String output;
    private Boolean removeInvalidSources;
}
