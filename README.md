[![Build Status](https://github.com/EHRI/ehri-frontend/workflows/CI/badge.svg)](https://github.com/EHRI/ehri-frontend/actions?query=workflow%3ACI)

Front-end for  the [EHRI REST](https://github.com/EHRI/ehri-rest) web service.

This app has a few dependencies, with the main ones being:

 - The Neo4j-based EHRI backend service
 - A PostgreSQL 14+ database
 - Solr, running configured as per the [EHRI Search Tools](https://github.com/EHRI/ehri-search-tools)
 - An S3-compatible object storage service (remote in production and local during dev),
   for example [MinIO](https://min.io)

There are currently three docker compose .yml files for development and testing:

 - `docker-compose.yml`: starts PostgreSQL, Neo4j, and Solr in default development config, using data that persists in a
    `DB` directory in the project folder
 - `docker-compose.minio.yml`: starts MinIO in development config as an S3-compatible object store, on ports 9100
     and 9101, with data persisting in the `miniodata` directory
 - `docker-compose.test.yml`: starts PostgreSQL, Neo4j, Solr, and MinIO with ephemeral storage and ports configured to
    match the test config

For more serious development you might want to run Neo4j, PostgreSQL, and Solr in a non-containerised fashion, which is
why the MinIO compose file is separate.

The Makefile contains targets for running the dev, minio, and test services, e.g.:

```bash
make dev-up
```

```bash
make minio-up
```

This will try and detect the container runtime, preferring Podman over Docker, and run the following
commands (using, in this case, the Podman runtime):

```bash
podman-compose --project-name docview-dev -f docker-compose.yml up
```

```bash
podman-compose --project-name docview-minio -f docker-compose.minio.yml up
```

If this all works you should be able to visit the following URLs in your browser and see something:

 - <http://localhost:7474> (Neo4j, with authentication disabled)
 - <http://localhost:8983> (Solr)
 - <http://localhost:9101> (MinIO)
 
If you have the PostgreSQL client libraries installed you should also be able to connect to the `docview` database like so:

```bash
psql -Udocview -hlocalhost docview
```

(Default password for development is 'changeme'.)

### MinIO setup for development

 - Rename `conf/minio.conf.example` to `conf/minio.conf`
 - Create some MinIO buckets for application data and import data: to match the example config use `portal-data` and `import-data`. 
   **Important**: the `portal-data` should be public and the `import-data` should have versioning enabled.

(*NB*: MinIO's default ports clash with the Play Framework, so the docker config remaps its ports to 9100 and 9101.)

### EHRI Backend setup

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

### Additional dev environment dependencies

 - install postfix or a suitable email-sending program
 - install Node JS (which handles client-side asset compilation). It's recommended to install [NVM](https://github.com/nvm-sh/nvm)
    and just type `nvm use` to use the configured Node version
 - install the [sbt launcher](https://www.scala-sbt.org/download.html)
 - Run `sbt` from the project directory and it will download and install the appropriate version and start the sbt shell 
   
### Running the app:

 - from the SBT shell type `run`. The app should start and connect to the PostgreSQL database if all is well
 - go to <http://localhost:9000>] in your browser
 - if/when you get a message about database evolutions being required, click "Apply this script now"
 - create an account at <http://localhost:9000/login>
 - get your new account ID, which can be found by looking at the URL for you account on the people page (<http://localhost:9000/people>). It should be `user000001`.
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

 - create a country record at <http://localhost:9000/admin/countries/create>. You only have to provide the country code, e.g. "us"
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

Running integration tests requires the same external services as for development, though in the `conf/test.conf` file their
ports are non-default. You can set up these services using the test docker compose file, e.g.:

```bash
make test-up
```

Restarting containerised services can be down with corresponding Makefile commands:

```bash
make dev-down
make minio-down
make test-down 
```
