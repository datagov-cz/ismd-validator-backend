package com.dia.conversion.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VocabularyMetadata {
    private String name;
    private String description;
    private String namespace;
}
