package org.aklakan.devblog.entityquery.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.aklakan.devblog.entityquery.domain.Person;
import org.aklakan.devblog.entityquery.domain.Publication;
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils;
import org.aksw.jena_sparql_api.rx.PartitionedQueryImpl;
import org.aksw.jena_sparql_api.rx.PartitionedQueryRx;
import org.aksw.jena_sparql_api.stmt.SparqlStmtMgr;
import org.aksw.jena_sparql_api.utils.Vars;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.SortCondition;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.aggregate.AggMax;
import org.apache.jena.sparql.lang.arq.ParseException;
import org.apache.jena.vocabulary.RDFS;

/**
 * This example demonstrates loading a SPARQL query from file
 * and accessing its result using the domain view (schema) defined in {@link Publication}.
 *
 * Unfortunately the SPARQL query model does not allow specifying
 * partitioning information and the <b>set of resources that act as starting points</b>
 * for traversal.
 * Hence, this information has to be added externally (programatically in this example).
 * However, we argue that this information is so essential
 * that it should become part to SPARQL query model.
 *
 * Under a different view, this proposed SPARQL extension allows to
 * designate a blank node or variable of the construct template or where pattern
 * as a starting resource. Hence, the query intensionally describes a set of resources
 * together with corresponding graph fragments.
 *
 *
 * Feature:
 *                                                    OWL | SPARQL
 * Specify a set of resources                         yes | no
 * Specify information to retrieve for the resources   no | yes
 * After this proposal                                yes | yes
 *
 *
 * @author Claus Stadler
 *
 */
public class MainEntityQuery {
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        // Scan the package of Publication.class and register all classes annotated with @ResourceView
        // to jena's polymorphism system
        JenaPluginUtils.scan(Publication.class);

        Dataset ds = RDFDataMgr.loadDataset("publications.ttl");
        // Load a query from file
        Query standardQuery = SparqlStmtMgr.loadQuery("publications.sparql");


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
        Expr sortExpr = new ExprAggregator(Var.alloc("dummy"), new AggMax(new ExprVar(Var.alloc("date"))));
        rootedQuery.getPartitionOrderBy().add(new SortCondition(sortExpr, Query.ORDER_ASCENDING));

        // If partitions is used, then limit/offset refers to partitions
        standardQuery.setOffset(0);
        standardQuery.setLimit(1);

        /* The code above corresponds to

            CONSTRUCT { ?pub a dct:BibliographicResource . ?pub ... }
            ENTITY ?pub
            WHERE {
              ?pub a dct:dct:BibliographicResource ;
                   dct:created ?date;
                   ...
            }
            PARTITION BY ?pub
            ORDER PARTITIONS BY DESC(MAX(?date))
       */


        // Pretend we are connected to a remote SPARQL endpoint
        try (RDFConnection conn = RDFConnectionFactory.connect(ds)) {
            List<Publication> publications = PartitionedQueryRx.execConstructRooted(conn, rootedQuery)
                    .map(r -> r.as(Publication.class))
                    .toList().blockingGet();

            // Publication instances an RDF Resources backed by the graph fragment as specified in the SPARQL query.
            // Because of the designated root node we can immediately start accessing the attributes of
            // the publications:
            for (Publication publication : publications) {
                System.out.println("Showing publication: " + publication.getURI());

                String authorListStr = publication.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
                publication.addProperty(RDFS.comment, "Written by " + authorListStr);

                RDFDataMgr.write(System.out, publication.getModel(), RDFFormat.TURTLE_PRETTY);
            }
        }

/* Output:

Showing publication: http://www.example.org/pub1
<http://www.example.org/Anna>
        a       <http://xmlns.com/foaf/0.1/Person> ;
        <http://xmlns.com/foaf/0.1/name>
                "Anna" .

<http://www.example.org/pub1>
        a       <http://purl.org/dc/terms/BibliographicResource> ;
        <http://www.w3.org/2000/01/rdf-schema#comment>
                "Written by Anna, Bob" ;
        <http://purl.org/dc/terms/creator>
                ( <http://www.example.org/Anna> <http://www.example.org/Bob> ) ;
        <http://purl.org/dc/terms/title>
                "Awesome research" .

<http://www.example.org/Bob>
        a       <http://xmlns.com/foaf/0.1/Person> ;
        <http://xmlns.com/foaf/0.1/name>
                "Bob" .


Showing publication: http://www.example.org/pub2
<http://www.example.org/pub2>
        a       <http://purl.org/dc/terms/BibliographicResource> ;
        <http://www.w3.org/2000/01/rdf-schema#comment>
                "Written by Bob, Charlie" ;
        <http://purl.org/dc/terms/creator>
                ( <http://www.example.org/Bob> <http://www.example.org/Charlie> ) ;
        <http://purl.org/dc/terms/title>
                "Awesome engineering" .

<http://www.example.org/Charlie>
        a       <http://xmlns.com/foaf/0.1/Person> ;
        <http://xmlns.com/foaf/0.1/name>
                "Charlie" .

<http://www.example.org/Bob>
        a       <http://xmlns.com/foaf/0.1/Person> ;
        <http://xmlns.com/foaf/0.1/name>
                "Bob" .

*/
    }
}
