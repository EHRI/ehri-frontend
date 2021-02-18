[![Build Status](https://github.com/EHRI/ehri-frontend/workflows/CI/badge.svg)](https://github.com/EHRI/ehri-frontend/actions?query=workflow%3ACI)

Front-end for  the [EHRI REST](https://github.com/EHRI/ehri-rest) web service.

This app has a few depependencies in addition to the backend:

 - A PostgreSQL 9.5+ database
 - Solr, running configurated as per the config in [EHRI Search Tools](https://github.com/EHRI/ehri-search-tools)

The setup docs to get these dependencies up and running ended up horribly out-of-date, so rather than
actively mislead people they've been temporarily removed pending the completion of some [Docker](https://www.docker.com)
-based dev setup instructions. In the meantime, here's how they'll start:

 - Set up the search engine on port 8983: 
 
     `sudo docker run --publish 8983:8983 -it ehri/ehri-search-tools`
      
 - Set up the backend web service on port 7474: 
 
     `sudo docker run --publish 7474:7474 -it ehri/ehri-rest`
     
 - Set up PostgreSQL (Dockerised) with the right schema: 
 
     `sudo docker run -e POSTGRES_USER=docview -e POSTGRES_PASSWORD=changeme --publish 5432:5432 postgres`

 - Create an additional group on the backend named "portal":

```bash 
curl  --header content-type:application/json \
      --header X-User:admin \
      --data-binary '{
           "id":"portal", 
           "type":"Group",
           "data":{"identifier": "portal", "name":"Portal"}
      }' \
      http://localhost:7474/ehri/classes/Group
```

 - install postfix or a suitable email-sending program
 - install Node JS (which handles client-side asset compilation)
 - install [sbt](http://www.scala-sbt.org/release/docs/Setup.html)
 - `sbt run`
 - go to localhost:9000
 - when you get a message about database evolutions being required, click "Apply this script now"
 - create an account at http://localhost:9000/login
 - get your new account ID, which can be found by looking at the URL for you account on the people page (`http://localhost:9000/people`). It should be `user000001`.
 - make developer account an admin on the backend (replace `{userId}` with actual ID):
 
 ```bash
curl -X POST \
        --header X-User:admin \
        http://localhost:7474/ehri/classes/Group/admin/{userId}
 ```
 
 - make account verified and staff on the front end (replace {userId} with actual ID and use default password 'changeme'):
 
 ```bash
psql -hlocalhost -Udocview docview \
        -c "update users set verified = true, staff = true where id = '{userId}'"
```

At this point you should be able to access the admin pages and create data, e.g:

 - create a country record at `http://localhost:9000/admin/countries/create`. You only have to provide the country code, e.g. "us"
 - create an institution in that country
 - create archival records in the institution
 
NOTE: certain functionality also depends on a valid AWS S3 configuration set in the `conf/aws.conf` file.
Use the `conf/aws.conf.example` as a template.

### Note on JS/CSS library assets

These assets live in the source tree in the `modules/{module}/app/assets/{js,css}/lib` directories but the reference for their 
versions is the `package.json` file that is managed by NPM. Modules within the `node_modules` directory are then copied to the 
source tree by the `./node_modules/.bin/grunt copy` command.

The copying is configured to make a few small changes to some files for e.g. the relative font path in the font-awesome SASS file
and to replace CRLF with LF where necessary.

Note: at present Grunt is only used for copying assets from `node_modules`. JS minification and SASS compilation is managed by
the main SBT build system.

### Testing

Running integration tests requires a number of different external services, including:

 - an instance of the Neo4j backend on port 7575
 - a PostgreSQL database on port 5431
 - an S3-compatible storage service with appropriate buckets set up running on port 9876 
 - an SMTP server on port 2500

This can be done with a single Docker Compose command:

    sudo docker-compose up
