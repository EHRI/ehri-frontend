# Miscellaneous docs / pre-emptive justification

## Search Dispatchers, Resolvers and Indexers

### Search Dispatchers

Search dispatchers abstract the interface of a particular search engine and provide an asynchronous interface to submit some
query and retrieve the results. At present the interface is unstable and highly subject to change.

#### Search Dispatcher Logging

It can be useful to see exactly what Solr search is being dispatched. This can be enabled by setting the
`eu.ehri.project.search.solr` logger to `DEBUG` by adding the following like to `conf/logger.xml`:

```xml
    <configuration>
        <!-- Lots of stuff... -->
        <logger name="eu.ehri.project.search.solr" level="DEBUG" />
        <!-- Lots more stuff... -->
    </configuration>
```

### Search Resolvers

When we get some search results from the dispatcher the first thing we often want to do is look them up in the database
for a fully-formed instance of the items returned. The `Resolver` type handles this task. It is a separate 'thing' for
several reasons:

- there are several ways to do bulk lookups depending on the characteristics of the backend
- we might want to use a different bulk lookup strategy in testing

The simplest way to look up a set of, say, 20 search results in the DB would be to iterate over them and issue a
separate call for each one depending on its type and id. This, however, would be incredibly slow. A better approach
would be to look them all up in one go in a manner analogous to an SQL `WHERE id IN ('foo', 'bar')` clause. The EHRI
REST backend provides a way to do this with both synthetic string identifiers (the ones EHRI derives ourselves) and the
internal (long) graph identifier.

Doing bulk lookups with the synthetic string identifiers is simple but has the disadvantage with our current REST
backend that because there is a single global index bulk lookups get slower as the graph is populated with more
material.

Bulk lookups using native graph identifiers are much faster since these - in Neo4j at least - are essentially pointers to
a position on disk. Bulk lookups therefore stay more or less 0(1) regardless of how much stuff the graph contains (assuming it can
all fit into memory.) However, this is an implementation detail, and, moreover, since native graph IDs are not stable, it cannot
reliably be used during testing.

At runtime, the application therefore uses a `Resolver` implementation that uses native graph IDs mapped from the `gid`
field from the search result. While testing we use an implementation that uses synthetic string IDs.

### Search Indexers

Search indexers provide an asynchronous interface to instruct the search engine that some data has changed in the database
and that it should re-index the relevant items.
