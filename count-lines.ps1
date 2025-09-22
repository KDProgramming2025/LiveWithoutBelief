# Requires: Windows PowerShell 5.1 or PowerShell 7+
# Purpose: Recursively count lines in every file under the current directory and print the top 10 files by line count.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Get-LineCount {
    <#
    .SYNOPSIS
        Counts the number of lines in a file using a streaming reader.

    .PARAMETER Path
        Full path to the file to count.

    .OUTPUTS
        [long] Line count

    .NOTES
        Uses StreamReader to avoid loading whole file into memory and to be resilient with locked files (ReadWrite share).
        If a file can't be read as text, returns 0 for that file instead of failing the entire script.
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string] $Path
    )

    [long]$count = 0
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            # Detect encoding = true allows BOM detection; fallback replaces invalid bytes
            $sr = New-Object System.IO.StreamReader($fs, $true)
            try {
                while ($null -ne $sr.ReadLine()) { $count++ }
            }
            finally { $sr.Dispose() }
        }
        finally { $fs.Dispose() }
    }
    catch {
        # Could be binary or locked; treat as zero lines
        $count = 0
    }
    return $count
}

# Optional: skip common binary extensions to speed up processing and avoid misleading counts
$BinaryExtensions = @(
    '.png','.jpg','.jpeg','.gif','.webp','.ico','.bmp','.svgz',
    '.zip','.gz','.7z','.rar','.jar','.aar','.dex','.so','.dll','.exe','.bin','.class','.apk','.aab','.o','.obj',
    '.pdf','.mp3','.wav','.ogg','.mp4','.mkv','.mov','.avi','.webm',
    '.ttf','.otf','.woff','.woff2'
)

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$results = New-Object System.Collections.Generic.List[psobject]

# Gather files; exclude tooling/build caches for speed and relevance
# Excluded directories: .git, .gradle*, build, node_modules, coverage, dist
$excludeDirs = @(
    "\\\.git(\\|$)",
    "\\\.gradle[^\\]*?(\\|$)",
    "\\build(\\|$)",
    "\\node_modules(\\|$)",
    "\\coverage(\\|$)",
    "\\dist(\\|$)"
)

$items = Get-ChildItem -LiteralPath (Get-Location) -Recurse -File -Force -ErrorAction SilentlyContinue |
    Where-Object {
        $full = $_.FullName
        foreach ($pat in $excludeDirs) { if ($full -match $pat) { return $false } }
        return $true
    }

$total = ($items | Measure-Object).Count
$index = 0

foreach ($file in $items) {
    $index++
    if ($index % 500 -eq 0) {
        Write-Progress -Activity "Counting lines" -Status "$index / $total" -PercentComplete ([int](100*$index/$total))
    }

    # Exclude common lockfiles explicitly
    $name = [System.IO.Path]::GetFileName($file.FullName)
    if ($name -in @('package-lock.json','yarn.lock','pnpm-lock.yaml','gradle.lockfile')) { continue }

    $ext = [System.IO.Path]::GetExtension($file.FullName)
    if ($BinaryExtensions -contains $ext.ToLowerInvariant()) {
        # Skip counting for known binary files
        continue
    }

    $lines = Get-LineCount -Path $file.FullName
    $results.Add([pscustomobject]@{
        File  = $file.FullName
        Lines = $lines
    }) | Out-Null
}

$stopwatch.Stop()

$top = $results |
    Sort-Object -Property Lines -Descending |
    Select-Object -First 10

"" | Out-Host
"Top 10 files by line count:" | Out-Host
$top | Format-Table -AutoSize @{Label='Lines';Expression={$_.Lines}}, @{Label='File';Expression={$_.File}}
"" | Out-Host
"Files processed: $($results.Count) (skipped binaries may be excluded)" | Out-Host
"Duration: $([math]::Round($stopwatch.Elapsed.TotalSeconds,2)) seconds" | Out-Host

if ($env:GITHUB_ACTIONS) {
    # Emit GitHub Actions friendly summary if applicable
    "::group::Top 10 files by line count" | Out-Host
    $top | ForEach-Object { "{0,10}  {1}" -f $_.Lines, $_.File } | Out-Host
    "::endgroup::" | Out-Host
}
