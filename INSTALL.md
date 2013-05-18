# Setting up the development environment

## Prerequisites

For development, you need a version of the EHRI Neo4j REST server installed both as libraries and running standalone. This can be done by following the instructions [here](https://github.com/mikesname/neo4j-ehri-plugin/blob/master/docs/INSTALL.md).

### Install and set up Solr

Download Solr and extract it to the location of your choice (using ~/apps for this example):

	curl -0 http://mirrors.ukfast.co.uk/sites/ftp.apache.org/lucene/solr/4.2.1/solr-4.2.1.tgz | tar -zx -C ~/apps

For now, re-use the example Solr core (named "collection1", inside the example/solr direction).  As a shortcut, you can just grab the `schema.xml` and `solrconfig.xml` from Github:

	curl https://raw.github.com/mikesname/docview/master/etc/schema.xml > ~/apps/solr-4.2.1/example/solr/collection1/conf/schema.xml
	curl https://raw.github.com/mikesname/docview/master/etc/solrconfig.xml > ~/apps/solr-4.2.1/example/solr/collection1/conf/solrconfig.xml

You should now able able to start the Solr server in another shell:

	cd ~/apps/solr-4.2.1/example
	java -jar start.jar

If that starts without spewing out any dodgy-looking stack traces all should be well. You can verify this by going to http://localhost:8983/solr which should display the Solr admin page.


### Installing Play 2.1:

Download and install Play 2.1:

    export PLAY_VERSION=2.1.1
    wget http://downloads.typesafe.com/play/${PLAY_VERSION}/play-${PLAY_VERSION}.zip
    unzip -d ~/apps play-${PLAY_VERSION}

Add the "play" command to your path (and your personal .bashrc/.profile if desired):

    export PATH=$PATH:$HOME/apps/play-${PLAY_VERSION}
    
## Setting up the development code:

Download the source from Github:

    cd ~/dev
    git clone https://github.com/mikesname/docview.git

Start the dependency download process (which usually takes a while):

    cd docview
    play clean compile

While this is running, we can set up the other database, used for authentication. This currently runs on Postgres:

    sudo apt-get install postgresql

Now we need to create an empty user and database for our application. The user and database will have the same name (docview) and we will use the default password (changeme). Start the Postgres shell (run as the postgres user):

    sudo su postgres -c psql

Now, **in the psql shell**, type the following commands:

    CREATE USER docview WITH PASSWORD 'changeme';
    CREATE DATABASE docview;
    GRANT ALL PRIVILEGES ON DATABASE docview TO docview;

There are some settings on the conf/application.conf file you can adjust if you change any of the defaults.

One setting you definitely should change is the value of the `solr.path` key, which needs to be changed to whatever the path to the Solr core is. Since the one we set up above used the default "collection1" name, adjust the setting to match this:

    solr.path = "http://localhost:8983/solr/collection1"

Start Neo4j server, if you haven't already:

    $NEO4J_HOME/bin/neo4j start

We can now see if the app actually works:

    play run

Now, visit http://localhost:9000 in your browser. The app should show a screen saying it needs to apply a migration to the database. **Click the "Apply This Script Now" button.**

Next, we have a little problem because we need to create the login details of our administrative user in the authorisation database. Unfortunately there is no way at present to do this without mucking with the database directly.

**Log in via OpenID**. The application with create you a default user id (like user00001), but by default your account will have no privileges. We need to change the default generated user ID to the one you earlier created in Neo4j.

So open up the Postgres shell again:

    sudo su postgres -c "psql docview"

First, **in the psql shell**, double check there is a user with an auto-generated profile id in the database:

    SELECT * FROM users;

```SQL
docview=# select * from users;
 id |        email        | profile_id
----+---------------------+------------
  1 | myemail@gmail.com | user000002
(1 row)

```

Now, **CHANGING `$USER` BELOW TO WHATEVER YOUR USER ACTUALLY IS**, run the update command:

    UPDATE users SET profile_id = '$USER' WHERE id = 1;

Now, re-log in to the app and you should have full admin privileges.

The first thing to do when logging in is to build the search index. This can be done by going to:

    http://localhost:9000/admin/updateIndex

... and checking all the boxes. With luck, or rather, assuming Solr is configured property, the search index should get populated from the Neo4j database.


