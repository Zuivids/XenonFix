package lv.xenonfix.admin;

public record AdminSessionState(AdminStatus status, long securityVersion) {
}
