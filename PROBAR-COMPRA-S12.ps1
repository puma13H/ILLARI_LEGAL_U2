# Prueba S08/S09/S11: dos productos en un carrito, orden y pago simulado con Kafka.
# No imprime ni guarda en archivo el token JWT.
$ErrorActionPreference = 'Stop'
$gateway = 'http://localhost:18080'
$tokenSec = Read-Host -AsSecureString 'Pega el access token temporal de Keycloak (NO enviarlo al chat)'
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tokenSec)
try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
if ([string]::IsNullOrWhiteSpace($token)) { throw 'Token vacio.' }
$h = @{ Authorization = "Bearer $token" }
[array]$servicios = Invoke-RestMethod "$gateway/api/v1/servicios"
if ($servicios.Count -lt 2) { throw 'Se necesitan al menos dos servicios en el catalogo.' }
$items = @(
 @{ idServicio = [long]$servicios[0].id; cantidad = 2 },
 @{ idServicio = [long]$servicios[1].id; cantidad = 1 }
)
Write-Host "Carrito: $($servicios[0].nombre) x2 + $($servicios[1].nombre) x1" -ForegroundColor Cyan
function NuevaCompra([string]$metodo,[string]$esperado) {
  $body = @{ metodoPago=$metodo; detalles=$items } | ConvertTo-Json -Depth 8
  $compraHeaders = @{ Authorization = $h.Authorization; 'Idempotency-Key' = [guid]::NewGuid().ToString() }
  $orden = Invoke-RestMethod "$gateway/api/v1/ordenes" -Method POST -Headers $compraHeaders -ContentType 'application/json' -Body $body
  Write-Host "Orden $($orden.id) creada con estado inicial $($orden.estado) y monto $($orden.total)"
  $estado = $orden.estado
  for ($i=0; $i -lt 40 -and $estado -eq 'PENDIENTE_PAGO'; $i++) {
    Start-Sleep -Seconds 2
    $current = Invoke-RestMethod "$gateway/api/v1/ordenes/$($orden.id)" -Headers $h
    $estado = $current.estado
  }
  if ($estado -ne $esperado) { throw "Orden $($orden.id): se esperaba $esperado, pero sigue en $estado" }
  [array]$pagos = Invoke-RestMethod "$gateway/api/v1/pagos/orden/$($orden.id)" -Headers $h
  if ($pagos.Count -ne 1) { throw "Orden $($orden.id): esperabamos un pago, hay $($pagos.Count)" }
  Write-Host "OK orden $($orden.id): $estado / pago $($pagos[0].estado)" -ForegroundColor Green
  return $orden
}
try {
  $aprobada = NuevaCompra 'YAPE' 'PAGADA'
  $rechazada = NuevaCompra 'TARJETA_RECHAZADA' 'CANCELADA'
  Write-Host "S12: compra multi-producto, Kafka y SAGA de pago aprobado/rechazado comprobadas para las ordenes $($aprobada.id), $($rechazada.id)." -ForegroundColor Green
} finally { $token = $null; $h.Clear() }
