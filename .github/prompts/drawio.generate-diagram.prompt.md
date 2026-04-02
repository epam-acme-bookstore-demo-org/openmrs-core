---
description: Generate a Draw.io architecture diagram from a natural-language description
argument-hint: "Provide the architecture description, shape library, and output path"
---

# Generate Draw.io Diagram

Create an editable `.drawio` architecture diagram based on the provided description.

## Architecture Description

${input:architecture_description}

## Shape Library

${input:shape_library}

Use `generic` if no specific library is needed. Supported values: `generic`, `azure`, `aws`, `gcp`, `uml`, `network`.

## Output Path

${input:output_path}

Default: `docs/diagrams/architecture.drawio`

## Instructions

1. Load the **drawio.diagram-ops** skill for Draw.io XML format reference and layout strategies.
2. Interpret the architecture description — identify components, services, data stores, and their relationships.
3. Select appropriate shapes from the specified library. Use generic shapes if the library is `generic` or unspecified.
4. Determine the best layout strategy (hierarchical, tree, or organic) based on the system topology.
5. Generate the `.drawio` XML with:
   - Labelled nodes for each component.
   - Directed connectors for all relationships and data flows.
   - Groups for logical boundaries (resource groups, VPCs, subnets).
   - A title and optional legend.
6. Write the file to the specified output path.
7. Verify the output:
   - All nodes have visible labels.
   - All connectors have source and target.
   - Groups contain their child elements.
   - The file is well-formed `.drawio` XML.
8. Report the file path and suggest opening it with the VS Code Draw.io extension (`hediet.vscode-drawio`) for visual review.
