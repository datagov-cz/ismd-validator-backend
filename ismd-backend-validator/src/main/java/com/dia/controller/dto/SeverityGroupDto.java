package com.dia.controller.dto;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@Getter
public class SeverityGroupDto {

    private final String severity;

    private final int count;

    private final String description;
}
