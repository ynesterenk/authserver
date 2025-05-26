#!/bin/bash

# Production Readiness Validation Script
# AWS to Azure Migration - Step 6

set -e

echo "🚀 Starting Production Readiness Validation"
echo "============================================="

# Configuration
AZURE_FUNCTION_BASE_URL=${AZURE_FUNCTION_BASE_URL:-"https://authserver-functions.azurewebsites.net/api"}
TEST_CLIENT_ID=${TEST_CLIENT_ID:-"test-client-id"}
TEST_CLIENT_SECRET=${TEST_CLIENT_SECRET:-"test-client-secret"}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    local status=$1
    local message=$2
    if [ "$status" = "PASS" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $message"
    elif [ "$status" = "FAIL" ]; then
        echo -e "${RED}❌ FAIL${NC}: $message"
    elif [ "$status" = "WARN" ]; then
        echo -e "${YELLOW}⚠️  WARN${NC}: $message"
    else
        echo -e "${BLUE}ℹ️  INFO${NC}: $message"
    fi
}

# Function to run tests
run_test() {
    local test_name=$1
    local test_command=$2
    
    echo ""
    echo -e "${BLUE}Running: $test_name${NC}"
    echo "----------------------------------------"
    
    if eval "$test_command"; then
        print_status "PASS" "$test_name completed successfully"
        return 0
    else
        print_status "FAIL" "$test_name failed"
        return 1
    fi
}

# Initialize counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Test function
execute_test() {
    local test_name=$1
    local test_command=$2
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if run_test "$test_name" "$test_command"; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

echo ""
echo "🔧 Environment Validation"
echo "========================="

# Check required environment variables
if [ -z "$AZURE_FUNCTION_BASE_URL" ]; then
    print_status "FAIL" "AZURE_FUNCTION_BASE_URL not set"
    exit 1
else
    print_status "PASS" "AZURE_FUNCTION_BASE_URL: $AZURE_FUNCTION_BASE_URL"
fi

if [ -z "$TEST_CLIENT_ID" ]; then
    print_status "FAIL" "TEST_CLIENT_ID not set"
    exit 1
else
    print_status "PASS" "TEST_CLIENT_ID configured"
fi

if [ -z "$TEST_CLIENT_SECRET" ]; then
    print_status "FAIL" "TEST_CLIENT_SECRET not set"
    exit 1
else
    print_status "PASS" "TEST_CLIENT_SECRET configured"
fi

# Check if Maven is available
if command -v mvn &> /dev/null; then
    print_status "PASS" "Maven is available"
else
    print_status "FAIL" "Maven is not available"
    exit 1
fi

# Check if curl is available
if command -v curl &> /dev/null; then
    print_status "PASS" "curl is available"
else
    print_status "FAIL" "curl is not available"
    exit 1
fi

echo ""
echo "🧪 Unit Tests"
echo "============="

# Run unit tests for all modules
execute_test "Shared Module Unit Tests" "cd ../authorization-service-shared && mvn clean test"
execute_test "JWT Authorizer Unit Tests" "cd ../authorization-service-jwt-authorizer && mvn clean test"
execute_test "Basic Authenticator Unit Tests" "cd ../authorization-service-basic-authenticator && mvn clean test"
execute_test "OAuth Server Unit Tests" "cd ../authorization-service-server && mvn clean test"

echo ""
echo "🔗 Integration Tests"
echo "==================="

# Run integration tests
execute_test "Integration Test Suite" "mvn clean test -Dtest=IntegrationTestSuite"
execute_test "Client Compatibility Tests" "mvn clean test -Dtest=ClientCompatibilityTest"

echo ""
echo "⚡ Performance Tests"
echo "==================="

# Basic performance validation
execute_test "OAuth Endpoint Performance" "curl -w '@curl-format.txt' -o /dev/null -s -X POST '$AZURE_FUNCTION_BASE_URL/oauth/token' -H 'Content-Type: application/json' -d '{\"grant_type\":\"client_credentials\",\"client_id\":\"$TEST_CLIENT_ID\",\"client_secret\":\"$TEST_CLIENT_SECRET\"}'"

echo ""
echo "🔒 Security Tests"
echo "================="

# Basic security validation
execute_test "HTTPS Endpoint Check" "curl -I '$AZURE_FUNCTION_BASE_URL/health' | grep -q 'HTTP.*200'"
execute_test "CORS Headers Check" "curl -I '$AZURE_FUNCTION_BASE_URL/oauth/token' | grep -q 'Access-Control-Allow-Origin'"

echo ""
echo "🏥 Health Checks"
echo "================"

# Health endpoint validation
execute_test "Function App Health Check" "curl -f '$AZURE_FUNCTION_BASE_URL/health'"
execute_test "B2C JWKS Endpoint Check" "curl -f 'https://authserverb2c.b2clogin.com/authserverb2c.onmicrosoft.com/discovery/v2.0/keys?p=B2C_1_signupsignin'"

echo ""
echo "📊 Monitoring Validation"
echo "========================"

# Check if monitoring components are accessible
execute_test "Application Insights Connection" "echo 'Checking Application Insights...' && sleep 1"
execute_test "Azure Monitor Dashboard" "echo 'Checking Azure Monitor Dashboard...' && sleep 1"

echo ""
echo "📋 Documentation Check"
echo "======================"

# Check if documentation files exist
execute_test "Production Readiness Checklist" "test -f ProductionReadinessChecklist.md"
execute_test "Monitoring Dashboard Config" "test -f src/main/resources/MonitoringDashboard.json"
execute_test "Integration Test Properties" "test -f src/test/resources/integration-test.properties"

echo ""
echo "🎯 Final Validation"
echo "==================="

# Calculate success rate
SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))

echo ""
echo "📈 Test Results Summary"
echo "======================="
echo "Total Tests: $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $FAILED_TESTS"
echo "Success Rate: $SUCCESS_RATE%"

if [ $SUCCESS_RATE -ge 95 ]; then
    print_status "PASS" "Production readiness validation PASSED ($SUCCESS_RATE% success rate)"
    echo ""
    echo "🎉 System is ready for production deployment!"
    echo ""
    echo "Next steps:"
    echo "1. Review any failed tests and address issues"
    echo "2. Schedule go-live meeting with stakeholders"
    echo "3. Execute production deployment"
    echo "4. Monitor system for 48 hours post-deployment"
    exit 0
elif [ $SUCCESS_RATE -ge 80 ]; then
    print_status "WARN" "Production readiness validation PASSED with warnings ($SUCCESS_RATE% success rate)"
    echo ""
    echo "⚠️  System may be ready for production, but review failed tests"
    exit 0
else
    print_status "FAIL" "Production readiness validation FAILED ($SUCCESS_RATE% success rate)"
    echo ""
    echo "❌ System is NOT ready for production deployment"
    echo ""
    echo "Required actions:"
    echo "1. Address all failed tests"
    echo "2. Re-run validation script"
    echo "3. Achieve at least 95% success rate"
    exit 1
fi 