package shared.infrastructure.azure.config;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class that maps AWS environment variables to Azure equivalents
 * and supports backward compatibility during migration.
 */
public class AzureEnvironmentConfig {

    // Azure AD B2C Configuration
    private static final String AZURE_B2C_TENANT_ID = "AZURE_B2C_TENANT_ID";
    private static final String AZURE_B2C_CLIENT_ID = "AZURE_B2C_CLIENT_ID";
    private static final String AZURE_B2C_CLIENT_SECRET = "AZURE_B2C_CLIENT_SECRET";
    private static final String AZURE_B2C_POLICY_NAME = "AZURE_B2C_POLICY_NAME";
    private static final String AZURE_B2C_DOMAIN = "AZURE_B2C_DOMAIN";

    // AWS Compatibility Environment Variables
    private static final String AWS_REGION = "AWS_REGION";
    private static final String AWS_COGNITO_USER_POOL_ID = "AWS_COGNITO_USER_POOL_ID";
    private static final String AWS_COGNITO_CLIENT_ID = "AWS_COGNITO_CLIENT_ID";

    // Azure Key Vault Configuration
    private static final String AZURE_KEY_VAULT_URL = "AZURE_KEY_VAULT_URL";

    // Application Insights Configuration
    private static final String APPINSIGHTS_INSTRUMENTATIONKEY = "APPINSIGHTS_INSTRUMENTATIONKEY";

    private final Map<String, String> environmentVariables;

    public AzureEnvironmentConfig() {
        this.environmentVariables = new HashMap<>(System.getenv());
    }

    public AzureEnvironmentConfig(Map<String, String> customEnvironment) {
        this.environmentVariables = new HashMap<>(customEnvironment);
    }

    /**
     * Gets Azure B2C Tenant ID with fallback to AWS Cognito User Pool ID
     */
    public String getTenantId() {
        String tenantId = getEnvironmentVariable(AZURE_B2C_TENANT_ID);
        if (StringUtils.isBlank(tenantId)) {
            // Fallback to AWS Cognito User Pool ID for backward compatibility
            tenantId = getEnvironmentVariable(AWS_COGNITO_USER_POOL_ID);
        }
        return tenantId;
    }

    /**
     * Gets Azure B2C Client ID with fallback to AWS Cognito Client ID
     */
    public String getClientId() {
        String clientId = getEnvironmentVariable(AZURE_B2C_CLIENT_ID);
        if (StringUtils.isBlank(clientId)) {
            // Fallback to AWS Cognito Client ID for backward compatibility
            clientId = getEnvironmentVariable(AWS_COGNITO_CLIENT_ID);
        }
        return clientId;
    }

    /**
     * Gets Azure B2C Client Secret
     */
    public String getClientSecret() {
        return getEnvironmentVariable(AZURE_B2C_CLIENT_SECRET);
    }

    /**
     * Gets Azure B2C Policy Name (e.g., B2C_1_signupsignin1)
     */
    public String getPolicyName() {
        return getEnvironmentVariable(AZURE_B2C_POLICY_NAME, "B2C_1_signupsignin1");
    }

    /**
     * Gets Azure B2C Domain (e.g., yourtenant.b2clogin.com)
     */
    public String getB2CDomain() {
        return getEnvironmentVariable(AZURE_B2C_DOMAIN);
    }

    /**
     * Generates Azure B2C Authority URL
     */
    public String getB2CAuthority() {
        String domain = getB2CDomain();
        String tenantId = getTenantId();
        String policyName = getPolicyName();
        
        if (StringUtils.isAnyBlank(domain, tenantId, policyName)) {
            throw new IllegalStateException("Missing required Azure B2C configuration: domain, tenantId, or policyName");
        }
        
        return String.format("https://%s/%s/%s", domain, tenantId, policyName);
    }

    /**
     * Generates Azure B2C JWKS URI
     */
    public String getB2CJwksUri() {
        String authority = getB2CAuthority();
        return authority + "/discovery/v2.0/keys";
    }

    /**
     * Gets Azure Key Vault URL
     */
    public String getKeyVaultUrl() {
        return getEnvironmentVariable(AZURE_KEY_VAULT_URL);
    }

    /**
     * Gets Application Insights Instrumentation Key
     */
    public String getApplicationInsightsKey() {
        return getEnvironmentVariable(APPINSIGHTS_INSTRUMENTATIONKEY);
    }

    /**
     * Gets AWS Region for backward compatibility
     */
    public String getAwsRegion() {
        return getEnvironmentVariable(AWS_REGION, "us-east-1");
    }

    /**
     * Checks if running in Azure environment
     */
    public boolean isAzureEnvironment() {
        return StringUtils.isNotBlank(getEnvironmentVariable(AZURE_B2C_TENANT_ID)) ||
               StringUtils.isNotBlank(getEnvironmentVariable("WEBSITE_SITE_NAME")); // Azure Functions indicator
    }

    /**
     * Checks if running in AWS environment
     */
    public boolean isAwsEnvironment() {
        return StringUtils.isNotBlank(getEnvironmentVariable("AWS_LAMBDA_FUNCTION_NAME")) ||
               StringUtils.isNotBlank(getEnvironmentVariable("AWS_EXECUTION_ENV"));
    }

    /**
     * Gets environment variable with optional default value
     */
    private String getEnvironmentVariable(String key, String defaultValue) {
        String value = environmentVariables.get(key);
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    /**
     * Gets environment variable
     */
    private String getEnvironmentVariable(String key) {
        return environmentVariables.get(key);
    }

    /**
     * Gets all environment variables for debugging
     */
    public Map<String, String> getAllEnvironmentVariables() {
        return new HashMap<>(environmentVariables);
    }

    /**
     * Validates that required Azure configuration is present
     */
    public void validateAzureConfiguration() {
        if (isAzureEnvironment()) {
            if (StringUtils.isBlank(getTenantId())) {
                throw new IllegalStateException("Azure B2C Tenant ID is required");
            }
            if (StringUtils.isBlank(getClientId())) {
                throw new IllegalStateException("Azure B2C Client ID is required");
            }
            if (StringUtils.isBlank(getB2CDomain())) {
                throw new IllegalStateException("Azure B2C Domain is required");
            }
        }
    }
} 