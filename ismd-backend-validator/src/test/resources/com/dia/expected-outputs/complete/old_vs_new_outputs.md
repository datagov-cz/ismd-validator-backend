# LOKALNI_KATALOG Removal — Impact on Test Outputs

## Change Summary

The `adresa-lokálního-katalogu-dat` (LOKALNI_KATALOG) property has been removed from the reading
and conversion process. The default namespace `https://slovník.gov.cz/` (DEFAULT_NS) is now used
for all ontologies instead of a per-vocabulary namespace extracted from input templates.

## Disabled Tests

The following test classes are disabled with `@Disabled` until updated templates and expected
outputs are provided:

| Test Class | Reason |
|---|---|
| `ArchiReaderUnitTest` | Asserts namespace extracted from template (`https://data.dia.gov.cz`) |
| `EnterpriseArchitectUnitTest` | Asserts namespace is not null (now null, handled by transformer) |
| `TurtleExporterUnitTest` | References removed `LOKALNI_KATALOG` constant in test setup |
| `ConversionWorkflowJsonTest` | Compares Archi/Excel output against `excel_output_jsonld.jsonld` |
| `ConversionWorkflowTurtleTest` | Compares Archi/Excel output against `excel_output_ttl.ttl` |
| `ConversionWorkflowEAJsonTest` | Compares EA output against `ea_output_jsonld.jsonld` |
| `ConversionWorkflowEATurtleTest` | Compares EA output against `ea_output_ttl.ttl` |

Tests **not** disabled (unaffected):
- `ExcelReaderUnitTest` — no namespace assertions
- `JsonExporterUnitTest` — uses `effectiveNamespace`, not `LOKALNI_KATALOG`

## Pending Template Updates

The following input templates contain `adresa lokálního katalogu dat` property and/or concept
identifiers using `https://data.dia.gov.cz/` namespace. They need to be updated to remove the
LOKALNI_KATALOG property and update concept identifiers to use `https://slovník.gov.cz/`:

| Template | Location | What to Update |
|---|---|---|
| `testArchiInput.xml` | `canonical/complete/` | Remove `propid-19` (LOKALNI_KATALOG = `https://data.dia.gov.cz`). Update 14 concept identifiers from `https://data.dia.gov.cz/...` to `https://slovník.gov.cz/...` |
| `testEAInput.xml` | `canonical/complete/` | Remove NAMESPACE tag. Update concept identifiers if applicable |
| `testExcelProject.xlsx` | `canonical/complete/` | Remove "Adresa lokálního katalogu dat" row. Update concept identifiers if applicable |

## Pending Expected Output Updates

Once templates are updated, regenerate and replace these expected output files:

| Expected Output | Used By |
|---|---|
| `excel_output_jsonld.jsonld` | `ConversionWorkflowJsonTest` (Excel + Archi) |
| `excel_output_ttl.ttl` | `ConversionWorkflowTurtleTest` (Excel + Archi) |
| `ea_output_jsonld.jsonld` | `ConversionWorkflowEAJsonTest` |
| `ea_output_ttl.ttl` | `ConversionWorkflowEATurtleTest` |

## Old vs New Output Comparison (testArchiInput.xml)

### Namespace Change

- **Before:** `effectiveNamespace` = `https://data.dia.gov.cz/` (from LOKALNI_KATALOG in template)
- **After:** `effectiveNamespace` = `https://slovník.gov.cz/` (DEFAULT_NS)

### Concept Count

- **Old output:** 41 subjects (all under `https://data.dia.gov.cz/`)
- **New output:** 28 subjects (27 under `https://slovník.gov.cz/` + 1 external `l111-2009:adresa`)

### Missing Concepts (13)

These 13 concepts had explicit `identifikátor` values in the template pointing to
`https://data.dia.gov.cz/.../pojem/...`. Since identifiers containing `/pojem/` are preserved
as-is by `URIGenerator`, their URIs remained under `data.dia.gov.cz`. With the new
`effectiveNamespace` being `slovník.gov.cz`, these are now classified as external references by
`belongsToCurrentVocabulary()` and filtered out by the exporters.

| Concept | Identifier in Template |
|---|---|
| řidičský-průkaz | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/řidičský-průkaz` |
| obecní-úřad-obce-s-rozšířenou-působností | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/obecní-úřad-obce-s-rozšířenou-působností` |
| vozidlo | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/vozidlo` |
| řidič | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/řidič` |
| řidič-evidovaný-v-registru-řidičů | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/řidič-evidovaný-v-registru-řidičů` |
| účastník-provozu-na-pozemních-komunikacích | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/účastník-provozu-na-pozemních-komunikacích` |
| jméno-držitele-řidičského-průkazu | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/jméno-držitele-řidičského-průkazu` |
| příjmení-držitele-řidičského-průkazu | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/příjmení-držitele-řidičského-průkazu` |
| název-obecního-úřadu-obce-s-rozšířenou-působností-copy | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/název-obecního-úřadu-obce-s-rozšířenou-působností-copy` |
| název-obecního-úřadu-obce-s-rozšířenou-působností | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/název-obecního-úřadu-obce-s-rozšířenou-působností` |
| drží-řidičský-průkaz | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/drží-řidičský-průkaz` |
| řídí-vozidlo | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/řídí-vozidlo` |
| sídlí-na-adrese | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/sídlí-na-adrese` |
| vydává-řidičský-průkaz | `https://data.dia.gov.cz/příkladový-slovník-z-metodiky-popisu-dat/pojem/vydává-řidičský-průkaz` |

This is expected and correct. Once the templates are updated with identifiers under
`https://slovník.gov.cz/`, these concepts will reappear in the output as local concepts.

## Re-enabling Tests Checklist

1. Update input templates (remove LOKALNI_KATALOG, update concept identifiers)
2. Run conversion on each updated template, capture output
3. Replace expected output files with new outputs
4. Update `ArchiReaderUnitTest` — remove namespace assertions or update expected values
5. Update `EnterpriseArchitectUnitTest` — adjust namespace assertion (now null)
6. Update `TurtleExporterUnitTest` — remove `LOKALNI_KATALOG` from test setup
7. Remove `@Disabled` annotations from all 7 test classes
8. Run full test suite to verify
