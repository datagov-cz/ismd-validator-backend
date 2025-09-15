package com.dia.reader;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.ssp.SSPReader;
import com.dia.conversion.reader.ssp.config.SPARQLConfiguration;
import com.dia.conversion.reader.ssp.data.ConceptData;
import com.dia.conversion.reader.ssp.data.DomainRangeInfo;
import com.dia.exceptions.ConversionException;
import com.dia.utility.UtilityMethods;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SSPReaderUnitTest {

    @Mock
    private SPARQLConfiguration config;

    @Mock
    private QueryExecutionHTTPBuilder builder;

    @Mock
    private QueryExecutionHTTP queryExecution;

    @Mock
    private ResultSet resultSet;

    @Mock
    private QuerySolution querySolution;

    @Mock
    private Resource resource;

    @InjectMocks
    private SSPReader sspReader;
}