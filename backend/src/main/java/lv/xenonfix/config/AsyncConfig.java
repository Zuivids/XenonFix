package lv.xenonfix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** For work the visitor should not wait for, such as sending email. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {

}
