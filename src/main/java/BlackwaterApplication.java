import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "controller",
        "service"
})
public class BlackwaterApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                BlackwaterApplication.class,
                args
        );
    }
}
