package shared.infrastructure.azure.adapters;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.logging.Logger;

/**
 * Adapter that implements AWS LambdaLogger interface using Azure Functions Logger
 * for backward compatibility during migration.
 */
public class AzureLambdaLoggerAdapter implements LambdaLogger {

    private final Logger azureLogger;

    public AzureLambdaLoggerAdapter(Logger azureLogger) {
        this.azureLogger = azureLogger;
    }

    public void log(String message) {
        azureLogger.info(message);
    }

    public void log(byte[] message) {
        azureLogger.info(new String(message));
    }

    /**
     * Get the underlying Azure Logger for Azure-specific operations
     */
    public Logger getAzureLogger() {
        return azureLogger;
    }
} 