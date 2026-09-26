package com.example.kanshiwarehousemanagementsystem.database;

import java.util.List;

/**
 * Generic Data Access Object (DAO) interface.
 * Demonstrates advanced Object-Oriented Programming (Abstraction & Generics).
 *
 * @param <T> The domain entity type managed by this DAO.
 */
public interface CrudDao<T> {

    /**
     * Retrieves all entities from the underlying database table.
     *
     * @return List of all entities.
     */
    List<T> getAll();

    /**
     * Retrieves a single entity by its primary key ID.
     *
     * @param id The primary key ID.
     * @return The entity if found, or null otherwise.
     */
    T getById(int id);

    /**
     * Inserts a new entity into the database.
     *
     * @param entity The entity to add.
     * @return True if insertion succeeded, false otherwise.
     */
    boolean add(T entity);

    /**
     * Updates an existing entity in the database.
     *
     * @param entity The entity containing updated values.
     * @return True if at least one row was updated, false otherwise.
     */
    boolean update(T entity);

    /**
     * Deletes an entity by its primary key ID.
     *
     * @param id The primary key ID to delete.
     * @return True if deletion succeeded, false otherwise.
     */
    boolean delete(int id);
}
