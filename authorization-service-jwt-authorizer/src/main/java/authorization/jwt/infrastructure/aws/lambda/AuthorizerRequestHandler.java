package authorization.jwt.infrastructure.aws.lambda;

import com.amazonaws.auth.policy.Policy;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jwt.core.Claims;
import jwt.core.Jwt;
import jwt.core.JwtVerificationException;
import jwt.core.PolicyRepository;
import authorization.jwt.infrastructure.aws.Factory;
import shared.infrastructure.aws.gateway.AuthorizerRequest;
import shared.infrastructure.aws.gateway.AuthorizerResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class AuthorizerRequestHandler implements RequestHandler<AuthorizerRequest, AuthorizerResponse> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizerRequestHandler.class);

    private final Factory factory;

    public AuthorizerRequestHandler() {
        this(Factory.getInstance());
    }

    @Override
    public AuthorizerResponse handleRequest(AuthorizerRequest request, Context context) {
        Jwt jwt = factory.createJsonWebToken();
        PolicyRepository rolePolicyRepository = factory.createRolePolicyRepository();

        try {
            Claims claims = jwt.verify(
                request.getAuthorizationToken());
            logger.info("Claims:", claims);
            Policy policy = rolePolicyRepository.getPolicy(claims);

            return new AuthorizerResponse.Builder()
                .withPrincipalId(claims.getUsername())
                .withPolicyDocument(policy)
                .build();

        } catch (JwtVerificationException e) {
            // TODO return 401 Unauthorized response
            logger.error("Jwt Verification Exception", e);
            return null;
        } catch (Exception e) {
            logger.error("Internal Server Error", e);
            throw new RuntimeException(e);
        }
    }

}