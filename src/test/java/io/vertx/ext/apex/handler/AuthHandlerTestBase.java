/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.apex.handler;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.apex.ApexTestBase;
import io.vertx.ext.apex.sstore.LocalSessionStore;
import io.vertx.ext.apex.sstore.SessionStore;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AuthHandlerTestBase extends ApexTestBase {

  @Test
  public void testAuthRoles() throws Exception {
    Set<String> roles = new HashSet<>();
    roles.add("morris_dancer");
    testAuthorisation("tim", false, roles, null);
  }

  @Test
  public void testAuthRolesFail() throws Exception {
    Set<String> roles = new HashSet<>();
    roles.add("knitter");
    testAuthorisation("tim", true, roles, null);
  }

  @Test
  public void testAuthPermissions() throws Exception {
    Set<String> perms = new HashSet<>();
    perms.add("do_actual_work");
    testAuthorisation("tim", false, null, perms);
  }

  @Test
  public void testAuthPermissionsFail() throws Exception {
    Set<String> perms = new HashSet<>();
    perms.add("eat_biscuit");
    testAuthorisation("tim", true, null, perms);
  }

  protected abstract AuthHandler createAuthHandler(AuthProvider authProvider);

  protected boolean requiresSession() {
    return false;
  }

  protected SessionStore getSessionStore() {
    return LocalSessionStore.create(vertx);
  }

  protected void testAuthorisation(String username, boolean fail, Set<String> roles, Set<String> permissions) throws Exception {
    if (requiresSession()) {
      router.route().handler(BodyHandler.create());
      router.route().handler(CookieHandler.create());
      SessionStore store = getSessionStore();
      router.route().handler(SessionHandler.create(store));
    }
    JsonObject authConfig = new JsonObject().put("properties_path", "classpath:login/loginusers.properties");
    AuthProvider authProvider = ShiroAuth.create(vertx, ShiroAuthRealmType.PROPERTIES, authConfig);
    AuthHandler authHandler = createAuthHandler(authProvider);
    if (roles != null) {
      authHandler.addRoles(roles);
    }
    if (permissions != null) {
      authHandler.addPermissions(permissions);
    }
    router.route().handler(rc -> {
      // we need to be logged in
      if (rc.user() == null) {
        JsonObject authInfo = new JsonObject().put("username", username).put("password", "sausages");
        authProvider.authenticate(authInfo, res -> {
          if (res.succeeded()) {
            rc.setUser(res.result());
            rc.next();
          } else {
            rc.fail(res.cause());
          }
        });
      }
    });
    router.route().handler(authHandler);
    router.route().handler(rc -> rc.response().end());

    testRequest(HttpMethod.GET, "/", fail ? 403: 200, fail? "Forbidden": "OK");
  }
}
