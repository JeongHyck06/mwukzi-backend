package jack.mwukzibackened;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "jack.mwukzibackened")
public class MwukziBackenedApplication {

    public static void main(String[] args) {
        SpringApplication.run(MwukziBackenedApplication.class, args);
    }

}
