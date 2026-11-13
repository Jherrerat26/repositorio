param([string]$Configuration = "$PSScriptRoot/app/google-services.json")
$ErrorActionPreference = 'Stop'
$config = Get-Content -LiteralPath $Configuration -Raw | ConvertFrom-Json
if ($config.project_info.project_id -ne 'carrito-bluetooth') { throw 'Proyecto Firebase inesperado.' }
$apiKey = $config.client[0].api_key[0].current_key
$databaseUrl = $config.project_info.firebase_url.TrimEnd('/')
$createdAccounts = [System.Collections.Generic.List[object]]::new()
$testUid = $null

function New-TestAccount {
    $account = Invoke-RestMethod -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey" -Method Post -ContentType 'application/json' -Body '{"returnSecureToken":true}' -TimeoutSec 30
    $createdAccounts.Add($account)
    return $account
}

function Assert-Denied([string]$Uri, [string]$Method = 'Get', [string]$Body = '') {
    try {
        if ($Method -eq 'Put') {
            $null = Invoke-RestMethod -Uri $Uri -Method Put -ContentType 'application/json' -Body $Body -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 30
        }
    } catch {
        if ([int]$_.Exception.Response.StatusCode -in @(400, 401, 403)) { return }
        throw 'La comprobación de acceso falló por un error de red o servicio.'
    }
    throw 'Una operación que debía ser denegada fue aceptada.'
}

try {
    $owner = New-TestAccount
    $other = New-TestAccount
    $testUid = $owner.localId
    $eventId = [guid]::NewGuid().ToString()
    $path = "$databaseUrl/users/$testUid/movements/$eventId.json"
    $record = @{
        command = 'F'; direction = 'Adelante'; speed = 140
        timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        deviceName = 'Prueba técnica de conexión (no es movimiento real)'
        deviceAddress = 'TEST'; sessionId = [guid]::NewGuid().ToString()
    }
    $body = $record | ConvertTo-Json -Compress
    $null = Invoke-RestMethod -Uri "$path`?auth=$($owner.idToken)" -Method Put -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
    $read = Invoke-RestMethod -Uri "$path`?auth=$($owner.idToken)" -Method Get -TimeoutSec 30
    if ($read.command -ne 'F' -or $read.speed -ne 140) { throw 'Los datos leídos no coinciden con el registro enviado.' }
    Assert-Denied -Uri $path
    Assert-Denied -Uri "$path`?auth=$($other.idToken)"
    $record.speed = 999
    Assert-Denied -Uri "$databaseUrl/users/$testUid/movements/invalid-speed.json?auth=$($owner.idToken)" -Method Put -Body ($record | ConvertTo-Json -Compress)
    Write-Output 'OK: acceso anónimo, escritura, lectura propia, rechazo sin autenticación, aislamiento entre usuarios y validación de velocidad.'
    Write-Output "Nodo temporal de verificación para retirar desde Firebase console: /users/$testUid"
} finally {
    foreach ($account in $createdAccounts) {
        try {
            $null = Invoke-RestMethod -Uri "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$apiKey" -Method Post -ContentType 'application/json' -Body (@{idToken=$account.idToken} | ConvertTo-Json -Compress) -TimeoutSec 30
        } catch {
            Write-Warning 'No se pudo retirar una cuenta anónima temporal de verificación.'
        }
    }
}
