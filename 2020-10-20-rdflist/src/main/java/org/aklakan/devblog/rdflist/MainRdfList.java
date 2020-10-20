package org.aklakan.devblog.rdflist;

import java.util.List;
import java.util.stream.Collectors;

import org.aklakan.devblog.rdflist.domain.Author;
import org.aklakan.devblog.rdflist.domain.Publication;
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;

public class MainRdfList {
    public static void main(String[] args) {
        // Scan the package of Publication and register all classes annotated with @ResourceView
        JenaPluginUtils.scan(Publication.class);

        Model model = RDFDataMgr.loadModel("publications.ttl");

        List<Publication> publications = model.listResourcesWithProperty(RDF.type, DCTerms.BibliographicResource)
            .mapWith(r -> r.as(Publication.class)).toList();

        for (Publication item : publications) {
            System.out.println(item.getTitle());
            System.out.println(item.getAuthors().stream().map(Author::getName).collect(Collectors.joining(", ")));
        }
        /* Output:
            Awesome research
            Anna, Bob
            Awesome engineering
            Bob, Charlie
         */
    }
}
