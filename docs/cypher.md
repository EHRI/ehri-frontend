Example Cypher usage
====================

Occasionally it's useful to use [Cypher](http://neo4j.com/docs/1.9.9/cypher-query-lang.html) to munge the graph.
Here is an example of that. The situation was as follows:

 - a group was added for annotation/link moderation (with id "moderators")
 - this group would be given access to private notes if their creator marked them *promotable*
 - however, there were existing promotable annotations already created that did not grant access to the moderators group
   (rather, they weren't accessible to anyone except admins)

So what we needed to do was:

 - find the moderators group
 - find all the promotable notes (with attribute `isPromotable`)
 - check there's not already an access relation from annotation to the moderators group (with the
   label `access` from item to user/group)

Then finally:

 - create the access relationship on these annotations

Here's some Cypher to test the

```
START   n = node:entities("__ISA__:annotation"),
        mods = node:entities("__ID__:moderators")
MATCH   n-[r?:access]->mods
WHERE   HAS(n.isPromotable)
    AND n.isPromotable = true
    AND r is null
RETURN  n, n.body
```

Once happy that we're getting all the annotations that need fixing we can modify our query
to create the relationship:

```
START   n = node:entities("__ISA__:annotation"),
        mods = node:entities("__ID__:moderators")
MATCH   n-[r?:access]->mods
WHERE   HAS(n.isPromotable)
    AND n.isPromotable = true
    AND r is null
CREATE  n-[:access]->mods
RETURN  n, n.body
```


