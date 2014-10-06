Deployment
==========

Like the backend, this project uses a [Fabric](http://www.fabfile.org/) script for automating deployment actions.
Once fabric is installed you can view the various deployment-related tasks with:

```bash
fab -l
```

The script assumes that you have ssh aliases for the following servers:

 - prod  = `ehriprod`
 - stage = `ehristage`
 - test = `ehritest`
 
Once the project has been built (using `play clean stage`) it can be deployed with:

```bash
fab prod deploy
```

Alternatively you can just do `fab prod clean_deploy` to clean/build and deploy in one go. Currently the
available commands are:

One the server the app is run from within `/opt/webapps/docview`. Within that dir there is a symlink called `target` which
points to the current version with the `deploys` directory. A version is named with the current Git hash appended
with the date. The contents of each deploy directory is the `target` directory that sbt creates.

## Setting the portal to read-only / maintenance mode

There is a configuration value `ehri.readonly.file` which points to a file path. The portal checks on each request that
this path a) exists, and b) is a plain file. If both those conditions are true the portal will prevent people logging in,
which also prevents modifications to the database. In the prod config, this file is `/opt/webapps/docview/READONLY`.

READONLY mode can be toggled on and off from the Fabric like so:

```bash
fab prod readonly
```

This simply touches or deletes `/opt/webapps/docview/READONLY`, depending on whether it exists.