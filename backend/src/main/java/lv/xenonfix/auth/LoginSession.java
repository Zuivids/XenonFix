package lv.xenonfix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lv.xenonfix.config.AppProperties;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Stores the login in the HTTP session. The session id changes at login, to prevent session
 * fixation. The idle timeout is enforced by the servlet container.
 */
@Component
class LoginSession {

	private final SecurityContextRepository securityContextRepository;

	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	private final AppProperties.Session properties;

	LoginSession(SecurityContextRepository securityContextRepository, AppProperties properties) {
		this.securityContextRepository = securityContextRepository;
		this.properties = properties.session();
	}

	void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = rotateSession(request);
		session.setMaxInactiveInterval(Math.toIntExact(this.properties.idle().toSeconds()));
		SecurityContext context = this.holderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		this.holderStrategy.setContext(context);
		this.securityContextRepository.saveContext(context, request, response);
	}

	private static HttpSession rotateSession(HttpServletRequest request) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
			return request.getSession();
		}
		return request.getSession(true);
	}

}
