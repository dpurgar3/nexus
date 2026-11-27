param([Parameter(Mandatory=$true)][string]$SheetJsDirectory,[Parameter(Mandatory=$true)][string]$PdfJsDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$files = @(@{Source=(Join-Path $SheetJsDirectory 'xlsx.mjs'); Name='xlsx.mjs'},@{Source=(Join-Path $PdfJsDirectory 'pdf.mjs'); Name='pdf.mjs'},@{Source=(Join-Path $PdfJsDirectory 'pdf.worker.mjs'); Name='pdf.worker.mjs'})
foreach ($f in $files) { if (!(Test-Path -LiteralPath $f.Source -PathType Leaf)) { throw ('Missing: '+$f.Source) }; Get-FileHash -LiteralPath $f.Source -Algorithm SHA256 }
if ((Read-Host 'Confirm official origin, matching PDF.js versions, licenses and verified hashes (YES)') -cne 'YES') { throw 'Cancelled' }
$dest = Join-Path $root 'vendor'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
foreach ($f in $files) { Copy-Item -LiteralPath $f.Source -Destination (Join-Path $dest $f.Name) }
Write-Host 'Local files copied. Retain upstream licenses. Run browser tests before release. No deployment performed.'
