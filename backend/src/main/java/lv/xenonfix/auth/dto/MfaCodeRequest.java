package lv.xenonfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param code the 6 digits from the authenticator app, or a backup code where one is accepted
 */
public record MfaCodeRequest(@NotBlank @Size(max = 20) String code) {
}
