package shared.infrastructure.azure.auth;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;

public class AzureRBACPolicyTest {

    private AzureRBACPolicy policy;
    private ObjectMapper objectMapper;

    @BeforeMethod
    public void setUp() {
        policy = new AzureRBACPolicy();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testAddRoleAssignment() {
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal("testuser");
        assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment.setScope("/subscriptions/test/resourceGroups/test-rg");
        
        policy.addRoleAssignment(assignment);
        
        Assert.assertEquals(policy.getRoleAssignments().size(), 1);
        Assert.assertEquals(policy.getRoleAssignments().get(0).getPrincipal(), "testuser");
    }

    @Test
    public void testToAwsCompatiblePolicyDocument() {
        // Create role assignment for API Gateway access
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal("testuser");
        assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment.setScope("/subscriptions/test/resourceGroups/test-rg/providers/Microsoft.ApiManagement/service/test-apim");
        
        policy.addRoleAssignment(assignment);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        Assert.assertNotNull(policyDocument);
        Assert.assertTrue(policyDocument.contains("2012-10-17"));
        Assert.assertTrue(policyDocument.contains("execute-api:Invoke"));
        Assert.assertTrue(policyDocument.contains("Allow"));
    }

    @Test
    public void testToAwsCompatiblePolicyDocumentWithMultipleOperations() {
        // Create role assignment for S3 access
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal("testuser");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
        assignment.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write");
        assignment.setScope("/subscriptions/test/resourceGroups/test-rg/providers/Microsoft.Storage/storageAccounts/test-storage");
        
        policy.addRoleAssignment(assignment);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        Assert.assertNotNull(policyDocument);
        Assert.assertTrue(policyDocument.contains("s3:GetObject"));
        Assert.assertTrue(policyDocument.contains("s3:PutObject"));
    }

    @Test
    public void testToAwsCompatiblePolicyDocumentWithKeyVaultAccess() {
        // Create role assignment for Key Vault access
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal("testuser");
        assignment.addOperation("Microsoft.KeyVault/vaults/secrets/read");
        assignment.setScope("/subscriptions/test/resourceGroups/test-rg/providers/Microsoft.KeyVault/vaults/test-vault");
        
        policy.addRoleAssignment(assignment);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        Assert.assertNotNull(policyDocument);
        Assert.assertTrue(policyDocument.contains("secretsmanager:GetSecretValue"));
        Assert.assertTrue(policyDocument.contains("arn:aws:secretsmanager"));
    }

    @Test
    public void testToAwsCompatiblePolicyDocumentWithUnknownOperation() {
        // Create role assignment with unknown operation
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal("testuser");
        assignment.addOperation("Microsoft.Unknown/service/action");
        assignment.setScope("/subscriptions/test/resourceGroups/test-rg");
        
        policy.addRoleAssignment(assignment);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        Assert.assertNotNull(policyDocument);
        Assert.assertTrue(policyDocument.contains("*")); // Fallback action
    }

    @Test
    public void testEmptyPolicy() {
        // Test empty policy
        Assert.assertEquals(policy.getRoleAssignments().size(), 0);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        Assert.assertNotNull(policyDocument);
        Assert.assertTrue(policyDocument.contains("2012-10-17"));
        Assert.assertTrue(policyDocument.contains("Statement"));
    }

    @Test
    public void testPolicyDocumentIsValidJson() throws Exception {
        // Create a policy and verify it produces valid JSON
        AzureRoleAssignment assignment = new AzureRoleAssignment();
        assignment.setPrincipal("testuser");
        assignment.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment.setScope("/subscriptions/test/resourceGroups/test-rg");
        
        policy.addRoleAssignment(assignment);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        // Parse as JSON to verify it's valid
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedPolicy = objectMapper.readValue(policyDocument, Map.class);
        
        Assert.assertNotNull(parsedPolicy);
        Assert.assertEquals(parsedPolicy.get("Version"), "2012-10-17");
        Assert.assertNotNull(parsedPolicy.get("Statement"));
    }

    @Test
    public void testMultipleRoleAssignments() {
        // Create multiple role assignments
        AzureRoleAssignment assignment1 = new AzureRoleAssignment();
        assignment1.setPrincipal("user1");
        assignment1.addOperation("Microsoft.ApiManagement/service/gateways/action");
        assignment1.setScope("/subscriptions/test/resourceGroups/test-rg");
        
        AzureRoleAssignment assignment2 = new AzureRoleAssignment();
        assignment2.setPrincipal("user2");
        assignment2.addOperation("Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read");
        assignment2.setScope("/subscriptions/test/resourceGroups/test-rg");
        
        policy.addRoleAssignment(assignment1);
        policy.addRoleAssignment(assignment2);
        
        Assert.assertEquals(policy.getRoleAssignments().size(), 2);
        
        String policyDocument = policy.toAwsCompatiblePolicyDocument();
        
        Assert.assertNotNull(policyDocument);
        Assert.assertTrue(policyDocument.contains("execute-api:Invoke"));
        Assert.assertTrue(policyDocument.contains("s3:GetObject"));
    }
} 