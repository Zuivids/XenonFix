package lv.xenonfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lv.xenonfix.common.PasswordRules;

public record LoginRequest(@NotBlank @Size(max = 320) String email,
		@NotBlank @Size(max = PasswordRules.MAX_LENGTH) String password) {

	@Override
	public String toString() {
		return "LoginRequest[]";
	}

}
