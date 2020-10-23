package org.aklakan.devblog.entityquery.domain;

import java.util.Collection;

import org.aksw.jena_sparql_api.mapper.annotation.ResourceView;
import org.apache.jena.rdf.model.Resource;

@ResourceView
public interface Publication
    extends Resource
{
    String getTitle();
    Publication setTitle(String title);
    Collection<Person> getAuthors();
}
