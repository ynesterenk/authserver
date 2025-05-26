#!/bin/bash

# End-to-End Integration Tests for Deployed Azure Infrastructure
# This script configures environment variables from Terraform outputs and runs comprehensive tests

set -e

echo "🚀 Starting End-to-End Integration Tests for Azure Infrastructure"
echo "=================================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    print_error "Please run this script from the integration directory"
    exit 1
fi

# Navigate to terraform directory to get outputs
TERRAFORM_DIR="../infrastructure/terraform"
if [ ! -d "$TERRAFORM_DIR" ]; then
    print_error "Terraform directory not found at $TERRAFORM_DIR"
    exit 1
fi

print_status "Extracting configuration from deployed infrastructure..."

# Get Terraform outputs
cd "$TERRAFORM_DIR"

# Extract key values from terraform output
APIM_GATEWAY_URL=$(terraform output -raw apim_gateway_url 2>/dev/null || echo "")
B2C_CLIENT_ID=$(terraform output -raw b2c_client_id 2>/dev/null || echo "")
B2C_TENANT_ID=$(terraform output -raw b2c_tenant_id 2>/dev/null || echo "")
B2C_JWKS_URL=$(terraform output -raw b2c_jwks_url 2>/dev/null || echo "")
KEY_VAULT_NAME=$(terraform output -raw key_vault_name 2>/dev/null || echo "")
RESOURCE_GROUP_NAME=$(terraform output -raw resource_group_name 2>/dev/null || echo "")

# Function URLs
OAUTH_FUNCTION_URL=$(terraform output -raw oauth_function_url 2>/dev/null || echo "")
JWT_FUNCTION_URL=$(terraform output -raw jwt_authorizer_function_url 2>/dev/null || echo "")
BASIC_FUNCTION_URL=$(terraform output -raw basic_auth_function_url 2>/dev/null || echo "")
PASSWORD_FUNCTION_URL=$(terraform output -raw password_change_function_url 2>/dev/null || echo "")

cd - > /dev/null

# Validate required outputs
if [ -z "$APIM_GATEWAY_URL" ] || [ -z "$B2C_CLIENT_ID" ]; then
    print_error "Failed to extract required configuration from Terraform outputs"
    print_error "Please ensure Terraform has been applied successfully"
    exit 1
fi

print_success "Configuration extracted successfully"

# Set environment variables
export AZURE_FUNCTION_BASE_URL="$APIM_GATEWAY_URL"
export TEST_CLIENT_ID="$B2C_CLIENT_ID"
export AZURE_B2C_TENANT_ID="$B2C_TENANT_ID"
export AZURE_B2C_JWKS_URL="$B2C_JWKS_URL"

# Get B2C client secret from Azure Key Vault
print_status "Retrieving B2C client secret from Key Vault..."
if command -v az &> /dev/null; then
    B2C_CLIENT_SECRET=$(az keyvault secret show --vault-name "$KEY_VAULT_NAME" --name "b2c-client-secret" --query "value" -o tsv 2>/dev/null || echo "")
    if [ -n "$B2C_CLIENT_SECRET" ]; then
        export B2C_CLIENT_SECRET="$B2C_CLIENT_SECRET"
        export TEST_CLIENT_SECRET="$B2C_CLIENT_SECRET"
        print_success "B2C client secret retrieved from Key Vault"
    else
        print_warning "Could not retrieve B2C client secret from Key Vault"
        print_warning "Tests requiring authentication may fail"
    fi
else
    print_warning "Azure CLI not found. Cannot retrieve secrets from Key Vault"
    print_warning "Please install Azure CLI or set B2C_CLIENT_SECRET manually"
fi

# Get Application Insights connection string
print_status "Retrieving Application Insights connection string..."
if command -v az &> /dev/null; then
    APP_INSIGHTS_CONNECTION=$(az monitor app-insights component show --app authserver-dev-ai --resource-group "$RESOURCE_GROUP_NAME" --query "connectionString" -o tsv 2>/dev/null || echo "")
    if [ -n "$APP_INSIGHTS_CONNECTION" ]; then
        export APPLICATIONINSIGHTS_CONNECTION_STRING="$APP_INSIGHTS_CONNECTION"
        print_success "Application Insights connection string retrieved"
    else
        print_warning "Could not retrieve Application Insights connection string"
    fi
fi

# Display configuration
print_status "Test Configuration:"
echo "  APIM Gateway URL: $APIM_GATEWAY_URL"
echo "  B2C Client ID: $B2C_CLIENT_ID"
echo "  B2C Tenant ID: $B2C_TENANT_ID"
echo "  Resource Group: $RESOURCE_GROUP_NAME"
echo "  Key Vault: $KEY_VAULT_NAME"

# Test endpoints availability
print_status "Testing endpoint availability..."

test_endpoint() {
    local url=$1
    local name=$2
    
    if curl -s --max-time 10 "$url" > /dev/null 2>&1; then
        print_success "$name endpoint is accessible"
        return 0
    else
        print_warning "$name endpoint is not accessible: $url"
        return 1
    fi
}

# Test health endpoint first
HEALTH_URL="$APIM_GATEWAY_URL/health"
if test_endpoint "$HEALTH_URL" "Health Check"; then
    print_success "Infrastructure appears to be healthy"
else
    print_warning "Health check failed, but continuing with tests..."
fi

# Test B2C JWKS endpoint
if test_endpoint "$B2C_JWKS_URL" "B2C JWKS"; then
    print_success "B2C configuration is accessible"
fi

# Run the integration tests
print_status "Starting Maven integration tests..."
echo "=================================================================="

# Set Maven options for better output
export MAVEN_OPTS="-Xmx1024m"

# Run tests with specific profiles
mvn clean test \
    -Dtest.environment=azure-dev \
    -Dazure.function.base.url="$APIM_GATEWAY_URL" \
    -Dtest.client.id="$B2C_CLIENT_ID" \
    -Dazure.b2c.jwks.url="$B2C_JWKS_URL" \
    -Dmaven.test.failure.ignore=false \
    -Dsurefire.printSummary=true \
    -Dsurefire.reportFormat=plain

TEST_EXIT_CODE=$?

echo "=================================================================="

if [ $TEST_EXIT_CODE -eq 0 ]; then
    print_success "🎉 All integration tests passed!"
    print_success "Your Azure infrastructure is working correctly"
    
    echo ""
    print_status "Available endpoints for testing:"
    echo "  OAuth Token: $APIM_GATEWAY_URL/oauth/token"
    echo "  JWT Authorization: $APIM_GATEWAY_URL/authorize/jwt"
    echo "  Basic Authorization: $APIM_GATEWAY_URL/authorize/basic"
    echo "  Password Change: $APIM_GATEWAY_URL/change-password"
    echo "  Health Check: $APIM_GATEWAY_URL/health"
    
    echo ""
    print_status "Next steps:"
    echo "  1. Deploy your Java application code to the Function Apps"
    echo "  2. Run performance tests under load"
    echo "  3. Configure production monitoring alerts"
    echo "  4. Update client applications to use new endpoints"
    
else
    print_error "❌ Some integration tests failed"
    print_error "Please check the test output above for details"
    
    echo ""
    print_status "Common troubleshooting steps:"
    echo "  1. Verify all Azure resources are deployed correctly"
    echo "  2. Check Function App logs in Azure Portal"
    echo "  3. Validate B2C configuration and test credentials"
    echo "  4. Ensure APIM policies are configured correctly"
fi

exit $TEST_EXIT_CODE 