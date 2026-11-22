package com.felarmouche.octo;

import com.felarmouche.octo.infra.ai.GeminiFallbackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GeminiFallbackProperties.class)
public class OctoApplication {

	/** The clock carries the one global timezone, in which every Schedule is read. */
	@Bean
	Clock clock(@Value("${octo.timezone}") ZoneId timezone) {
		return Clock.system(timezone);
	}

	public static void main(String[] args) {
		SpringApplication.run(OctoApplication.class, args);
	}

}
