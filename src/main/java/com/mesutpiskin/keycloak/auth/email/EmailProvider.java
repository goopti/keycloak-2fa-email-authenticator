package com.mesutpiskin.keycloak.auth.email;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JBossLog
public class EmailProvider {
  private final EmailTemplateProvider emailTemplateProvider;

  public EmailProvider(EmailTemplateProvider emailTemplateProvider) {
    this.emailTemplateProvider = emailTemplateProvider;
  }

  public void sendEmailWithCode(RealmModel realm, UserModel user, String code, int ttl) {
    if (user.getEmail() == null) {
      log.warnf("Could not send access code email due to missing email. realm=%s user=%s", realm.getId(), user.getUsername());
      return;
    }

    Map<String, Object> mailBodyAttributes = new HashMap<>();
    mailBodyAttributes.put("username", user.getUsername());
    mailBodyAttributes.put("code", code);
    mailBodyAttributes.put("ttl", ttl);

    String realmName = realm.getDisplayName() != null ? realm.getDisplayName() : realm.getName();
    List<Object> subjectParams = List.of(realmName);
    try {
      EmailTemplateProvider emailProvider = emailTemplateProvider;
      emailProvider.setRealm(realm);
      emailProvider.setUser(user);

      // Don't forget to add the welcome-email.ftl (html and text) template to your theme.
      emailProvider.send("emailCodeSubject", subjectParams, "code-email.ftl", mailBodyAttributes);
    } catch (EmailException eex) {
      log.errorf(eex, "Failed to send access code email. realm=%s user=%s", realm.getId(), user.getUsername());
    }
  }
}
