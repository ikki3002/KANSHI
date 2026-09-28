package com.example.warehousescadasystem.database;

import java.util.List;


public interface CrudDao<T> {


    List<T> getAll();

    T getById(int id);


    boolean add(T entity);


    boolean update(T entity);


    boolean delete(int id);
}
