package shared.infrastructure.azure.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Azure RBAC Policy representation that can be converted to AWS IAM policy format
 * for backward compatibility with existing authorization logic.
 */
@Data
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
                case "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write":
                    awsActions.add("s3:PutObject");
                    break;
                case "Microsoft.KeyVault/vaults/secrets/read":
                    awsActions.add("secretsmanager:GetSecretValue");
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
        } else if (azureScope.contains("Microsoft.KeyVault")) {
            return "arn:aws:secretsmanager:*:*:secret:*";
        }
        
        return "*"; // Fallback
    }
} 