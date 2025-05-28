# Proper Java Function Apps Deployment Script
# This script builds Azure Functions packages with function.json files and deploys them

param(
    [switch]$Verbose = $false,
    [switch]$SkipBuild = $false
)

Write-Host "Deploying Java Function Apps to Azure (Proper Method)" -ForegroundColor Blue
Write-Host "======================================================" -ForegroundColor Blue

# Get Terraform outputs
Write-Host "[INFO] Getting deployment configuration..." -ForegroundColor Cyan
$TerraformDir = "../infrastructure/terraform"

Push-Location $TerraformDir
$ResourceGroupName = terraform output -raw resource_group_name 2>$null
$OAuthFunctionName = terraform output -raw oauth_function_name 2>$null
$JwtFunctionName = terraform output -raw jwt_authorizer_function_name 2>$null
$BasicAuthFunctionName = terraform output -raw basic_auth_function_name 2>$null
Pop-Location

Write-Host "[SUCCESS] Configuration retrieved" -ForegroundColor Green
Write-Host "  Resource Group: $ResourceGroupName" -ForegroundColor Gray
Write-Host "  OAuth Function: $OAuthFunctionName" -ForegroundColor Gray
Write-Host "  JWT Function: $JwtFunctionName" -ForegroundColor Gray
Write-Host "  Basic Auth Function: $BasicAuthFunctionName" -ForegroundColor Gray

# Function to build and deploy a Java Function App properly
function Deploy-JavaFunctionProper {
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
        # Step 1: Build Azure Functions package with function.json files
        if (-not $SkipBuild) {
            Write-Host "  [1/4] Building Azure Functions package..." -ForegroundColor Yellow
            mvn clean package azure-functions:package -DskipTests -q
            if ($LASTEXITCODE -ne 0) {
                Write-Host "[ERROR] Maven build failed for $DisplayName" -ForegroundColor Red
                return $false
            }
        }
        
        # Step 2: Check if Azure Functions artifacts were generated
        $azureFunctionsDir = "target/azure-functions"
        if (-not (Test-Path $azureFunctionsDir)) {
            Write-Host "[ERROR] Azure Functions artifacts not generated for $DisplayName" -ForegroundColor Red
            return $false
        }
        
        # Step 3: Find the generated function app directory
        $functionAppDirs = Get-ChildItem $azureFunctionsDir -Directory
        if ($functionAppDirs.Count -eq 0) {
            Write-Host "[ERROR] No function app directory found in $azureFunctionsDir" -ForegroundColor Red
            return $false
        }
        
        $functionAppDir = $functionAppDirs[0].FullName
        Write-Host "  [2/4] Found function app directory: $($functionAppDirs[0].Name)" -ForegroundColor Yellow
        
        # Step 4: List what functions were generated
        $functionDirs = Get-ChildItem $functionAppDir -Directory | Where-Object { $_.Name -ne "lib" }
        if ($functionDirs.Count -gt 0) {
            Write-Host "  [2/4] Generated functions:" -ForegroundColor Yellow
            foreach ($funcDir in $functionDirs) {
                $functionJsonPath = Join-Path $funcDir.FullName "function.json"
                if (Test-Path $functionJsonPath) {
                    $functionJson = Get-Content $functionJsonPath | ConvertFrom-Json
                    $route = $functionJson.bindings | Where-Object { $_.type -eq "httpTrigger" } | Select-Object -ExpandProperty route -First 1
                    $methods = ($functionJson.bindings | Where-Object { $_.type -eq "httpTrigger" } | Select-Object -ExpandProperty methods -First 1) -join ", "
                    Write-Host "    - $($funcDir.Name): /$route ($methods)" -ForegroundColor Gray
                } else {
                    Write-Host "    - $($funcDir.Name): No function.json found" -ForegroundColor Red
                }
            }
        }
        
        # Step 5: Create deployment package
        $zipPath = "target/$DisplayName-functions.zip"
        Write-Host "  [3/4] Creating deployment package..." -ForegroundColor Yellow
        
        Push-Location $functionAppDir
        try {
            Compress-Archive -Path * -DestinationPath "../../$DisplayName-functions.zip" -Force
        }
        finally {
            Pop-Location
        }
        
        if (-not (Test-Path $zipPath)) {
            Write-Host "[ERROR] Failed to create deployment package for $DisplayName" -ForegroundColor Red
            return $false
        }
        
        # Step 6: Deploy to Azure
        Write-Host "  [4/4] Deploying to Azure Function App..." -ForegroundColor Yellow
        
        az functionapp deployment source config-zip --resource-group $ResourceGroup --name $FunctionAppName --src $zipPath --timeout 600
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Azure deployment failed for $DisplayName" -ForegroundColor Red
            return $false
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

# Deploy OAuth Server
$oauth_success = Deploy-JavaFunctionProper -ProjectPath "../authorization-service-server" -FunctionAppName $OAuthFunctionName -ResourceGroup $ResourceGroupName -DisplayName "OAuth-Server"

# Deploy JWT Authorizer
$jwt_success = Deploy-JavaFunctionProper -ProjectPath "../authorization-service-jwt-authorizer" -FunctionAppName $JwtFunctionName -ResourceGroup $ResourceGroupName -DisplayName "JWT-Authorizer"

# Deploy Basic Authenticator
$basic_success = Deploy-JavaFunctionProper -ProjectPath "../authorization-service-basic-authenticator" -FunctionAppName $BasicAuthFunctionName -ResourceGroup $ResourceGroupName -DisplayName "Basic-Authenticator"

# Restart Function Apps to ensure new code is loaded
Write-Host ""
Write-Host "[INFO] Restarting Function Apps..." -ForegroundColor Cyan

if ($oauth_success) {
    Write-Host "  Restarting OAuth Server..." -ForegroundColor Gray
    az functionapp restart --resource-group $ResourceGroupName --name $OAuthFunctionName | Out-Null
}

if ($jwt_success) {
    Write-Host "  Restarting JWT Authorizer..." -ForegroundColor Gray
    az functionapp restart --resource-group $ResourceGroupName --name $JwtFunctionName | Out-Null
}

if ($basic_success) {
    Write-Host "  Restarting Basic Authenticator..." -ForegroundColor Gray
    az functionapp restart --resource-group $ResourceGroupName --name $BasicAuthFunctionName | Out-Null
}

# Wait for restarts to complete
Write-Host "  Waiting for restarts to complete..." -ForegroundColor Gray
Start-Sleep -Seconds 30


# Summary
Write-Host ""
Write-Host "Deployment Summary" -ForegroundColor Blue
Write-Host "=================" -ForegroundColor Blue

$successCount = 0
if ($oauth_success) { 
    Write-Host "  OAuth Server: SUCCESS" -ForegroundColor Green
    $successCount++
} else {
    Write-Host "  OAuth Server: FAILED" -ForegroundColor Red
}

if ($jwt_success) { 
    Write-Host "  JWT Authorizer: SUCCESS" -ForegroundColor Green
    $successCount++
} else {
    Write-Host "  JWT Authorizer: FAILED" -ForegroundColor Red
}

if ($basic_success) { 
    Write-Host "  Basic Authenticator: SUCCESS" -ForegroundColor Green
    $successCount++
} else {
    Write-Host "  Basic Authenticator: FAILED" -ForegroundColor Red
}


Write-Host ""
Write-Host "Results: $successCount/3 deployments successful" -ForegroundColor $(if ($successCount -eq 3) { "Green" } else { "Yellow" })

if ($successCount -eq 3) {
    Write-Host ""
    Write-Host "[SUCCESS] All Java Function Apps deployed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Testing deployed functions..." -ForegroundColor Cyan
    
    # Quick test of OAuth server
    try {
        Write-Host "  Testing OAuth server..." -ForegroundColor Gray
        $response = Invoke-WebRequest -Uri "https://$OAuthFunctionName.azurewebsites.net/api/oauth/token" -Method POST -TimeoutSec 10 -ErrorAction Stop
        Write-Host "  ✅ OAuth server responding (Status: $($response.StatusCode))" -ForegroundColor Green
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode
        if ($statusCode -eq 400 -or $statusCode -eq 401) {
            Write-Host "  ✅ OAuth server responding (Status: $statusCode - Expected for missing auth)" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️ OAuth server status: $statusCode" -ForegroundColor Yellow
        }
    }
    
    Write-Host ""
    Write-Host "Function App URLs:" -ForegroundColor Cyan
    Write-Host "  OAuth Server: https://$OAuthFunctionName.azurewebsites.net" -ForegroundColor Gray
    Write-Host "  JWT Authorizer: https://$JwtFunctionName.azurewebsites.net" -ForegroundColor Gray
    Write-Host "  Basic Authenticator: https://$BasicAuthFunctionName.azurewebsites.net" -ForegroundColor Gray
    Write-Host ""
    Write-Host "APIM Endpoints (with subscription key):" -ForegroundColor Cyan
    Write-Host "  OAuth Token: https://authserver-dev-apim.azure-api.net/oauth/token" -ForegroundColor Gray
    Write-Host "  JWT Auth: https://authserver-dev-apim.azure-api.net/authorize/jwt" -ForegroundColor Gray
    Write-Host "  Basic Auth: https://authserver-dev-apim.azure-api.net/authorize/basic" -ForegroundColor Gray
    Write-Host "  Password Change: https://authserver-dev-apim.azure-api.net/change-password" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Run test-deployed-functions.ps1 to verify full functionality" -ForegroundColor Gray
    Write-Host "  2. Test APIM integration with subscription keys" -ForegroundColor Gray
    Write-Host "  3. Monitor function logs in Azure Portal" -ForegroundColor Gray
    
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