package com.abco.taxassessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("com.abco.taxassessment.config.properties")
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class TaxAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxAssessmentApplication.class, args);
    }
}
