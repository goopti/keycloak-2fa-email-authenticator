package com.mesutpiskin.keycloak.auth.email;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EmailConstants {
	public String EMAIL = "email";
	public String RESEND_CODE_COUNT = "resendCodeCount";
	public String RESEND_CODE_LIMIT = "resendCodeLimit";
	public String CODE = "emailCode";
	public String CODE_LENGTH = "length";
	public String CODE_TTL = "ttl";
	public String CODE_RESENT_SUCCESSFULLY = "codeResentSuccessfully";

	public int DEFAULT_LENGTH = 6;
	public int DEFAULT_TTL = 300;
	public int DEFAULT_RESEND_CODE_LIMIT = 5;
}
