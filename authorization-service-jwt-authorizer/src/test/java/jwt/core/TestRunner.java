package jwt.core;

import authorization.jwt.core.Claims;
import authorization.jwt.core.Jwt;
import authorization.jwt.core.JwtVerificationException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;

/**
 * Custom test runner to avoid Surefire plugin signature issues
 */
public class TestRunner {
    
    private static int testCount = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    
    public static void main(String[] args) {
        System.out.println("=== JWT Authorizer Test Runner ===");
        System.out.println("Running tests without Surefire plugin to avoid signature issues...\n");
        
        try {
            TestRunner runner = new TestRunner();
            runner.runAllTests();
            
            System.out.println("\n=== Test Results ===");
            System.out.println("Total tests: " + testCount);
            System.out.println("Passed: " + passedTests);
            System.out.println("Failed: " + failedTests);
            
            if (failedTests > 0) {
                System.exit(1);
            } else {
                System.out.println("All tests passed!");
                System.exit(0);
            }
            
        } catch (Exception e) {
            System.err.println("Test execution failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private KeyPair keysHolder;
    private RSAKey publicKey;
    private JWSSigner jwsSigner;
    private Jwt jwt;
    private String accessToken;
    
    public void runAllTests() throws Exception {
        init();
        setUp();
        
        // Test incorrect header formats
        testIncorrectHeaderFormat("");
        testIncorrectHeaderFormat(accessToken);
        testIncorrectHeaderFormat("Basic " + accessToken);
        testIncorrectHeaderFormat("Bearer" + accessToken);
        testIncorrectHeaderFormat("bearer " + accessToken);
        
        // Test incorrect JWT tokens
        testIncorrectJWTToken("foo" + accessToken);
        testIncorrectJWTToken("aa.bb.cc");
        
        // Test valid JWT
        testVerifyValidJWT();
        
        // Test unknown JWT
        testUnknownJWT();
        
        // Test bad signature
        testBadSignature();
        
        // Test expired time
        testExpiredTime();
    }
    
    private void init() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        keysHolder = generator.generateKeyPair();
        jwsSigner = new RSASSASigner(keysHolder.getPrivate());
        publicKey = RSAKey(keysHolder.getPublic(), "keyId");
    }
    
    private void setUp() throws JOSEException {
        jwt = new Jwt(new ImmutableJWKSet<>(new JWKSet(publicKey)));
        JWTClaimsSet claims = claims(expirationTime(60), "role1", "role2");
        accessToken = jws(claims, publicKey).serialize();
    }
    
    private void testIncorrectHeaderFormat(String header) {
        runTest("testIncorrectHeaderFormat(" + header + ")", () -> {
            try {
                jwt.verify(header);
                throw new AssertionError("Expected JwtVerificationException for incorrect header format");
            } catch (JwtVerificationException e) {
                if (!e.getMessage().contains("Incorrect header format")) {
                    throw new AssertionError("Expected 'Incorrect header format' message, got: " + e.getMessage());
                }
            }
        });
    }
    
    private void testIncorrectJWTToken(String token) {
        runTest("testIncorrectJWTToken(" + token + ")", () -> {
            try {
                jwt.verify("Bearer " + token);
                throw new AssertionError("Expected JwtVerificationException for incorrect JWT token");
            } catch (JwtVerificationException e) {
                if (!e.getMessage().contains("JWT couldn't be parsed")) {
                    throw new AssertionError("Expected 'JWT couldn't be parsed' message, got: " + e.getMessage());
                }
            }
        });
    }
    
    private void testVerifyValidJWT() {
        runTest("testVerifyValidJWT", () -> {
            Claims claims = jwt.verify("Bearer " + accessToken);
            
            if (claims == null) {
                throw new AssertionError("Claims should not be null");
            }
            if (!"foo".equals(claims.getUsername())) {
                throw new AssertionError("Expected username 'foo', got: " + claims.getUsername());
            }
            if (!Arrays.asList("role1", "role2").equals(claims.getRoles())) {
                throw new AssertionError("Expected roles [role1, role2], got: " + claims.getRoles());
            }
        });
    }
    
    private void testUnknownJWT() throws JOSEException {
        runTest("testUnknownJWT", () -> {
            try {
                JWTClaimsSet claims = claims(expirationTime(60), "role1", "role2");
                String header = "Bearer " + jws(claims, RSAKey(keysHolder.getPublic(), "foo")).serialize();
                jwt.verify(header);
                throw new AssertionError("Expected JwtVerificationException for unknown JWT");
            } catch (JwtVerificationException e) {
                if (!e.getMessage().contains("JWS object didn't pass the verification")) {
                    throw new AssertionError("Expected 'JWS object didn't pass the verification' message, got: " + e.getMessage());
                }
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private void testBadSignature() {
        runTest("testBadSignature", () -> {
            try {
                jwt.verify("Bearer " + accessToken + "a");
                throw new AssertionError("Expected JwtVerificationException for bad signature");
            } catch (JwtVerificationException e) {
                if (!e.getMessage().contains("JWS object didn't pass the verification")) {
                    throw new AssertionError("Expected 'JWS object didn't pass the verification' message, got: " + e.getMessage());
                }
            }
        });
    }
    
    private void testExpiredTime() throws JOSEException {
        runTest("testExpiredTime", () -> {
            try {
                String header = "Bearer " + jws(
                    claims(expirationTime(-60), "role1", "role2"),
                    publicKey).serialize();
                jwt.verify(header);
                throw new AssertionError("Expected JwtVerificationException for expired JWT");
            } catch (JwtVerificationException e) {
                if (!e.getMessage().contains("JWT has expired")) {
                    throw new AssertionError("Expected 'JWT has expired' message, got: " + e.getMessage());
                }
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private void runTest(String testName, Runnable test) {
        testCount++;
        try {
            test.run();
            passedTests++;
            System.out.println("✓ " + testName);
        } catch (Exception e) {
            failedTests++;
            System.out.println("✗ " + testName + " - " + e.getMessage());
        }
    }
    
    private JWSObject jws(JWTClaimsSet claims, RSAKey publicKey) throws JOSEException {
        JWSObject jws = new JWSObject(
            new JWSHeader(JWSAlgorithm.RS512, null, null, null, null, null, null, null, null, null, publicKey.getKeyID(), null, null),
            new Payload(claims.toString()));
        jws.sign(jwsSigner);
        return jws;
    }
    
    private static RSAKey RSAKey(PublicKey publicKey, String kid) {
        return new RSAKey.Builder((RSAPublicKey) publicKey)
            .keyUse(KeyUse.SIGNATURE)
            .keyID(kid)
            .build();
    }
    
    private static JWTClaimsSet claims(Date expirationTime, String... roles) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.claim("cognito:username", "foo");
        
        if (roles.length != 0) {
            builder.claim("cognito:roles", Arrays.asList(roles));
        }
        
        if (expirationTime != null) {
            builder.expirationTime(expirationTime);
        }
        
        return builder.build();
    }
    
    private static Date expirationTime(int offset) {
        return new Date(new Date().getTime() + offset * 1000);
    }
} 