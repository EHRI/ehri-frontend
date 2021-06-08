<?xml version="1.0"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ead="urn:isbn:1-931666-22-9"
                exclude-result-prefixes="ead">

    <xsl:output method="xml" omit-xml-declaration="no" indent="yes"/>
    <xsl:param name="test-param" />
    <xsl:param name="test-value" />

    <!-- replace any ead node with the properly namespaced version -->
    <xsl:template match="/*[local-name()='ead']">
        <ead xmlns="urn:isbn:1-931666-22-9"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:xlink="http://www.w3.org/1999/xlink"
                xsi:schemaLocation="http://www.loc.gov/ead/ead.xsd">
            <xsl:if test="$test-param != ''">
                <xsl:attribute name="{$test-param}">
                    <xsl:value-of select="$test-value"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:copy-of select="node()|@*" />
        </ead>
    </xsl:template>
</xsl:stylesheet>
