package shared.infrastructure.azure.monitoring;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.EventTelemetry;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that replaces CloudWatch metrics with Application Insights
 * while maintaining metric naming compatibility for monitoring dashboards.
 */
public class ApplicationInsightsAdapter {

    private final TelemetryClient telemetryClient;
    private final AzureEnvironmentConfig config;

    public ApplicationInsightsAdapter(AzureEnvironmentConfig config) {
        this.config = config;
        this.telemetryClient = initializeTelemetryClient();
    }

    /**
     * Initializes the Application Insights telemetry client
     */
    private TelemetryClient initializeTelemetryClient() {
        // Return default client - instrumentation key should be configured via environment variables
        // APPINSIGHTS_INSTRUMENTATIONKEY or connection string
        return new TelemetryClient();
    }

    /**
     * Tracks a request with AWS CloudWatch compatible naming
     */
    public void trackRequest(String name, String url, Instant startTime, long durationMs, String responseCode, boolean success) {
        // Track as custom event since RequestTelemetry has complex URL requirements
        Map<String, String> properties = new HashMap<>();
        properties.put("request_name", name);
        properties.put("url", url);
        properties.put("duration_ms", String.valueOf(durationMs));
        properties.put("response_code", responseCode);
        properties.put("success", String.valueOf(success));
        properties.put("aws.lambda.function_name", name);
        properties.put("aws.region", config.getAwsRegion());
        
        trackEvent("HttpRequest", properties);
        trackMetric("RequestDuration", durationMs, properties);
    }

    /**
     * Tracks an exception with AWS CloudWatch compatible naming
     */
    public void trackException(Exception exception, String functionName) {
        ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry();
        exceptionTelemetry.setException(exception);
        
        // Add AWS-compatible properties
        Map<String, String> properties = new HashMap<>();
        properties.put("aws.lambda.function_name", functionName);
        properties.put("aws.region", config.getAwsRegion());
        properties.put("error.type", exception.getClass().getSimpleName());
        exceptionTelemetry.getProperties().putAll(properties);
        
        telemetryClient.trackException(exceptionTelemetry);
    }

    /**
     * Tracks a custom metric with AWS CloudWatch compatible naming
     */
    public void trackMetric(String metricName, double value, Map<String, String> dimensions) {
        MetricTelemetry metric = new MetricTelemetry();
        metric.setName(metricName);
        metric.setValue(value);
        
        // Add AWS-compatible dimensions as properties
        Map<String, String> properties = new HashMap<>();
        properties.put("aws.region", config.getAwsRegion());
        if (dimensions != null) {
            properties.putAll(dimensions);
        }
        metric.getProperties().putAll(properties);
        
        telemetryClient.trackMetric(metric);
    }

    /**
     * Tracks authorization success metric (AWS CloudWatch compatible)
     */
    public void trackAuthorizationSuccess(String functionName, String principalId) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("function_name", functionName);
        dimensions.put("principal_id", principalId);
        dimensions.put("result", "success");
        
        trackMetric("AuthorizationAttempts", 1.0, dimensions);
        trackEvent("AuthorizationSuccess", dimensions);
    }

    /**
     * Tracks authorization failure metric (AWS CloudWatch compatible)
     */
    public void trackAuthorizationFailure(String functionName, String reason) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("function_name", functionName);
        dimensions.put("failure_reason", reason);
        dimensions.put("result", "failure");
        
        trackMetric("AuthorizationAttempts", 1.0, dimensions);
        trackEvent("AuthorizationFailure", dimensions);
    }

    /**
     * Tracks token validation metric (AWS CloudWatch compatible)
     */
    public void trackTokenValidation(String functionName, boolean isValid, String tokenType) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("function_name", functionName);
        dimensions.put("token_type", tokenType);
        dimensions.put("is_valid", String.valueOf(isValid));
        
        trackMetric("TokenValidations", 1.0, dimensions);
    }

    /**
     * Tracks function duration metric (AWS CloudWatch compatible)
     */
    public void trackFunctionDuration(String functionName, long durationMs) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("function_name", functionName);
        
        trackMetric("Duration", durationMs, dimensions);
    }

    /**
     * Tracks function invocation count (AWS CloudWatch compatible)
     */
    public void trackFunctionInvocation(String functionName) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("function_name", functionName);
        
        trackMetric("Invocations", 1.0, dimensions);
    }

    /**
     * Tracks function error count (AWS CloudWatch compatible)
     */
    public void trackFunctionError(String functionName, String errorType) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("function_name", functionName);
        dimensions.put("error_type", errorType);
        
        trackMetric("Errors", 1.0, dimensions);
    }

    /**
     * Tracks a custom event with properties
     */
    public void trackEvent(String eventName, Map<String, String> properties) {
        EventTelemetry event = new EventTelemetry();
        event.setName(eventName);
        
        // Add AWS-compatible properties
        Map<String, String> allProperties = new HashMap<>();
        allProperties.put("aws.region", config.getAwsRegion());
        if (properties != null) {
            allProperties.putAll(properties);
        }
        event.getProperties().putAll(allProperties);
        
        telemetryClient.trackEvent(event);
    }

    /**
     * Tracks dependency call (e.g., to Azure B2C, Key Vault)
     */
    public void trackDependency(String dependencyType, String target, String command, Instant startTime, long durationMs, boolean success) {
        Map<String, String> properties = new HashMap<>();
        properties.put("dependency_type", dependencyType);
        properties.put("target", target);
        properties.put("command", command);
        properties.put("success", String.valueOf(success));
        properties.put("duration_ms", String.valueOf(durationMs));
        
        // Track as custom event since trackDependency has complex signature requirements
        trackEvent("DependencyCall", properties);
        trackMetric("DependencyDuration", durationMs, properties);
    }

    /**
     * Flushes all telemetry data to Application Insights
     */
    public void flush() {
        telemetryClient.flush();
    }

    /**
     * Sets custom properties that will be added to all telemetry
     */
    public void setGlobalProperties(Map<String, String> properties) {
        if (properties != null) {
            telemetryClient.getContext().getProperties().putAll(properties);
        }
    }

    /**
     * Sets the user context for telemetry
     */
    public void setUserContext(String userId, String accountId) {
        telemetryClient.getContext().getUser().setId(userId);
        telemetryClient.getContext().getUser().setAccountId(accountId);
    }

    /**
     * Sets the operation context for telemetry correlation
     */
    public void setOperationContext(String operationId, String operationName) {
        telemetryClient.getContext().getOperation().setId(operationId);
        telemetryClient.getContext().getOperation().setName(operationName);
    }

    /**
     * Gets the underlying telemetry client for advanced scenarios
     */
    public TelemetryClient getTelemetryClient() {
        return telemetryClient;
    }
} 