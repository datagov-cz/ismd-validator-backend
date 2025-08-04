package com.dia.service;

import com.dia.conversion.data.TransformationResult;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.enums.ValidationTiming;
import org.apache.jena.rdf.model.Model;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ValidationService {

    ISMDValidationReport validate(TransformationResult result);

    ISMDValidationReport validate(TransformationResult result, ValidationTiming timing);

    ISMDValidationReport validateWithRules(TransformationResult result, List<String> ruleNames, ValidationTiming timing);

    ISMDValidationReport validateModel(Model model);
    ISMDValidationReport validateModelWithRules(Model model, List<String> ruleNames);

    ISMDValidationReport validateRdf(String rdfContent, String format);
    ISMDValidationReport validateRdfWithRules(String rdfContent, String format, List<String> ruleNames);

    ISMDValidationReport validateTtl(String ttlContent);
    ISMDValidationReport validateTtlWithRules(String ttlContent, List<String> ruleNames);

    ISMDValidationReport validateTtlFile(MultipartFile file);
    ISMDValidationReport validateTtlFileWithRules(MultipartFile file, List<String> ruleNames);
}
