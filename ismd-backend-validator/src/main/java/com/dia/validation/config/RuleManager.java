package com.dia.validation.config;

import com.dia.exceptions.ValidationConfigurationException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Slf4j
public class RuleManager {

    private final ValidationConfiguration config;
    @Getter
    private final ResourceLoader resourceLoader;
    private final Map<String, RuleDefinition> allRules = new ConcurrentHashMap<>();
    private final Map<String, Model> ruleModels = new ConcurrentHashMap<>();
    private final Map<String, Boolean> ruleLocalityMap = new ConcurrentHashMap<>();

    @Autowired
    public RuleManager(ValidationConfiguration config, ResourceLoader resourceLoader) {
        this.config = config;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadRules() {
        log.info("Loading SHACL validation rules from: {}", config.getRulesDirectory());

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = config.getRulesDirectory().replace("classpath:", "classpath*:") + "**/*.ttl";
            org.springframework.core.io.Resource[] resources = resolver.getResources(pattern);

            log.info("Found {} rule files", resources.length);

            for (org.springframework.core.io.Resource resource : resources) {
                loadRuleFile(resource);
            }

            log.info("Successfully loaded {} validation rules", allRules.size());
            logRuleStatus();
            logLocalityStatus();

        } catch (IOException e) {
            log.error("Failed to load validation rules", e);
            throw new ValidationConfigurationException("Could not load validation rules", e);
        }
    }

    private void loadRuleFile(org.springframework.core.io.Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            String filename = resource.getFilename();
            String ruleName = extractRuleName(filename);

            log.debug("Loading rule file: {} as rule: {}", filename, ruleName);

            Model model = ModelFactory.createDefaultModel();
            model.read(inputStream, null, "TTL");

            RuleDefinition ruleDefinition = new RuleDefinition(
                    ruleName,
                    filename,
                    resource.getURI().toString(),
                    extractRuleMetadata(model)
            );

            allRules.put(ruleName, ruleDefinition);
            ruleModels.put(ruleName, model);

            registerRuleLocality(ruleName, filename);
            log.debug("Successfully registered rule locality: {} as rule: {}", filename, ruleName);

            extractAndRegisterShaclRuleNames(model, filename);
            log.debug("Successfully loaded rule name: {} as rule: {}", filename, ruleName);

            log.debug("Loaded rule: {} with {} statements", ruleName, model.size());

        } catch (Exception e) {
            log.error("Failed to load rule file: {}", resource.getFilename(), e);
            if (!config.isContinueOnRuleError()) {
                throw new ValidationConfigurationException("Failed to load rule: " + resource.getFilename(), e);
            }
        }
    }

    private String extractRuleName(String filename) {
        if (filename == null) return "unknown";

        String name = filename.toLowerCase();
        if (name.endsWith(".ttl")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replaceAll("[^a-z0-9]+", "-");
    }

    private Map<String, String> extractRuleMetadata(Model model) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put("statements", String.valueOf(model.size()));

        return metadata;
    }

    public void registerRuleLocality(String ruleName, String fileName) {
        if (fileName == null) {
            log.warn("Cannot determine locality for rule {} - no filename provided", ruleName);
            return;
        }

        boolean isLocal = fileName.toLowerCase().startsWith("local-");
        ruleLocalityMap.put(ruleName, isLocal);

        log.debug("Registered rule '{}' from file '{}' as {}",
                ruleName, fileName, isLocal ? "LOCAL" : "GLOBAL");
    }

    public boolean isLocalRule(String ruleName) {
        if (ruleName == null) {
            return false;
        }

        Boolean locality = ruleLocalityMap.get(ruleName);
        if (locality != null) {
            return locality;
        }

        String normalizedRuleName = normalizeRuleName(ruleName);
        for (Map.Entry<String, Boolean> entry : ruleLocalityMap.entrySet()) {
            String registeredName = normalizeRuleName(entry.getKey());
            if (registeredName.equals(normalizedRuleName)) {
                log.debug("Found rule locality match: {} -> {} ({})",
                        ruleName, entry.getKey(), Boolean.TRUE.equals(entry.getValue()) ? "LOCAL" : "GLOBAL");
                return entry.getValue();
            }
        }

        return false;
    }

    private void extractAndRegisterShaclRuleNames(Model model, String fileName) {
        try {
            String queryString = """
                PREFIX sh: <http://www.w3.org/ns/shacl#>
                PREFIX ex: <http://example.org/shapes#>
               \s
                SELECT DISTINCT ?shape WHERE {
                    ?shape a sh:NodeShape .
                }
           \s""";

            try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
                ResultSet results = qexec.execSelect();

                while (results.hasNext()) {
                    QuerySolution solution = results.nextSolution();
                    Resource shape = solution.getResource("shape");

                    if (shape != null) {
                        String shaclRuleName = extractRuleNameFromResource(shape);
                        if (shaclRuleName != null && !shaclRuleName.isEmpty()) {
                            registerRuleLocality(shaclRuleName, fileName);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract SHACL rule names from model for file '{}': {}",
                    fileName, e.getMessage());
            log.debug("SHACL extraction error details", e);
        }
    }

    private String extractRuleNameFromResource(Resource resource) {
        if (resource == null) {
            return null;
        }

        String uri = resource.getURI();
        if (uri != null) {
            if (uri.contains("#")) {
                return uri.substring(uri.lastIndexOf("#") + 1);
            } else if (uri.contains("/")) {
                return uri.substring(uri.lastIndexOf("/") + 1);
            }
            return uri;
        }

        return resource.toString();
    }

    private String normalizeRuleName(String ruleName) {
        if (ruleName == null) {
            return "";
        }

        return ruleName.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    public Set<String> getLocalRuleNames() {
        return ruleLocalityMap.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    public Set<String> getGlobalRuleNames() {
        return ruleLocalityMap.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    public List<Model> getEnabledRules() {
        return ruleModels.entrySet().stream()
                .filter(entry -> config.isRuleEnabled(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Model getCombinedEnabledRules() {
        Model combinedModel = ModelFactory.createDefaultModel();

        getEnabledRules().forEach(combinedModel::add);

        log.debug("Combined {} enabled rules into model with {} statements",
                getEnabledRuleNames().size(), combinedModel.size());

        return combinedModel;
    }

    public Set<String> getEnabledRuleNames() {
        return ruleModels.keySet().stream()
                .filter(config::isRuleEnabled)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    public Set<String> getAllRuleNames() {
        return new LinkedHashSet<>(allRules.keySet());
    }

    public Optional<RuleDefinition> getRuleDefinition(String ruleName) {
        return Optional.ofNullable(allRules.get(ruleName));
    }

    public Optional<Model> getRuleModel(String ruleName) {
        return Optional.ofNullable(ruleModels.get(ruleName));
    }

    private void logRuleStatus() {
        log.info("Validation rules status:");
        getAllRuleNames().forEach(ruleName -> {
            boolean enabled = config.isRuleEnabled(ruleName);
            RuleDefinition def = allRules.get(ruleName);
            log.info("  {} - {} ({})",
                    ruleName,
                    enabled ? "ENABLED" : "DISABLED",
                    def != null ? def.filename() : "unknown");
        });
    }

    private void logLocalityStatus() {
        log.info("Rule locality mapping:");
        log.info("  Local rules: {}", getLocalRuleNames());
        log.info("  Global rules: {}", getGlobalRuleNames());
        log.info("  Total rules with locality info: {}", ruleLocalityMap.size());
    }
}