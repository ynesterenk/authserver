# Step 4: JWT Authorizer Function Migration

## Context
This is **Step 4 of 6** in the AWS to Azure migration. You are migrating the JwtAuthorizerLambda to Azure Functions with B2C JWKS integration and Azure RBAC policy generation.

**Prerequisites**: Steps 1-3 must be completed.

**Goal**: Convert JWT authorization function to use Azure AD B2C JWKS and generate Azure RBAC policies.

## What You're Migrating
- **Source**: `authorization-service-jwt-authorizer/src/main/java/authorization/jwt/infrastructure/aws/lambda/AuthorizerRequestHandler.java`
- **Target**: Azure Function with B2C JWT validation and Azure RBAC policy generation
- **Critical**: Maintain identical authorization behavior with Azure RBAC

## Dependencies to Address
Based on the AWS analysis:
- JWT validation with Cognito JWKS → B2C JWKS validation
- IAM policy generation → Azure RBAC policy generation
- `AuthorizerRequest/AuthorizerResponse` → Azure Function request/response
- `ApiGatewayFullAccess` role mapping

## Your Tasks

### 1. Create JWT Authorizer Azure Function

Create `JwtAuthorizerFunction.java` in `authorization-service-jwt-authorizer/src/main/java/authorization/jwt/infrastructure/azure/functions/`:

```java
package authorization.jwt.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import shared.infrastructure.azure.adapters.RequestResponseAdapter;
import shared.infrastructure.azure.adapters.ResponseFormatAdapter;
import shared.infrastructure.azure.monitoring.ApplicationInsightsAdapter;
import shared.infrastructure.azure.auth.AzureB2CJwtValidator;
import authorization.jwt.infrastructure.aws.lambda.AuthorizerRequestHandler;
import authorization.jwt.domain.AuthorizerRequest;
import authorization.jwt.domain.AuthorizerResponse;

public class JwtAuthorizerFunction {
    
    private final AzureB2CJwtValidator jwtValidator;
    private final AuthorizerRequestHandler awsHandler;
    
    public JwtAuthorizerFunction() {
        this.jwtValidator = new AzureB2CJwtValidator();
        this.awsHandler = new AuthorizerRequestHandler();
    }
    
    @FunctionName("jwt-authorize")
    public HttpResponseMessage jwtAuthorize(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "authorize/jwt",
                     authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<AuthorizerRequest> request,
        final ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            AuthorizerRequest authRequest = request.getBody();
            
            // Validate JWT token using Azure B2C JWKS
            String token = extractTokenFromAuthorizationHeader(authRequest.getAuthorizationToken());
            Claims claims = jwtValidator.validateToken(token);
            
            // Generate Azure RBAC policy based on claims
            AzureRBACPolicy rbacPolicy = generateAzureRBACPolicy(claims, authRequest.getMethodArn());
            
            // Convert to AWS-compatible AuthorizerResponse for existing logic
            AuthorizerResponse awsResponse = convertToAwsAuthorizerResponse(rbacPolicy, claims);
            
            // Track success metrics
            long duration = System.currentTimeMillis() - startTime;
            ApplicationInsightsAdapter.trackRequest("jwt-authorize", authRequest.getMethodArn(), duration, true);
            ApplicationInsightsAdapter.trackMetric("JwtValidations", 1.0);
            
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(awsResponse)
                .build();
                
        } catch (JwtVerificationException e) {
            // Track JWT validation failures
            long duration = System.currentTimeMillis() - startTime;
            ApplicationInsightsAdapter.trackRequest("jwt-authorize", "unknown", duration, false);
            ApplicationInsightsAdapter.trackException(e, context);
            
            return ResponseFormatAdapter.createErrorResponse(request, HttpStatus.UNAUTHORIZED, 
                "Invalid JWT token", "invalid_token");
                
        } catch (Exception e) {
            // Track general errors
            long duration = System.currentTimeMillis() - startTime;
            ApplicationInsightsAdapter.trackException(e, context);
            
            return ResponseFormatAdapter.createErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, 
                e.getMessage(), "authorization_error");
        }
    }
    
    private String extractTokenFromAuthorizationHeader(String authorizationToken) {
        if (authorizationToken != null && authorizationToken.startsWith("Bearer ")) {
            return authorizationToken.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization token format");
    }
    
    private AzureRBACPolicy generateAzureRBACPolicy(Claims claims, String methodArn) {
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        // Map AWS actions to Azure operations based on user groups
        for (String group : claims.getGroups()) {
            if ("ApiGatewayFullAccess".equals(group)) {
                AzureRoleAssignment assignment = new AzureRoleAssignment();
                assignment.setPrincipal(claims.getUsername());
                assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
                assignment.addScope(convertMethodArnToAzureScope(methodArn));
                
                policy.addRoleAssignment(assignment);
            }
        }
        
        return policy;
    }
    
    private String convertMethodArnToAzureScope(String methodArn) {
        // Convert AWS API Gateway ARN to Azure APIM resource scope
        // Example: arn:aws:execute-api:region:account:api-id/stage/method/resource
        // To: /subscriptions/{sub}/resourceGroups/{rg}/providers/Microsoft.ApiManagement/service/{apim}
        
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/service/authserver-apim",
            AzureEnvironmentConfig.getSubscriptionId());
    }
    
    private AuthorizerResponse convertToAwsAuthorizerResponse(AzureRBACPolicy rbacPolicy, Claims claims) {
        // Convert Azure RBAC policy back to AWS AuthorizerResponse format
        // This maintains compatibility with existing response handling
        
        AuthorizerResponse response = new AuthorizerResponse();
        response.setPrincipalId(claims.getUsername());
        response.setPolicyDocument(rbacPolicy.toAwsCompatiblePolicyDocument());
        response.setContext(createResponseContext(claims));
        
        return response;
    }
    
    private Map<String, Object> createResponseContext(Claims claims) {
        Map<String, Object> context = new HashMap<>();
        context.put("username", claims.getUsername());
        context.put("groups", String.join(",", claims.getGroups()));
        context.put("issuer", claims.getIssuer());
        context.put("audience", claims.getAudience());
        
        return context;
    }
}
```

### 2. Create Azure RBAC Policy Classes

Create `AzureRBACPolicy.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/auth/`:

```java
package shared.infrastructure.azure.auth;

import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AzureRBACPolicy {
    private List<AzureRoleAssignment> roleAssignments;
    
    public AzureRBACPolicy() {
        this.roleAssignments = new ArrayList<>();
    }
    
    public void addRoleAssignment(AzureRoleAssignment assignment) {
        this.roleAssignments.add(assignment);
    }
    
    public List<AzureRoleAssignment> getRoleAssignments() {
        return roleAssignments;
    }
    
    /**
     * Convert Azure RBAC policy to AWS IAM policy format for compatibility
     */
    public String toAwsCompatiblePolicyDocument() {
        // Create AWS IAM policy JSON that represents the Azure RBAC permissions
        Map<String, Object> policy = new HashMap<>();
        policy.put("Version", "2012-10-17");
        
        List<Map<String, Object>> statements = new ArrayList<>();
        
        for (AzureRoleAssignment assignment : roleAssignments) {
            Map<String, Object> statement = new HashMap<>();
            statement.put("Effect", "Allow");
            statement.put("Action", convertAzureOperationsToAwsActions(assignment.getOperations()));
            statement.put("Resource", convertAzureScopeToAwsResource(assignment.getScope()));
            
            statements.add(statement);
        }
        
        policy.put("Statement", statements);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(policy);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize policy", e);
        }
    }
    
    private List<String> convertAzureOperationsToAwsActions(List<String> azureOperations) {
        List<String> awsActions = new ArrayList<>();
        
        for (String operation : azureOperations) {
            switch (operation) {
                case "Microsoft.ApiManagement/service/gateways/action":
                    awsActions.add("execute-api:Invoke");
                    break;
                case "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read":
                    awsActions.add("s3:GetObject");
                    break;
                default:
                    awsActions.add("*"); // Fallback for unmapped operations
            }
        }
        
        return awsActions;
    }
    
    private String convertAzureScopeToAwsResource(String azureScope) {
        // Convert Azure resource scope to AWS ARN format
        if (azureScope.contains("Microsoft.ApiManagement")) {
            return "arn:aws:execute-api:*:*:*";
        } else if (azureScope.contains("Microsoft.Storage")) {
            return "arn:aws:s3:::*/*";
        }
        
        return "*"; // Fallback
    }
}

public class AzureRoleAssignment {
    private String principal;
    private List<String> operations;
    private String scope;
    private List<String> groups;
    
    public AzureRoleAssignment() {
        this.operations = new ArrayList<>();
        this.groups = new ArrayList<>();
    }
    
    // Getters and setters
    public void setPrincipal(String principal) { this.principal = principal; }
    public String getPrincipal() { return principal; }
    
    public void addOperation(String operation) { this.operations.add(operation); }
    public List<String> getOperations() { return operations; }
    
    public void addScope(String scope) { this.scope = scope; }
    public String getScope() { return scope; }
    
    public void setGroups(List<String> groups) { this.groups = groups; }
    public List<String> getGroups() { return groups; }
}
```

### 3. Update Maven Dependencies

Add Azure Resource Manager dependencies to `authorization-service-jwt-authorizer/pom.xml`:

```xml
<dependencies>
    <!-- Existing dependencies... -->
    
    <!-- Azure Functions Java SDK -->
    <dependency>
        <groupId>com.microsoft.azure.functions</groupId>
        <artifactId>azure-functions-java-library</artifactId>
        <version>3.0.0</version>
    </dependency>
    
    <!-- Azure Resource Manager for RBAC -->
    <dependency>
        <groupId>com.azure.resourcemanager</groupId>
        <artifactId>azure-resourcemanager-authorization</artifactId>
        <version>2.19.0</version>
    </dependency>
    
    <!-- Reference shared Azure components -->
    <dependency>
        <groupId>com.lseg.authserver</groupId>
        <artifactId>authorization-service-shared</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

### 4. Create Function Configuration

Create `host.json` in `authorization-service-jwt-authorizer/`:

```json
{
  "version": "2.0",
  "functionTimeout": "00:00:30",
  "logging": {
    "applicationInsights": {
      "samplingSettings": {
        "isEnabled": true
      }
    }
  },
  "extensionBundle": {
    "id": "Microsoft.Azure.Functions.ExtensionBundle",
    "version": "[2.*, 3.0.0)"
  }
}
```

### 5. Update Unit Tests

Create comprehensive tests for JWT validation and RBAC policy generation:

```java
@Test
public void testJwtValidationWithB2CToken() {
    // Mock B2C JWT token
    String b2cToken = createMockB2CToken();
    
    // Mock request
    AuthorizerRequest request = new AuthorizerRequest();
    request.setAuthorizationToken("Bearer " + b2cToken);
    request.setMethodArn("arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request");
    
    HttpRequestMessage<AuthorizerRequest> httpRequest = mock(HttpRequestMessage.class);
    when(httpRequest.getBody()).thenReturn(request);
    
    // Execute function
    JwtAuthorizerFunction function = new JwtAuthorizerFunction();
    HttpResponseMessage response = function.jwtAuthorize(httpRequest, mock(ExecutionContext.class));
    
    // Verify response
    assertEquals(HttpStatus.OK, response.getStatus());
    
    AuthorizerResponse authResponse = (AuthorizerResponse) response.getBody();
    assertNotNull(authResponse.getPolicyDocument());
    assertEquals("admin", authResponse.getPrincipalId());
}

@Test
public void testAzureRBACPolicyGeneration() {
    // Test Azure RBAC policy generation for ApiGatewayFullAccess group
    Claims claims = new Claims();
    claims.setUsername("admin");
    claims.setGroups(Arrays.asList("ApiGatewayFullAccess"));
    
    JwtAuthorizerFunction function = new JwtAuthorizerFunction();
    AzureRBACPolicy policy = function.generateAzureRBACPolicy(claims, "test-method-arn");
    
    assertNotNull(policy);
    assertEquals(1, policy.getRoleAssignments().size());
    
    AzureRoleAssignment assignment = policy.getRoleAssignments().get(0);
    assertEquals("admin", assignment.getPrincipal());
    assertTrue(assignment.getOperations().contains("Microsoft.ApiManagement/service/gateways/action"));
}
```

## Success Criteria
- ✅ JWT validation works with Azure B2C JWKS endpoint
- ✅ Azure RBAC policies generated correctly for user groups
- ✅ AuthorizerResponse maintains AWS-compatible format
- ✅ ApiGatewayFullAccess group maps to correct Azure permissions
- ✅ Error handling preserves AWS error response format
- ✅ Performance metrics tracked in Application Insights
- ✅ All existing unit tests pass with minimal changes
- ✅ Integration tests verify B2C JWT validation

## Testing Commands
```bash
# Run JWT authorizer tests
cd authorization-service-jwt-authorizer
mvn clean test

# Test B2C JWKS connectivity
mvn test -Dtest=*B2CJwtValidator*

# Test RBAC policy generation
mvn test -Dtest=*RBACPolicy*

# Integration test with real B2C token
mvn test -Dtest=*Integration*
```

## Next Steps
After completing this step:
1. JWT authorization uses Azure B2C JWKS validation
2. Step 5 can begin migrating Basic Authenticator function
3. Azure RBAC policies replace AWS IAM policies
4. Authorization behavior is Azure-native but API-compatible

## Risk Mitigation
- **JWKS Endpoint**: Verify B2C JWKS endpoint accessibility and format
- **Policy Compatibility**: Ensure Azure RBAC policies provide equivalent permissions
- **Token Validation**: Comprehensive testing with various JWT token formats
- **Error Handling**: Maintain exact AWS error response format