package  com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;

/**
 * In-memory implementation of IEventRepository with CAS-style optimistic locking.
 * Uses the aggregate version counter to detect concurrent modifications.
 * On save, if the entity's version does not match the stored version, an
 * OptimisticLockException is thrown, signaling the caller to retry.
 */
@org.springframework.stereotype.Component
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryEventRepository implements IEventRepository {

    private final ConcurrentHashMap<UUID, Event> store = new ConcurrentHashMap<>();

    @Override
    public void save(Event event) {
        store.compute(event.getId(), (id, existing) -> {
            if (existing == null) {
                // New entity - first save
                event.incrementVersion();
                return event;
            }
            // Existing entity - check version for CAS
            if (event.getVersion() != existing.getVersion()) {
                throw new OptimisticLockException("Event", id);
            }
            event.incrementVersion();
            return event;
        });
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Event> findByCompanyName(String companyName) {
        if (companyName == null) return List.of();
        List<Event> hits = new ArrayList<>();
        for (Event event : store.values()) {
            if (companyName.equals(event.getCompanyName())) {
                hits.add(event);
            }
        }
        return hits;
    }

    @Override
    public List<Event> findAll() {
        return new ArrayList<>(store.values());
    }
}



