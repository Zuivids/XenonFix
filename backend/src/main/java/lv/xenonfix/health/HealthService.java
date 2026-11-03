package lv.xenonfix.health;

import lv.xenonfix.health.dto.HealthResponse;
import lv.xenonfix.health.dto.HealthResponse.Status;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
class HealthService {

	private final JdbcClient jdbcClient;

	HealthService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	HealthResponse check() {
		try {
			this.jdbcClient.sql("SELECT 1").query(Integer.class).single();
			return new HealthResponse(Status.UP, Status.UP);
		}
		catch (DataAccessException ex) {
			return new HealthResponse(Status.DOWN, Status.DOWN);
		}
	}

}
