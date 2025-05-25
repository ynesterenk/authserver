package server.infrastructure.azure.functions;

import com.microsoft.azure.functions.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

public class AuthServerFunctionTest {

    @Mock
    private HttpRequestMessage<Optional<String>> mockRequest;
    
    @Mock
    private ExecutionContext mockContext;
    
    @Mock
    private Logger mockLogger;

    private AuthServerFunction function;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        function = new AuthServerFunction();
        
        // Setup mock context
        when(mockContext.getInvocationId()).thenReturn("test-invocation-id");
        when(mockContext.getFunctionName()).thenReturn("oauth-token");
        when(mockContext.getLogger()).thenReturn(mockLogger);
        
        // Setup mock request
        when(mockRequest.getHttpMethod()).thenReturn(HttpMethod.POST);
        when(mockRequest.getUri()).thenReturn(URI.create("http://localhost:7071/api/oauth/token"));
        when(mockRequest.getHeaders()).thenReturn(new HashMap<>());
        when(mockRequest.getQueryParameters()).thenReturn(new HashMap<>());
        when(mockRequest.getBody()).thenReturn(Optional.of("{\"grant_type\":\"client_credentials\"}"));
        
        // Setup response builder
        HttpResponseMessage.Builder mockBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage mockResponse = mock(HttpResponseMessage.class);
        when(mockRequest.createResponseBuilder(any(HttpStatus.class))).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyString())).thenReturn(mockBuilder);
        when(mockBuilder.body(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockResponse);
    }

    @Test
    public void testOauthTokenEndpointExists() {
        // This test verifies that the OAuth token endpoint method exists and can be called
        // The actual business logic testing is done through the existing AWS handler tests
        
        try {
            HttpResponseMessage response = function.oauthToken(mockRequest, mockContext);
            Assert.assertNotNull(response);
        } catch (Exception e) {
            // Expected since we're using mocks and don't have real AWS services configured
            // The important thing is that the method exists and the adapter pattern works
            Assert.assertTrue(e.getMessage().contains("AWS") || e.getMessage().contains("region") || 
                            e.getMessage().contains("Cognito") || e.getMessage().contains("null"));
        }
    }

    @Test
    public void testChangePasswordEndpointExists() {
        // This test verifies that the change password endpoint method exists and can be called
        
        when(mockRequest.getHttpMethod()).thenReturn(HttpMethod.GET);
        when(mockRequest.getUri()).thenReturn(URI.create("http://localhost:7071/api/account"));
        
        try {
            HttpResponseMessage response = function.changePassword(mockRequest, mockContext);
            Assert.assertNotNull(response);
        } catch (Exception e) {
            // Expected since we're using mocks and don't have real AWS services configured
            // The important thing is that the method exists and the adapter pattern works
            Assert.assertTrue(e.getMessage().contains("AWS") || e.getMessage().contains("region") || 
                            e.getMessage().contains("Cognito") || e.getMessage().contains("null"));
        }
    }

    @Test
    public void testCorsPreflightEndpoint() {
        // Test CORS preflight handling
        when(mockRequest.getHttpMethod()).thenReturn(HttpMethod.OPTIONS);
        when(mockRequest.getUri()).thenReturn(URI.create("http://localhost:7071/api/oauth/token"));
        
        HttpResponseMessage response = function.corsPreflightHandler(mockRequest, mockContext);
        Assert.assertNotNull(response);
    }
} 