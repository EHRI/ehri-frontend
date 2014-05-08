#!/bin/sh

export _JAVA_OPTIONS="-Xms64m -Xmx1024m -Xss2m -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -Dconfig.file=conf/test.conf"

if [ -z "$1" ]; then
    $HOME/apps/activator-1.1.1/activator test
else 
    $HOME/apps/activator-1.1.1/activator "test-only $@"
fi
