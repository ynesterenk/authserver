package server.core;

import shared.infrastructure.aws.gateway.proxy.ProxyRequest;

import java.util.Arrays;

public final class Router {

    public static Route match(ProxyRequest request) {
        return Arrays.stream(Route.values())
            .filter(route -> route.getPath()
                .matcher(request.getPath())
                .matches())
            .filter(route -> route.getHttpMethod()
                .matcher(request.getHttpMethod())
                .matches())
            .findFirst().orElse(Route.NOT_FOUND);
    }

}
