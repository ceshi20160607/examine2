param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Read-Json([string]$RelativePath) {
    $path = Join-Path $Root $RelativePath
    return Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Property-Value($Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Assert-ExactSet($Actual, $Expected, [string]$Message) {
    $diff = @(Compare-Object @($Expected | Sort-Object -Unique) @($Actual | Sort-Object -Unique))
    Assert-True ($diff.Count -eq 0) "$Message`n$($diff | Out-String)"
}

function Resolve-RepositoryPath([string]$RelativePath, [string]$Label) {
    Assert-True (-not [IO.Path]::IsPathRooted($RelativePath)) "$Label must be repository-relative."
    $resolved = [IO.Path]::GetFullPath((Join-Path $Root $RelativePath))
    $rootPrefix = $Root.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    Assert-True ($resolved.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) "$Label escapes the repository root."
    return $resolved
}

$fieldContract = Read-Json '.cursor/session/rebuild/vs4-field-contract.json'
$apiContract = Read-Json '.cursor/session/rebuild/vs4-api-contract.schema.json'
$verificationContract = Read-Json '.cursor/session/rebuild/vs4-verification-contract.json'
$state = Read-Json '.cursor/session/state.json'
$planPath = Join-Path $Root '.cursor/session/rebuild/vs4-task-plan.md'
$plan = Get-Content -LiteralPath $planPath -Raw -Encoding UTF8
$roadmapPath = Join-Path $Root '.cursor/session/delivery-roadmap.md'
$roadmap = Get-Content -LiteralPath $roadmapPath -Raw -Encoding UTF8

Assert-True ($fieldContract.schemaVersion -eq 2) 'VS4 field contract schemaVersion must be 2.'
Assert-True ($fieldContract.types.Count -eq 49) "VS4 field contract must contain 49 types, got $($fieldContract.types.Count)."
Assert-ExactSet @($fieldContract.types.ordinal) @(1..49) 'VS4 field ordinals must be exactly 1..49.'
Assert-True ((@($fieldContract.types.type | Sort-Object -Unique)).Count -eq 49) 'VS4 field type names must be unique.'

$sourcePath = Join-Path $Root 'backend/examine-module/src/main/java/com/unique/examine/module/manage/api/ConfigTypes.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$fieldBlock = [regex]::Match($source, 'enum FieldType\s*\{(?<body>.*?)\}', 'Singleline').Groups['body'].Value
$codeTypes = @([regex]::Matches($fieldBlock, '\b[A-Z][A-Z0-9_]+\b') | ForEach-Object { $_.Value } | Sort-Object -Unique)
$contractTypes = @($fieldContract.types.type | Sort-Object -Unique)
Assert-ExactSet $contractTypes $codeTypes 'VS4 field contract does not match ConfigTypes.FieldType.'

$fieldPeriodMap = [regex]::Match($roadmap, '(?s)field_period_map:start(?<body>.*?)field_period_map:end')
Assert-True $fieldPeriodMap.Success 'Delivery roadmap has no machine-readable field period map.'
$mappedTypes = @([regex]::Matches($fieldPeriodMap.Groups['body'].Value, '`([A-Z][A-Z0-9_]*)`') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -in $contractTypes })
Assert-True ($mappedTypes.Count -eq 49) "Delivery roadmap must assign exactly 49 canonical field types, got $($mappedTypes.Count)."
Assert-True ((@($mappedTypes | Sort-Object -Unique)).Count -eq 49) 'Delivery roadmap assigns at least one canonical field type more than once.'
Assert-ExactSet $mappedTypes $contractTypes 'Delivery roadmap field periods do not cover the canonical field contract exactly once.'

$caseCatalog = @($fieldContract.caseCatalog)
Assert-True ($caseCatalog.Count -eq (@($caseCatalog | Sort-Object -Unique)).Count) 'VS4 caseCatalog contains duplicate names.'
$modeProperties = @($fieldContract.caseApplicabilityByMode.PSObject.Properties)
$operatorCaseProperties = @($fieldContract.caseApplicabilityByOperatorPolicy.PSObject.Properties)
$conditionalCaseNames = @($fieldContract.conditionalCaseApplicability.PSObject.Properties.Name)
Assert-ExactSet @($operatorCaseProperties.Name) @('EXPLICIT', 'RESULT_SCHEMA', 'NONE') 'Operator policy case mapping is incomplete.'
foreach ($property in @($modeProperties + $operatorCaseProperties)) {
    foreach ($caseName in @($property.Value)) {
        Assert-True ($caseName -in $caseCatalog) "Case policy $($property.Name) references unknown case $caseName."
    }
}
foreach ($caseName in $conditionalCaseNames) {
    Assert-True ($caseName -in $caseCatalog) "Conditional policy references unknown case $caseName."
}

$fixtureNames = @($fieldContract.fixtureProfiles.PSObject.Properties.Name)
$modeNames = @($modeProperties.Name)
$operatorProfiles = @($fieldContract.operatorVectorProfiles.PSObject.Properties.Name)
$allowedTypedColumns = @('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'TIME', 'BOOLEAN', 'GEO_HASH', 'HASH')
$allowedIndexKinds = @('TYPED', 'SEARCH_TABLE', 'HMAC_EQUALITY', 'MONEY_TYPED', 'RELATION_TABLE', 'RESULT_TYPED', 'SUBTABLE_TABLE', 'TYPED_WITH_EXACT_POST_FILTER', 'JSON_PATH_TYPED')
$allowedUniquePolicies = @('NONE', 'VALUE', 'ELEMENT')
$normalizationModes = @('WRITABLE', 'WRITABLE_TRANSITION', 'SENSITIVE_WRITABLE')

foreach ($type in $fieldContract.types) {
    Assert-True ($type.fixtureProfile -in $fixtureNames) "Field $($type.type) references missing fixture profile $($type.fixtureProfile)."
    Assert-True ($type.mode -in $modeNames) "Field $($type.type) references missing mode policy $($type.mode)."
    Assert-True ($type.minCardinality -le $type.maxCardinality) "Field $($type.type) has invalid cardinality."
    Assert-True (-not [string]::IsNullOrWhiteSpace($type.normalizerId)) "Field $($type.type) has no normalizerId."
    Assert-True ($type.uniquePolicy -in $allowedUniquePolicies) "Field $($type.type) has unknown uniquePolicy $($type.uniquePolicy)."

    $fixture = Property-Value $fieldContract.fixtureProfiles $type.fixtureProfile
    if ($type.mode -ne 'DEFERRED') {
        Assert-True (@($fixture.legalVectors).Count -gt 0) "Field $($type.type) has no legal vector."
        Assert-True (@($fixture.illegalVectors).Count -gt 0) "Field $($type.type) has no illegal vector."
    }
    if ($type.mode -in $normalizationModes) {
        Assert-True (@($fixture.normalizationVectors).Count -gt 0) "Writable field $($type.type) has no executable normalization vector."
    }

    $operatorCases = @(Property-Value $fieldContract.caseApplicabilityByOperatorPolicy $type.operatorPolicy)
    if ($type.operatorPolicy -eq 'EXPLICIT') {
        Assert-ExactSet $operatorCases @('EACH_EXPLICIT_OPERATOR') "Field $($type.type) EXPLICIT case mapping is invalid."
        Assert-True ($type.operatorVectorProfile -in $operatorProfiles) "Field $($type.type) has no operator vector profile."
        $profile = Property-Value $fieldContract.operatorVectorProfiles $type.operatorVectorProfile
        Assert-True (@($profile.fixtureValues).Count -gt 0) "Field $($type.type) operator profile has no fixture values."
        $operatorKeys = @($profile.expectedByOperator.PSObject.Properties.Name | ForEach-Object { ($_ -split ':', 2)[0] } | Sort-Object -Unique)
        foreach ($operator in $type.operators) {
            Assert-True ($operator -in $operatorKeys) "Field $($type.type) operator $operator has no executable vector."
        }
    } elseif ($type.operatorPolicy -eq 'RESULT_SCHEMA') {
        Assert-ExactSet $operatorCases @('RESULT_SCHEMA_OPERATOR_EXPANSION') "Field $($type.type) RESULT_SCHEMA case mapping is invalid."
        Assert-True ($type.operators.Count -eq 0) "Field $($type.type) RESULT_SCHEMA policy cannot declare placeholder operators."
    } elseif ($type.operatorPolicy -eq 'NONE') {
        Assert-True ($operatorCases.Count -eq 0) "Field $($type.type) NONE policy cannot add operator cases."
        Assert-True ($type.operators.Count -eq 0) "Field $($type.type) NONE policy cannot declare operators."
    } else {
        throw "Field $($type.type) has unknown operatorPolicy $($type.operatorPolicy)."
    }

    foreach ($strategy in $type.indexStrategies) {
        Assert-True ($strategy.kind -in $allowedIndexKinds) "Field $($type.type) has unknown index kind $($strategy.kind)."
        if ($null -ne $strategy.typedColumn) {
            Assert-True ($strategy.typedColumn -in $allowedTypedColumns) "Field $($type.type) has unknown typed column $($strategy.typedColumn)."
        }
    }

    if ($type.mode -eq 'DEFERRED') {
        Assert-True ($type.targetSlice -in @('VS5', 'VS6', 'VS10')) "Deferred field $($type.type) has no valid targetSlice."
    }
}

$moneyType = @($fieldContract.types | Where-Object { $_.type -eq 'MONEY' })[0]
$moneyStrategy = @($moneyType.indexStrategies | Where-Object { $_.kind -eq 'MONEY_TYPED' })[0]
Assert-True ($moneyType.operatorVectorProfile -eq 'MONEY') 'MONEY must use the currency-aware MONEY operator profile.'
Assert-True ($moneyStrategy.typedColumn -eq 'DECIMAL' -and 'CURRENCY' -in @($moneyStrategy.companionColumns)) 'MONEY index must bind DECIMAL and CURRENCY.'
$moneyFixture = Property-Value $fieldContract.fixtureProfiles 'money'
Assert-True (@($moneyFixture.comparisonVectors).Count -ge 2) 'MONEY requires same-currency and cross-currency comparison vectors.'

$resultPolicyProperties = @($fieldContract.resultTypePolicies.PSObject.Properties)
$resultPolicyNames = @($resultPolicyProperties.Name)
Assert-ExactSet $resultPolicyNames @('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN') 'Result type policies are incomplete.'
foreach ($policy in $resultPolicyProperties) {
    Assert-True ($policy.Value.typedColumn -in $allowedTypedColumns) "Result type $($policy.Name) has unknown typed column."
    Assert-True ($policy.Value.operators.Count -gt 0) "Result type $($policy.Name) has no operators."
    Assert-True ($policy.Value.operatorVectorProfile -in $operatorProfiles) "Result type $($policy.Name) has no operator vector profile."
    $profile = Property-Value $fieldContract.operatorVectorProfiles $policy.Value.operatorVectorProfile
    $operatorKeys = @($profile.expectedByOperator.PSObject.Properties.Name | ForEach-Object { ($_ -split ':', 2)[0] } | Sort-Object -Unique)
    foreach ($operator in $policy.Value.operators) {
        Assert-True ($operator -in $operatorKeys) "Result type $($policy.Name) operator $operator has no executable vector."
    }
}

$expectedDerivedTypes = @('REFERENCE', 'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP')
Assert-ExactSet @($fieldContract.derivedFixtureByType.PSObject.Properties.Name) $expectedDerivedTypes 'Derived fixture map is incomplete.'
foreach ($derivedTypeName in $expectedDerivedTypes) {
    $type = @($fieldContract.types | Where-Object { $_.type -eq $derivedTypeName })[0]
    $fixtureName = Property-Value $fieldContract.derivedFixtureByType $derivedTypeName
    Assert-True ($type.fixtureProfile -eq $fixtureName) "Derived type $derivedTypeName does not use its dedicated fixture."
    $fixture = Property-Value $fieldContract.fixtureProfiles $fixtureName
    Assert-True (@($fixture.recalculationVectors).Count -gt 0) "Derived type $derivedTypeName has no recalculation vector."
    Assert-ExactSet @($fixture.resultVectors.resultSchema) $resultPolicyNames "Derived type $derivedTypeName does not cover every result schema."
    foreach ($vector in $fixture.resultVectors) {
        $policy = Property-Value $fieldContract.resultTypePolicies $vector.resultSchema
        Assert-True ($vector.typedColumn -eq $policy.typedColumn) "Derived type $derivedTypeName has wrong typed column for $($vector.resultSchema)."
        Assert-True ($vector.operatorVectorProfile -eq $policy.operatorVectorProfile) "Derived type $derivedTypeName has wrong operator profile for $($vector.resultSchema)."
    }
}

$edgeKeys = @{}
foreach ($edge in $fieldContract.migrationEdges) {
    Assert-True ($edge.sourceType -in $contractTypes) "Migration edge has unknown source $($edge.sourceType)."
    Assert-True ($edge.targetType -in $contractTypes) "Migration edge has unknown target $($edge.targetType)."
    $edgeKey = "$($edge.sourceType)->$($edge.targetType)"
    Assert-True (-not $edgeKeys.ContainsKey($edgeKey)) "Duplicate migration edge $edgeKey."
    $edgeKeys[$edgeKey] = $true
    Assert-True (-not [string]::IsNullOrWhiteSpace($edge.converterId)) "Migration edge $edgeKey has no converterId."
    Assert-True (-not [string]::IsNullOrWhiteSpace($edge.failurePolicy)) "Migration edge $edgeKey has no failurePolicy."
    if ($edge.reversible) {
        Assert-True (-not [string]::IsNullOrWhiteSpace($edge.reverseConverterId)) "Reversible edge $edgeKey has no reverse converter."
    }

    $sourceType = @($fieldContract.types | Where-Object { $_.type -eq $edge.sourceType })[0]
    $targetType = @($fieldContract.types | Where-Object { $_.type -eq $edge.targetType })[0]
    if ($sourceType.maxCardinality -gt $targetType.maxCardinality) {
        $blocksCardinality = $edge.failurePolicy -match 'CARDINALITY|TARGET_LIMIT'
        Assert-True ($edge.lossy -or $blocksCardinality) "Cardinality-reducing edge $edgeKey is not lossy or blocking."
    }
}

$defs = $apiContract.PSObject.Properties['$defs'].Value
$defNames = @($defs.PSObject.Properties.Name)
$endpoints = @($apiContract.'x-endpointContracts')
Assert-True ($endpoints.Count -eq 43) "VS4 API schema must contain exactly 43 endpoints, got $($endpoints.Count)."
Assert-True ((@($endpoints.id | Sort-Object -Unique)).Count -eq 43) 'VS4 endpoint IDs must be unique.'
Assert-True ((@($endpoints | ForEach-Object { "$($_.method) $($_.path)" } | Sort-Object -Unique)).Count -eq 43) 'VS4 method/path pairs must be unique.'
foreach ($endpoint in $endpoints) {
    foreach ($ref in @($endpoint.pathParamsRef, $endpoint.queryRef, $endpoint.bodyRef, $endpoint.responseRef, $endpoint.authorizationRef) + @($endpoint.errorRefs.PSObject.Properties.Value)) {
        if ($null -eq $ref) { continue }
        Assert-True ($ref -match '^#/\$defs/(?<name>[A-Za-z][A-Za-z0-9]+)$') "Endpoint $($endpoint.id) has invalid ref $ref."
        Assert-True ($Matches['name'] -in $defNames) "Endpoint $($endpoint.id) has dangling ref $ref."
    }
}
$requiredDtos = @(
    'ErrorResponse', 'RecordSchemaResponse', 'RecordQuery', 'RecordListResponse', 'RecordDetailResponse',
    'CreateRecordRequest', 'UpdateRecordRequest', 'RecordWriteResponse', 'VersionCommandRequest',
    'TransferRecordRequest', 'AutosaveRecordRequest', 'AutosaveRecordResponse', 'RecordMutationResponse',
    'DraftQueryRequest', 'NeighborRequest', 'NeighborResponse', 'BatchCapabilityRequest',
    'BatchCapabilityResponse', 'BatchEditRequest', 'BatchTransferRequest', 'BatchCommandRequest',
    'BatchResponse', 'RelationInput', 'SubtableInput', 'TeamInput', 'RelationMutationRequest',
    'SubtableMutationRequest', 'TeamMutationRequest', 'CommentCreateRequest', 'CommentUpdateRequest',
    'CommentDeleteRequest', 'HistoryPageResponse', 'SavedViewCreateRequest', 'SavedViewUpdateRequest',
    'FavoriteCreateRequest', 'SearchRequest', 'SearchResponse', 'EndpointContract', 'AuthorizationBinding',
    'RecordVersionConflictErrorResponse', 'RateLimitErrorResponse', 'BatchPreconditionErrorResponse',
    'MutationReceipt', 'FailureReceipt', 'CollaborationCapabilities'
)
foreach ($dto in $requiredDtos) {
    Assert-True ($dto -in $defNames) "VS4 API schema is missing DTO $dto."
}

$apiValidator = Join-Path $Root '.cursor/scripts/validate-vs4-api-contract.mjs'
$ajvPackage = Join-Path $Root 'frontend/node_modules/ajv/package.json'
Assert-True (Test-Path -LiteralPath $ajvPackage) 'Ajv is not installed; run npm.cmd ci in frontend before contract validation.'
$apiValidationOutput = @(& node $apiValidator 2>&1)
Assert-True ($LASTEXITCODE -eq 0) "VS4 Draft 2020-12 validation failed:`n$($apiValidationOutput -join [Environment]::NewLine)"
$apiValidationOutput | Write-Output

$tableCount = ([regex]::Matches($plan, '(?m)^\| \d+ \| `un_module_[^`]+` \|')).Count
$actionCount = ([regex]::Matches($plan, '(?m)^\| (CREATE|UPDATE|DELETE|CUSTOM|APPROVAL|IMPORT|EXPORT|PRINT) \|')).Count
Assert-True ($tableCount -eq 20) "VS4 plan must contain 20 table rows, got $tableCount."
Assert-True ($actionCount -eq 8) "VS4 plan must contain 8 action rows, got $actionCount."

$requiredPlanTokens = @(
    'SG-06', 'INDEX_GENERATION_DELTA', 'MutationReceipt', 'VARBINARY(128)', 'scope-token',
    'AUTOSAVE | FP01', 'ARCHIVE / TRASH | FP01', 'UNARCHIVE | FP01', 'RESTORE_FROM_TRASH | FP01',
    'DISCARD | FP01', 'RECOVER | FP01', 'DUPLICATE | FP01', 'x-endpointContracts'
)
foreach ($token in $requiredPlanTokens) {
    Assert-True ($plan.Contains($token)) "VS4 plan is missing required contract token: $token"
}

$expectedCapabilityIds = @()
$expectedCapabilityIds += @(1..9 | ForEach-Object { 'VS4-B3-{0:D2}' -f $_ })
$expectedCapabilityIds += @(1..9 | ForEach-Object { 'VS4-B4-{0:D2}' -f $_ })
$expectedCapabilityIds += @(1..9 | ForEach-Object { 'VS4-B5-{0:D2}' -f $_ })
$expectedCapabilityIds += @(1..8 | ForEach-Object { 'VS4-B11-{0:D2}' -f $_ })
$ledgerCandidateLines = @([regex]::Matches($plan, '(?m)^\| VS4-B(?:3|4|5|11)-[^\r\n]+$'))
$ledgerPattern = '(?m)^\| (VS4-B(?:3|4|5|11)-\d{2}) \| ([^|]+) \| ([A-Z_]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$'
$ledgerRows = @([regex]::Matches($plan, $ledgerPattern))
Assert-True ($ledgerCandidateLines.Count -eq $ledgerRows.Count) 'At least one VS4 capability ledger row is malformed and was not parsed.'
Assert-True ($ledgerRows.Count -eq 35) "VS4 capability ledger must contain exactly 35 rows, got $($ledgerRows.Count)."
$ledgerIds = @($ledgerRows | ForEach-Object { $_.Groups[1].Value.Trim() })
Assert-True ((@($ledgerIds | Sort-Object -Unique)).Count -eq 35) 'VS4 capability IDs must be unique.'
Assert-ExactSet $ledgerIds $expectedCapabilityIds 'VS4 capability ledger ID set is incomplete.'

$baseline = @{}
foreach ($property in @($state.vs4CapabilityLedgerBaseline.PSObject.Properties)) {
    foreach ($id in @($property.Value)) {
        Assert-True (-not $baseline.ContainsKey($id)) "Capability baseline repeats $id."
        $baseline[$id] = $property.Name
    }
}
Assert-ExactSet @($baseline.Keys) $expectedCapabilityIds 'VS4 capability baseline ID set is incomplete.'
$allowedStates = @('NOT_STARTED', 'IMPLEMENTED', 'TESTED', 'ACCEPTED', 'DEFERRED')
$allowedNext = @{
    'NOT_STARTED' = @('NOT_STARTED', 'IMPLEMENTED')
    'IMPLEMENTED' = @('IMPLEMENTED', 'TESTED')
    'TESTED' = @('TESTED', 'ACCEPTED')
    'ACCEPTED' = @('ACCEPTED')
    'DEFERRED' = @('DEFERRED')
}
$evidencePolicy = $verificationContract.capabilityEvidencePolicy
$acceptedEvidenceRoot = Resolve-RepositoryPath $evidencePolicy.acceptedEvidenceRoot 'Accepted evidence root'
$acceptedEvidencePrefix = $acceptedEvidenceRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$manifestRows = @()
$requiresManifest = @($ledgerRows | Where-Object { $_.Groups[3].Value.Trim() -in @('TESTED', 'ACCEPTED') }).Count -gt 0
if ($requiresManifest) {
    $manifestPath = Resolve-RepositoryPath $evidencePolicy.testManifestPath 'Capability test manifest'
    Assert-True (Test-Path -LiteralPath $manifestPath -PathType Leaf) 'TESTED/ACCEPTED capabilities require the machine test manifest.'
    $manifestRows = @(Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json)
    Assert-True ($manifestRows.Count -gt 0) 'Capability test manifest is empty.'
    foreach ($manifestRow in $manifestRows) {
        foreach ($requiredField in @($evidencePolicy.requiredManifestFields)) {
            $property = $manifestRow.PSObject.Properties[$requiredField]
            Assert-True ($null -ne $property) "Capability test manifest row is missing $requiredField."
        }
        Assert-True ($manifestRow.capabilityId -in $expectedCapabilityIds) "Manifest has unknown capability $($manifestRow.capabilityId)."
        Assert-True ($manifestRow.exitCode -eq 0) "Manifest test $($manifestRow.testId) did not exit successfully."
        Assert-True ($manifestRow.artifactSha256 -match '^[a-f0-9]{64}$') "Manifest test $($manifestRow.testId) has an invalid artifact hash."
        Assert-True (-not [string]::IsNullOrWhiteSpace($manifestRow.command) -and -not [string]::IsNullOrWhiteSpace($manifestRow.caseId)) "Manifest test $($manifestRow.testId) lacks command/case identity."
        $testFile = Resolve-RepositoryPath $manifestRow.testFile "Manifest test file $($manifestRow.testId)"
        Assert-True (Test-Path -LiteralPath $testFile -PathType Leaf) "Manifest test file does not exist: $($manifestRow.testFile)"
        if (@($manifestRow.viewports).Count -gt 0) {
            Assert-ExactSet @($manifestRow.viewports) @($evidencePolicy.requiredViewportsForBrowserCapability) "Manifest browser viewports are incomplete for $($manifestRow.testId)."
        }
    }
    $manifestKeys = @($manifestRows | ForEach-Object { "$($_.capabilityId)|$($_.testId)" })
    Assert-True ((@($manifestKeys | Sort-Object -Unique)).Count -eq $manifestKeys.Count) 'Capability test manifest contains duplicate capability/test rows.'
}
foreach ($row in $ledgerRows) {
    $id = $row.Groups[1].Value.Trim()
    $status = $row.Groups[3].Value.Trim()
    $targetSlice = $row.Groups[4].Value.Trim()
    $testId = $row.Groups[5].Value.Trim()
    $evidencePath = $row.Groups[6].Value.Trim()
    Assert-True ($status -in $allowedStates) "Capability $id has unknown status $status."
    Assert-True ($status -in $allowedNext[$baseline[$id]]) "Capability $id regressed or skipped a state: $($baseline[$id]) -> $status."
    if ($status -eq 'NOT_STARTED') {
        Assert-True ($targetSlice -eq '-' -and $testId -eq 'pending' -and $evidencePath -eq 'pending') "NOT_STARTED capability $id must remain pending."
    }
    if ($status -in @('TESTED', 'ACCEPTED')) {
        Assert-True ($testId -ne 'pending') "TESTED capability $id has no testId."
        $matchingManifest = @($manifestRows | Where-Object { $_.capabilityId -eq $id -and $_.testId -eq $testId })
        Assert-True ($matchingManifest.Count -eq 1) "Capability $id must have exactly one matching machine test manifest row."
    }
    if ($status -eq 'ACCEPTED') {
        Assert-True ($testId -ne 'pending' -and $evidencePath -ne 'pending') "ACCEPTED capability $id has no test/evidence."
        $acceptedPath = Resolve-RepositoryPath $evidencePath "ACCEPTED capability $id evidence path"
        Assert-True ($acceptedPath.StartsWith($acceptedEvidencePrefix, [StringComparison]::OrdinalIgnoreCase)) "ACCEPTED capability $id evidence is outside the approved VS4 evidence root."
        Assert-True (Test-Path -LiteralPath $acceptedPath -PathType Leaf) "ACCEPTED capability $id evidence file does not exist."
        $actualHash = (Get-FileHash -LiteralPath $acceptedPath -Algorithm SHA256).Hash.ToLowerInvariant()
        Assert-True ($actualHash -eq $matchingManifest[0].artifactSha256) "ACCEPTED capability $id artifact hash does not match the manifest."
    }
    if ($status -eq 'DEFERRED') {
        Assert-True ($targetSlice -match '^VS(5|6|10)$') "Deferred capability $id has no target slice."
        Assert-True ($testId -match '^BOUNDARY-VS4-B(?:3|4|5|11)-\d{2}$') "Deferred capability $id has no boundary testId."
        Assert-True ($evidencePath -match '^planned:\.cursor/session/evidence/vs4/boundaries/.+\.json$') "Deferred capability $id has no planned boundary evidence path."
    }
}

Write-Output "VS4 contract validation passed: fields=49 tables=$tableCount actions=$actionCount ledger=$($ledgerRows.Count) endpoints=$($endpoints.Count) defs=$($defNames.Count)"
