package shared.infrastructure.azure.auth;

import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class AzureB2CJwtValidatorTest {

    private AzureB2CJwtValidator jwtValidator;
    private AzureEnvironmentConfig mockConfig;

    @BeforeMethod
    public void setUp() {
        
        // Create mock configuration for testing
        Map<String, String> testEnv = new HashMap<>();
        testEnv.put("AZURE_B2C_TENANT_ID", "test-tenant.onmicrosoft.com");
        testEnv.put("AZURE_B2C_CLIENT_ID", "test-client-id");
        testEnv.put("AZURE_B2C_POLICY_NAME", "B2C_1_signupsignin1");
        testEnv.put("AZURE_B2C_DOMAIN", "test-tenant.b2clogin.com");
        
        mockConfig = new AzureEnvironmentConfig(testEnv);
        jwtValidator = new AzureB2CJwtValidator(mockConfig);
    }

    @Test
    public void testValidatorInitialization() {
        // Test that the validator initializes correctly
        Assert.assertNotNull(jwtValidator);
        Assert.assertNotNull(jwtValidator.getAuthority());
        Assert.assertNotNull(jwtValidator.getJwksUri());
    }

    @Test
    public void testGetJwksUri() {
        String jwksUri = jwtValidator.getJwksUri();
        Assert.assertNotNull(jwksUri);
        Assert.assertTrue(jwksUri.contains("test-tenant.b2clogin.com"));
        Assert.assertTrue(jwksUri.contains("discovery/v2.0/keys"));
    }

    @Test
    public void testGetAuthority() {
        String authority = jwtValidator.getAuthority();
        Assert.assertNotNull(authority);
        Assert.assertTrue(authority.contains("test-tenant.b2clogin.com"));
        Assert.assertTrue(authority.contains("test-tenant.onmicrosoft.com"));
        Assert.assertTrue(authority.contains("B2C_1_signupsignin1"));
    }

    @Test
    public void testValidateTokenWithNullToken() {
        try {
            jwtValidator.validateToken(null);
            Assert.fail("Should have thrown an exception for null token");
        } catch (AzureB2CJwtValidator.JwtValidationException e) {
            Assert.assertTrue(e.getMessage().contains("Token is required"));
        }
    }

    @Test
    public void testValidateTokenWithEmptyToken() {
        try {
            jwtValidator.validateToken("");
            Assert.fail("Should have thrown an exception for empty token");
        } catch (AzureB2CJwtValidator.JwtValidationException e) {
            Assert.assertTrue(e.getMessage().contains("Token is required"));
        }
    }

    @Test
    public void testValidateTokenWithInvalidToken() {
        try {
            jwtValidator.validateToken("invalid.jwt.token");
            Assert.fail("Should have thrown an exception for invalid token");
        } catch (AzureB2CJwtValidator.JwtValidationException e) {
            Assert.assertTrue(e.getMessage().contains("Token validation failed"));
        }
    }

    @Test
    public void testAwsCompatibleClaimsStructure() {
        // Test the AwsCompatibleClaims class structure
        AzureB2CJwtValidator.AwsCompatibleClaims claims = new AzureB2CJwtValidator.AwsCompatibleClaims();
        
        // Test setters and getters
        claims.setUsername("testuser");
        Assert.assertEquals(claims.getUsername(), "testuser");
        
        claims.setEmail("test@example.com");
        Assert.assertEquals(claims.getEmail(), "test@example.com");
        
        claims.setSubject("test-subject");
        Assert.assertEquals(claims.getSubject(), "test-subject");
        
        claims.setIssuer("test-issuer");
        Assert.assertEquals(claims.getIssuer(), "test-issuer");
        
        Map<String, Object> customClaims = new HashMap<>();
        customClaims.put("custom", "value");
        claims.setCustomClaims(customClaims);
        Assert.assertEquals(claims.getCustomClaims().get("custom"), "value");
    }
} 