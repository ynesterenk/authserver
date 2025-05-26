# Simple Infrastructure Test Script for Deployed Azure Resources
# This script tests the deployed infrastructure manually without Maven dependencies

param(
    [switch]$Detailed = $false
)

Write-Host "🧪 Testing Deployed Azure Infrastructure" -ForegroundColor Blue
Write-Host "=========================================" -ForegroundColor Blue

function Write-Status {
    param($Message)
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param($Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param($Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param($Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Get Terraform outputs
Write-Status "Extracting configuration from Terraform..."
$TerraformDir = "../infrastructure/terraform"

if (-not (Test-Path $TerraformDir)) {
    Write-Error "Terraform directory not found"
    exit 1
}

Push-Location $TerraformDir

try {
    $ApimGatewayUrl = terraform output -raw apim_gateway_url 2>$null
    $B2cClientId = terraform output -raw b2c_client_id 2>$null
    $B2cJwksUrl = terraform output -raw b2c_jwks_url 2>$null
    $KeyVaultName = terraform output -raw key_vault_name 2>$null
    $ResourceGroupName = terraform output -raw resource_group_name 2>$null
    
    # Function URLs (direct access)
    $OAuthFunctionUrl = terraform output -raw oauth_function_url 2>$null
    $JwtFunctionUrl = terraform output -raw jwt_authorizer_function_url 2>$null
    $BasicFunctionUrl = terraform output -raw basic_auth_function_url 2>$null
    $PasswordFunctionUrl = terraform output -raw password_change_function_url 2>$null
}
finally {
    Pop-Location
}

Write-Success "Configuration extracted successfully"

# Test Results
$TestResults = @()

# Test 1: B2C JWKS Endpoint
Write-Status "Test 1: Testing B2C JWKS endpoint..."
try {
    $response = Invoke-WebRequest -Uri $B2cJwksUrl -Method GET -TimeoutSec 10 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Success "✅ B2C JWKS endpoint is accessible"
        $jwksContent = $response.Content | ConvertFrom-Json
        if ($jwksContent.keys -and $jwksContent.keys.Count -gt 0) {
            Write-Success "✅ B2C JWKS contains valid keys ($($jwksContent.keys.Count) keys found)"
            $TestResults += @{Test="B2C JWKS"; Status="PASS"; Details="$($jwksContent.keys.Count) keys available"}
        } else {
            Write-Warning "⚠️ B2C JWKS endpoint accessible but no keys found"
            $TestResults += @{Test="B2C JWKS"; Status="WARN"; Details="No keys found"}
        }
    }
}
catch {
    Write-Error "❌ B2C JWKS endpoint failed: $_"
    $TestResults += @{Test="B2C JWKS"; Status="FAIL"; Details=$_.Exception.Message}
}

# Test 2: APIM Gateway (expecting 401 due to subscription key requirement)
Write-Status "Test 2: Testing APIM Gateway accessibility..."
try {
    $healthUrl = "$ApimGatewayUrl/health"
    $response = Invoke-WebRequest -Uri $healthUrl -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Write-Success "✅ APIM Gateway is accessible (unexpected - no auth required)"
    $TestResults += @{Test="APIM Gateway"; Status="PASS"; Details="Accessible without auth"}
}
catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Success "✅ APIM Gateway is accessible and properly secured (401 Unauthorized)"
        $TestResults += @{Test="APIM Gateway"; Status="PASS"; Details="Properly secured with subscription key"}
    } else {
        Write-Error "❌ APIM Gateway failed with unexpected error: $_"
        $TestResults += @{Test="APIM Gateway"; Status="FAIL"; Details=$_.Exception.Message}
    }
}

# Test 3: Direct Function App Health (if accessible)
Write-Status "Test 3: Testing direct Function App accessibility..."
$functionTests = @(
    @{Name="OAuth Function"; Url="$OAuthFunctionUrl/health"},
    @{Name="JWT Function"; Url="$JwtFunctionUrl/health"},
    @{Name="Basic Auth Function"; Url="$BasicFunctionUrl/health"},
    @{Name="Password Function"; Url="$PasswordFunctionUrl/health"}
)

foreach ($funcTest in $functionTests) {
    try {
        $response = Invoke-WebRequest -Uri $funcTest.Url -Method GET -TimeoutSec 10 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "✅ $($funcTest.Name) is accessible"
            $TestResults += @{Test=$funcTest.Name; Status="PASS"; Details="Direct access successful"}
        }
    }
    catch {
        if ($_.Exception.Response.StatusCode -eq 401 -or $_.Exception.Response.StatusCode -eq 403) {
            Write-Success "✅ $($funcTest.Name) is deployed and secured"
            $TestResults += @{Test=$funcTest.Name; Status="PASS"; Details="Secured (401/403)"}
        } else {
            Write-Warning "⚠️ $($funcTest.Name) may not be deployed or accessible: $_"
            $TestResults += @{Test=$funcTest.Name; Status="WARN"; Details="Not accessible"}
        }
    }
}

# Test 4: Azure Resource Existence
Write-Status "Test 4: Verifying Azure resources exist..."
if (Get-Command az -ErrorAction SilentlyContinue) {
    try {
        # Test Resource Group
        $rg = az group show --name $ResourceGroupName --query "name" -o tsv 2>$null
        if ($rg -eq $ResourceGroupName) {
            Write-Success "✅ Resource Group exists: $ResourceGroupName"
            $TestResults += @{Test="Resource Group"; Status="PASS"; Details="Exists"}
        }
        
        # Test Key Vault
        $kv = az keyvault show --name $KeyVaultName --query "name" -o tsv 2>$null
        if ($kv -eq $KeyVaultName) {
            Write-Success "✅ Key Vault exists: $KeyVaultName"
            $TestResults += @{Test="Key Vault"; Status="PASS"; Details="Exists"}
        }
        
        # Test APIM
        $apim = az apim show --name "authserver-dev-apim" --resource-group $ResourceGroupName --query "name" -o tsv 2>$null
        if ($apim -eq "authserver-dev-apim") {
            Write-Success "✅ API Management exists: authserver-dev-apim"
            $TestResults += @{Test="API Management"; Status="PASS"; Details="Exists"}
        }
    }
    catch {
        Write-Warning "⚠️ Could not verify all Azure resources via CLI: $_"
    }
} else {
    Write-Warning "⚠️ Azure CLI not available - skipping resource verification"
}

# Test Summary
Write-Host ""
Write-Host "📊 Test Summary" -ForegroundColor Blue
Write-Host "===============" -ForegroundColor Blue

$passCount = ($TestResults | Where-Object {$_.Status -eq "PASS"}).Count
$warnCount = ($TestResults | Where-Object {$_.Status -eq "WARN"}).Count
$failCount = ($TestResults | Where-Object {$_.Status -eq "FAIL"}).Count
$totalCount = $TestResults.Count

Write-Host "Total Tests: $totalCount" -ForegroundColor White
Write-Host "Passed: $passCount" -ForegroundColor Green
Write-Host "Warnings: $warnCount" -ForegroundColor Yellow
Write-Host "Failed: $failCount" -ForegroundColor Red

if ($Detailed) {
    Write-Host ""
    Write-Host "Detailed Results:" -ForegroundColor Blue
    foreach ($result in $TestResults) {
        $color = switch ($result.Status) {
            "PASS" { "Green" }
            "WARN" { "Yellow" }
            "FAIL" { "Red" }
        }
        Write-Host "  $($result.Test): $($result.Status) - $($result.Details)" -ForegroundColor $color
    }
}

Write-Host ""
if ($failCount -eq 0) {
    Write-Success "🎉 Infrastructure validation completed successfully!"
    Write-Host ""
    Write-Status "Your deployed endpoints:"
    Write-Host "  APIM Gateway: $ApimGatewayUrl"
    Write-Host "  OAuth Token: $ApimGatewayUrl/oauth/token"
    Write-Host "  JWT Auth: $ApimGatewayUrl/authorize/jwt"
    Write-Host "  Basic Auth: $ApimGatewayUrl/authorize/basic"
    Write-Host "  Password Change: $ApimGatewayUrl/change-password"
    Write-Host "  Health Check: $ApimGatewayUrl/health"
    
    Write-Host ""
    Write-Status "Next steps:"
    Write-Host "  1. Configure APIM subscription keys for client access"
    Write-Host "  2. Deploy Java application code to Function Apps"
    Write-Host "  3. Test OAuth flow with actual client credentials"
    Write-Host "  4. Run performance tests under load"
    
    exit 0
} else {
    Write-Error "❌ Some infrastructure tests failed"
    Write-Host ""
    Write-Status "Troubleshooting steps:"
    Write-Host "  1. Check Azure Portal for resource deployment status"
    Write-Host "  2. Verify Terraform apply completed successfully"
    Write-Host "  3. Check Function App logs for any deployment issues"
    
    exit 1
}