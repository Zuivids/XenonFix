package lv.xenonfix.health;

import static org.assertj.core.api.Assertions.assertThat;

import lv.xenonfix.health.dto.HealthResponse;
import lv.xenonfix.health.dto.HealthResponse.Status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class HealthServiceTests {

	@Test
	void reportsDownWhenTheDatabaseIsUnreachable() {
		var unreachable = new DriverManagerDataSource("jdbc:mariadb://localhost:1/none?connectTimeout=500");

		HealthResponse health = new HealthService(JdbcClient.create(unreachable)).check();

		assertThat(health).isEqualTo(new HealthResponse(Status.DOWN, Status.DOWN));
	}

}
