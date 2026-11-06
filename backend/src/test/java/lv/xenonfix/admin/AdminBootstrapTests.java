package lv.xenonfix.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import lv.xenonfix.audit.AuditService;
import lv.xenonfix.config.AppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

class AdminBootstrapTests {

	private static final String PASSWORD = "a long enough password";

	private final AdminUserRepository admins = mock();

	@BeforeEach
	void setUp() {
		when(this.admins.save(any())).thenAnswer((invocation) -> {
			AdminUser admin = mock();
			when(admin.getId()).thenReturn(1L);
			return admin;
		});
	}

	@Test
	void createsTheFirstAdminWhenThereAreNone() {
		when(this.admins.count()).thenReturn(0L);

		bootstrap(" First@Example.com ", PASSWORD).run(null);

		var saved = ArgumentCaptor.forClass(AdminUser.class);
		verify(this.admins).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("first@example.com");
		assertThat(saved.getValue().isPasswordChangeRequired()).isTrue();
	}

	@Test
	void doesNothingOnceThereAreAdmins() {
		when(this.admins.count()).thenReturn(1L);

		bootstrap("first@example.com", PASSWORD).run(null);

		verify(this.admins, never()).save(any());
	}

	@Test
	void doesNothingWithoutSettings() {
		when(this.admins.count()).thenReturn(0L);

		bootstrap("", "").run(null);

		verify(this.admins, never()).save(any());
	}

	@Test
	void refusesAShortPassword() {
		when(this.admins.count()).thenReturn(0L);

		assertThatIllegalStateException().isThrownBy(() -> bootstrap("first@example.com", "short").run(null));
	}

	@SuppressWarnings("deprecation")
	private AdminBootstrap bootstrap(String email, String password) {
		var bootstrapAdmin = new AppProperties.BootstrapAdmin(email, password, true);
		var properties = new AppProperties(null, bootstrapAdmin, null, null, null, null, null, null);
		return new AdminBootstrap(properties, this.admins, NoOpPasswordEncoder.getInstance(), mock(AuditService.class),
				Clock.systemUTC());
	}

}
