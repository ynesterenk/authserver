# Step 6: Final Integration and Monitoring

## Context
This is **Step 6 of 6** in the AWS to Azure migration. You are completing the end-to-end integration, comprehensive monitoring, and final validation of the migrated authserver.

**Prerequisites**: Steps 1-5 must be completed.

**Goal**: Ensure complete system integration, comprehensive monitoring, and production readiness.

## What You're Completing
- End-to-end integration testing
- Comprehensive Azure Monitor setup
- Performance validation and optimization
- Client compatibility verification
- Production readiness checklist
- Rollback procedures

## Your Tasks

### 1. Create Integration Test Suite

Create `IntegrationTestSuite.java` in `integration/src/test/java/`:

```java
package integration;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:integration-test.properties")
@TestMethodOrder(OrderAnnotation.class)
public class IntegrationTestSuite {
    
    private static final String AZURE_FUNCTION_BASE_URL = "https://authserver-functions.azurewebsites.net/api";
    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    
    @Test
    @Order(1)
    public void testOAuthClientCredentialsFlow() {
        // Test complete OAuth 2.0 client credentials flow
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        Map<String, String> requestBody = Map.of(
            "grant_type", "client_credentials",
            "client_id", TEST_CLIENT_ID,
            "client_secret", TEST_CLIENT_SECRET
        );
        
        // Make request to Azure Function
        HttpResponse response = makeHttpRequest("POST", tokenEndpoint, requestBody);
        
        // Verify response format matches AWS API Gateway exactly
        assertEquals(200, response.getStatusCode());
        
        Map<String, Object> responseBody = parseJsonResponse(response.getBody());
        assertTrue(responseBody.containsKey("statusCode"));
        assertTrue(responseBody.containsKey("headers"));
        assertTrue(responseBody.containsKey("body"));
        assertEquals(false, responseBody.get("isBase64Encoded"));
        
        // Verify token response structure
        Map<String, Object> tokenResponse = parseJsonResponse((String) responseBody.get("body"));
        assertTrue(tokenResponse.containsKey("access_token"));
        assertTrue(tokenResponse.containsKey("token_type"));
        assertTrue(tokenResponse.containsKey("expires_in"));
        assertEquals("Bearer", tokenResponse.get("token_type"));
    }
    
    @Test
    @Order(2)
    public void testJwtTokenValidation() {
        // Get token from OAuth flow
        String accessToken = getAccessTokenFromOAuthFlow();
        
        // Test JWT validation endpoint
        String authEndpoint = AZURE_FUNCTION_BASE_URL + "/authorize/jwt";
        
        Map<String, Object> authRequest = Map.of(
            "authorizationToken", "Bearer " + accessToken,
            "methodArn", "arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request"
        );
        
        HttpResponse response = makeHttpRequest("POST", authEndpoint, authRequest);
        
        // Verify authorization response
        assertEquals(200, response.getStatusCode());
        
        Map<String, Object> authResponse = parseJsonResponse(response.getBody());
        assertTrue(authResponse.containsKey("principalId"));
        assertTrue(authResponse.containsKey("policyDocument"));
        assertTrue(authResponse.containsKey("context"));
        
        // Verify policy document structure
        String policyDocument = (String) authResponse.get("policyDocument");
        Map<String, Object> policy = parseJsonResponse(policyDocument);
        assertEquals("2012-10-17", policy.get("Version"));
        assertTrue(policy.containsKey("Statement"));
    }
    
    @Test
    @Order(3)
    public void testBasicAuthFallback() {
        // Test Basic Auth for legacy clients
        String authEndpoint = AZURE_FUNCTION_BASE_URL + "/authorize/basic";
        
        String credentials = Base64.getEncoder().encodeToString("admin:password".getBytes());
        
        Map<String, Object> authRequest = Map.of(
            "authorizationToken", "Basic " + credentials,
            "methodArn", "arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request"
        );
        
        HttpResponse response = makeHttpRequest("POST", authEndpoint, authRequest);
        
        // Verify Basic Auth response
        assertEquals(200, response.getStatusCode());
        
        Map<String, Object> authResponse = parseJsonResponse(response.getBody());
        assertEquals("admin", authResponse.get("principalId"));
        assertTrue(authResponse.containsKey("policyDocument"));
        
        // Verify context contains auth type
        Map<String, Object> context = (Map<String, Object>) authResponse.get("context");
        assertEquals("basic", context.get("authType"));
    }
    
    @Test
    @Order(4)
    public void testPasswordChangeEndpoint() {
        // Test password change functionality
        String passwordEndpoint = AZURE_FUNCTION_BASE_URL + "/change-password";
        
        Map<String, String> requestBody = Map.of(
            "username", "admin",
            "previousPassword", "oldPassword",
            "proposedPassword", "newPassword123!"
        );
        
        HttpResponse response = makeHttpRequest("POST", passwordEndpoint, requestBody);
        
        // Verify password change response
        assertEquals(200, response.getStatusCode());
        
        Map<String, Object> responseBody = parseJsonResponse(response.getBody());
        assertTrue(responseBody.containsKey("statusCode"));
        
        Map<String, Object> changeResponse = parseJsonResponse((String) responseBody.get("body"));
        assertTrue(changeResponse.containsKey("message"));
    }
    
    @Test
    @Order(5)
    public void testErrorHandlingCompatibility() {
        // Test error responses match AWS format exactly
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        Map<String, String> invalidRequest = Map.of(
            "grant_type", "invalid_grant",
            "client_id", "invalid_client"
        );
        
        HttpResponse response = makeHttpRequest("POST", tokenEndpoint, invalidRequest);
        
        // Verify error response format
        assertTrue(response.getStatusCode() >= 400);
        
        Map<String, Object> responseBody = parseJsonResponse(response.getBody());
        assertTrue(responseBody.containsKey("statusCode"));
        assertTrue(responseBody.containsKey("headers"));
        assertTrue(responseBody.containsKey("body"));
        
        Map<String, Object> errorResponse = parseJsonResponse((String) responseBody.get("body"));
        assertTrue(errorResponse.containsKey("error"));
        assertTrue(errorResponse.containsKey("error_description"));
    }
    
    @Test
    @Order(6)
    public void testPerformanceRequirements() {
        // Test performance requirements: ≤200ms p95, ≤500ms cold start
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        List<Long> responseTimes = new ArrayList<>();
        
        // Warm up the function
        makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
        
        // Measure response times
        for (int i = 0; i < 100; i++) {
            long startTime = System.currentTimeMillis();
            HttpResponse response = makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
            long endTime = System.currentTimeMillis();
            
            assertEquals(200, response.getStatusCode());
            responseTimes.add(endTime - startTime);
        }
        
        // Calculate p95
        responseTimes.sort(Long::compareTo);
        long p95 = responseTimes.get((int) (responseTimes.size() * 0.95));
        
        // Verify performance requirements
        assertTrue(p95 <= 200, "P95 response time should be ≤200ms, actual: " + p95 + "ms");
        
        // Test cold start (requires function restart)
        // This would typically be done in a separate test environment
    }
    
    private String getAccessTokenFromOAuthFlow() {
        // Helper method to get access token
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        HttpResponse response = makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
        
        Map<String, Object> responseBody = parseJsonResponse(response.getBody());
        Map<String, Object> tokenResponse = parseJsonResponse((String) responseBody.get("body"));
        
        return (String) tokenResponse.get("access_token");
    }
    
    private Map<String, String> getValidTokenRequest() {
        return Map.of(
            "grant_type", "client_credentials",
            "client_id", TEST_CLIENT_ID,
            "client_secret", TEST_CLIENT_SECRET
        );
    }
}
```

### 2. Create Comprehensive Monitoring Dashboard

Create `MonitoringDashboard.json` for Azure Monitor:

```json
{
  "dashboard": {
    "name": "AuthServer Migration Dashboard",
    "description": "Comprehensive monitoring for AWS to Azure migration",
    "widgets": [
      {
        "type": "metric",
        "title": "Function Execution Count",
        "query": "requests | where cloud_RoleName contains 'authserver' | summarize count() by bin(timestamp, 5m), operation_Name",
        "visualization": "timechart"
      },
      {
        "type": "metric", 
        "title": "Response Time P95",
        "query": "requests | where cloud_RoleName contains 'authserver' | summarize percentile(duration, 95) by bin(timestamp, 5m)",
        "visualization": "timechart",
        "threshold": 200
      },
      {
        "type": "metric",
        "title": "Error Rate",
        "query": "requests | where cloud_RoleName contains 'authserver' | summarize errorRate = (countif(success == false) * 100.0) / count() by bin(timestamp, 5m)",
        "visualization": "timechart",
        "threshold": 2.0
      },
      {
        "type": "metric",
        "title": "Authentication Success Rate",
        "query": "customMetrics | where name == 'TokenRequests' or name == 'BasicAuthValidations' | summarize sum(value) by bin(timestamp, 5m), name",
        "visualization": "timechart"
      },
      {
        "type": "log",
        "title": "Recent Errors",
        "query": "exceptions | where cloud_RoleName contains 'authserver' | order by timestamp desc | take 50",
        "visualization": "table"
      },
      {
        "type": "metric",
        "title": "B2C Token Validation",
        "query": "customMetrics | where name == 'JwtValidations' | summarize sum(value) by bin(timestamp, 5m)",
        "visualization": "timechart"
      }
    ],
    "alerts": [
      {
        "name": "High Error Rate",
        "condition": "errorRate > 2%",
        "severity": "Error",
        "action": "email"
      },
      {
        "name": "High Response Time",
        "condition": "p95ResponseTime > 200ms",
        "severity": "Warning", 
        "action": "email"
      },
      {
        "name": "Function Failures",
        "condition": "functionFailures > 5 in 5 minutes",
        "severity": "Critical",
        "action": "email"
      }
    ]
  }
}
```

### 3. Create Performance Monitoring Script

Create `PerformanceMonitor.java` in `integration/src/main/java/`:

```java
package integration.monitoring;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PerformanceMonitor {
    private final TelemetryClient telemetryClient;
    private final ScheduledExecutorService scheduler;
    
    public PerformanceMonitor() {
        this.telemetryClient = new TelemetryClient();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    public void startMonitoring() {
        // Monitor response times every minute
        scheduler.scheduleAtFixedRate(this::monitorResponseTimes, 0, 1, TimeUnit.MINUTES);
        
        // Monitor system health every 30 seconds
        scheduler.scheduleAtFixedRate(this::monitorSystemHealth, 0, 30, TimeUnit.SECONDS);
        
        // Monitor B2C connectivity every 5 minutes
        scheduler.scheduleAtFixedRate(this::monitorB2CConnectivity, 0, 5, TimeUnit.MINUTES);
    }
    
    private void monitorResponseTimes() {
        try {
            // Test OAuth endpoint response time
            long startTime = System.currentTimeMillis();
            HttpResponse response = testOAuthEndpoint();
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Track metric
            MetricTelemetry metric = new MetricTelemetry();
            metric.setName("OAuth.ResponseTime");
            metric.setValue(responseTime);
            metric.getProperties().put("endpoint", "oauth/token");
            metric.getProperties().put("success", String.valueOf(response.getStatusCode() == 200));
            
            telemetryClient.trackMetric(metric);
            
            // Alert if response time exceeds threshold
            if (responseTime > 200) {
                telemetryClient.trackEvent("PerformanceAlert", 
                    Map.of("type", "HighResponseTime", "value", String.valueOf(responseTime)));
            }
            
        } catch (Exception e) {
            telemetryClient.trackException(e);
        }
    }
    
    private void monitorSystemHealth() {
        try {
            // Check function app health
            boolean functionsHealthy = checkFunctionAppHealth();
            
            // Check B2C connectivity
            boolean b2cHealthy = checkB2CHealth();
            
            // Check Key Vault connectivity
            boolean keyVaultHealthy = checkKeyVaultHealth();
            
            // Track overall health
            MetricTelemetry healthMetric = new MetricTelemetry();
            healthMetric.setName("System.Health");
            healthMetric.setValue(functionsHealthy && b2cHealthy && keyVaultHealthy ? 1 : 0);
            healthMetric.getProperties().put("functions", String.valueOf(functionsHealthy));
            healthMetric.getProperties().put("b2c", String.valueOf(b2cHealthy));
            healthMetric.getProperties().put("keyVault", String.valueOf(keyVaultHealthy));
            
            telemetryClient.trackMetric(healthMetric);
            
        } catch (Exception e) {
            telemetryClient.trackException(e);
        }
    }
    
    private void monitorB2CConnectivity() {
        try {
            // Test B2C JWKS endpoint
            long startTime = System.currentTimeMillis();
            boolean jwksAccessible = testB2CJwksEndpoint();
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Track B2C connectivity
            MetricTelemetry metric = new MetricTelemetry();
            metric.setName("B2C.Connectivity");
            metric.setValue(jwksAccessible ? 1 : 0);
            metric.getProperties().put("responseTime", String.valueOf(responseTime));
            
            telemetryClient.trackMetric(metric);
            
            if (!jwksAccessible) {
                telemetryClient.trackEvent("B2CConnectivityAlert", 
                    Map.of("type", "JWKSEndpointUnavailable"));
            }
            
        } catch (Exception e) {
            telemetryClient.trackException(e);
        }
    }
    
    private boolean testB2CJwksEndpoint() {
        try {
            String jwksUrl = AzureEnvironmentConfig.getB2CJwksUrl();
            HttpResponse response = makeHttpRequest("GET", jwksUrl, null);
            return response.getStatusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean checkFunctionAppHealth() {
        // Implementation to check Function App health
        return true; // Placeholder
    }
    
    private boolean checkB2CHealth() {
        // Implementation to check B2C health
        return true; // Placeholder
    }
    
    private boolean checkKeyVaultHealth() {
        // Implementation to check Key Vault health
        return true; // Placeholder
    }
}
```

### 4. Create Client Compatibility Test

Create `ClientCompatibilityTest.java` in `integration/src/test/java/`:

```java
package integration.compatibility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ClientCompatibilityTest {
    
    @Test
    public void testLegacyJavaClient() {
        // Test with legacy Java client that expects AWS API Gateway format
        LegacyJavaClient client = new LegacyJavaClient(AZURE_FUNCTION_BASE_URL);
        
        TokenResponse response = client.getAccessToken("client_credentials", TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getExpiresIn() > 0);
    }
    
    @Test
    public void testCurlClient() {
        // Test with curl-like HTTP client
        String curlCommand = String.format(
            "curl -X POST %s/oauth/token -H 'Content-Type: application/json' -d '{\"grant_type\":\"client_credentials\",\"client_id\":\"%s\",\"client_secret\":\"%s\"}'",
            AZURE_FUNCTION_BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET
        );
        
        ProcessResult result = executeCommand(curlCommand);
        
        assertEquals(0, result.getExitCode());
        
        Map<String, Object> response = parseJsonResponse(result.getOutput());
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.containsKey("body"));
    }
    
    @Test
    public void testPostmanCollection() {
        // Test with Postman collection format
        PostmanCollection collection = loadPostmanCollection("authserver-tests.json");
        
        for (PostmanRequest request : collection.getRequests()) {
            PostmanResponse response = executePostmanRequest(request);
            
            assertTrue(response.isSuccessful(), 
                "Request failed: " + request.getName() + " - " + response.getErrorMessage());
            
            // Verify response format matches expected AWS format
            verifyAwsApiGatewayFormat(response);
        }
    }
    
    @Test
    public void testJavaScriptClient() {
        // Test with JavaScript/Node.js client
        String jsScript = """
            const axios = require('axios');
            
            async function testOAuth() {
                const response = await axios.post('%s/oauth/token', {
                    grant_type: 'client_credentials',
                    client_id: '%s',
                    client_secret: '%s'
                });
                
                console.log(JSON.stringify(response.data));
                return response.data;
            }
            
            testOAuth();
            """.formatted(AZURE_FUNCTION_BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        ProcessResult result = executeNodeScript(jsScript);
        
        assertEquals(0, result.getExitCode());
        
        Map<String, Object> response = parseJsonResponse(result.getOutput());
        assertTrue(response.containsKey("statusCode"));
        assertTrue(response.containsKey("body"));
    }
    
    private void verifyAwsApiGatewayFormat(PostmanResponse response) {
        Map<String, Object> responseData = response.getJsonData();
        
        // Verify AWS API Gateway response structure
        assertTrue(responseData.containsKey("statusCode"));
        assertTrue(responseData.containsKey("headers"));
        assertTrue(responseData.containsKey("body"));
        assertTrue(responseData.containsKey("isBase64Encoded"));
        
        assertEquals(false, responseData.get("isBase64Encoded"));
        
        // Verify headers structure
        Map<String, Object> headers = (Map<String, Object>) responseData.get("headers");
        assertTrue(headers.containsKey("Content-Type"));
        assertTrue(headers.containsKey("Access-Control-Allow-Origin"));
    }
}
```

### 5. Create Production Readiness Checklist

Create `ProductionReadinessChecklist.md`:

```markdown
# Production Readiness Checklist

## ✅ Functional Requirements
- [ ] OAuth 2.0 client credentials flow working
- [ ] JWT token validation with B2C JWKS
- [ ] Basic Auth fallback for legacy clients
- [ ] Password change functionality
- [ ] Error handling maintains AWS compatibility
- [ ] Response format exactly matches AWS API Gateway

## ✅ Performance Requirements
- [ ] P95 response time ≤ 200ms (warm requests)
- [ ] Cold start time ≤ 500ms
- [ ] 99.9% availability SLA capability
- [ ] Load testing completed (100 RPS sustained)
- [ ] Memory usage optimized (≤ 512MB)

## ✅ Security Requirements
- [ ] Azure AD B2C properly configured
- [ ] JWT tokens use RS256 algorithm
- [ ] Key Vault secrets properly secured
- [ ] Managed Identity authentication working
- [ ] HTTPS enforced for all endpoints
- [ ] CORS properly configured

## ✅ Monitoring & Observability
- [ ] Application Insights configured
- [ ] Custom metrics tracking
- [ ] Error tracking and alerting
- [ ] Performance monitoring dashboard
- [ ] Log correlation IDs implemented
- [ ] Distributed tracing enabled

## ✅ Testing & Quality
- [ ] Unit tests passing (>90% coverage)
- [ ] Integration tests passing
- [ ] Client compatibility tests passing
- [ ] Performance tests passing
- [ ] Security tests passing
- [ ] Load tests completed

## ✅ Deployment & Operations
- [ ] Terraform infrastructure validated
- [ ] CI/CD pipeline configured
- [ ] Blue-green deployment strategy
- [ ] Rollback procedures documented
- [ ] Disaster recovery plan
- [ ] Multi-region setup (West/North Europe)

## ✅ Documentation
- [ ] API documentation updated
- [ ] Migration guide completed
- [ ] Troubleshooting guide
- [ ] Runbook for operations
- [ ] Client migration guide
```

## Success Criteria
- ✅ All integration tests pass
- ✅ Performance requirements met (≤200ms p95, ≤500ms cold start)
- ✅ Client compatibility verified with multiple client types
- ✅ Comprehensive monitoring and alerting operational
- ✅ Production readiness checklist 100% complete
- ✅ Error rate < 0.1% in production testing
- ✅ 99.9% availability demonstrated over 7 days
- ✅ Rollback procedures tested and documented

## Testing Commands
```bash
# Run complete integration test suite
cd integration
mvn clean test -Dtest=IntegrationTestSuite

# Run client compatibility tests
mvn test -Dtest=ClientCompatibilityTest

# Run performance tests
mvn test -Dtest=PerformanceTest

# Start performance monitoring
java -jar performance-monitor.jar

# Validate production readiness
./scripts/validate-production-readiness.sh
```

## Final Validation
After completing this step:
1. ✅ Complete AWS to Azure migration achieved
2. ✅ All functions migrated and operational
3. ✅ Performance and reliability targets met
4. ✅ Client compatibility maintained
5. ✅ Production monitoring operational
6. ✅ Ready for production cutover

## Rollback Procedures
If issues are discovered:
1. **Immediate**: Route traffic back to AWS via DNS/load balancer
2. **Function Level**: Disable Azure Functions, re-enable AWS Lambda
3. **Authentication**: Switch back to Cognito User Pool
4. **Monitoring**: Continue monitoring both environments during rollback
5. **Data Consistency**: Ensure no data loss during rollback process

## Post-Migration Tasks
1. Monitor system for 48 hours post-cutover
2. Validate all client applications working correctly
3. Confirm performance metrics meet SLA requirements
4. Update documentation with final configuration
5. Schedule AWS resource cleanup (Phase 8)