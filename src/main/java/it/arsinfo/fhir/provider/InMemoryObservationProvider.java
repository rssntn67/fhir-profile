package it.arsinfo.fhir.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryObservationProvider implements IResourceProvider {

    private final Map<String, Observation> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public InMemoryObservationProvider() {
        seed("obs-001", "test-patient-001", "8867-4", "Heart rate", 72, "beats/min");
        seed("obs-002", "test-patient-001", "8310-5", "Body temperature", 37, "Cel");
        seed("obs-003", "test-patient-002", "8867-4", "Heart rate", 80, "beats/min");
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Observation.class;
    }

    @Read
    public Observation read(@IdParam IdType id) {
        Observation o = store.get(id.getIdPart());
        if (o == null) throw new ResourceNotFoundException(id);
        return o;
    }

    @Search
    public List<Observation> search(@OptionalParam(name = Observation.SP_SUBJECT) ReferenceParam subject) {
        if (subject == null) return new ArrayList<>(store.values());
        String ref = subject.getValue();
        return store.values().stream()
                .filter(o -> o.getSubject().getReference().contains(ref))
                .toList();
    }

    @Create
    public MethodOutcome create(@ResourceParam Observation observation) {
        String id = String.valueOf(idSeq.getAndIncrement());
        observation.setId(id);
        store.put(id, observation);
        return new MethodOutcome(new IdType("Observation", id)).setCreated(true);
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Observation observation) {
        observation.setId(id.getIdPart());
        store.put(id.getIdPart(), observation);
        return new MethodOutcome(id);
    }

    @Delete
    public void delete(@IdParam IdType id) {
        if (!store.containsKey(id.getIdPart())) throw new ResourceNotFoundException(id);
        store.remove(id.getIdPart());
    }

    private void seed(String id, String patientId, String loincCode, String display, int value, String unit) {
        Observation o = new Observation();
        o.setId(id);
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.setSubject(new Reference("Patient/" + patientId));
        o.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode(loincCode)
                .setDisplay(display);
        Quantity q = new Quantity();
        q.setValue(new BigDecimal(value)).setUnit(unit);
        o.setValue(q);
        store.put(id, o);
    }
}
