## Proposal for a Resource-Centric SPARQL Query Form

Maybe you never noticed, but SPARQL 1.1 does not directly allow retrieval of graph fragments rooted in an entity.
Sure, one can retrieve graph fragments using SPARQL construct queries, such as

```
CONSTRUCT WHERE { ?x a foaf:Person ; foaf:knows ?y . ?y a foaf:Person }
```

But this gives one just a bunch of triples. Standard SPARQL CONSTRUCT queries are triple-centric.
However, it is the resource-centric view that facilitates traversal of an RDF graph akin to traversal of attributes of an object graph.
With object graph I broadly mean any form of traversal from an entity via an attribute to another entity or value.
E.g. Moving from a json object to another one via one of its properties. Moving from an HTML element to its child or parent. Moving from a Java object via an attribute to a related one. Thing - attribute - thing. Its ubiquitous. It's the thing that is needed in applications.

Standard SPARQL neither offers mechanisms to partition the RDF graph to meaningful sub graphs (akin to a sub entity graph of a single entity) nor to
designate a starting point (the entity itself) for traversal of the data within a partition.


In other words there is no way to express that the response of a construct query should be based on partitioning the bindings of the WHERE clause and
for each value for a variable in that partition the construct template should serve as a starting node for travesal
```
CONSTRUCT WHERE { ?x a foaf:Person ; foaf:knows ?y . ?y a foaf:Person } PARTITION BY ?x ROOTED IN ?x
```


> :wrench: This description is maybe too Apache Jena specific but for those familiar with it may see the benefit more easily
The practical consequence would be a SPARQL query form for which a framework could yield to a `Stream<RDFNode>`, 
RDFNode is a pair (T, G) with T an RDF term and G an RDF graph - i.e. a graph fragment and a starting point.


Without PARTITION BY each value for ?x in `ROOTED IN ?x` would yield a new `RDFNode` within the whole RDF graph specified by the CONSTRUCT query.

To summarize the most important aspects
* PARTITION BY operates on the binding level - i.e. the query's graph pattern. Its arguments are a sequence of SPARQL variables.
* ROOTED IN only applies to CONSTRUCT queries. It takes as argument a single RDF term occurring in a template. Continue reading to see how it can interact with blank nodes and compound keys.



To illustrate the working let's use some data:
```
:Anna    a foaf:Person ; foaf:knows :Bob, :Charlie .
:Bob     a foaf:Person ; foaf:knows :Anna .
:Charlie a foaf:Person ; foaf:knows :Bob .

:Foo  a owl:Thing .

```

Now, a CONSTRUCT query is based on the bindings returned by the WHERE clause. So let's examine this first.


```
SELECT ?x ?y { ?x a foaf:Person ; foaf:knows ?y . ?y a foaf:Person } PARTITION BY ?x
```

What this should give us is a set of bindings partitioned by the given variables:
We can just reuse the concept of bindings for the keys:


| Key (?x)         | Bindings                                            |
|------------------|-----------------------------------------------------|
| (?x = :Anna)     | (?x = :Anna,    ?y = :Bob)                          |
|                  | (?x = :Anna,    ?y = :Charlie)                      |
| (?x = :Bob)      | (?x = :Bob,     ?y = :Anna)                         |
| (?x = :Charlie)  | (?x = :Charlie, ?y = :Bob)                          |



For construct queries, the template is instantiated for each partition:
```
CONSTRUCT WHERE { ?x a foaf:Person ; foaf:knows ?y . ?y a foaf:Person } PARTITION BY ?x ROOTED IN ?x
```


| Key (?x)         | Graphs (1 per partition)                             | Roots (sequence of RDF terms) |
|------------------|------------------------------------------------------|-------------------------------|
| (?x = :Anna)     | :Anna    a foaf:Person ; foaf:knows :Bob, :Charlie . | (:Anna)                       |
| (?x = :Bob)      | :Bob     a foaf:Person ; foaf:knows :Anna .          | (:Bob )                       |
| (?x = :Charlie)  | :Charlie a foaf:Person ; foaf:knows :Bob .           | (:Charlie)                    |


As only one RDF term of the template can be designated as a root there is no need to include the variable in the result.


If we combine this with an ORDER BY clause that matches the PARTITION BY clause then
we know that all bindings for a partition are consecutive and once the binding that acts as the primary key changes we know
we have seen the complete partition - this is a simple basis for enabling stream processing.

```sparql
SELECT ?x ?y { ?x a foaf:Person ; foaf:knows ?y . ?y a foaf:Person } PARTITION BY ?x ORDER BY ?x
```


## PARTITION BY and ROOTED IN with Compound Keys

### The Problem
One might be tempted to say something like "In the Semantic Web everthing should be identified by an IRI anyway so there is no need for compound keys".
But this is wrong. Compound keys occur right away when aggregation results should be mapped to RDF:

```
CONSTRUCT {
  X
    a sosa:Observation ;
    :city ?city ;
    :day ?day ;
    :avgTemp ?avgTemp
    .

  ?city rdfs:label ?cityLabel .
} WHERE {
  { SELECT ?city ?day (AVG(?temperature) AS ?avgTemp) { ... } }

  ?city rdfs:label ?cityLabel .
}
```

The problem is what to use in the place of X. Whatever it is, in this example it's identity should to be based on the compound key (?city, ?day).

With standard SPARQL 1.1 there aren't that many options:

* (1) We turn X into the variable ?X and add a BIND element to the graph pattern. Too bad that SPARQL does not have a tuple type so we cannot do
        BIND(tuple(?city, ?day) AS ?X). As a workaround we may resort to
        ```sparql
        BIND(BNODE(CONCAT(STR(?city), '|custom_separator|', STR(?day)) AS ?X))
        ```
        Ugly, but it works for allocating unique blank nodes. However, this way the information that X should be blank node based on a compound key is no longer explicit in the model. I will show in a moment why we this information is very useful.
* (2) We turn X into the blank node _:X. Then every single binding of the WHERE clause will create a new blank node. If the cardinality between ?city and ?cityLabel is not 1:1 we will end up with many blank nodes which have equivalent values for their city and day properties. Certainly something we don't want.


### Proposed Solution
The proposed solution is to extend CONSTRUCT queries with an explicit mapping of blank nodes to variables that act as the keys.
This way, a certain degree of control over the blank node allocation in CONSTRUCT template instantiation is gained.


```sparql
CONSTRUCT { _:X a sosa:Observation }
KEY _:X (?city ?day)
WHERE { { SELECT ?city ?day ... } }
```

This pattern can be repeated to assign any blank node mentioned in the CONSTRUCT template, e.g.
```sparql
CONSTRUCT { ... _:X ... _:Y  ... }
KEY _:X (?a ?b)
KEY _:Y (?b ?c)
WHERE { ... }
```

To this point we could achieve the same th BIND(BNODE(...)) approach.

The aspect that makes the difference is when combining it with partitioned resource centric retrieval:

```sparql
CONSTRUCT { _:X a sosa:Observation }
KEY :X (?city ?day)
WHERE { { SELECT ?city ?day ... } }
PARTITION BY (?city ?day) //
ROOTED IN _:X
```

This yields for each value of (?city ?day) an RDF graph with a starting node that is a blank node based on the values of (?city ?day) within that partition.


| Key (?city day)                 | Graphs (1 per partition)                         | Roots (sequence of RDF terms) |
|---------------------------------|---------------------------------------------------|-------------------------------|
| (?city = :Leipzig, ?day = 30)   | _:leipzig-30 :city :Leipzig; :day 30; :avgTemp 10 | (_:leipzig-30)                |


