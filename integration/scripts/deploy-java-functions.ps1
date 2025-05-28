# Deploy Java Function Apps to Azure
# This script deploys all Java applications to their respective Azure Function Apps

param(
    [switch]$SkipBuild = $false,
    [switch]$Verbose = $false
)

$ErrorActionPreference = "Stop"

Write-Host "Deploying Java Function Apps to Azure" -ForegroundColor Blue
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

# Check prerequisites
Write-Status "Checking prerequisites..."

# Check if Azure CLI is installed
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Error "Azure CLI is not installed. Please install Azure CLI first."
    exit 1
}

# Check if logged in to Azure
try {
    $account = az account show --query "name" -o tsv 2>$null
    if (-not $account) {
        Write-Error "Not logged in to Azure. Please run 'az login' first."
        exit 1
    }
    Write-Success "Logged in to Azure account: $account"
}
catch {
    Write-Error "Not logged in to Azure. Please run 'az login' first."
    exit 1
}

# Get Terraform outputs
Write-Status "Getting deployment configuration from Terraform..."
$TerraformDir = "../infrastructure/terraform"

if (-not (Test-Path $TerraformDir)) {
    Write-Error "Terraform directory not found at $TerraformDir"
    exit 1
}

Push-Location $TerraformDir

try {
    $ResourceGroupName = terraform output -raw resource_group_name 2>$null
    $OAuthFunctionName = terraform output -raw oauth_function_name 2>$null
    $JwtFunctionName = terraform output -raw jwt_authorizer_function_name 2>$null
    $BasicAuthFunctionName = terraform output -raw basic_auth_function_name 2>$null
    $PasswordFunctionName = terraform output -raw password_change_function_name 2>$null
    
    if (-not $ResourceGroupName -or -not $OAuthFunctionName) {
        Write-Error "Failed to get required configuration from Terraform outputs"
        exit 1
    }
    
    Write-Success "Configuration retrieved successfully"
    Write-Host "  Resource Group: $ResourceGroupName"
    Write-Host "  OAuth Function: $OAuthFunctionName"
    Write-Host "  JWT Function: $JwtFunctionName"
    Write-Host "  Basic Auth Function: $BasicAuthFunctionName"
    Write-Host "  Password Function: $PasswordFunctionName"
}
finally {
    Pop-Location
}

# Build applications if not skipped
if (-not $SkipBuild) {
    Write-Status "Building Java applications..."
    
    # Build OAuth Server
    Write-Status "Building OAuth Server..."
    Push-Location "../authorization-service-server"
    try {
        mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to build OAuth Server"
            exit 1
        }
        Write-Success "OAuth Server built successfully"
    }
    finally {
        Pop-Location
    }
    
    # Build JWT Authorizer
    Write-Status "Building JWT Authorizer..."
    Push-Location "../authorization-service-jwt-authorizer"
    try {
        mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to build JWT Authorizer"
            exit 1
        }
        Write-Success "JWT Authorizer built successfully"
    }
    finally {
        Pop-Location
    }
    
    # Build Basic Authenticator
    Write-Status "Building Basic Authenticator..."
    Push-Location "../authorization-service-basic-authenticator"
    try {
        mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to build Basic Authenticator"
            exit 1
        }
        Write-Success "Basic Authenticator built successfully"
    }
    finally {
        Pop-Location
    }
} else {
    Write-Status "Skipping build (using existing artifacts)"
}

# Function to deploy a Function App
function Deploy-FunctionApp {
    param(
        [string]$FunctionName,
        [string]$SourcePath,
        [string]$DisplayName
    )
    
    Write-Status "Deploying $DisplayName to $FunctionName..."
    
    if (-not (Test-Path $SourcePath)) {
        Write-Error "Source path not found: $SourcePath"
        return $false
    }
    
    try {
        # Create a temporary zip file for deployment
        $TempZip = [System.IO.Path]::GetTempFileName() + ".zip"
        
        # For OAuth server, use the Azure Functions package
        if ($DisplayName -eq "OAuth Server") {
            $AzureFunctionsPath = "$SourcePath/target/azure-functions/authorization-service-server"
            if (Test-Path $AzureFunctionsPath) {
                Compress-Archive -Path "$AzureFunctionsPath/*" -DestinationPath $TempZip -Force
            } else {
                Write-Error "Azure Functions package not found for OAuth Server"
                return $false
            }
        } else {
            # For other services, create a simple package
            $TempDir = [System.IO.Path]::GetTempPath() + [System.Guid]::NewGuid().ToString()
            New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
            
            # Copy JAR file
            $JarFile = Get-ChildItem -Path "$SourcePath/target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } | Select-Object -First 1
            if ($JarFile) {
                Copy-Item $JarFile.FullName -Destination "$TempDir/app.jar"
                
                # Create basic host.json if it doesn't exist
                if (Test-Path "$SourcePath/host.json") {
                    Copy-Item "$SourcePath/host.json" -Destination "$TempDir/host.json"
                } else {
                    @'
{
  "version": "2.0",
  "extensionBundle": {
    "id": "Microsoft.Azure.Functions.ExtensionBundle",
    "version": "[3.*, 4.0.0)"
  }
}
'@ | Out-File -FilePath "$TempDir/host.json" -Encoding UTF8
                }
                
                Compress-Archive -Path "$TempDir/*" -DestinationPath $TempZip -Force
                Remove-Item -Path $TempDir -Recurse -Force
            } else {
                Write-Error "JAR file not found in $SourcePath/target"
                return $false
            }
        }
        
        # Deploy using Azure CLI
        Write-Status "Uploading $DisplayName to Azure..."
        if ($Verbose) {
            $deployResult = az functionapp deployment source config-zip `
                --resource-group $ResourceGroupName `
                --name $FunctionName `
                --src $TempZip `
                --build-remote true `
                --verbose 2>&1
        } else {
            $deployResult = az functionapp deployment source config-zip `
                --resource-group $ResourceGroupName `
                --name $FunctionName `
                --src $TempZip `
                --build-remote true 2>&1
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "$DisplayName deployed successfully to $FunctionName"
            
            # Wait a moment for deployment to settle
            Start-Sleep -Seconds 5
            
            # Restart the function app to ensure new code is loaded
            Write-Status "Restarting $FunctionName..."
            az functionapp restart --resource-group $ResourceGroupName --name $FunctionName | Out-Null
            
            return $true
        } else {
            Write-Error "Failed to deploy $DisplayName`: $deployResult"
            return $false
        }
    }
    catch {
        Write-Error "Error deploying $DisplayName`: $_"
        return $false
    }
    finally {
        # Clean up temp file
        if (Test-Path $TempZip) {
            Remove-Item $TempZip -Force
        }
    }
}

# Deploy all Function Apps
Write-Status "Starting deployment of all Function Apps..."

$deploymentResults = @()

# Deploy OAuth Server (includes password change functionality)
$result1 = Deploy-FunctionApp -FunctionName $OAuthFunctionName -SourcePath "../authorization-service-server" -DisplayName "OAuth Server"
$deploymentResults += @{Service="OAuth Server"; FunctionName=$OAuthFunctionName; Success=$result1}

# Deploy JWT Authorizer
$result2 = Deploy-FunctionApp -FunctionName $JwtFunctionName -SourcePath "../authorization-service-jwt-authorizer" -DisplayName "JWT Authorizer"
$deploymentResults += @{Service="JWT Authorizer"; FunctionName=$JwtFunctionName; Success=$result2}

# Deploy Basic Authenticator
$result3 = Deploy-FunctionApp -FunctionName $BasicAuthFunctionName -SourcePath "../authorization-service-basic-authenticator" -DisplayName "Basic Authenticator"
$deploymentResults += @{Service="Basic Authenticator"; FunctionName=$BasicAuthFunctionName; Success=$result3}

# Note: Password change functionality is included in the OAuth Server
Write-Status "Configuring password change endpoint..."
Write-Status "Password change functionality is included in the OAuth Server deployment"

# Display deployment summary
Write-Host ""
Write-Host "Deployment Summary" -ForegroundColor Blue
Write-Host "=====================" -ForegroundColor Blue

$successCount = 0
foreach ($result in $deploymentResults) {
    $status = if ($result.Success) { "SUCCESS" } else { "FAILED" }
    $color = if ($result.Success) { "Green" } else { "Red" }
    Write-Host "  $($result.Service): $status" -ForegroundColor $color
    Write-Host "    Function App: $($result.FunctionName)" -ForegroundColor Gray
    if ($result.Success) { $successCount++ }
}

Write-Host ""
Write-Host "Deployment Results: $successCount/$($deploymentResults.Count) successful" -ForegroundColor $(if ($successCount -eq $deploymentResults.Count) { "Green" } else { "Yellow" })

if ($successCount -eq $deploymentResults.Count) {
    Write-Success "All Java Function Apps deployed successfully!"
    
    Write-Host ""
    Write-Status "Function App URLs:"
    Write-Host "  OAuth Server: https://$OAuthFunctionName.azurewebsites.net"
    Write-Host "  JWT Authorizer: https://$JwtFunctionName.azurewebsites.net"
    Write-Host "  Basic Authenticator: https://$BasicAuthFunctionName.azurewebsites.net"
    Write-Host "  Password Change: https://$OAuthFunctionName.azurewebsites.net (included in OAuth Server)"
    
    Write-Host ""
    Write-Status "Next steps:"
    Write-Host "  1. Test the deployed functions using the APIM endpoints"
    Write-Host "  2. Run integration tests to verify functionality"
    Write-Host "  3. Monitor function logs in Azure Portal"
    Write-Host "  4. Configure any additional environment variables if needed"
    
    exit 0
} else {
    Write-Error "Some deployments failed. Please check the errors above."
    exit 1
} 