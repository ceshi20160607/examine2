param(
    [string]$FixturePath = "$PSScriptRoot/task-02-fixture.json",
    [string]$RuntimePath = "$PSScriptRoot/task-02-runtime.json",
    [string]$OutputPath = "$PSScriptRoot/task-02-restart.json"
)

$ErrorActionPreference = 'Stop'
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$runtime = Get-Content -LiteralPath $RuntimePath -Raw | ConvertFrom-Json
$baseUrl = [string]$fixture.baseUrl
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-Envelope([string]$Path, [string]$Method, [object]$Body) {
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET','HEAD')) {
        $csrf = ($session.Cookies.GetCookies($baseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    $arguments = @{
        Uri = "$baseUrl$Path"; Method = $Method; Headers = $headers; WebSession = $session
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 'OK') { throw "API $Path returned $($response.code)" }
    return $response.data
}

$null = Invoke-Envelope '/api/v1/auth/login' 'POST' @{
    account = [string]$fixture.username; password = [string]$fixture.password
}
$null = Invoke-Envelope "/api/v1/context/systems/$($fixture.systemId):switch" 'POST' @{}
$root = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$query = Invoke-Envelope "$root/records:query" 'POST' @{
    schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 50
    recordScope = 'active'; q = $null
    filter = @{ kind='PREDICATE'; fieldCode='completion_rate'; operator='EQ'; value=50 }
    sort = @(); columns = @(); viewId = $null
}
$expectedRecordId = [string]$runtime.activeRecordIds[0]
if (@($query.rows).Count -ne 1 -or [string]$query.rows[0].recordId -ne $expectedRecordId) {
    throw 'Restart query did not return the reserved active record'
}

$draftId = [string]$runtime.releasedRecordIds[1]
$request = [System.Net.HttpWebRequest]::Create("$baseUrl$root/records/${draftId}:activate")
$request.Method = 'POST'
$request.ContentType = 'application/json; charset=utf-8'
$request.CookieContainer = $session.Cookies
$request.Headers.Add('X-Request-ID', [guid]::NewGuid().ToString())
$request.Headers.Add('Idempotency-Key', [guid]::NewGuid().ToString())
$csrf = ($session.Cookies.GetCookies($baseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
$request.Headers.Add('X-CSRF-Token', [uri]::UnescapeDataString($csrf))
$bytes = [System.Text.Encoding]::UTF8.GetBytes('{"expectedVersion":0}')
$request.ContentLength = $bytes.Length
$stream = $request.GetRequestStream()
$stream.Write($bytes, 0, $bytes.Length)
$stream.Close()
$status = 0
$code = $null
try {
    $response = $request.GetResponse()
    $response.Close()
} catch [System.Net.WebException] {
    $response = $_.Exception.Response
    $status = [int]$response.StatusCode
    $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
    $payload = $reader.ReadToEnd() | ConvertFrom-Json
    $reader.Close()
    $response.Close()
    $code = [string]$payload.code
}
if ($status -ne 409 -or $code -ne 'FIELD_UNIQUE_CONFLICT') {
    throw "Restart unique enforcement expected 409/FIELD_UNIQUE_CONFLICT but received $status/$code"
}

$rows = & docker run --rm --env MYSQL_PWD=examine mysql:8.4 mysql --protocol=tcp `
    -h 192.168.0.211 -P 3306 -u examine -D examine2 --batch --raw --skip-column-names `
    -e "SELECT COUNT(*) FROM un_module_record_unique WHERE system_id=$($fixture.systemId)"
if ($LASTEXITCODE -ne 0 -or [long](@($rows)[0]) -ne 3) {
    throw 'Restart unique reservation count is not three'
}

$result = [ordered]@{
    verifiedAt = (Get-Date).ToString('o')
    processStart = (Get-Process java | Sort-Object StartTime -Descending | Select-Object -First 1).StartTime.ToString('o')
    queryRecordId = $expectedRecordId
    conflictDraftId = $draftId
    conflictStatus = $status
    conflictCode = $code
    uniqueRows = 3
    result = 'PASS'
}
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
$result | ConvertTo-Json -Depth 5
