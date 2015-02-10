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

## Setting the portal to read-only mode

There is a configuration value `ehri.readonly.file` which points to a file path. The portal checks on each request that
this path a) exists, and b) is a plain file. If both those conditions are true the portal will prevent people logging in,
which also prevents modifications to the database. In the prod config, this file is `/opt/webapps/docview/READONLY`.

READONLY mode can be toggled on and off from the Fabric like so:

```bash
fab prod readonly
```

This simply touches or deletes `/opt/webapps/docview/READONLY`, depending on whether it exists.

## Setting the portal to maintenance mode

Maintenance mode simply serves 503 service unavailable to everyone, but in a prettier manner than shutting down
the server. It can be enabled by touching `/opt/webapps/docview/MAINTENANCE` on the server or using the Fabric command:

```bash
fab prod maintenance
```

## Serving a message on all pages

For messages like "The site will be down for maintenance in 1 hour" create a file called `/opt/webapps/docview/MESSAGE`
containing the message text, or use the Fabric command:

```bash
fab prod message:"This is some message text"
```

Run the command without a message to disable.

## Serving 503 to everyone except specific IP addresses

If you want to conduct maintenance on the site without other people interfering, an IP whitelist can be enabled by creating
a file `/opt/webapps/docview/IP_WHITELIST` containing the allowed IPs one per line. For single IPs the Fabric command can
be used:

```bash
fab prod whitelist:123.123.123.123
```

Note: this system is pretty stupid and doesn't handle IP ranges or anything.


