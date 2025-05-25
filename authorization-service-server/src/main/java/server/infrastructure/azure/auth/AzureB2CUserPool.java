package server.infrastructure.azure.auth;

import server.core.UserPool;
import server.core.UserPoolException;
import shared.infrastructure.azure.auth.B2CUserPoolAdapter;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import lombok.extern.java.Log;

/**
 * Azure B2C UserPool implementation for the server module.
 * Implements server.core.UserPool interface and delegates to shared B2CUserPoolAdapter.
 */
@Log
public class AzureB2CUserPool implements UserPool {

    private final B2CUserPoolAdapter b2cAdapter;

    public AzureB2CUserPool() {
        this.b2cAdapter = new B2CUserPoolAdapter();
        log.info("Initialized Azure B2C UserPool for server module");
    }

    public AzureB2CUserPool(AzureEnvironmentConfig config) {
        this.b2cAdapter = new B2CUserPoolAdapter(config);
        log.info("Initialized Azure B2C UserPool with custom config");
    }

    @Override
    public String authenticate(String username, String password) throws UserPoolException {
        try {
            log.info("Server module authenticating user: " + username);
            return b2cAdapter.authenticate(username, password);
        } catch (Exception e) {
            log.severe("Server authentication failed for user " + username + ": " + e.getMessage());
            throw new UserPoolException(e.getMessage(), e);
        }
    }

    @Override
    public void changePassword(String username, String previousPassword, String proposedPassword) 
            throws UserPoolException {
        try {
            log.info("Server module changing password for user: " + username);
            b2cAdapter.changePassword(username, previousPassword, proposedPassword);
        } catch (Exception e) {
            log.severe("Server password change failed for user " + username + ": " + e.getMessage());
            throw new UserPoolException(e.getMessage(), e);
        }
    }

    /**
     * Gets the underlying B2C adapter for advanced operations
     */
    public B2CUserPoolAdapter getB2CAdapter() {
        return b2cAdapter;
    }
} 