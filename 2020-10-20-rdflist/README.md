## Querying list with SPARQL

created: 20-10-2020
by: Claus Stadler (http://aksw.org/ClausStadler)

### Introduction
In this note I describe how it is possible to work effectively with rdf:Lists:

This note demonstrates how


### SPARQL 1.1 List Retrieval
Let's assume you have an RDF dataset that contains information about scientific publications.
The author list of these publications is (also) modeled as an rdf:List. This is important because
when displaying this information in a user interface the authors need to appear in correct order
for their due credits.


The first problem is: How to retrieve an rdf:List from a remote SPARQL endpoint?

While SPARQL 1.1 does not provide features to turn an rdf:List into an indexed sequence of items
this is not really an issue.
Is it sufficient to retrieve the whole rdf:List RDF graph fragment because then a client can create
a list or array in its host language anyway. Heck, if there was a way to preserve blank nodes we could
even modify the list on the client, track changes, and update the remote dataset. But first things first.


The solution to retrieve the whole fragment is fairly staight forward but still worth writing down:


```
INSERT DATA {
  eg:pub1
    eg:authors (eg:aut1 eg:aut2) .

  eg:pub2
    eg:authors (eg:aut2 eg:aut3)
}

CONSTRUCT {
  ?pub
    eg:authors ?auts .

  # ln = list name, f = first, r = rest
  ?ln
    rdf:first ?f ;
    rdf:rest ?r .
} {
  ?pub eg:authors ?auts .

  ?auts rdf:rest* ?ln .

  ?ln
    rdf:first ?f ;
    rdf:rest ?r .
} ORDER BY ?pub ?f
```

You can run these queries using your favourite SPARQL 1.1 tool. A simple way to run it
is with the  [https://github.com/SmartDataAnalytics/RdfProcessingToolkit](sparql-integrate command) command:
```bash
sparql-integrate list-example.sparql
```


The output is exactly what we want:

```ttl
eg:pub1  eg:authors  _:b0 .

_:b0    rdf:first  eg:aut1 ;
        rdf:rest   _:b1 .

_:b1    rdf:first  eg:aut2 ;
        rdf:rest   rdf:nil .

eg:pub2  eg:authors  _:b2 .

_:b2    rdf:first  eg:aut2 ;
        rdf:rest   _:b3 .

_:b3    rdf:first  eg:aut3 ;
        rdf:rest   rdf:nil .
```


### Accessing the RDF Fragment

Let's assume we have a local copy of resulting fragment such as in [publications.ttl](src/main/resources/publications.ttl)
With the [jena-sparql-api mapper-proxy](https://github.com/SmartDataAnalytics/jena-sparql-api/tree/master/jena-sparql-api-mapper-proxy) module display of this fragment is straight forward:

* Step 1: Create annotated domain interfaces and mapper proxy will create the implementaions for you.
  * [Author.java](src/main/java/org/aklakan/devblog/rdflist/domain/Author.java)
  * [Person.java](src/main/java/org/aklakan/devblog/rdflist/domain/Person.java)
* Step 2: Load the RDF graph, select the appropriate subset of resources and apply the views to them. The following snippet is from [MainSparqlList.java](src/main/java/org/aklakan/devblog/rdflist/domain/MainRdfList.java):
```java

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
}

```

Without further ado this yields:
```
Awesome research
Anna, Bob
Awesome engineering
Bob, Charlie
```




