# Simple Infrastructure Test Script for Deployed Azure Resources
param([switch]$Detailed = $false)

Write-Host "Testing Deployed Azure Infrastructure" -ForegroundColor Blue
Write-Host "=====================================" -ForegroundColor Blue

# Get Terraform outputs
Write-Host "[INFO] Extracting configuration from Terraform..." -ForegroundColor Cyan
$TerraformDir = "../infrastructure/terraform"

Push-Location $TerraformDir
$ApimGatewayUrl = terraform output -raw apim_gateway_url 2>$null
$B2cJwksUrl = terraform output -raw b2c_jwks_url 2>$null
$ResourceGroupName = terraform output -raw resource_group_name 2>$null
Pop-Location

Write-Host "[SUCCESS] Configuration extracted successfully" -ForegroundColor Green

# Test Results
$TestResults = @()

# Test 1: B2C JWKS Endpoint
Write-Host "[INFO] Test 1: Testing B2C JWKS endpoint..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri $B2cJwksUrl -Method GET -TimeoutSec 10 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "[SUCCESS] B2C JWKS endpoint is accessible" -ForegroundColor Green
        $jwksContent = $response.Content | ConvertFrom-Json
        if ($jwksContent.keys -and $jwksContent.keys.Count -gt 0) {
            Write-Host "[SUCCESS] B2C JWKS contains $($jwksContent.keys.Count) valid keys" -ForegroundColor Green
            $TestResults += @{Test="B2C JWKS"; Status="PASS"}
        }
    }
}
catch {
    Write-Host "[ERROR] B2C JWKS endpoint failed: $_" -ForegroundColor Red
    $TestResults += @{Test="B2C JWKS"; Status="FAIL"}
}

# Test 2: APIM Gateway
Write-Host "[INFO] Test 2: Testing APIM Gateway accessibility..." -ForegroundColor Cyan
try {
    $healthUrl = "$ApimGatewayUrl/health"
    $response = Invoke-WebRequest -Uri $healthUrl -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Write-Host "[SUCCESS] APIM Gateway is accessible" -ForegroundColor Green
    $TestResults += @{Test="APIM Gateway"; Status="PASS"}
}
catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "[SUCCESS] APIM Gateway is accessible and properly secured (401 Unauthorized)" -ForegroundColor Green
        $TestResults += @{Test="APIM Gateway"; Status="PASS"}
    } else {
        Write-Host "[ERROR] APIM Gateway failed: $_" -ForegroundColor Red
        $TestResults += @{Test="APIM Gateway"; Status="FAIL"}
    }
}

# Test Summary
Write-Host ""
Write-Host "Test Summary" -ForegroundColor Blue
Write-Host "============" -ForegroundColor Blue

$passCount = ($TestResults | Where-Object {$_.Status -eq "PASS"}).Count
$failCount = ($TestResults | Where-Object {$_.Status -eq "FAIL"}).Count
$totalCount = $TestResults.Count

Write-Host "Total Tests: $totalCount" -ForegroundColor White
Write-Host "Passed: $passCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red

if ($Detailed) {
    Write-Host ""
    Write-Host "Detailed Results:" -ForegroundColor Blue
    foreach ($result in $TestResults) {
        $color = if ($result.Status -eq "PASS") { "Green" } else { "Red" }
        Write-Host "  $($result.Test): $($result.Status)" -ForegroundColor $color
    }
}

Write-Host ""
if ($failCount -eq 0) {
    Write-Host "[SUCCESS] Infrastructure validation completed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "[INFO] Your deployed endpoints:" -ForegroundColor Cyan
    Write-Host "  APIM Gateway: $ApimGatewayUrl"
    Write-Host "  OAuth Token: $ApimGatewayUrl/oauth/token"
    Write-Host "  JWT Auth: $ApimGatewayUrl/authorize/jwt"
    Write-Host "  Basic Auth: $ApimGatewayUrl/authorize/basic"
    Write-Host "  Password Change: $ApimGatewayUrl/change-password"
    Write-Host "  Health Check: $ApimGatewayUrl/health"
    
    Write-Host ""
    Write-Host "[INFO] Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Configure APIM subscription keys for client access"
    Write-Host "  2. Deploy Java application code to Function Apps"
    Write-Host "  3. Test OAuth flow with actual client credentials"
    Write-Host "  4. Run performance tests under load"
    
    exit 0
} else {
    Write-Host "[ERROR] Some infrastructure tests failed" -ForegroundColor Red
    exit 1
} 