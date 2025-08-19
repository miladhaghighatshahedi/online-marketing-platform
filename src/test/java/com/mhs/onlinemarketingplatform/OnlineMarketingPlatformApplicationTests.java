package com.mhs.onlinemarketingplatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@SpringBootTest
class OnlineMarketingPlatformApplicationTests {

	ApplicationModules modules = ApplicationModules.of(OnlineMarketingPlatformApplication.class);

	@Test
	void contextLoads() {
	}

	@Test
	void verifyModule(){
		for (var m: modules){
			System.out.println("modules: " + m.getDisplayName() + ":" + m.getBasePackage());
		}
		modules.verify();
	}

	@Test
	void createDocumentation(){
		new Documenter(modules).writeDocumentation();
	}

}
