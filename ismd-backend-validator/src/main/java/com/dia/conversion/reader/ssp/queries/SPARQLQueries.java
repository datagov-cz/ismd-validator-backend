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
    
    public static final String SGOV_MODEL_ELEMENTS_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       \s
        SELECT DISTINCT ?element ?type ?label ?subClassOf WHERE {
            ?element a ?type .
            VALUES ?type {
                <https://slovník.gov.cz/základní/pojem/typ-objektu>
                <https://slovník.gov.cz/základní/pojem/typ-vlastnosti>
                <https://slovník.gov.cz/základní/pojem/typ-vztahu>
                <https://slovník.gov.cz/základní/pojem/typ-události>
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
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
       \s
        SELECT DISTINCT ?property ?propertyLabel ?domain ?domainLabel WHERE {
            ?property a <https://slovník.gov.cz/základní/pojem/typ-vlastnosti> .
            FILTER(STRSTARTS(STR(?property), "%s"))
           \s
            OPTIONAL { ?property skos:prefLabel ?propertyLabel }
           \s
            # SGoV encodes property domains via OWL restrictions on rdfs:subClassOf.
            # The onProperty is a blank node (inverse property expression), so we match
            # only on owl:allValuesFrom which consistently holds the domain class.
            OPTIONAL {
                ?property rdfs:subClassOf ?restriction .
                ?restriction a owl:Restriction ;
                             owl:allValuesFrom ?domain .
                FILTER(isIRI(?domain))
            }
           \s
            OPTIONAL { ?domain skos:prefLabel ?domainLabel }
        }
        ORDER BY ?property
       \s""";

    public static final String SGOV_OWL_SPECIALIZATION_HIERARCHY_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
       \s
        SELECT DISTINCT ?child ?childLabel ?parent ?parentLabel WHERE {
            ?child rdfs:subClassOf ?parent .
           \s
            ?child a ?elementType .
            VALUES ?elementType {
                <https://slovník.gov.cz/základní/pojem/typ-objektu>
                <https://slovník.gov.cz/základní/pojem/typ-vlastnosti>
                <https://slovník.gov.cz/základní/pojem/typ-vztahu>
                <https://slovník.gov.cz/základní/pojem/typ-události>
            }
           \s
            FILTER(!isBlank(?parent))
            FILTER(isIRI(?parent))
           \s
            FILTER(STRSTARTS(STR(?child), "%s"))
           \s
            OPTIONAL { ?child skos:prefLabel ?childLabel . FILTER(LANG(?childLabel) = "cs") }
            OPTIONAL { ?parent skos:prefLabel ?parentLabel . FILTER(LANG(?parentLabel) = "cs") }
        }
        ORDER BY ?childLabel
       \s""";

    private SPARQLQueries() {}
}