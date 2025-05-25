package basic.infrastructure.azure.auth;

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
    public void testVerifyMethodExists() {
        // This test verifies that the verify method exists and can be called
        // The actual B2C authentication would require real Azure B2C configuration
        
        try {
            String result = azureB2CUserPool.verify("testuser", "testpassword");
            // We don't expect this to succeed with mock configuration
            Assert.fail("Should have thrown an exception with mock configuration");
        } catch (RuntimeException e) {
            // Expected - the method exists and throws appropriate exception
            Assert.assertTrue(e.getMessage().contains("User verification failed") || 
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
    public void testVerifyWithNullParameters() {
        try {
            azureB2CUserPool.verify(null, "password");
            Assert.fail("Should have thrown an exception for null username");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("User verification failed") ||
                            e.getMessage().contains("required"));
        }
    }

    @Test
    public void testVerifyWithEmptyParameters() {
        try {
            azureB2CUserPool.verify("", "");
            Assert.fail("Should have thrown an exception for empty parameters");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("User verification failed") ||
                            e.getMessage().contains("required"));
        }
    }
} 