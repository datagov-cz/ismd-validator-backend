package com.dia.converter.data;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class OntologyData {
    private final VocabularyMetadata vocabularyMetadata;
    private final List<ClassData> classes;
    private final List<PropertyData> properties;
    private final List<RelationshipData> relationships;

    private OntologyData(Builder builder) {
        this.vocabularyMetadata = builder.vocabularyMetadata;
        this.classes = List.copyOf(builder.classes);
        this.properties = List.copyOf(builder.properties);
        this.relationships = List.copyOf(builder.relationships);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VocabularyMetadata vocabularyMetadata;
        private List<ClassData> classes = new ArrayList<>();
        private List<PropertyData> properties = new ArrayList<>();
        private List<RelationshipData> relationships = new ArrayList<>();

        public Builder vocabularyMetadata(VocabularyMetadata vocabularyMetadata) {
            this.vocabularyMetadata = vocabularyMetadata;
            return this;
        }

        public Builder classes(List<ClassData> classes) {
            this.classes = classes != null ? classes : new ArrayList<>();
            return this;
        }

        public Builder properties(List<PropertyData> properties) {
            this.properties = properties != null ? properties : new ArrayList<>();
            return this;
        }

        public Builder relationships(List<RelationshipData> relationships) {
            this.relationships = relationships != null ? relationships : new ArrayList<>();
            return this;
        }

        public OntologyData build() {
            return new OntologyData(this);
        }
    }
}
