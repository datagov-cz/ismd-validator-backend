package com.dia.conversion.reader.ssp.queries;

public class SPARQLQueries {

    public static final String VOCABULARY_METADATA_QUERY = """
        PREFIX a-popis-dat-pojem: <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/>
        PREFIX dcterms: <http://purl.org/dc/terms/>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        
        SELECT DISTINCT ?vocabulary ?title ?description ?created ?modified
        WHERE {
            BIND(<%s> AS ?vocabulary)
            ?vocabulary a owl:Ontology .
            OPTIONAL { ?vocabulary a a-popis-dat-pojem:slovník }
            OPTIONAL { ?vocabulary dcterms:title ?title }
            OPTIONAL { ?vocabulary dcterms:description ?description }
            OPTIONAL { ?vocabulary dcterms:created ?created }
            OPTIONAL { ?vocabulary dcterms:modified ?modified }
        }
        """;

    public static final String VOCABULARY_CONCEPTS_QUERY = """
        PREFIX dc: <http://purl.org/dc/terms/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        
        SELECT DISTINCT ?concept ?prefLabel ?altLabel ?definition ?scopeNote ?source ?broader
        WHERE {
            GRAPH <%s/glosář> {
                ?concept a skos:Concept .
                OPTIONAL { ?concept skos:prefLabel ?prefLabel }
                OPTIONAL { ?concept skos:altLabel ?altLabel }
                OPTIONAL { ?concept skos:definition ?definition }
                OPTIONAL { ?concept skos:scopeNote ?scopeNote }
                OPTIONAL { ?concept skos:broader ?broader }
                OPTIONAL { ?concept dc:source ?source }
            }
        }
        ORDER BY ?concept
        LIMIT 10
        """;

    public static final String MODEL_TYPES_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
       \s
        SELECT DISTINCT ?concept ?type
        WHERE {
            GRAPH <%s/model> {
                ?concept a owl:Class .
                ?concept a ?type .
                VALUES ?type {\s
                    z-sgov-pojem:typ-objektu\s
                    z-sgov-pojem:typ-vlastnosti\s
                    z-sgov-pojem:typ-vztahu\s
                }
            }
        }
        LIMIT 10
       \s""";

    public static final String DOMAIN_RANGE_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT DISTINCT ?concept ?domain ?range
        WHERE {
            GRAPH <%s/model> {
                ?concept a owl:Class .
                OPTIONAL { ?concept rdfs:domain ?domain }
                OPTIONAL { ?concept rdfs:range ?range }
            }
        }
        LIMIT 10
        """;

    public static final String HIERARCHY_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        
        SELECT DISTINCT ?subClass ?superClass
        WHERE {
            GRAPH <%s/model> {
                ?subClass rdfs:subClassOf ?superClass .
                ?subClass a owl:Class .
                ?superClass a owl:Class .
                FILTER(!isBlank(?superClass))
            }
        }
        LIMIT 10
        """;

    private SPARQLQueries() {}
}