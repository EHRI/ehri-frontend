#!/bin/sh

#export _JAVA_OPTIONS="-Xms64m -Xmx1024m -Xss2m -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -Dconfig.file=conf/test.conf -Dlogback.configurationFile=$HOME/dev/play/docview/conf/test-logger.xml"
export _JAVA_OPTIONS="-Xms64m -Xmx1024m -Xss2m -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -Dconfig.file=conf/test.conf -Dlogger.resource=$HOME/dev/play/docview/conf/test-logger.xml"

if [ -z "$1" ]; then
    play test
else 
    play "test-only $@"
fi
