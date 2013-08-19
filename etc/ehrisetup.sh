#!/bin/sh

# Set up the EHRI server

SOLR_VERS=4.4.0

set -e

# Step 1: download and extract Solr 4.4.0 to /opt
SOLR_URL=http://mirrors.supportex.net/apache/lucene/solr/${SOLR_VERS}/solr-${SOLR_VERS}.tgz
echo "Downloading $SOLR_URL"
curl -0 $SOLR_URL | tar -zx -C /tmp

SOLR_SRC=/tmp/solr-$SOLR_VERS

echo "Creating Solr install..."
# Create a directory to house the multicore install
mkdir /opt/solr4

SOLR_NEW=/opt/webapps/solr4/ehri

# Copy the example directory from the downloaded install to the Solr4 dir
cp -r $SOLR_SRC/example/solr $SOLR_NEW

# Copy the WAR file from the download to the install
cp $SOLR_SRC/dist/solr-${SOLR_VERS}.war $SOLR_NEW/solr.war

# Copy the example collection core to two places
cp -r $SOLR_NEW/collection1   $SOLR_NEW/portal
cat > $SOLR_NEW/portal/core.properties << EOF
name=portal
EOF

mv $SOLR_NEW/collection1      $SOLR_NEW/registry
cat > $SOLR_NEW/registry/core.properties << EOF
name=registry
EOF
chown -R tomcat.tomcat $SOLR_NEW/registry
chown -R tomcat.tomcat $SOLR_NEW/portal

# Create a Tomcat descriptor
echo "Creating Tomcat descriptor for Solr..."
cat > /etc/tomcat6/Catalina/localhost/ehri.xml << EOF
<?xml version="1.0" encoding="utf-8"?>
<Context docBase="$SOLR_NEW/solr.war" debug="0" crossContext="true">
  <Environment name="solr/home" type="java.lang.String" value="$SOLR_NEW" override="true"/>
</Context>
EOF


# Do horrid copying of logging jars specified here:
# http://wiki.apache.org/solr/SolrLogging
echo "Copying logging jars to Tomcat install lib dir..."
cp $SOLR_SRC/example/lib/ext/*jar /usr/share/tomcat6/lib
cp $SOLR_SRC/example/resources/log4j.properties /usr/share/tomcat6/lib


echo "Restarting Tomcat..."
/etc/init.d/tomcat6 restart

echo "Cleaning up..."
rm -rf $SOLR_SRC

exit 0
