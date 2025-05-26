package integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:integration-test.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTestSuite {
    
    private static final String AZURE_FUNCTION_BASE_URL = System.getProperty("azure.function.base.url", 
        "https://authserver-functions.azurewebsites.net/api");
    private static final String TEST_CLIENT_ID = System.getProperty("test.client.id", "test-client-id");
    private static final String TEST_CLIENT_SECRET = System.getProperty("test.client.secret", "test-client-secret");
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    @Test
    @Order(1)
    @DisplayName("Test OAuth 2.0 Client Credentials Flow")
    public void testOAuthClientCredentialsFlow() throws IOException {
        // Test complete OAuth 2.0 client credentials flow
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        Map<String, String> requestBody = Map.of(
            "grant_type", "client_credentials",
            "client_id", TEST_CLIENT_ID,
            "client_secret", TEST_CLIENT_SECRET
        );
        
        // Make request to Azure Function
        Response response = makeHttpRequest("POST", tokenEndpoint, requestBody);
        
        // Verify response format matches AWS API Gateway exactly
        assertEquals(200, response.code(), "OAuth token request should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> responseBody = parseJsonResponse(responseBodyString);
        
        // Verify AWS API Gateway response structure
        assertTrue(responseBody.containsKey("statusCode"), "Response should contain statusCode");
        assertTrue(responseBody.containsKey("headers"), "Response should contain headers");
        assertTrue(responseBody.containsKey("body"), "Response should contain body");
        assertTrue(responseBody.containsKey("isBase64Encoded"), "Response should contain isBase64Encoded");
        assertEquals(false, responseBody.get("isBase64Encoded"), "isBase64Encoded should be false");
        
        // Verify token response structure
        String bodyString = (String) responseBody.get("body");
        Map<String, Object> tokenResponse = parseJsonResponse(bodyString);
        assertTrue(tokenResponse.containsKey("access_token"), "Token response should contain access_token");
        assertTrue(tokenResponse.containsKey("token_type"), "Token response should contain token_type");
        assertTrue(tokenResponse.containsKey("expires_in"), "Token response should contain expires_in");
        assertEquals("Bearer", tokenResponse.get("token_type"), "Token type should be Bearer");
        
        // Verify headers
        Map<String, Object> headers = (Map<String, Object>) responseBody.get("headers");
        assertTrue(headers.containsKey("Content-Type"), "Headers should contain Content-Type");
        assertTrue(headers.containsKey("Access-Control-Allow-Origin"), "Headers should contain CORS header");
        
        response.close();
    }
    
    @Test
    @Order(2)
    @DisplayName("Test JWT Token Validation")
    public void testJwtTokenValidation() throws IOException {
        // Get token from OAuth flow
        String accessToken = getAccessTokenFromOAuthFlow();
        assertNotNull(accessToken, "Access token should not be null");
        
        // Test JWT validation endpoint
        String authEndpoint = AZURE_FUNCTION_BASE_URL + "/authorize/jwt";
        
        Map<String, Object> authRequest = Map.of(
            "authorizationToken", "Bearer " + accessToken,
            "methodArn", "arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request"
        );
        
        Response response = makeHttpRequest("POST", authEndpoint, authRequest);
        
        // Verify authorization response
        assertEquals(200, response.code(), "JWT authorization should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> authResponse = parseJsonResponse(responseBodyString);
        
        assertTrue(authResponse.containsKey("principalId"), "Auth response should contain principalId");
        assertTrue(authResponse.containsKey("policyDocument"), "Auth response should contain policyDocument");
        assertTrue(authResponse.containsKey("context"), "Auth response should contain context");
        
        // Verify policy document structure
        String policyDocument = (String) authResponse.get("policyDocument");
        Map<String, Object> policy = parseJsonResponse(policyDocument);
        assertEquals("2012-10-17", policy.get("Version"), "Policy version should be 2012-10-17");
        assertTrue(policy.containsKey("Statement"), "Policy should contain Statement");
        
        // Verify context contains expected fields
        Map<String, Object> context = (Map<String, Object>) authResponse.get("context");
        assertTrue(context.containsKey("authType"), "Context should contain authType");
        assertEquals("jwt", context.get("authType"), "Auth type should be jwt");
        
        response.close();
    }
    
    @Test
    @Order(3)
    @DisplayName("Test Basic Auth Fallback")
    public void testBasicAuthFallback() throws IOException {
        // Test Basic Auth for legacy clients
        String authEndpoint = AZURE_FUNCTION_BASE_URL + "/authorize/basic";
        
        String credentials = Base64.getEncoder().encodeToString("admin:password".getBytes());
        
        Map<String, Object> authRequest = Map.of(
            "authorizationToken", "Basic " + credentials,
            "methodArn", "arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request"
        );
        
        Response response = makeHttpRequest("POST", authEndpoint, authRequest);
        
        // Verify Basic Auth response
        assertEquals(200, response.code(), "Basic Auth should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> authResponse = parseJsonResponse(responseBodyString);
        
        assertEquals("admin", authResponse.get("principalId"), "Principal ID should be admin");
        assertTrue(authResponse.containsKey("policyDocument"), "Response should contain policyDocument");
        
        // Verify context contains auth type
        Map<String, Object> context = (Map<String, Object>) authResponse.get("context");
        assertEquals("basic", context.get("authType"), "Auth type should be basic");
        
        response.close();
    }
    
    @Test
    @Order(4)
    @DisplayName("Test Password Change Endpoint")
    public void testPasswordChangeEndpoint() throws IOException {
        // Test password change functionality
        String passwordEndpoint = AZURE_FUNCTION_BASE_URL + "/change-password";
        
        Map<String, String> requestBody = Map.of(
            "username", "admin",
            "previousPassword", "oldPassword",
            "proposedPassword", "newPassword123!"
        );
        
        Response response = makeHttpRequest("POST", passwordEndpoint, requestBody);
        
        // Verify password change response
        assertEquals(200, response.code(), "Password change request should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> responseBody = parseJsonResponse(responseBodyString);
        
        assertTrue(responseBody.containsKey("statusCode"), "Response should contain statusCode");
        assertTrue(responseBody.containsKey("body"), "Response should contain body");
        
        String bodyString = (String) responseBody.get("body");
        Map<String, Object> changeResponse = parseJsonResponse(bodyString);
        assertTrue(changeResponse.containsKey("message"), "Change response should contain message");
        
        response.close();
    }
    
    @Test
    @Order(5)
    @DisplayName("Test Error Handling Compatibility")
    public void testErrorHandlingCompatibility() throws IOException {
        // Test error responses match AWS format exactly
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        Map<String, String> invalidRequest = Map.of(
            "grant_type", "invalid_grant",
            "client_id", "invalid_client"
        );
        
        Response response = makeHttpRequest("POST", tokenEndpoint, invalidRequest);
        
        // Verify error response format
        assertTrue(response.code() >= 400, "Should return error status code");
        
        String responseBodyString = response.body().string();
        Map<String, Object> responseBody = parseJsonResponse(responseBodyString);
        
        assertTrue(responseBody.containsKey("statusCode"), "Error response should contain statusCode");
        assertTrue(responseBody.containsKey("headers"), "Error response should contain headers");
        assertTrue(responseBody.containsKey("body"), "Error response should contain body");
        
        String bodyString = (String) responseBody.get("body");
        Map<String, Object> errorResponse = parseJsonResponse(bodyString);
        assertTrue(errorResponse.containsKey("error"), "Error response should contain error");
        assertTrue(errorResponse.containsKey("error_description"), "Error response should contain error_description");
        
        response.close();
    }
    
    @Test
    @Order(6)
    @DisplayName("Test Performance Requirements")
    public void testPerformanceRequirements() throws IOException {
        // Test performance requirements: ≤200ms p95, ≤500ms cold start
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        List<Long> responseTimes = new ArrayList<>();
        
        // Warm up the function
        Response warmupResponse = makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
        warmupResponse.close();
        
        // Measure response times
        for (int i = 0; i < 100; i++) {
            long startTime = System.currentTimeMillis();
            Response response = makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
            long endTime = System.currentTimeMillis();
            
            assertEquals(200, response.code(), "Performance test request should succeed");
            responseTimes.add(endTime - startTime);
            response.close();
        }
        
        // Calculate p95
        responseTimes.sort(Long::compareTo);
        long p95 = responseTimes.get((int) (responseTimes.size() * 0.95));
        
        // Verify performance requirements
        assertTrue(p95 <= 200, "P95 response time should be ≤200ms, actual: " + p95 + "ms");
        
        // Calculate average response time
        double average = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        System.out.println("Average response time: " + average + "ms");
        System.out.println("P95 response time: " + p95 + "ms");
    }
    
    @Test
    @Order(7)
    @DisplayName("Test CORS Headers")
    public void testCorsHeaders() throws IOException {
        // Test CORS headers are properly set
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        
        Response response = makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
        
        assertEquals(200, response.code(), "CORS test request should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> responseBody = parseJsonResponse(responseBodyString);
        
        Map<String, Object> headers = (Map<String, Object>) responseBody.get("headers");
        assertTrue(headers.containsKey("Access-Control-Allow-Origin"), "Should contain CORS origin header");
        assertTrue(headers.containsKey("Access-Control-Allow-Methods"), "Should contain CORS methods header");
        assertTrue(headers.containsKey("Access-Control-Allow-Headers"), "Should contain CORS headers header");
        
        response.close();
    }
    
    @Test
    @Order(8)
    @DisplayName("Test Health Check Endpoint")
    public void testHealthCheckEndpoint() throws IOException {
        // Test health check endpoint
        String healthEndpoint = AZURE_FUNCTION_BASE_URL + "/health";
        
        Response response = makeHttpRequest("GET", healthEndpoint, null);
        
        assertEquals(200, response.code(), "Health check should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> healthResponse = parseJsonResponse(responseBodyString);
        
        assertTrue(healthResponse.containsKey("status"), "Health response should contain status");
        assertEquals("healthy", healthResponse.get("status"), "Status should be healthy");
        
        response.close();
    }
    
    // Helper methods
    
    private String getAccessTokenFromOAuthFlow() throws IOException {
        String tokenEndpoint = AZURE_FUNCTION_BASE_URL + "/oauth/token";
        Response response = makeHttpRequest("POST", tokenEndpoint, getValidTokenRequest());
        
        String responseBodyString = response.body().string();
        Map<String, Object> responseBody = parseJsonResponse(responseBodyString);
        String bodyString = (String) responseBody.get("body");
        Map<String, Object> tokenResponse = parseJsonResponse(bodyString);
        
        response.close();
        return (String) tokenResponse.get("access_token");
    }
    
    private Map<String, String> getValidTokenRequest() {
        return Map.of(
            "grant_type", "client_credentials",
            "client_id", TEST_CLIENT_ID,
            "client_secret", TEST_CLIENT_SECRET
        );
    }
    
    private Response makeHttpRequest(String method, String url, Object requestBody) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        
        if (requestBody != null) {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            switch (method.toUpperCase()) {
                case "POST":
                    requestBuilder.post(body);
                    break;
                case "PUT":
                    requestBuilder.put(body);
                    break;
                case "PATCH":
                    requestBuilder.patch(body);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported method with body: " + method);
            }
        } else {
            switch (method.toUpperCase()) {
                case "GET":
                    requestBuilder.get();
                    break;
                case "DELETE":
                    requestBuilder.delete();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported method: " + method);
            }
        }
        
        return httpClient.newCall(requestBuilder.build()).execute();
    }
    
    private Map<String, Object> parseJsonResponse(String json) throws IOException {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
} 