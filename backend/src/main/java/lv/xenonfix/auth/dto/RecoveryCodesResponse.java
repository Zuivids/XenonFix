package lv.xenonfix.auth.dto;

import java.util.List;

/**
 * @param codes the new backup codes; shown this once, never stored as text
 * @param admin the admin, with the step their session is at now
 */
public record RecoveryCodesResponse(List<String> codes, MeResponse admin) {
}
