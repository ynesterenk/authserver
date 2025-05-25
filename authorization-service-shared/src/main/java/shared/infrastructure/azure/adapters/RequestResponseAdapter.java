package shared.infrastructure.azure.adapters;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import lombok.extern.java.Log;
import shared.infrastructure.aws.gateway.AuthorizerRequest;
import shared.infrastructure.aws.gateway.AuthorizerResponse;
import shared.infrastructure.aws.gateway.proxy.ProxyRequest;
import shared.infrastructure.aws.gateway.proxy.ProxyResponse;
import com.amazonaws.services.lambda.runtime.Context;

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

    /**
     * Converts Azure Functions HTTP request to AWS Lambda ProxyRequest format
     */
    public static ProxyRequest convertAzureToAwsRequest(HttpRequestMessage<Optional<String>> azureRequest) {
        ProxyRequest proxyRequest = new ProxyRequest();
        
        // Set basic request properties
        proxyRequest.setHttpMethod(azureRequest.getHttpMethod().toString());
        proxyRequest.setPath(azureRequest.getUri().getPath());
        proxyRequest.setResource(azureRequest.getUri().getPath()); // Use path as resource for simplicity
        
        // Set headers
        proxyRequest.setHeaders(extractHeaders(azureRequest));
        
        // Set query parameters
        proxyRequest.setQueryStringParameters(extractQueryParameters(azureRequest));
        
        // Set body
        if (azureRequest.getBody().isPresent()) {
            proxyRequest.setBody(azureRequest.getBody().get());
        }
        
        // Set default values for AWS-specific fields
        proxyRequest.setPathParameters(new HashMap<>());
        proxyRequest.setStageVariables(new HashMap<>());
        proxyRequest.setIsBase64Encoded(false);
        
        // Create a minimal request context
        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("httpMethod", azureRequest.getHttpMethod().toString());
        requestContext.put("path", azureRequest.getUri().getPath());
        requestContext.put("stage", "prod");
        requestContext.put("requestId", java.util.UUID.randomUUID().toString());
        proxyRequest.setRequestContext(requestContext);
        
        return proxyRequest;
    }

    /**
     * Converts Azure ExecutionContext to AWS Lambda Context
     */
    public static Context convertAzureToAwsContext(com.microsoft.azure.functions.ExecutionContext azureContext) {
        return new AwsContextAdapter(azureContext);
    }

    /**
     * Converts AWS Lambda ProxyResponse to Azure Functions HTTP response
     */
    public static HttpResponseMessage convertAwsToAzureResponse(
            ProxyResponse awsResponse, 
            HttpRequestMessage<Optional<String>> azureRequest) {
        
        // Map AWS status code to Azure HttpStatus
        HttpStatus azureStatus = mapAwsStatusToAzure(awsResponse.getStatusCode());
        
        // Create response builder
        HttpResponseMessage.Builder responseBuilder = azureRequest.createResponseBuilder(azureStatus);
        
        // Add headers from AWS response
        if (awsResponse.getHeaders() != null) {
            awsResponse.getHeaders().forEach(responseBuilder::header);
        }
        
        // Add default CORS headers if not present
        if (awsResponse.getHeaders() == null || !awsResponse.getHeaders().containsKey("Access-Control-Allow-Origin")) {
            responseBuilder.header("Access-Control-Allow-Origin", "*");
        }
        if (awsResponse.getHeaders() == null || !awsResponse.getHeaders().containsKey("Content-Type")) {
            responseBuilder.header("Content-Type", "application/json");
        }
        
        // Set body
        responseBuilder.body(awsResponse.getBody());
        
        return responseBuilder.build();
    }

    /**
     * Maps AWS HTTP status codes to Azure Functions HttpStatus enum
     */
    private static HttpStatus mapAwsStatusToAzure(Integer awsStatusCode) {
        if (awsStatusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        
        switch (awsStatusCode) {
            case 200: return HttpStatus.OK;
            case 201: return HttpStatus.CREATED;
            case 204: return HttpStatus.NO_CONTENT;
            case 400: return HttpStatus.BAD_REQUEST;
            case 401: return HttpStatus.UNAUTHORIZED;
            case 403: return HttpStatus.FORBIDDEN;
            case 404: return HttpStatus.NOT_FOUND;
            case 409: return HttpStatus.CONFLICT;
            case 500: return HttpStatus.INTERNAL_SERVER_ERROR;
            case 502: return HttpStatus.BAD_GATEWAY;
            case 503: return HttpStatus.SERVICE_UNAVAILABLE;
            default: return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
} 