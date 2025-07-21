package com.dia.service;

import com.dia.exceptions.ValidationException;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

public interface SPARQLQueryService {

    boolean executeAsk(String sparqlQuery) throws ValidationException;

    ResultSet executeSelect(String sparqlQuery) throws ValidationException;

    Model executeConstruct(String sparqlQuery) throws ValidationException;

    boolean testConnectivity();

    String getEndpointUrl();
}
