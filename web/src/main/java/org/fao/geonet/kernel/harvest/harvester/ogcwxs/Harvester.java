//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.kernel.harvest.harvester.ogcwxs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jeeves.interfaces.Logger;
import jeeves.resources.dbms.Dbms;
import jeeves.server.context.ServiceContext;
import jeeves.utils.BinaryFile;
import jeeves.utils.Xml;
import jeeves.utils.XmlRequest;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.constants.Params;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.kernel.SchemaManager;
import org.fao.geonet.kernel.harvest.BaseAligner;
import org.fao.geonet.kernel.harvest.harvester.CategoryMapper;
import org.fao.geonet.kernel.harvest.harvester.GroupMapper;
import org.fao.geonet.kernel.harvest.harvester.HarvestResult;
import org.fao.geonet.kernel.harvest.harvester.UUIDMapper;
import org.fao.geonet.lib.Lib;
import org.fao.geonet.services.thumbnail.Set;
import org.fao.geonet.util.FileCopyMgr;
import org.fao.geonet.util.Sha1Encoder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.Text;
import org.jdom.filter.ElementFilter;
import org.jdom.xpath.XPath;

//=============================================================================
/**
 * A OgcWxSHarvester is able to generate metadata for data and service from a
 * GetCapabilities documents. Metadata for layers are generated using layer
 * information contained in the GetCapabilities document or using a xml document
 * pointed by the metadataUrl attribute of layer element.
 * 
 * OGC services supported are :
 * <ul>
 * <li>WMS</li>
 * <li>WFS</li>
 * <li>WCS</li>
 * <li>WPS</li>
 * <li>SOS</li>
 * </ul>
 * 
 * Metadata produced are :
 * <ul>
 * <li>ISO19119 for service's metadata</li>
 * <li>ISO19139 for data's metadata</li>
 * </ul>
 * 
 * Note : Layer stands for "Layer" for WMS, "FeatureType" for WFS and "Coverage"
 * for WCS.
 * 
 * <pre>
 * <nodes>
 *  <node type="ogcwxs" id="113">
 *    <site>
 *      <name>TEST</name>
 *      <uuid>c1da2928-c866-49fd-adde-466fe36d3508</uuid>
 *      <account>
 *        <use>true</use>
 *        <username />
 *        <password />
 *      </account>
 *      <url>http://localhost:8080/geoserver/wms</url>
 *      <ogctype>WMS111</ogctype>
 *      <icon>default.gif</icon>
 *    </site>
 *    <options>
 *      <every>90</every>
 *      <oneRunOnly>false</oneRunOnly>
 *      <status>active</status>
 *      <lang>eng</lang>
 *      <useLayer>true</useLayer>
 *      <useLayerMd>false</useLayerMd>
 *      <datasetCategory></datasetCategory>
 *    </options>
 *    <privileges>
 *      <group id="1">
 *        <operation name="view" />
 *      </group>
 *    </privileges>
 *    <categories>
 *      <category id="3" />
 *    </categories>
 *    <info>
 *      <lastRun>2007-12-05T16:17:20</lastRun>
 *      <running>false</running>
 *    </info>
 *  </node>
 * </nodes>
 * </pre>
 * 
 * @author fxprunayre
 * 
 */
class Harvester extends BaseAligner {

    /**
     * Constructor
     * 
     * @param log
     * @param context
     *            Jeeves context
     * @param dbms
     *            Database
     * @param params
     *            Information about harvesting configuration for the node
     * 
     * @return null
     */
    public Harvester(Logger log, ServiceContext context, Dbms dbms, OgcWxSParams params) {
        this.log = log;
        this.context = context;
        this.dbms = dbms;
        this.params = params;

        result = new HarvestResult();

        GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);
        dataMan = gc.getDataManager();
        schemaMan = gc.getSchemamanager();
    }

    protected List<String> previouslyHarvested = new ArrayList<String>();
    // ---------------------------------------------------------------------------
    // ---
    // --- API methods
    // ---
    // ---------------------------------------------------------------------------
    /**
     * Start the harvesting of a WMS, WFS or WCS node.
     */
    public HarvestResult harvest() throws Exception {
        Element xml;

        // TODO rewrite completely, so that it can be testable !
        // 0. void cleanupPreviousServiceMetadata()
        // 1. Element retrieveGetCapabilitiesDocument(URL url)
        // 2. Element transformGetCapabilitiesToServiceMetadata(Element capa)
        // 3. if (userLayer || userLayerMd)
        // 3.1.  List<Element> extractLayerFromGetCapabilities(Element capa)
        // 3.2.  for each (Element) layer do
        // 3.2.1    if userLayerMd then 
        // 3.2.1.1    extractRemoteMetadataUrl(Element layer)
        // 3.2.1.2    dataMetadata <= retrieveRemoteMetadata(URL url)
        // 3.2.1.4    if error previousError <= true
        //          end
        //       end
        // 3.3.  if (userLayer || (userLayerMd && previousError))
        // 3.3.1.  dataMetadata <= generateDataMetadataFromLayer(Element layer)
        // 3.4.1 if (alreadyInLocalCatalog(dataMetadata)
        // 3.4.2    insertMetadata(dataMetadata)
        // 3.5.1 else
        // 3.5.2    updateMetadata(dataMetadata)
        // 3.6.  generatesOperatesOnAndUpdateServiceMetadata(Element serviceMetadata, List<Element> dataMetadata)
        
        
        log.info("Retrieving remote metadata information for : " + params.name);

        // Clean all before harvest : Remove/Add mechanism. If harvest failed
        // (ie. if node unreachable), metadata will be removed, and the node
        // will not be referenced in the catalogue until next harvesting.

        // TODO : define a rule for UUID in order to be able to do an update
        // operation ?

        UUIDMapper localUuids = new UUIDMapper(dbms, params.uuid);

        // Try to load capabilities document
        this.capabilitiesUrl = getBaseUrl(params.url) + "SERVICE=" + params.ogctype.substring(0, 3) + "&VERSION="
                + params.ogctype.substring(3) + "&REQUEST=" + GETCAPABILITIES;

        if (log.isDebugEnabled())
            log.debug("GetCapabilities document: " + this.capabilitiesUrl);

        XmlRequest req = new XmlRequest();
        req.setUrl(new URL(this.capabilitiesUrl));
        req.setMethod(XmlRequest.Method.GET);
        Lib.net.setupProxy(context, req);

        if (params.useAccount) {
            req.setCredentials(params.username, params.password);
        }

        xml = req.execute();

        
        /*
         * Removes only the service MD associated with the service. Keeping the
         * already harvested data MD, because there is no point removing them
         * and reinserting them if they did not change at all. see
         * https://github.com/geonetwork/core-geonetwork/issues/893
         */
        for (String uuid : localUuids.getUUIDs()) {
            String id = localUuids.getID(uuid);
            if (isServiceMetadata(dataMan.getMetadata(dbms, id))) {
                // Remove thumbnails
                unsetThumbnail(id);
                // Remove metadata
                dataMan.deleteMetadata(context, dbms, id);
                result.locallyRemoved++;
            } else {
                // push into the previouslyHarvested metadata list for later use
                log.info("Adding MD uuid " + uuid + " to the metadata previously harvested list.");
                previouslyHarvested.add(uuid);
            }
        }

        if (result.locallyRemoved > 0)
            dbms.commit();

        // Convert from GetCapabilities to ISO19119
        addMetadata(xml);

        dbms.commit();

        result.totalMetadata = result.addedMetadata + result.layer;

        return result;
    }

    /**
     * Checks if a MD is a service metadata or a data metadata.
     *
     * @param md
     *            a Metadata to be tested
     *
     * @return true if the provided MD is a service metadata, false if it is a
     *         data metadata.
     * 
     * @throws JDOMException
     *
     *             TODO: it might be misplaced here, and shall go to ISO19139/
     *             Xml Utils specific classes.
     *
     */
    private boolean isServiceMetadata(Element md) throws JDOMException {
        Namespace gmd = Namespace.getNamespace("gmd", "http://www.isotc211.org/2005/gmd");
        Namespace gco = Namespace.getNamespace("gco", "http://www.isotc211.org/2005/gco");
        Namespace srv = Namespace.getNamespace("srv", "http://www.isotc211.org/2005/srv");
        
        XPath xp = XPath.newInstance(".//gmd:identificationInfo/srv:SV_ServiceIdentification|"
                + ".//gmd:identificationInfo/*[contains(@gco:isoType, 'SV_ServiceIdentification')]");
        xp.addNamespace(gmd);
        xp.addNamespace(gco);
        xp.addNamespace(srv);
        List nodes = xp.selectNodes(md);
        return ! nodes.isEmpty();
    }
    
    /**
     * Fetches a remote XML document using the parameters provided for the harvesting endpoint.
     * If an account is given to query the remote service, it might be necessary to also use it to get the remote metadata.
     * 
     */
    
    /**
     * Add metadata to the node for a WxS service
     * 
     * - 1.Use GetCapabilities Document
     * - 2.Transform using XSLT to iso19119
     * - 3.Loop through layers
     * - 4.Create md for layer
     * - 5.Add operatesOn elem with uuid
     * - 6.Save all
     *
     * @param capa
     *            GetCapabilities document
     * 
     */
    private void addMetadata(Element capa) throws Exception {
        if (capa == null)
            return;

        // --- Loading categories and groups
        localCateg = new CategoryMapper(dbms);
        localGroups = new GroupMapper(dbms);

        // sha1 the full capabilities URL
        String uuid = Sha1Encoder.encodeString(this.capabilitiesUrl); // is the
                                                                      // service
                                                                      // identifier

        // --- Loading stylesheet
        String styleSheet = schemaMan.getSchemaDir(params.outputSchema) + Geonet.Path.CONVERT_STYLESHEETS
                + "/OGCWxSGetCapabilitiesto19119/" + "/OGC" + params.ogctype.substring(0, 3)
                + "GetCapabilities-to-ISO19119_ISO19139.xsl";

        if (log.isDebugEnabled()) {
            log.debug("  - XSLT transformation using " + styleSheet);
        }

        Map<String, String> param = new HashMap<String, String>();
        param.put("lang", params.lang);
        param.put("topic", params.topic);
        param.put("uuid", uuid);

        Element md = Xml.transform(capa, styleSheet, param);

        String schema = dataMan.autodetectSchema(md, null); // ie. iso19139;

        if (schema == null) {
            log.warning("Skipping metadata with unknown schema.");
            result.unknownSchema++;
        }

        // --- Create metadata for layers only if the user asked for it
        if (params.useLayer || params.useLayerMd) {
            // Load CRS
            // TODO

            // --- Select layers, featureTypes and Coverages (for layers having
            // no child named layer = not take group of layer into account)
            // and add the metadata
            XPath xp = XPath.newInstance("//Layer[count(./*[name(.)='Layer'])=0] | "
                    + "//wms:Layer[count(./*[name(.)='Layer'])=0] | " + "//wfs:FeatureType | "
                    + "//wcs:CoverageOfferingBrief | " + "//sos:ObservationOffering");
            xp.addNamespace("wfs", "http://www.opengis.net/wfs");
            xp.addNamespace("wcs", "http://www.opengis.net/wcs");
            xp.addNamespace("wms", "http://www.opengis.net/wms");
            xp.addNamespace("sos", "http://www.opengis.net/sos/1.0");

            @SuppressWarnings("unchecked")
            List<Element> layers = xp.selectNodes(capa);
            if (layers.size() > 0) {
                log.info("  - Number of layers, featureTypes or Coverages found : " + layers.size());

                for (Element layer : layers) {
                    WxSLayerRegistry s = addLayerMetadata(layer, capa);
                    if (s != null)
                        layersRegistry.add(s);
                }
                // Update ISO19119 for data/service links creation (ie.
                // operatesOn element)
                // The editor will support that but it will make quite heavy
                // XML.
                md = addOperatesOnUuid(md, layersRegistry);
            }
        }
        
        // Removes remaining metadatas from previouslyHarvested
        for (String uuidPrev : previouslyHarvested) {
            try {
                log.info("Removing metadata id " + uuidPrev + " which is no longer referenced in the GetCapability response");
                dataMan.deleteMetadata(context, dbms, dataMan.getMetadataId(dbms, uuidPrev));
            } catch (Exception e) {
                log.error("Error occured while trying to remove MD id: " + uuidPrev + ". Cause: " + e.getMessage());
            }
        }

        // Save iso19119 metadata in DB
        log.info("  - Adding metadata for services with uuid: " + uuid);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date date = new Date();

        //
        // insert metadata
        //
        String group = null, isTemplate = null, docType = null, title = null, category = null;
        boolean ufo = false, indexImmediate = false;
        String id = dataMan.insertMetadata(context, dbms, schema, md,
                context.getSerialFactory().getSerial(dbms, "Metadata"), uuid, Integer.parseInt(params.ownerId), group,
                params.uuid, isTemplate, docType, title, category, df.format(date), df.format(date), ufo,
                indexImmediate);

        int iId = Integer.parseInt(id);

        addPrivileges(id, params.getPrivileges(), localGroups, dataMan, context, dbms, log);
        addCategories(id, params.getCategories(), localCateg, dataMan, dbms, context, log, null);

        dataMan.setHarvestedExt(dbms, iId, params.uuid, params.url);
        dataMan.setTemplate(dbms, iId, "n", null);

        dbms.commit();
        // dataMan.indexMetadata(dbms, id); setTemplate update the index

        result.addedMetadata++;

        // Add Thumbnails only after metadata insertion to avoid concurrent
        // transaction
        // and loaded thumbnails could eventually failed anyway.
        if (params.ogctype.startsWith("WMS") && params.createThumbnails) {
            for (WxSLayerRegistry layer : layersRegistry) {
                loadThumbnail(layer);
            }
        }
    }

    /**
     * Add OperatesOn elements on an ISO19119 metadata
     * 
     * <srv:operatesOn> <gmd:MD_DataIdentification uuidref=""/>
     * </srv:operatesOn>
     *
     * @param md
     *            iso19119 metadata
     * @param layersRegistry
     *            uuid to be added as an uuidref attribute
     * 
     */
    private Element addOperatesOnUuid(Element md, List<WxSLayerRegistry> layersRegistry) {

        Namespace gmd = Namespace.getNamespace("gmd", "http://www.isotc211.org/2005/gmd");
        Namespace gco = Namespace.getNamespace("gco", "http://www.isotc211.org/2005/gco");
        Namespace srv = Namespace.getNamespace("srv", "http://www.isotc211.org/2005/srv");
        Namespace xlink = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");

        Element root = md.getChild("identificationInfo", gmd).getChild("SV_ServiceIdentification", srv);

        if (root != null) {
            if (log.isDebugEnabled())
                log.debug("  - add SV_CoupledResource and OperatesOnUuid");

            Element couplingType = root.getChild("couplingType", srv);
            int coupledResourceIdx = root.indexOf(couplingType);

            for (WxSLayerRegistry layer : layersRegistry) {
                // Create coupled resources elements to register all layername
                // in service metadata. This information could be used to add
                // interactive map button when viewing service metadata.
                Element coupledResource = new Element("coupledResource", srv);
                Element scr = new Element("SV_CoupledResource", srv);

                // Create operation according to service type
                Element operation = new Element("operationName", srv);
                Element operationValue = new Element("CharacterString", gco);

                if (params.ogctype.startsWith("WMS"))
                    operationValue.setText("GetMap");
                else if (params.ogctype.startsWith("WFS"))
                    operationValue.setText("GetFeature");
                else if (params.ogctype.startsWith("WCS"))
                    operationValue.setText("GetCoverage");
                else if (params.ogctype.startsWith("SOS"))
                    operationValue.setText("GetObservation");
                operation.addContent(operationValue);

                // Create identifier (which is the metadata identifier)
                Element id = new Element("identifier", srv);
                Element idValue = new Element("CharacterString", gco);
                idValue.setText(layer.uuid);
                id.addContent(idValue);

                // Create scoped name element as defined in CSW 2.0.2 ISO profil
                // specification to link service metadata to a layer in a
                // service.
                Element scopedName = new Element("ScopedName", gco);
                scopedName.setText(layer.name);

                scr.addContent(operation);
                scr.addContent(id);
                scr.addContent(scopedName);
                coupledResource.addContent(scr);

                // Add coupled resource before coupling type element
                root.addContent(coupledResourceIdx, coupledResource);

                // Add operatesOn element at the end of identification section.
                Element op = new Element("operatesOn", srv);
                op.setAttribute("uuidref", layer.uuid);

                String hRefLink = dataMan.getSiteURL(context) + "/xml.metadata.get?uuid=" + layer.uuid;
                op.setAttribute("href", hRefLink, xlink);

                root.addContent(op);

            }
        }

        return md;
    }

    /**
     * Add metadata for a Layer/FeatureType/Coverage element of a
     * GetCapabilities document. This function search for a metadataUrl element
     * (with @type = TC211 and format = text/xml) and try to load the XML
     * document. If failed, then an XSLT is used for creating metadata from the
     * Layer/FeatureType/Coverage element. If loaded document contain an
     * existing uuid, metadata will not be loaded in the catalogue.
     * 
     * @param layer
     *            Layer/FeatureType/Coverage element
     * @param capa
     *            GetCapabilities document
     * 
     * @return uuid
     * 
     */
    private WxSLayerRegistry addLayerMetadata(Element layer, Element capa) throws JDOMException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date dt = new Date();
        WxSLayerRegistry reg = new WxSLayerRegistry();
        String schema;
        String mdXml;
        String date = df.format(dt);
        // --- Loading stylesheet
        String styleSheet = schemaMan.getSchemaDir(params.outputSchema) + Geonet.Path.CONVERT_STYLESHEETS
                + "/OGCWxSGetCapabilitiesto19119/" + "/OGC" + params.ogctype.substring(0, 3)
                + "GetCapabilitiesLayer-to-19139.xsl";
        Element xml = null;

        boolean exist, needsUpdate = false;
        boolean loaded = false;

        if (params.ogctype.substring(0, 3).equals("WMS")) {
            Element name;
            if (params.ogctype.substring(3, 8).equals("1.3.0")) {
                Namespace wms = Namespace.getNamespace("http://www.opengis.net/wms");
                name = layer.getChild("Name", wms);
            } else {
                name = layer.getChild("Name");
            }
            // --- For the moment, skip non-requestable category layers
            if (name == null || name.getValue().trim().equals("")) {
                log.info("  - skipping layer with no name element");
                return null;
            }
            reg.name = name.getValue();
        } else if (params.ogctype.substring(0, 3).equals("WFS")) {
            Namespace wfs = Namespace.getNamespace("http://www.opengis.net/wfs");
            reg.name = layer.getChild("Name", wfs).getValue();
        } else if (params.ogctype.substring(0, 3).equals("WCS")) {
            Namespace wcs = Namespace.getNamespace("http://www.opengis.net/wcs");
            reg.name = layer.getChild("name", wcs).getValue();
        } else if (params.ogctype.substring(0, 3).equals("SOS")) {
            Namespace gml = Namespace.getNamespace("http://www.opengis.net/gml");
            reg.name = layer.getChild("name", gml).getValue();
        }

        log.info("  - Loading layer: " + reg.name);

        // --- sha1 the full capabilities URL + the layer, coverage or feature
        // name
        reg.uuid = Sha1Encoder.encodeString(this.capabilitiesUrl + "#" + reg.name); // the dataset identifier

        // --- Trying loading metadataUrl element
        if (params.useLayerMd && !params.ogctype.substring(0, 3).equals("WMS")
                && !params.ogctype.substring(0, 3).equals("WFS")) {
            log.info("  - MetadataUrl harvester only supported for WMS or WFS layers.");
        }

        if (params.useLayerMd
                && (params.ogctype.substring(0, 3).equals("WMS") || params.ogctype.substring(0, 3).equals("WFS"))) {

            Namespace xlink = Namespace.getNamespace("http://www.w3.org/1999/xlink");

            // Get metadataUrl xlink:href
            // TODO : add support for WCS metadataUrl element.

            // Check if add namespace prefix to Xpath queries. If
            // layer.getNamespace() is:
            // * Namespace.NO_NAMESPACE, should not be added, otherwise
            // exception is launched
            // * Another namespace, should be added a namespace prefix to Xpath
            // queries, otherwise doesn't find any result
            String dummyNsPrefix = "";
            boolean addNsPrefix = !layer.getNamespace().equals(Namespace.NO_NAMESPACE);
            if (addNsPrefix)
                dummyNsPrefix = "x:";

            // WMS
            if (params.ogctype.substring(0, 3).equals("WMS")) {

                XPath mdUrl = XPath.newInstance("./" + dummyNsPrefix + "MetadataURL[@type='TC211' and " + dummyNsPrefix
                        + "Format='text/xml']/" + dummyNsPrefix + "OnlineResource");
                if (addNsPrefix)
                    mdUrl.addNamespace("x", layer.getNamespace().getURI());
                Element onLineSrc = (Element) mdUrl.selectSingleNode(layer);

                // Check if metadataUrl in WMS 1.3.0 format
                if (onLineSrc == null) {
                    mdUrl = XPath.newInstance("./" + dummyNsPrefix + "MetadataURL[@type='ISO19115:2003' and "
                            + dummyNsPrefix + "Format='text/xml']/" + dummyNsPrefix + "OnlineResource");
                    if (addNsPrefix)
                        mdUrl.addNamespace("x", layer.getNamespace().getURI());
                    onLineSrc = (Element) mdUrl.selectSingleNode(layer);
                }

                if (onLineSrc != null) {
                    org.jdom.Attribute href = onLineSrc.getAttribute("href", xlink);

                    if (href != null) { // No metadataUrl attribute for that layer
                        mdXml = href.getValue();
                        try {
                            xml = Xml.loadFile(new URL(mdXml));

                            // If url is CSW GetRecordById remove envelope
                            if (xml.getName().equals("GetRecordByIdResponse")) {
                                xml = (Element) xml.getChildren().get(0);
                            }

                            schema = dataMan.autodetectSchema(xml, null); // ie. iso19115 or 139 or DC
                            // Extract uuid from loaded xml document
                            reg.uuid = dataMan.extractUUID(schema, xml);
                            exist = dataMan.existsMetadataUuid(dbms, reg.uuid);

                            if (exist) {
                                log.warning("    Metadata uuid "+ reg.uuid +" already exist in the catalogue. Metadata will be updated.");
                                previouslyHarvested.remove(reg.uuid);
                                result.layerUuidExist++;
                                loaded = true;
                                needsUpdate = true;
                            }

                            if (schema == null) {
                                log.warning("    Failed to detect schema from metadataUrl file. Use GetCapabilities document instead for that layer.");
                                result.unknownSchema++;
                                loaded = false;
                            } else {
                                log.info("  - Load layer metadataUrl document ok: " + mdXml);

                                loaded = true;
                                result.layerUsingMdUrl++;
                            }
                            // TODO : catch other exception
                        } catch (Exception e) {
                            log.warning("  - Failed to load layer using metadataUrl attribute : " + e.getMessage());
                            loaded = false;
                        }
                    } else {
                        log.info("  - No metadataUrl attribute with format text/xml found for that layer");
                        loaded = false;
                    }
                } else {
                    log.info("  - No OnlineResource found for that layer");
                    loaded = false;
                }
            }
            // WFS: there are no metadataUrl in WFS 1.0.0, we need to be in 1.1.0 specifically.
            else if (params.ogctype.substring(0, 3).equals("WFS") && params.ogctype.substring(3, 8).equals("1.1.0")) {
                try {
                    xml = getWfsMdFromMetadataUrl(layer);
                    // applying the same logic as above with WMS
                    if (xml.getName().equals("GetRecordByIdResponse")) {
                        xml = (Element) xml.getChildren().get(0);
                    }
                    schema = dataMan.autodetectSchema(xml, null); // ie. iso19115 or 139 or DC
                    reg.uuid = dataMan.extractUUID(schema, xml);
                    exist = dataMan.existsMetadataUuid(dbms, reg.uuid);
                    if (exist) {
                        // TODO needs update ?
                        log.warning("    Metadata uuid "+reg.uuid+" already exist in the catalogue. Metadata will be updated.");
                        previouslyHarvested.remove(reg.uuid);
                        result.layerUuidExist++;
                        loaded = true;
                        needsUpdate = true;
                    }
                    if (schema == null) {
                        log.warning("    Failed to detect schema from metadataUrl file. Use GetCapabilities document instead for that layer.");
                        result.unknownSchema++;
                        loaded = false;
                    } else {
                        loaded = true;
                        result.layerUsingMdUrl++;
                    }
                } catch (Throwable e) {
                    log.error("  - Error trying to get the Metadata from the metadataUrl element (WFS): " + e.getMessage());
                    loaded = false;
                }
            }
        }
        // --- using GetCapabilities document to create a brand-new data Metadata
        if (!loaded) {
            try {
                // --- set XSL param to filter on layer and set uuid
                Map<String, String> param = new HashMap<String, String>();
                param.put("uuid", reg.uuid);
                param.put("Name", reg.name);
                param.put("lang", params.lang);
                param.put("topic", params.topic);
                xml = Xml.transform(capa, styleSheet, param);
                if (log.isDebugEnabled())
                    log.info("  - Layer loaded using GetCapabilities document.");
                
                // Check if the MD already exist
                exist = dataMan.existsMetadataUuid(dbms, reg.uuid);
                if (exist) {
                    log.info("Metadata UUID " + reg.uuid + " (automatically generated from GetCapability response) already in the catalogue." );
                    // TODO needs update ?
                    previouslyHarvested.remove(reg.uuid);
                    needsUpdate = true;
                }
            } catch (Exception e) {
                log.warning("  - Failed to do XSLT transformation on Layer element : " + e.getMessage());
            }
        }

        // Insert/Update in db
        try {


            String group = null, isTemplate = null, docType = null, title = null, category = null;
            boolean ufo = false, indexImmediate = false;

            schema = dataMan.autodetectSchema(xml);
            //
            // inserts the data metadata
            //
            if (! needsUpdate) {
                log.info("  - inserting new metadata uuid: " + reg.uuid);
                reg.id = dataMan.insertMetadata(context, dbms, schema, xml,
                        context.getSerialFactory().getSerial(dbms, "Metadata"), reg.uuid,
                        Integer.parseInt(params.ownerId), group, params.uuid, isTemplate, docType, title, category,
                        date, date, ufo, indexImmediate);
            }
            // updates it instead
            else {
                reg.id = dataMan.getMetadataId(dbms, reg.uuid);
                log.info("  - updating existing metadata uuid: " + reg.uuid);
                dataMan.updateMetadata(context, dbms, reg.id, xml, false, ufo, indexImmediate,
                        params.lang, null, false);
            }
            xml = dataMan.updateFixedInfo(schema, reg.id, params.uuid, xml, null, DataManager.UpdateDatestamp.no, dbms,
                    context);

            int iId = Integer.parseInt(reg.id);
            if (log.isDebugEnabled())
                log.debug("    - Layer loaded in DB.");

            if (log.isDebugEnabled())
                log.debug("    - Set Privileges and category.");
            addPrivileges(reg.id, params.getPrivileges(), localGroups, dataMan, context, dbms, log);

            if (params.datasetCategory != null && !params.datasetCategory.equals(""))
                dataMan.setCategory(context, dbms, reg.id, params.datasetCategory);

            if (log.isDebugEnabled())
                log.debug("    - Set Harvested.");
            dataMan.setHarvestedExt(dbms, iId, params.uuid, params.url); // FIXME: harvestUuid should be a MD5 string

            dbms.commit();

            dataMan.indexMetadata(dbms, reg.id);

            try {
                // Load bbox info for later use (eg. WMS thumbnails creation)
                Namespace gmd = Namespace.getNamespace("http://www.isotc211.org/2005/gmd");
                Namespace gco = Namespace.getNamespace("http://www.isotc211.org/2005/gco");

                ElementFilter bboxFinder = new ElementFilter("EX_GeographicBoundingBox", gmd);
                @SuppressWarnings("unchecked")
                Iterator<Element> bboxes = xml.getDescendants(bboxFinder);

                while (bboxes.hasNext()) {
                    Element box = bboxes.next();
                    // FIXME : Could be null. Default bbox if from root layer
                    reg.minx = Double.valueOf(box.getChild("westBoundLongitude", gmd).getChild("Decimal", gco)
                            .getText());
                    reg.miny = Double.valueOf(box.getChild("southBoundLatitude", gmd).getChild("Decimal", gco)
                            .getText());
                    reg.maxx = Double.valueOf(box.getChild("eastBoundLongitude", gmd).getChild("Decimal", gco)
                            .getText());
                    reg.maxy = Double.valueOf(box.getChild("northBoundLatitude", gmd).getChild("Decimal", gco)
                            .getText());

                }
            } catch (Exception e) {
                log.warning("  - Failed to extract layer bbox from metadata : " + e.getMessage());
            }

            result.layer++;
            log.info("  - metadata loaded with uuid: " + reg.uuid + "/internal id: " + reg.id);

        } catch (Exception e) {
            e.printStackTrace();
            log.warning("  - Failed to load layer metadata : " + e.getMessage());
            result.unretrievable++;
            return null;
        }

        return reg;
    }

    /**
     * Gets a remote data metadata when referenced in the MetadataURL WFS
     * GetCapability for the current featureType (element layer in parameter).
     * 
     * @param layer
     *            the featureType XML fragment
     * @return the remote data metadata as an XML element
     */
    private Element getWfsMdFromMetadataUrl(Element layer) throws Exception {
        String dummyNsPrefix = "";
        if (!layer.getNamespace().equals(Namespace.NO_NAMESPACE)) {
            dummyNsPrefix = "x:";
        }
        XPath mdUrl = XPath.newInstance(String.format("./%sMetadataURL[@type='TC211' and @format='text/xml']/text()",
                dummyNsPrefix));

        if (!layer.getNamespace().equals(Namespace.NO_NAMESPACE)) {
            mdUrl.addNamespace("x", layer.getNamespace().getURI());
        }

        Text onLineSrc = (Text) mdUrl.selectSingleNode(layer);

        if (onLineSrc == null) {
            throw new Exception("Online resource not found in the WFS XML fragment. Skipping.");
        } else {
            try {
                // TODO: need to pass the credentials in
                Element ret =  Xml.loadFile(new URL(onLineSrc.getText()));
                return ret;
            } catch (Exception e) {
                log.error("Unable to fetch " + onLineSrc.getText() + ": " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Call GeoNetwork service to load thumbnails and create small and big ones.
     * 
     * 
     * @param layer
     *            layer for which the thumbnail needs to be generated
     * 
     */
    private void loadThumbnail(WxSLayerRegistry layer) {
        if (log.isDebugEnabled())
            log.debug("  - Creating thumbnail for layer metadata: " + layer.name + " id: " + layer.id);
        Set s = new org.fao.geonet.services.thumbnail.Set();

        try {
            String filename = getMapThumbnail(layer);

            if (filename != null) {
                if (log.isDebugEnabled())
                    log.debug("  - File: " + filename);

                Element par = new Element("request");
                par.addContent(new Element("id").setText(layer.id));
                par.addContent(new Element("version").setText("10"));
                par.addContent(new Element("type").setText("large"));

                Element fname = new Element("fname").setText(filename);
                fname.setAttribute("content-type", "image/png");
                fname.setAttribute("type", "file");
                fname.setAttribute("size", "");

                par.addContent(fname);
                par.addContent(new Element("add").setText("Add"));
                par.addContent(new Element("createSmall").setText("on"));
                par.addContent(new Element("smallScalingFactor").setText("180"));
                par.addContent(new Element("smallScalingDir").setText("width"));

                // Call the services
                s.execOnHarvest(par, context, dbms, dataMan);
                dbms.commit();
                result.thumbnails++;
            } else
                result.thumbnailsFailed++;
        } catch (Exception e) {
            log.warning("  - Failed to set thumbnail for metadata: " + e.getMessage());
            e.printStackTrace();
            result.thumbnailsFailed++;
        }

    }

    /**
     * Remove thumbnails directory for all metadata FIXME : Do this only for
     * existing one !
     * 
     * @param id
     *            layer for which the thumbnail needs to be generated
     * 
     */
    private void unsetThumbnail(String id) {
        if (log.isDebugEnabled())
            log.debug("  - Removing thumbnail for layer metadata: " + id);

        try {
            String file = Lib.resource.getDir(context, Params.Access.PUBLIC, id);
            FileCopyMgr.removeDirectoryOrFile(new File(file));
        } catch (Exception e) {
            log.warning("  - Failed to remove thumbnail for metadata: " + id + ", error: " + e.getMessage());
        }
    }

    /**
     * Load thumbnails making a GetMap operation. Width is 300px. Ratio is
     * computed for height using LatLongBoundingBoxElement.
     * 
     * 
     * @param layer
     *            layer for which the thumbnail needs to be generated
     * 
     */
    private String getMapThumbnail(WxSLayerRegistry layer) {
        String filename = layer.uuid + ".png";
        String dir = context.getUploadDir();
        Double r = WIDTH / (layer.maxx - layer.minx) * (layer.maxy - layer.miny);

        // Usual GetMap url tested with mapserver and geoserver
        // http://localhost:8080/geoserver/wms?service=WMS&request=GetMap&VERSION=1.1.1&
        // LAYERS=gn:world&WIDTH=200&HEIGHT=200&FORMAT=image/png&BBOX=-180,-90,180,90&STYLES=
        String url = getBaseUrl(params.url) + "&SERVICE=" + params.ogctype.substring(0, 3) + "&VERSION="
                + params.ogctype.substring(3) + "&REQUEST=" + GETMAP + "&FORMAT=" + IMAGE_FORMAT + "&WIDTH=" + WIDTH
                + "&SRS=EPSG:4326" + "&HEIGHT=" + r.intValue() + "&LAYERS=" + layer.name + "&STYLES=" + "&BBOX="
                + layer.minx + "," + layer.miny + "," + layer.maxx + "," + layer.maxy;
        // All is in Lat/Long epsg:4326

        HttpClient httpclient = new HttpClient();
        GetMethod req = new GetMethod(url);

        if (log.isDebugEnabled())
            log.debug("Retrieving remote document: " + url);

        // set proxy from settings manager
        Lib.net.setupProxy(context, httpclient);

        try {
            // Connect
            int result = httpclient.executeMethod(req);
            if (log.isDebugEnabled())
                log.debug("   Get " + result);

            if (result == 200) {
                // Save image document to temp directory
                // TODO: Check OGC exception
                OutputStream fo = null;
                InputStream in = null;

                try {
                    fo = new FileOutputStream(dir + filename);
                    in = req.getResponseBodyAsStream();
                    BinaryFile.copy(in, fo);
                } finally {
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(fo);
                }
            } else {
                log.info(" Http error connecting");
                return null;
            }
        } catch (HttpException he) {
            log.info(" Http error connecting to '" + httpclient.toString() + "'");
            log.info(he.getMessage());
            return null;
        } catch (IOException ioe) {
            log.info(" Unable to connect to '" + httpclient.toString() + "'");
            log.info(ioe.getMessage());
            return null;
        } finally {
            // Release current connection to the connection pool once you are
            // done
            req.releaseConnection();
        }

        return filename;
    }

    /**
     * Add '?' or '&' if required to url so that parameters can just be appended
     * to it
     * 
     * @param url
     *            Url to which parameters are going to be appended
     * 
     */
    private String getBaseUrl(String url) {
        if (url.endsWith("?")) {
            return url;
        } else if (url.contains("?")) {
            return url + "&";
        } else {
            return url + "?";
        }
    }

    // ---------------------------------------------------------------------------
    // ---
    // --- Variables
    // ---
    // ---------------------------------------------------------------------------

    private Logger log;
    private ServiceContext context;
    private Dbms dbms;
    private OgcWxSParams params;
    private DataManager dataMan;
    private SchemaManager schemaMan;
    private CategoryMapper localCateg;
    private GroupMapper localGroups;
    private HarvestResult result;

    /**
     * Store the GetCapabilities operation URL. This URL is scrambled and used
     * to uniquely identified the service. The idea of generating a uuid based
     * on the URL instead of a randomuuid is to be able later to do an update of
     * the service metadata (which could have been updated in the catalogue)
     * instead of a delete/insert operation.
     */
    private String capabilitiesUrl;
    private static final int WIDTH = 900;
    private static final String GETCAPABILITIES = "GetCapabilities";
    private static final String GETMAP = "GetMap";
    private static final String IMAGE_FORMAT = "image/png";
    private List<WxSLayerRegistry> layersRegistry = new ArrayList<WxSLayerRegistry>();

    private static class WxSLayerRegistry {
        public String uuid;
        public String id;
        public String name;
        public Double minx = -180.0;
        public Double miny = -90.0;
        public Double maxx = 180.0;
        public Double maxy = 90.0;
    }

}