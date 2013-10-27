#!/bin/sh

export _JAVA_OPTIONS="-Xms64m -Xmx1024m -Xss2m -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -Dconfig.file=conf/test.conf"

play test
