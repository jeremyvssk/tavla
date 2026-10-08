// Turns on @Scheduled methods: the guest cart cleanup and the release of unpaid orders.
package com.iloveshopping.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
