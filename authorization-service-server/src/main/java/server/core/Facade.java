package server.core;

import shared.infrastructure.aws.gateway.proxy.ProxyRequest;
import shared.infrastructure.aws.gateway.proxy.ProxyResponse;

public interface Facade {

    ProxyResponse process(ProxyRequest request) throws Exception;

}
