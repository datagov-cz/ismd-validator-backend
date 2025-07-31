package com.dia.conversion.reader.ssp.queries;

public class SPARQLQueries {

    public static final String VOCABULARY_METADATA_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX dcterms: <http://purl.org/dc/terms/>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
        
        SELECT DISTINCT ?vocabulary ?title ?description ?created ?modified
        WHERE {
            BIND(<%s> AS ?vocabulary)
            ?vocabulary a owl:Ontology, z-sgov:slovník .
            OPTIONAL { ?vocabulary dcterms:title ?title }
            OPTIONAL { ?vocabulary dcterms:description ?description }
            OPTIONAL { ?vocabulary dcterms:created ?created }
            OPTIONAL { ?vocabulary dcterms:modified ?modified }
        }
        """;

    public static final String VOCABULARY_CONCEPTS_QUERY = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX dcterms: <http://purl.org/dc/terms/>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
        
        SELECT DISTINCT ?concept ?prefLabel ?altLabel ?definition ?source ?scopeNote ?broader
        WHERE {
            ?concept a skos:Concept ;
                     skos:inScheme <%s/glosář> .
            OPTIONAL { ?concept skos:prefLabel ?prefLabel FILTER(lang(?prefLabel) = "cs") }
            OPTIONAL { ?concept skos:altLabel ?altLabel FILTER(lang(?altLabel) = "cs") }
            OPTIONAL { ?concept skos:definition ?definition FILTER(lang(?definition) = "cs") }
            OPTIONAL { ?concept dcterms:source ?source }
            OPTIONAL { ?concept skos:scopeNote ?scopeNote FILTER(lang(?scopeNote) = "cs") }
            OPTIONAL { ?concept skos:broader ?broader }
        }
        ORDER BY ?concept
        """;

    public static final String MODEL_TYPES_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
        
        SELECT DISTINCT ?concept ?type
        WHERE {
            ?concept a owl:Class .
            VALUES ?type { 
                z-sgov:typ-objektu 
                z-sgov:typ-vlastnosti 
                z-sgov:typ-vztahu 
            }
            ?concept a ?type .
            FILTER(STRSTARTS(STR(?concept), "%s"))
        }
        """;

    // Simplified domain/range queries for OFN model
    public static final String DOMAIN_RANGE_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT DISTINCT ?concept ?domain ?range
        WHERE {
            ?concept a owl:Class .
            OPTIONAL { ?concept rdfs:domain ?domain }
            OPTIONAL { ?concept rdfs:range ?range }
            FILTER(STRSTARTS(STR(?concept), "%s"))
        }
        """;

    public static final String HIERARCHY_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        
        SELECT DISTINCT ?subClass ?superClass
        WHERE {
            ?subClass rdfs:subClassOf ?superClass .
            ?subClass a owl:Class .
            ?superClass a owl:Class .
            FILTER(!isBlank(?superClass))
            FILTER(STRSTARTS(STR(?subClass), "%s"))
            FILTER(STRSTARTS(STR(?superClass), "%s"))
        }
        """;

    private SPARQLQueries() {}
}
