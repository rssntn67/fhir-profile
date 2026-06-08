package it.arsinfo.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan({
    "ca.uhn.fhir.jpa.model.entity",  // HAPI JPA resource tables
    "ca.uhn.fhir.jpa.entity",         // HAPI JPA term/subscription entities
    "it.arsinfo.fhir.domain.entity"   // RBAC entities
})
public class FhirServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhirServerApplication.class, args);
    }
}
