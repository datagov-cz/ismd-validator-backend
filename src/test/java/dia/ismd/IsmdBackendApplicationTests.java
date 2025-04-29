package dia.ismd;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.test.ApplicationModuleTest;

@SpringBootTest
@ApplicationModuleTest
class IsmdBackendApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void modulesTest(){
        ApplicationModules.of(IsmdBackendApplication.class).verify();
    }

}
