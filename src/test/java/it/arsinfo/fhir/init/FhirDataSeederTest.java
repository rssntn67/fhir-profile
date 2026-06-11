package it.arsinfo.fhir.init;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that FhirDataSeeder (dev profile) populates exactly 2 patients and 3 observations
 * at startup via the JPA DAO layer (no HTTP involved).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
class FhirDataSeederTest {

    @Autowired IFhirResourceDao<Patient>     patientDao;
    @Autowired IFhirResourceDao<Observation> observationDao;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void seeder_creates_two_patients() {
        IBundleProvider results = patientDao.search(new SearchParameterMap(), null);
        assertThat(results.size()).isEqualTo(2);
    }

    @Test
    void seeder_creates_three_observations() {
        IBundleProvider results = observationDao.search(new SearchParameterMap(), null);
        assertThat(results.size()).isEqualTo(3);
    }
}
