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

    private SPARQLQueries() {}
}