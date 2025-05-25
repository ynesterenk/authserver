package server.infrastructure.azure.auth;

import server.core.UserPoolException;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class AzureB2CUserPoolTest {

    private AzureB2CUserPool azureB2CUserPool;
    private AzureEnvironmentConfig mockConfig;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        
        // Create mock configuration for testing
        Map<String, String> testEnv = new HashMap<>();
        testEnv.put("AZURE_B2C_TENANT_ID", "test-tenant.onmicrosoft.com");
        testEnv.put("AZURE_B2C_CLIENT_ID", "test-client-id");
        testEnv.put("AZURE_B2C_CLIENT_SECRET", "test-client-secret");
        testEnv.put("AZURE_B2C_POLICY_NAME", "B2C_1_signupsignin1");
        testEnv.put("AZURE_B2C_DOMAIN", "test-tenant.b2clogin.com");
        
        mockConfig = new AzureEnvironmentConfig(testEnv);
        azureB2CUserPool = new AzureB2CUserPool(mockConfig);
    }

    @Test
    public void testAuthenticateMethodExists() {
        // This test verifies that the authenticate method exists and can be called
        // The actual B2C authentication would require real Azure B2C configuration
        
        try {
            String result = azureB2CUserPool.authenticate("testuser", "testpassword");
            // We don't expect this to succeed with mock configuration
            Assert.fail("Should have thrown an exception with mock configuration");
        } catch (UserPoolException e) {
            // Expected - the method exists and throws appropriate exception
            Assert.assertTrue(e.getMessage().contains("Authentication failed") || 
                            e.getMessage().contains("Missing required") ||
                            e.getMessage().contains("Failed to create"));
        }
    }

    @Test
    public void testChangePasswordMethodExists() {
        // This test verifies that the changePassword method exists and can be called
        
        try {
            azureB2CUserPool.changePassword("testuser", "oldpassword", "newpassword");
            // We don't expect this to succeed with mock configuration
            Assert.fail("Should have thrown an exception with mock configuration");
        } catch (UserPoolException e) {
            // Expected - the method exists and throws appropriate exception
            Assert.assertTrue(e.getMessage().contains("Password change failed") || 
                            e.getMessage().contains("Authentication failed") ||
                            e.getMessage().contains("Missing required") ||
                            e.getMessage().contains("Failed to create"));
        }
    }

    @Test
    public void testGetB2CAdapter() {
        // Test that we can access the underlying B2C adapter
        Assert.assertNotNull(azureB2CUserPool.getB2CAdapter());
    }

    @Test
    public void testAuthenticateWithNullParameters() {
        try {
            azureB2CUserPool.authenticate(null, "password");
            Assert.fail("Should have thrown an exception for null username");
        } catch (UserPoolException e) {
            Assert.assertTrue(e.getMessage().contains("Authentication failed") ||
                            e.getMessage().contains("required"));
        }
    }

    @Test
    public void testChangePasswordWithNullParameters() {
        try {
            azureB2CUserPool.changePassword(null, "oldpass", "newpass");
            Assert.fail("Should have thrown an exception for null username");
        } catch (UserPoolException e) {
            Assert.assertTrue(e.getMessage().contains("Password change failed") ||
                            e.getMessage().contains("required"));
        }
    }
} 