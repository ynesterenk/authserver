package server.core.translator;

import com.amazonaws.util.StringUtils;
import com.amazonaws.util.json.Jackson;
import server.core.model.ChangePasswordRequest;
import shared.core.http.FormUrlencodedScheme;
import shared.infrastructure.aws.gateway.proxy.ProxyRequest;

public class ChangePasswordRequestTranslator {

    public static ChangePasswordRequest from(ProxyRequest proxyRequest) {
        ProxyRequest request = FormUrlencodedScheme.decode(proxyRequest);

        return Jackson.fromJsonString(
            StringUtils.isNullOrEmpty(request.getBody()) ? "{}" : request.getBody(),
            ChangePasswordRequest.class);
    }

}
