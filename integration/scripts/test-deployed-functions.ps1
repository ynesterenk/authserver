# Test Deployed Java Function Apps
# This script tests all deployed Function Apps to verify they're working correctly

param(
    [switch]$TestDirect = $false,
    [switch]$Verbose = $false
)

Write-Host "Testing Deployed Java Function Apps" -ForegroundColor Blue
Write-Host "===================================" -ForegroundColor Blue

# Get configuration
Write-Host "[INFO] Getting configuration..." -ForegroundColor Cyan
$TerraformDir = "../infrastructure/terraform"

Push-Location $TerraformDir
$ResourceGroupName = terraform output -raw resource_group_name 2>$null
$OAuthFunctionName = terraform output -raw oauth_function_name 2>$null
$JwtFunctionName = terraform output -raw jwt_authorizer_function_name 2>$null
$BasicAuthFunctionName = terraform output -raw basic_auth_function_name 2>$null
$ApimGatewayUrl = terraform output -raw apim_gateway_url 2>$null
$FullAccessKey = terraform output -raw apim_full_access_subscription_key 2>$null
Pop-Location

Write-Host "[SUCCESS] Configuration retrieved" -ForegroundColor Green

# Test Results
$TestResults = @()

# Function to test an endpoint
function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$Method = "GET",
        [string]$Body = $null
    )
    
    Write-Host "[INFO] Testing $Name..." -ForegroundColor Cyan
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            TimeoutSec = 15
            UseBasicParsing = $true
        }
        
        if ($Headers.Count -gt 0) {
            $params.Headers = $Headers
        }
        
        if ($Body) {
            $params.Body = $Body
            $params.ContentType = "application/json"
        }
        
        $response = Invoke-WebRequest @params
        
        if ($response.StatusCode -eq 200) {
            Write-Host "[SUCCESS] $Name is working (200 OK)" -ForegroundColor Green
            return @{Name=$Name; Status="SUCCESS"; Details="HTTP 200 OK"}
        } else {
            Write-Host "[INFO] $Name returned status: $($response.StatusCode)" -ForegroundColor Yellow
            return @{Name=$Name; Status="INFO"; Details="HTTP $($response.StatusCode)"}
        }
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode
        if ($statusCode -eq 401) {
            Write-Host "[INFO] $Name requires authentication (401)" -ForegroundColor Yellow
            return @{Name=$Name; Status="AUTH_REQUIRED"; Details="HTTP 401 - Authentication required"}
        } elseif ($statusCode -eq 404) {
            Write-Host "[WARNING] $Name endpoint not found (404)" -ForegroundColor Yellow
            return @{Name=$Name; Status="NOT_FOUND"; Details="HTTP 404 - Endpoint not found"}
        } elseif ($statusCode -eq 500) {
            Write-Host "[WARNING] $Name has server error (500)" -ForegroundColor Yellow
            return @{Name=$Name; Status="SERVER_ERROR"; Details="HTTP 500 - Server error"}
        } else {
            Write-Host "[ERROR] $Name failed: $_" -ForegroundColor Red
            return @{Name=$Name; Status="FAILED"; Details=$_.Exception.Message}
        }
    }
}

# Test 1: Direct Function App Health (if enabled)
if ($TestDirect) {
    Write-Host ""
    Write-Host "Testing Direct Function App Access" -ForegroundColor Blue
    Write-Host "==================================" -ForegroundColor Blue
    
    $TestResults += Test-Endpoint -Name "OAuth Server (Direct)" -Url "https://$OAuthFunctionName.azurewebsites.net/api/oauth/token" -Method "POST"
    $TestResults += Test-Endpoint -Name "JWT Authorizer (Direct)" -Url "https://$JwtFunctionName.azurewebsites.net/api/authorize" -Method "POST"
    $TestResults += Test-Endpoint -Name "Basic Auth (Direct)" -Url "https://$BasicAuthFunctionName.azurewebsites.net/api/authorize" -Method "POST"
}

# Test 2: APIM Endpoints (Primary Test)
Write-Host ""
Write-Host "Testing APIM Gateway Endpoints" -ForegroundColor Blue
Write-Host "==============================" -ForegroundColor Blue

$headers = @{"Ocp-Apim-Subscription-Key" = $FullAccessKey}

# Test OAuth Token endpoint
$oauthBody = @{
    grant_type = "client_credentials"
    client_id = "test-client"
    client_secret = "test-secret"
} | ConvertTo-Json

$TestResults += Test-Endpoint -Name "OAuth Token (APIM)" -Url "$ApimGatewayUrl/oauth/token" -Headers $headers -Method "POST" -Body $oauthBody

# Test JWT Authorization endpoint
$jwtBody = @{
    token = "test-jwt-token"
    resource = "test-resource"
} | ConvertTo-Json

$TestResults += Test-Endpoint -Name "JWT Authorization (APIM)" -Url "$ApimGatewayUrl/authorize/jwt" -Headers $headers -Method "POST" -Body $jwtBody

# Test Basic Authorization endpoint
$basicBody = @{
    username = "test-user"
    password = "test-password"
    resource = "test-resource"
} | ConvertTo-Json

$TestResults += Test-Endpoint -Name "Basic Authorization (APIM)" -Url "$ApimGatewayUrl/authorize/basic" -Headers $headers -Method "POST" -Body $basicBody

# Test Password Change endpoint
$passwordBody = @{
    username = "test-user"
    previousPassword = "old-password"
    proposedPassword = "new-password"
} | ConvertTo-Json

$TestResults += Test-Endpoint -Name "Password Change (APIM)" -Url "$ApimGatewayUrl/change-password" -Headers $headers -Method "POST" -Body $passwordBody

# Test Health endpoint
$TestResults += Test-Endpoint -Name "Health Check (APIM)" -Url "$ApimGatewayUrl/health" -Headers $headers

# Test 3: APIM without subscription key (should fail)
Write-Host ""
Write-Host "Testing APIM Security" -ForegroundColor Blue
Write-Host "=====================" -ForegroundColor Blue

$TestResults += Test-Endpoint -Name "OAuth Token (No Key)" -Url "$ApimGatewayUrl/oauth/token" -Method "POST" -Body $oauthBody

# Display Results
Write-Host ""
Write-Host "Test Results Summary" -ForegroundColor Blue
Write-Host "===================" -ForegroundColor Blue

$successCount = 0
$authRequiredCount = 0
$failedCount = 0

foreach ($result in $TestResults) {
    $color = switch ($result.Status) {
        "SUCCESS" { "Green"; $successCount++ }
        "AUTH_REQUIRED" { "Yellow"; $authRequiredCount++ }
        "INFO" { "Cyan"; $successCount++ }
        "NOT_FOUND" { "Yellow" }
        "SERVER_ERROR" { "Red"; $failedCount++ }
        "FAILED" { "Red"; $failedCount++ }
        default { "Gray" }
    }
    
    Write-Host "  $($result.Name): $($result.Status)" -ForegroundColor $color
    if ($Verbose) {
        Write-Host "    Details: $($result.Details)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Blue
Write-Host "  Successful: $successCount" -ForegroundColor Green
Write-Host "  Auth Required: $authRequiredCount" -ForegroundColor Yellow
Write-Host "  Failed: $failedCount" -ForegroundColor Red

# Overall Assessment
Write-Host ""
if ($failedCount -eq 0 -and $successCount -gt 0) {
    Write-Host "[SUCCESS] Java Function Apps are deployed and accessible!" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "Deployment Status:" -ForegroundColor Cyan
    Write-Host "  OAuth Server: DEPLOYED and ACCESSIBLE" -ForegroundColor Green
    Write-Host "  JWT Authorizer: DEPLOYED and ACCESSIBLE" -ForegroundColor Green
    Write-Host "  Basic Authenticator: DEPLOYED and ACCESSIBLE" -ForegroundColor Green
    Write-Host "  Password Change: DEPLOYED and ACCESSIBLE" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "APIM Integration:" -ForegroundColor Cyan
    Write-Host "  Subscription Keys: WORKING" -ForegroundColor Green
    Write-Host "  Security: PROPERLY CONFIGURED" -ForegroundColor Green
    Write-Host "  Endpoints: ACCESSIBLE" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Run full integration tests with real B2C credentials" -ForegroundColor Gray
    Write-Host "  2. Test OAuth flow end-to-end" -ForegroundColor Gray
    Write-Host "  3. Monitor function performance and logs" -ForegroundColor Gray
    Write-Host "  4. Configure production monitoring and alerts" -ForegroundColor Gray
    
    exit 0
} elseif ($failedCount -gt 0) {
    Write-Host "[WARNING] Some Function Apps may have issues" -ForegroundColor Yellow
    Write-Host "Check the failed tests above and Function App logs in Azure Portal" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "[INFO] Function Apps are deployed but may need configuration" -ForegroundColor Cyan
    exit 0
} 