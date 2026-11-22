package com.felarmouche.octo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"spring.security.user.name=octo", "spring.security.user.password=secret"})
@Import(TestcontainersConfiguration.class)
class OctoApplicationTests {

	@Test
	void contextLoads() {
	}

}
