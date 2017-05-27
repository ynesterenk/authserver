package com.github.vitalibo.auth.server.core.facade;

import com.github.vitalibo.auth.core.ErrorState;
import com.github.vitalibo.auth.core.HttpError;
import com.github.vitalibo.auth.core.Principal;
import com.github.vitalibo.auth.core.Rule;
import com.github.vitalibo.auth.infrastructure.aws.gateway.proxy.ProxyRequest;
import com.github.vitalibo.auth.infrastructure.aws.gateway.proxy.ProxyResponse;
import com.github.vitalibo.auth.server.core.Facade;
import com.github.vitalibo.auth.server.core.UserPool;
import com.github.vitalibo.auth.server.core.UserPoolException;
import com.github.vitalibo.auth.server.core.model.OAuth2Request;
import com.github.vitalibo.auth.server.core.model.OAuth2Response;
import com.github.vitalibo.auth.server.core.translator.OAuth2RequestTranslator;
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
    private final Collection<Rule<OAuth2Request>> postRules;

    @Override
    public ProxyResponse process(ProxyRequest request) {
        preRules.forEach(rule -> rule.accept(request, errorState));
        if (errorState.hasErrors()) {
            throw errorState;
        }

        try {
            OAuth2Response response = process(
                OAuth2RequestTranslator.from(request));

            return new ProxyResponse.Builder()
                .withStatusCode(HttpStatus.SC_OK)
                .withBody(response)
                .build();

        } catch (UserPoolException e) {
            ErrorState errorState = new ErrorState();
            errorState.addError("authorization", e.getMessage());
            return new HttpError.Builder()
                .withStatusCode(HttpStatus.SC_UNAUTHORIZED)
                .withErrorState(errorState)
                .build()
                .asProxyResponse();
        }
    }

    OAuth2Response process(OAuth2Request request) throws UserPoolException {
        postRules.forEach(rule -> rule.accept(request, errorState));
        if (errorState.hasErrors()) {
            throw errorState;
        }

        Principal principal = userPool.authenticate(
            request.getClientId(), request.getClientSecret());

        OAuth2Response response = new OAuth2Response();
        response.setTokenType("Bearer");
        response.setAccessToken(principal.getAccessToken());
        response.setExpiresIn(
            ZonedDateTime.now(ZoneId.of("UTC")).plusHours(1)
                .toEpochSecond());
        return response;
    }

}