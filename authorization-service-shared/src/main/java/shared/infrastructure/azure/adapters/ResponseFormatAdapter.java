package shared.infrastructure.azure.adapters;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that maintains exact AWS API Gateway response format for Azure Functions
 * to ensure compatibility with existing clients and infrastructure.
 */
public class ResponseFormatAdapter {

    /**
     * Creates an AWS API Gateway compatible success response
     */
    public static HttpResponseMessage createSuccessResponse(
            HttpRequestMessage<Optional<String>> request,
            Object body) {
        
        Map<String, Object> awsResponse = new HashMap<>();
        awsResponse.put("statusCode", 200);
        awsResponse.put("body", body);
        awsResponse.put("headers", getDefaultHeaders());
        awsResponse.put("isBase64Encoded", false);
        
        return addDefaultHeaders(request.createResponseBuilder(HttpStatus.OK))
                .body(awsResponse)
                .build();
    }

    /**
     * Creates an AWS API Gateway compatible error response
     */
    public static HttpResponseMessage createErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            int statusCode,
            String errorMessage,
            String errorType) {
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("errorMessage", errorMessage);
        errorBody.put("errorType", errorType);
        
        Map<String, Object> awsResponse = new HashMap<>();
        awsResponse.put("statusCode", statusCode);
        awsResponse.put("body", errorBody);
        awsResponse.put("headers", getDefaultHeaders());
        awsResponse.put("isBase64Encoded", false);
        
        HttpStatus azureStatus = mapHttpStatusCode(statusCode);
        
        return addDefaultHeaders(request.createResponseBuilder(azureStatus))
                .body(awsResponse)
                .build();
    }

    /**
     * Creates an AWS API Gateway compatible unauthorized response (401)
     */
    public static HttpResponseMessage createUnauthorizedResponse(
            HttpRequestMessage<Optional<String>> request,
            String message) {
        
        return createErrorResponse(request, 401, message, "Unauthorized");
    }

    /**
     * Creates an AWS API Gateway compatible forbidden response (403)
     */
    public static HttpResponseMessage createForbiddenResponse(
            HttpRequestMessage<Optional<String>> request,
            String message) {
        
        return createErrorResponse(request, 403, message, "Forbidden");
    }

    /**
     * Creates an AWS API Gateway compatible bad request response (400)
     */
    public static HttpResponseMessage createBadRequestResponse(
            HttpRequestMessage<Optional<String>> request,
            String message) {
        
        return createErrorResponse(request, 400, message, "BadRequest");
    }

    /**
     * Creates an AWS API Gateway compatible internal server error response (500)
     */
    public static HttpResponseMessage createInternalServerErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            String message) {
        
        return createErrorResponse(request, 500, message, "InternalServerError");
    }

    /**
     * Creates an AWS API Gateway compatible authorizer response
     */
    public static HttpResponseMessage createAuthorizerResponse(
            HttpRequestMessage<Optional<String>> request,
            String principalId,
            Map<?, ?> policyDocument,
            Map<String, ?> context) {
        
        Map<String, Object> authorizerBody = new HashMap<>();
        authorizerBody.put("principalId", principalId);
        authorizerBody.put("policyDocument", policyDocument);
        authorizerBody.put("context", context);
        
        Map<String, Object> awsResponse = new HashMap<>();
        awsResponse.put("statusCode", 200);
        awsResponse.put("body", authorizerBody);
        awsResponse.put("headers", getDefaultHeaders());
        awsResponse.put("isBase64Encoded", false);
        
        return addDefaultHeaders(request.createResponseBuilder(HttpStatus.OK))
                .body(awsResponse)
                .build();
    }

    /**
     * Creates an AWS API Gateway compatible CORS preflight response
     */
    public static HttpResponseMessage createCorsPreflightResponse(
            HttpRequestMessage<Optional<String>> request) {
        
        Map<String, String> corsHeaders = new HashMap<>(getDefaultHeaders());
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        corsHeaders.put("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Amz-Date, X-Api-Key, X-Amz-Security-Token");
        corsHeaders.put("Access-Control-Max-Age", "86400");
        
        Map<String, Object> awsResponse = new HashMap<>();
        awsResponse.put("statusCode", 200);
        awsResponse.put("body", "");
        awsResponse.put("headers", corsHeaders);
        awsResponse.put("isBase64Encoded", false);
        
        return addHeaders(request.createResponseBuilder(HttpStatus.OK), corsHeaders)
                .body(awsResponse)
                .build();
    }

    /**
     * Creates a custom response with AWS API Gateway format
     */
    public static HttpResponseMessage createCustomResponse(
            HttpRequestMessage<Optional<String>> request,
            int statusCode,
            Object body,
            Map<String, String> additionalHeaders) {
        
        Map<String, String> headers = new HashMap<>(getDefaultHeaders());
        if (additionalHeaders != null) {
            headers.putAll(additionalHeaders);
        }
        
        Map<String, Object> awsResponse = new HashMap<>();
        awsResponse.put("statusCode", statusCode);
        awsResponse.put("body", body);
        awsResponse.put("headers", headers);
        awsResponse.put("isBase64Encoded", false);
        
        HttpStatus azureStatus = mapHttpStatusCode(statusCode);
        
        return addHeaders(request.createResponseBuilder(azureStatus), headers)
                .body(awsResponse)
                .build();
    }

    /**
     * Gets default CORS headers that match AWS API Gateway behavior
     */
    private static Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Credentials", "true");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Adds default headers to the response builder
     */
    private static HttpResponseMessage.Builder addDefaultHeaders(HttpResponseMessage.Builder builder) {
        return builder
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Credentials", "true");
    }

    /**
     * Adds custom headers to the response builder
     */
    private static HttpResponseMessage.Builder addHeaders(HttpResponseMessage.Builder builder, Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder;
    }

    /**
     * Maps HTTP status codes to Azure Functions HttpStatus enum
     */
    private static HttpStatus mapHttpStatusCode(int statusCode) {
        switch (statusCode) {
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

    /**
     * Extracts the original request information for logging and debugging
     */
    public static Map<String, Object> extractRequestInfo(HttpRequestMessage<Optional<String>> request) {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("method", request.getHttpMethod().toString());
        requestInfo.put("uri", request.getUri().toString());
        requestInfo.put("headers", request.getHeaders());
        requestInfo.put("queryParameters", request.getQueryParameters());
        
        if (request.getBody().isPresent()) {
            requestInfo.put("body", request.getBody().get());
        }
        
        return requestInfo;
    }
} 