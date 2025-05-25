# Code Adaptation Phase - AWS to Azure SDK Migration

## Context
You are converting the Java 11 authserver codebase from AWS SDK to Azure SDK equivalents. This is Phase 3 focusing on code adaptation while maintaining functional parity.

Based on the AWS dependencies analysis, you need to migrate:
- **3 Lambda Functions**: ServerLambda/ProxyRequestHandler, JwtAuthorizerLambda, BasicAuthenticatorLambda
- **AWS Cognito User Pool**: AdminInitiateAuth flow with NEW_PASSWORD_REQUIRED challenge handling
- **AWS IAM**: Policy generation for ApiGatewayFullAccess role
- **JWKS Integration**: Remote key fetching from Cognito to B2C endpoints

## Migration Targets
- Replace AWS SDK calls with Azure SDK for Java
- Convert Cognito User Pool calls to Azure AD B2C/MSAL4J
- Adapt IAM policy generation to Azure RBAC
- Convert CloudWatch logging to Azure Monitor
- Maintain identical API contracts and response formats

## Discovered AWS Dependencies to Replace

### High Priority AWS SDK Dependencies
```java
// AWS Lambda Runtime (CRITICAL)
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

// AWS Cognito Identity Provider (CRITICAL)
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClient;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;

// AWS IAM (CRITICAL)
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.GetRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetRolePolicyResult;

// AWS Policy Framework (CRITICAL)
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Condition;
```

### Azure SDK Replacements
```java
// Azure Functions Java SDK
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.ExecutionContext;

// Azure AD B2C/MSAL4J
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.ClientCredentialParameters;

// Azure Resource Manager
import com.azure.resourcemanager.authorization.AuthorizationManager;
import com.azure.resourcemanager.authorization.models.RoleAssignment;
import com.azure.resourcemanager.authorization.models.RoleDefinition;

// Azure Identity
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;

// JWT Validation
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
```

## Your Tasks

### 1. Lambda Handler Adaptation

#### AuthServer Function (Replace ServerLambda/ProxyRequestHandler)
```java
// AWS Lambda Handler (BEFORE)
package server.infrastructure.aws.lambda;

public class ProxyRequestHandler implements RequestHandler<ProxyRequest, ProxyResponse> {
    @Override
    public ProxyResponse handleRequest(ProxyRequest request, Context context) {
        // Current implementation with Router.match() and Factory patterns
    }
}

// Azure Function (AFTER)
package server.infrastructure.azure.functions;

public class AuthServerFunction {
    @FunctionName("oauth-token")
    public HttpResponseMessage oauthToken(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "oauth/token",
                     authLevel = AuthorizationLevel.ANONYMOUS) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        
        // Convert Azure request to AWS-compatible ProxyRequest
        ProxyRequest awsRequest = convertAzureToAwsRequest(request);
        Context awsContext = convertAzureToAwsContext(context);
        
        // Reuse existing handler logic
        ProxyRequestHandler handler = new ProxyRequestHandler();
        ProxyResponse awsResponse = handler.handleRequest(awsRequest, awsContext);
        
        // Convert back to Azure response
        return convertAwsToAzureResponse(awsResponse, request);
    }
    
    @FunctionName("change-password")
    public HttpResponseMessage changePassword(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "change-password",
                     authLevel = AuthorizationLevel.ANONYMOUS) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        // Similar adapter pattern
    }
}
```

#### JwtAuthorizer Function (Replace JwtAuthorizerLambda)
```java
// AWS Lambda Handler (BEFORE)
package authorization.jwt.infrastructure.aws.lambda;

public class AuthorizerRequestHandler implements RequestHandler<AuthorizerRequest, AuthorizerResponse> {
    @Override
    public AuthorizerResponse handleRequest(AuthorizerRequest request, Context context) {
        // JWT validation with Cognito JWKS
        // IAM policy generation
    }
}

// Azure Function (AFTER)
package authorization.jwt.infrastructure.azure.functions;

public class JwtAuthorizerFunction {
    @FunctionName("jwt-authorize")
    public AuthorizerResponse jwtAuthorize(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "authorize/jwt",
                     authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<AuthorizerRequest> request,
        final ExecutionContext context) {
        
        // Use Azure B2C JWKS endpoint
        String jwksUrl = String.format(
            "https://%s.b2clogin.com/%s/%s/discovery/v2.0/keys",
            tenantName, tenantId, policyName
        );
        
        // Validate JWT and generate Azure RBAC policy
    }
}
```

#### BasicAuthenticator Function (Replace BasicAuthenticatorLambda)
```java
// AWS Lambda Handler (BEFORE)
package basic.infrastructure.aws.lambda;

public class AuthorizerRequestHandler implements RequestHandler<AuthorizerRequest, AuthorizerResponse> {
    @Override
    public AuthorizerResponse handleRequest(AuthorizerRequest request, Context context) {
        // Basic auth validation with Cognito
    }
}

// Azure Function (AFTER)
package basic.infrastructure.azure.functions;

public class BasicAuthenticatorFunction {
    @FunctionName("basic-authorize")
    public AuthorizerResponse basicAuthorize(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "authorize/basic",
                     authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<AuthorizerRequest> request,
        final ExecutionContext context) {
        // Basic auth validation with Azure AD B2C
    }
}
```

### 2. Cognito to Azure AD B2C Migration

#### Replace CognitoUserPool.java
```java
// AWS Cognito Implementation (BEFORE)
public class CognitoUserPool implements UserPool {
    private final AWSCognitoIdentityProviderClient cognitoClient;
    
    @Override
    public String authenticate(String username, String password) throws UserPoolException {
        AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
            .withUserPoolId(userPoolId)
            .withClientId(clientId)
            .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
            .withAuthParameters(Map.of(
                "USERNAME", username,
                "PASSWORD", password
            ));
            
        AdminInitiateAuthResult result = cognitoClient.adminInitiateAuth(authRequest);
        
        // Handle NEW_PASSWORD_REQUIRED challenge
        if (result.getChallengeName() != null) {
            // Challenge handling logic
        }
        
        return result.getAuthenticationResult().getIdToken();
    }
}

// Azure AD B2C Implementation (AFTER)
public class B2CUserPoolAdapter implements UserPool {
    private final ConfidentialClientApplication msalApp;
    private final String tenantId;
    private final String clientId;
    
    @Override
    public String authenticate(String username, String password) throws UserPoolException {
        try {
            // Mimic AdminInitiateAuth behavior with MSAL4J
            UserNamePasswordParameters parameters = 
                UserNamePasswordParameters.builder(
                    Collections.singleton(clientId + "/.default"),
                    username,
                    password.toCharArray())
                .build();
                
            CompletableFuture<IAuthenticationResult> future = 
                msalApp.acquireToken(parameters);
            IAuthenticationResult result = future.get();
            
            return result.idToken();
        } catch (Exception e) {
            throw new UserPoolException("Authentication failed", e);
        }
    }
    
    @Override
    public void changePassword(String username, String previousPassword, String proposedPassword) 
            throws UserPoolException {
        // Implement password change via B2C Graph API
        // Handle NEW_PASSWORD_REQUIRED challenge equivalent
    }
}
```

#### Update JWKS Integration
```java
// AWS Cognito JWKS (BEFORE)
public class Jwt {
    private static final String COGNITO_JWKS_URL = 
        "https://cognito-idp.{region}.amazonaws.com/{poolId}/.well-known/jwks.json";
        
    public static Claims verify(String token) {
        // Cognito JWKS validation
    }
}

// Azure B2C JWKS (AFTER)
public class AzureB2CJwtValidator {
    private final String tenantName;
    private final String tenantId; 
    private final String policyName;
    
    public JWKSource<SecurityContext> createJWKSource() throws MalformedURLException {
        // Azure AD B2C JWKS endpoint format
        String jwksUrl = String.format(
            "https://%s.b2clogin.com/%s/%s/discovery/v2.0/keys",
            tenantName, tenantId, policyName
        );
        
        return new RemoteJWKSet<>(new URL(jwksUrl));
    }
    
    public Claims validateToken(String token) throws JwtVerificationException {
        try {
            JWKSource<SecurityContext> jwkSource = createJWKSource();
            
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = 
                new DefaultJWTProcessor<>();
            jwtProcessor.setJWKSource(jwkSource);
            jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));
            
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
            JWSKeySelector<SecurityContext> keySelector = 
                new JWSVerificationKeySelector<>(expectedJWSAlg, jwkSource);
            jwtProcessor.setJWSKeySelector(keySelector);
            
            SecurityContext ctx = null;
            JWTClaimsSet claimsSet = jwtProcessor.process(token, ctx);
            
            return new Claims(claimsSet);
        } catch (Exception e) {
            throw new JwtVerificationException("Token validation failed", e);
        }
    }
}
```

### 3. IAM Policy to Azure RBAC Conversion

#### Replace RolePolicyRepository.java
```java
// AWS IAM Implementation (BEFORE)
public class RolePolicyRepository {
    private final AmazonIdentityManagement iamClient;
    
    public Policy getPolicy(String roleName) {
        GetRolePolicyRequest request = new GetRolePolicyRequest()
            .withRoleName(roleName)
            .withPolicyName("ApiGatewayFullAccess");
            
        GetRolePolicyResult result = iamClient.getRolePolicy(request);
        return Policy.fromJson(result.getPolicyDocument());
    }
}

// Azure RBAC Implementation (AFTER)
public class AzureRBACPolicyRepository {
    private final AuthorizationManager authManager;
    
    public AzureRBACPolicy getPolicy(String principalId, List<String> groups) {
        // Map AWS IAM policy to Azure RBAC
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        // For ApiGatewayFullAccess group
        if (groups.contains("ApiGatewayFullAccess")) {
            RoleAssignment assignment = authManager.roleAssignments()
                .define(UUID.randomUUID().toString())
                .forPrincipal(principalId)
                .withBuiltInRole(BuiltInRole.API_MANAGEMENT_SERVICE_CONTRIBUTOR)
                .withScope("/subscriptions/{sub}/resourceGroups/authserver-rg")
                .create();
                
            policy.addRoleAssignment(assignment);
        }
        
        return policy;
    }
}
```

#### Update PolicyBuilder.java
```java
// AWS IAM Policy Builder (BEFORE)
public class PolicyBuilder {
    public Policy buildPolicy(Claims claims) {
        Policy policy = new Policy();
        Statement statement = new Statement(Effect.Allow);
        
        statement.setActions(Arrays.asList(
            new Action("execute-api:Invoke")
        ));
        
        statement.setResources(Arrays.asList(
            new Resource("arn:aws:execute-api:*:*:*")
        ));
        
        policy.setStatements(Arrays.asList(statement));
        return policy;
    }
}

// Azure RBAC Policy Builder (AFTER)
public class AzureRBACPolicyBuilder {
    public AzureRBACPolicy buildPolicy(Claims claims) {
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        // Map AWS actions to Azure operations
        Map<String, String> actionMappings = Map.of(
            "execute-api:Invoke", "Microsoft.ApiManagement/service/gateways/action",
            "s3:GetObject", "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read",
            "logs:CreateLogStream", "Microsoft.Insights/logs/write"
        );
        
        for (String group : claims.getGroups()) {
            if ("ApiGatewayFullAccess".equals(group)) {
                AzureRoleAssignment assignment = new AzureRoleAssignment();
                assignment.setPrincipal(claims.getUsername());
                assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
                assignment.addScope("/subscriptions/{sub}/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/*");
                
                policy.addRoleAssignment(assignment);
            }
        }
        
        return policy;
    }
}
```

### 4. Environment Variable Migration

#### Update Factory Classes
```java
// AWS Environment Variables (BEFORE)
public class Factory {
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String COGNITO_USER_POOL_ID = System.getenv("AWS_COGNITO_USER_POOL_ID");
    private static final String COGNITO_CLIENT_ID = System.getenv("AWS_COGNITO_CLIENT_ID");
    
    public static CognitoUserPool createCognitoUserPool() {
        AWSCognitoIdentityProviderClient client = AWSCognitoIdentityProviderClientBuilder
            .standard()
            .withRegion(AWS_REGION)
            .build();
            
        return new CognitoUserPool(client, COGNITO_USER_POOL_ID, COGNITO_CLIENT_ID);
    }
}

// Azure Environment Variables (AFTER)
public class AzureFactory {
    private static final String AZURE_REGION = System.getenv("AZURE_REGION");
    private static final String B2C_TENANT_ID = System.getenv("B2C_TENANT_ID");
    private static final String B2C_CLIENT_ID = System.getenv("B2C_CLIENT_ID");
    private static final String KEY_VAULT_URL = System.getenv("KEY_VAULT_URL");
    
    public static B2CUserPoolAdapter createB2CUserPool() {
        // Use Managed Identity instead of explicit credentials
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        
        String authority = String.format("https://%s.b2clogin.com/%s/B2C_1_client_credentials", 
            B2C_TENANT_ID.split("\\.")[0], B2C_TENANT_ID);
            
        ConfidentialClientApplication app = ConfidentialClientApplication.builder(
            B2C_CLIENT_ID, 
            ClientCredentialFactory.createFromSecret(getClientSecretFromKeyVault()))
            .authority(authority)
            .build();
            
        return new B2CUserPoolAdapter(app, B2C_TENANT_ID, B2C_CLIENT_ID);
    }
    
    private static String getClientSecretFromKeyVault() {
        // Retrieve from Azure Key Vault using Managed Identity
        SecretClient secretClient = new SecretClientBuilder()
            .vaultUrl(KEY_VAULT_URL)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
            
        return secretClient.getSecret("b2c-client-secret").getValue();
    }
}
```

### 5. Error Handling Adaptation

#### Convert AWS Exceptions to Azure Exceptions
```java
// AWS Exception Handling (BEFORE)
try {
    AdminInitiateAuthResult result = cognitoClient.adminInitiateAuth(request);
} catch (NotAuthorizedException e) {
    throw new UserPoolException("Invalid credentials", e);
} catch (UserNotFoundException e) {
    throw new UserPoolException("User not found", e);
} catch (AWSCognitoIdentityProviderException e) {
    throw new UserPoolException("Authentication service error", e);
}

// Azure Exception Handling (AFTER)
try {
    IAuthenticationResult result = msalApp.acquireToken(parameters).get();
} catch (MsalServiceException e) {
    if (e.errorCode().equals("invalid_grant")) {
        throw new UserPoolException("Invalid credentials", e);
    } else if (e.errorCode().equals("user_not_found")) {
        throw new UserPoolException("User not found", e);
    } else {
        throw new UserPoolException("Authentication service error", e);
    }
} catch (Exception e) {
    throw new UserPoolException("Authentication failed", e);
}
```

### 6. Logging and Monitoring

#### Convert CloudWatch to Azure Monitor
```java
// AWS CloudWatch Logging (BEFORE)
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class ProxyRequestHandler {
    public ProxyResponse handleRequest(ProxyRequest request, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Processing OAuth token request");
        
        // Custom metrics
        CloudWatchMetrics.putMetric("TokenRequests", 1.0);
    }
}

// Azure Application Insights (AFTER)
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;

public class AuthServerFunction {
    private static final TelemetryClient telemetryClient = new TelemetryClient();
    
    @FunctionName("oauth-token")
    public HttpResponseMessage oauthToken(HttpRequestMessage<Optional<String>> request, 
                                         ExecutionContext context) {
        context.getLogger().info("Processing OAuth token request");
        
        // Custom metrics
        telemetryClient.trackMetric("TokenRequests", 1.0);
        
        // Request telemetry
        RequestTelemetry requestTelemetry = new RequestTelemetry();
        requestTelemetry.setName("oauth-token");
        requestTelemetry.setUrl(request.getUri().toString());
        telemetryClient.trackRequest(requestTelemetry);
    }
}
```

### 7. Backward Compatibility Adapters

#### API Response Format Preservation
```java
@Component
public class ResponseFormatAdapter {
    
    public HttpResponseMessage createCompatibleResponse(
            HttpRequestMessage<?> request, 
            Object responseBody, 
            HttpStatus status) {
        
        // Maintain exact AWS API Gateway response format
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", status.value());
        response.put("headers", createCompatibleHeaders());
        response.put("body", JsonUtils.toJson(responseBody));
        response.put("isBase64Encoded", false);
        
        return request.createResponseBuilder(status)
            .header("Content-Type", "application/json")
            .body(JsonUtils.toJson(response))
            .build();
    }
    
    private Map<String, String> createCompatibleHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        return headers;
    }
}
```

#### JWT Claims Compatibility
```java
public class ClaimsCompatibilityAdapter {
    
    public Claims adaptB2CClaimsToAwsFormat(JWTClaimsSet b2cClaims) {
        Claims awsCompatibleClaims = new Claims();
        
        // Map B2C claims to AWS Cognito format
        awsCompatibleClaims.setUsername(b2cClaims.getStringClaim("preferred_username"));
        awsCompatibleClaims.setGroups(b2cClaims.getStringListClaim("groups"));
        awsCompatibleClaims.setAudience(b2cClaims.getAudience());
        awsCompatibleClaims.setIssuer(b2cClaims.getIssuer());
        awsCompatibleClaims.setExpiration(b2cClaims.getExpirationTime());
        awsCompatibleClaims.setIssuedAt(b2cClaims.getIssueTime());
        awsCompatibleClaims.setSubject(b2cClaims.getSubject());
        
        // Preserve any custom claims
        for (String claimName : b2cClaims.getClaims().keySet()) {
            if (!isStandardClaim(claimName)) {
                awsCompatibleClaims.addCustomClaim(claimName, b2cClaims.getClaim(claimName));
            }
        }
        
        return awsCompatibleClaims;
    }
}
```

## Output Format
For each file you modify, provide:

```markdown
## File: [filename]
### Changes Made
- [List of specific changes]

### AWS SDK Replacements
- [Old AWS SDK call] → [New Azure SDK call]

### Environment Variables Updated
- [Old AWS env var] → [New Azure env var]

### Testing Considerations
- [Unit test updates needed]
- [Integration test changes]

### Code Diff Summary
```java
// Key changes highlighted
```
```

## Success Criteria
- All AWS SDK imports removed and replaced with Azure equivalents
- Azure Functions annotations properly implemented
- Azure AD B2C integration working with identical JWT claim structure
- Azure RBAC policy generation maintaining equivalent permissions
- Environment variables converted to Azure-specific values
- Managed Identity authentication implemented
- Error handling preserved with Azure-specific exceptions
- Logging migrated to Azure Application Insights
- Unit tests updated and passing
- **Backward compatibility maintained**: Existing clients work without changes

## Critical Requirements
- **Functional Parity**: Identical `/oauth/token` and `/change-password` behavior
- **Security Parity**: RS256 JWT signing maintained with B2C
- **Performance**: No degradation in response times (≤200ms p95, ≤500ms cold start)
- **Backward Compatibility**: Existing clients continue working without changes
- **Claims Compatibility**: JWT tokens have identical claim structure
- **Error Response Compatibility**: Same HTTP status codes and error message formats

## Testing Strategy
After each code change:
1. Run existing unit tests (should pass with minimal changes)
2. Test OAuth client credentials flow with B2C
3. Test JWT validation with B2C JWKS endpoint
4. Test Basic Auth fallback with B2C authentication
5. Verify error response formats match AWS API Gateway
6. Check logging output in Azure Application Insights
7. Validate performance targets (≤200ms p95, ≤500ms cold start)
8. Test with existing client applications (no changes required)

## Risk Mitigation
- **Adapter Pattern**: Use adapters to maintain AWS Lambda handler interfaces
- **Incremental Migration**: Make changes one function at a time
- **Feature Flags**: Implement toggles for gradual rollout
- **Comprehensive Testing**: Test each component thoroughly before proceeding
- **Rollback Plan**: Maintain ability to revert to AWS implementation
- **Documentation**: Document all breaking changes (should be none for clients)
- **Monitoring**: Enhanced logging during migration to catch issues early

## High-Risk Areas to Address
1. **Cognito AdminInitiateAuth → B2C Client Credentials**: Different authentication patterns
2. **JWKS URL Changes**: Cognito format vs B2C format endpoints
3. **IAM Policy → RBAC Translation**: Different permission models and syntax
4. **Lambda Context → Azure ExecutionContext**: Different execution context objects
5. **Error Response Formats**: Ensure identical HTTP status codes and JSON structure 