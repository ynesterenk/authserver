package shared.infrastructure.azure.exceptions;

import com.azure.core.exception.AzureException;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.exception.ResourceModifiedException;
import com.microsoft.aad.msal4j.MsalException;
import com.microsoft.aad.msal4j.MsalServiceException;
import com.microsoft.aad.msal4j.MsalClientException;
import shared.core.AuthorizationServiceException;

/**
 * Adapter that converts Azure SDK exceptions to domain exceptions
 * while maintaining AWS error message compatibility.
 */
public class AzureExceptionAdapter {

    /**
     * Converts MSAL exceptions to AuthorizationServiceException
     */
    public static AuthorizationServiceException convertMsalException(MsalException msalException) {
        String message = "Authentication failed";
        
        if (msalException instanceof MsalServiceException) {
            MsalServiceException serviceException = (MsalServiceException) msalException;
            message = String.format("Authentication service error: %s (Error Code: %s)", 
                    serviceException.getMessage(), serviceException.errorCode());
        } else if (msalException instanceof MsalClientException) {
            MsalClientException clientException = (MsalClientException) msalException;
            message = String.format("Authentication client error: %s", clientException.getMessage());
        } else {
            message = String.format("Authentication error: %s", msalException.getMessage());
        }
        
        return new AuthorizationServiceException(message, msalException);
    }

    /**
     * Converts Azure Core exceptions to AuthorizationServiceException
     */
    public static AuthorizationServiceException convertAzureException(AzureException azureException) {
        String message = "Azure service error";
        
        if (azureException instanceof ClientAuthenticationException) {
            message = "Azure authentication failed: " + azureException.getMessage();
        } else if (azureException instanceof ResourceNotFoundException) {
            message = "Azure resource not found: " + azureException.getMessage();
        } else if (azureException instanceof HttpResponseException) {
            HttpResponseException httpException = (HttpResponseException) azureException;
            message = String.format("Azure HTTP error %d: %s", 
                    httpException.getResponse().getStatusCode(), 
                    httpException.getMessage());
        } else {
            message = "Azure service error: " + azureException.getMessage();
        }
        
        return new AuthorizationServiceException(message, azureException);
    }

    /**
     * Converts Azure Key Vault exceptions to AuthorizationServiceException
     */
    public static AuthorizationServiceException convertKeyVaultException(AzureException keyVaultException) {
        String message = String.format("Key Vault error: %s", keyVaultException.getMessage());
        return new AuthorizationServiceException(message, keyVaultException);
    }

    /**
     * Converts generic exceptions to AuthorizationServiceException with Azure context
     */
    public static AuthorizationServiceException convertGenericException(Exception exception) {
        String message = String.format("Azure operation failed: %s", exception.getMessage());
        return new AuthorizationServiceException(message, exception);
    }

    /**
     * Creates an AWS-compatible error message for unauthorized access
     */
    public static AuthorizationServiceException createUnauthorizedException(String details) {
        String message = String.format("User is not authorized to access this resource: %s", details);
        return new AuthorizationServiceException(message);
    }

    /**
     * Creates an AWS-compatible error message for forbidden access
     */
    public static AuthorizationServiceException createForbiddenException(String details) {
        String message = String.format("Access denied: %s", details);
        return new AuthorizationServiceException(message);
    }

    /**
     * Creates an AWS-compatible error message for invalid token
     */
    public static AuthorizationServiceException createInvalidTokenException(String details) {
        String message = String.format("Invalid authorization token: %s", details);
        return new AuthorizationServiceException(message);
    }

    /**
     * Creates an AWS-compatible error message for expired token
     */
    public static AuthorizationServiceException createExpiredTokenException() {
        String message = "Authorization token has expired";
        return new AuthorizationServiceException(message);
    }

    /**
     * Creates an AWS-compatible error message for malformed token
     */
    public static AuthorizationServiceException createMalformedTokenException(String details) {
        String message = String.format("Malformed authorization token: %s", details);
        return new AuthorizationServiceException(message);
    }

    /**
     * Creates an AWS-compatible error message for configuration errors
     */
    public static AuthorizationServiceException createConfigurationException(String details) {
        String message = String.format("Configuration error: %s", details);
        return new AuthorizationServiceException(message);
    }

    /**
     * Determines if an exception should be retried based on Azure error patterns
     */
    public static boolean isRetryableException(Exception exception) {
        if (exception instanceof HttpResponseException) {
            HttpResponseException httpException = (HttpResponseException) exception;
            int statusCode = httpException.getResponse().getStatusCode();
            // Retry on 5xx server errors and 429 throttling
            return statusCode >= 500 || statusCode == 429;
        }
        
        if (exception instanceof MsalServiceException) {
            MsalServiceException msalException = (MsalServiceException) exception;
            // Retry on service unavailable or throttling errors
            return msalException.errorCode() != null && 
                   (msalException.errorCode().contains("service_unavailable") ||
                    msalException.errorCode().contains("temporarily_unavailable"));
        }
        
        return false;
    }

    /**
     * Extracts error code from Azure exceptions for logging and monitoring
     */
    public static String extractErrorCode(Exception exception) {
        if (exception instanceof HttpResponseException) {
            HttpResponseException httpException = (HttpResponseException) exception;
            return String.valueOf(httpException.getResponse().getStatusCode());
        }
        
        if (exception instanceof MsalServiceException) {
            MsalServiceException msalException = (MsalServiceException) exception;
            return msalException.errorCode();
        }
        
        if (exception instanceof AzureException) {
            return exception.getClass().getSimpleName();
        }
        
        return "UNKNOWN_ERROR";
    }
} 