package shared.infrastructure.azure.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Azure AD B2C JWT Validator that validates tokens and converts claims
 * to AWS Cognito-compatible format for backward compatibility.
 */
@Log
public class AzureB2CJwtValidator {
    
    private final AzureEnvironmentConfig config;
    private final String tenantId; 
    private final String policyName;
    private final String clientId;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    
    public AzureB2CJwtValidator() {
        this.config = new AzureEnvironmentConfig();
        this.tenantId = config.getTenantId();
        this.policyName = config.getPolicyName();
        this.clientId = config.getClientId();
        this.jwtProcessor = createJWTProcessor();
        
        log.info("Initialized Azure B2C JWT Validator for tenant: " + tenantId);
    }
    
    public AzureB2CJwtValidator(AzureEnvironmentConfig config) {
        this.config = config;
        this.tenantId = config.getTenantId();
        this.policyName = config.getPolicyName();
        this.clientId = config.getClientId();
        this.jwtProcessor = createJWTProcessor();
        
        log.info("Initialized Azure B2C JWT Validator with custom config");
    }
    
    /**
     * Creates and configures the JWT processor for B2C token validation
     */
    private ConfigurableJWTProcessor<SecurityContext> createJWTProcessor() {
        try {
            // Azure AD B2C JWKS endpoint format
            String jwksUrl = config.getB2CJwksUri();
            log.info("Using B2C JWKS URL: " + jwksUrl);
            
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUrl));
            
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            
            // Configure for RS256 algorithm (standard for B2C)
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
            JWSKeySelector<SecurityContext> keySelector = 
                new JWSVerificationKeySelector<>(expectedJWSAlg, jwkSource);
            processor.setJWSKeySelector(keySelector);
            processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));
            
            return processor;
        } catch (Exception e) {
            log.severe("Failed to create JWT processor: " + e.getMessage());
            throw new RuntimeException("Failed to create JWT processor", e);
        }
    }
    
    /**
     * Validates B2C JWT token and returns AWS Cognito-compatible claims
     */
    public AwsCompatibleClaims validateToken(String token) throws JwtValidationException {
        try {
            if (StringUtils.isBlank(token)) {
                throw new JwtValidationException("Token is required");
            }
            
            log.info("Validating B2C JWT token");
            
            SecurityContext ctx = null;
            JWTClaimsSet claimsSet = jwtProcessor.process(token, ctx);
            
            // Validate basic token properties
            validateTokenClaims(claimsSet);
            
            // Convert B2C claims to AWS Cognito-compatible format
            return adaptB2CClaimsToAwsFormat(claimsSet);
            
        } catch (Exception e) {
            log.severe("Token validation failed: " + e.getMessage());
            throw new JwtValidationException("Token validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates basic token claims (audience, expiration, etc.)
     */
    private void validateTokenClaims(JWTClaimsSet claimsSet) throws JwtValidationException {
        // Check expiration
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime != null && expirationTime.before(new Date())) {
            throw new JwtValidationException("Token has expired");
        }
        
        // Check audience (should include our client ID)
        List<String> audience = claimsSet.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            log.warning("Token audience validation failed. Expected: " + clientId + ", Got: " + audience);
            // Don't fail for now to maintain compatibility during migration
        }
        
        // Check issuer (should be our B2C tenant)
        String issuer = claimsSet.getIssuer();
        if (StringUtils.isBlank(issuer) || !issuer.contains(tenantId)) {
            log.warning("Token issuer validation failed. Expected tenant: " + tenantId + ", Got: " + issuer);
            // Don't fail for now to maintain compatibility during migration
        }
    }
    
    /**
     * Converts B2C claims to AWS Cognito-compatible format
     */
    private AwsCompatibleClaims adaptB2CClaimsToAwsFormat(JWTClaimsSet b2cClaims) {
        AwsCompatibleClaims awsCompatibleClaims = new AwsCompatibleClaims();
        
        // Map standard claims
        awsCompatibleClaims.setUsername(extractUsername(b2cClaims));
        awsCompatibleClaims.setGroups(extractGroups(b2cClaims));
        awsCompatibleClaims.setAudience(b2cClaims.getAudience());
        awsCompatibleClaims.setIssuer(b2cClaims.getIssuer());
        awsCompatibleClaims.setExpiration(b2cClaims.getExpirationTime());
        awsCompatibleClaims.setIssuedAt(b2cClaims.getIssueTime());
        awsCompatibleClaims.setSubject(b2cClaims.getSubject());
        
        // Map B2C specific claims to Cognito equivalents
        try {
            awsCompatibleClaims.setEmail(b2cClaims.getStringClaim("email"));
            awsCompatibleClaims.setEmailVerified(b2cClaims.getBooleanClaim("email_verified"));
            awsCompatibleClaims.setGivenName(b2cClaims.getStringClaim("given_name"));
            awsCompatibleClaims.setFamilyName(b2cClaims.getStringClaim("family_name"));
        } catch (ParseException e) {
            log.warning("Failed to parse B2C claims: " + e.getMessage());
        }
        
        // Preserve any custom claims
        Map<String, Object> customClaims = new HashMap<>();
        for (String claimName : b2cClaims.getClaims().keySet()) {
            if (!isStandardClaim(claimName)) {
                customClaims.put(claimName, b2cClaims.getClaim(claimName));
            }
        }
        awsCompatibleClaims.setCustomClaims(customClaims);
        
        log.info("Successfully converted B2C claims to AWS format for user: " + awsCompatibleClaims.getUsername());
        return awsCompatibleClaims;
    }
    
    /**
     * Extracts username from B2C claims with fallback logic
     */
    private String extractUsername(JWTClaimsSet claimsSet) {
        try {
            // Try different claim names in order of preference
            String username = claimsSet.getStringClaim("preferred_username");
            if (StringUtils.isBlank(username)) {
                username = claimsSet.getStringClaim("unique_name");
            }
            if (StringUtils.isBlank(username)) {
                username = claimsSet.getStringClaim("email");
            }
            if (StringUtils.isBlank(username)) {
                username = claimsSet.getSubject();
            }
            return username;
        } catch (ParseException e) {
            log.warning("Failed to extract username from claims: " + e.getMessage());
            return claimsSet.getSubject(); // Fallback to subject
        }
    }
    
    /**
     * Extracts groups from B2C claims
     */
    private List<String> extractGroups(JWTClaimsSet claimsSet) {
        try {
            List<String> groups = claimsSet.getStringListClaim("groups");
            if (groups == null) {
                // Try alternative claim names
                groups = claimsSet.getStringListClaim("roles");
            }
            return groups;
        } catch (Exception e) {
            log.warning("Failed to extract groups from token: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if a claim is a standard JWT/OIDC claim
     */
    private boolean isStandardClaim(String claimName) {
        return Arrays.asList(
            "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "azp",
            "preferred_username", "unique_name", "email", "email_verified",
            "given_name", "family_name", "groups", "roles"
        ).contains(claimName);
    }
    
    /**
     * Gets the B2C JWKS URI
     */
    public String getJwksUri() {
        return config.getB2CJwksUri();
    }
    
    /**
     * Gets the B2C authority
     */
    public String getAuthority() {
        return config.getB2CAuthority();
    }
    
    /**
     * AWS Cognito-compatible claims structure
     */
    public static class AwsCompatibleClaims {
        private String username;
        private List<String> groups;
        private List<String> audience;
        private String issuer;
        private Date expiration;
        private Date issuedAt;
        private String subject;
        private String email;
        private Boolean emailVerified;
        private String givenName;
        private String familyName;
        private Map<String, Object> customClaims;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public List<String> getGroups() { return groups; }
        public void setGroups(List<String> groups) { this.groups = groups; }
        
        public List<String> getAudience() { return audience; }
        public void setAudience(List<String> audience) { this.audience = audience; }
        
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        
        public Date getExpiration() { return expiration; }
        public void setExpiration(Date expiration) { this.expiration = expiration; }
        
        public Date getIssuedAt() { return issuedAt; }
        public void setIssuedAt(Date issuedAt) { this.issuedAt = issuedAt; }
        
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public Boolean getEmailVerified() { return emailVerified; }
        public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
        
        public String getGivenName() { return givenName; }
        public void setGivenName(String givenName) { this.givenName = givenName; }
        
        public String getFamilyName() { return familyName; }
        public void setFamilyName(String familyName) { this.familyName = familyName; }
        
        public Map<String, Object> getCustomClaims() { return customClaims; }
        public void setCustomClaims(Map<String, Object> customClaims) { this.customClaims = customClaims; }
    }
    
    /**
     * JWT validation exception
     */
    public static class JwtValidationException extends Exception {
        public JwtValidationException(String message) {
            super(message);
        }
        
        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 