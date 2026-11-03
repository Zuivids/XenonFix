package lv.xenonfix.health.dto;

public record HealthResponse(Status status, Status database) {

	public enum Status {

		UP, DOWN

	}

}
