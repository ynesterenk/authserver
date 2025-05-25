package shared.infrastructure.azure.adapters;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.microsoft.azure.functions.ExecutionContext;

/**
 * Adapter that implements AWS Lambda Context interface using Azure ExecutionContext
 * for backward compatibility during migration.
 */
public class AwsContextAdapter implements Context {

    private final ExecutionContext azureContext;
    private final AzureLambdaLoggerAdapter logger;

    public AwsContextAdapter(ExecutionContext azureContext) {
        this.azureContext = azureContext;
        this.logger = new AzureLambdaLoggerAdapter(azureContext.getLogger());
    }

    @Override
    public String getAwsRequestId() {
        // Azure Functions uses invocation ID as equivalent to AWS request ID
        return azureContext.getInvocationId();
    }

    @Override
    public String getLogGroupName() {
        // Azure Functions doesn't have log groups, return function app name
        return azureContext.getFunctionName() + "-logs";
    }

    @Override
    public String getLogStreamName() {
        // Azure Functions doesn't have log streams, create a compatible format
        return azureContext.getFunctionName() + "/" + azureContext.getInvocationId();
    }

    @Override
    public String getFunctionName() {
        return azureContext.getFunctionName();
    }

    @Override
    public String getFunctionVersion() {
        // Azure Functions doesn't have versions like AWS Lambda, return default
        return "$LATEST";
    }

    @Override
    public String getInvokedFunctionArn() {
        // Azure Functions doesn't have ARNs, create a compatible format
        return String.format("arn:azure:functions:*:*:function:%s", azureContext.getFunctionName());
    }

    @Override
    public CognitoIdentity getIdentity() {
        // Azure Functions doesn't have Cognito identity, return null
        return null;
    }

    @Override
    public ClientContext getClientContext() {
        // Azure Functions doesn't have client context, return null
        return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
        // Azure Functions doesn't expose remaining time, return a default value
        // In production, this could be calculated based on function timeout settings
        return 300000; // 5 minutes default
    }

    @Override
    public int getMemoryLimitInMB() {
        // Azure Functions doesn't expose memory limit in the same way
        // Return a default value that matches typical Azure Functions memory
        return 1536; // Default Azure Functions memory
    }

    @Override
    public LambdaLogger getLogger() {
        return logger;
    }

    /**
     * Get the underlying Azure ExecutionContext for Azure-specific operations
     */
    public ExecutionContext getAzureContext() {
        return azureContext;
    }
} 