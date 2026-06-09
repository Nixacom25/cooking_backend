package com.cooked.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.test.mock.mockito.MockBean;
import io.github.bucket4j.distributed.proxy.ProxyManager;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @MockBean
    private ProxyManager<byte[]> proxyManager;

	@Test
	void contextLoads() {
	}

}
