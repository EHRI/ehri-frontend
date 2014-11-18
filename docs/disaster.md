Disaster Recover and General Troubleshooting
===========================================

If something is going wrong with the database the first step should be
to put it into read-only mode. This is done by placing an empty file
called `READONLY` at the project root (`/opt/webapps/docview`). This
will prevent people from logging in, which should likewise prevent any
changes being made to the database.

Read-only mode can be toggled using the Fabric command:

    fab prod readonly

Running the same command when read-only mode is enabled will disable it.

A backup should then be made of the production database prior to any
troubleshooting. This can be done using the **backend management**
tasks, in particular `online_clone_db:<local-filename>`.

The state of the Neo4j database can then be investigated either using the
local clone, or via an SSH port forward to the running instance on production,
e.g.:

    ssh ehriprod -L7474:localhost:7575

(This makes the Neo4j webadm console available locally at port 7575.)


