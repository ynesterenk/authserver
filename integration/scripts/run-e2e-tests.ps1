# End-to-End Integration Tests for Deployed Azure Infrastructure (PowerShell)
# This script configures environment variables from Terraform outputs and runs comprehensive tests

param(
    [switch]$SkipEndpointTests = $false,
    [switch]$Verbose = $false
)

$ErrorActionPreference = "Stop"

Write-Host "🚀 Starting End-to-End Integration Tests for Azure Infrastructure" -ForegroundColor Blue
Write-Host "==================================================================" -ForegroundColor Blue

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

# Check if we're in the right directory
if (-not (Test-Path "pom.xml")) {
    Write-Error "Please run this script from the integration directory"
    exit 1
}

# Navigate to terraform directory to get outputs
$TerraformDir = "../infrastructure/terraform"
if (-not (Test-Path $TerraformDir)) {
    Write-Error "Terraform directory not found at $TerraformDir"
    exit 1
}

Write-Status "Extracting configuration from deployed infrastructure..."

# Get Terraform outputs
Push-Location $TerraformDir

try {
    # Extract key values from terraform output
    $ApimGatewayUrl = terraform output -raw apim_gateway_url 2>$null
    $B2cClientId = terraform output -raw b2c_client_id 2>$null
    $B2cTenantId = terraform output -raw b2c_tenant_id 2>$null
    $B2cJwksUrl = terraform output -raw b2c_jwks_url 2>$null
    $KeyVaultName = terraform output -raw key_vault_name 2>$null
    $ResourceGroupName = terraform output -raw resource_group_name 2>$null
    
    # Function URLs
    $OAuthFunctionUrl = terraform output -raw oauth_function_url 2>$null
    $JwtFunctionUrl = terraform output -raw jwt_authorizer_function_url 2>$null
    $BasicFunctionUrl = terraform output -raw basic_auth_function_url 2>$null
    $PasswordFunctionUrl = terraform output -raw password_change_function_url 2>$null
}
catch {
    Write-Error "Failed to extract Terraform outputs: $_"
    exit 1
}
finally {
    Pop-Location
}

# Validate required outputs
if ([string]::IsNullOrEmpty($ApimGatewayUrl) -or [string]::IsNullOrEmpty($B2cClientId)) {
    Write-Error "Failed to extract required configuration from Terraform outputs"
    Write-Error "Please ensure Terraform has been applied successfully"
    exit 1
}

Write-Success "Configuration extracted successfully"

# Set environment variables
$env:AZURE_FUNCTION_BASE_URL = $ApimGatewayUrl
$env:TEST_CLIENT_ID = $B2cClientId
$env:AZURE_B2C_TENANT_ID = $B2cTenantId
$env:AZURE_B2C_JWKS_URL = $B2cJwksUrl

# Get B2C client secret from Azure Key Vault
Write-Status "Retrieving B2C client secret from Key Vault..."
if (Get-Command az -ErrorAction SilentlyContinue) {
    try {
        $B2cClientSecret = az keyvault secret show --vault-name $KeyVaultName --name "b2c-client-secret" --query "value" -o tsv 2>$null
        if (-not [string]::IsNullOrEmpty($B2cClientSecret)) {
            $env:B2C_CLIENT_SECRET = $B2cClientSecret
            $env:TEST_CLIENT_SECRET = $B2cClientSecret
            Write-Success "B2C client secret retrieved from Key Vault"
        }
        else {
            Write-Warning "Could not retrieve B2C client secret from Key Vault"
            Write-Warning "Tests requiring authentication may fail"
        }
    }
    catch {
        Write-Warning "Error retrieving B2C client secret: $_"
    }
}
else {
    Write-Warning "Azure CLI not found. Cannot retrieve secrets from Key Vault"
    Write-Warning "Please install Azure CLI or set B2C_CLIENT_SECRET manually"
}

# Get Application Insights connection string
Write-Status "Retrieving Application Insights connection string..."
if (Get-Command az -ErrorAction SilentlyContinue) {
    try {
        $AppInsightsConnection = az monitor app-insights component show --app authserver-dev-ai --resource-group $ResourceGroupName --query "connectionString" -o tsv 2>$null
        if (-not [string]::IsNullOrEmpty($AppInsightsConnection)) {
            $env:APPLICATIONINSIGHTS_CONNECTION_STRING = $AppInsightsConnection
            Write-Success "Application Insights connection string retrieved"
        }
        else {
            Write-Warning "Could not retrieve Application Insights connection string"
        }
    }
    catch {
        Write-Warning "Error retrieving Application Insights connection: $_"
    }
}

# Display configuration
Write-Status "Test Configuration:"
Write-Host "  APIM Gateway URL: $ApimGatewayUrl"
Write-Host "  B2C Client ID: $B2cClientId"
Write-Host "  B2C Tenant ID: $B2cTenantId"
Write-Host "  Resource Group: $ResourceGroupName"
Write-Host "  Key Vault: $KeyVaultName"

# Test endpoints availability
if (-not $SkipEndpointTests) {
    Write-Status "Testing endpoint availability..."
    
    function Test-Endpoint {
        param($Url, $Name)
        
        try {
            $response = Invoke-WebRequest -Uri $Url -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
            Write-Success "$Name endpoint is accessible"
            return $true
        }
        catch {
            Write-Warning "$Name endpoint is not accessible: $Url"
            return $false
        }
    }
    
    # Test health endpoint first
    $HealthUrl = "$ApimGatewayUrl/health"
    if (Test-Endpoint $HealthUrl "Health Check") {
        Write-Success "Infrastructure appears to be healthy"
    }
    else {
        Write-Warning "Health check failed, but continuing with tests..."
    }
    
    # Test B2C JWKS endpoint
    if (Test-Endpoint $B2cJwksUrl "B2C JWKS") {
        Write-Success "B2C configuration is accessible"
    }
}

# Run the integration tests
Write-Status "Starting Maven integration tests..."
Write-Host "=================================================================="

# Set Maven options for better output
$env:MAVEN_OPTS = "-Xmx1024m"

# Build Maven command
$MavenArgs = @(
    "clean", "test",
    "-Dtest.environment=azure-dev",
    "-Dazure.function.base.url=$ApimGatewayUrl",
    "-Dtest.client.id=$B2cClientId",
    "-Dazure.b2c.jwks.url=$B2cJwksUrl",
    "-Dmaven.test.failure.ignore=false",
    "-Dsurefire.printSummary=true",
    "-Dsurefire.reportFormat=plain"
)

if ($Verbose) {
    $MavenArgs += "-X"
}

# Run Maven tests
try {
    & mvn $MavenArgs
    $TestExitCode = $LASTEXITCODE
}
catch {
    Write-Error "Failed to run Maven tests: $_"
    $TestExitCode = 1
}

Write-Host "=================================================================="

if ($TestExitCode -eq 0) {
    Write-Success "🎉 All integration tests passed!"
    Write-Success "Your Azure infrastructure is working correctly"
    
    Write-Host ""
    Write-Status "Available endpoints for testing:"
    Write-Host "  OAuth Token: $ApimGatewayUrl/oauth/token"
    Write-Host "  JWT Authorization: $ApimGatewayUrl/authorize/jwt"
    Write-Host "  Basic Authorization: $ApimGatewayUrl/authorize/basic"
    Write-Host "  Password Change: $ApimGatewayUrl/change-password"
    Write-Host "  Health Check: $ApimGatewayUrl/health"
    
    Write-Host ""
    Write-Status "Next steps:"
    Write-Host "  1. Deploy your Java application code to the Function Apps"
    Write-Host "  2. Run performance tests under load"
    Write-Host "  3. Configure production monitoring alerts"
    Write-Host "  4. Update client applications to use new endpoints"
}
else {
    Write-Error "❌ Some integration tests failed"
    Write-Error "Please check the test output above for details"
    
    Write-Host ""
    Write-Status "Common troubleshooting steps:"
    Write-Host "  1. Verify all Azure resources are deployed correctly"
    Write-Host "  2. Check Function App logs in Azure Portal"
    Write-Host "  3. Validate B2C configuration and test credentials"
    Write-Host "  4. Ensure APIM policies are configured correctly"
}

exit $TestExitCode 