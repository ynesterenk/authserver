package authorization.jwt.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import shared.infrastructure.azure.adapters.RequestResponseAdapter;
import shared.infrastructure.azure.adapters.ResponseFormatAdapter;
import shared.infrastructure.azure.monitoring.ApplicationInsightsAdapter;
import shared.infrastructure.azure.auth.AzureB2CJwtValidator;
import shared.infrastructure.azure.auth.AzureRBACPolicy;
import shared.infrastructure.azure.auth.AzureRoleAssignment;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import shared.infrastructure.aws.gateway.AuthorizerRequest;
import shared.infrastructure.aws.gateway.AuthorizerResponse;
import lombok.extern.java.Log;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * Azure Functions JWT Authorizer that validates Azure AD B2C tokens
 * and generates Azure RBAC policies while maintaining AWS compatibility.
 */
@Log
public class JwtAuthorizerFunction {
    
    private final AzureB2CJwtValidator jwtValidator;
    private final AzureEnvironmentConfig config;
    private final ApplicationInsightsAdapter insights;
    private final ObjectMapper objectMapper;
    
    public JwtAuthorizerFunction() {
        this.config = new AzureEnvironmentConfig();
        this.jwtValidator = new AzureB2CJwtValidator(config);
        this.insights = new ApplicationInsightsAdapter(config);
        this.objectMapper = new ObjectMapper();
        log.info("Initialized JWT Authorizer Function with Azure B2C validation");
    }
    
    @FunctionName("jwt-authorize")
    public HttpResponseMessage jwtAuthorize(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "authorize/jwt",
                     authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<String> request,
        final ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse the request body to AuthorizerRequest
            String requestBody = request.getBody();
            AuthorizerRequest authRequest = parseAuthorizerRequest(requestBody);
            
            log.info("Processing JWT authorization request for method: " + authRequest.getMethodArn());
            
            // Validate JWT token using Azure B2C JWKS
            String token = extractTokenFromAuthorizationHeader(authRequest.getAuthorizationToken());
            AzureB2CJwtValidator.AwsCompatibleClaims claims = jwtValidator.validateToken(token);
            
            // Generate Azure RBAC policy based on claims
            AzureRBACPolicy rbacPolicy = generateAzureRBACPolicy(claims, authRequest.getMethodArn());
            
            // Convert to AWS-compatible AuthorizerResponse for existing logic
            AuthorizerResponse awsResponse = convertToAwsAuthorizerResponse(rbacPolicy, claims);
            
            // Track success metrics
            long duration = System.currentTimeMillis() - startTime;
            insights.trackTokenValidation("jwt-authorize", true, "B2C");
            insights.trackFunctionDuration("jwt-authorize", duration);
            
            log.info("JWT authorization successful for user: " + claims.getUsername());
            
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(formatAuthorizerResponse(awsResponse))
                .build();
                
        } catch (AzureB2CJwtValidator.JwtValidationException e) {
            // Track JWT validation failures
            long duration = System.currentTimeMillis() - startTime;
            insights.trackTokenValidation("jwt-authorize", false, "B2C");
            insights.trackException(e, "jwt-authorize");
            
            log.warning("JWT validation failed: " + e.getMessage());
            
            return createErrorResponse(request, 401, "Invalid JWT token", "invalid_token");
                
        } catch (Exception e) {
            // Track general errors
            long duration = System.currentTimeMillis() - startTime;
            insights.trackException(e, "jwt-authorize");
            
            log.severe("JWT authorization error: " + e.getMessage());
            
            return createErrorResponse(request, 500, e.getMessage(), "authorization_error");
        }
    }
    
    private String extractTokenFromAuthorizationHeader(String authorizationToken) {
        if (authorizationToken != null && authorizationToken.startsWith("Bearer ")) {
            return authorizationToken.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization token format");
    }
    
    private AzureRBACPolicy generateAzureRBACPolicy(AzureB2CJwtValidator.AwsCompatibleClaims claims, String methodArn) {
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        // Map AWS actions to Azure operations based on user groups
        List<String> groups = claims.getGroups();
        if (groups != null) {
            for (String group : groups) {
                if ("ApiGatewayFullAccess".equals(group)) {
                    AzureRoleAssignment assignment = new AzureRoleAssignment();
                    assignment.setPrincipal(claims.getUsername());
                    assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
                    assignment.setScope(convertMethodArnToAzureScope(methodArn));
                    
                    policy.addRoleAssignment(assignment);
                } else if ("S3FullAccess".equals(group)) {
                    AzureRoleAssignment assignment = new AzureRoleAssignment();
                    assignment.setPrincipal(claims.getUsername());
                    assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
                    assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write");
                    assignment.setScope(convertMethodArnToAzureScope(methodArn));
                    
                    policy.addRoleAssignment(assignment);
                } else if ("SecretsManagerAccess".equals(group)) {
                    AzureRoleAssignment assignment = new AzureRoleAssignment();
                    assignment.setPrincipal(claims.getUsername());
                    assignment.addOperation("Microsoft.KeyVault/vaults/secrets/read");
                    assignment.setScope(convertMethodArnToAzureScope(methodArn));
                    
                    policy.addRoleAssignment(assignment);
                }
            }
        }
        
        // If no specific groups found, create a default deny policy
        if (policy.getRoleAssignments().isEmpty()) {
            log.warning("No matching groups found for user: " + claims.getUsername() + ", groups: " + groups);
            // Return empty policy which will result in deny
        }
        
        return policy;
    }
    
    private String convertMethodArnToAzureScope(String methodArn) {
        // Convert AWS API Gateway ARN to Azure APIM resource scope
        // Example: arn:aws:execute-api:region:account:api-id/stage/method/resource
        // To: /subscriptions/{sub}/resourceGroups/{rg}/providers/Microsoft.ApiManagement/service/{apim}
        
        String subscriptionId = config.getAwsRegion(); // Use AWS region as fallback
        if (subscriptionId == null) {
            subscriptionId = "default-subscription";
        }
        
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/service/authserver-apim",
            subscriptionId);
    }
    
    private AuthorizerResponse convertToAwsAuthorizerResponse(AzureRBACPolicy rbacPolicy, AzureB2CJwtValidator.AwsCompatibleClaims claims) {
        // Convert Azure RBAC policy back to AWS AuthorizerResponse format
        // This maintains compatibility with existing response handling
        
        AuthorizerResponse.Builder responseBuilder = new AuthorizerResponse.Builder()
            .withPrincipalId(claims.getUsername());
        
        // Convert Azure RBAC policy to AWS IAM policy format
        if (!rbacPolicy.getRoleAssignments().isEmpty()) {
            String policyDocument = rbacPolicy.toAwsCompatiblePolicyDocument();
            responseBuilder.withPolicyDocument(createPolicyFromJson(policyDocument));
        } else {
            // Create a deny policy if no role assignments
            responseBuilder.withPolicyDocument(createDenyPolicy());
        }
        
        // Add context information
        Map<String, Object> context = createResponseContext(claims);
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() instanceof String) {
                responseBuilder.withContextAsString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                responseBuilder.withContextAsNumber(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                responseBuilder.withContextAsNumber(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Double) {
                responseBuilder.withContextAsNumber(entry.getKey(), (Double) entry.getValue());
            }
        }
        
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
    
    private Map<String, Object> createResponseContext(AzureB2CJwtValidator.AwsCompatibleClaims claims) {
        Map<String, Object> context = new HashMap<>();
        context.put("username", claims.getUsername());
        
        if (claims.getGroups() != null) {
            context.put("groups", String.join(",", claims.getGroups()));
        }
        
        context.put("issuer", claims.getIssuer());
        
        if (claims.getAudience() != null && !claims.getAudience().isEmpty()) {
            context.put("audience", claims.getAudience().get(0));
        }
        
        if (claims.getExpiration() != null) {
            context.put("expirationTime", claims.getExpiration().getTime() / 1000); // Unix timestamp
        }
        
        return context;
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
} 