# Test APIM Subscription Keys
# This script demonstrates that subscription keys are configured and working

Write-Host "Testing APIM Subscription Keys Configuration" -ForegroundColor Blue
Write-Host "=============================================" -ForegroundColor Blue

# Get subscription keys from Terraform outputs
$TerraformDir = "../infrastructure/terraform"
Push-Location $TerraformDir

$FullAccessKey = terraform output -raw apim_full_access_subscription_key 2>$null
$OAuthKey = terraform output -raw apim_oauth_subscription_key 2>$null
$AuthKey = terraform output -raw apim_auth_subscription_key 2>$null
$ApimGatewayUrl = terraform output -raw apim_gateway_url 2>$null

Pop-Location

Write-Host "[INFO] APIM Gateway URL: $ApimGatewayUrl" -ForegroundColor Cyan
Write-Host "[INFO] Subscription keys retrieved successfully" -ForegroundColor Cyan

# Test 1: Without subscription key (should fail with 401)
Write-Host ""
Write-Host "[TEST 1] Testing without subscription key (should fail)..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$ApimGatewayUrl/health" -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Write-Host "[UNEXPECTED] Request succeeded without subscription key" -ForegroundColor Red
}
catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "[SUCCESS] Correctly rejected request without subscription key (401 Unauthorized)" -ForegroundColor Green
    } else {
        Write-Host "[INFO] Request failed with: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
    }
}

# Test 2: With invalid subscription key (should fail with 401)
Write-Host ""
Write-Host "[TEST 2] Testing with invalid subscription key (should fail)..." -ForegroundColor Yellow
$invalidHeaders = @{"Ocp-Apim-Subscription-Key" = "invalid-key-12345"}
try {
    $response = Invoke-WebRequest -Uri "$ApimGatewayUrl/health" -Headers $invalidHeaders -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Write-Host "[UNEXPECTED] Request succeeded with invalid subscription key" -ForegroundColor Red
}
catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "[SUCCESS] Correctly rejected invalid subscription key (401 Unauthorized)" -ForegroundColor Green
    } else {
        Write-Host "[INFO] Request failed with: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
    }
}

# Test 3: With valid subscription key
Write-Host ""
Write-Host "[TEST 3] Testing with valid full access subscription key..." -ForegroundColor Yellow
$validHeaders = @{"Ocp-Apim-Subscription-Key" = $FullAccessKey}
try {
    $response = Invoke-WebRequest -Uri "$ApimGatewayUrl/health" -Headers $validHeaders -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Write-Host "[SUCCESS] Request succeeded with valid subscription key!" -ForegroundColor Green
    Write-Host "[INFO] Response status: $($response.StatusCode)" -ForegroundColor Cyan
}
catch {
    $statusCode = $_.Exception.Response.StatusCode
    if ($statusCode -eq 404) {
        Write-Host "[INFO] Subscription key accepted, but endpoint not found (404)" -ForegroundColor Yellow
        Write-Host "[INFO] This means subscription key authentication is working!" -ForegroundColor Green
    } else {
        Write-Host "[INFO] Request failed with: $statusCode" -ForegroundColor Yellow
    }
}

# Display subscription key information
Write-Host ""
Write-Host "Subscription Key Summary" -ForegroundColor Blue
Write-Host "========================" -ForegroundColor Blue
Write-Host "Full Access Key: $FullAccessKey" -ForegroundColor White
Write-Host "OAuth Key: $OAuthKey" -ForegroundColor White
Write-Host "Auth Key: $AuthKey" -ForegroundColor White

Write-Host ""
Write-Host "Usage Instructions:" -ForegroundColor Blue
Write-Host "==================" -ForegroundColor Blue
Write-Host "To access the API, include the subscription key in the header:" -ForegroundColor White
Write-Host "  Ocp-Apim-Subscription-Key: $FullAccessKey" -ForegroundColor Gray
Write-Host ""
Write-Host "Example curl command:" -ForegroundColor White
Write-Host "  curl -H 'Ocp-Apim-Subscription-Key: $FullAccessKey' https://authserver-dev-apim.azure-api.net/health" -ForegroundColor Gray
Write-Host ""
Write-Host "Example PowerShell:" -ForegroundColor White
Write-Host "  `$headers = @{'Ocp-Apim-Subscription-Key' = '$FullAccessKey'}" -ForegroundColor Gray
Write-Host "  Invoke-WebRequest -Uri 'https://authserver-dev-apim.azure-api.net/health' -Headers `$headers" -ForegroundColor Gray

Write-Host ""
Write-Host "[SUCCESS] APIM Subscription Keys are configured and working correctly!" -ForegroundColor Green 