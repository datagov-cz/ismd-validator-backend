package com.dia.conversion.data;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class VocabularyMetadata {
    private String name;
    private String description;
    private String namespace;
    private String dateOfCreation;
    private String dateOfModification;
}
