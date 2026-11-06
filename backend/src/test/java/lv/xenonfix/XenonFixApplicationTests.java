package lv.xenonfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import lv.xenonfix.config.AppProperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
class XenonFixApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private AppProperties properties;

	@Test
	void migrationHasRun() {
		String version = this.jdbcClient
			.sql("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1")
			.query(String.class)
			.single();
		String legalName = this.jdbcClient.sql("SELECT setting_value FROM app_setting WHERE setting_key = 'shop_legal_name'")
			.query(String.class)
			.single();

		assertThat(version).isEqualTo("8");
		assertThat(legalName).isEqualTo("SIA Elekanga");
	}

	@Test
	@Transactional
	void databaseKeepsLatvianAndRussianText() {
		String text = "Priekšējais lukturis — Передняя фара";
		this.jdbcClient.sql("INSERT INTO app_setting (setting_key, setting_value) VALUES ('test', ?)")
			.param(text)
			.update();

		String stored = this.jdbcClient.sql("SELECT setting_value FROM app_setting WHERE setting_key = 'test'")
			.query(String.class)
			.single();

		assertThat(stored).isEqualTo(text);
	}

	@Test
	void healthIsUp() throws Exception {
		this.mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void uploadsAreServedFromDisk() throws Exception {
		Path file = this.properties.uploadsPath().resolve("smoke-test.txt");
		Files.writeString(file, "from disk");
		try {
			this.mockMvc.perform(get("/uploads/smoke-test.txt"))
				.andExpect(status().isOk())
				.andExpect(content().string("from disk"));
		}
		finally {
			Files.delete(file);
		}
		this.mockMvc.perform(get("/uploads/smoke-test.txt")).andExpect(status().isNotFound());
	}

}
