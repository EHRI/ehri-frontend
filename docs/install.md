# Setting up the development environment

## Prerequisites

For development, you need a version of the EHRI Neo4j REST server installed both as libraries and running standalone. This can be done by following the instructions [here](https://github.com/mikesname/ehri-rest/blob/master/docs/INSTALL.md).

### Install and set up Solr

Download Solr and extract it to the location of your choice (using ~/apps for this example):

	curl -0 http://mirrors.ukfast.co.uk/sites/ftp.apache.org/lucene/solr/4.2.1/solr-4.2.1.tgz | tar -zx -C ~/apps

For now, re-use the example Solr core (named "collection1", inside the example/solr direction).  As a shortcut, you can just grab the `schema.xml` and `solrconfig.xml` from Github:

	curl https://rawgithub.com/mikesname/ehri-indexer/master/solrconf/schema.xml > ~/apps/solr-4.2.1/example/solr/collection1/conf/schema.xml
	curl https://rawgithub.com/mikesname/ehri-indexer/master/solrconf/solrconfig.xml > ~/apps/solr-4.2.1/example/solr/collection1/conf/solrconfig.xml

You should now able able to start the Solr server in another shell:

	cd ~/apps/solr-4.2.1/example
	java -jar start.jar

If that starts without spewing out any dodgy-looking stack traces all should be well. You can verify this by going to http://localhost:8983/solr which should display the Solr admin page.

### Install and set up the indexer utility

The EHRI frontend does not interact directly with Solr for indexing (it used to, but this made it difficult to tune indexing without mucking about the the frontend code.) Instead there's a [separate utility](https://github.com/mikesname/ehri-indexer) that deals with transforming the database format JSON into Solr format JSON, and provides a convenient command-line syntax to index individual items and classes of items. The front-end currently delegates to this command-line tool.

To set up and build the indexer, do the following:

    cd ~/dev
    git clone https://github.com/mikesname/ehri-indexer.git
    cd ehri-indexer
    mvn clean compile assembly:single

If all goes well this will result in a single Jar file called `ehri-indexer-1 .0-SNAPSHOT-jar-with-dependencies.jar` ending up in the `target` directory.

### Installing Play 2.2.1:

Download and install Play 2.2.x:

    export PLAY_VERSION=2.2.2
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

### MySQL Docs

While this is running, we can set up the other database, used for authentication. Install MySQL via your favoured channel (Brew, Apt):

    sudo apt-get install mysql-server

Now we need to create an empty user and database for our application. The user and database will have the same name (docview). Start the MySQL admin console:

    mysql -uroot

Now, **at the MySQL shell**, type the following commands (replacing the password with your password):

    CREATE USER 'docview'@'localhost' IDENTIFIED BY '<PASSWORD>';
    CREATE DATABASE docview;
    GRANT ALL PRIVILEGES ON docview.* TO 'docview'@'localhost';

There are some settings on the conf/application.conf file you can adjust if you change any of the defaults.

===============================================================================

### PostgreSQL - ALTERNATIVE DB instructions

**NB: Postgres is not the default DB given here (for operational reasons). If you want to use it, rename the `conf/evolutions/default` directory to `conf/evolutions/mysql` and rename `conf/evolutions/postgres` to `conf/evolutions/default`.**

Install via your favourite method. Note that on some OS X versions, Postgres can be a bit fiddly because the one installed by brew conflicts with the bundled default:

    sudo apt-get install postgresql

Now we need to create an empty user and database for our application. The user and database will have the same name (docview). Start the Postgres shell (run as the postgres user):

    sudo su postgres -c psql

Now, **in the psql shell**, type the following commands (replacing the password with your password):

    CREATE USER docview WITH PASSWORD '<PASSWORD>';
    CREATE DATABASE docview;
    GRANT ALL PRIVILEGES ON DATABASE docview TO docview;

There are some settings on the conf/application.conf file you can adjust if you change any of the defaults.

===============================================================================

## Back to Solr

One setting you definitely should change is the value of the `solr.path` key, which needs to be changed to whatever the path to the Solr core is. Since the one we set up above used the default "collection1" name, adjust the setting to match this:

    solr.path = "http://localhost:8983/solr/collection1"

Also, we need to put the indexer utility where the interface can find it, in the `bin` directory, named `indexer`. This can be done with a symlink:

    ln -s ~/dev/ehri-indexer/target/ehri-indexer-1 .0-SNAPSHOT-jar-with-dependencies.jar ~/dev/docview/bin/indexer

Start Neo4j server, if you haven't already:

    $NEO4J_HOME/bin/neo4j start

We can now see if the app actually works:

    play run

Now, visit http://localhost:9000 in your browser. The app should show a screen saying it needs to apply a migration to the database. **Click the "Apply This Script Now" button.**

Next, we have a little problem because we need to create the login details of our administrative user in the authorisation database. Unfortunately there is no way at present to do this without mucking with the database directly.

Basically, we need to create a database entry that links the default username you created in Neo4j to an email address (the email address is a key that identifies a user.)

So open up the MySql console again:

    mysql -udocview -p -hlocalhost docview # or for postgres: sudo su postgres -c "psql docview"

First, **in the DB shell**, double check there is no existing user and/or email:

    SELECT * FROM users;

```SQL
mysql> select * from users;
 Empty Set
```

Now add one corresponding to your user + email:

```SQL
mysql> INSERT INTO users (id, email, verified, staff, active) VALUES ('example', 'example@example.com', 1, 1, 1);
Query OK, 1 row affected (0.00 sec)
```

**Now log in via OpenID for the email you just created**. The application will notice that there is already a corresponding email in the database and, if the OpenID auth succeeds, add an OpenID associate to the account.

Once logged in to the app you should have full admin privileges. You can try using an OpenID email account that has not been _pre set up_ and the application will create you a default account with no privileges.

The first thing to do when logging in is to build the search index. This can be done by going to:

    http://localhost:9000/admin/updateIndex

... and checking all the boxes. With luck, or rather, assuming Solr is configured property, the search index should get populated from the Neo4j database.

