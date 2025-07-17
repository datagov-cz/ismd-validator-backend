package com.dia.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class ValidationResultsDto {

    private final List<SeverityGroupDto> severityGroups;
}
