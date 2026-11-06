package lv.xenonfix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** For nightly work, such as erasing old customer details. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {

}
