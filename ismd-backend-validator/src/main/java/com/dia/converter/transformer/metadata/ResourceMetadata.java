package com.dia.converter.transformer.metadata;

import com.dia.converter.data.ClassData;
import com.dia.converter.data.PropertyData;
import com.dia.converter.data.RelationshipData;

public record ResourceMetadata(
        String name,
        String description,
        String definition,
        String source,
        String relatedSource,
        String identifier
) {
    public static ResourceMetadata from(ClassData classData) {
        return new ResourceMetadata(
                classData.getName(),
                classData.getDescription(),
                classData.getDefinition(),
                classData.getSource(),
                classData.getRelatedSource(),
                classData.getIdentifier()
        );
    }

    public static ResourceMetadata from(PropertyData propertyData) {
        return new ResourceMetadata(
                propertyData.getName(),
                propertyData.getDescription(),
                propertyData.getDefinition(),
                propertyData.getSource(),
                propertyData.getRelatedSource(),
                propertyData.getIdentifier()
        );
    }

    public static ResourceMetadata from(RelationshipData relationshipData) {
        return new ResourceMetadata(
                relationshipData.getName(),
                relationshipData.getDescription(),
                relationshipData.getDefinition(),
                relationshipData.getSource(),
                relationshipData.getRelatedSource(),
                relationshipData.getIdentifier()
        );
    }
}