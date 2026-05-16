package it.arsinfo.fhir.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryPatientProvider implements IResourceProvider {

    private final Map<String, Patient> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public InMemoryPatientProvider() {
        seed("test-patient-001", "Doe", "Jane", Enumerations.AdministrativeGender.FEMALE);
        seed("test-patient-002", "Smith", "Robert", Enumerations.AdministrativeGender.MALE);
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType id) {
        Patient p = store.get(id.getIdPart());
        if (p == null) throw new ResourceNotFoundException(id);
        return p;
    }

    @Search
    public List<Patient> search(@OptionalParam(name = Patient.SP_FAMILY) StringParam family) {
        if (family == null) return new ArrayList<>(store.values());
        String q = family.getValue().toLowerCase();
        return store.values().stream()
                .filter(p -> p.getName().stream()
                        .anyMatch(n -> n.getFamily() != null && n.getFamily().toLowerCase().contains(q)))
                .toList();
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient patient) {
        String id = String.valueOf(idSeq.getAndIncrement());
        patient.setId(id);
        store.put(id, patient);
        return new MethodOutcome(new IdType("Patient", id)).setCreated(true);
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
        patient.setId(id.getIdPart());
        store.put(id.getIdPart(), patient);
        return new MethodOutcome(id);
    }

    @Delete
    public void delete(@IdParam IdType id) {
        if (!store.containsKey(id.getIdPart())) throw new ResourceNotFoundException(id);
        store.remove(id.getIdPart());
    }

    private void seed(String id, String family, String given, Enumerations.AdministrativeGender gender) {
        Patient p = new Patient();
        p.setId(id);
        p.addName().setFamily(family).addGiven(given);
        p.setGender(gender);
        store.put(id, p);
    }
}
