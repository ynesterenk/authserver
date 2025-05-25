package basic.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import shared.infrastructure.azure.adapters.RequestResponseAdapter;
import shared.infrastructure.azure.adapters.ResponseFormatAdapter;
import shared.infrastructure.azure.monitoring.ApplicationInsightsAdapter;
import shared.infrastructure.azure.auth.B2CUserPoolAdapter;
import shared.infrastructure.azure.auth.AzureRBACPolicyRepository;
import shared.infrastructure.azure.auth.AzureB2CJwtValidator;
import shared.infrastructure.azure.auth.AzureRBACPolicy;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import shared.infrastructure.aws.gateway.AuthorizerRequest;
import shared.infrastructure.aws.gateway.AuthorizerResponse;
import basic.core.Principal;
import lombok.extern.java.Log;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Azure Functions Basic Authenticator that validates Basic Auth credentials
 * with Azure AD B2C and generates Azure RBAC policies while maintaining AWS compatibility.
 */
@Log
public class BasicAuthenticatorFunction {
    
    private final B2CUserPoolAdapter userPool;
    private final AzureRBACPolicyRepository policyRepository;
    private final AzureB2CJwtValidator jwtValidator;
    private final ApplicationInsightsAdapter insights;
    private final ObjectMapper objectMapper;
    private final AzureEnvironmentConfig config;
    
    public BasicAuthenticatorFunction() {
        this.config = new AzureEnvironmentConfig();
        this.userPool = new B2CUserPoolAdapter(config);
        this.policyRepository = new AzureRBACPolicyRepository(config);
        this.jwtValidator = new AzureB2CJwtValidator(config);
        this.insights = new ApplicationInsightsAdapter(config);
        this.objectMapper = new ObjectMapper();
        log.info("Initialized Basic Authenticator Function with Azure B2C validation");
    }
    
    @FunctionName("basic-authorize")
    public HttpResponseMessage basicAuthorize(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "authorize/basic",
                     authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<String> request,
        final ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse the request body to AuthorizerRequest
            String requestBody = request.getBody();
            AuthorizerRequest authRequest = parseAuthorizerRequest(requestBody);
            
            log.info("Processing Basic Auth authorization request for method: " + authRequest.getMethodArn());
            
            // Extract Basic Auth credentials
            BasicAuthCredentials credentials = extractBasicAuthCredentials(authRequest.getAuthorizationToken());
            
            // Authenticate with Azure B2C
            String token = userPool.authenticate(credentials.getUsername(), credentials.getPassword());
            
            // Parse token to get user claims
            AzureB2CJwtValidator.AwsCompatibleClaims claims = jwtValidator.validateToken(token);
            
            // Create Principal from claims (for AWS compatibility)
            Principal principal = createPrincipalFromClaims(claims);
            
            // Generate Azure RBAC policy based on user groups
            AzureRBACPolicy rbacPolicy = policyRepository.getPolicy(claims.getUsername(), claims.getGroups());
            
            // Convert to AWS-compatible AuthorizerResponse
            AuthorizerResponse awsResponse = convertToAwsAuthorizerResponse(rbacPolicy, principal, authRequest.getMethodArn());
            
            // Track success metrics
            long duration = System.currentTimeMillis() - startTime;
            insights.trackTokenValidation("basic-authorize", true, "BasicAuth");
            insights.trackFunctionDuration("basic-authorize", duration);
            insights.trackAuthorizationSuccess("basic-authorize", principal.getId());
            
            log.info("Basic Auth authorization successful for user: " + principal.getUsername());
            
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(formatAuthorizerResponse(awsResponse))
                .build();
                
        } catch (IllegalArgumentException e) {
            // Track credential format errors
            long duration = System.currentTimeMillis() - startTime;
            insights.trackTokenValidation("basic-authorize", false, "BasicAuth");
            insights.trackException(e, "basic-authorize");
            insights.trackAuthorizationFailure("basic-authorize", "invalid_credentials_format");
            
            log.warning("Invalid Basic Auth format: " + e.getMessage());
            
            return createErrorResponse(request, 400, "Invalid Basic Auth format", "invalid_credentials_format");
            
        } catch (Exception e) {
            // Track authentication failures and general errors
            long duration = System.currentTimeMillis() - startTime;
            insights.trackTokenValidation("basic-authorize", false, "BasicAuth");
            insights.trackException(e, "basic-authorize");
            
            // Determine if it's an authentication failure or general error
            if (e.getMessage() != null && (e.getMessage().contains("Authentication failed") || 
                                         e.getMessage().contains("Invalid credentials") ||
                                         e.getMessage().contains("User not found"))) {
                insights.trackAuthorizationFailure("basic-authorize", "authentication_failed");
                log.warning("Basic Auth authentication failed: " + e.getMessage());
                return createErrorResponse(request, 401, "Invalid credentials", "invalid_credentials");
            } else {
                insights.trackAuthorizationFailure("basic-authorize", "internal_error");
                log.severe("Basic Auth authorization error: " + e.getMessage());
                return createErrorResponse(request, 500, e.getMessage(), "authorization_error");
            }
        }
    }
    
    private BasicAuthCredentials extractBasicAuthCredentials(String authorizationToken) {
        if (authorizationToken == null || !authorizationToken.startsWith("Basic ")) {
            throw new IllegalArgumentException("Invalid Basic Auth format - missing 'Basic ' prefix");
        }
        
        String encodedCredentials = authorizationToken.substring(6);
        if (encodedCredentials.isEmpty()) {
            throw new IllegalArgumentException("Invalid Basic Auth format - empty credentials");
        }
        
        try {
            String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            
            String[] parts = decodedCredentials.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid Basic Auth credentials format - missing username or password");
            }
            
            if (parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid Basic Auth credentials format - empty username or password");
            }
            
            return new BasicAuthCredentials(parts[0], parts[1]);
            
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Basic Auth format - invalid base64 encoding: " + e.getMessage());
        }
    }
    
    private Principal createPrincipalFromClaims(AzureB2CJwtValidator.AwsCompatibleClaims claims) {
        Principal principal = new Principal();
        principal.setId(claims.getSubject() != null ? claims.getSubject() : claims.getUsername());
        principal.setUsername(claims.getUsername());
        principal.setScope(claims.getGroups());
        
        if (claims.getExpiration() != null) {
            principal.setExpirationTime(claims.getExpiration().getTime());
        }
        
        return principal;
    }
    
    private AuthorizerResponse convertToAwsAuthorizerResponse(AzureRBACPolicy rbacPolicy, Principal principal, String methodArn) {
        AuthorizerResponse.Builder responseBuilder = new AuthorizerResponse.Builder()
            .withPrincipalId(principal.getId());
        
        // Convert Azure RBAC policy to AWS IAM policy format
        if (!rbacPolicy.getRoleAssignments().isEmpty()) {
            String policyDocument = rbacPolicy.toAwsCompatiblePolicyDocument();
            responseBuilder.withPolicyDocument(createPolicyFromJson(policyDocument));
        } else {
            // Create a deny policy if no role assignments
            responseBuilder.withPolicyDocument(createDenyPolicy());
        }
        
        // Add context information (AWS compatibility)
        responseBuilder.withContextAsString("username", principal.getUsername());
        
        if (principal.getScope() != null && !principal.getScope().isEmpty()) {
            responseBuilder.withContextAsString("scope", String.join(",", principal.getScope()));
        }
        
        if (principal.getExpirationTime() != null) {
            responseBuilder.withContextAsNumber("expirationTime", principal.getExpirationTime());
        }
        
        // Add auth type for identification
        responseBuilder.withContextAsString("authType", "basic");
        
        return responseBuilder.build();
    }
    
    private com.amazonaws.auth.policy.Policy createDenyPolicy() {
        return new com.amazonaws.auth.policy.Policy()
            .withStatements(new com.amazonaws.auth.policy.Statement(com.amazonaws.auth.policy.Statement.Effect.Deny)
                .withActions(() -> "*")
                .withResources(new com.amazonaws.auth.policy.Resource("*")));
    }
    
    private com.amazonaws.auth.policy.Policy createPolicyFromJson(String policyJson) {
        return com.amazonaws.auth.policy.Policy.fromJson(policyJson);
    }
    
    /**
     * Parses JSON string to AuthorizerRequest
     */
    private AuthorizerRequest parseAuthorizerRequest(String requestBody) {
        try {
            return objectMapper.readValue(requestBody, AuthorizerRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse authorizer request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Formats AuthorizerResponse to JSON
     */
    private Object formatAuthorizerResponse(AuthorizerResponse response) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("principalId", response.getPrincipalId());
        responseMap.put("policyDocument", response.getPolicyDocument());
        responseMap.put("context", response.getContext());
        return responseMap;
    }
    
    /**
     * Creates error response
     */
    private HttpResponseMessage createErrorResponse(HttpRequestMessage<String> request, int statusCode, String message, String errorType) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("errorMessage", message);
        errorBody.put("errorType", errorType);
        
        HttpStatus azureStatus = mapHttpStatusCode(statusCode);
        
        return request.createResponseBuilder(azureStatus)
            .header("Content-Type", "application/json")
            .body(errorBody)
            .build();
    }
    
    /**
     * Maps HTTP status codes to Azure Functions HttpStatus enum
     */
    private HttpStatus mapHttpStatusCode(int statusCode) {
        switch (statusCode) {
            case 200: return HttpStatus.OK;
            case 400: return HttpStatus.BAD_REQUEST;
            case 401: return HttpStatus.UNAUTHORIZED;
            case 403: return HttpStatus.FORBIDDEN;
            case 404: return HttpStatus.NOT_FOUND;
            case 500: return HttpStatus.INTERNAL_SERVER_ERROR;
            default: return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
    
    /**
     * Basic Auth credentials holder
     */
    private static class BasicAuthCredentials {
        private final String username;
        private final String password;
        
        public BasicAuthCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() { 
            return username; 
        }
        
        public String getPassword() { 
            return password; 
        }
    }
} 