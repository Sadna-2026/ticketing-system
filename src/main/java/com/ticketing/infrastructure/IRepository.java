package com.ticketing.infrastructure;
import java.util.List;

import java.util.UUID;

public interface IRepository<T> {
    T findById(UUID id);
    List<T> getAll();
    void delete();
    void save(T entity);
}