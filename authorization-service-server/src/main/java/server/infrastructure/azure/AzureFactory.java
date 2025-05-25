package server.infrastructure.azure;

import server.core.UserPool;
import server.core.ValidationRules;
import server.core.facade.ChangePasswordFacade;
import server.core.facade.ClientCredentialsFacade;
import server.infrastructure.azure.auth.AzureB2CUserPool;
import shared.core.validation.ErrorState;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import shared.infrastructure.azure.auth.AzureB2CJwtValidator;
import lombok.Getter;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.util.Arrays;
import java.util.Map;

/**
 * Azure Factory for the server module.
 * Creates Azure AD B2C components while maintaining compatibility with AWS interfaces.
 */
public class AzureFactory {

    @Getter(lazy = true)
    private static final AzureFactory instance = new AzureFactory(System.getenv());

    private final AzureEnvironmentConfig azureConfig;
    private final UserPool userPool;
    private final AzureB2CJwtValidator jwtValidator;
    private final VelocityEngine velocityEngine;

    AzureFactory(Map<String, String> env) {
        azureConfig = new AzureEnvironmentConfig(env);
        userPool = createAzureB2CUserPool();
        jwtValidator = createAzureB2CJwtValidator();
        velocityEngine = createVelocityEngine();
    }

    public ClientCredentialsFacade createClientCredentialsFacade() {
        return new ClientCredentialsFacade(
            new ErrorState(),
            userPool,
            Arrays.asList(
                ValidationRules::verifyBody,
                ValidationRules::verifyBasicAuthenticationHeader),
            Arrays.asList(
                ValidationRules::verifyGrantType,
                ValidationRules::verifyClientId,
                ValidationRules::verifyClientSecret));
    }

    public ChangePasswordFacade createChangePasswordFacade() {
        return new ChangePasswordFacade(
            new ErrorState(),
            userPool,
            velocityEngine.getTemplate("public/index.html"),
            Arrays.asList(
                ValidationRules::verifyBody,
                ValidationRules::verifyBasicAuthenticationHeader),
            Arrays.asList(
                ValidationRules::verifyUserName,
                ValidationRules::verifyPreviousPassword,
                ValidationRules::verifyProposedPassword));
    }

    /**
     * Creates Azure B2C UserPool instance
     */
    private UserPool createAzureB2CUserPool() {
        return new AzureB2CUserPool(azureConfig);
    }

    /**
     * Creates Azure B2C JWT Validator instance
     */
    private AzureB2CJwtValidator createAzureB2CJwtValidator() {
        return new AzureB2CJwtValidator(azureConfig);
    }

    /**
     * Creates Velocity Engine for template processing
     */
    private VelocityEngine createVelocityEngine() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        return velocityEngine;
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

    /**
     * Gets the JWT Validator instance
     */
    public AzureB2CJwtValidator getJwtValidator() {
        return jwtValidator;
    }

    /**
     * Gets the Velocity Engine instance
     */
    public VelocityEngine getVelocityEngine() {
        return velocityEngine;
    }
} 