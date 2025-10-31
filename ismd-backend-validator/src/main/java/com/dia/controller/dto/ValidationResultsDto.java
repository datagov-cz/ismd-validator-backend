package com.dia.controller.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
@Getter
public class ValidationResultsDto {

    private final List<SeverityGroupDto> severityGroups;
}
