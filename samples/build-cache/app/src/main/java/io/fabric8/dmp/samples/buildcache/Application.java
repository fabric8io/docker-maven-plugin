package io.fabric8.dmp.samples.buildcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @RestController
    public static class HelloController {

        @RequestMapping("/")
        public String greeting() {
            return "Hello World!";
        }
    }
}
