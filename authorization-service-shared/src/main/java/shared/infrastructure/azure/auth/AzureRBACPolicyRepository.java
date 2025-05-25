package shared.infrastructure.azure.auth;

import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import lombok.extern.java.Log;

import java.util.List;
import java.util.Arrays;

/**
 * Azure RBAC Policy Repository that maps user groups to Azure RBAC permissions
 * and generates policies for authorization decisions.
 */
@Log
public class AzureRBACPolicyRepository {
    
    private final AzureEnvironmentConfig config;
    
    public AzureRBACPolicyRepository() {
        this.config = new AzureEnvironmentConfig();
        log.info("Initialized Azure RBAC Policy Repository");
    }
    
    public AzureRBACPolicyRepository(AzureEnvironmentConfig config) {
        this.config = config;
        log.info("Initialized Azure RBAC Policy Repository with custom config");
    }
    
    /**
     * Gets Azure RBAC policy for a user based on their groups
     */
    public AzureRBACPolicy getPolicy(String principalId, List<String> groups) {
        AzureRBACPolicy policy = new AzureRBACPolicy();
        
        if (groups == null || groups.isEmpty()) {
            log.warning("No groups found for user: " + principalId + ", creating minimal policy");
            addMinimalPermissions(policy, principalId);
            return policy;
        }
        
        // Map user groups to Azure RBAC roles
        for (String group : groups) {
            switch (group) {
                case "ApiGatewayFullAccess":
                    addApiManagementPermissions(policy, principalId);
                    break;
                case "S3FullAccess":
                    addStoragePermissions(policy, principalId);
                    break;
                case "SecretsManagerAccess":
                    addKeyVaultPermissions(policy, principalId);
                    break;
                case "ReadOnlyAccess":
                    addReadOnlyPermissions(policy, principalId);
                    break;
                case "AdminAccess":
                    addAdminPermissions(policy, principalId);
                    break;
                default:
                    log.warning("Unknown group: " + group + " for user: " + principalId);
                    addMinimalPermissions(policy, principalId);
            }
        }
        
        log.info("Generated Azure RBAC policy for user: " + principalId + " with groups: " + groups);
        return policy;
    }
    
    private void addApiManagementPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Add API Management permissions
        assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        assignment.addOperation("Microsoft.ApiManagement/service/apis/write");
        assignment.addOperation("Microsoft.ApiManagement/service/policies/read");
        assignment.addOperation("Microsoft.ApiManagement/service/policies/write");
        
        // Scope to the authserver APIM instance
        assignment.setScope(getApiManagementScope());
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addStoragePermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Add Storage permissions
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/delete");
        assignment.addOperation("Microsoft.Storage/storageAccounts/listKeys/action");
        
        // Scope to storage account
        assignment.setScope(getStorageScope());
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addKeyVaultPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Add Key Vault permissions
        assignment.addOperation("Microsoft.KeyVault/vaults/secrets/read");
        assignment.addOperation("Microsoft.KeyVault/vaults/secrets/write");
        assignment.addOperation("Microsoft.KeyVault/vaults/keys/read");
        
        // Scope to Key Vault
        assignment.setScope(getKeyVaultScope());
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addReadOnlyPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Add read-only permissions
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
        assignment.addOperation("Microsoft.KeyVault/vaults/secrets/read");
        assignment.addOperation("Microsoft.Insights/logs/read");
        
        // Scope to resource group
        assignment.setScope(getResourceGroupScope());
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addAdminPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Admin permissions - all operations
        assignment.addOperation("*");
        
        // Scope to resource group
        assignment.setScope(getResourceGroupScope());
        
        policy.addRoleAssignment(assignment);
    }
    
    private void addMinimalPermissions(AzureRBACPolicy policy, String principalId) {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal(principalId);
        
        // Minimal permissions for unknown groups
        assignment.addOperation("Microsoft.ApiManagement/service/apis/read");
        
        // Scope to specific API
        assignment.setScope(getApiManagementScope() + "/apis/oauth-api");
        
        policy.addRoleAssignment(assignment);
    }
    
    private String getApiManagementScope() {
        String subscriptionId = config.getAwsRegion(); // Use AWS region as fallback
        if (subscriptionId == null) {
            subscriptionId = "default-subscription";
        }
        
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.ApiManagement/service/authserver-apim",
            subscriptionId);
    }
    
    private String getStorageScope() {
        String subscriptionId = config.getAwsRegion(); // Use AWS region as fallback
        if (subscriptionId == null) {
            subscriptionId = "default-subscription";
        }
        
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.Storage/storageAccounts/authserverstorage",
            subscriptionId);
    }
    
    private String getKeyVaultScope() {
        String subscriptionId = config.getAwsRegion(); // Use AWS region as fallback
        if (subscriptionId == null) {
            subscriptionId = "default-subscription";
        }
        
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg/providers/Microsoft.KeyVault/vaults/authserver-vault",
            subscriptionId);
    }
    
    private String getResourceGroupScope() {
        String subscriptionId = config.getAwsRegion(); // Use AWS region as fallback
        if (subscriptionId == null) {
            subscriptionId = "default-subscription";
        }
        
        return String.format("/subscriptions/%s/resourceGroups/authserver-rg",
            subscriptionId);
    }
    
    /**
     * Gets policy for a single group (convenience method)
     */
    public AzureRBACPolicy getPolicy(String principalId, String group) {
        return getPolicy(principalId, Arrays.asList(group));
    }
} 