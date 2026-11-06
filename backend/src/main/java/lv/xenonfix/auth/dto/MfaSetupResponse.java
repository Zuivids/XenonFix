package lv.xenonfix.auth.dto;

/**
 * @param qrCode a PNG data address to show as an image
 * @param secret the same key as text, in groups of four, for typing into the app
 */
public record MfaSetupResponse(String qrCode, String secret) {
}
