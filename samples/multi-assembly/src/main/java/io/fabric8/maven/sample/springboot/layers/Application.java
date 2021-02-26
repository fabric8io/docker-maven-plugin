package io.fabric8.maven.sample.springboot.layers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext applicationContext) {
        return new CommandLineRunner() {
            @Override
            public void run(String... args) {
                LOGGER.info("Hello from Spring Boot!");
                SpringApplication.exit(applicationContext);
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }
}
