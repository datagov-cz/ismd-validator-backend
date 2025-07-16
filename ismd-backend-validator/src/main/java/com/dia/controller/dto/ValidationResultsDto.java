package com.dia.controller.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class ValidationResultsDto {

    private final List<SeverityGroupDto> severityGroups;
}
