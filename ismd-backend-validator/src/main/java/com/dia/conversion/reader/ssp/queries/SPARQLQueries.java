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
        """;

    public static final String MODEL_TYPES_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
        
        SELECT DISTINCT ?concept ?type
        WHERE {
            GRAPH <%s/model> {
                ?concept a owl:Class .
                ?concept a ?type .
                VALUES ?type { 
                    z-sgov-pojem:typ-objektu 
                    z-sgov-pojem:typ-vlastnosti 
                    z-sgov-pojem:typ-vztahu 
                }
            }
        }
        ORDER BY ?concept
        """;

    public static final String RELATIONSHIP_RESTRICTIONS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?relationship ?property ?targetClass ?restrictionType
        WHERE {
            GRAPH <%s/model> {
                ?relationship a z-sgov:typ-vztahu, owl:Class .
                ?relationship rdfs:subClassOf ?restriction .
                ?restriction a owl:Restriction .
                ?restriction owl:onProperty ?property .
                ?restriction owl:onClass ?targetClass .
               \s
                # Get the restriction type (cardinality, allValuesFrom, etc.)
                OPTIONAL { ?restriction owl:qualifiedCardinality ?card . BIND("qualifiedCardinality" AS ?restrictionType) }
                OPTIONAL { ?restriction owl:minQualifiedCardinality ?minCard . BIND("minQualifiedCardinality" AS ?restrictionType) }
                OPTIONAL { ?restriction owl:maxQualifiedCardinality ?maxCard . BIND("maxQualifiedCardinality" AS ?restrictionType) }
                OPTIONAL { ?restriction owl:allValuesFrom ?allValues . BIND("allValuesFrom" AS ?restrictionType) }
                OPTIONAL { ?restriction owl:someValuesFrom ?someValues . BIND("someValuesFrom" AS ?restrictionType) }
               \s
                # Filter for the standard relationship properties
                FILTER(
                    ?property = z-sgov:má-vztažený-prvek-1 ||
                    ?property = z-sgov:má-vztažený-prvek-2 ||
                    STRSTARTS(STR(?property), "https://slovník.gov.cz/základní/má-vztažený-prvek")
                )
            }
        }
        ORDER BY ?relationship ?property
       \s""";

    public static final String INVERSE_RELATIONSHIPS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?sourceClass ?relationship ?targetClass ?inverseProperty
        WHERE {
            GRAPH <%s/model> {
                ?sourceClass a owl:Class .
                ?sourceClass rdfs:subClassOf ?restriction .
                ?restriction a owl:Restriction .
                ?restriction owl:onProperty ?invProp .
                ?restriction owl:onClass ?relationship .
               \s
                # Look for inverse property patterns
                ?invProp owl:inverseOf ?inverseProperty .
               \s
                # The relationship should be a typ-vztahu
                ?relationship a z-sgov:typ-vztahu, owl:Class .
               \s
                FILTER(
                    ?inverseProperty = z-sgov:má-vztažený-prvek-1 ||
                    ?inverseProperty = z-sgov:má-vztažený-prvek-2 ||
                    STRSTARTS(STR(?inverseProperty), "https://slovník.gov.cz/základní/má-vztažený-prvek")
                )
            }
        }
        ORDER BY ?relationship ?sourceClass
       \s""";

    public static final String RELATIONSHIP_ELEMENTS_QUERY = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
   \s
    SELECT DISTINCT ?relationship ?element1 ?element2
    WHERE {
        GRAPH <%s/model> {
            # Get the relationship
            ?relationship a z-sgov-pojem:typ-vztahu, owl:Class .
           \s
            # Find element 1 using allValuesFrom or someValuesFrom
            OPTIONAL {
                ?relationship rdfs:subClassOf ?rest1 .
                ?rest1 a owl:Restriction .
                ?rest1 owl:onProperty z-sgov-pojem:má-vztažený-prvek-1 .
                {
                    ?rest1 owl:allValuesFrom ?element1 .
                } UNION {
                    ?rest1 owl:someValuesFrom ?element1 .
                }
            }
           \s
            # Find element 2 using allValuesFrom or someValuesFrom \s
            OPTIONAL {
                ?relationship rdfs:subClassOf ?rest2 .
                ?rest2 a owl:Restriction .
                ?rest2 owl:onProperty z-sgov-pojem:má-vztažený-prvek-2 .
                {
                    ?rest2 owl:allValuesFrom ?element2 .
                } UNION {
                    ?rest2 owl:someValuesFrom ?element2 .
                }
            }
           \s
            # Only return relationships that have at least one element
            FILTER(BOUND(?element1) || BOUND(?element2))
        }
    }
    ORDER BY ?relationship
   \s""";

    public static final String RELATIONSHIP_ELEMENTS_SIMPLE_QUERY = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
   \s
    SELECT DISTINCT ?relationship ?property ?targetClass
    WHERE {
        GRAPH <%s/model> {
            ?relationship a z-sgov-pojem:typ-vztahu, owl:Class .
            ?relationship rdfs:subClassOf ?restriction .
            ?restriction a owl:Restriction .
            ?restriction owl:onProperty ?property .
           \s
            # Get target class from allValuesFrom or someValuesFrom
            {
                ?restriction owl:allValuesFrom ?targetClass .
            } UNION {
                ?restriction owl:someValuesFrom ?targetClass .
            }
           \s
            # Filter for relationship properties
            FILTER(
                ?property = z-sgov-pojem:má-vztažený-prvek-1 ||
                ?property = z-sgov-pojem:má-vztažený-prvek-2
            )
        }
    }
    ORDER BY ?relationship ?property
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
        ORDER BY ?concept
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
        ORDER BY ?subClass ?superClass
        """;

    // TEST QUERIES
    public static final String TEST_ONTOLOGY_EXISTS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        
        SELECT DISTINCT ?ontology ?type
        WHERE {
            ?ontology a owl:Ontology .
            ?ontology a ?type .
            FILTER(STRSTARTS(STR(?ontology), "%s"))
        }
        LIMIT 10
        """;

    public static final String TEST_GRAPHS_QUERY = """
        SELECT DISTINCT ?graph
        WHERE {
            GRAPH ?graph { ?s ?p ?o }
            FILTER(STRSTARTS(STR(?graph), "%s"))
        }
        LIMIT 10
        """;

    // DEBUG QUERIES
    public static final String DEBUG_RELATIONSHIP_RESTRICTIONS = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
   \s
    SELECT DISTINCT ?relationship ?restriction ?property ?onProperty ?onClass ?value
    WHERE {
        GRAPH <%s/model> {
            ?relationship a z-sgov-pojem:typ-vztahu, owl:Class .
            ?relationship rdfs:subClassOf ?restriction .
            ?restriction a owl:Restriction .
           \s
            # Get all restriction properties
            ?restriction ?property ?value .
           \s
            # Specifically look for common OWL restriction properties
            OPTIONAL { ?restriction owl:onProperty ?onProperty }
            OPTIONAL { ?restriction owl:onClass ?onClass }
           \s
            # Filter to show only OWL restriction-related properties
            FILTER(
                ?property = owl:onProperty ||
                ?property = owl:onClass ||
                ?property = owl:qualifiedCardinality ||
                ?property = owl:minQualifiedCardinality ||
                ?property = owl:maxQualifiedCardinality ||
                ?property = owl:allValuesFrom ||
                ?property = owl:someValuesFrom ||
                STRSTARTS(STR(?property), "http://www.w3.org/2002/07/owl#")
            )
        }
    }
    ORDER BY ?relationship ?restriction ?property
    LIMIT 50
   \s""";

    public static final String DEBUG_RELATIONSHIP_SUBCLASSOF = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
    
    SELECT DISTINCT ?relationship ?superClass
    WHERE {
        GRAPH <%s/model> {
            ?relationship a z-sgov-pojem:typ-vztahu, owl:Class .
            ?relationship rdfs:subClassOf ?superClass .
        }
    }
    ORDER BY ?relationship
    LIMIT 20
    """;

    public static final String DEBUG_RESTRICTION_PROPERTIES = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX z-sgov: <https://slovník.gov.cz/základní/>
    
    SELECT DISTINCT ?property (COUNT(?property) as ?count)
    WHERE {
        GRAPH <%s/model> {
            ?restriction a owl:Restriction .
            ?restriction owl:onProperty ?property .
        }
    }
    GROUP BY ?property
    ORDER BY DESC(?count)
    LIMIT 20
    """;

    public static final String DEBUG_INVERSE_PROPERTIES = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    
    SELECT DISTINCT ?prop1 ?prop2
    WHERE {
        GRAPH <%s/model> {
            ?prop1 owl:inverseOf ?prop2 .
        }
    }
    LIMIT 20
    """;

    private SPARQLQueries() {}
}