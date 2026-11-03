package lv.xenonfix.auth.dto;

public record MeResponse(long id, String email, String displayName, boolean passwordChangeRequired) {
}
