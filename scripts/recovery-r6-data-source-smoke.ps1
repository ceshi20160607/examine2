param(
    [string]$BaseUrl = 'http://127.0.0.1:9999',
    [switch]$KeepCreatedData
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {
                $body = $_.Exception.Message
            }
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 16 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystem {
    if ($KeepCreatedData) {
        return 'SKIPPED'
    }
    if ([string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:AdminHeaders) {
        return 'SKIPPED'
    }
    $cleanup = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:AdminHeaders -Body @{
        reason = 'recovery-r6-data-source cleanup created system'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-r6-$script:Suffix"
    }
    return [string]$cleanup.result
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystem
        } catch {
            Write-Error "Cleanup created system failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$Suffix = Get-Date -Format 'MMddHHmmss'
$CleanupResult = 'SKIPPED'

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AdminAccessToken = [string]$AdminLogin.accessToken
$AdminHeaders = @{ Authorization = "Bearer $AdminAccessToken" }
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AdminAccessToken)) -Message 'Default admin login did not return an access token.'

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health' -Headers $AdminHeaders
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not all UP: $($Health | ConvertTo-Json -Compress)"

$CreatedSystem = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $AdminHeaders -Body @{
    systemName = "R6 Data Source System $Suffix"
    systemCode = "r6ds_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$SystemId = [string]$CreatedSystem.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($SystemId)) -Message 'Platform system create did not return systemId.'

$Switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $AdminHeaders -Body @{
    systemId = $SystemId
    tenantId = $null
    reason = 'recovery-r6-data-source'
}
$TenantId = [string]$Switch.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'System switch did not return tenantId.'

$Before = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/data-sources?pageNo=1&pageSize=20" -Headers $AdminHeaders
Assert-True -Condition ($null -ne $Before.records) -Message 'Data source list did not return records.'

$DataSource = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/data-sources" -Headers $AdminHeaders -Body @{
    sourceCode = "r6_internal_$Suffix"
    sourceName = "R6 Internal Data Source $Suffix"
    sourceType = 'SYSTEM_INTERNAL'
    tenantId = $TenantId
    connectionConfig = @{
        moduleScope = @('*')
    }
    authConfig = @{}
    syncConfig = @{
        mode = 'MANUAL'
    }
    desensitizeConfig = @{
        mode = 'PERMISSION'
    }
    status = 1
    idempotencyKey = "r6-data-source-create-$Suffix"
}
$DataSourceId = [string]$DataSource.dataSourceId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($DataSourceId)) -Message 'Data source create did not return dataSourceId.'

$Detail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/data-sources/$DataSourceId" -Headers $AdminHeaders
Assert-True -Condition ($Detail.sourceCode -eq "r6_internal_$Suffix") -Message 'Data source detail did not read back sourceCode.'

$ConnectionCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/data-sources/$DataSourceId/connection-check" -Headers $AdminHeaders
Assert-True -Condition ($ConnectionCheck.passed -eq $true) -Message 'Data source connection-check did not pass.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$ConnectionCheck.traceId)) -Message 'Data source connection-check did not return traceId.'

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/data-sources/$DataSourceId/publish-check" -Headers $AdminHeaders
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message 'Data source publish-check did not pass.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$PublishCheck.targetVersion)) -Message 'Data source publish-check did not return targetVersion.'

$After = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/data-sources?pageNo=1&pageSize=20" -Headers $AdminHeaders
$Ids = @($After.records | ForEach-Object { [string]$_.dataSourceId })
Assert-True -Condition ($Ids -contains $DataSourceId) -Message 'Created data source not visible in list readback.'

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-008'
    suffix = $Suffix
    system = [ordered]@{
        systemId = $SystemId
        tenantId = $TenantId
        dataSourceId = $DataSourceId
        sourceCode = $DataSource.sourceCode
        listBefore = $Before.total
        listAfter = $After.total
        connectionCheckTraceId = $ConnectionCheck.traceId
        publishCheckTraceId = $PublishCheck.traceId
        targetVersion = $PublishCheck.targetVersion
    }
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 30
