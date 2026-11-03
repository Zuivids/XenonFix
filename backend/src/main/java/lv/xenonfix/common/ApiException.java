package lv.xenonfix.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An expected error for the client. The response is a problem detail with a {@code code}
 * property, which the frontend turns into a message in its own language.
 */
public class ApiException extends ErrorResponseException {

	public ApiException(HttpStatus status, String code) {
		super(status, problem(status, code), null);
	}

	private static ProblemDetail problem(HttpStatus status, String code) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setProperty("code", code);
		return problem;
	}

}
