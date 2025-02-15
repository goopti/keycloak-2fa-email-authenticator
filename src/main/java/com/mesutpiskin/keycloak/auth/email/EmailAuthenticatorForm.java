package com.mesutpiskin.keycloak.auth.email;

import lombok.extern.jbosslog.JBossLog;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.common.util.SecretGenerator;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.CompletableFuture;

@JBossLog
public class EmailAuthenticatorForm extends AbstractUsernameFormAuthenticator {
    private final KeycloakSession session;

    public EmailAuthenticatorForm(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        challenge(context, null);
    }

    @Override
    protected Response challenge(AuthenticationFlowContext context, String error, String field) {
        generateAndSendEmailCode(context);

        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        if (error != null) {
            if (field != null) {
                form.addError(new FormMessage(field, error));
            } else {
                form.setError(error);
            }
        }

        return createResponse(context, form);
    }

    protected void challengeSuccess(AuthenticationFlowContext context, String successMessage, String field) {
        generateAndSendEmailCode(context);

        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        if (successMessage != null) {
            if (field != null) {
                form.addSuccess(new FormMessage(field, successMessage));
            } else {
                form.setSuccess(successMessage);
            }
        }

        createResponse(context, form);
    }

    private Response createResponse(AuthenticationFlowContext context, LoginFormsProvider form) {
        String email = context.getAuthenticationSession().getAuthNote(EmailConstants.EMAIL);
        form.setAttribute(EmailConstants.EMAIL, email);
        int codeLength = getCodeLength(context.getAuthenticatorConfig());
        form.setAttribute(EmailConstants.CODE_LENGTH, codeLength);

        Response response = form.createForm("email-code-form.ftl");
        context.challenge(response);

        return response;
    }

    private void generateAndSendEmailCode(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        AuthenticationSessionModel session = context.getAuthenticationSession();

        if (session.getAuthNote(EmailConstants.CODE) != null) {
            // skip sending email code
            return;
        }

        int length = getCodeLength(config);
        int ttl = getTtl(config);

        String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);

        UserModel user = context.getUser();
        RealmModel realm = context.getRealm();

        if (enabledUser(context, user)) {
            sendEmailWithCodeAsync(realm, user, code, ttl);
        }

        session.setAuthNote(EmailConstants.CODE, code);
        session.setAuthNote(EmailConstants.CODE_TTL, Long.toString(System.currentTimeMillis() + (ttl * 1000L)));
    }

    private int getCodeLength (AuthenticatorConfigModel config) {
        return config != null ? Integer.parseInt(config.getConfig().get(EmailConstants.CODE_LENGTH)) : EmailConstants.DEFAULT_LENGTH;
    }

    private int getTtl (AuthenticatorConfigModel config) {
        return config != null ? Integer.parseInt(config.getConfig().get(EmailConstants.CODE_TTL)) : EmailConstants.DEFAULT_TTL;
    }

    private void sendEmailWithCodeAsync(RealmModel realm, UserModel user, String code, int ttl) {
        EmailTemplateProvider emailTemplateProvider = this.session.getProvider(EmailTemplateProvider.class);
        EmailProvider emailProvider = new EmailProvider(emailTemplateProvider);

        CompletableFuture.runAsync(() -> {
            try {
                emailProvider.sendEmailWithCode(realm, user, code, ttl);
            } catch (Exception e) {
                log.error(e);
            }
        });
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        UserModel userModel = context.getUser();
        AuthenticationSessionModel session = context.getAuthenticationSession();

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        if (formData.containsKey("resend")) {
            int codeResendCount = ResendCodeLimitService.increaseCodeResendCount(session);
            boolean resendCodeLimitReached = ResendCodeLimitService.codeResendLimitReached(context.getAuthenticatorConfig(), codeResendCount);

            if (resendCodeLimitReached) {
                context.getEvent().user(userModel).error(Errors.SLOW_DOWN);
                Response challengeResponse = challenge(context, Messages.EMAIL_SENT_ERROR);
                context.failureChallenge(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR, challengeResponse);

                return;
            }

            resetEmailCode(context);
            challengeSuccess(context, EmailConstants.CODE_RESENT_SUCCESSFULLY_MESSAGE, null);

            return;
        }

        if (formData.containsKey("cancel")) {
            resetEmailCode(context);
            context.resetFlow();
            return;
        }


        String code = session.getAuthNote(EmailConstants.CODE);
        String ttl = session.getAuthNote(EmailConstants.CODE_TTL);
        String enteredCode = formData.getFirst(EmailConstants.CODE);

        if (enteredCode.equals(code) && enabledUser(context, userModel)) {
            if (Long.parseLong(ttl) < System.currentTimeMillis()) {
                // expired
                context.getEvent().user(userModel).error(Errors.EXPIRED_CODE);
                Response challengeResponse = challenge(context, Messages.EXPIRED_ACTION_TOKEN_SESSION_EXISTS);
                context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE, challengeResponse);
            } else {
                // valid
                resetEmailCode(context);
                context.success();
            }
        } else {
            // invalid
            AuthenticationExecutionModel execution = context.getExecution();
            if (execution.isRequired()) {
                context.getEvent().user(userModel).error(Errors.INVALID_USER_CREDENTIALS);
                Response challengeResponse = challenge(context, Messages.INVALID_ACCESS_CODE);
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challengeResponse);
            } else if (execution.isConditional() || execution.isAlternative()) {
                context.attempted();
            }
        }
    }

    @Override
    public boolean enabledUser(AuthenticationFlowContext context, UserModel user) {
        boolean validUser = user != null && user.isEnabled();

        return validUser && AuthenticatorUtils.getDisabledByBruteForceEventError(context, user) == null;
    }

    @Override
    protected String disabledByBruteForceError() {
        return Messages.INVALID_ACCESS_CODE;
    }

    private void resetEmailCode(AuthenticationFlowContext context) {
        context.getAuthenticationSession().removeAuthNote(EmailConstants.CODE);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }
}
