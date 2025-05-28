package server.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import shared.infrastructure.azure.adapters.RequestResponseAdapter;
import shared.infrastructure.azure.adapters.ResponseFormatAdapter;
import shared.infrastructure.azure.monitoring.ApplicationInsightsAdapter;
import shared.infrastructure.azure.config.AzureEnvironmentConfig;
import server.infrastructure.azure.AzureProxyRequestHandler;
import shared.infrastructure.aws.gateway.proxy.ProxyRequest;
import shared.infrastructure.aws.gateway.proxy.ProxyResponse;
import com.amazonaws.services.lambda.runtime.Context;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Azure Functions implementation for the AuthServer endpoints.
 * Uses adapter pattern to reuse existing AWS Lambda handler logic.
 */
public class AuthServerFunction {

    private static final Logger logger = Logger.getLogger(AuthServerFunction.class.getName());
    private static final ApplicationInsightsAdapter insights = new ApplicationInsightsAdapter(new AzureEnvironmentConfig());

    @FunctionName("oauth-token")
    public HttpResponseMessage oauthToken(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.POST}, 
                     route = "oauth/token",
                     authLevel = AuthorizationLevel.ANONYMOUS) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        logger.info("Processing OAuth token request: " + request.getUri().toString());
        
        try {
            // Track function invocation
            insights.trackFunctionInvocation("oauth-token");
            
            // Convert Azure request to AWS-compatible format
            ProxyRequest awsRequest = RequestResponseAdapter.convertAzureToAwsRequest(request);
            Context awsContext = RequestResponseAdapter.convertAzureToAwsContext(context);
            
            // Reuse existing AWS handler logic
            AzureProxyRequestHandler handler = new AzureProxyRequestHandler();
            ProxyResponse awsResponse = handler.handleRequest(awsRequest, awsContext);
            
            // Track success metrics
            long duration = System.currentTimeMillis() - startTime;
            insights.trackRequest("oauth-token", request.getUri().toString(), 
                    java.time.Instant.ofEpochMilli(startTime), duration, 
                    String.valueOf(awsResponse.getStatusCode()), true);
            insights.trackMetric("TokenRequests", 1.0, null);
            insights.trackFunctionDuration("oauth-token", duration);
            
            // Convert back to Azure response
            return RequestResponseAdapter.convertAwsToAzureResponse(awsResponse, request);
            
        } catch (Exception e) {
            // Track error metrics
            long duration = System.currentTimeMillis() - startTime;
            logger.severe("Error processing OAuth token request: " + e.getMessage());
            
            insights.trackRequest("oauth-token", request.getUri().toString(), 
                    java.time.Instant.ofEpochMilli(startTime), duration, "500", false);
            insights.trackException(e, "oauth-token");
            insights.trackFunctionError("oauth-token", e.getClass().getSimpleName());
            
            return ResponseFormatAdapter.createInternalServerErrorResponse(request, 
                "Internal server error: " + e.getMessage());
        } finally {
            insights.flush();
        }
    }
    
    @FunctionName("change-password")
    public HttpResponseMessage changePassword(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.GET, HttpMethod.POST}, 
                     route = "account",
                     authLevel = AuthorizationLevel.ANONYMOUS) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        logger.info("Processing change password request: " + request.getUri().toString());
        
        try {
            // Track function invocation
            insights.trackFunctionInvocation("change-password");
            
            // Convert Azure request to AWS-compatible format
            ProxyRequest awsRequest = RequestResponseAdapter.convertAzureToAwsRequest(request);
            Context awsContext = RequestResponseAdapter.convertAzureToAwsContext(context);
            
            // Reuse existing AWS handler logic
            AzureProxyRequestHandler handler = new AzureProxyRequestHandler();
            ProxyResponse awsResponse = handler.handleRequest(awsRequest, awsContext);
            
            // Track success metrics
            long duration = System.currentTimeMillis() - startTime;
            insights.trackRequest("change-password", request.getUri().toString(), 
                    java.time.Instant.ofEpochMilli(startTime), duration, 
                    String.valueOf(awsResponse.getStatusCode()), true);
            insights.trackMetric("PasswordChangeRequests", 1.0, null);
            insights.trackFunctionDuration("change-password", duration);
            
            // Convert back to Azure response
            return RequestResponseAdapter.convertAwsToAzureResponse(awsResponse, request);
            
        } catch (Exception e) {
            // Track error metrics
            long duration = System.currentTimeMillis() - startTime;
            logger.severe("Error processing change password request: " + e.getMessage());
            
            insights.trackRequest("change-password", request.getUri().toString(), 
                    java.time.Instant.ofEpochMilli(startTime), duration, "500", false);
            insights.trackException(e, "change-password");
            insights.trackFunctionError("change-password", e.getClass().getSimpleName());
            
            return ResponseFormatAdapter.createInternalServerErrorResponse(request, 
                "Internal server error: " + e.getMessage());
        } finally {
            insights.flush();
        }
    }

    @FunctionName("cors-preflight")
    public HttpResponseMessage corsPreflightHandler(
        @HttpTrigger(name = "req", 
                     methods = {HttpMethod.OPTIONS}, 
                     route = "{*path}",
                     authLevel = AuthorizationLevel.ANONYMOUS) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
        
        logger.info("Processing CORS preflight request: " + request.getUri().toString());
        
        // Return CORS preflight response
        return ResponseFormatAdapter.createCorsPreflightResponse(request);
    }
} 
