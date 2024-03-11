package com.mesutpiskin.keycloak.auth.username;
import com.mesutpiskin.keycloak.auth.email.EmailConstants;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.LoginFormsUtil;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.utils.EmailValidationUtil;

@JBossLog
public class PassthroughUsernameForm extends UsernamePasswordForm {

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    if (context.getUser() != null) {
      // We can skip the form when user is re-authenticating. Unless current user has some IDP set, so he can re-authenticate with that IDP
      List<IdentityProviderModel> identityProviders = LoginFormsUtil
              .filterIdentityProviders(context.getRealm().getIdentityProvidersStream(), context.getSession(), context);
      if (identityProviders.isEmpty()) {
        context.success();
        return;
      }
    }
    super.authenticate(context);
  }

  @Override
  protected boolean validateForm(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
    boolean isUserValid = validateUser(context, formData);
    UserModel user = context.getUser();

    String email = formData.getFirst("username");
    log.info("email:" + email +":");

    boolean isInvalidEmail = !EmailValidationUtil.isValidEmail(email);

    if (isInvalidEmail) {
      context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
      Response challengeResponse = challenge(context, Messages.INVALID_EMAIL);
      context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challengeResponse);

      return false;
    }

    AuthenticationSessionModel session = context.getAuthenticationSession();
    session.setAuthNote(EmailConstants.EMAIL, email);

    return user == null || isUserValid;
  }

  @Override
  public boolean enabledUser(AuthenticationFlowContext context, UserModel user) {
    return true;
  }

  @Override
  protected Response challenge(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
    LoginFormsProvider forms = context.form();

    if (!formData.isEmpty()) forms.setFormData(formData);

    return forms.createLoginUsername();
  }

  @Override
  protected Response createLoginForm(LoginFormsProvider form) {
    return form.createLoginUsername();
  }
  @Override
  public void testInvalidUser(AuthenticationFlowContext context, UserModel user) {
    // Do not raise error if user is not found
  }
}