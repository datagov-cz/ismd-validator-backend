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
            # Try rdfs:domain directly
            OPTIONAL { ?property rdfs:domain ?domain }
           \s
            # Try SGoV OWL restriction pattern
            OPTIONAL {
                ?property rdfs:subClassOf ?restriction .
                ?restriction a owl:Restriction ;
                             owl:onProperty <https://slovník.gov.cz/základní/pojem/je-vlastností> ;
                             owl:allValuesFrom ?domain .
            }
            OPTIONAL {
                ?property rdfs:subClassOf ?restriction2 .
                ?restriction2 a owl:Restriction ;
                              owl:onProperty <https://slovník.gov.cz/základní/pojem/je-vlastností> ;
                              owl:onClass ?domain .
            }
           \s
            OPTIONAL { ?domain skos:prefLabel ?domainLabel }
        }
        ORDER BY ?property
       \s""";

    /**
     * Diagnostic query: shows all triples related to typ-vlastnosti concepts
     * to discover how domain information is encoded.
     */
    /**
     * Diagnostic query: for first 3 typ-vlastnosti concepts, traverses blank node
     * restrictions to show what owl:onProperty and owl:onClass/allValuesFrom/someValuesFrom they contain.
     */
    public static final String DIAGNOSTIC_PROPERTY_STRUCTURE_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
       \s
        SELECT ?property ?prefLabel ?restrictionPred ?restrictionObj WHERE {
            {
                SELECT ?property WHERE {
                    ?property a <https://slovník.gov.cz/základní/pojem/typ-vlastnosti> .
                    FILTER(STRSTARTS(STR(?property), "%s"))
                }
                ORDER BY ?property
                LIMIT 3
            }
            OPTIONAL { ?property skos:prefLabel ?prefLabel }
            ?property rdfs:subClassOf ?blank .
            FILTER(isBlank(?blank))
            ?blank ?restrictionPred ?restrictionObj .
        }
        ORDER BY ?property ?restrictionPred
       \s""";

    /**
     * Diagnostic query: shows hierarchy (rdfs:subClassOf) relationships
     * and how parents are typed, to understand why superclass lookup fails.
     */
    public static final String DIAGNOSTIC_HIERARCHY_STRUCTURE_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
       \s
        SELECT DISTINCT ?child ?childLabel ?parent ?parentLabel ?childType ?parentType ?parentIsBlank WHERE {
            ?child a ?childType .
            VALUES ?childType {
                <https://slovník.gov.cz/základní/pojem/typ-objektu>
                <https://slovník.gov.cz/základní/pojem/typ-vlastnosti>
                <https://slovník.gov.cz/základní/pojem/typ-vztahu>
                <https://slovník.gov.cz/základní/pojem/typ-události>
            }
            FILTER(STRSTARTS(STR(?child), "%s"))
           \s
            ?child rdfs:subClassOf ?parent .
            FILTER(!isBlank(?parent))
           \s
            OPTIONAL { ?child skos:prefLabel ?childLabel }
            OPTIONAL { ?parent skos:prefLabel ?parentLabel }
            OPTIONAL { ?parent a ?parentType }
            BIND(isBlank(?parent) AS ?parentIsBlank)
        }
        ORDER BY ?childType ?child ?parent
       \s""";

    /**
     * Diagnostic query: dumps all relevant triples for each concept typed as owl:ObjectProperty
     * or slovníky:vztah within the namespace. Shows types, domain, range, restrictions, subClassOf.
     * Used to understand why some objectProperties are missing domain/range.
     */
    public static final String DIAGNOSTIC_OBJECT_PROPERTY_STRUCTURE_QUERY = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX z-sgov-pojem: <https://slovník.gov.cz/základní/pojem/>
       \s
        SELECT DISTINCT ?concept ?type ?domain ?range ?subClassOf ?restrictionProp ?restrictionClass ?prefLabel WHERE {
            ?concept a ?type .
            FILTER(STRSTARTS(STR(?concept), "%s"))

            # Match concepts that are objectProperties or relationships
            FILTER(
                ?type = owl:ObjectProperty ||
                ?type = z-sgov-pojem:typ-vztahu ||
                ?type = <https://slovník.gov.cz/datový/pracovní-prostor/pojem/vztah> ||
                ?type = <https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/vztah>
            )

            OPTIONAL { ?concept rdfs:domain ?domain }
            OPTIONAL { ?concept rdfs:range ?range }
            OPTIONAL { ?concept rdfs:subClassOf ?subClassOf }
            OPTIONAL { ?concept skos:prefLabel ?prefLabel }
            OPTIONAL {
                ?concept rdfs:subClassOf ?restriction .
                ?restriction a owl:Restriction ;
                             owl:onProperty ?restrictionProp ;
                             owl:onClass ?restrictionClass .
            }
        }
        ORDER BY ?concept ?type
       \s""";

    public static final String EXPLORE_RELATIONSHIP_RESTRICTIONS_QUERY = """
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       \s
        SELECT DISTINCT ?relationship ?restriction ?property ?class WHERE {
            ?relationship a <https://slovník.gov.cz/základní/pojem/typ-vztahu> .
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
       \s
        SELECT DISTINCT ?property ?restriction ?onProperty ?class WHERE {
            ?property a <https://slovník.gov.cz/základní/pojem/typ-vlastnosti> .
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
       \s
        SELECT DISTINCT ?concept ?type ?domain ?range WHERE {
            ?concept a ?type .
            VALUES ?type {
                <https://slovník.gov.cz/základní/pojem/typ-vlastnosti>
                <https://slovník.gov.cz/základní/pojem/typ-vztahu>
            }
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
       \s
        SELECT ?child ?childLabel ?parent ?parentLabel ?elementType WHERE {
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