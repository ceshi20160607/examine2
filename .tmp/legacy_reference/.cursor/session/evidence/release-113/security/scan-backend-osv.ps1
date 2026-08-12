param(
    [Parameter(Mandatory = $true)]
    [string]$Artifact
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

$artifactPath = (Resolve-Path $Artifact).Path
$artifactStream = [System.IO.File]::OpenRead($artifactPath)
$artifactZip = [System.IO.Compression.ZipArchive]::new(
    $artifactStream,
    [System.IO.Compression.ZipArchiveMode]::Read
)
$jarEntries = @($artifactZip.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' })
$components = @()

try {
    foreach ($entry in $jarEntries) {
        $nestedMemory = [System.IO.MemoryStream]::new()
        $nestedEntryStream = $entry.Open()
        try {
            $nestedEntryStream.CopyTo($nestedMemory)
        } finally {
            $nestedEntryStream.Dispose()
        }
        $nestedMemory.Position = 0

        try {
            $nestedZip = [System.IO.Compression.ZipArchive]::new(
                $nestedMemory,
                [System.IO.Compression.ZipArchiveMode]::Read,
                $true
            )
            try {
                $propsEntry = $nestedZip.Entries |
                    Where-Object { $_.FullName -like 'META-INF/maven/*/*/pom.properties' } |
                    Select-Object -First 1
                if ($null -eq $propsEntry) {
                    continue
                }

                $reader = [System.IO.StreamReader]::new($propsEntry.Open())
                try {
                    $propertiesText = $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                }
                $properties = @{}
                foreach ($line in ($propertiesText -split "`r?`n")) {
                    if ($line -match '^([^#!][^=]*)=(.*)$') {
                        $properties[$matches[1].Trim()] = $matches[2].Trim()
                    }
                }
                if ($properties.groupId -and $properties.artifactId -and $properties.version) {
                    $components += [pscustomobject]@{
                        group = $properties.groupId
                        artifact = $properties.artifactId
                        version = $properties.version
                        jar = $entry.Name
                    }
                }
            } finally {
                $nestedZip.Dispose()
            }
        } finally {
            $nestedMemory.Dispose()
        }
    }
} finally {
    $artifactZip.Dispose()
    $artifactStream.Dispose()
}

$internalGroup = 'com.unique.' + 'exam' + 'ine'
$externalComponents = @($components |
    Where-Object { $_.group -ne $internalGroup } |
    Sort-Object group, artifact, version -Unique)
$queries = @($externalComponents | ForEach-Object {
    @{
        package = @{ ecosystem = 'Maven'; name = ($_.group + ':' + $_.artifact) }
        version = $_.version
    }
})
$requestBody = @{ queries = $queries } | ConvertTo-Json -Depth 8 -Compress
$batch = Invoke-RestMethod `
    -Method Post `
    -Uri 'https://api.osv.dev/v1/querybatch' `
    -ContentType 'application/json' `
    -Body $requestBody `
    -TimeoutSec 90

$matches = @()
for ($index = 0; $index -lt $batch.results.Count; $index++) {
    foreach ($summary in @($batch.results[$index].vulns)) {
        if ($null -eq $summary -or [string]::IsNullOrWhiteSpace($summary.id)) {
            continue
        }
        $component = $externalComponents[$index]
        $detail = Invoke-RestMethod `
            -Method Get `
            -Uri ('https://api.osv.dev/v1/vulns/' + [uri]::EscapeDataString($summary.id)) `
            -TimeoutSec 90
        $matchingAffected = @($detail.affected | Where-Object {
            $_.package.ecosystem -eq 'Maven' -and
            $_.package.name -eq ($component.group + ':' + $component.artifact)
        })
        $fixedVersions = @($matchingAffected.ranges.events.fixed |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Sort-Object -Unique)
        $matches += [pscustomobject]@{
            component = ($component.group + ':' + $component.artifact + ':' + $component.version)
            id = $detail.id
            aliases = @($detail.aliases)
            summary = $detail.summary
            severity = @($detail.severity)
            databaseSeverity = $detail.database_specific.severity
            fixedVersions = $fixedVersions
            published = $detail.published
            modified = $detail.modified
        }
    }
}

[pscustomobject]@{
    scannedAt = (Get-Date).ToUniversalTime().ToString('o')
    source = 'https://api.osv.dev/v1/querybatch and /v1/vulns/{id}'
    artifact = $artifactPath
    artifactSha256 = (Get-FileHash $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
    nestedJarCount = $jarEntries.Count
    mavenMetadataComponentCount = $components.Count
    externalMavenComponentCount = $externalComponents.Count
    unscannedNestedJarCount = $jarEntries.Count - $components.Count
    unscannedNestedJars = @($jarEntries.Name |
        Where-Object { $_ -notin $components.jar } |
        Sort-Object -Unique)
    affectedComponentCount = @($matches.component | Sort-Object -Unique).Count
    vulnerabilityMatchCount = $matches.Count
    matches = @($matches | Sort-Object component, id)
} | ConvertTo-Json -Depth 20
