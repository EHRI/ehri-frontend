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
