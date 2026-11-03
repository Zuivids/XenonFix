package lv.xenonfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lv.xenonfix.common.PasswordRules;

public record ChangePasswordRequest(@NotBlank @Size(max = PasswordRules.MAX_LENGTH) String currentPassword,
		@NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH) String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[]";
	}

}
