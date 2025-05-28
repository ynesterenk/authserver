package server.infrastructure.azure;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import server.core.Facade;
import server.core.Route;
import server.core.Router;
import server.infrastructure.azure.AzureFactory;
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
public class AzureProxyRequestHandler implements RequestHandler<ProxyRequest, ProxyResponse> {

    private static final Logger logger = LoggerFactory.getLogger(AzureProxyRequestHandler.class);

    private final AzureFactory azureFactory;

    public AzureProxyRequestHandler() {
        this(AzureFactory.getInstance());
    }

    @Override
    public ProxyResponse handleRequest(ProxyRequest o, Context context) {
        final ProxyRequest request = ProxyRequestTranslator.ofNullable(o);
        logger.debug("invoke Azure function with {}", request);

        final Route route = Router.match(request);
        final Facade facade;
        switch (route) {
            case CHANGE_PASSWORD:
                facade = azureFactory.createChangePasswordFacade();
                break;

            case OAUTH2_CLIENT_CREDENTIALS:
                facade = azureFactory.createClientCredentialsFacade();
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
