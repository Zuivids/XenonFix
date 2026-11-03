package lv.xenonfix.common;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

	/** Two admins saved the same thing at the same moment; the later one must look again. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ErrorResponse changedByAnother(OptimisticLockingFailureException ex) {
		return new ApiException(HttpStatus.CONFLICT, "CHANGED_BY_ANOTHER");
	}

}
