# Step 3: Azure AD B2C Integration

## Context
This is **Step 3 of 6** in the AWS to Azure migration. You are replacing AWS Cognito User Pool with Azure AD B2C while maintaining identical authentication behavior.

**Prerequisites**: Steps 1-2 must be completed.

**Goal**: Replace Cognito authentication with Azure AD B2C using MSAL4J while preserving exact API behavior.

## What You're Migrating
- **Source**: AWS Cognito User Pool with AdminInitiateAuth flow
- **Target**: Azure AD B2C with custom policies and MSAL4J
- **Critical**: Maintain identical JWT token structure and claims

## Dependencies to Address
Based on the AWS analysis:
- `AWSCognitoIdentityProviderClient` → `ConfidentialClientApplication` (MSAL4J)
- `AdminInitiateAuth` flow → B2C client credentials flow
- Cognito JWKS URL → B2C JWKS URL
- `NEW_PASSWORD_REQUIRED` challenge handling

## Your Tasks

### 1. Create B2C User Pool Adapter

Create `B2CUserPoolAdapter.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/auth/`:

```java
package shared.infrastructure.azure.auth;

import com.microsoft.aad.msal4j.*;
import shared.domain.UserPool;
import shared.domain.exceptions.UserPoolException;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import shared.infrastructure.azure.exceptions.AzureExceptionAdapter;

public class B2CUserPoolAdapter implements UserPool {
    private final ConfidentialClientApplication msalApp;
    private final String tenantId;
    private final String clientId;
    
    public B2CUserPoolAdapter() {
        this.tenantId = AzureEnvironmentConfig.B2C_TENANT_ID;
        this.clientId = AzureEnvironmentConfig.B2C_CLIENT_ID;
        this.msalApp = createMsalApplication();
    }
    
    private ConfidentialClientApplication createMsalApplication() {
        try {
            String authority = AzureEnvironmentConfig.getB2CAuthority();
            String clientSecret = getClientSecretFromKeyVault();
            
            IClientCredential credential = ClientCredentialFactory.createFromSecret(clientSecret);
            
            return ConfidentialClientApplication.builder(clientId, credential)
                .authority(authority)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MSAL application", e);
        }
    }
    
    @Override
    public String authenticate(String username, String password) throws UserPoolException {
        try {
            // For client credentials flow (service-to-service)
            if (isClientCredentialsFlow(username, password)) {
                return authenticateClientCredentials(username, password);
            }
            
            // For username/password flow (mimic AdminInitiateAuth)
            return authenticateUserPassword(username, password);
            
        } catch (MsalException e) {
            throw AzureExceptionAdapter.convertMsalException(e);
        } catch (Exception e) {
            throw new UserPoolException("Authentication failed", e);
        }
    }
    
    private String authenticateClientCredentials(String clientId, String clientSecret) throws Exception {
        ClientCredentialParameters parameters = ClientCredentialParameters.builder(
            Collections.singleton(this.clientId + "/.default"))
            .build();
            
        CompletableFuture<IAuthenticationResult> future = msalApp.acquireToken(parameters);
        IAuthenticationResult result = future.get();
        
        return result.accessToken(); // Return access token for client credentials
    }
    
    private String authenticateUserPassword(String username, String password) throws Exception {
        // Mimic AdminInitiateAuth behavior with username/password
        UserNamePasswordParameters parameters = 
            UserNamePasswordParameters.builder(
                Collections.singleton(clientId + "/.default"),
                username,
                password.toCharArray())
            .build();
            
        CompletableFuture<IAuthenticationResult> future = msalApp.acquireToken(parameters);
        IAuthenticationResult result = future.get();
        
        return result.idToken(); // Return ID token for user authentication
    }
    
    @Override
    public void changePassword(String username, String previousPassword, String proposedPassword) 
            throws UserPoolException {
        try {
            // Implement password change via B2C Graph API
            // This requires Microsoft Graph SDK integration
            changePasswordViaGraphAPI(username, previousPassword, proposedPassword);
            
        } catch (Exception e) {
            throw new UserPoolException("Password change failed", e);
        }
    }
    
    private void changePasswordViaGraphAPI(String username, String oldPassword, String newPassword) {
        // Implementation using Microsoft Graph API
        // Handle NEW_PASSWORD_REQUIRED challenge equivalent
        // This would involve Graph API calls to update user password
    }
    
    private boolean isClientCredentialsFlow(String username, String password) {
        // Detect if this is client credentials vs user password flow
        // Based on the format of username/password parameters
        return username != null && username.startsWith("client_") && password != null;
    }
    
    private String getClientSecretFromKeyVault() {
        // Retrieve from Azure Key Vault using Managed Identity
        SecretClient secretClient = new SecretClientBuilder()
            .vaultUrl(AzureEnvironmentConfig.KEY_VAULT_URL)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
            
        return secretClient.getSecret("b2c-client-secret").getValue();
    }
}
```

### 2. Create Azure B2C JWT Validator

Create `AzureB2CJwtValidator.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/auth/`:

```java
package shared.infrastructure.azure.auth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.JWTClaimsSet;
import shared.domain.Claims;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;

public class AzureB2CJwtValidator {
    private final String tenantName;
    private final String tenantId; 
    private final String policyName;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    
    public AzureB2CJwtValidator() {
        this.tenantId = AzureEnvironmentConfig.B2C_TENANT_ID;
        this.policyName = AzureEnvironmentConfig.B2C_POLICY_NAME;
        this.tenantName = tenantId.split("\\.")[0]; // Extract tenant name
        this.jwtProcessor = createJWTProcessor();
    }
    
    private ConfigurableJWTProcessor<SecurityContext> createJWTProcessor() {
        try {
            // Azure AD B2C JWKS endpoint format
            String jwksUrl = AzureEnvironmentConfig.getB2CJwksUrl();
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUrl));
            
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWKSource(jwkSource);
            processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));
            
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
            JWSKeySelector<SecurityContext> keySelector = 
                new JWSVerificationKeySelector<>(expectedJWSAlg, jwkSource);
            processor.setJWSKeySelector(keySelector);
            
            return processor;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT processor", e);
        }
    }
    
    public Claims validateToken(String token) throws JwtVerificationException {
        try {
            SecurityContext ctx = null;
            JWTClaimsSet claimsSet = jwtProcessor.process(token, ctx);
            
            // Convert B2C claims to AWS Cognito-compatible format
            return adaptB2CClaimsToAwsFormat(claimsSet);
            
        } catch (Exception e) {
            throw new JwtVerificationException("Token validation failed", e);
        }
    }
    
    private Claims adaptB2CClaimsToAwsFormat(JWTClaimsSet b2cClaims) {
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
    
    private boolean isStandardClaim(String claimName) {
        return Arrays.asList("iss", "sub", "aud", "exp", "iat", "preferred_username", "groups")
            .contains(claimName);
    }
}
```

### 3. Update Factory Classes

Update existing Factory classes to use B2C instead of Cognito:

```java
// Update authorization-service-shared Factory classes
public class AzureFactory {
    
    public static UserPool createUserPool() {
        return new B2CUserPoolAdapter();
    }
    
    public static JwtValidator createJwtValidator() {
        return new AzureB2CJwtValidator();
    }
    
    // Keep existing factory methods but use Azure implementations
}
```

## Success Criteria
- ✅ B2C authentication produces identical JWT token structure to Cognito
- ✅ OAuth client credentials flow works with B2C
- ✅ Password change functionality implemented
- ✅ JWT validation works with B2C JWKS endpoint
- ✅ Claims mapping preserves AWS Cognito claim structure
- ✅ Error handling maintains AWS error message compatibility
- ✅ All existing unit tests pass without modification
- ✅ Integration tests verify B2C connectivity

## Testing Commands
```bash
# Run authentication tests
cd authorization-service-shared
mvn clean test -Dtest=*UserPool*

# Test B2C JWT validation
mvn test -Dtest=*JwtValidator*

# Integration test with B2C
mvn test -Dtest=*Integration*
```

## Next Steps
After completing this step:
1. Cognito is fully replaced with Azure AD B2C
2. Step 4 can begin migrating JWT Authorizer function
3. Authentication flow is Azure-native but API-compatible
4. JWT validation uses B2C JWKS endpoint

## Risk Mitigation
- **Claims Compatibility**: Exact mapping of B2C claims to Cognito format
- **Token Structure**: Preserve JWT structure for client compatibility
- **Error Messages**: Maintain AWS error message format
- **Fallback Strategy**: Keep Cognito adapter available for rollback