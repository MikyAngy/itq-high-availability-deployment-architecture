# Script reutilizable para medir disponibilidad.
# Uso: .\test-availability.ps1 -OutputFile <nombre_archivo>
param (
    [Parameter(Mandatory = $true)]
    [string]$OutputFile
)

if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    Write-Host "Error: Debes proporcionar el nombre del archivo de salida" -ForegroundColor Red
    Write-Host "Uso: .\test-availability.ps1 -OutputFile <nombre_archivo>" -ForegroundColor Yellow
    exit 1
}

if (Test-Path $OutputFile) {
    Remove-Item $OutputFile
}

$endpointUrl = "http://localhost:8080/v1/products"
$numberOfRequests = 200

Write-Host "Ejecutando $numberOfRequests solicitudes a $endpointUrl" -ForegroundColor Cyan
Write-Host "Guardando en: $OutputFile" -ForegroundColor Cyan

for ($i = 1; $i -le $numberOfRequests; $i++) {
    $tempFile = [System.IO.Path]::GetTempFileName()

    try {
        $metadata = curl.exe -sS -o $tempFile -w "%{http_code} %{time_total}" $endpointUrl 2>$null
        $body = Get-Content -Raw -Path $tempFile -ErrorAction SilentlyContinue
        $body = $body -replace "`r?`n", ' '

        $line = "{0:D3} {1} {2}" -f $i, $metadata, $body
        Add-Content -Path $OutputFile -Value $line
        Write-Host $line
    }
    finally {
        Remove-Item -Force $tempFile -ErrorAction SilentlyContinue
    }
}

$lines = Get-Content $OutputFile
$total = $lines.Count
$success = ($lines | Where-Object { $_ -like '*"status":"SUCCESS"*' }).Count
$http200 = ($lines | Where-Object { $_ -match '^\d+\s+200\s+' }).Count
$httpErrors = $total - $http200

Write-Host "`nResultados guardados en: $OutputFile`n" -ForegroundColor Green
Write-Host "Respuestas SUCCESS: $success"
Write-Host "Respuestas HTTP 200: $http200"
Write-Host "Respuestas HTTP distintas de 200: $httpErrors"
Write-Host "Solicitudes totales: $total"

if ($total -gt 0) {
    $availability = [math]::Round($http200 / $total, 4)
    Write-Host "Disponibilidad observada: $availability" -ForegroundColor Yellow
}
