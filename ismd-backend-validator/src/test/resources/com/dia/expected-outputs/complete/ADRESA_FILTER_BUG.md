# External-Reference Filter Bug — Adresa Concept

**Discovered:** 2026-04-28 (during LKOD test re-enable on `feat/issue-88`)
**Status:** Open — to be fixed on a separate branch

## Symptom

After re-enabling the previously-disabled workflow tests, the conversion output
contains 41 `pojmy` entries instead of the expected 40. The extra entry is the
external-reference concept `Adresa`:

```
https://slovník.gov.cz/legislativní/sbírka/111/2009/pojem/adresa
```

`Adresa` is referenced by `sídlí na adrese` as `obor-hodnot` (range), but it is
defined in a different vocabulary (`legislativní/sbírka/111/2009`) and should
appear only as a reference, not as a full local concept entry.

## Root Cause

The "is this concept local to the current vocabulary?" check is too coarse.

**Two `belongsToCurrentVocabulary` implementations** with identical logic:

- `src/main/java/com/dia/exporter/JsonExporter.java:905`
- `src/main/java/com/dia/conversion/transformer/OntologyResourceBuilder.java:990`

Both compare:

```java
conceptURI.startsWith(effectiveNamespace)
```

**With LKOD removed:**

1. `VocabularyMetadata.getNamespace()` is now always `null` (readers no longer
   set it — `ArchiReader.extractVocabularyMetadata`,
   `EnterpriseArchitectReader.extractVocabularyMetadata`).
2. `OFNDataTransformerNew.determineEffectiveNamespace`
   (`src/main/java/com/dia/conversion/transformer/OFNDataTransformerNew.java:272`)
   falls back to `DEFAULT_NS = "https://slovník.gov.cz/"`
   (`ismd-validator-common/.../VocabularyConstants.java:12`).
3. `URIGenerator.generateConceptURI`
   (`ismd-validator-common/.../URIGenerator.java:27`) preserves any identifier
   containing `/pojem/` as-is — so `Adresa` keeps its template-supplied IRI
   `https://slovník.gov.cz/legislativní/sbírka/111/2009/pojem/adresa`.
4. The filter `startsWith("https://slovník.gov.cz/")` matches that URI →
   `Adresa` is treated as local.

`https://slovník.gov.cz/` is the umbrella namespace for **all** Czech government
vocabularies, not the namespace of *this* vocabulary. The filter cannot
distinguish "concept lives under DEFAULT_NS" from "concept belongs to *this*
vocabulary."

## Where Local Concepts Actually Live

In the current EA/Archi output, local concepts land in two distinct sub-paths:

- `https://slovník.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/...`
  — concepts whose template `identifikátor` was preserved.
- `https://slovník.gov.cz/slovník-dle-metodiky-dat-digitální-a-informační-agentury/pojem/...`
  — concepts auto-built from the model name.

These two roots disagree because the model name (`"Slovník dle metodiky dat
Digitální a informační agentury"`) differs from the prefix used in the template's
concept identifiers (`"příkladový-slovník-..."`). That's a separate template
inconsistency that may also need revisiting.

## Tests Disabled

The following tests were re-enabled after LKOD removal but are now disabled
again pending this fix. Each has `@Disabled` with a reason pointing here.

| Test class | Method | Failure mode |
|---|---|---|
| `ConversionWorkflowJsonTest` | `conversionWorkflow_shouldProduceExpectedOutput` | `Adresa` present in actual but not in expected; pojmy count 41 vs 40 |
| `ConversionWorkflowJsonTest` | `conversionWorkflow_shouldPreserveAllData` | Output contains 41 pojmy, hardcoded expected is 40 |
| `ConversionWorkflowEAJsonTest` | `eaConversionWorkflow_shouldProduceExpectedOutput` | Same as Archi/Excel |
| `ConversionWorkflowEAJsonTest` | `eaConversionWorkflow_shouldPreserveAllData` | Same |
| `ConversionWorkflowTurtleTest` | `conversionWorkflow_shouldProduceSemanticallySameTurtleOutput` | Extra Adresa concept produces 26 extra triples in actual model |
| `ConversionWorkflowTurtleTest` | `conversionWorkflow_turtleShouldPreserveAllData` | TTL output has 42 entities, input data sums to 41 |
| `ConversionWorkflowEATurtleTest` | `eaConversionWorkflow_shouldProduceSemanticallySameTurtleOutput` | Same |
| `ConversionWorkflowEATurtleTest` | `eaConversionWorkflow_turtleShouldPreserveAllData` | Same |

**Not disabled** (still running and passing):
- `*_shouldPreserveCharacteristics` — checks field presence, not count
- `*_turtleShouldUseCorrectRdfVocabulary` — checks RDF vocabulary use
- All `ArchiReaderUnitTest`, `EnterpriseArchitectUnitTest`,
  `TurtleExporterUnitTest`, `JsonExporterUnitTest` tests.

## Known Quirks Surfacing Alongside

- **`alternativní-název` array order** — `řidičský-průkaz` emits
  `["řidičák", "ŘP"]` but expected fixture has `["ŘP", "řidičák"]`. This is the
  general array-ordering instability noted by the team; does not need to be
  fixed alongside the filter, but the deviation comparator should treat
  `@container: @set` arrays as unordered.

## Fix Direction (for the new branch)

Three plausible options, in order of correctness:

1. **(a) URI-prefix only** — change the filter to
   `effectiveNamespace + sanitize(vocabularyName) + "/"`. Excludes legitimate
   `příkladový-slovník-...` concepts because they don't use the model-name slug.
   Wrong unless the template inconsistency is also fixed.
2. **(b) Reader-tagged concepts** — readers tag each `OntologyData` element
   with `isLocal` based on the input's structural definition (e.g. direct
   children of the vocabulary package in EA, top-level elements in Archi);
   exporter respects the tag. Most correct, more code.
3. **(c) Derived local-prefix set** — at transform time, derive a set of
   "local prefixes" by examining which concepts the input actually defines
   (vs. references), and filter against that set. Implementable as a `Set<String>`
   replacing the single `effectiveNamespace` string in the filter.

Probably (b). Either way, regenerate `excel_output_*_no-lkod.{jsonld,ttl}` and
`ea_output_*_no-lkod.{jsonld,ttl}` once the filter is correct, and re-enable the
8 disabled tests above.

## Reproducer

```bash
cd ismd-backend-validator
./mvnw test -Dtest='ConversionWorkflowJsonTest#conversionWorkflow_shouldProduceExpectedOutput'
```

(remove the `@Disabled` first). Failure reports:

```
Workflow produced 4 deviation(s) from expected output:
1. COUNT_MISMATCH at root.pojmy
   Array size mismatch: expected 40 elements, actual 41 elements
4. EXTRA_FIELD at root.pojmy[iri=https://slovník.gov.cz/legislativní/sbírka/111/2009/pojem/adresa]
```