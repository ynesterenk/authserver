package integration.compatibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:integration-test.properties")
public class ClientCompatibilityTest {
    
    private static final String AZURE_FUNCTION_BASE_URL = System.getProperty("azure.function.base.url", 
        "https://authserver-functions.azurewebsites.net/api");
    private static final String TEST_CLIENT_ID = System.getProperty("test.client.id", "test-client-id");
    private static final String TEST_CLIENT_SECRET = System.getProperty("test.client.secret", "test-client-secret");
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    @BeforeEach
    public void setUp() {
        // Verify test environment is ready
        assertNotNull(AZURE_FUNCTION_BASE_URL, "Azure Function base URL must be configured");
        assertNotNull(TEST_CLIENT_ID, "Test client ID must be configured");
        assertNotNull(TEST_CLIENT_SECRET, "Test client secret must be configured");
    }
    
    @Test
    @DisplayName("Test Legacy Java Client Compatibility")
    public void testLegacyJavaClient() throws IOException {
        // Test with legacy Java client that expects AWS API Gateway format
        LegacyJavaClient client = new LegacyJavaClient(AZURE_FUNCTION_BASE_URL);
        
        TokenResponse response = client.getAccessToken("client_credentials", TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        assertNotNull(response, "Token response should not be null");
        assertNotNull(response.getAccessToken(), "Access token should not be null");
        assertEquals("Bearer", response.getTokenType(), "Token type should be Bearer");
        assertTrue(response.getExpiresIn() > 0, "Expires in should be positive");
        
        // Verify token format (should be JWT)
        String[] tokenParts = response.getAccessToken().split("\\.");
        assertEquals(3, tokenParts.length, "JWT should have 3 parts");
    }
    
    @Test
    @DisplayName("Test cURL Client Compatibility")
    public void testCurlClient() throws Exception {
        // Test with curl-like HTTP client
        String curlCommand = String.format(
            "curl -X POST %s/oauth/token -H \"Content-Type: application/json\" -d \"{\\\"grant_type\\\":\\\"client_credentials\\\",\\\"client_id\\\":\\\"%s\\\",\\\"client_secret\\\":\\\"%s\\\"}\"",
            AZURE_FUNCTION_BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET
        );
        
        ProcessResult result = executeCommand(curlCommand);
        
        assertEquals(0, result.getExitCode(), "cURL command should succeed");
        
        Map<String, Object> response = parseJsonResponse(result.getOutput());
        assertEquals(200, ((Number) response.get("statusCode")).intValue(), "Status code should be 200");
        assertTrue(response.containsKey("body"), "Response should contain body");
        
        // Verify body contains token response
        String bodyString = (String) response.get("body");
        Map<String, Object> tokenResponse = parseJsonResponse(bodyString);
        assertTrue(tokenResponse.containsKey("access_token"), "Token response should contain access_token");
        assertTrue(tokenResponse.containsKey("token_type"), "Token response should contain token_type");
    }
    
    @Test
    @DisplayName("Test Postman Collection Compatibility")
    public void testPostmanCollection() throws IOException {
        // Test with Postman collection format
        PostmanCollection collection = loadPostmanCollection();
        
        for (PostmanRequest request : collection.getRequests()) {
            PostmanResponse response = executePostmanRequest(request);
            
            assertTrue(response.isSuccessful(), 
                "Request failed: " + request.getName() + " - " + response.getErrorMessage());
            
            // Verify response format matches expected AWS format
            verifyAwsApiGatewayFormat(response);
        }
    }
    
    @Test
    @DisplayName("Test JavaScript/Node.js Client Compatibility")
    public void testJavaScriptClient() throws Exception {
        // Create a simple Node.js script for testing
        String jsScript = String.format("""
            const https = require('https');
            
            const data = JSON.stringify({
                grant_type: 'client_credentials',
                client_id: '%s',
                client_secret: '%s'
            });
            
            const url = new URL('%s/oauth/token');
            const options = {
                hostname: url.hostname,
                port: url.port || 443,
                path: url.pathname,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': data.length
                }
            };
            
            const req = https.request(options, (res) => {
                let responseData = '';
                res.on('data', (chunk) => {
                    responseData += chunk;
                });
                res.on('end', () => {
                    console.log(responseData);
                });
            });
            
            req.on('error', (e) => {
                console.error('Error:', e.message);
                process.exit(1);
            });
            
            req.write(data);
            req.end();
            """, TEST_CLIENT_ID, TEST_CLIENT_SECRET, AZURE_FUNCTION_BASE_URL);
        
        ProcessResult result = executeNodeScript(jsScript);
        
        assertEquals(0, result.getExitCode(), "Node.js script should succeed");
        
        Map<String, Object> response = parseJsonResponse(result.getOutput());
        assertTrue(response.containsKey("statusCode"), "Response should contain statusCode");
        assertTrue(response.containsKey("body"), "Response should contain body");
    }
    
    @Test
    @DisplayName("Test Python Client Compatibility")
    public void testPythonClient() throws Exception {
        // Test with Python requests library
        String pythonScript = String.format("""
            import json
            import urllib.request
            import urllib.parse
            
            data = {
                'grant_type': 'client_credentials',
                'client_id': '%s',
                'client_secret': '%s'
            }
            
            json_data = json.dumps(data).encode('utf-8')
            
            req = urllib.request.Request(
                '%s/oauth/token',
                data=json_data,
                headers={'Content-Type': 'application/json'}
            )
            
            try:
                with urllib.request.urlopen(req) as response:
                    result = response.read().decode('utf-8')
                    print(result)
            except Exception as e:
                print(f'Error: {e}')
                exit(1)
            """, TEST_CLIENT_ID, TEST_CLIENT_SECRET, AZURE_FUNCTION_BASE_URL);
        
        ProcessResult result = executePythonScript(pythonScript);
        
        assertEquals(0, result.getExitCode(), "Python script should succeed");
        
        Map<String, Object> response = parseJsonResponse(result.getOutput());
        assertTrue(response.containsKey("statusCode"), "Response should contain statusCode");
        assertTrue(response.containsKey("body"), "Response should contain body");
    }
    
    @Test
    @DisplayName("Test Basic Auth Legacy Client")
    public void testBasicAuthLegacyClient() throws IOException {
        // Test Basic Auth with legacy client format
        String credentials = Base64.getEncoder().encodeToString("admin:password".getBytes());
        String authHeader = "Basic " + credentials;
        
        Map<String, Object> authRequest = Map.of(
            "authorizationToken", authHeader,
            "methodArn", "arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request"
        );
        
        String jsonBody = objectMapper.writeValueAsString(authRequest);
        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        Request request = new Request.Builder()
            .url(AZURE_FUNCTION_BASE_URL + "/authorize/basic")
            .post(body)
            .build();
        
        Response response = httpClient.newCall(request).execute();
        
        assertEquals(200, response.code(), "Basic Auth should succeed");
        
        String responseBodyString = response.body().string();
        Map<String, Object> authResponse = parseJsonResponse(responseBodyString);
        
        assertEquals("admin", authResponse.get("principalId"), "Principal ID should be admin");
        assertTrue(authResponse.containsKey("policyDocument"), "Response should contain policyDocument");
        
        response.close();
    }
    
    // Helper classes and methods
    
    /**
     * Legacy Java Client simulation
     */
    private static class LegacyJavaClient {
        private final String baseUrl;
        
        public LegacyJavaClient(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        public TokenResponse getAccessToken(String grantType, String clientId, String clientSecret) throws IOException {
            Map<String, String> requestBody = Map.of(
                "grant_type", grantType,
                "client_id", clientId,
                "client_secret", clientSecret
            );
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            Request request = new Request.Builder()
                .url(baseUrl + "/oauth/token")
                .post(body)
                .build();
            
            Response response = httpClient.newCall(request).execute();
            String responseBodyString = response.body().string();
            
            // Parse AWS API Gateway format
            Map<String, Object> apiGatewayResponse = parseJsonResponse(responseBodyString);
            String bodyString = (String) apiGatewayResponse.get("body");
            Map<String, Object> tokenData = parseJsonResponse(bodyString);
            
            response.close();
            
            return new TokenResponse(
                (String) tokenData.get("access_token"),
                (String) tokenData.get("token_type"),
                ((Number) tokenData.get("expires_in")).intValue()
            );
        }
    }
    
    /**
     * Token Response class
     */
    private static class TokenResponse {
        private final String accessToken;
        private final String tokenType;
        private final int expiresIn;
        
        public TokenResponse(String accessToken, String tokenType, int expiresIn) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
        }
        
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public int getExpiresIn() { return expiresIn; }
    }
    
    /**
     * Postman Collection classes
     */
    private static class PostmanCollection {
        private final PostmanRequest[] requests;
        
        public PostmanCollection(PostmanRequest[] requests) {
            this.requests = requests;
        }
        
        public PostmanRequest[] getRequests() { return requests; }
    }
    
    private static class PostmanRequest {
        private final String name;
        private final String method;
        private final String url;
        private final Map<String, Object> body;
        
        public PostmanRequest(String name, String method, String url, Map<String, Object> body) {
            this.name = name;
            this.method = method;
            this.url = url;
            this.body = body;
        }
        
        public String getName() { return name; }
        public String getMethod() { return method; }
        public String getUrl() { return url; }
        public Map<String, Object> getBody() { return body; }
    }
    
    private static class PostmanResponse {
        private final boolean successful;
        private final String errorMessage;
        private final Map<String, Object> jsonData;
        
        public PostmanResponse(boolean successful, String errorMessage, Map<String, Object> jsonData) {
            this.successful = successful;
            this.errorMessage = errorMessage;
            this.jsonData = jsonData;
        }
        
        public boolean isSuccessful() { return successful; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, Object> getJsonData() { return jsonData; }
    }
    
    /**
     * Process Result class
     */
    private static class ProcessResult {
        private final int exitCode;
        private final String output;
        
        public ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
        
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
    }
    
    // Helper methods
    
    private PostmanCollection loadPostmanCollection() {
        // Create a simple test collection
        PostmanRequest[] requests = {
            new PostmanRequest(
                "OAuth Token Request",
                "POST",
                AZURE_FUNCTION_BASE_URL + "/oauth/token",
                Map.of(
                    "grant_type", "client_credentials",
                    "client_id", TEST_CLIENT_ID,
                    "client_secret", TEST_CLIENT_SECRET
                )
            )
        };
        
        return new PostmanCollection(requests);
    }
    
    private PostmanResponse executePostmanRequest(PostmanRequest request) throws IOException {
        try {
            String jsonBody = objectMapper.writeValueAsString(request.getBody());
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            Request httpRequest = new Request.Builder()
                .url(request.getUrl())
                .post(body)
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            String responseBodyString = response.body().string();
            
            Map<String, Object> jsonData = parseJsonResponse(responseBodyString);
            boolean successful = response.code() >= 200 && response.code() < 300;
            
            response.close();
            
            return new PostmanResponse(successful, null, jsonData);
        } catch (Exception e) {
            return new PostmanResponse(false, e.getMessage(), null);
        }
    }
    
    private void verifyAwsApiGatewayFormat(PostmanResponse response) {
        Map<String, Object> responseData = response.getJsonData();
        
        // Verify AWS API Gateway response structure
        assertTrue(responseData.containsKey("statusCode"), "Response should contain statusCode");
        assertTrue(responseData.containsKey("headers"), "Response should contain headers");
        assertTrue(responseData.containsKey("body"), "Response should contain body");
        assertTrue(responseData.containsKey("isBase64Encoded"), "Response should contain isBase64Encoded");
        
        assertEquals(false, responseData.get("isBase64Encoded"), "isBase64Encoded should be false");
        
        // Verify headers structure
        Map<String, Object> headers = (Map<String, Object>) responseData.get("headers");
        assertTrue(headers.containsKey("Content-Type"), "Headers should contain Content-Type");
        assertTrue(headers.containsKey("Access-Control-Allow-Origin"), "Headers should contain CORS header");
    }
    
    private ProcessResult executeCommand(String command) throws Exception {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = new DefaultExecutor();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        
        try {
            int exitCode = executor.execute(cmdLine);
            return new ProcessResult(exitCode, outputStream.toString());
        } catch (Exception e) {
            return new ProcessResult(1, e.getMessage());
        }
    }
    
    private ProcessResult executeNodeScript(String script) throws Exception {
        // Write script to temporary file
        Path tempFile = Files.createTempFile("test-script", ".js");
        Files.write(tempFile, script.getBytes());
        
        try {
            String command = "node " + tempFile.toString();
            return executeCommand(command);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private ProcessResult executePythonScript(String script) throws Exception {
        // Write script to temporary file
        Path tempFile = Files.createTempFile("test-script", ".py");
        Files.write(tempFile, script.getBytes());
        
        try {
            String command = "python " + tempFile.toString();
            return executeCommand(command);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static Map<String, Object> parseJsonResponse(String json) throws IOException {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
} 