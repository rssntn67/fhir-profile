package it.arsinfo.fhir.init;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("dev")
public class FhirDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FhirDataSeeder.class);

    private final IFhirResourceDao<Patient>     patientDao;
    private final IFhirResourceDao<Observation> observationDao;

    public FhirDataSeeder(IFhirResourceDao<Patient> patientDao,
                          IFhirResourceDao<Observation> observationDao) {
        this.patientDao     = patientDao;
        this.observationDao = observationDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        // ddl-auto=create-drop guarantees empty tables on every dev startup — no idempotency guard needed
        IdType janeDoeId = seedPatient("Jane", "Doe", Enumerations.AdministrativeGender.FEMALE);
        IdType robertSmithId = seedPatient("Robert", "Smith", Enumerations.AdministrativeGender.MALE);

        seedObservation(janeDoeId,    "8867-4", "Heart rate",        72, "beats/min");
        seedObservation(janeDoeId,    "8310-5", "Body temperature",  37, "Cel");
        seedObservation(robertSmithId,"8867-4", "Heart rate",        80, "beats/min");

        log.info("Seeded 2 dev Patients and 3 dev Observations");
    }

    private IdType seedPatient(String given, String family,
                                Enumerations.AdministrativeGender gender) {
        Patient p = new Patient();
        p.addName().setFamily(family).addGiven(given);
        p.setGender(gender);
        return (IdType) patientDao.create(p).getId();
    }

    private void seedObservation(IdType patientId, String loincCode,
                                  String display, int value, String unit) {
        Observation o = new Observation();
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.setSubject(new Reference("Patient/" + patientId.getIdPart()));
        o.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode(loincCode)
                .setDisplay(display);
        Quantity q = new Quantity();
        q.setValue(new BigDecimal(value)).setUnit(unit);
        o.setValue(q);
        observationDao.create(o);
    }
}
