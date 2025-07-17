package com.dia.controller.dto;

import com.dia.enums.ValidationSeverity;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class SeverityGroupDto {

    private final String severity;

    private final int count;

    private final String description;
}
