package com.ticketing.infrastructure;

import java.util.List;
import java.util.Optional;

public interface IRepository<T, S> {
    Optional<T> findById(S id);
    List<T> getAll();
    void delete(S id);
    void save(T entity);
    void deleteAll();
}
