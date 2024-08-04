package io.fabric8.maven.sample.springboot.jib;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class HelloController {

    @GetMapping("/")
    public String index() {
        return "<h1>Greetings from Spring Boot(Powered by JIB)!!</h1>";
    }

}
