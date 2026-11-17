param(
    [string]$GatewayUrl = 'http://localhost:8080'
)

$services = @(
    @{ Name = "API Gateway";            Url = "$GatewayUrl/actuator/health" },
    @{ Name = "Auth Service";           Url = "http://localhost:9081/actuator/health" },
    @{ Name = "Product Service";        Url = "http://localhost:9082/actuator/health" },
    @{ Name = "Inventory Service";      Url = "http://localhost:9083/actuator/health" },
    @{ Name = "Order Service";          Url = "http://localhost:9084/actuator/health" },
    @{ Name = "Payment Service";        Url = "http://localhost:9085/actuator/health" },
    @{ Name = "Search Service";         Url = "http://localhost:9086/actuator/health" },
    @{ Name = "Notification Service";   Url = "http://localhost:9087/actuator/health" },
    @{ Name = "Elasticsearch";          Url = "http://localhost:9200/_cluster/health" },
    @{ Name = "Prometheus";             Url = "http://localhost:9091/-/healthy" }
)

Write-Host "=== CommerceFlow Distributed Microservices Health Check ===" -ForegroundColor Cyan

foreach ($svc in $services) {
    try {
        $res = Invoke-RestMethod -Uri $svc.Url -TimeoutSec 3 -ErrorAction Stop
        $status = if ($res.status) { $res.status } else { "UP" }
        Write-Host " [OK]   $($svc.Name.PadRight(24)) -> $status ($($svc.Url))" -ForegroundColor Green
    } catch {
        Write-Host " [DOWN] $($svc.Name.PadRight(24)) -> Unreachable ($($svc.Url))" -ForegroundColor Red
    }
}

Write-Host "`nWeb Storefront: $GatewayUrl/index.html" -ForegroundColor Yellow
Write-Host "Grafana:        http://localhost:3000 (admin / password in .env)" -ForegroundColor Yellow
