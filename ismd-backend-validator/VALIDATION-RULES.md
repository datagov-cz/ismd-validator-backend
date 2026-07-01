# Validation Rules Configuration

## Overview

The ISMD validator uses [SHACL](https://www.w3.org/TR/shacl/) rules to validate vocabulary data. Rules are defined as `.ttl` (Turtle) files and loaded automatically at startup from the rules directory.

There are two categories of rules:

- **Local rules** (`local-*.ttl`) — validate a single vocabulary in isolation
- **Global rules** (`global-*.ttl`) — compare vocabulary data against published vocabularies via SPARQL

Each rule has a **severity level**:

| Severity | SHACL value | Meaning |
|----------|-------------|---------|
| Chyba (Error) | `sh:Violation` | Blocking problem — vocabulary cannot be published |
| Varování (Warning) | `sh:Warning` | Should be fixed, but does not block publication |
| Informace (Info) | `sh:Info` | Informational notice |

## Rule Files

Rules are stored in:

```
src/main/resources/validation/rules/
```

Each `.ttl` file contains one or more SHACL `sh:NodeShape` definitions. The file is loaded and identified by its **sanitized filename** (lowercase, `.ttl` removed).

### Current Rules

#### Errors (Chyba)

| File | Message |
|------|---------|
| `local-název-slovníku.ttl` | Slovnik nema vyplneny nazev |
| `local-název-pojmu.ttl` | Pojem nema vyplneny nazev |
| `local-duplikátní-iri-pojmů.ttl` | Slovnik obsahuje vice pojmu se stejnym IRI |
| `local-duplikátní-název-pojmů.ttl` | Slovnik obsahuje vice pojmu se stejnym nazvem |
| `local-charakteristika-hodnota-číselník.ttl` | Charakteristika pojmu neobsahuje hodnotu dle ciselniku z OFN |
| `local-charakteristika-hodnota-typ.ttl` | Charakteristika pojmu neobsahuje hodnotu dle typu z OFN |
| `local-charakteristika-hodnota-regex.ttl` | Charakteristika pojmu neobsahuje hodnotu dle regexu z OFN |
| `global-slovník-s-jiným-iri-stejným-názvem.ttl` | Mezi publikovanymi slovniky existuje slovnik s jinym IRI a stejnym nazvem |
| `global-slovník-se-stejným-iri-jiným-názvem.ttl` | Mezi publikovanymi slovniky existuje slovnik se stejnym IRI a jinym nazvem |
| `global-pojem-se-stejným-iri-jiným-názvem.ttl` | Mezi publikovanymi slovniky existuje pojem se stejnym IRI a jinym nazvem |

#### Warnings (Varovani)

| File | Message |
|------|---------|
| `local-popis-slovníku.ttl` | Slovnik nema vyplneny popis |
| `local-zdroj-pojmu.ttl` | Pojem nema vyplneny zdroj |
| `local-definice-bez-zdroje.ttl` | Pojem ma vyplneny atribut definice a soucasne nema vyplneny zdroj (a obracene) |
| `local-zdroj-ani-související-zdroj.ttl` | Pojem nema vyplneny atribut zdroj ani souvisejici zdroj |
| `local-osamocené-pojmy.ttl` | Pojem nema vazbu na model jako celek |
| `local-alternativní-název-stejný.ttl` | Alternativni nazev je stejny, jako nazev |
| `local-typ-pojmu.ttl` | Pojem nema vyplneny typ (Subjekt/Objekt/Vlastnost/Vztah) |

#### Info (Informace)

| File | Message |
|------|---------|
| `local-charakteristiky-rpp-pojmu.ttl` | Pojem nema vyplnene charakteristiky pro RPP/360 / Pojem ma kompletni charakteristiky pro RPP |
| `global-pojem-se-stejným-iri.ttl` | Mezi publikovanymi slovniky existuje pojem se stejnym IRI |
| `global-slovník-se-stejným-iri.ttl` | Mezi publikovanymi slovniky existuje slovnik se stejnym IRI |

#### Disabled

| File | Reason |
|------|--------|
| `local-povinné-údaje-pojmu.ttl` | Not in current requirements |
| `local-popis-pojmu.ttl` | Not in current requirements |
| `global-pojem-s-jiným-iri-stejným-názvem.ttl` | Not in current requirements |

## Configuration

### Enabling / Disabling Rules

In `application.properties`, each rule has a toggle:

```properties
validation.rules.enabled.<rule-key>=true|false
```

The `<rule-key>` **must match the sanitized filename** (lowercase, no `.ttl` extension). For example, the file `local-název-slovníku.ttl` has key `local-název-slovníku`.

**Default behavior:** If a `.ttl` file exists in the rules directory but has no corresponding entry in `application.properties`, the rule is **enabled by default**.

To disable a rule, explicitly set it to `false`:

```properties
validation.rules.enabled.local-povinné-údaje-pojmu=false
```

### Execution Settings

```properties
# Directory containing rule files
validation.rules.rules-directory=classpath:validation/rules/

# When to run validation (before_export, json_export, ttl_export)
validation.rules.default-timing=before_export

# Maximum time for validation execution (milliseconds)
validation.rules.timeout-ms=10000

# Cache validation results
validation.rules.cache-results=true

# If true, continue loading other rules when one rule file fails to parse
validation.rules.continue-on-rule-error=true
```

### Global Validation Settings

Global rules query a SPARQL endpoint to compare against published vocabularies:

```properties
validation.global.sparql-endpoint=
validation.global.timeout-ms=10000
validation.global.max-attempts=3
validation.global.retry-delay-ms=1000
```

## How to Add a New Rule

1. **Create a `.ttl` file** in `src/main/resources/validation/rules/`

   Use the naming convention:
   - `local-<rule-name>.ttl` for rules that validate a single vocabulary
   - `global-<rule-name>.ttl` for rules that compare against published data

2. **Define the SHACL shape** in the file. Minimal template:

   ```turtle
   @prefix sh: <http://www.w3.org/ns/shacl#> .
   @prefix slovníky: <https://slovník.gov.cz/generický/datový-slovník-ofn-slovníků/pojem/> .

   <https://slovník.gov.cz/shacl/lokální/my-rule-name>
       a sh:NodeShape ;
       sh:targetClass slovníky:pojem ;
       sh:name "Rule name"@cs ;
       sh:description "What this rule checks" ;
       sh:message "User-facing message when rule is violated"@cs ;
       sh:severity sh:Violation ;  # or sh:Warning or sh:Info

       # Add constraints here (sh:property, sh:sparql, sh:or, etc.)
       sh:property [
           sh:path <some:property> ;
           sh:minCount 1 ;
       ] .
   ```

   For SPARQL-based rules, use `sh:sparql` instead of `sh:property`:

   ```turtle
       sh:sparql [
           sh:select """
               SELECT $this WHERE {
                   # your SPARQL query
               }
           """ ;
       ] .
   ```

3. **Add a config entry** in `application.properties`:

   ```properties
   validation.rules.enabled.local-my-rule-name=true
   ```

4. **Restart the application.** Rules are loaded at startup. Check the logs for:

   ```
   Loaded rule: local-my-rule-name - ENABLED (local-my-rule-name.ttl) [LOCAL]
   ```

## Architecture

Key classes:

| Class | Role |
|-------|------|
| `RuleManager` | Loads `.ttl` files at startup, manages enable/disable state |
| `ValidationConfiguration` | Binds `validation.rules.*` properties, provides `isRuleEnabled()` |
| `SHACLRuleEngine` | Executes SHACL validation using Apache Jena |
| `ValidationServiceImpl` | Orchestrates the validation workflow |
| `ISMDValidationReport` | Contains validation results with filtering by severity |

## Common RDF Properties

Reference for properties used in rules:

| Czech name | Property |
|------------|----------|
| nazev | `skos:prefLabel` |
| alternativni-nazev | `skos:altLabel` |
| definice | `skos:definition` |
| popis | `dcterms:description` |
| zdroj | `dcterms:source` / `dc:source` |
| definujici-ustanoveni | `slovníky:definující-ustanovení` |
| souvisejici-ustanoveni | `slovníky:související-ustanovení` |
| definujici-nelegislativni-zdroj | `slovníky:definující-nelegislativní-zdroj` |
| souvisejici-nelegislativni-zdroj | `slovníky:související-nelegislativní-zdroj` |
| je-sdilen-v-ppdf | `a104:je-sdílen-v-propojeném-datovém-fondu` |
| zpusob-ziskani-udaje | `slovníky:má-způsob-získání-údaje` |
| typ-obsahu-udaje | `slovníky:má-typ-obsahu-údaje` |
| zpusoby-sdileni-udaje | `slovníky:má-způsob-sdílení-údaje` |

Target classes:

| Type | Class IRI |
|------|-----------|
| Vocabulary | `skos:ConceptScheme` |
| Concept | `slovníky:pojem` |
| Class | `owl:Class` |
| Property | `owl:DatatypeProperty` |
| Relationship | `owl:ObjectProperty` |
| Subject type | `vsgov:typ-subjektu-práva` |
| Object type | `vsgov:typ-objektu-práva` |
