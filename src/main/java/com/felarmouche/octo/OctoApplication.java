package com.felarmouche.octo;

import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.GeminiFallbackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GeminiFallbackProperties.class)
public class OctoApplication {

	public static void main(String[] args) {
		SpringApplication.run(OctoApplication.class, args);
	}

}
