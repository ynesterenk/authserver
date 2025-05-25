package shared.infrastructure.azure.adapters;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import lombok.extern.java.Log;
import shared.infrastructure.aws.gateway.AuthorizerRequest;
import shared.infrastructure.aws.gateway.AuthorizerResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter to convert Azure Functions requests/responses to AWS Lambda format
 * for backward compatibility during migration.
 */
@Log
public class RequestResponseAdapter {

    /**
     * Converts Azure Functions HTTP request to AWS Lambda AuthorizerRequest format
     */
    public static AuthorizerRequest convertToAuthorizerRequest(HttpRequestMessage<Optional<String>> request) {
        AuthorizerRequest authorizerRequest = new AuthorizerRequest();
        
        // Extract authorization token from Authorization header
        String authorizationHeader = request.getHeaders().get("authorization");
        if (authorizationHeader == null) {
            authorizationHeader = request.getHeaders().get("Authorization");
        }
        
        authorizerRequest.setAuthorizationToken(authorizationHeader);
        authorizerRequest.setType("TOKEN");
        
        // Construct method ARN from Azure Functions context
        String methodArn = constructMethodArn(request);
        authorizerRequest.setMethodArn(methodArn);
        
        return authorizerRequest;
    }

    /**
     * Converts AWS Lambda AuthorizerResponse to Azure Functions HTTP response
     */
    public static HttpResponseMessage convertFromAuthorizerResponse(
            AuthorizerResponse authorizerResponse,
            HttpRequestMessage<Optional<String>> request) {
        
        // For authorizer responses, we typically return 200 OK with the policy
        // The actual authorization decision is embedded in the policy document
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("principalId", authorizerResponse.getPrincipalId());
        responseBody.put("policyDocument", authorizerResponse.getPolicyDocument());
        responseBody.put("context", authorizerResponse.getContext());
        
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .body(responseBody)
                .build();
    }

    /**
     * Creates a generic HTTP response for Azure Functions that matches AWS API Gateway format
     */
    public static HttpResponseMessage createHttpResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus status,
            Object body) {
        
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .body(body)
                .build();
    }

    /**
     * Creates an error response that matches AWS API Gateway error format
     */
    public static HttpResponseMessage createErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus status,
            String errorMessage) {
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("errorMessage", errorMessage);
        errorBody.put("errorType", "AuthorizationException");
        
        return createHttpResponse(request, status, errorBody);
    }

    /**
     * Constructs a method ARN similar to AWS API Gateway format for Azure Functions
     */
    private static String constructMethodArn(HttpRequestMessage<Optional<String>> request) {
        // Azure Functions doesn't have the same ARN concept, so we create a compatible format
        String method = request.getHttpMethod().toString();
        String uri = request.getUri().getPath();
        
        // Format: arn:azure:functions:region:account:function/functionName/method/resource
        return String.format("arn:azure:functions:*:*:function/%s/%s%s", 
                "authorization-service", method, uri);
    }

    /**
     * Extracts query parameters from Azure Functions request in AWS Lambda format
     */
    public static Map<String, String> extractQueryParameters(HttpRequestMessage<Optional<String>> request) {
        Map<String, String> queryParams = new HashMap<>();
        request.getQueryParameters().forEach((key, value) -> {
            queryParams.put(key, value);
        });
        return queryParams;
    }

    /**
     * Extracts headers from Azure Functions request in AWS Lambda format
     */
    public static Map<String, String> extractHeaders(HttpRequestMessage<Optional<String>> request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((key, value) -> {
            headers.put(key, value);
        });
        return headers;
    }
} 