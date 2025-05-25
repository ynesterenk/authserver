package basic.infrastructure.azure.auth;

import basic.core.UserPool;
import shared.infrastructure.azure.auth.B2CUserPoolAdapter;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import lombok.extern.java.Log;

/**
 * Azure B2C UserPool implementation for the basic authenticator module.
 * Implements basic.core.UserPool interface and delegates to shared B2CUserPoolAdapter.
 */
@Log
public class AzureB2CUserPool implements UserPool {

    private final B2CUserPoolAdapter b2cAdapter;

    public AzureB2CUserPool() {
        this.b2cAdapter = new B2CUserPoolAdapter();
        log.info("Initialized Azure B2C UserPool for basic authenticator module");
    }

    public AzureB2CUserPool(AzureEnvironmentConfig config) {
        this.b2cAdapter = new B2CUserPoolAdapter(config);
        log.info("Initialized Azure B2C UserPool with custom config for basic authenticator");
    }

    @Override
    public String verify(String username, String password) {
        try {
            log.info("Basic authenticator verifying user: " + username);
            return b2cAdapter.authenticate(username, password);
        } catch (Exception e) {
            log.severe("Basic authenticator verification failed for user " + username + ": " + e.getMessage());
            throw new RuntimeException("User verification failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the underlying B2C adapter for advanced operations
     */
    public B2CUserPoolAdapter getB2CAdapter() {
        return b2cAdapter;
    }
} 