package com.dia.conversion.data;

import lombok.*;

@Getter
@Setter
@Builder
public class VocabularyMetadata {
    private String name;
    private String description;
    private String namespace;
}
