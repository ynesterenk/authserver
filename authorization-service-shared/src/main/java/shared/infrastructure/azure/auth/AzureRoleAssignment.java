package shared.infrastructure.azure.auth;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents an Azure RBAC role assignment with principal, operations, and scope.
 */
@Data
public class AzureRoleAssignment {
    private String principal;
    private List<String> operations;
    private String scope;
    private List<String> groups;
    
    public AzureRoleAssignment() {
        this.operations = new ArrayList<>();
        this.groups = new ArrayList<>();
    }
    
    // Getters and setters
    public void setPrincipal(String principal) { 
        this.principal = principal; 
    }
    
    public String getPrincipal() { 
        return principal; 
    }
    
    public void addOperation(String operation) { 
        this.operations.add(operation); 
    }
    
    public List<String> getOperations() { 
        return operations; 
    }
    
    public void setScope(String scope) { 
        this.scope = scope; 
    }
    
    public String getScope() { 
        return scope; 
    }
    
    public void setGroups(List<String> groups) { 
        this.groups = groups; 
    }
    
    public List<String> getGroups() { 
        return groups; 
    }
} 