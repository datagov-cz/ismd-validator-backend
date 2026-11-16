package com.dia.ofn;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OfnTermsTest {

    @Test
    void everyTermTheBuilderWrites_isDefinedInTheBundledContext() throws IllegalAccessException {
        Set<String> contextTerms = OfnSpecification.bundled().contextIndex().allTerms().values().stream()
                .map(OfnContextIndex.Term::name)
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (Field field : OfnTerms.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                String term = (String) field.get(null);
                if (!contextTerms.contains(term)) {
                    missing.add(field.getName() + " = \"" + term + "\"");
                }
            }
        }

        assertThat(missing).as("OfnTerms constants not defined in kontext.jsonld").isEmpty();
    }
}
