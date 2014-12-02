Anatomy of diagnosing a bug
===========================

Bug: one collection from DANS results in a Proxy Error 502 from Apache when viewed on the staging server. What do we know:

 - the URL of the collection that's failing is `http://portal.aehri.dans.knaw.nl/units/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f`
 - Apache is only a reverse proxy for the Play docview service running on port 9000
 - Proxy Error 502 is a generic error for when the service being proxied fails to generate a (timely) response
 - therefore the issue is most likely the Play service hanging for some reason

Why would it do this?

 - an infinite loop, non-terminating recursion, or other programming bug
 - it's waiting on the backend Neo4j service to generate a response (but waiting long enough for the Apache proxy to give up first)

Since the front-end Play service uses very little in the way of loops or explicit recursion, whereas the backend uses those techniques a lot, we should check out the backend first.  We can do this by cloning the DB locally and running on it the same web service requests called by the front-end in the course of the failing request.

To clone the Neo4j DB locally to a directory called "proxy_error_db" we use the Fabric script, ala:

	fab stage online_clone_db:proxy_error_db

Then stop our local Neo4j service and replace the "graph.db" folder of our local instance with this directory:

    ./neo4j-community-1.9.8/bin/neo4j stop
    mv neo4j-community-1.9.8/data/graph.db neo4j-community-1.9.8/data/graph.db.current
    mv proxy_error_db neo4j-community-1.9.8/data/graph.db
    ./neo4j-community-1.9.8/bin/neo4j start

With the troublesome database installed locally we can run on it the web service calls that might be hanging for some reason. Looking at the Play front-end log on the staging server we can see that for each call to that page on the front-end six web service requests are made to the backend running on `http://localhost:7474`:

 - Fetch permissions for user `mike` within the scope of the collection: `/units/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f`
 - Fetch item-level permissions for user `mike`: `/ehri/permission/mike/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=imageUrl`
 - Fetch the item data: `/ehri/documentaryUnit/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=urlPattern&_ip=otherFormsOfName&_ip=parallelFormsOfName&_ip=logoUrl&_ip=imageUrl`
 - Fetch the items user `mike` is watching: `ehri/userProfile/mike/watching?_ip=imageUrl&offset=0&limit=-1`
 - Fetch annotations for the item: `/ehri/annotation/for/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=imageUrl&offset=0&limit=-1`
 - Fetch links for the item: `/ehri/link/for/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=imageUrl&offset=0&limit=-1`

(Note: the `_ip=propertyName` parameters are used to _include a property_ in returned data, regardless of what depth at which it is being serialized. Normally, data for items serialized below depth 1 only includes mandatory structural properties. The `_ip` configuration parameter allows overriding this, so we can say "I am always interested in the `imageUrl` property, whenever an item is serialized".)

Next we try running these commands locally with `curl`:

    curl -H "Authorization:mike" "http://localhost:7474/ehri/documentaryUnit/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=urlPattern&_ip=otherFormsOfName&_ip=parallelFormsOfName&_ip=logoUrl&_ip=imageUrl"
    
    curl -H "Authorization:mike" "http://localhost:7474/ehri/permission/mike/scope/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=imageUrl"

    curl -H "Authorization:mike" "http://localhost:7474/ehri/documentaryUnit/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=urlPattern&_ip=otherFormsOfName&_ip=parallelFormsOfName&_ip=logoUrl&_ip=imageUrl"

    curl -H "Authorization:mike" "http://localhost:7474/ehri/userProfile/mike/watching?_ip=imageUrl&offset=0&limit=-1"

    curl -H "Authorization:mike" "http://localhost:7474/ehri/annotation/for/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=imageUrl&offset=0&limit=-1"

    curl -H "Authorization:mike" "http://localhost:7474/ehri/link/for/nl-003006-easy-collection-2-urn-nbn-nl-ui-13-hobu-8f?_ip=imageUrl&offset=0&limit=-1"

In this case, call of these commands returns data with no problems. We can therefore, for now, rule out an issue with the backend.

Now lets look at the frontend in dev mode. As usual, to run a Play app in dev mode just run:

    play ~run  

(The tilde means that it will auto-recompile and start whenever changes are made to the code.)

The controller action that's hanging on the front-end is the one that renders the page for a documentary unit at `localhost:9000/units/<item-id>`. This points to a controller action called `browseDocument(id: String)`, with the following code:

```scala
def browseDocument(id: String) = getAction[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
    item => details => implicit userOpt => implicit request =>
  if (isAjax) Ok(p.documentaryUnit.itemDetails(item, details.annotations, details.links, details.watched))
  else Ok(p.documentaryUnit.show(item, details.annotations, details.links, details.watched))
}
```

What's basically happening here is:

 - call a generic controller method called `getAction` for the type `DocumentaryUnit` with the id `id`.
 - after doing so, run some code that takes some parameters (the documentary unit object, additional details such as annotations and links, the user (if we have one), and the request) and returns a response
 - if it's an Ajax call, return part of a page rendered by the `itemDetails()` function, otherwise, render the whole page using the `show()` function

Since the backend doesn't seem to have a problem, we can confirm this the old-fashioned way by shoving a quick `println` before the template rendering:

```scala
def browseDocument(id: String) = getAction[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
    item => details => implicit userOpt => implicit request =>

  println("DATA WAS RECIEVED OKAY, must be a problem with the rendering...")

  if (isAjax) Ok(p.documentaryUnit.itemDetails(item, details.annotations, details.links, details.watched))
  else Ok(p.documentaryUnit.show(item, details.annotations, details.links, details.watched))
}

When the app recompiles and we re-run the action, this text is printed out prior to the app hanging. Therefore, the hanging **must be something to do with the template rendering**.

This could be awkward to debug the old-fashioned way so lets open JVisualVM and try and see what's hanging directly. In JVisualVM we attach to the `xsbt.boot.Boot (pid 1234)` process (which is what runs the Play dev mode.) In the monitor we can see that the process is clearly active, so go to the "Sampler" tab and click "CPU" to see what's actually being run. Looking down the Hot Spots list we can see:

 - `org.jboss.netty.util.HashedWheelTimer$Worker.waitForNextTick()`
 - `sbt.SourceModificationWatch$.watch()`
 - `org.jboss.netty.channel.socket.nio.SelectorUtil.select()`
 - `com.jolbox.bonecp.PoolWatchThread.run()`
 - `org.parboiled.parserunners.BasicParseRunner.match()`
 - ... etc

The first four items there are just web framework and SBT housekeeping so we can disregard them. But the fifth! That's a **Parboiled** parser function, as used by the **PegDown** markdown-rendering library we use on the front-end. So already we can pretty strongly suspect the cause of this hang is:

 - something in the data looks like Markdown
 - the Markdown parser/renderer is hanging trying to render it as HTML

Before we try to pinpoint exactly what the data/render problem is and attempt to resolve it, we need to face up to some unfortunate realities:

 - at the time of writing this the version of Pegdown used is 1.1. Higher versioned use a version of the ASM library that conflicts with Neo4j
 - this could well be a Pegdown bug that was fixed a long time ago, but we can't update to it for compatibility reasons

Okayyyy....

The quickest way to find out what part of the data is causing the issue is to remove parts of the description being rendered until we pinpoint the guilty part.

We can see from viewing the raw data via curl that most of the data is within the `scopeAndContent` property, and sure enough, commenting out the rendering of this field prevents the page timeout.

So to summarise:

 - as of version fb9c096f709433922fae2fdcd63a32d70bd2046a the project uses [Pegdown](https://github.com/sirthias/pegdown) library to render markdown as HTML. Higher versions of the library (highest is 1.4.2) are not being used because of a library conflict with the `asm` dependency with the Neo4j/Blueprints components which causes a runtime `IncompatibleClassChangeError`.
 - unfortunately Pegdown 1.1.0 does not support parsing timeouts for badly specified markdown, causing it to hang on bad input
 - the document we're looking at is tripping up the parser

Looking more closely at the troublesome data we can see text like this: `Summary_Project_Long_Shadow_of_Sobibor`. This is suspicuous because underscores in markdown represent italic text in certain contexts and there was a [known bug](https://github.com/sirthias/pegdown/issues/43) for unbalanced underscores in Pegdown 1.1.0.

Sure enough, if we replace the underscores in this context with hyphens the data renders with no issues.

So we have a problem: 

 - Pegdown 1.1.0 is buggy and we've just encountered pathalogical input which breaks it
 - we can't currently upgrade Pegdown till the `asm` dependency conflict is resolved
 
**TBC**




 




