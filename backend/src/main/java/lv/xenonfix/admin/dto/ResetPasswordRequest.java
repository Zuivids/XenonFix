package lv.xenonfix.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lv.xenonfix.common.PasswordRules;

public record ResetPasswordRequest(
		@NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH) String password) {

	@Override
	public String toString() {
		return "ResetPasswordRequest[]";
	}

}
