package com.feilonglab.springboot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.feilonglab.springboot.util.MessageUtils;

@SpringBootTest(properties = "app.locale=en")
class ZeroTraceSpringBootApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void testEnglishLocale() {
		String msg = MessageUtils.getMessage("mail.validate.to.empty");
		assertEquals("Recipient email address cannot be empty.", msg);
	}

}
