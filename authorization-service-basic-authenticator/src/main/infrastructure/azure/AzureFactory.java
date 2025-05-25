package basic.infrastructure.azure;

import basic.core.HttpBasicAuthenticator;
import basic.core.UserPool;
import basic.infrastructure.azure.auth.AzureB2CUserPool;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import lombok.Getter;

import java.util.Map;

/**
 * Azure Factory for the basic authenticator module.
 * Creates Azure AD B2C components while maintaining compatibility with AWS interfaces.
 */
public class AzureFactory {

    @Getter(lazy = true)
    private static final AzureFactory instance = new AzureFactory();

    private final AzureEnvironmentConfig azureConfig;
    private final UserPool userPool;

    private AzureFactory() {
        this(System.getenv());
    }

    AzureFactory(Map<String, String> env) {
        this.azureConfig = new AzureEnvironmentConfig(env);
        this.userPool = createAzureB2CUserPool();
    }

    public HttpBasicAuthenticator createHttpBasicAuthenticator() {
        return new HttpBasicAuthenticator(userPool);
    }

    /**
     * Creates Azure B2C UserPool instance
     */
    private UserPool createAzureB2CUserPool() {
        return new AzureB2CUserPool(azureConfig);
    }

    /**
     * Gets the Azure configuration
     */
    public AzureEnvironmentConfig getAzureConfig() {
        return azureConfig;
    }

    /**
     * Gets the UserPool instance
     */
    public UserPool getUserPool() {
        return userPool;
    }
} 