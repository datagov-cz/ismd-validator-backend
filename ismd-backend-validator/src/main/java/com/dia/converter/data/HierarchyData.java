package com.dia.converter.data;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class HierarchyData {
    private String childClass;
    private String parentClass;
    private String relationshipName;
    private String identifier;

    private String description;
    private String definition;
    private String source;
    private String relatedSource;
    private String alternativeName;

    public HierarchyData(String childClass, String parentClass) {
        this.childClass = childClass;
        this.parentClass = parentClass;
    }

    public HierarchyData(String childClass, String parentClass, String relationshipName) {
        this.childClass = childClass;
        this.parentClass = parentClass;
        this.relationshipName = relationshipName;
    }

    public boolean hasValidData() {
        return childClass != null && !childClass.trim().isEmpty() &&
                parentClass != null && !parentClass.trim().isEmpty() &&
                !childClass.equals(parentClass); // Prevent self-inheritance
    }

    public boolean isCircularWith(HierarchyData other) {
        if (other == null) return false;
        return this.childClass.equals(other.parentClass) &&
                this.parentClass.equals(other.childClass);
    }

    public HierarchyData reverse() {
        HierarchyData reversed = new HierarchyData(this.parentClass, this.childClass);
        reversed.setRelationshipName(this.relationshipName != null ? this.relationshipName + "-reverse" : null);
        reversed.setDescription(this.description);
        reversed.setDefinition(this.definition);
        reversed.setSource(this.source);
        reversed.setRelatedSource(this.relatedSource);
        reversed.setAlternativeName(this.alternativeName);
        reversed.setIdentifier(this.identifier != null ? this.identifier + "-reverse" : null);
        return reversed;
    }

    public String getRelationshipString() {
        return childClass + " → " + parentClass;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("HierarchyData{");
        sb.append("child='").append(childClass).append('\'');
        sb.append(", parent='").append(parentClass).append('\'');
        if (relationshipName != null) {
            sb.append(", name='").append(relationshipName).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HierarchyData that = (HierarchyData) o;

        if (!Objects.equals(childClass, that.childClass)) return false;
        return Objects.equals(parentClass, that.parentClass);
    }

    @Override
    public int hashCode() {
        int result = childClass != null ? childClass.hashCode() : 0;
        result = 31 * result + (parentClass != null ? parentClass.hashCode() : 0);
        return result;
    }
}
