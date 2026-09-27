package com.flowtask.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlowtaskApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
