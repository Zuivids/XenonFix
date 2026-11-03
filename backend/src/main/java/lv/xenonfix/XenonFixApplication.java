package lv.xenonfix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XenonFixApplication {

	public static void main(String[] args) {
		SpringApplication.run(XenonFixApplication.class, args);
	}

}
