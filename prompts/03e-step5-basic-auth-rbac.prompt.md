# Step 5: Basic Authenticator and RBAC Migration

## Context
This is **Step 5 of 6** in the AWS to Azure migration. You are migrating the BasicAuthenticatorLambda and completing the IAM to Azure RBAC conversion.

**Prerequisites**: Steps 1-4 must be completed.

**Goal**: Convert Basic Auth function to use Azure AD B2C and complete Azure RBAC policy implementation.

## What You're Migrating
- **Source**: `authorization-service-basic-authenticator/src/main/java/basic/infrastructure/aws/lambda/AuthorizerRequestHandler.java`
- **Target**: Azure Function with B2C Basic Auth validation and Azure RBAC
- **Pattern**: HTTP Basic Auth fallback for legacy clients using B2C authentication

## Dependencies to Address
Based on the AWS analysis:
- Basic Auth validation with Cognito → B2C authentication
- IAM policy generation → Azure RBAC policy generation
- Legacy client support with identical response format
- `RolePolicyRepository` → `AzureRBACPolicyRepository`

## Your Tasks

### 1. Create Basic Authenticator Azure Function

Create `BasicAuthenticatorFunction.java` in `authorization-service-basic-authenticator/src/main/java/basic/infrastructure/azure/functions/`:

```java
package basic.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import shared.infrastructure.azure.adapters.RequestResponseAdapter;
import shared.infrastructure.azure.adapters.ResponseFormatAdapter;
import shared.infrastructure.azure.monitoring.ApplicationInsightsAdapter;
import shared.infrastructure.azure.auth.B2CUserPoolAdapter;
import shared.infrastructure.azure.auth.AzureRBACPolicyRepository;
import basic.infrastructure.aws.lambda.AuthorizerRequestHandler;
import basic.domain.AuthorizerRequest;
import basic.domain.AuthorizerResponse;

public class BasicAuthenticatorFunction {
    
    private final B2CUserPoolAdapter userPool;
    private final AzureRBACPolicyRepository policyRepository;
    private final AuthorizerRequestHandler awsHandler;
    
    public BasicAuthenticatorFunction() {
        this.userPool = new B2CUserPoolAdapter();
        this.policyRepository = new AzureRBACPolicyRepository();
        this.awsHandler = new AuthorizerRequestHandler();
    }
    
    @FunctionName("basic-authorize")
    public HttpResponseMessage basicAuthorize(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "authorize/basic",
                     authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<AuthorizerRequest> request,
        final ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            AuthorizerRequest authRequest = request.getBody();
            
            // Extract Basic Auth credentials
            BasicAuthCredentials credentials = extractBasicAuthCredentials(authRequest.getAuthorizationToken());
            
            // Authenticate with Azure B2C
            String token = userPool.authenticate(credentials.getUsername(), credentials.getPassword());
            
            // Parse token to get user claims
            Claims claims = parseTokenClaims(token);
            
            // Generate Azure RBAC policy based on user groups
            AzureRBACPolicy rbacPolicy = policyRepository.getPolicy(claims.getUsername(), claims.getGroups());
            
            // Convert to AWS-compatible AuthorizerResponse
            AuthorizerResponse awsResponse = convertToAwsAuthorizerResponse(rbacPolicy, claims, authRequest.getMethodArn());
            
            // Track success metrics
            long duration = System.currentTimeMillis() - startTime;
            ApplicationInsightsAdapter.trackRequest("basic-authorize", authRequest.getMethodArn(), duration, true);
            ApplicationInsightsAdapter.trackMetric("BasicAuthValidations", 1.0);
            
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(awsResponse)
                .build();
                
        } catch (UserPoolException e) {
            // Track authentication failures
            long duration = System.currentTimeMillis() - startTime;
            ApplicationInsightsAdapter.trackRequest("basic-authorize", "unknown", duration, false);
            ApplicationInsightsAdapter.trackException(e, context);
            
            return ResponseFormatAdapter.createErrorResponse(request, HttpStatus.UNAUTHORIZED, 
                "Invalid credentials", "invalid_credentials");
                
        } catch (Exception e) {
            // Track general errors
            long duration = System.currentTimeMillis() - startTime;
            ApplicationInsightsAdapter.trackException(e, context);
            
            return ResponseFormatAdapter.createErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, 
                e.getMessage(), "authorization_error");
        }
    }
    
    private BasicAuthCredentials extractBasicAuthCredentials(String authorizationToken) {
        if (authorizationToken == null || !authorizationToken.startsWith("Basic ")) {
            throw new IllegalArgumentException("Invalid Basic Auth format");
        }
        
        String encodedCredentials = authorizationToken.substring(6);
        String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
        
        String[] parts = decodedCredentials.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Basic Auth credentials format");
        }
        
        return new BasicAuthCredentials(parts[0], parts[1]);
    }
    
    private Claims parseTokenClaims(String token) {
        // Parse JWT token to extract claims
        // This could use the same AzureB2CJwtValidator from Step 4
        AzureB2CJwtValidator validator = new AzureB2CJwtValidator();
        return validator.validateToken(token);
    }
    
    private AuthorizerResponse convertToAwsAuthorizerResponse(AzureRBACPolicy rbacPolicy, Claims claims, String methodArn) {
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
        context.put("authType", "basic");
        context.put("issuer", claims.getIssuer());
        
        return context;
    }
    
    private static class BasicAuthCredentials {
        private final String username;
        private final String password;
        
        public BasicAuthCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}
```

### 2. Create Azure RBAC Policy Repository

Create `AzureRBACPolicyRepository.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/auth/`:

```java
package shared.infrastructure.azure.auth;

import com.azure.resourcemanager.authorization.AuthorizationManager;
import com.azure.resourcemanager.authorization.models.RoleAssignment;
import com.azure.resourcemanager.authorization.models.RoleDefinition;
import com.azure.identity.DefaultAzureCredentialBuilder;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;

public class AzureRBACPolicyRepository {
    private final AuthorizationManager authManager;
    
    public AzureRBACPolicyRepository() {
        this.authManager = AuthorizationManager.authenticate(
            new DefaultAzureCredentialBuilder().build(),
            AzureEnvironmentConfig.getAzureProfile()
        );
    }
    
    public AzureRBACPolicy getPolicy(String principalId, List<String> groups) {
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        // Map user groups to Azure RBAC roles
        for (String group : groups) {
            switch (group) {
                case "ApiGatewayFullAccess":
                    addApiManagementPermissions(policy, principalId);
                    break;
                case "ReadOnlyAccess":
                    addReadOnlyPermissions(policy, principalId);
                    break;
                default:
                    // Handle unknown groups with minimal permissions
                    addMinimalPermissions(policy, principalId);
            }
        }
        
        return policy;
    }
    
    private void addApiManagementPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Add API Management permissions
        assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        assignment.addOperation("Microsoft.ApiManagement/service/apis/write");
        
        // Scope to the authserver APIM instance
        String scope = String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/service/authserver-apim",
            AzureEnvironmentConfig.getSubscriptionId());
        assignment.addScope(scope);
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addReadOnlyPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Add read-only permissions
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
        
        String scope = String.format("/subscriptions/%s/resourceGroups/authserver-rg",
            AzureEnvironmentConfig.getSubscriptionId());
        assignment.addScope(scope);
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addMinimalPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Minimal permissions for unknown groups
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        
        String scope = String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/service/authserver-apim",
            AzureEnvironmentConfig.getSubscriptionId());
        assignment.addScope(scope);
        
        policy.addRoleAssignment(assignment);
    }
    
    /**
     * Create custom Azure RBAC role definition if needed
     */
    public void createCustomRoleDefinition(String roleName, List<String> operations, String scope) {
        try {
            RoleDefinition roleDefinition = authManager.roleDefinitions()
                .define(UUID.randomUUID().toString())
                .withRoleName(roleName)
                .withDescription("Custom role for authserver migration")
                .withType("CustomRole")
                .withAssignableScope(scope)
                .withPermissions(operations)
                .create();
                
            // Log role creation
            System.out.println("Created custom role: " + roleDefinition.roleName());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create custom role definition", e);
        }
    }
}
```

### 3. Update Policy Builder for Azure RBAC

Create `AzureRBACPolicyBuilder.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/auth/`:

```java
package shared.infrastructure.azure.auth;

import shared.domain.Claims;
import java.util.Map;
import java.util.HashMap;

public class AzureRBACPolicyBuilder {
    
    private static final Map<String, String> AWS_TO_AZURE_ACTION_MAPPINGS = Map.of(
        "execute-api:Invoke", "Microsoft.ApiManagement/service/gateways/action",
        "s3:GetObject", "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read",
        "s3:PutObject", "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write",
        "logs:CreateLogStream", "Microsoft.Insights/logs/write",
        "logs:PutLogEvents", "Microsoft.Insights/logs/write"
    );
    
    public AzureRBACPolicy buildPolicy(Claims claims) {
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        // Generate role assignments based on user groups
        for (String group : claims.getGroups()) {
            AzureRoleAssignment assignment = createRoleAssignmentForGroup(group, claims.getUsername());
            if (assignment != null) {
                policy.addRoleAssignment(assignment);
            }
        }
        
        return policy;
    }
    
    private AzureRoleAssignment createRoleAssignmentForGroup(String group, String username) {
        switch (group) {
            case "ApiGatewayFullAccess":
                return createApiGatewayFullAccessAssignment(username);
            case "ReadOnlyAccess":
                return createReadOnlyAccessAssignment(username);
            case "AdminAccess":
                return createAdminAccessAssignment(username);
            default:
                return createDefaultAccessAssignment(username);
        }
    }
    
    private AzureRoleAssignment createApiGatewayFullAccessAssignment(String username) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(username);
        
        // Full API Management permissions
        assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        assignment.addOperation("Microsoft.ApiManagement/service/apis/write");
        assignment.addOperation("Microsoft.ApiManagement/service/policies/read");
        assignment.addOperation("Microsoft.ApiManagement/service/policies/write");
        
        // Scope to authserver APIM
        assignment.addScope(getApiManagementScope());
        
        return assignment;
    }
    
    private AzureRoleAssignment createReadOnlyAccessAssignment(String username) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(username);
        
        // Read-only permissions
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
        assignment.addOperation("Microsoft.Insights/logs/read");
        
        // Scope to resource group
        assignment.addScope(getResourceGroupScope());
        
        return assignment;
    }
    
    private AzureRoleAssignment createAdminAccessAssignment(String username) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(username);
        
        // Admin permissions
        assignment.addOperation("*");
        
        // Scope to resource group
        assignment.addScope(getResourceGroupScope());
        
        return assignment;
    }
    
    private AzureRoleAssignment createDefaultAccessAssignment(String username) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(username);
        
        // Minimal permissions
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        
        // Scope to specific API
        assignment.addScope(getApiManagementScope() + "/apis/oauth-api");
        
        return assignment;
    }
    
    private String getApiManagementScope() {
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/service/authserver-apim",
            AzureEnvironmentConfig.getSubscriptionId());
    }
    
    private String getResourceGroupScope() {
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg",
            AzureEnvironmentConfig.getSubscriptionId());
    }
    
    /**
     * Convert AWS IAM actions to Azure operations
     */
    public static String mapAwsActionToAzureOperation(String awsAction) {
        return AWS_TO_AZURE_ACTION_MAPPINGS.getOrDefault(awsAction, awsAction);
    }
    
    /**
     * Convert AWS resource ARN to Azure resource scope
     */
    public static String mapAwsResourceToAzureScope(String awsResourceArn) {
        if (awsResourceArn.contains("execute-api")) {
            return getApiManagementScope();
        } else if (awsResourceArn.contains("s3")) {
            return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.Storage/storageAccounts/authserverstorage",
                AzureEnvironmentConfig.getSubscriptionId());
        }
        
        return getResourceGroupScope(); // Default fallback
    }
}
```

### 4. Update Maven Dependencies

Add dependencies to `authorization-service-basic-authenticator/pom.xml`:

```xml
<dependencies>
    <!-- Existing dependencies... -->
    
    <!-- Azure Functions Java SDK -->
    <dependency>
        <groupId>com.microsoft.azure.functions</groupId>
        <artifactId>azure-functions-java-library</artifactId>
        <version>3.0.0</version>
    </dependency>
    
    <!-- Azure Resource Manager -->
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

## Success Criteria
- ✅ Basic Auth validation works with Azure B2C
- ✅ Legacy clients can authenticate using HTTP Basic Auth
- ✅ Azure RBAC policies generated correctly for all user groups
- ✅ Policy repository maps groups to appropriate Azure permissions
- ✅ AuthorizerResponse maintains AWS-compatible format
- ✅ Error handling preserves AWS error response format
- ✅ Performance metrics tracked in Application Insights
- ✅ All existing unit tests pass with minimal changes

## Testing Commands
```bash
# Run Basic Auth tests
cd authorization-service-basic-authenticator
mvn clean test

# Test RBAC policy generation
mvn test -Dtest=*RBACPolicy*

# Test Basic Auth credential parsing
mvn test -Dtest=*BasicAuth*

# Integration test with B2C
mvn test -Dtest=*Integration*
```

## Next Steps
After completing this step:
1. All authentication methods use Azure AD B2C
2. Step 6 can begin final integration and monitoring
3. Azure RBAC completely replaces AWS IAM
4. Legacy Basic Auth clients are fully supported

## Risk Mitigation
- **Credential Security**: Ensure Basic Auth credentials are handled securely
- **Policy Mapping**: Verify Azure RBAC policies provide equivalent access
- **Legacy Support**: Maintain exact Basic Auth behavior for legacy clients
- **Error Compatibility**: Preserve AWS error response format exactly