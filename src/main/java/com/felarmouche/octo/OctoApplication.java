package com.felarmouche.octo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OctoApplication {

	public static void main(String[] args) {
		SpringApplication.run(OctoApplication.class, args);
	}

}
