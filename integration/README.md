# Integration Testing and Monitoring

This module provides comprehensive integration testing, monitoring, and production readiness validation for the AWS to Azure migration.

## Overview

The integration module contains:
- **End-to-end integration tests** for all Azure Functions
- **Client compatibility tests** for various client types
- **Performance monitoring** with Azure Application Insights
- **Production readiness validation** scripts
- **Azure Monitor dashboard** configuration

## Prerequisites

- Java 11+
- Maven 3.6+
- Azure Function App deployed and accessible
- Azure AD B2C tenant configured
- Environment variables configured

## Environment Variables

Set the following environment variables before running tests:

```bash
export AZURE_FUNCTION_BASE_URL="https://authserver-functions.azurewebsites.net/api"
export TEST_CLIENT_ID="your-test-client-id"
export TEST_CLIENT_SECRET="your-test-client-secret"
export APPLICATIONINSIGHTS_CONNECTION_STRING="your-app-insights-connection-string"
export AZURE_MONITOR_WORKSPACE_ID="your-monitor-workspace-id"
```

## Running Tests

### All Tests
```bash
mvn clean test
```

### Integration Tests Only
```bash
mvn test -Dtest=IntegrationTestSuite
```

### Client Compatibility Tests Only
```bash
mvn test -Dtest=ClientCompatibilityTest
```

### Performance Tests
```bash
mvn test -Dtest=IntegrationTestSuite#testPerformanceRequirements
```

## Production Readiness Validation

Run the comprehensive production readiness validation:

```bash
./scripts/validate-production-readiness.sh
```

This script will:
- Validate environment configuration
- Run all unit tests across modules
- Execute integration test suite
- Perform basic performance validation
- Check security configurations
- Validate health endpoints
- Verify monitoring setup
- Check documentation completeness

## Performance Monitoring

### Start Performance Monitor
```bash
java -cp target/classes:target/dependency/* integration.monitoring.PerformanceMonitor
```

The performance monitor will:
- Track OAuth endpoint response times every minute
- Monitor system health every 30 seconds
- Check B2C connectivity every 5 minutes
- Send metrics to Azure Application Insights
- Generate alerts for performance issues

### Monitor Configuration

Configure monitoring through environment variables:
- `AZURE_FUNCTION_BASE_URL`: Function app base URL
- `TEST_CLIENT_ID`: Test client credentials
- `TEST_CLIENT_SECRET`: Test client secret
- `APPLICATIONINSIGHTS_CONNECTION_STRING`: Application Insights connection

## Azure Monitor Dashboard

Deploy the monitoring dashboard:

1. Import `src/main/resources/MonitoringDashboard.json` into Azure Monitor
2. Configure alert rules for production monitoring
3. Set up notification channels (email, SMS, Slack)

### Dashboard Features

- **Function Execution Count**: Real-time execution metrics
- **Response Time P95**: 95th percentile response time monitoring
- **Error Rate**: Percentage of failed requests
- **Authentication Success Rate**: OAuth, JWT, and Basic Auth metrics
- **Recent Errors**: Latest exceptions and error details
- **System Health**: Overall system health monitoring
- **Component Health**: Individual component status

## Test Configuration

### Integration Test Properties

Configure tests in `src/test/resources/integration-test.properties`:

```properties
# Azure Function Configuration
azure.function.base.url=https://authserver-functions.azurewebsites.net/api
azure.function.timeout=30000

# Test Credentials
test.client.id=test-client-id
test.client.secret=test-client-secret

# Performance Thresholds
performance.test.p95.threshold=200
performance.test.cold.start.threshold=500

# Error Rate Thresholds
error.rate.threshold=0.1
availability.threshold=99.9
```

## Client Compatibility

The following client types are tested for compatibility:

1. **Legacy Java Client**: Simulates existing Java applications
2. **cURL Client**: Command-line HTTP client
3. **JavaScript/Node.js Client**: Browser and Node.js applications
4. **Python Client**: Python urllib-based clients
5. **Postman Collection**: API testing tools
6. **Basic Auth Legacy Client**: Legacy Basic Authentication clients

All clients receive responses in the exact AWS API Gateway format, ensuring zero client-side changes are required.

## Production Readiness Checklist

The production readiness checklist covers:

### Functional Requirements
- OAuth 2.0 client credentials flow
- JWT token validation with B2C JWKS
- Basic Auth fallback for legacy clients
- Password change functionality
- Error handling AWS compatibility
- Response format preservation

### Performance Requirements
- P95 response time ≤ 200ms
- Cold start time ≤ 500ms
- 99.9% availability capability
- Load testing (100 RPS sustained)
- Memory optimization (≤ 512MB)

### Security Requirements
- Azure AD B2C configuration
- JWT RS256 algorithm
- Key Vault secret management
- Managed Identity authentication
- HTTPS enforcement
- CORS configuration

### Monitoring & Observability
- Application Insights integration
- Custom metrics tracking
- Error tracking and alerting
- Performance monitoring dashboard
- Log correlation IDs
- Distributed tracing

## Troubleshooting

### Common Issues

1. **Test Failures**: Check environment variables and Azure Function accessibility
2. **Performance Issues**: Verify Azure Function scaling and cold start optimization
3. **Authentication Errors**: Validate B2C configuration and test credentials
4. **Monitoring Issues**: Check Application Insights connection string

### Debug Mode

Run tests with debug logging:
```bash
mvn test -Dtest=IntegrationTestSuite -Dlogging.level.integration=DEBUG
```

### Health Checks

Verify system health:
```bash
curl https://authserver-functions.azurewebsites.net/api/health
curl https://authserverb2c.b2clogin.com/authserverb2c.onmicrosoft.com/discovery/v2.0/keys?p=B2C_1_signupsignin
```

## Success Criteria

For production deployment, ensure:
- ✅ All integration tests pass (8/8)
- ✅ Client compatibility tests pass (6/6 client types)
- ✅ Performance requirements met (P95 ≤ 200ms)
- ✅ Production readiness validation ≥ 95% success rate
- ✅ Monitoring dashboard operational
- ✅ All documentation complete

## Next Steps

After successful validation:
1. Schedule go-live meeting with stakeholders
2. Execute blue-green production deployment
3. Monitor system for 48 hours post-deployment
4. Notify clients of successful migration
5. Schedule AWS resource cleanup

---

**Migration Status**: Step 6 Complete - Ready for Production Deployment 🚀 