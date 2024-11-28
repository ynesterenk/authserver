package server.core.translator;

import com.amazonaws.util.StringUtils;
import com.amazonaws.util.json.Jackson;
import server.core.model.ClientCredentialsRequest;
import shared.core.http.BasicAuthenticationException;
import shared.core.http.BasicScheme;
import shared.core.http.Credentials;
import shared.core.http.FormUrlencodedScheme;
import shared.infrastructure.aws.gateway.proxy.ProxyRequest;

public class ClientCredentialsRequestTranslator {

    public static ClientCredentialsRequest from(ProxyRequest o) {
        ProxyRequest proxyRequest = FormUrlencodedScheme.decode(o);

        ClientCredentialsRequest request = Jackson.fromJsonString(
            StringUtils.isNullOrEmpty(proxyRequest.getBody()) ? "{}" : proxyRequest.getBody(),
            ClientCredentialsRequest.class);

        if (!StringUtils.isNullOrEmpty(request.getClientId()) &&
            !StringUtils.isNullOrEmpty(request.getClientSecret())) {

            return request;
        }

        try {
            Credentials credentials = BasicScheme.decode(proxyRequest.getHeaders());

            request.setClientId(credentials.getUsername());
            request.setClientSecret(credentials.getPassword());
        } catch (BasicAuthenticationException ignored) {
        }

        return request;
    }

}
