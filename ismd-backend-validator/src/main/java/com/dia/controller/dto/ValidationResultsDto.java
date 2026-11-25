package com.dia.controller.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import com.dia.validation.GlobalValidationSummary;

import java.util.List;

@Data
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
@Getter
public class ValidationResultsDto {

    private final List<SeverityGroupDto> severityGroups;

    /** Whether the corpus (global) checks ran: OK, SKIPPED or UNAVAILABLE, with a user-facing message. */
    private GlobalValidationSummary globalValidation;
}
