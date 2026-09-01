param(
    [Parameter(Mandatory = $true)][ValidateSet('RunSecurity','PrepareRotation','ActivateRotation','RunPerformance')][string]$Action,
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$AccessToken,
    [long]$SecretRefId,
    [long]$RotationId,
    [long]$SecurityRunId,
    [string[]]$ConsumerCodes = @(),
    [string]$ApprovalReference = '',
    [string]$Confirmation = ''
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/') + '/api/admin/system/operations/security-performance'
$headers = @{ Authorization = "Bearer $AccessToken"; 'X-Client-Source' = 'OPERATIONS_SCRIPT' }

function Invoke-OperationsApi([string]$Method, [string]$Path, [hashtable]$Body) {
    $arguments = @{ Method = $Method; Uri = $base + $Path; Headers = $headers; ContentType = 'application/json' }
    if ($Body) { $arguments.Body = ($Body | ConvertTo-Json -Depth 8 -Compress) }
    $response = Invoke-RestMethod @arguments
    if (-not $response.success) { throw "Operations API rejected the request: $($response.code)" }
    return $response.data
}

switch ($Action) {
    'RunSecurity' {
        $result = Invoke-OperationsApi 'POST' '/security-runs' @{
            approvalReference = $ApprovalReference
            confirmation = 'RUN SECURITY BASELINE'
        }
        if ($result.status -ne 'PASSED') { throw "Security baseline blocked rotation; runId=$($result.id)" }
        [ordered]@{ runId=$result.id; status=$result.status; findingCount=@($result.findings).Count } | ConvertTo-Json
    }
    'PrepareRotation' {
        if ($SecretRefId -le 0 -or $ConsumerCodes.Count -eq 0) { throw 'SecretRefId and ConsumerCodes are required' }
        $result = Invoke-OperationsApi 'POST' "/secret-refs/$SecretRefId/rotations" @{
            consumerCodes = $ConsumerCodes
            approvalReference = $ApprovalReference
            confirmation = $Confirmation
        }
        Write-Warning 'The following secret is displayed once. Distribute it only to the declared consumers; it is not written to evidence files.'
        Write-Output $result.oneTimeSecret
        [ordered]@{ rotationId=$result.rotation.id; fromVersion=$result.rotation.fromVersion; toVersion=$result.rotation.toVersion; status=$result.rotation.status } | ConvertTo-Json
    }
    'ActivateRotation' {
        if ($RotationId -le 0 -or $SecurityRunId -le 0 -or $ConsumerCodes.Count -eq 0) { throw 'RotationId, SecurityRunId and ConsumerCodes are required' }
        $checks = @($ConsumerCodes | ForEach-Object { @{ consumerCode=$_; status='PASSED'; evidenceReference=$ApprovalReference } })
        $result = Invoke-OperationsApi 'POST' "/rotations/$RotationId/activate" @{
            securityRunId = $SecurityRunId
            consumerChecks = $checks
            confirmation = $Confirmation
        }
        if (-not $result.switched) { throw "Rotation was not switched; status=$($result.status), failure=$($result.failureCode)" }
        [ordered]@{ rotationId=$result.id; status=$result.status; activeVersion=$result.activeVersion; oldVersionStatus=$result.oldVersionStatus } | ConvertTo-Json
    }
    'RunPerformance' {
        $result = Invoke-OperationsApi 'POST' '/performance-runs' @{
            pageSize=50; fileProbeBytes=65536; timeoutMillis=3000
            listThresholdMillis=500; fileThresholdMillis=500
            queueThresholdMillis=500; statisticsThresholdMillis=500
            approvalReference=$ApprovalReference
        }
        [ordered]@{ runId=$result.id; status=$result.status; optimizationTaskCount=@($result.findings).Count } | ConvertTo-Json
    }
}
