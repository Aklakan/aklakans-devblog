package org.aklakan.devblog.entityquery.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.aklakan.devblog.entityquery.domain.Person;
import org.aklakan.devblog.entityquery.domain.Publication;
import org.aklakan.devblog.entityquery.domain.PublicationWithSetOfAuthors;
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils;
import org.aksw.jena_sparql_api.rx.PartitionedQueryImpl;
import org.aksw.jena_sparql_api.rx.PartitionedQueryRx;
import org.aksw.jena_sparql_api.stmt.SparqlStmtMgr;
import org.apache.jena.query.Query;
import org.apache.jena.query.SortCondition;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.aggregate.AggMin;
import org.apache.jena.sparql.lang.arq.ParseException;
import org.apache.jena.vocabulary.RDFS;


public class MainEntityQueryScholarlyData {
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        // Scan the package of Publication.class and register all classes annotated with @ResourceView
        // to jena's polymorphism system
        JenaPluginUtils.scan(Publication.class);

        String sparqlEndpoint = "http://www.scholarlydata.org/sparql/";

        // Load a query from file
        Query standardQuery = SparqlStmtMgr.loadQuery("publications.scholarlydata.sparql");

        // This is not part of the SPARQL 1.1 query model - so we have
        // to programmatically interfere here!

        PartitionedQueryImpl rootedQuery = new PartitionedQueryImpl(standardQuery);
        Var rootNode = Var.alloc("pub");
        rootedQuery.setRootNode(rootNode);
        rootedQuery.getPartitionVars().add(rootNode);

        // Note: jena cannot parse agg expressions without a query because they need to be allocated there
        // ExprUtils.parse("MAX(?date)")

        // The aggregator of the following expression will be properly allocated on the query;
        // the dummy var will then be lost
        Expr sortExpr = new ExprAggregator(Var.alloc("dummy"), new AggMin(new ExprVar(Var.alloc("firstAuthorName"))));
        rootedQuery.getPartitionOrderBy().add(new SortCondition(sortExpr, Query.ORDER_ASCENDING));

        // If partitions is used, then limit/offset refers to partitions
        standardQuery.setOffset(0);
        standardQuery.setLimit(10);


        // Pretend we are connected to a remote SPARQL endpoint
        try (RDFConnection conn = RDFConnectionFactory.connect(sparqlEndpoint)) {
            List<? extends Publication> publications = PartitionedQueryRx.execConstructRooted(conn, rootedQuery)
                    .map(r -> r.as(PublicationWithSetOfAuthors.class))
                    .toList().blockingGet();

            // An ad-hoc property to access the sort key (which we did not expose using the Java domain view)
            Property sortKey = ResourceFactory.createProperty("http://www.example.org/sortKey");

            // Publication instances an RDF Resources backed by the graph fragment as specified in the SPARQL query.
            // Because of the designated root node we can immediately start accessing the attributes of
            // the publications:
            for (Publication publication : publications) {
                System.out.println(">>> Showing publication: " + publication.getURI());
                System.out.println("  Sort key: " + publication.getProperty(sortKey).getObject());

                String authorListStr = publication.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
                publication.addProperty(RDFS.comment, "Written by (in no particular order) " + authorListStr);

                // Write out the comment we just tadded
                System.out.println(publication.getProperty(RDFS.comment).getObject());

                System.out.println();
                System.out.println();
            }
        }
    }
}
