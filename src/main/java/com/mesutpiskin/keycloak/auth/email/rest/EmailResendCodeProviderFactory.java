package com.mesutpiskin.keycloak.auth.email;

import com.google.auto.service.AutoService;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

@AutoService(RealmResourceProviderFactory.class)
public class EmailResendCodeProviderFactory implements RealmResourceProviderFactory {
  private static final Logger logger = Logger.getLogger(EmailResendCodeProviderFactory.class);

  @Override
  public String getId() {
    return "email";
  }

  @Override
  public RealmResourceProvider create(KeycloakSession session) {
    return new EmailResendCodeProvider(session);
  }

  @Override
  public void init(Scope config) {
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
  }

  @Override
  public void close() {
  }
}
