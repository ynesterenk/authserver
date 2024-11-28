package server.infrastructure.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import server.core.Facade;
import server.core.Route;
import server.core.Router;
import server.infrastructure.aws.Factory;
import shared.core.validation.ValidationException;
import shared.infrastructure.aws.gateway.proxy.ProxyError;
import shared.infrastructure.aws.gateway.proxy.ProxyRequest;
import shared.infrastructure.aws.gateway.proxy.ProxyRequestTranslator;
import shared.infrastructure.aws.gateway.proxy.ProxyResponse;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class ProxyRequestHandler implements RequestHandler<ProxyRequest, ProxyResponse> {

    private static final Logger logger = LoggerFactory.getLogger(ProxyRequestHandler.class);

    private final Factory factory;

    public ProxyRequestHandler() {
        this(Factory.getInstance());
    }

    @Override
    public ProxyResponse handleRequest(ProxyRequest o, Context context) {
        final ProxyRequest request = ProxyRequestTranslator.ofNullable(o);
        logger.debug("invoke lambda with {}", request);

        final Route route = Router.match(request);
        final Facade facade;
        switch (route) {
            case CHANGE_PASSWORD:
                facade = factory.createChangePasswordFacade();
                break;

            case OAUTH2_CLIENT_CREDENTIALS:
                facade = factory.createClientCredentialsFacade();
                break;

            case NOT_FOUND:
                return new ProxyError.Builder()
                    .withStatusCode(HttpStatus.SC_NOT_FOUND)
                    .withRequestId(context.getAwsRequestId())
                    .build();

            default:
                throw new IllegalStateException();
        }

        try {
            return facade.process(request);
        } catch (ValidationException e) {
            return new ProxyError.Builder()
                .withStatusCode(HttpStatus.SC_BAD_REQUEST)
                .withErrorState(e.getErrorState())
                .withRequestId(context.getAwsRequestId())
                .build();
        } catch (Exception e) {
            logger.error("Internal Server Error", e);
            throw new RuntimeException(e);
        }
    }

}
