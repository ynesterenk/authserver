package shared.core.validation;

import shared.core.AuthorizationServiceException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ValidationException extends AuthorizationServiceException {

    @Getter
    private final ErrorState errorState;

}
