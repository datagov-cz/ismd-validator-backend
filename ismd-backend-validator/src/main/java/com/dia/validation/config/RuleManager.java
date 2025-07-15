package com.dia.validation.config;

import com.dia.exceptions.ValidationConfigurationException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
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
    private final ResourceLoader resourceLoader;
    private final Map<String, RuleDefinition> allRules = new ConcurrentHashMap<>();
    private final Map<String, Model> ruleModels = new ConcurrentHashMap<>();

    @Autowired
    public RuleManager(ValidationConfiguration config, ResourceLoader resourceLoader) {
        this.config = config;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Load all SHACL rules from the configured directory
     */
    @PostConstruct
    public void loadRules() {
        log.info("Loading SHACL validation rules from: {}", config.getRulesDirectory());

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = config.getRulesDirectory().replace("classpath:", "classpath*:") + "**/*.ttl";
            Resource[] resources = resolver.getResources(pattern);

            log.info("Found {} rule files", resources.length);

            for (Resource resource : resources) {
                loadRuleFile(resource);
            }

            log.info("Successfully loaded {} validation rules", allRules.size());
            logRuleStatus();

        } catch (IOException e) {
            log.error("Failed to load validation rules", e);
            throw new ValidationConfigurationException("Could not load validation rules", e);
        }
    }

    /**
     * Load a single rule file
     */
    private void loadRuleFile(Resource resource) {
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

            log.debug("Loaded rule: {} with {} statements", ruleName, model.size());

        } catch (Exception e) {
            log.error("Failed to load rule file: {}", resource.getFilename(), e);
            if (!config.isContinueOnRuleError()) {
                throw new ValidationConfigurationException("Failed to load rule: " + resource.getFilename(), e);
            }
        }
    }

    /**
     * Extract rule name from filename
     */
    private String extractRuleName(String filename) {
        if (filename == null) return "unknown";

        String name = filename.toLowerCase();
        if (name.endsWith(".ttl")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replaceAll("[^a-z0-9]+", "-");
    }

    /**
     * Extract metadata from SHACL model (description, severity, etc.)
     */
    private Map<String, String> extractRuleMetadata(Model model) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put("statements", String.valueOf(model.size()));

        return metadata;
    }

    /**
     * Get all enabled rule models
     */
    public List<Model> getEnabledRules() {
        return ruleModels.entrySet().stream()
                .filter(entry -> config.isRuleEnabled(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Get enabled rule models combined into a single model
     */
    public Model getCombinedEnabledRules() {
        Model combinedModel = ModelFactory.createDefaultModel();

        getEnabledRules().forEach(combinedModel::add);

        log.debug("Combined {} enabled rules into model with {} statements",
                getEnabledRuleNames().size(), combinedModel.size());

        return combinedModel;
    }

    /**
     * Get names of all enabled rules
     */
    public Set<String> getEnabledRuleNames() {
        return ruleModels.keySet().stream()
                .filter(config::isRuleEnabled)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * Get names of all available rules
     */
    public Set<String> getAllRuleNames() {
        return new LinkedHashSet<>(allRules.keySet());
    }

    /**
     * Get rule definition by name
     */
    public Optional<RuleDefinition> getRuleDefinition(String ruleName) {
        return Optional.ofNullable(allRules.get(ruleName));
    }

    /**
     * Get rule model by name
     */
    public Optional<Model> getRuleModel(String ruleName) {
        return Optional.ofNullable(ruleModels.get(ruleName));
    }

    /**
     * Reload all rules
     */
    public void reloadRules() {
        log.info("Reloading validation rules");
        allRules.clear();
        ruleModels.clear();
        loadRules();
    }

    /**
     * Log current rule status
     */
    private void logRuleStatus() {
        log.info("Validation rules status:");
        getAllRuleNames().forEach(ruleName -> {
            boolean enabled = config.isRuleEnabled(ruleName);
            RuleDefinition def = allRules.get(ruleName);
            log.info("  {} - {} ({})",
                    ruleName,
                    enabled ? "ENABLED" : "DISABLED",
                    def != null ? def.getFilename() : "unknown");
        });
    }
}
