# Simple Java Function Apps Deployment Script
# This script deploys JAR files directly to Azure Function Apps

param(
    [switch]$Verbose = $false
)

Write-Host "Deploying Java Function Apps to Azure" -ForegroundColor Blue
Write-Host "=====================================" -ForegroundColor Blue

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

# Deploy OAuth Server
Write-Host ""
Write-Host "[INFO] Deploying OAuth Server..." -ForegroundColor Cyan
$oauthJar = "../authorization-service-server/target/authorization-service-server-1.0-SNAPSHOT.jar"
if (Test-Path $oauthJar) {
    az functionapp deployment source config-zip `
        --resource-group $ResourceGroupName `
        --name $OAuthFunctionName `
        --src $oauthJar
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[SUCCESS] OAuth Server deployed successfully" -ForegroundColor Green
        $oauth_success = $true
    } else {
        Write-Host "[ERROR] OAuth Server deployment failed" -ForegroundColor Red
        $oauth_success = $false
    }
} else {
    Write-Host "[ERROR] OAuth Server JAR not found: $oauthJar" -ForegroundColor Red
    $oauth_success = $false
}

# Deploy JWT Authorizer
Write-Host ""
Write-Host "[INFO] Deploying JWT Authorizer..." -ForegroundColor Cyan
$jwtJar = "../authorization-service-jwt-authorizer/target/authorization-service-jwt-authorizer-1.0-SNAPSHOT.jar"
if (Test-Path $jwtJar) {
    az functionapp deployment source config-zip `
        --resource-group $ResourceGroupName `
        --name $JwtFunctionName `
        --src $jwtJar
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[SUCCESS] JWT Authorizer deployed successfully" -ForegroundColor Green
        $jwt_success = $true
    } else {
        Write-Host "[ERROR] JWT Authorizer deployment failed" -ForegroundColor Red
        $jwt_success = $false
    }
} else {
    Write-Host "[ERROR] JWT Authorizer JAR not found: $jwtJar" -ForegroundColor Red
    $jwt_success = $false
}

# Deploy Basic Authenticator
Write-Host ""
Write-Host "[INFO] Deploying Basic Authenticator..." -ForegroundColor Cyan
$basicJar = "../authorization-service-basic-authenticator/target/authorization-service-basic-authenticator-1.0-SNAPSHOT.jar"
if (Test-Path $basicJar) {
    az functionapp deployment source config-zip `
        --resource-group $ResourceGroupName `
        --name $BasicAuthFunctionName `
        --src $basicJar
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[SUCCESS] Basic Authenticator deployed successfully" -ForegroundColor Green
        $basic_success = $true
    } else {
        Write-Host "[ERROR] Basic Authenticator deployment failed" -ForegroundColor Red
        $basic_success = $false
    }
} else {
    Write-Host "[ERROR] Basic Authenticator JAR not found: $basicJar" -ForegroundColor Red
    $basic_success = $false
}

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
    Write-Host "  1. Test the deployed functions using subscription keys" -ForegroundColor Gray
    Write-Host "  2. Run integration tests" -ForegroundColor Gray
    Write-Host "  3. Monitor function logs in Azure Portal" -ForegroundColor Gray
    
    exit 0
} else {
    Write-Host ""
    Write-Host "[WARNING] Some deployments failed. Check the errors above." -ForegroundColor Yellow
    exit 1
} 