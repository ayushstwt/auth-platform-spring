# Load Test Script: Auth & OTP Endpoints Under Concurrency
param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TotalRequests = 50,
    [int]$Concurrency = 5
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running Phase 7 Load Test against $BaseUrl" -ForegroundColor Cyan
Write-Host " Requests: $TotalRequests, Concurrency: $Concurrency" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$Endpoints = @(
    @{ Url = "$BaseUrl/api/v1/health"; Method = "GET"; Name = "Health Probe" },
    @{ Url = "$BaseUrl/.well-known/jwks.json"; Method = "GET"; Name = "Public JWKS" },
    @{ Url = "$BaseUrl/api/v1/auth/login"; Method = "POST"; Name = "Login (Rate Limited)"; Body = '{"identifier":"test@example.com","password":"DummyPassword123!"}' }
)

foreach ($endpoint in $Endpoints) {
    Write-Host "`nTesting $($endpoint.Name) -> $($endpoint.Url)" -ForegroundColor Yellow
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $successCount = 0
    $rateLimitedCount = 0
    $failureCount = 0

    1..$TotalRequests | ForEach-Object -Parallel {
        $ep = $using:endpoint
        try {
            $headers = @{ "Content-Type" = "application/json" }
            if ($ep.Method -eq "GET") {
                $response = Invoke-WebRequest -Uri $ep.Url -Method Get -Headers $headers -TimeoutSec 5 -ErrorAction Stop
                if ($response.StatusCode -eq 200) { $global:successCount++ }
            } else {
                $response = Invoke-WebRequest -Uri $ep.Url -Method Post -Body $ep.Body -Headers $headers -TimeoutSec 5 -ErrorAction Stop
                if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { $global:successCount++ }
            }
        } catch {
            $status = $_.Exception.Response.StatusCode.value__
            if ($status -eq 429) {
                $global:rateLimitedCount++
            } elseif ($status -eq 401 -or $status -eq 400) {
                # Expected credential failure is still handled properly by API without 500 error
                $global:successCount++
            } else {
                $global:failureCount++
            }
        }
    } -ThrottleLimit $Concurrency

    $stopwatch.Stop()
    Write-Host "Completed in $($stopwatch.ElapsedMilliseconds) ms" -ForegroundColor Green
    Write-Host "  Success/Handled: $successCount" -ForegroundColor Green
    Write-Host "  Rate-Limited (429): $rateLimitedCount" -ForegroundColor Magenta
    Write-Host "  Errors/5xx: $failureCount" -ForegroundColor Red
}

Write-Host "`nLoad test completed." -ForegroundColor Cyan
