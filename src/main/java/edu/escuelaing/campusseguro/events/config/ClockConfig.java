package edu.escuelaing.campusseguro.events.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj único de la aplicación. Todo el código que necesite la hora actual
 * debe recibir este Clock en lugar de llamar a Instant.now(), para poder
 * probar fechas (inicio de eventos, validaciones) con un reloj fijo.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${app.timezone:America/Bogota}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
