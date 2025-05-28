# Deploy Java Functions to Azure Function Apps - Proper Method
# This script uses the Azure Functions Maven plugin to properly build and deploy Java Functions

param(
    [switch]$SkipBuild = $false,
    [switch]$Verbose = $false
)

Write-Host "Deploying Java Functions to Azure Function Apps (Proper Method)" -ForegroundColor Blue
Write-Host "================================================================" -ForegroundColor Blue

# Get configuration from Terraform
Write-Host "[INFO] Getting Terraform configuration..." -ForegroundColor Cyan
$TerraformDir = "../infrastructure/terraform"

Push-Location $TerraformDir
try {
    $ResourceGroupName = terraform output -raw resource_group_name 2>$null
    $OAuthFunctionName = terraform output -raw oauth_function_name 2>$null
    $JwtFunctionName = terraform output -raw jwt_authorizer_function_name 2>$null
    $BasicAuthFunctionName = terraform output -raw basic_auth_function_name 2>$null
    
    if (-not $ResourceGroupName) {
        throw "Failed to get resource group name from Terraform"
    }
    
    Write-Host "[SUCCESS] Configuration retrieved:" -ForegroundColor Green
    Write-Host "  Resource Group: $ResourceGroupName" -ForegroundColor Gray
    Write-Host "  OAuth Function: $OAuthFunctionName" -ForegroundColor Gray
    Write-Host "  JWT Function: $JwtFunctionName" -ForegroundColor Gray
    Write-Host "  Basic Auth Function: $BasicAuthFunctionName" -ForegroundColor Gray
}
finally {
    Pop-Location
}

# Function to deploy a Java Function App using Maven
function Deploy-JavaFunction {
    param(
        [string]$ProjectPath,
        [string]$FunctionAppName,
        [string]$ResourceGroup,
        [string]$DisplayName
    )
    
    Write-Host ""
    Write-Host "[INFO] Deploying $DisplayName..." -ForegroundColor Cyan
    Write-Host "  Project: $ProjectPath" -ForegroundColor Gray
    Write-Host "  Function App: $FunctionAppName" -ForegroundColor Gray
    
    if (-not (Test-Path $ProjectPath)) {
        Write-Host "[ERROR] Project path not found: $ProjectPath" -ForegroundColor Red
        return $false
    }
    
    Push-Location $ProjectPath
    try {
        # Clean and package the function
        if (-not $SkipBuild) {
            Write-Host "  [1/3] Cleaning project..." -ForegroundColor Yellow
            mvn clean -q
            if ($LASTEXITCODE -ne 0) {
                Write-Host "[ERROR] Maven clean failed for $DisplayName" -ForegroundColor Red
                return $false
            }
            
            Write-Host "  [2/3] Building and packaging function..." -ForegroundColor Yellow
            mvn package azure-functions:package -q
            if ($LASTEXITCODE -ne 0) {
                Write-Host "[ERROR] Maven package failed for $DisplayName" -ForegroundColor Red
                return $false
            }
        }
        
        # Deploy to Azure
        Write-Host "  [3/3] Deploying to Azure Function App..." -ForegroundColor Yellow
        
        # Update the pom.xml temporarily with the correct Function App name
        $pomPath = "pom.xml"
        $pomContent = Get-Content $pomPath -Raw
        $originalPomContent = $pomContent
        
        # Replace the appName in the Azure Functions plugin configuration
        $pomContent = $pomContent -replace '<appName>.*?</appName>', "<appName>$FunctionAppName</appName>"
        $pomContent = $pomContent -replace '<resourceGroup>.*?</resourceGroup>', "<resourceGroup>$ResourceGroup</resourceGroup>"
        
        Set-Content $pomPath $pomContent
        
        try {
            # Deploy using Azure Functions Maven plugin
            mvn azure-functions:deploy -q
            if ($LASTEXITCODE -ne 0) {
                Write-Host "[ERROR] Azure Functions deployment failed for $DisplayName" -ForegroundColor Red
                return $false
            }
        }
        finally {
            # Restore original pom.xml
            Set-Content $pomPath $originalPomContent
        }
        
        Write-Host "[SUCCESS] $DisplayName deployed successfully!" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "[ERROR] Exception during deployment of $DisplayName`: $_" -ForegroundColor Red
        return $false
    }
    finally {
        Pop-Location
    }
}

# Deploy each Function App
$deploymentResults = @()

Write-Host ""
Write-Host "Starting Function App Deployments" -ForegroundColor Blue
Write-Host "==================================" -ForegroundColor Blue

# 1. Deploy OAuth Server
$result1 = Deploy-JavaFunction -ProjectPath "../../authorization-service-server" -FunctionAppName $OAuthFunctionName -ResourceGroup $ResourceGroupName -DisplayName "OAuth Server"
$deploymentResults += @{Name="OAuth Server"; Success=$result1; FunctionApp=$OAuthFunctionName}

# 2. Deploy JWT Authorizer
$result2 = Deploy-JavaFunction -ProjectPath "../../authorization-service-jwt-authorizer" -FunctionAppName $JwtFunctionName -ResourceGroup $ResourceGroupName -DisplayName "JWT Authorizer"
$deploymentResults += @{Name="JWT Authorizer"; Success=$result2; FunctionApp=$JwtFunctionName}

# 3. Deploy Basic Authenticator
$result3 = Deploy-JavaFunction -ProjectPath "../../authorization-service-basic-authenticator" -FunctionAppName $BasicAuthFunctionName -ResourceGroup $ResourceGroupName -DisplayName "Basic Authenticator"
$deploymentResults += @{Name="Basic Authenticator"; Success=$result3; FunctionApp=$BasicAuthFunctionName}

# Display Results
Write-Host ""
Write-Host "Deployment Results Summary" -ForegroundColor Blue
Write-Host "==========================" -ForegroundColor Blue

$successCount = 0
$failedCount = 0

foreach ($result in $deploymentResults) {
    if ($result.Success) {
        Write-Host "  ✅ $($result.Name): DEPLOYED" -ForegroundColor Green
        $successCount++
    } else {
        Write-Host "  ❌ $($result.Name): FAILED" -ForegroundColor Red
        $failedCount++
    }
    
    if ($Verbose) {
        Write-Host "     Function App: $($result.FunctionApp)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Blue
Write-Host "  Successful: $successCount" -ForegroundColor Green
Write-Host "  Failed: $failedCount" -ForegroundColor Red

# Overall result
if ($failedCount -eq 0) {
    Write-Host ""
    Write-Host "[SUCCESS] All Java Functions deployed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Run test-deployed-functions.ps1 to verify functionality" -ForegroundColor Gray
    Write-Host "  2. Check Function App logs if any issues" -ForegroundColor Gray
    Write-Host "  3. Test APIM integration" -ForegroundColor Gray
    
    exit 0
} else {
    Write-Host ""
    Write-Host "[WARNING] Some deployments failed. Check the errors above." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Cyan
    Write-Host "  1. Check Maven is installed and configured" -ForegroundColor Gray
    Write-Host "  2. Verify Azure CLI is logged in" -ForegroundColor Gray
    Write-Host "  3. Check Function App names and resource group" -ForegroundColor Gray
    Write-Host "  4. Review Maven build logs for specific errors" -ForegroundColor Gray
    
    exit 1
} 