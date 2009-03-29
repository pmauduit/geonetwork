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

package org.fao.geonet.kernel.csw.services.getrecords;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import jeeves.resources.dbms.Dbms;
import jeeves.server.context.ServiceContext;
import jeeves.utils.Log;
import jeeves.utils.Util;
import jeeves.utils.Xml;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.csw.common.Csw;
import org.fao.geonet.csw.common.ResultType;
import org.fao.geonet.csw.common.TypeName;
import org.fao.geonet.csw.common.exceptions.CatalogException;
import org.fao.geonet.csw.common.exceptions.InvalidParameterValueEx;
import org.fao.geonet.csw.common.exceptions.NoApplicableCodeEx;
import org.fao.geonet.kernel.AccessManager;
import org.fao.geonet.kernel.search.LuceneSearcher;
import org.fao.geonet.kernel.search.LuceneUtils;
import org.fao.geonet.kernel.search.SearchManager;
import org.fao.geonet.kernel.search.spatial.Pair;
import org.jdom.Element;

//=============================================================================

public class CatalogSearcher
{
	private Element _summaryConfig;
	private Map<String, Boolean> _isTokenizedField = new HashMap<String, Boolean>();
	private Hits _hits;
	
    public CatalogSearcher(File summaryConfig)
    {
    	try {
			if (summaryConfig != null)
				_summaryConfig = Xml.loadStream(new FileInputStream(summaryConfig));
		} catch (Exception e) {
			throw new RuntimeException ("Error reading summary configuration file", e);
		}
    }
	
	//---------------------------------------------------------------------------
	//---
	//--- Main search method
	//---
	//---------------------------------------------------------------------------

	/**
	 * Convert a filter to a lucene search and run the search.
	 * 
	 * @return a list of id that match the given filter, ordered by sortFields
	 */
	public Pair<Element, List<ResultItem>> search(ServiceContext context, Element filterExpr,
			String filterVersion, Set<TypeName> typeNames, Sort sort,
			ResultType resultType, int maxRecords) throws CatalogException
	{
		Element luceneExpr = filterToLucene(context, filterExpr);

        try {
		
			if (luceneExpr != null)
			{
				checkForErrors(luceneExpr);
				remapFields(luceneExpr);
				convertPhrases(luceneExpr, context);
			}

            Pair<Element, List<ResultItem>> results = performSearch(context, luceneExpr,
                    filterExpr, filterVersion, sort, resultType, maxRecords);
            return results;
        } catch (Exception e) {
            Log.error(Geonet.CSW_SEARCH, "Error while searching metadata ");
            Log.error(Geonet.CSW_SEARCH, "  (C) StackTrace:\n" + Util.getStackTrace(e));

            throw new NoApplicableCodeEx(
                    "Raised exception while searching metadata : " + e);
        }
	}
	
	/**
     * <p>
     * Gets results in current searcher
     * </p>
     * 
     * @return current searcher result in "fast" mode
     * 
     * @throws IOException
     * @throws CorruptIndexException
     */
    public Element getAll() throws CorruptIndexException, IOException
    {
        Element response = new Element("response");

        if (_hits.length() == 0) {
            response.setAttribute("from", 0 + "");
            response.setAttribute("to", 0 + "");
            return response;
        }

        response.setAttribute("from", 1 + "");
        response.setAttribute("to", _hits.length() + "");
        for (int i = 0; i < _hits.length(); i++) {
            Document doc = _hits.doc(i);
            String id = doc.get("_id");

            // FAST mode
            Element md = LuceneSearcher.getMetadataFromIndex(doc, id);
            response.addContent(md);
        }

        return response;
    }

	//---------------------------------------------------------------------------
	//---
	//--- Private methods
	//---
	//---------------------------------------------------------------------------

	/**
	 * Use filter-to-lucene stylesheet to create a Lucene search query 
	 * to be used by LuceneSearcher.
	 * 
	 * @return XML representation of Lucene query 
	 */
	private Element filterToLucene(ServiceContext context, Element filterExpr) throws NoApplicableCodeEx
	{
		if (filterExpr == null)
			return null;

		String styleSheet = context.getAppPath() + Geonet.Path.CSW + Geonet.File.FILTER_TO_LUCENE;

		try
		{
			return Xml.transform(filterExpr, styleSheet);
		}
		catch (Exception e)
		{
			context.error("Error during Filter to Lucene conversion : "+ e);
			context.error("  (C) StackTrace\n"+ Util.getStackTrace(e));

			throw new NoApplicableCodeEx("Error during Filter to Lucene conversion : "+ e);
		}
	}

	//---------------------------------------------------------------------------

	private void checkForErrors(Element elem) throws InvalidParameterValueEx
	{
		List children = elem.getChildren();

		if (elem.getName().equals("error"))
		{
			String type = elem.getAttributeValue("type");
			String oper = Xml.getString((Element) children.get(0));

			throw new InvalidParameterValueEx(type, oper);
		}

		for(int i=0; i<children.size(); i++)
			checkForErrors((Element) children.get(i));
	}

	//---------------------------------------------------------------------------

	// Only tokenize field must be converted
	// TODO add token parameter in CSW conf for each field, may be useful for GetDomain operation too.
	private void convertPhrases(Element elem, ServiceContext context) throws CorruptIndexException, IOException
	{
		if (elem.getName().equals("TermQuery"))
		{
			String field = elem.getAttributeValue("fld");
			String text  = elem.getAttributeValue("txt");

			boolean isTokenized = isTokenized(field, context);
			if ( isTokenized && text.indexOf(" ") != -1) {
				elem.setName("PhraseQuery");

				StringTokenizer st = new StringTokenizer(text, " ");

				while (st.hasMoreTokens())
				{
					Element term = new Element("TermQuery");
					term.setAttribute("fld", field);
					term.setAttribute("txt", st.nextToken());

					elem.addContent(term);
				}
			}
		}

		else
		{
			List children = elem.getChildren();

			for(int i=0; i<children.size(); i++)
				convertPhrases((Element) children.get(i), context);
		}
	}

	//---------------------------------------------------------------------------
	private boolean isTokenized(String field, ServiceContext context) throws IOException
	{
	    Boolean tokenized = _isTokenizedField.get(field);
	    if (tokenized != null) {
	        return tokenized.booleanValue();
	    }
	    GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);
	    SearchManager sm = gc.getSearchmanager();
	    IndexReader reader = IndexReader.open(sm.getLuceneDir());

	    int i = 0;
	    while (i < reader.numDocs() && tokenized == null) {
	        Document doc = reader.document(i);
	        Field tmp = doc.getField(field);
	        if (tmp != null) {
	            tokenized = tmp.isTokenized();
	        }
	        i++;
	    }
	    
	    if (tokenized != null) {
	        _isTokenizedField.put(field, tokenized.booleanValue());
	        return tokenized.booleanValue();
	    }
	    _isTokenizedField.put(field, false);
	    return false;
	}
	
	
	/**
	 * Map OGC CSW search field names to Lucene field names
	 * using {@link FieldMapper}. If a field name is not defined
	 * then the any (ie. full text) criteria is used.
	 *  
	 */
	private void remapFields(Element elem)
	{
		String field = elem.getAttributeValue("fld");

		if (field != null)
		{
			if (field.equals(""))
				field = "any";

			String mapped = FieldMapper.map(field);

			if (mapped != null)
				elem.setAttribute("fld", mapped);
			else
				Log.info(Geonet.CSW_SEARCH, "Unknown queryable field : "+ field);  //FIXME log doesn't work
		}

		List children = elem.getChildren();

		for(int i=0; i<children.size(); i++)
			remapFields((Element) children.get(i));
	}

	//---------------------------------------------------------------------------

	private Pair <Element, List<ResultItem>> performSearch(ServiceContext context,
			Element luceneExpr, Element filterExpr, String filterVersion, Sort sort, ResultType resultType, int maxRecords)
			throws Exception
    {
		GeonetContext gc = (GeonetContext) context
				.getHandlerContext(Geonet.CONTEXT_NAME);
		SearchManager sm = gc.getSearchmanager();

		if (luceneExpr != null)
			Log.debug(Geonet.CSW_SEARCH, "Search criteria:\n"
					+ Xml.getString(luceneExpr));

		Query data = (luceneExpr == null) ? null : LuceneSearcher
				.makeQuery(luceneExpr);
		Query groups = getGroupsQuery(context);

		// --- put query on groups in AND with lucene query

		BooleanQuery query = new BooleanQuery();
		
		// FIXME : DO I need to fix that here ???
//		BooleanQuery.setMaxClauseCount(1024); // FIXME : using MAX_VALUE solve
//        // partly the org.apache.lucene.search.BooleanQuery$TooManyClauses
//        // problem.
//        // Improve index content.

		BooleanClause.Occur occur = LuceneUtils
				.convertRequiredAndProhibitedToOccur(true, false);
		if (data != null)
			query.add(data, occur);

		query.add(groups, occur);

		// --- proper search
		Log.debug(Geonet.CSW_SEARCH, "Lucene query: " + query.toString());
		
		IndexReader reader = IndexReader.open(sm.getLuceneDir());
		IndexSearcher searcher = new IndexSearcher(reader);
		
		try
		{
			// TODO Handle NPE creating spatial filter (due to constraint language version). 
			Filter spatialfilter = sm.getSpatial().filter(query,
					filterExpr, filterVersion);
			
            if (spatialfilter == null) {
            	_hits = searcher.search(query, sort);
            } else {
            	_hits = searcher.search(query, new CachingWrapperFilter(spatialfilter), sort);
            }

			Log.debug(Geonet.CSW_SEARCH, "Records matched : "+ _hits.length());
			
			//--- retrieve results

			List<ResultItem> results = new ArrayList<ResultItem>();

			for (int i = 0; i < _hits.length(); i++) {
				Document doc = _hits.doc(i);
				String id = doc.get("_id");

				ResultItem ri = new ResultItem(id);
				results.add(ri);

				for (String field : FieldMapper.getMappedFields()) {
					String value = doc.get(field);

					if (value != null)
						ri.add(field, value);
				}
			}

			Element summary = null;

			// Only compute GeoNetwork summary on results_with_summary option 
			if (resultType == ResultType.RESULTS_WITH_SUMMARY) {
					summary = LuceneSearcher.makeSummary(_hits, _hits.length(),
							_summaryConfig, resultType.toString(), 10);
					summary.setName("Summary");
					summary.setNamespace(Csw.NAMESPACE_GEONET);
			}
			
			return Pair.read(summary, results);
		}
		finally
		{
			searcher.close();
			reader  .close();
		}
    }
	
	//---------------------------------------------------------------------------

	/**
	 * Allow search on current user's groups only adding a 
	 * BooleanClause to the search.
	 */
	private Query getGroupsQuery(ServiceContext context) throws Exception
	{
		Dbms dbms = (Dbms) context.getResourceManager().open(Geonet.Res.MAIN_DB);

		GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);
		AccessManager am = gc.getAccessManager();
		Set<String>   hs = am.getUserGroups(dbms, context.getUserSession(), context.getIpAddress());

		BooleanQuery query = new BooleanQuery();

		String operView = "_op0";

		BooleanClause.Occur occur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);							
		for(Object group: hs)
		{
			TermQuery tq = new TermQuery(new Term(operView, group.toString()));
			query.add(tq, occur);
		}
		
		// If user is authenticated, add the current user to the query because 
        // if an editor unchecked all
        // visible options in privileges panel for all groups, then the metadata
        // records could not be found anymore, even by its editor.
        if (context.getUserSession().getUserId() != null) {
            TermQuery tq = new TermQuery(new Term("_owner", context.getUserSession().getUserId()));
            query.add(tq, occur);
        }

		return query;
	}
	
}

//=============================================================================

/**
 * Class containing result items with information
 * retrieved from Lucene index.
 */
class ResultItem
{
	/**
	 * Metadata identifier
	 */
	private String id;

	/**
	 * Other Lucene index information declared in {@link FieldMapper}
	 */
	private HashMap<String, String> hmFields = new HashMap<String, String>();

	//---------------------------------------------------------------------------
	//---
	//--- Constructor
	//---
	//---------------------------------------------------------------------------

	public ResultItem(String id)
	{
		this.id = id;
	}

	//---------------------------------------------------------------------------
	//---
	//--- API methods
	//---
	//---------------------------------------------------------------------------

	public String getID() { return id; }

	//---------------------------------------------------------------------------

	public void add(String field, String value)
	{
		hmFields.put(field, value);
	}

	//---------------------------------------------------------------------------

	public String getValue(String field) { return hmFields.get(field); }
}

//=============================================================================

/**
 * Used to sort search results
 * 
 * comment francois : could we use {@link Sort} instead ?
 */
class ItemComparator implements Comparator<ResultItem>
{
	private List<SortField> sortFields;

	//---------------------------------------------------------------------------
	//---
	//--- Constructor
	//---
	//---------------------------------------------------------------------------

	public ItemComparator(List<SortField> sf)
	{
		sortFields = sf;
	}

	//---------------------------------------------------------------------------
	//---
	//--- Comparator interface
	//---
	//---------------------------------------------------------------------------

	public int compare(ResultItem ri1, ResultItem ri2)
	{
		for(SortField sf : sortFields)
		{
			String value1 = ri1.getValue(sf.field);
			String value2 = ri2.getValue(sf.field);

			//--- some metadata may have null values for some fields
			//--- in this case we push null values at the bottom

			if (value1 == null && value2 != null)
				return 1;

			if (value1 != null && value2 == null)
				return -1;

			if (value1 == null || value2 == null)
				return 0;

			//--- values are ok, do a proper comparison

			int comp = value1.compareTo(value2);

			if (comp == 0)
				continue;

			return (!sf.descend) ? comp : -comp;
		}

		return 0;
	}
}

//=============================================================================
