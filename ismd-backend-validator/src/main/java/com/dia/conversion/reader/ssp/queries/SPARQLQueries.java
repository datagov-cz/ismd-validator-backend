package com.dia.conversion.reader.ssp.queries;

public class SPARQLQueries {
    
    public static final String SGOV_OWNED_CONCEPTS_ONLY_QUERY = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX dcterms: <http://purl.org/dc/terms/>
       \s
        SELECT DISTINCT ?concept ?prefLabel ?definition ?altLabel ?source ?scopeNote ?broader ?thesaurus WHERE {
            # Find the collection of owned concepts
            ?thesaurus a skos:ConceptScheme ;
                       dcterms:hasPart ?ownedCollection .
            ?ownedCollection a skos:Collection ;
                             skos:member ?concept .
           \s
            ?concept a skos:Concept ;
                     skos:inScheme ?thesaurus .
           \s
            FILTER(STRSTARTS(STR(?concept), "%s"))
           \s
            OPTIONAL { ?concept skos:prefLabel ?prefLabel }
            OPTIONAL { ?concept skos:definition ?definition }
            OPTIONAL { ?concept skos:altLabel ?altLabel }
            OPTIONAL { ?concept dcterms:source ?source }
            OPTIONAL { ?concept skos:scopeNote ?scopeNote }
            OPTIONAL { ?concept skos:broader ?broader }
        }
        ORDER BY ?concept
       \s""";
    
    public static final String SGOV_ALL_CONCEPTS_WITH_OWNERSHIP_QUERY = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX dcterms: <http://purl.org/dc/terms/>
       \s
        SELECT DISTINCT ?concept ?prefLabel ?definition ?isOwned ?thesaurus WHERE {
            ?concept a skos:Concept ;
                     skos:inScheme ?thesaurus .
            ?thesaurus a skos:ConceptScheme .
           \s
            FILTER(STRSTARTS(STR(?concept), "%s"))
           \s
            OPTIONAL { ?concept skos:prefLabel ?prefLabel }
            OPTIONAL { ?concept skos:definition ?definition }
           \s
            BIND(EXISTS {
                ?thesaurus dcterms:hasPart ?ownedCollection .
                ?ownedCollection skos:member ?concept
            } AS ?isOwned)
        }
        ORDER BY ?concept
       \s""";
    
    public static final String SGOV_MODEL_ELEMENTS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?element ?type ?label ?subClassOf WHERE {
            ?model a z-sgov:model ;
                   a owl:Ontology .
            FILTER(STRSTARTS(STR(?model), "%s"))
           \s
            ?element a ?type .
            VALUES ?type {\s
                z-sgov:typ-objektu\s
                z-sgov:typ-vlastnosti\s
                z-sgov:typ-vztahu\s
                z-sgov:typ-události\s
            }
           \s
            FILTER(STRSTARTS(STR(?element), "%s"))
           \s
            OPTIONAL { ?element rdfs:label ?label }
            OPTIONAL { ?element rdfs:subClassOf ?subClassOf }
        }
        ORDER BY ?element
       \s""";
    
    public static final String SGOV_COMPREHENSIVE_CONCEPTS_QUERY = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX dcterms: <http://purl.org/dc/terms/>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?concept ?prefLabel ?definition ?altLabel ?source ?scopeNote\s
               ?broader ?narrower ?related ?thesaurus ?isOwned\s
               ?modelType ?subClassOf WHERE {
           \s
            ?concept a skos:Concept ;
                     skos:inScheme ?thesaurus .
           \s
            FILTER(STRSTARTS(STR(?concept), "%s"))
           \s
            OPTIONAL { ?concept skos:prefLabel ?prefLabel }
            OPTIONAL { ?concept skos:definition ?definition }
            OPTIONAL { ?concept skos:altLabel ?altLabel }
            OPTIONAL { ?concept dcterms:source ?source }
            OPTIONAL { ?concept skos:scopeNote ?scopeNote }
            OPTIONAL { ?concept skos:broader ?broader }
            OPTIONAL { ?concept skos:narrower ?narrower }
            OPTIONAL { ?concept skos:related ?related }
           \s
            BIND(EXISTS {
                ?thesaurus dcterms:hasPart ?ownedCollection .
                ?ownedCollection skos:member ?concept
            } AS ?isOwned)
           \s
            OPTIONAL {\s
                ?concept a ?modelType .
                VALUES ?modelType {\s
                    z-sgov:typ-objektu\s
                    z-sgov:typ-vlastnosti\s
                    z-sgov:typ-vztahu\s
                    z-sgov:typ-události\s
                }
            }
           \s
            OPTIONAL { ?concept rdfs:subClassOf ?subClassOf }
        }
        ORDER BY ?concept
       \s""";

    public static final String SIMPLE_VOCABULARY_CONCEPTS_QUERY = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX dcterms: <http://purl.org/dc/terms/>
       \s
        SELECT DISTINCT ?concept ?prefLabel ?definition ?altLabel ?source ?scopeNote ?broader WHERE {
            ?concept a skos:Concept .
            FILTER(STRSTARTS(STR(?concept), "%s/pojem/"))
           \s
            OPTIONAL { ?concept skos:prefLabel ?prefLabel }
            OPTIONAL { ?concept skos:definition ?definition }
            OPTIONAL { ?concept skos:altLabel ?altLabel }
            OPTIONAL { ?concept dcterms:source ?source }
            OPTIONAL { ?concept skos:scopeNote ?scopeNote }
            OPTIONAL { ?concept skos:broader ?broader }
        }
        ORDER BY ?concept
       \s""";

    public static final String SGOV_SIMPLE_DOMAIN_RANGE_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
       \s
        SELECT DISTINCT ?relationship ?domain ?range WHERE {
            ?relationship a <https://slovník.gov.cz/základní/pojem/typ-vztahu> .
            FILTER(STRSTARTS(STR(?relationship), "%s"))
           \s
            # Try standard RDF patterns first
            OPTIONAL { ?relationship rdfs:domain ?domain }
            OPTIONAL { ?relationship rdfs:range ?range }
           \s
            # Also try on the ObjectProperty if it exists
            OPTIONAL {
                ?relationship a owl:ObjectProperty .
                OPTIONAL { ?relationship rdfs:domain ?domain }
                OPTIONAL { ?relationship rdfs:range ?range }
            }
           \s
            # Try SGoV-specific OWL restrictions pattern
            OPTIONAL {
                ?relationship rdfs:subClassOf ?restriction1 .
                ?restriction1 a owl:Restriction ;
                              owl:onProperty <https://slovník.gov.cz/základní/pojem/má-vztažený-prvek-1> ;
                              owl:onClass ?domain .
            }
            OPTIONAL {
                ?relationship rdfs:subClassOf ?restriction2 .
                ?restriction2 a owl:Restriction ;
                              owl:onProperty <https://slovník.gov.cz/základní/pojem/má-vztažený-prvek-2> ;
                              owl:onClass ?range .
            }
        }
        ORDER BY ?relationship
       \s""";

    public static final String SGOV_PROPERTY_DOMAIN_QUERY = """
        SELECT ?property ?propertyLabel ?domain ?domainLabel WHERE {
            # No properties found in this vocabulary, return empty results
            ?property a <http://nonexistent.example/type> .
        }
        """;

    public static final String EXPLORE_RELATIONSHIP_RESTRICTIONS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?relationship ?restriction ?property ?class WHERE {
            ?relationship a z-sgov:typ-vztahu .
            FILTER(STRSTARTS(STR(?relationship), "%s"))
           \s
            ?relationship rdfs:subClassOf ?restriction .
            ?restriction a owl:Restriction ;
                        owl:onProperty ?property ;
                        owl:onClass ?class .
        }
        ORDER BY ?relationship
       \s""";

    public static final String EXPLORE_PROPERTY_RESTRICTIONS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?property ?restriction ?onProperty ?class WHERE {
            ?property a z-sgov:typ-vlastnosti .
            FILTER(STRSTARTS(STR(?property), "%s"))
           \s
            ?property rdfs:subClassOf ?restriction .
            ?restriction a owl:Restriction ;
                        owl:onProperty ?onProperty ;
                        owl:onClass ?class .
        }
        ORDER BY ?property
       \s""";

    public static final String EXPLORE_DIRECT_DOMAIN_RANGE_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT DISTINCT ?concept ?type ?domain ?range WHERE {
            ?concept a ?type .
            VALUES ?type { z-sgov:typ-vlastnosti z-sgov:typ-vztahu }
            FILTER(STRSTARTS(STR(?concept), "%s"))
           \s
            OPTIONAL { ?concept rdfs:domain ?domain }
            OPTIONAL { ?concept rdfs:range ?range }
        }
        ORDER BY ?concept
       \s""";

    public static final String SGOV_OWL_SPECIALIZATION_HIERARCHY_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX z-sgov: <https://slovník.gov.cz/základní/>
       \s
        SELECT ?child ?childLabel ?parent ?parentLabel ?elementType WHERE {
            ?child rdfs:subClassOf ?parent .
           \s
            ?child a ?elementType .
            VALUES ?elementType {
                z-sgov:typ-objektu
                z-sgov:typ-vlastnosti \s
                z-sgov:typ-vztahu
                z-sgov:typ-události
                <https://slovník.gov.cz/základní/pojem/typ-objektu>
                <https://slovník.gov.cz/základní/pojem/typ-vlastnosti>
                <https://slovník.gov.cz/základní/pojem/typ-vztahu>
                <https://slovník.gov.cz/základní/pojem/typ-události>
            }
           \s
            FILTER(!isBlank(?parent))
           \s
            FILTER(
                EXISTS { ?parent a ?elementType } ||
                STRSTARTS(STR(?parent), "https://slovník.gov.cz/základní/pojem/")
            )
           \s
            FILTER(STRSTARTS(STR(?child), "%s"))
           \s
            OPTIONAL { ?child skos:prefLabel ?childLabel }
            OPTIONAL { ?parent skos:prefLabel ?parentLabel }
        }
        ORDER BY ?elementType ?parentLabel ?childLabel
       \s""";

    private SPARQLQueries() {}
}