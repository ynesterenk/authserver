package basic.core;

import shared.core.http.BasicAuthenticationException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HttpBasicAuthenticatorTest {

    @Mock
    private UserPool mockUserPool;

    private HttpBasicAuthenticator basicAuthenticator;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        basicAuthenticator = new HttpBasicAuthenticator(mockUserPool);
        String accessKey = new BufferedReader(new InputStreamReader(
            HttpBasicAuthenticatorTest.class.getResourceAsStream("/CognitoAccessKey.jwt")))
            .lines().collect(Collectors.joining());
        Mockito.when(mockUserPool.verify(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(accessKey);
    }

    @Test
    public void testAuth() {
        Principal actual = basicAuthenticator.authenticate(
            "Basic aHR0cHdhdGNoOmY=");

        Assert.assertNotNull(actual);
        Assert.assertEquals(actual.getId(), "1234567890");
        Assert.assertEquals(actual.getUsername(), "admin");
        Assert.assertEquals(actual.getScope(), Arrays.asList("foo", "bar"));
        Assert.assertEquals(actual.getExpirationTime(), (Long) 1234567890000L);
        Mockito.verify(mockUserPool).verify("httpwatch", "f");
    }

    @DataProvider
    public Object[][] samples() {
        return new Object[][]{
            {null}, {""}, {"Basic aHR0cHdhdGNoOmY"}, {"Basic"}, {"aHR0cHdhdGNoOmY="},
            {"Base aHR0cHdhdGNoOmY="}, {"Basic YWFhOmJiYjpjY2M="}, {"Basic Og=="},
            {"Basic YTo="}, {"Basic OmE="}
        };
    }

    @Test(dataProvider = "samples", expectedExceptions = BasicAuthenticationException.class)
    public void testFailAuth(String header) {
        Principal actual = basicAuthenticator.authenticate(header);

        Assert.assertNull(actual);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testFailAuth() {
        Mockito.reset(mockUserPool);
        Mockito.when(mockUserPool.verify("httpwatch", "f"))
            .thenThrow(RuntimeException.class);

        Principal actual = basicAuthenticator.authenticate("Basic aHR0cHdhdGNoOmY=");

        Assert.assertNull(actual);
    }

}