package com.github.vitalibo.authorization.server.core;

import com.github.vitalibo.authorization.shared.core.Principal;

public interface UserPool {

    Principal authenticate(String username, String password) throws UserPoolException;

    boolean changePassword(Principal principal, String newPassword) throws UserPoolException;

}