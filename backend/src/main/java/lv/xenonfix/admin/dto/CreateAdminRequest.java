package lv.xenonfix.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lv.xenonfix.common.PasswordRules;

public record CreateAdminRequest(@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(max = 100) String displayName,
		@NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH) String password) {

	@Override
	public String toString() {
		return "CreateAdminRequest[email=" + this.email + "]";
	}

}
