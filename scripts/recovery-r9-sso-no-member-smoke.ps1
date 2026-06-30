param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
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
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 20 -Compress)"
    }
    return $response.data
}

function Invoke-ExpectedFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$ExpectedStatus = 403,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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
        Assert-True -Condition ($status -eq $ExpectedStatus) -Message "Expected HTTP $ExpectedStatus for $Method $Path but got HTTP $status $body"
        return @{ status = $status; body = $body }
    }
    throw "Expected API failure for $Method $Path but request succeeded."
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:TargetSystemId, $script:RequesterOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r9-sso-no-member cleanup created system'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r9-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        $script:CleanupResult = Remove-CreatedSystems
    }
    throw $originalError
}

$Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:TargetSystemId = $null
$script:RequesterOwnedSystemId = $null
$script:AdminHeaders = $null
$CleanupResult = @()

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }
$AdminAccountId = [string]$AdminLogin.profile.accountId

$Target = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R9 SSO Target $Suffix"
    systemCode = "r9_sso_target_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:TargetSystemId = [string]$Target.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TargetOption = @($Options | Where-Object { [string]$_.systemId -eq $script:TargetSystemId }) | Select-Object -First 1
$TargetTenantId = [string]$TargetOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TargetTenantId)) -Message 'Target tenant id missing.'

$AdminSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r9 target setup'
}
Assert-True -Condition (@($AdminSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN') -Message 'Admin did not switch as system super admin.'
$AdminMemberId = [string]$AdminSwitch.systemMemberId

$Provider = Invoke-Api -Method 'Post' -Path '/api/v1/platform/identity-providers' -Headers $script:AdminHeaders -Body @{
    name = "R9 OIDC $Suffix"
    protocol = 'OIDC'
    issuer = "https://idp.r9-$Suffix.example.test"
    clientId = "r9-client-$Suffix"
    secretRefId = "sec_r9_$Suffix"
    certRefId = "cert_r9_$Suffix"
    domainWhitelist = @("r9-$Suffix.example.test")
    jitPolicy = @{ enabled = $true; createSystemMember = $false; requireApproval = $true }
    mfaPolicy = @{ inherited = $true }
    status = 'DRAFT'
}
$ProviderId = [string]$Provider.providerId
$ProviderTest = Invoke-Api -Method 'Post' -Path "/api/v1/platform/identity-providers/$ProviderId/test" -Headers $script:AdminHeaders -Body @{
    redirectUri = "$BaseUrl/sso/callback"
    testLoginName = "r9_no_member_$Suffix"
}
Assert-True -Condition ([bool]$ProviderTest.passed) -Message 'Identity provider test did not pass.'
$ProviderPublish = Invoke-Api -Method 'Post' -Path "/api/v1/platform/identity-providers/$ProviderId/publish" -Headers $script:AdminHeaders
Assert-True -Condition ($ProviderPublish.publishStatus -eq 'PUBLISHED') -Message 'Identity provider did not publish.'

$Policy = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$script:TargetSystemId/sso/policies" -Headers $script:AdminHeaders -Body @{
    enabledProviderIds = @($ProviderId)
    tenantDomains = @("r9-$Suffix.example.test")
    orgMapping = @{ externalDeptId = 'deptCode'; targetTree = 'systemDepartment' }
    employeeBinding = @{ matchKeys = @('email', 'employeeNo'); manualConfirmRequired = $true }
    jitMemberPolicy = @{ enabled = $true; createSystemMember = $false; requireApproval = $true }
    noMemberFeedback = @{ status = 'SUBMITTED'; disabledReason = 'NO_SYSTEM_MEMBER_MAPPING' }
    status = 'ACTIVE'
}
Assert-True -Condition (@($Policy.enabledProviderIds) -contains $ProviderId) -Message 'System SSO policy did not read back provider id.'

$Precheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/sso/org-sync/precheck" -Headers $script:AdminHeaders -Body @{
    identityProvider = $ProviderId
    tenantId = $TargetTenantId
    externalDepartmentIds = @("dept-r9-$Suffix")
    externalUserIds = @("ext-r9-$Suffix")
    dryRun = $true
    idempotencyKey = "r9-precheck-$Suffix"
}
Assert-True -Condition ($Precheck.unmatchedDepartmentCount -ge 1 -and $Precheck.unboundMemberCount -ge 1) -Message 'SSO precheck did not expose unmapped department/member issues.'

$RequesterLoginName = "r9_requester_$Suffix"
$RequesterRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $RequesterLoginName
    mobile = "18$Suffix"
    email = "r9_requester_$Suffix@example.com"
    password = $Password
    systemName = "R9 Requester Owned $Suffix"
    systemCode = "r9_requester_owned_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:RequesterOwnedSystemId = [string]$RequesterRegister.systemId
$RequesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $RequesterLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$RequesterHeaders = @{ Authorization = "Bearer $($RequesterLogin.accessToken)" }
$RequesterAccountId = [string]$RequesterLogin.profile.accountId

$SwitchBeforeApproval = Invoke-ExpectedFailure -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $RequesterHeaders -ExpectedStatus 403 -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'r9 should be blocked before no-member approval'
}

$CreatedRequest = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests" -Headers $RequesterHeaders -Body @{
    identityProvider = $ProviderId
    externalUserId = $RequesterAccountId
    tenantId = $TargetTenantId
    requestRole = 'SYSTEM_MEMBER'
    requestReason = 'Need access after SSO no-member mapping'
    idempotencyKey = "r9-no-member-$Suffix"
}
Assert-True -Condition ($CreatedRequest.status -eq 'SUBMITTED' -and -not [bool]$CreatedRequest.businessAccessAllowed) -Message 'No-member request was not submitted in blocked state.'

$RequesterOwnRequests = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests?pageNo=1&pageSize=20&status=SUBMITTED" -Headers $RequesterHeaders
Assert-True -Condition ([int]$RequesterOwnRequests.total -eq 1) -Message 'Requester could not read back exactly one own submitted no-member request.'

$AdminSubmittedRequests = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests?pageNo=1&pageSize=20&status=SUBMITTED" -Headers $script:AdminHeaders
$AdminRequestIds = @($AdminSubmittedRequests.records | ForEach-Object { [string]$_.requestId })
Assert-True -Condition ($AdminRequestIds -contains [string]$CreatedRequest.requestId) -Message 'System admin could not see submitted no-member request.'

$IncompleteApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests/$($CreatedRequest.requestId)/approve" -Headers $script:AdminHeaders -Body @{
    approverId = $AdminMemberId
    accountId = $RequesterAccountId
    systemMemberId = $null
    roleIds = @()
    dataScope = @{}
    approveComment = 'Incomplete review should not grant access'
    idempotencyKey = "r9-incomplete-$Suffix"
}
Assert-True -Condition ($IncompleteApprove.status -eq 'REVIEWING' -and -not [bool]$IncompleteApprove.businessAccessAllowed) -Message 'Incomplete approval did not remain in REVIEWING blocked state.'

$SwitchAfterIncomplete = Invoke-ExpectedFailure -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $RequesterHeaders -ExpectedStatus 403 -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'r9 should still be blocked after incomplete approval'
}

$Role = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R9 Approved Member $Suffix"
    roleCode = "R9_APPROVED_$Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R9 approved no-member role'
}
$Member = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R9 Approved Member $Suffix"
    employeeNo = "R9NM$Suffix"
    mobile = "16$Suffix"
    email = "r9_member_$Suffix@example.com"
    status = 1
    roleIds = @()
}

$FullApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests/$($CreatedRequest.requestId)/approve" -Headers $script:AdminHeaders -Body @{
    approverId = $AdminMemberId
    accountId = $RequesterAccountId
    systemMemberId = [string]$Member.systemMemberId
    roleIds = @([string]$Role.roleId)
    dataScope = @{ type = 'ALL'; source = 'recovery-r9' }
    approveComment = 'Grant target system member mapping'
    idempotencyKey = "r9-full-$Suffix"
}
Assert-True -Condition ($FullApprove.status -eq 'APPROVED' -and [bool]$FullApprove.businessAccessAllowed) -Message 'Full approval did not allow business access.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$FullApprove.accountMemberBindingId)) -Message 'Full approval did not return account-member binding id.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$FullApprove.ssoBindingId)) -Message 'Full approval did not return SSO binding id.'
Assert-True -Condition (@($FullApprove.roleIds) -contains [string]$Role.roleId) -Message 'Full approval did not preserve role id.'

$ApprovedRequests = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests?pageNo=1&pageSize=20&status=APPROVED" -Headers $script:AdminHeaders
$ApprovedReadback = @($ApprovedRequests.records | Where-Object { [string]$_.requestId -eq [string]$CreatedRequest.requestId }) | Select-Object -First 1
Assert-True -Condition ($null -ne $ApprovedReadback) -Message 'Approved request not visible in admin readback.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$ApprovedReadback.ssoBindingId)) -Message 'Approved readback did not include SSO binding id.'

$RequesterSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $RequesterHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'r9 switch after full no-member approval'
}
Assert-True -Condition ([string]$RequesterSwitch.systemMemberId -eq [string]$Member.systemMemberId) -Message 'Requester switch did not resolve to approved system member.'
Assert-True -Condition (@($RequesterSwitch.effectiveRoleIds) -contains [string]$Role.roleCode) -Message 'Requester switch did not include approved role code.'

$CleanupResult = Remove-CreatedSystems

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-014'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    suffix = $Suffix
    targetSystemId = $script:TargetSystemId
    targetTenantId = $TargetTenantId
    providerId = $ProviderId
    providerTestPassed = $ProviderTest.passed
    providerPublishStatus = $ProviderPublish.publishStatus
    ssoPolicyStatus = $Policy.status
    precheckUnmatchedDepartmentCount = $Precheck.unmatchedDepartmentCount
    precheckUnboundMemberCount = $Precheck.unboundMemberCount
    requesterAccount = $RequesterLoginName
    requesterAccountId = $RequesterAccountId
    requesterOwnedSystemId = $script:RequesterOwnedSystemId
    switchBeforeApprovalStatus = $SwitchBeforeApproval.status
    requestId = $CreatedRequest.requestId
    requesterOwnSubmittedTotal = $RequesterOwnRequests.total
    incompleteStatus = $IncompleteApprove.status
    incompleteBusinessAccessAllowed = $IncompleteApprove.businessAccessAllowed
    switchAfterIncompleteStatus = $SwitchAfterIncomplete.status
    approvedStatus = $FullApprove.status
    approvedBusinessAccessAllowed = $FullApprove.businessAccessAllowed
    approvedSystemMemberId = $FullApprove.systemMemberId
    accountMemberBindingId = $FullApprove.accountMemberBindingId
    ssoBindingId = $FullApprove.ssoBindingId
    approvedReadbackSsoBindingId = $ApprovedReadback.ssoBindingId
    requesterSwitchSystemMemberId = $RequesterSwitch.systemMemberId
    requesterEffectiveRoles = $RequesterSwitch.effectiveRoleIds
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 30
