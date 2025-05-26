package integration.monitoring;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import com.microsoft.applicationinsights.telemetry.EventTelemetry;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Performance Monitor for AWS to Azure Migration
 * Monitors system health, performance metrics, and connectivity
 */
public class PerformanceMonitor {
    private final TelemetryClient telemetryClient;
    private final ScheduledExecutorService scheduler;
    private final OkHttpClient httpClient;
    private final String azureFunctionBaseUrl;
    private final String testClientId;
    private final String testClientSecret;
    
    public PerformanceMonitor() {
        this.telemetryClient = new TelemetryClient();
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
        
        // Load configuration from environment variables
        this.azureFunctionBaseUrl = System.getenv("AZURE_FUNCTION_BASE_URL");
        this.testClientId = System.getenv("TEST_CLIENT_ID");
        this.testClientSecret = System.getenv("TEST_CLIENT_SECRET");
    }
    
    /**
     * Start monitoring all system components
     */
    public void startMonitoring() {
        System.out.println("Starting Performance Monitor...");
        
        // Monitor response times every minute
        scheduler.scheduleAtFixedRate(this::monitorResponseTimes, 0, 1, TimeUnit.MINUTES);
        
        // Monitor system health every 30 seconds
        scheduler.scheduleAtFixedRate(this::monitorSystemHealth, 0, 30, TimeUnit.SECONDS);
        
        // Monitor B2C connectivity every 5 minutes
        scheduler.scheduleAtFixedRate(this::monitorB2CConnectivity, 0, 5, TimeUnit.MINUTES);
        
        System.out.println("Performance Monitor started successfully");
    }
    
    /**
     * Stop monitoring and cleanup resources
     */
    public void stopMonitoring() {
        System.out.println("Stopping Performance Monitor...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Performance Monitor stopped");
    }
    
    /**
     * Monitor OAuth endpoint response times
     */
    private void monitorResponseTimes() {
        try {
            // Test OAuth endpoint response time
            long startTime = System.currentTimeMillis();
            Response response = testOAuthEndpoint();
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean success = response.code() == 200;
            response.close();
            
            // Track metric
            MetricTelemetry metric = new MetricTelemetry();
            metric.setName("OAuth.ResponseTime");
            metric.setValue(responseTime);
            metric.getProperties().put("endpoint", "oauth/token");
            metric.getProperties().put("success", String.valueOf(success));
            metric.getProperties().put("statusCode", String.valueOf(response.code()));
            
            telemetryClient.trackMetric(metric);
            
            // Alert if response time exceeds threshold
            if (responseTime > 200) {
                EventTelemetry alert = new EventTelemetry("PerformanceAlert");
                alert.getProperties().put("type", "HighResponseTime");
                alert.getProperties().put("value", String.valueOf(responseTime));
                alert.getProperties().put("threshold", "200");
                alert.getProperties().put("endpoint", "oauth/token");
                telemetryClient.trackEvent(alert);
            }
            
            System.out.println("OAuth Response Time: " + responseTime + "ms (Success: " + success + ")");
            
        } catch (Exception e) {
            ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(e);
            exceptionTelemetry.getProperties().put("operation", "monitorResponseTimes");
            telemetryClient.trackException(exceptionTelemetry);
            System.err.println("Error monitoring response times: " + e.getMessage());
        }
    }
    
    /**
     * Monitor overall system health
     */
    private void monitorSystemHealth() {
        try {
            // Check function app health
            boolean functionsHealthy = checkFunctionAppHealth();
            
            // Check B2C connectivity
            boolean b2cHealthy = checkB2CHealth();
            
            // Check Key Vault connectivity (simulated)
            boolean keyVaultHealthy = checkKeyVaultHealth();
            
            // Track overall health
            MetricTelemetry healthMetric = new MetricTelemetry();
            healthMetric.setName("System.Health");
            healthMetric.setValue(functionsHealthy && b2cHealthy && keyVaultHealthy ? 1 : 0);
            healthMetric.getProperties().put("functions", String.valueOf(functionsHealthy));
            healthMetric.getProperties().put("b2c", String.valueOf(b2cHealthy));
            healthMetric.getProperties().put("keyVault", String.valueOf(keyVaultHealthy));
            
            telemetryClient.trackMetric(healthMetric);
            
            // Track individual component health
            trackComponentHealth("FunctionApp", functionsHealthy);
            trackComponentHealth("B2C", b2cHealthy);
            trackComponentHealth("KeyVault", keyVaultHealthy);
            
            System.out.println("System Health - Functions: " + functionsHealthy + 
                             ", B2C: " + b2cHealthy + 
                             ", KeyVault: " + keyVaultHealthy);
            
        } catch (Exception e) {
            ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(e);
            exceptionTelemetry.getProperties().put("operation", "monitorSystemHealth");
            telemetryClient.trackException(exceptionTelemetry);
            System.err.println("Error monitoring system health: " + e.getMessage());
        }
    }
    
    /**
     * Monitor B2C connectivity and JWKS endpoint
     */
    private void monitorB2CConnectivity() {
        try {
            // Test B2C JWKS endpoint
            long startTime = System.currentTimeMillis();
            boolean jwksAccessible = testB2CJwksEndpoint();
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Track B2C connectivity
            MetricTelemetry metric = new MetricTelemetry();
            metric.setName("B2C.Connectivity");
            metric.setValue(jwksAccessible ? 1 : 0);
            metric.getProperties().put("responseTime", String.valueOf(responseTime));
            metric.getProperties().put("endpoint", "jwks");
            
            telemetryClient.trackMetric(metric);
            
            if (!jwksAccessible) {
                EventTelemetry alert = new EventTelemetry("B2CConnectivityAlert");
                alert.getProperties().put("type", "JWKSEndpointUnavailable");
                alert.getProperties().put("responseTime", String.valueOf(responseTime));
                telemetryClient.trackEvent(alert);
            }
            
            System.out.println("B2C JWKS Connectivity: " + jwksAccessible + " (" + responseTime + "ms)");
            
        } catch (Exception e) {
            ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(e);
            exceptionTelemetry.getProperties().put("operation", "monitorB2CConnectivity");
            telemetryClient.trackException(exceptionTelemetry);
            System.err.println("Error monitoring B2C connectivity: " + e.getMessage());
        }
    }
    
    /**
     * Test OAuth endpoint
     */
    private Response testOAuthEndpoint() throws IOException {
        if (azureFunctionBaseUrl == null || testClientId == null || testClientSecret == null) {
            throw new IllegalStateException("Missing required environment variables");
        }
        
        String tokenEndpoint = azureFunctionBaseUrl + "/oauth/token";
        
        Map<String, String> requestBody = Map.of(
            "grant_type", "client_credentials",
            "client_id", testClientId,
            "client_secret", testClientSecret
        );
        
        String jsonBody = "{\"grant_type\":\"" + requestBody.get("grant_type") + 
                         "\",\"client_id\":\"" + requestBody.get("client_id") + 
                         "\",\"client_secret\":\"" + requestBody.get("client_secret") + "\"}";
        
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
        Request request = new Request.Builder()
            .url(tokenEndpoint)
            .post(body)
            .build();
        
        return httpClient.newCall(request).execute();
    }
    
    /**
     * Test B2C JWKS endpoint
     */
    private boolean testB2CJwksEndpoint() {
        try {
            String jwksUrl = "https://authserverb2c.b2clogin.com/authserverb2c.onmicrosoft.com/discovery/v2.0/keys?p=B2C_1_signupsignin";
            
            Request request = new Request.Builder()
                .url(jwksUrl)
                .get()
                .build();
            
            Response response = httpClient.newCall(request).execute();
            boolean success = response.code() == 200;
            response.close();
            
            return success;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check Function App health
     */
    private boolean checkFunctionAppHealth() {
        try {
            if (azureFunctionBaseUrl == null) {
                return false;
            }
            
            String healthEndpoint = azureFunctionBaseUrl + "/health";
            
            Request request = new Request.Builder()
                .url(healthEndpoint)
                .get()
                .build();
            
            Response response = httpClient.newCall(request).execute();
            boolean healthy = response.code() == 200;
            response.close();
            
            return healthy;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check B2C health
     */
    private boolean checkB2CHealth() {
        // For now, use JWKS endpoint as health indicator
        return testB2CJwksEndpoint();
    }
    
    /**
     * Check Key Vault health (simulated)
     */
    private boolean checkKeyVaultHealth() {
        // Simulate Key Vault health check
        // In real implementation, this would test Key Vault connectivity
        return true;
    }
    
    /**
     * Track individual component health
     */
    private void trackComponentHealth(String component, boolean healthy) {
        MetricTelemetry metric = new MetricTelemetry();
        metric.setName("Component.Health");
        metric.setValue(healthy ? 1 : 0);
        metric.getProperties().put("component", component);
        metric.getProperties().put("status", healthy ? "healthy" : "unhealthy");
        
        telemetryClient.trackMetric(metric);
    }
    
    /**
     * Main method for standalone execution
     */
    public static void main(String[] args) {
        PerformanceMonitor monitor = new PerformanceMonitor();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(monitor::stopMonitoring));
        
        // Start monitoring
        monitor.startMonitoring();
        
        // Keep running until interrupted
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Performance Monitor interrupted");
            Thread.currentThread().interrupt();
        }
    }
} 