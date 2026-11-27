# NEXSUS 3.4 — Independent Business Intelligence

## Mandatory integration boundaries
- Accept only user-selected PDF, XLSX and CSV exports.
- Never connect to Remaris databases, APIs, credentials or application interfaces.
- Never modify Remaris or perform fiscal operations.
- Parse documents locally; do not send business contents to external AI providers.
- Keep NOMAD and Artist separate; require explicit venue selection and review.
- Preserve source identifiers, distinguish purchase and sale prices, flag ambiguous rows and negative stock without silently correcting them.
- Require explicit confirmation before any database write; prevent duplicate imports and retain provenance.
- Do not commit real business documents to GitHub.
- Keep stable branches and production unchanged; do not activate paid services.

## Release gates
Install reviewed local XLSX/PDF dependencies, test all seven reference exports, verify RLS with separate users, and validate duplicate handling before enabling persistent imports. Until then, native document processing remains review-only and incomplete.
