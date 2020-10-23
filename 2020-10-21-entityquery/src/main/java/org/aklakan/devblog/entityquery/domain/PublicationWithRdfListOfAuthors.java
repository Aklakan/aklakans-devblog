package org.aklakan.devblog.entityquery.domain;

import java.util.List;

import org.aksw.jena_sparql_api.mapper.annotation.Iri;
import org.aksw.jena_sparql_api.mapper.annotation.IriNs;
import org.aksw.jena_sparql_api.mapper.annotation.ResourceView;

@ResourceView
public interface PublicationWithRdfListOfAuthors
    extends Publication
{
    @IriNs("http://purl.org/dc/terms/")
    PublicationWithRdfListOfAuthors setTitle(String title);

    @Iri("http://purl.org/dc/terms/creator")
    List<Person> getAuthors();
}
