package com.dia.service;

import com.dia.conversion.data.TransformationResult;
import com.dia.service.record.RuleInfo;
import com.dia.service.record.ValidationConfigurationSummary;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.enums.ValidationTiming;
import org.apache.jena.rdf.model.Model;

import java.util.List;
import java.util.Optional;

public interface ValidationService {

    ISMDValidationReport validate(TransformationResult result);

    ISMDValidationReport validate(TransformationResult result, ValidationTiming timing);

    ISMDValidationReport validateWithRules(TransformationResult result, List<String> ruleNames, ValidationTiming timing);

    ISMDValidationReport validateModel(Model model);

    ISMDValidationReport validateRdf(String rdfContent, String format);

    ISMDValidationReport validateGlobally(TransformationResult result);

    ISMDValidationReport validateComplete(TransformationResult result, boolean includeGlobal);

    ValidationConfigurationSummary getConfigurationSummary();

    void setRuleEnabled(String ruleName, boolean enabled);

    Optional<RuleInfo> getRuleInfo(String ruleName);

    List<String> getAllRuleNames();

    List<String> getEnabledRuleNames();

    ISMDValidationReport testValidation();

    boolean testGlobalValidationConnectivity();
}
