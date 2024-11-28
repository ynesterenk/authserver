package server.core.facade;

import server.core.Facade;
import server.core.UserPool;
import server.core.UserPoolException;
import server.core.model.ClientCredentialsRequest;
import server.core.model.ClientCredentialsResponse;
import server.core.translator.ClientCredentialsRequestTranslator;
import shared.core.validation.ErrorState;
import shared.core.validation.Rule;
import shared.core.validation.ValidationException;
import shared.infrastructure.aws.gateway.proxy.ProxyError;
import shared.infrastructure.aws.gateway.proxy.ProxyRequest;
import shared.infrastructure.aws.gateway.proxy.ProxyResponse;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;

@RequiredArgsConstructor
public class ClientCredentialsFacade implements Facade {

    private final ErrorState errorState;
    private final UserPool userPool;
    private final Collection<Rule<ProxyRequest>> preRules;
    private final Collection<Rule<ClientCredentialsRequest>> postRules;

    @Override
    public ProxyResponse process(ProxyRequest request) {
        preRules.forEach(rule -> rule.accept(request, errorState));
        if (errorState.hasErrors()) {
            throw new ValidationException(errorState);
        }

        try {
            ClientCredentialsResponse response = process(
                ClientCredentialsRequestTranslator.from(request));

            return new ProxyResponse.Builder()
                .withStatusCode(HttpStatus.SC_OK)
                .withBody(response)
                .build();

        } catch (UserPoolException e) {
            ErrorState errorState = new ErrorState();
            errorState.addError("authorization", e.getMessage());
            return new ProxyError.Builder()
                .withStatusCode(HttpStatus.SC_UNAUTHORIZED)
                .withErrorState(errorState)
                .build();
        }
    }

    ClientCredentialsResponse process(ClientCredentialsRequest request) throws UserPoolException {
        postRules.forEach(rule -> rule.accept(request, errorState));
        if (errorState.hasErrors()) {
            throw new ValidationException(errorState);
        }

        String accessToken = userPool.authenticate(
            request.getClientId(), request.getClientSecret());

        ClientCredentialsResponse response = new ClientCredentialsResponse();
        response.setTokenType("Bearer");
        response.setAccessToken(accessToken);
        response.setExpiresIn(
            ZonedDateTime.now(ZoneId.of("UTC")).plusHours(1)
                .toEpochSecond());
        return response;
    }

}
