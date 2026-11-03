package lv.xenonfix.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Path uploadsPath) {
}
