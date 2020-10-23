package org.aklakan.devblog.entityquery.domain;

import java.util.Set;

import org.aksw.jena_sparql_api.mapper.annotation.Iri;
import org.aksw.jena_sparql_api.mapper.annotation.IriNs;

public interface PublicationWithSetOfAuthors
    extends Publication
{
    @IriNs("http://purl.org/dc/terms/")
    String getTitle();
    Publication setTitle(String title);

    @Iri("http://purl.org/dc/terms/creator")
    Set<Person> getAuthors();

}
