package dia.ismd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootApplication
public class IsmdBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IsmdBackendApplication.class, args);
        var modules = ApplicationModules.of(IsmdBackendApplication.class);
        modules.forEach(System.out::println);
    }

}
