package basic.infrastructure.aws.lambda;

import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.services.cognitoidp.model.NotAuthorizedException;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import basic.core.HttpBasicAuthenticator;
import basic.core.Principal;
import basic.infrastructure.aws.Factory;
import shared.core.http.BasicAuthenticationException;
import shared.infrastructure.aws.gateway.AuthorizerRequest;
import shared.infrastructure.aws.gateway.AuthorizerResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Collectors;

@AllArgsConstructor
public class AuthorizerRequestHandler implements RequestHandler<AuthorizerRequest, AuthorizerResponse> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizerRequestHandler.class);

    private final Factory factory;

    public AuthorizerRequestHandler() {
        this(Factory.getInstance());
    }

    @Override
    public AuthorizerResponse handleRequest(AuthorizerRequest request, Context context) {
        Statement.Effect effect = Statement.Effect.Deny;
        HttpBasicAuthenticator authenticator = factory.createHttpBasicAuthenticator();

        Principal principal = new Principal();
        try {
            principal = authenticator.authenticate(request.getAuthorizationToken());

            effect = Statement.Effect.Allow;
        } catch (BasicAuthenticationException e) {
            logger.warn("Validation error. {}", e.getMessage());
        } catch (UserNotFoundException | NotAuthorizedException e) {
            logger.warn("AWS Cognito error. {}", e.getErrorMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }

        return new AuthorizerResponse.Builder()
            .withPrincipalId(principal.getId())
            .withPolicyDocument(new Policy()
                .withStatements(new Statement(effect)
                    .withActions(() -> "execute-api:Invoke")
                    .withResources(new Resource(request.getMethodArn()))))
            .withContextAsString("username", principal.getUsername())
            .withContextAsString("scope", Optional.ofNullable(principal.getScope())
                .map(o -> o.stream().collect(Collectors.joining(","))).orElse(null))
            .withContextAsNumber("expirationTime", principal.getExpirationTime())
            .build();
    }

}
