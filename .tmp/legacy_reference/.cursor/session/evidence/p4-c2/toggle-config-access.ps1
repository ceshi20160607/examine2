param(
    [Parameter(Mandatory = $true)][ValidateSet('Grant', 'Revoke')][string]$Mode,
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [Parameter(Mandatory = $true)][string]$DatabasePassword,
    [string]$FixturePath = ''
)

$ErrorActionPreference = 'Stop'
if (-not $FixturePath) { $FixturePath = Join-Path $PSScriptRoot 'fixture.json' }
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$systemId = [long]$fixture.systemId
$roleId = [long]$fixture.roleId

$grant = @"
START TRANSACTION;
SET @permission_id=(SELECT id FROM un_plat_permission WHERE scope_type='SYSTEM' AND scope_key=$systemId AND permission_code='system.admin.access' AND status='ACTIVE' LIMIT 1);
SET @next_id=(SELECT COALESCE(MAX(id),0)+1 FROM un_plat_role_permission);
SET @actor_id=(SELECT created_by FROM un_plat_role_permission WHERE role_id=$roleId LIMIT 1);
INSERT INTO un_plat_role_permission (id,scope_type,scope_key,role_id,permission_id,effect,created_at,created_by)
SELECT @next_id,'SYSTEM',$systemId,$roleId,@permission_id,'ALLOW',NOW(),@actor_id
WHERE @permission_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM un_plat_role_permission WHERE scope_type='SYSTEM' AND scope_key=$systemId AND role_id=$roleId AND permission_id=@permission_id
);
SET @changed=ROW_COUNT();
UPDATE un_plat_authz_epoch SET epoch=epoch+IF(@changed=1,1,0),updated_at=NOW(),updated_by=@actor_id,version=version+IF(@changed=1,1,0)
WHERE scope_type='SYSTEM' AND scope_key=$systemId;
COMMIT;
"@

$revoke = @"
START TRANSACTION;
SET @permission_id=(SELECT id FROM un_plat_permission WHERE scope_type='SYSTEM' AND scope_key=$systemId AND permission_code='system.admin.access' AND status='ACTIVE' LIMIT 1);
SET @actor_id=(SELECT created_by FROM un_plat_role_permission WHERE role_id=$roleId LIMIT 1);
DELETE FROM un_plat_role_permission WHERE scope_type='SYSTEM' AND scope_key=$systemId AND role_id=$roleId AND permission_id=@permission_id;
SET @changed=ROW_COUNT();
UPDATE un_plat_authz_epoch SET epoch=epoch+IF(@changed=1,1,0),updated_at=NOW(),updated_by=@actor_id,version=version+IF(@changed=1,1,0)
WHERE scope_type='SYSTEM' AND scope_key=$systemId;
COMMIT;
"@

$statement = if ($Mode -eq 'Grant') { $grant } else { $revoke }
$null = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
    --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
    --batch --raw --skip-column-names -e $statement
if ($LASTEXITCODE -ne 0) { throw "Unable to $($Mode.ToLowerInvariant()) temporary configuration access" }

$count = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
    --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
    --batch --raw --skip-column-names -e "SELECT COUNT(*) FROM un_plat_role_permission rp JOIN un_plat_permission p ON p.id=rp.permission_id WHERE rp.scope_type='SYSTEM' AND rp.scope_key=$systemId AND rp.role_id=$roleId AND p.permission_code='system.admin.access';"
if ($LASTEXITCODE -ne 0) { throw 'Unable to verify temporary configuration access' }
$expected = if ($Mode -eq 'Grant') { 1 } else { 0 }
if ([int]$count -ne $expected) { throw "Temporary configuration access state is $count, expected $expected" }
Write-Output "temporaryConfigAccess=$Mode verified=$count"
