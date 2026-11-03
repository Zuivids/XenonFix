package lv.xenonfix.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final Path uploadsPath;

	WebConfig(AppProperties properties) {
		this.uploadsPath = properties.uploadsPath().toAbsolutePath().normalize();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		try {
			Files.createDirectories(this.uploadsPath);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot create uploads directory " + this.uploadsPath, ex);
		}
		// Path.toUri() ends with "/" for an existing directory, as resource locations require
		registry.addResourceHandler("/uploads/**")
			.addResourceLocations(this.uploadsPath.toUri().toString())
			// Uploaded files get a new random name instead of changing, so they can be cached for long
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
	}

}
