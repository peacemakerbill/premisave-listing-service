package com.premisave.listing;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

// UserDetailsServiceAutoConfiguration's import changed for Spring Boot 4:
// moved from org.springframework.boot.autoconfigure.security.servlet to
// org.springframework.boot.security.autoconfigure. Not otherwise changed
// in behavior — still excluded here to suppress the generated-password
// log line, since this app is JWT-authenticated, not username/password.
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class PremisaveListingServiceApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        // Optional. Check-in/check-out times, cron schedules and stored dates all use the JVM's zone,
        // so pin it here instead of relying on whatever the server is set to. Leave APP_TIME_ZONE unset
        // to keep the server's zone. Do not change it on a database that already holds bookings
        // without converting them: stored times are read back in the zone the app runs in.
        String zone = System.getProperty("APP_TIME_ZONE", System.getenv("APP_TIME_ZONE"));
        if (zone != null && !zone.isBlank()) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone.trim())));
        }

        SpringApplication.run(PremisaveListingServiceApplication.class, args);
    }
}