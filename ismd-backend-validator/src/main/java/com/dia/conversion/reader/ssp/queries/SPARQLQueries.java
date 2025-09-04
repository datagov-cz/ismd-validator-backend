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

    public static final String VOCABULARY_CONCEPTS_FALLBACK_QUERY = """
        PREFIX dc: <http://purl.org/dc/terms/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        
        SELECT DISTINCT ?concept ?prefLabel ?altLabel ?definition ?scopeNote ?source ?broader
        WHERE {
            ?concept a skos:Concept .
            FILTER(STRSTARTS(STR(?concept), "%s"))
            OPTIONAL { ?concept skos:prefLabel ?prefLabel }
            OPTIONAL { ?concept skos:altLabel ?altLabel }
            OPTIONAL { ?concept skos:definition ?definition }
            OPTIONAL { ?concept skos:scopeNote ?scopeNote }
            OPTIONAL { ?concept skos:broader ?broader }
            OPTIONAL { ?concept dc:source ?source }
        }
        ORDER BY ?concept
        """;

    public static final String MODEL_TYPES_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        
        SELECT DISTINCT ?concept ?type
        WHERE {
            GRAPH <%s/model> {
                ?concept rdf:type ?type .
                VALUES ?type { 
                    z-sgov-pojem:typ-objektu 
                    z-sgov-pojem:typ-vlastnosti 
                    z-sgov-pojem:typ-vztahu 
                }
            }
        }
        ORDER BY ?concept
        """;

    public static final String MODEL_TYPES_FALLBACK_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        
        SELECT DISTINCT ?concept ?type
        WHERE {
            ?concept rdf:type ?type .
            FILTER(STRSTARTS(STR(?concept), "%s"))
            VALUES ?type { 
                z-sgov-pojem:typ-objektu 
                z-sgov-pojem:typ-vlastnosti 
                z-sgov-pojem:typ-vztahu 
            }
        }
        ORDER BY ?concept
        """;

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

    public static final String DOMAIN_RANGE_FALLBACK_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT DISTINCT ?concept ?domain ?range
        WHERE {
            ?concept a owl:Class .
            FILTER(STRSTARTS(STR(?concept), "%s"))
            OPTIONAL { ?concept rdfs:domain ?domain }
            OPTIONAL { ?concept rdfs:range ?range }
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

    public static final String HIERARCHY_FALLBACK_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        
        SELECT DISTINCT ?subClass ?superClass
        WHERE {
            ?subClass rdfs:subClassOf ?superClass .
            ?subClass a owl:Class .
            ?superClass a owl:Class .
            FILTER(STRSTARTS(STR(?subClass), "%s"))
            FILTER(!isBlank(?superClass))
        }
        ORDER BY ?subClass ?superClass
        """;

    public static final String RELATIONSHIP_ELEMENTS_FALLBACK_QUERY = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
   \s
    SELECT DISTINCT ?relationship ?property ?targetClass
    WHERE {
        ?relationship a z-sgov-pojem:typ-vztahu, owl:Class .
        FILTER(STRSTARTS(STR(?relationship), "%s"))
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
    ORDER BY ?relationship ?property
   \s""";

    private SPARQLQueries() {}
}