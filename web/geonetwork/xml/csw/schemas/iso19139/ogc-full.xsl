<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
										xmlns:csw="http://www.opengis.net/cat/csw/2.0.2"
										xmlns:dc ="http://purl.org/dc/elements/1.1/"
										xmlns:dct="http://purl.org/dc/terms/"
										xmlns:gco="http://www.isotc211.org/2005/gco"
										xmlns:gmd="http://www.isotc211.org/2005/gmd"
										xmlns:srv="http://www.isotc211.org/2005/srv"
										xmlns:geonet="http://www.fao.org/geonetwork"
										xmlns:ows="http://www.opengis.net/ows"
										exclude-result-prefixes="gmd srv gco">

	<xsl:param name="displayInfo"/>
	
	<!-- ============================================================================= -->

	<xsl:template match="gmd:MD_Metadata|*[@gco:isoType='gmd:MD_Metadata']">
		
		<xsl:variable name="info" select="geonet:info"/>
		<xsl:variable name="identification" select="gmd:identificationInfo/gmd:MD_DataIdentification|
			gmd:identificationInfo/*[@gco:isoType='gmd:MD_DataIdentification']|
			gmd:identificationInfo/srv:SV_ServiceIdentification"/>
		
		<csw:Record>

			<xsl:for-each select="gmd:fileIdentifier">
				<dc:identifier><xsl:value-of select="gco:CharacterString"/></dc:identifier>
			</xsl:for-each>

			<!-- DataIdentification - - - - - - - - - - - - - - - - - - - - - -->

			<xsl:for-each select="$identification/gmd:citation/gmd:CI_Citation">	
				<xsl:for-each select="gmd:title/gco:CharacterString">
					<dc:title><xsl:value-of select="."/></dc:title>
				</xsl:for-each>
				
				<!-- Type - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -->
				
				<xsl:for-each select="../../../../gmd:hierarchyLevel/gmd:MD_ScopeCode/@codeListValue">
					<dc:type><xsl:value-of select="."/></dc:type>
				</xsl:for-each>
				
				<!-- subject -->
				
				<xsl:for-each select="../../gmd:descriptiveKeywords/gmd:MD_Keywords/gmd:keyword/gco:CharacterString">
					<dc:subject><xsl:value-of select="."/></dc:subject>
				</xsl:for-each>
				
				<!-- Distribution - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -->
				
				<xsl:for-each select="../../../../gmd:distributionInfo/gmd:MD_Distribution">
					<xsl:for-each select="gmd:distributionFormat/gmd:MD_Format/gmd:name/gco:CharacterString">
						<dc:format><xsl:value-of select="."/></dc:format>
					</xsl:for-each>
				</xsl:for-each>
				
				
				<xsl:for-each select="gmd:date/gmd:CI_Date[gmd:dateType/gmd:CI_DateTypeCode/@codeListValue='revision']/gmd:date/gco:Date">
					<dct:modified><xsl:value-of select="."/></dct:modified>
				</xsl:for-each>

				<xsl:for-each select="gmd:citedResponsibleParty/gmd:CI_ResponsibleParty[gmd:role/gmd:CI_RoleCode/@codeListValue='originator']/gmd:organisationName/gco:CharacterString">
					<dc:creator><xsl:value-of select="."/></dc:creator>
				</xsl:for-each>

				<xsl:for-each select="gmd:citedResponsibleParty/gmd:CI_ResponsibleParty[gmd:role/gmd:CI_RoleCode/@codeListValue='publisher']/gmd:organisationName/gco:CharacterString">
					<dc:publisher><xsl:value-of select="."/></dc:publisher>
				</xsl:for-each>

				<xsl:for-each select="gmd:citedResponsibleParty/gmd:CI_ResponsibleParty[gmd:role/gmd:CI_RoleCode/@codeListValue='author']/gmd:organisationName/gco:CharacterString">
					<dc:contributor><xsl:value-of select="."/></dc:contributor>
				</xsl:for-each>
			</xsl:for-each>

			
			<!-- abstract -->

			<xsl:for-each select="$identification/gmd:abstract/gco:CharacterString">
				<dct:abstract><xsl:value-of select="."/></dct:abstract>
			</xsl:for-each>

			<!-- rights -->

			<xsl:for-each select="$identification/gmd:resourceConstraints/gmd:MD_LegalConstraints|
				gmd:resourceConstraints/*[@gco:isoType='gmd:MD_LegalConstraints']">
				<xsl:for-each select="*/gmd:MD_RestrictionCode/@codeListValue">
					<dc:rights><xsl:value-of select="."/></dc:rights>
				</xsl:for-each>

				<xsl:for-each select="$identification/otherConstraints/gco:CharacterString">
					<dc:rights><xsl:value-of select="."/></dc:rights>
				</xsl:for-each>
			</xsl:for-each>

			<!-- language -->

			<xsl:for-each select="$identification/gmd:language/gco:CharacterString">
				<dc:language><xsl:value-of select="."/></dc:language>
			</xsl:for-each>
			
			<!-- Lineage -->
			
			<xsl:for-each select="gmd:dataQualityInfo/gmd:DQ_DataQuality/gmd:lineage/gmd:LI_Lineage/gmd:statement/gco:CharacterString">
				<dc:source><xsl:value-of select="."/></dc:source>
			</xsl:for-each>
			
			<!-- Parent Identifier -->
			
			<xsl:for-each select="gmd:parentIdentifier/gco:CharacterString">
				<dc:relation><xsl:value-of select="."/></dc:relation>
			</xsl:for-each>
			
			<!-- bounding box -->

			<xsl:for-each select="$identification/gmd:extent/gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox">
				<xsl:variable name="rsi"  select="/gmd:MD_Metadata/gmd:referenceSystemInfo/gmd:MD_ReferenceSystem/
					gmd:referenceSystemIdentifier/gmd:RS_Identifier|/gmd:MD_Metadata/gmd:referenceSystemInfo/
					*[@gco:isoType='MD_ReferenceSystem']/gmd:referenceSystemIdentifier/gmd:RS_Identifier"/>
				<xsl:variable name="auth" select="$rsi/gmd:codeSpace/gco:CharacterString"/>
				<xsl:variable name="id"   select="$rsi/gmd:code/gco:CharacterString"/>

				<ows:BoundingBox crs="{$auth}::{$id}">
					<ows:LowerCorner>
						<xsl:value-of select="concat(gmd:eastBoundLongitude/gco:Decimal, ' ', gmd:southBoundLatitude/gco:Decimal)"/>
					</ows:LowerCorner>
	
					<ows:UpperCorner>
						<xsl:value-of select="concat(gmd:westBoundLongitude/gco:Decimal, ' ', gmd:northBoundLatitude/gco:Decimal)"/>
					</ows:UpperCorner>
				</ows:BoundingBox>
			</xsl:for-each>
			
			<!-- GeoNetwork elements added when resultType is equal to results_with_summary -->
			<xsl:if test="$displayInfo = 'true'">
				<xsl:copy-of select="$info"/>
            </xsl:if>
			
		</csw:Record>
	</xsl:template>

	<!-- ============================================================================= -->

	<xsl:template match="*">
		<xsl:apply-templates select="*"/>
	</xsl:template>

	<!-- ============================================================================= -->

</xsl:stylesheet>
