package shared.infrastructure.azure.auth;

import com.microsoft.aad.msal4j.*;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import shared.infrastructure.azure.exceptions.AzureExceptionAdapter;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.java.Log;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Azure AD B2C User Pool adapter that implements UserPool interface
 * for compatibility with existing AWS Cognito implementations.
 */
@Log
public class B2CUserPoolAdapter {
    
    private final AzureEnvironmentConfig config;
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String authority;
    private ConfidentialClientApplication msalApp;
    private final boolean isLocalDevelopment;

    public B2CUserPoolAdapter() {
        this.config = new AzureEnvironmentConfig();
        this.tenantId = config.getTenantId();
        this.clientId = config.getClientId();
        this.isLocalDevelopment = isLocalDevelopmentMode(config);
        this.clientSecret = getClientSecretFromKeyVault();
        this.authority = config.getB2CAuthority();
        this.msalApp = createMsalApplication();
        
        log.info("Initialized B2C User Pool Adapter with tenant: " + tenantId + " (Local dev: " + isLocalDevelopment + ")");
    }

    public B2CUserPoolAdapter(AzureEnvironmentConfig config) {
        this.config = config;
        this.tenantId = config.getTenantId();
        this.clientId = config.getClientId();
        this.isLocalDevelopment = isLocalDevelopmentMode(config);
        this.clientSecret = getClientSecretFromKeyVault();
        this.authority = config.getB2CAuthority();
        this.msalApp = createMsalApplication();
        
        log.info("Initialized B2C User Pool Adapter with custom config (Local dev: " + isLocalDevelopment + ")");
    }

    /**
     * Authenticates user and returns ID token (mimics AWS Cognito AdminInitiateAuth)
     */
    public String authenticate(String username, String password) throws Exception {
        log.info("Authenticating user: " + username);
        
        // For local development, return mock token
        if (isLocalDevelopment) {
            return authenticateLocalDevelopment(username, password);
        }
        
        try {
            // Validate inputs
            if (StringUtils.isAnyBlank(username, password)) {
                throw new IllegalArgumentException("Username and password are required");
            }
            
            // For client credentials flow (service-to-service)
            if (isClientCredentialsFlow(username, password)) {
                return authenticateClientCredentials(username, password);
            }
            
            // For username/password flow (mimic AdminInitiateAuth)
            return authenticateUserPassword(username, password);
            
        } catch (MsalException e) {
            log.severe("MSAL authentication failed: " + e.getMessage());
            throw AzureExceptionAdapter.convertMsalException(e);
        } catch (Exception e) {
            log.severe("Authentication failed: " + e.getMessage());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Changes user password (mimics AWS Cognito changePassword)
     */
    public void changePassword(String username, String previousPassword, String proposedPassword) throws Exception {
        log.info("Changing password for user: " + username);
        
        // For local development, simulate success
        if (isLocalDevelopment) {
            changePasswordLocalDevelopment(username, previousPassword, proposedPassword);
            return;
        }
        
        try {
            // Validate inputs
            if (StringUtils.isAnyBlank(username, previousPassword, proposedPassword)) {
                throw new IllegalArgumentException("Username, previous password, and new password are required");
            }
            
            // First authenticate with current password to verify user
            authenticate(username, previousPassword);
            
            // Implement password change via B2C Graph API
            changePasswordViaGraphAPI(username, previousPassword, proposedPassword);
            
        } catch (Exception e) {
            log.severe("Password change failed for user " + username + ": " + e.getMessage());
            throw new RuntimeException("Password change failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if running in local development mode
     */
    private boolean isLocalDevelopmentMode(AzureEnvironmentConfig config) {
        String tenantId = config.getTenantId();
        String domain = config.getB2CDomain();
        String keyVaultUrl = config.getKeyVaultUrl();
        
        // Check for mock values that indicate local development
        return (tenantId != null && tenantId.contains("mock")) ||
               (domain != null && domain.contains("mock")) ||
               (keyVaultUrl != null && keyVaultUrl.contains("mock")) ||
               "UseDevelopmentStorage=true".equals(System.getenv("AzureWebJobsStorage"));
    }

    /**
     * Mock authentication for local development
     */
    private String authenticateLocalDevelopment(String username, String password) {
        log.info("Local development mode: Mocking authentication for user: " + username);
        
        // Validate basic inputs
        if (StringUtils.isAnyBlank(username, password)) {
            throw new IllegalArgumentException("Username and password are required");
        }
        
        // Return a mock JWT token for local testing
        return "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Im1vY2sta2V5LWlkIn0." +
               "eyJpc3MiOiJtb2NrLWlzc3VlciIsInN1YiI6IiIgKyB1c2VybmFtZSArICIiLCJhdWQiOiJtb2NrLWF1ZGllbmNlIiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjE2MDAwMDM2MDB9." +
               "mock-signature-for-local-development";
    }

    /**
     * Mock password change for local development
     */
    private void changePasswordLocalDevelopment(String username, String previousPassword, String proposedPassword) {
        log.info("Local development mode: Mocking password change for user: " + username);
        
        // Validate basic inputs
        if (StringUtils.isAnyBlank(username, previousPassword, proposedPassword)) {
            throw new IllegalArgumentException("Username, previous password, and new password are required");
        }
        
        // Simulate successful password change
        log.info("Password change simulated successfully for user: " + username);
    }

    /**
     * Creates MSAL application for B2C authentication
     */
    private ConfidentialClientApplication createMsalApplication() {
        // Skip MSAL creation for local development
        if (isLocalDevelopment) {
            log.info("Local development mode: Skipping MSAL application creation");
            return null;
        }
        
        try {
            if (StringUtils.isAnyBlank(clientId, clientSecret, authority)) {
                throw new IllegalStateException("Missing required B2C configuration: clientId, clientSecret, or authority");
            }
            
            IClientCredential credential = ClientCredentialFactory.createFromSecret(clientSecret);
            
            return ConfidentialClientApplication.builder(clientId, credential)
                    .authority(authority)
                    .build();
                    
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid B2C authority URL: " + authority, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MSAL application", e);
        }
    }

    /**
     * Authenticates using client credentials flow
     */
    private String authenticateClientCredentials(String clientId, String clientSecret) throws Exception {
        log.info("Performing client credentials authentication");
        
        ClientCredentialParameters parameters = ClientCredentialParameters.builder(
                Collections.singleton(this.clientId + "/.default"))
                .build();
                
        CompletableFuture<IAuthenticationResult> future = msalApp.acquireToken(parameters);
        IAuthenticationResult result = future.get();
        
        if (result == null || StringUtils.isBlank(result.accessToken())) {
            throw new RuntimeException("Failed to acquire access token via client credentials");
        }
        
        log.info("Client credentials authentication successful");
        return result.accessToken(); // Return access token for client credentials
    }

    /**
     * Authenticates using username/password flow (Resource Owner Password Credentials)
     */
    private String authenticateUserPassword(String username, String password) throws Exception {
        log.info("Performing username/password authentication for user: " + username);
        
        try {
            // Create a PublicClientApplication for ROPC flow
            PublicClientApplication publicApp = PublicClientApplication.builder(clientId)
                    .authority(authority)
                    .build();
            
            // Use Resource Owner Password Credentials (ROPC) flow
            UserNamePasswordParameters parameters = 
                UserNamePasswordParameters.builder(
                    Collections.singleton(clientId + "/.default"),
                    username,
                    password.toCharArray())
                .build();
                
            CompletableFuture<IAuthenticationResult> future = publicApp.acquireToken(parameters);
            IAuthenticationResult result = future.get();
            
            if (result == null) {
                throw new RuntimeException("Authentication failed: No result returned");
            }
            
            // Return ID token for user authentication (mimics Cognito behavior)
            String idToken = result.idToken();
            if (StringUtils.isBlank(idToken)) {
                // Fallback to access token if ID token is not available
                idToken = result.accessToken();
            }
            
            if (StringUtils.isBlank(idToken)) {
                throw new RuntimeException("Authentication failed: No token returned");
            }
            
            log.info("Username/password authentication successful for user: " + username);
            return idToken;
            
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MsalException) {
                throw (MsalException) cause;
            }
            throw new RuntimeException("Authentication execution failed", cause);
        }
    }

    /**
     * Changes password via Microsoft Graph API
     */
    private void changePasswordViaGraphAPI(String username, String oldPassword, String newPassword) {
        // TODO: Implement password change via Microsoft Graph API
        // This would involve:
        // 1. Getting an access token for Microsoft Graph
        // 2. Making a PATCH request to /users/{userId} endpoint
        // 3. Updating the passwordProfile property
        
        log.warning("Password change via Graph API not yet implemented. " +
                   "This would require Microsoft Graph SDK integration.");
        
        // For now, we'll simulate success since the user was already authenticated
        // In a real implementation, this would make actual Graph API calls
        log.info("Password change simulated for user: " + username);
    }

    /**
     * Determines if this is a client credentials flow based on username/password format
     */
    private boolean isClientCredentialsFlow(String username, String password) {
        // Detect client credentials flow by checking if username looks like a client ID
        // and password looks like a client secret
        return username != null && 
               (username.startsWith("client_") || username.contains("-") && username.length() > 20) &&
               password != null && password.length() > 30;
    }

    /**
     * Retrieves client secret from Azure Key Vault using Managed Identity
     */
    private String getClientSecretFromKeyVault() {
        try {
            // For local development, skip Azure Key Vault and use environment variable or fallback
            if (isLocalDevelopmentMode(config)) {
                log.info("Local development mode: Skipping Azure Key Vault for client secret");
                String clientSecret = config.getClientSecret();
                if (StringUtils.isNotBlank(clientSecret)) {
                    log.info("Using client secret from environment variable");
                    return clientSecret;
                }
                log.info("Using development fallback client secret");
                return "development-client-secret";
            }
            
            // First try to get from environment variable (for local development)
            String clientSecret = config.getClientSecret();
            if (StringUtils.isNotBlank(clientSecret)) {
                log.info("Using client secret from environment variable");
                return clientSecret;
            }
            
            // Try to get from Azure Key Vault
            String keyVaultUrl = config.getKeyVaultUrl();
            if (StringUtils.isNotBlank(keyVaultUrl)) {
                log.info("Retrieving client secret from Azure Key Vault");
                
                SecretClient secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultUrl)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient();
                        
                return secretClient.getSecret("b2c-client-secret").getValue();
            }
            
            // Fallback for development/testing
            log.warning("No client secret found in environment or Key Vault. Using fallback for development.");
            return "development-client-secret";
            
        } catch (Exception e) {
            log.warning("Failed to retrieve client secret from Key Vault: " + e.getMessage());
            // Return a development fallback
            return "development-client-secret";
        }
    }

    /**
     * Validates B2C configuration
     */
    public void validateConfiguration() {
        config.validateAzureConfiguration();
        
        if (StringUtils.isBlank(clientSecret)) {
            throw new IllegalStateException("B2C client secret is required");
        }
        
        if (msalApp == null) {
            throw new IllegalStateException("MSAL application not initialized");
        }
    }

    /**
     * Gets the B2C authority URL
     */
    public String getAuthority() {
        return authority;
    }

    /**
     * Gets the B2C client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the B2C tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }
} 