package org.aklakan.devblog.rdflist.domain;

import java.util.List;

import org.aksw.jena_sparql_api.mapper.annotation.Iri;
import org.aksw.jena_sparql_api.mapper.annotation.IriNs;
import org.aksw.jena_sparql_api.mapper.annotation.ResourceView;
import org.apache.jena.rdf.model.Resource;

@ResourceView
public interface Publication
    extends Resource
{
    @IriNs("http://purl.org/dc/terms/")
    String getTitle();
    Publication setTitle(String title);

    @Iri("http://purl.org/dc/terms/creator")
    List<Person> getAuthors();
}
