package org.aklakan.devblog.rdflist.domain;

import org.aksw.jena_sparql_api.mapper.annotation.IriNs;
import org.aksw.jena_sparql_api.mapper.annotation.ResourceView;
import org.apache.jena.rdf.model.Resource;

@ResourceView
public interface Person
    extends Resource
{
    @IriNs("http://xmlns.com/foaf/0.1/")
    String getName();
}
