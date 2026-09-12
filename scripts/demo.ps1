param(
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateSet('SUCCESS', 'DECLINE', 'CANCEL')]
    [string]$PaymentMode = 'SUCCESS'
)
$ErrorActionPreference = 'Stop'
if (-not $env:ADMIN_EMAIL -or -not $env:ADMIN_PASSWORD) {
    throw 'Set ADMIN_EMAIL and ADMIN_PASSWORD to the credentials used to start the stack.'
}
function Invoke-Commerce($Method, $Path, $Body, $Token, $Key) {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($Key) { $headers['Idempotency-Key'] = $Key }
    $arguments = @{ Method = $Method; Uri = "$($BaseUrl.TrimEnd('/'))$Path"; Headers = $headers; TimeoutSec = 15 }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    Invoke-RestMethod @arguments
}
$admin = Invoke-Commerce POST '/api/auth/login' @{ email = $env:ADMIN_EMAIL; password = $env:ADMIN_PASSWORD }
$credentials = @{ email = "demo-$([guid]::NewGuid())@example.com"; password = "Demo-$([guid]::NewGuid())!" }
$null = Invoke-Commerce POST '/api/auth/register' $credentials
$customer = Invoke-Commerce POST '/api/auth/login' $credentials
$product = Invoke-Commerce POST '/api/products' @{
    sku = "DEMO-$([guid]::NewGuid())"; name = 'Mechanical Keyboard'; description = 'USB-C demo keyboard'
    category = 'electronics'; price = 79.99
} $admin.accessToken
$null = Invoke-Commerce POST "/api/inventory/$($product.id)/restock" @{ quantity = 3 } $admin.accessToken ([guid]::NewGuid().ToString())
$order = Invoke-Commerce POST '/api/orders' @{
    items = @(@{ productId = $product.id; quantity = 1 }); paymentMode = $PaymentMode
} $customer.accessToken ([guid]::NewGuid().ToString())
$deadline = (Get-Date).AddSeconds(60)
while ($order.state -notin @('CONFIRMED', 'CANCELLED')) {
    if ((Get-Date) -gt $deadline) { throw "Order $($order.id) did not finish within 60 seconds." }
    Start-Sleep -Seconds 1
    $order = Invoke-Commerce GET "/api/orders/$($order.id)" $null $customer.accessToken
}
$expected = if ($PaymentMode -eq 'SUCCESS') { 'CONFIRMED' } else { 'CANCELLED' }
if ($order.state -ne $expected) { throw "Expected $expected, received $($order.state)." }
$order | Format-List
Invoke-Commerce GET "/api/inventory/$($product.id)" $null $admin.accessToken | Format-List
