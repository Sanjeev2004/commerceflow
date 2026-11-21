#!/usr/bin/env bash
set -e

GATEWAY_URL="${1:-http://localhost:8080}"

echo "=== CommerceFlow Distributed Microservices Health Check ==="

check_service() {
    local name="$1"
    local url="$2"
    if curl -s -f -m 3 "$url" > /dev/null 2>&1; then
        printf " \033[32m[OK]\033[0m   %-24s -> UP (%s)\n" "$name" "$url"
    else
        printf " \033[31m[DOWN]\033[0m %-24s -> Unreachable (%s)\n" "$name" "$url"
    fi
}

check_service "API Gateway"          "${MANAGEMENT_GATEWAY_URL:-http://localhost:9080}/actuator/health"
check_service "Auth Service"         "http://localhost:9081/actuator/health"
check_service "Product Service"      "http://localhost:9082/actuator/health"
check_service "Inventory Service"    "http://localhost:9083/actuator/health"
check_service "Order Service"        "http://localhost:9084/actuator/health"
check_service "Payment Service"      "http://localhost:9085/actuator/health"
check_service "Search Service"       "http://localhost:9086/actuator/health"
check_service "Notification Service" "http://localhost:9087/actuator/health"
check_service "Elasticsearch"        "http://localhost:9200/_cluster/health"
check_service "Prometheus"           "http://localhost:9091/-/healthy"

echo ""
echo "Web Storefront: $GATEWAY_URL/index.html"
echo "Grafana:        http://localhost:3000"
