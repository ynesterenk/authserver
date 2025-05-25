# Code Adaptation Phase - AWS to Azure SDK Migration

## Context
You are converting the Java 11 authserver codebase from AWS SDK to Azure SDK equivalents. This is Phase 3 focusing on code adaptation while maintaining functional parity.

## Migration Targets
- Replace AWS SDK calls with Azure SDK for Java
- Convert Cognito User Pool calls to Azure AD B2C/MSAL
- Adapt IAM policy generation to Azure RBAC
- Convert CloudWatch logging to Azure Monitor
- Maintain identical API contracts and response formats

## Key SDK Replacements

### AWS Cognito → Azure AD B2C/MSAL
```java
// AWS Cognito (BEFORE)
import software.amazon.awssdk.services.cognitoidentityprovider.*;

// Azure MSAL (AFTER)  
import com.microsoft.aad.msal4j.*;
import com.azure.identity.*;
```

### AWS IAM → Azure RBAC
```java
// AWS IAM (BEFORE)
import software.amazon.awssdk.services.iam.*;

// Azure RBAC (AFTER)
import com.azure.resourcemanager.authorization.*;
import com.azure.core.management.policy.*;
```

### AWS CloudWatch → Azure Monitor
```java
// AWS CloudWatch (BEFORE)
import software.amazon.awssdk.services.cloudwatch.*;

// Azure Monitor (AFTER)
import com.azure.monitor.query.*;
import com.azure.core.util.logging.*;
```

## Your Tasks

### 1. Lambda Handler Adaptation
Convert AWS Lambda handlers to Azure Function handlers:
- Replace `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`
- Use Azure Functions annotations (`@FunctionName`, `@HttpTrigger`)
- Adapt request/response object mapping
- Maintain identical JSON response structures

### 2. Cognito to Azure AD B2C Migration
Replace Cognito User Pool operations:
- **Token Validation**: Convert `CognitoJwtVerifier` to MSAL token validation
- **User Authentication**: Replace Cognito auth flows with B2C custom policies
- **JWT Claims**: Ensure identical claim structure in B2C tokens
- **OIDC Metadata**: Use B2C OIDC discovery endpoint

### 3. IAM Policy to Azure RBAC Conversion
Convert IAM policy generation:
- Replace AWS IAM policy JSON with Azure RBAC role assignments
- Convert resource ARNs to Azure resource IDs
- Map AWS actions to Azure operations
- Maintain equivalent permission levels

### 4. Environment Variable Migration
Convert AWS-specific environment variables:
- `AWS_REGION` → `AZURE_REGION` or function app settings
- `COGNITO_USER_POOL_ID` → `B2C_TENANT_ID`
- `COGNITO_CLIENT_ID` → `B2C_CLIENT_ID`
- AWS credentials → Managed Identity (remove explicit credentials)

### 5. Error Handling Adaptation
Adapt AWS-specific error handling:
- Convert AWS SDK exceptions to Azure SDK exceptions
- Maintain identical HTTP status codes and error messages
- Preserve error response JSON structure
- Add Azure-specific retry policies

### 6. Logging and Monitoring
Convert CloudWatch logging to Azure Monitor:
- Replace CloudWatch log statements with Azure Application Insights
- Convert custom metrics to Azure Monitor metrics
- Maintain log correlation IDs
- Add distributed tracing support

## Code Transformation Examples

### OAuth Token Endpoint
```java
// AWS Lambda Handler (BEFORE)
public class ServerLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final CognitoIdentityProviderClient cognitoClient;
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // AWS-specific implementation
    }
}

// Azure Function (AFTER)
public class AuthServerFunction {
    @FunctionName("oauth-token")
    public HttpResponseMessage oauthToken(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "oauth/token") 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        // Azure-specific implementation
    }
}
```

### JWT Validation
```java
// AWS Cognito JWT Validation (BEFORE)
CognitoJwtVerifier jwtVerifier = CognitoJwtVerifier.create(userPoolId);
Jwt jwt = jwtVerifier.verify(token);

// Azure B2C JWT Validation (AFTER)
ConfidentialClientApplication app = ConfidentialClientApplication.builder(clientId, clientCredential)
    .authority(authority)
    .build();
// Use MSAL for token validation
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
- All AWS SDK imports removed
- Azure SDK equivalents implemented
- Identical API response formats maintained
- Environment variables converted
- Error handling preserved
- Logging migrated to Azure Monitor
- Unit tests updated and passing

## Critical Requirements
- **Functional Parity**: Identical `/oauth/token` and `/oauth/check_token` behavior
- **Security Parity**: RS256 JWT signing maintained
- **Performance**: No degradation in response times
- **Backward Compatibility**: Existing clients continue working without changes

## Testing Strategy
After each code change:
1. Run existing unit tests (should pass with minimal changes)
2. Test OAuth token issuance flow
3. Test JWT validation flow  
4. Test Basic Auth fallback
5. Verify error response formats
6. Check logging output format

## Risk Mitigation
- Make incremental changes, test frequently
- Preserve original AWS code in comments during transition
- Maintain feature flags for gradual rollout
- Document all breaking changes (should be none for clients) 