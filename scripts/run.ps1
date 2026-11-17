param(
    [Parameter(Position=0)]
    [ValidateSet('up', 'dev', 'down', 'build', 'demo', 'health', 'logs', 'clean')]
    [string]$Action = 'dev'
)

switch ($Action) {
    'dev' {
        Write-Host "Starting lightweight stack (1 PostgreSQL instance for all services)..." -ForegroundColor Cyan
        docker compose -f docker-compose.dev.yml up -d --build --wait --wait-timeout 300
        .\scripts\health-check.ps1
    }
    'up' {
        Write-Host "Starting full production stack (18 containers)..." -ForegroundColor Cyan
        docker compose up -d --build --wait --wait-timeout 300
        .\scripts\health-check.ps1
    }
    'down' {
        Write-Host "Stopping all containers..." -ForegroundColor Yellow
        docker compose down
        docker compose -f docker-compose.dev.yml down
    }
    'clean' {
        Write-Host "Stopping containers and deleting volumes..." -ForegroundColor Red
        docker compose down -v
        docker compose -f docker-compose.dev.yml down -v
    }
    'build' {
        Write-Host "Building with Maven..." -ForegroundColor Cyan
        mvn clean package -DskipTests
    }
    'demo' {
        .\scripts\demo.ps1
    }
    'health' {
        .\scripts\health-check.ps1
    }
    'logs' {
        docker compose logs -f gateway order-service inventory-service payment-service
    }
}
