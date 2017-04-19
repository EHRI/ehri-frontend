[![Build Status](https://travis-ci.org/EHRI/ehri-frontend.svg?branch=master)](https://travis-ci.org/EHRI/frontend)

Front-end for  the [EHRI REST](https://github.com/EHRI/ehri-rest) web service.

This app has a few depependencies in addition to the backend:

 - A PostgreSQL 9.5+ database
 - Solr, running configurated as per the config in [EHRI Search Tools](https://github.com/EHRI/ehri-search-tools)

The setup docs to get these dependencies up and running ended up horribly out-of-date, so rather than
actively mislead people they've been temporarily removed pending the completion of some [Docker](http://www.docker.com)
-based dev setup instructions. In the meantime, here's how they'll start:

 - Set up the search engine on port 8983: `sudo docker run --publish 8983:8983 -it ehri/ehri-search-tools` 
 - Set up the backend web service on port 7474: `sudo docker run --publish 7474:7474 -it ehri/ehri-rest`
 - [set up PostgreSQL with the right schema]
 - install Typesafe activator
 - `activator run`
 - go to localhost:9000
 - create an account at http://localhost:9000/login
 - get your new account id (probably `user000001`)
 - run `curl -X POST http://localhost:7474/ehri/group/admin/{userId}` to make dev admin 

### Testing

Running integration tests requires an instance of the backend service running locally on port 7575. This can be done with a single Docker command:

    sudo docker run --publish 7575:7474 -it ehri/ehri-rest
