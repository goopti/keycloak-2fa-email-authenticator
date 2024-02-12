package com.mesutpiskin.keycloak.auth.email;

import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.sessions.AuthenticationSessionModel;

public class ResendCodeLimitService {

  public static boolean codeResendLimitReached(AuthenticatorConfigModel config, int codeResendCount) {
    int codeResendLimit = EmailConstants.DEFAULT_RESEND_CODE_LIMIT;

    if (config != null) {
      codeResendLimit = Integer.parseInt(config.getConfig().get(EmailConstants.RESEND_CODE_LIMIT));
    }

    return codeResendCount > codeResendLimit;
  }

  public static int increaseCodeResendCount(AuthenticationSessionModel session) {
    int codeResendCount = 0;
    String codeResetAuthNote = session.getAuthNote(EmailConstants.RESEND_CODE_COUNT);

    if (codeResetAuthNote != null) {
      codeResendCount = Integer.parseInt(codeResetAuthNote);
    }

    int increasedCodeResendCount = codeResendCount + 1;
    session.setAuthNote(EmailConstants.RESEND_CODE_COUNT, String.valueOf(increasedCodeResendCount));

    return increasedCodeResendCount;
  }
}
