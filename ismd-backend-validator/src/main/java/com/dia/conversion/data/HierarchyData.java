package com.dia.conversion.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HierarchyData {
    private String subClass;
    private String superClass;
    private String relationshipId;
    private String relationshipName;

    private String description;
    private String definition;
    private String source;

    public HierarchyData(String subClass, String superClass, String relationshipId, String relationshipName) {
        this.subClass = subClass;
        this.superClass = superClass;
        this.relationshipId = relationshipId;
        this.relationshipName = relationshipName;
    }

    public boolean hasValidData() {
        return subClass != null && !subClass.trim().isEmpty() &&
                superClass != null && !superClass.trim().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("HierarchyData{subClass='%s', superClass='%s', relationshipName='%s'}",
                subClass, superClass, relationshipName);
    }
}
