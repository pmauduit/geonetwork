package org.fao.geonet.kernel.search;

import jeeves.utils.Xml;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.fao.geonet.util.spring.StringUtils;
import org.jdom.Element;

import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 * Builds a Lucene query from a JDOM element representing a search request.
 *
 * @author heikki doeleman
 *
 */
public class LuceneQueryBuilder {

	/**
	 * Creates a query for a string.
	 */
	private Query textFieldToken(String string, String luceneIndexField, String similarity) {
		// similarity is not set or is 1
		if(similarity == null || similarity.equals("1")) {
			TermQuery query = null;
			if(string != null) {
				query = new TermQuery(new Term(luceneIndexField, string.toLowerCase()));
			}
			return query;
		}
		// similarity is not null and not 1
		else {
			FuzzyQuery query = null;
			if(string != null) {
				Float minimumSimilarity = Float.parseFloat(similarity);
				query = new FuzzyQuery(new Term(luceneIndexField, string.toLowerCase()), minimumSimilarity);
			}
			return query;
		}
	}

	/**
	 * Creates a query for all tokens in the search param. The query must select only results
	 * where none of the tokens in the search param is present.
	 */
	private BooleanClause prohibitedTextField(String searchParam, String luceneIndexField, String similarity) {
		BooleanClause booleanClause  = null;
		BooleanClause.Occur occur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
		BooleanClause.Occur dontOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, true);
		if(searchParam != null) {
			searchParam = searchParam.trim();
			if(searchParam.length() > 0) {
				BooleanQuery booleanQuery = new BooleanQuery();
				MatchAllDocsQuery matchAllDocsQuery = new MatchAllDocsQuery();
				BooleanClause matchAllDocsClause = new BooleanClause(matchAllDocsQuery, occur);
				booleanQuery.add(matchAllDocsClause);
				// tokenize searchParam
			    StringTokenizer st = new StringTokenizer(searchParam);
			    while (st.hasMoreTokens()) {
			        String token = st.nextToken();
			        // ignore fuzziness in without-queries
			        Query subQuery = textFieldToken(token, luceneIndexField, null);
					BooleanClause subClause = new BooleanClause(subQuery, dontOccur);
					booleanQuery.add(subClause);
			    }
			    booleanClause = new BooleanClause(booleanQuery, occur);
			}
		}
		return booleanClause;
	}

	/**
	 * Creates a query for all tokens in the search param. 'Not required' does not mean that this is
	 * not a required search parameter; rather it means that if this parameter is present, the query
	 * must select results where at least one of the tokens in the search param is present.
	 */
	private BooleanClause notRequiredTextField(String searchParam, String luceneIndexField, String similarity) {
		BooleanClause booleanClause  = null;
		BooleanClause.Occur occur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
		BooleanClause.Occur tokenOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);
		if(searchParam != null) {
			searchParam = searchParam.trim();
			if(searchParam.length() > 0) {
				// tokenize searchParam
			    StringTokenizer st = new StringTokenizer(searchParam);
				BooleanQuery booleanQuery = new BooleanQuery();
			    while (st.hasMoreTokens()) {
			        String token = st.nextToken();
			        Query subQuery = textFieldToken(token, luceneIndexField, similarity);
					BooleanClause subClause = new BooleanClause(subQuery, tokenOccur);
					booleanQuery.add(subClause);
			    }
			    booleanClause = new BooleanClause(booleanQuery, occur);
			}
		}
		return booleanClause;
	}

	/**
	 * Creates a query for all tokens in the search param. 'Required' does not mean that this is
	 * a required search parameter; rather it means that if this parameter is present, the query
	 * must select only results where each of the tokens in the search param is present.
	 */
	private BooleanClause requiredTextField(String searchParam, String luceneIndexField, String similarity) {
		BooleanClause booleanClause  = null;
		BooleanClause.Occur occur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
		if(searchParam != null) {
			searchParam = searchParam.trim();
			if(searchParam.length() > 0) {
				// tokenize searchParam
			    StringTokenizer st = new StringTokenizer(searchParam);
			    if(st.countTokens() == 1) {
			        String token = st.nextToken();
			        Query subQuery = textFieldToken(token, luceneIndexField, similarity);
				    booleanClause = new BooleanClause(subQuery, occur);
			    }
			    else {
					BooleanQuery booleanQuery = new BooleanQuery();
				    while (st.hasMoreTokens()) {
				        String token = st.nextToken();
				        Query subQuery = textFieldToken(token, luceneIndexField, similarity);
						BooleanClause subClause = new BooleanClause(subQuery, occur);
						booleanQuery.add(subClause);
				    }
				    booleanClause = new BooleanClause(booleanQuery, occur);
			    }
			}
		}
		return booleanClause;
	}

	public Query build(Element request) {

		System.out.println("\n\nLuceneQueryBuilder: request is\n" + Xml.getString(request) + "\n\n");


		// top query to hold all sub-queries for each search parameter
		BooleanQuery query = new BooleanQuery();

		//
		// hits per page
		//
		// nothing happens with it ?
		// String hitsPerPage = request.getChildText("hitsPerPage");

		//
		// attrset
		//
		// nothing happens with it ?
		//String attrset = request.getChildText("attrset");

		//
		// similarity
		//
		// this is passed to textfield-query-creating methods
		String similarity = request.getChildText("similarity");

		//
		// uuid
		//
		String uuidParam = request.getChildText("uuid");
		if(uuidParam != null) {
			uuidParam = uuidParam.trim();
			if(uuidParam.length() > 0) {
				// the uuid param is an 'or' separated list. Remove the 'or's and handle like an 'or' query:
				uuidParam = uuidParam.replaceAll("\\sor\\s", " ");
				BooleanClause uuidQuery = notRequiredTextField(uuidParam, LuceneIndexField.UUID, similarity);
				if(uuidQuery != null) {
					query.add(uuidQuery);
				}
			}
		}

		//
		// any
		//
		BooleanClause anyClause  = null;
		BooleanClause.Occur occur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
		String any = request.getChildText("any");
		if(any != null) {
			any = any.trim();
			if(any.length() > 0) {
				// tokenize searchParam
			    StringTokenizer st = new StringTokenizer(any);
			    if(st.countTokens() == 1) {
			        String token = st.nextToken();
			        Query subQuery = textFieldToken(token, LuceneIndexField.ANY, similarity);
			        if(subQuery != null) {
			        	anyClause = new BooleanClause(subQuery, occur);
			        }
			    }
			    else {
					BooleanQuery booleanQuery = new BooleanQuery();
				    while (st.hasMoreTokens()) {
				        String token = st.nextToken();
				        Query subQuery = textFieldToken(token, LuceneIndexField.ANY, similarity);
						BooleanClause subClause = new BooleanClause(subQuery, occur);
						if(subClause != null){
							booleanQuery.add(subClause);
						}
				    }
				    anyClause = new BooleanClause(booleanQuery, occur);
			    }
			}
		}
		if(anyClause != null) {
			query.add(anyClause);
		}

		//
		// all -- mapped to same Lucene field as 'any'
		//
		BooleanClause allQuery = requiredTextField(request.getChildText("all"), LuceneIndexField.ANY, similarity);
		if(allQuery != null) {
			query.add(allQuery);
		}

		//
		// or
		//
		BooleanClause orQuery = notRequiredTextField(request.getChildText("or"), LuceneIndexField.ANY, similarity);
		if(orQuery != null) {
			query.add(orQuery);
		}

		//
		// without
		//
		BooleanClause withoutQuery = prohibitedTextField(request.getChildText("without"), LuceneIndexField.ANY, similarity);
		if(withoutQuery != null) {
			query.add(withoutQuery);
		}

		//
		// phrase
		//
		String phrase = request.getChildText("phrase");
		if(phrase != null) {
			phrase = phrase.trim();
			if(phrase.length() > 0) {
				PhraseQuery phraseQuery = new PhraseQuery();
				BooleanClause.Occur phraseOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				// tokenize phrase
			    StringTokenizer st = new StringTokenizer(phrase);
			    while (st.hasMoreTokens()) {
			        String phraseElement = st.nextToken();
			        phraseElement = phraseElement.trim().toLowerCase();
			        phraseQuery.add(new Term(LuceneIndexField.ANY, phraseElement));
			    }
				query.add(phraseQuery, phraseOccur);
			}
		}

		//
		// ISO topic category
		//
		@SuppressWarnings("unchecked")
		List<Element> isoTopicCategories = (List<Element>)request.getChildren("topic-category");
		if(isoTopicCategories != null && isoTopicCategories.size() > 0) {
			BooleanQuery isoTopicCategoriesQuery = new BooleanQuery();
			BooleanClause.Occur topicCategoryOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);
			for(Iterator<Element> i = isoTopicCategories.iterator();i.hasNext();){
				String isoTopicCategory =  i.next().getText();
				isoTopicCategory = isoTopicCategory.trim();
				if(isoTopicCategory.length() > 0) {
					// some clients (like GN's GUI) stupidly append a * already. Prevent double stars here:
					if(isoTopicCategory.endsWith("*")) {
						isoTopicCategory = isoTopicCategory.substring(0, isoTopicCategory.length()-1);
					}
					PrefixQuery topicCategoryQuery = new PrefixQuery(new Term(LuceneIndexField.TOPIC_CATEGORY, isoTopicCategory));
					BooleanClause topicCategoryClause = new BooleanClause(topicCategoryQuery, topicCategoryOccur);
					isoTopicCategoriesQuery.add(topicCategoryClause);
				}
			}
			BooleanClause.Occur isoTopicCategoriesOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
			BooleanClause isoTopicCategoriesClause = new BooleanClause(isoTopicCategoriesQuery, isoTopicCategoriesOccur);
			query.add(isoTopicCategoriesClause);
		}

		//
		// download
		//
        String download = request.getChildText("download");
        if (StringUtils.hasText(download) && download.equals("on")) {
            BooleanQuery downloadQuery = new BooleanQuery();

            WildcardQuery downloadQueryProtocol = new WildcardQuery(new Term(LuceneIndexField.PROTOCOL, "WWW:DOWNLOAD-*--download"));
            BooleanClause.Occur  downloadOccurProtocol = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);
            downloadQuery.add(downloadQueryProtocol, downloadOccurProtocol);

            BooleanClause.Occur  downloadOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
            BooleanClause downloadClause = new BooleanClause(downloadQuery, downloadOccur);
            query.add(downloadClause);
        }


		//
		// dynamic
		//
        String dynamic = request.getChildText("dynamic");
        if (StringUtils.hasText(dynamic) && dynamic.equals("on")) {
            BooleanQuery dynamicQuery = new BooleanQuery();

            BooleanClause.Occur  dynamicProtocolOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);

            WildcardQuery dynamicQueryGetMap = new WildcardQuery(new Term(LuceneIndexField.PROTOCOL, "OGC:WMS-*-get-map"));
            dynamicQuery.add(dynamicQueryGetMap, dynamicProtocolOccur);

            WildcardQuery dynamicQueryGetCapabilities = new WildcardQuery(new Term(LuceneIndexField.PROTOCOL, "OGC:WMS-*-get-capabilities"));
            dynamicQuery.add(dynamicQueryGetCapabilities, dynamicProtocolOccur);

            WildcardQuery dynamicQueryEsriAims = new WildcardQuery(new Term(LuceneIndexField.PROTOCOL, "ESRI:AIMS-*-get-image"));
            dynamicQuery.add(dynamicQueryEsriAims, dynamicProtocolOccur);

            BooleanClause.Occur  dynamicOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
            BooleanClause downloadClause = new BooleanClause(dynamicQuery, dynamicOccur);
            query.add(downloadClause);

        }

		//
		// protocol
		//
		BooleanClause protocolClause = requiredTextField(request.getChildText("protocol"), LuceneIndexField.PROTOCOL, similarity);
		if(protocolClause != null) {
			query.add(protocolClause);
		}

		//
		// featured
		//
		String featured = request.getChildText("featured");
		if(featured != null && featured.equals("true")) {
			BooleanClause.Occur featuredOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
			TermQuery featuredQuery = new TermQuery(new Term(LuceneIndexField._OP6, "1"));
			BooleanClause featuredClause = new BooleanClause(featuredQuery, featuredOccur);
			query.add(featuredClause);
			TermQuery viewQuery = new TermQuery(new Term(LuceneIndexField._OP0, "1"));
			BooleanClause viewClause = new BooleanClause(viewQuery, featuredOccur);
			query.add(viewClause);
		}
		else {
			BooleanQuery groupsQuery = new BooleanQuery();
			boolean groupsQueryEmpty = true;
			@SuppressWarnings("unchecked")
			List<Element> groups = (List<Element>)request.getChildren("group");
			BooleanClause.Occur groupOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);
			if(groups != null && groups.size() > 0) {
				for(Iterator<Element> i = groups.iterator(); i.hasNext();) {
					String group = i.next().getText();
					group = group.trim();
					if(group.length() > 0) {
						TermQuery groupQuery = new TermQuery(new Term(LuceneIndexField._OP0, group));
						BooleanClause groupClause = new BooleanClause(groupQuery, groupOccur);
						groupsQueryEmpty = false;
						groupsQuery.add(groupClause);
					}
				}
			}
			String reviewer = request.getChildText("isReviewer");
			if(reviewer != null) {
				if(groups != null && groups.size() > 0) {
					for(Iterator<Element> i = groups.iterator(); i.hasNext();) {
						String group = i.next().getText();
						group = group.trim();
						if(group.length() > 0) {
							TermQuery groupQuery = new TermQuery(new Term(LuceneIndexField.GROUP_OWNER, group));
							BooleanClause groupClause = new BooleanClause(groupQuery, groupOccur);
							groupsQueryEmpty = false;
							groupsQuery.add(groupClause);
						}
					}
				}
			}
			String userAdmin = request.getChildText("isUserAdmin");
			if(userAdmin != null) {
				if(groups != null && groups.size() > 0) {
					for(Iterator<Element> i = groups.iterator(); i.hasNext();) {
						String group = i.next().getText();
						group = group.trim();
						if(group.length() > 0) {
							TermQuery groupQuery = new TermQuery(new Term(LuceneIndexField.GROUP_OWNER, group));
							BooleanClause groupClause = new BooleanClause(groupQuery, groupOccur);
							groupsQueryEmpty = false;
							groupsQuery.add(groupClause);
						}
					}
				}
			}
			String owner = request.getChildText("owner");
			if(owner != null) {
				TermQuery ownerQuery = new TermQuery(new Term(LuceneIndexField.OWNER, owner));
				BooleanClause ownerClause = new BooleanClause(ownerQuery, groupOccur);
				groupsQueryEmpty = false;
				groupsQuery.add(ownerClause);
			}
			String admin = request.getChildText("isAdmin");
			if(admin != null) {
				TermQuery adminQuery = new TermQuery(new Term(LuceneIndexField.DUMMY, "0"));
				BooleanClause adminClause = new BooleanClause(adminQuery, groupOccur);
				groupsQueryEmpty = false;
				groupsQuery.add(adminClause);
			}
			if(groupsQueryEmpty == false) {
				BooleanClause.Occur groupsOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				BooleanClause groupsClause = new BooleanClause(groupsQuery, groupsOccur);
				query.add(groupsClause);
			}

			@SuppressWarnings("unchecked")
			List<Element> groupOwners = (List<Element>)request.getChildren("groupOwner");
			if(groupOwners != null && groupOwners.size() > 0) {
				BooleanClause.Occur groupOwnerOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				for(Iterator<Element> i = groupOwners.iterator();i.hasNext();) {
					String groupOwner = i.next().getText();
					groupOwner = groupOwner.trim();
					if(groupOwner.length() > 0) {
						TermQuery groupOwnerQuery = new TermQuery(new Term(LuceneIndexField.GROUP_OWNER, groupOwner));
						BooleanClause groupOwnerClause = new BooleanClause(groupOwnerQuery, groupOwnerOccur);
						query.add(groupOwnerClause);
					}
				}
			}
		}

		//
		// category
		//
		@SuppressWarnings("unchecked")
		List<Element> categories = (List<Element>)request.getChildren("category");
		if(categories != null && categories.size() > 0) {
			BooleanQuery categoriesQuery = new BooleanQuery();
			BooleanClause.Occur categoriesOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
			BooleanClause categoriesClause = null;
			for(Iterator<Element> i = categories.iterator(); i.hasNext();) {
				String category = i.next().getText();
				if(category != null){
					category = category.trim();
					if(category.length() > 0) {
						BooleanClause categoryClause = notRequiredTextField(category, LuceneIndexField.CAT, similarity);
						if(categoryClause != null) {
							if(categoriesClause == null) {
								categoriesClause = new BooleanClause(categoriesQuery, categoriesOccur);
							}
							categoriesQuery.add(categoryClause);
						}
					}
				}
			}
			if(categoriesClause != null) {
				query.add(categoriesClause);
			}
		}

		//
		// template
		//
		String isTemplate = request.getChildText("template");
		BooleanClause.Occur templateOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
		TermQuery templateQuery = null;
		if(isTemplate != null && isTemplate.equals("y")) {
			templateQuery = new TermQuery(new Term(LuceneIndexField.IS_TEMPLATE, "y"));
		}
		else if(isTemplate != null && isTemplate.equals("s")) {
			templateQuery = new TermQuery(new Term(LuceneIndexField.IS_TEMPLATE, "s"));
		}
		else {
			templateQuery = new TermQuery(new Term(LuceneIndexField.IS_TEMPLATE, "n"));
		}
		query.add(templateQuery, templateOccur);

		//
		// date range
		//
		String dateTo = request.getChildText("dateTo");
		String dateFrom = request.getChildText("dateFrom");
		if((dateTo != null && dateTo.length() > 0) || (dateFrom != null && dateFrom.length() > 0)) {
			Term lowerTerm = null;
			Term upperTerm = null;
			RangeQuery rangeQuery = null;
			if(dateFrom != null) {
				lowerTerm = new Term(LuceneIndexField.CHANGE_DATE, dateFrom);
			}
			if(dateTo != null) {
				// while the 'from' parameter can be short (like yyyy-mm-dd)
				// the 'until' parameter must be long to match
				if(dateTo.length() == 10) {
					dateTo = dateTo + "T23:59:59";
				}
				upperTerm = new Term(LuceneIndexField.CHANGE_DATE, dateTo);
			}
			rangeQuery = new RangeQuery(lowerTerm, upperTerm, true);
			BooleanClause.Occur dateOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
			BooleanClause dateRangeClause = new BooleanClause(rangeQuery, dateOccur);
			query.add(dateRangeClause);
		}

		// metadataStandardName
		//
		BooleanClause metadataStandardNameClause = requiredTextField(request.getChildText("metadataStandardName"), LuceneIndexField.METADATA_STANDARD_NAME, similarity);
		if(metadataStandardNameClause != null) {
			query.add(metadataStandardNameClause);
		}

		//
		// type
		//
		BooleanClause typeClause = requiredTextField(request.getChildText("type"), LuceneIndexField.TYPE, similarity);
		if(typeClause != null) {
			query.add(typeClause);
		}

		//
		// siteId / source
		//
		BooleanClause sourceQuery = requiredTextField(request.getChildText("siteId"), LuceneIndexField.SOURCE, similarity);
		if(sourceQuery != null) {
			query.add(sourceQuery);
		}

        //
        // themekey
        //
        @SuppressWarnings("unchecked")
        List<Element> themeKeys = (List<Element>)request.getChildren("themekey");
        if(themeKeys != null && themeKeys.size() > 0) {
            for(Iterator<Element> i = themeKeys.iterator(); i.hasNext();) {
                BooleanQuery allkeywordsQuery = new BooleanQuery();
                BooleanClause.Occur allKeywordsOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);

                String themeKey = i.next().getText();
                if (StringUtils.hasText(themeKey)) {
                    BooleanClause.Occur keywordOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);
                    // TODO: Check separator
                    String[] tokens = StringUtils.delimitedListToStringArray(themeKey," or ");
                    for(int j = 0; j < tokens.length; j++) {
                        String token = tokens[j];
                        token = token.trim();
                        if(token.startsWith("\"")) {
                            token = token.substring(1);
                        }
                        if(token.endsWith("\"")) {
                            token = token.substring(0, token.length() - 1);
                        }
                        //
                        TermQuery keywordQuery = new TermQuery(new Term(LuceneIndexField.KEYWORD, token));
                        BooleanClause keywordClause = new BooleanClause(keywordQuery, keywordOccur);
                        allkeywordsQuery.add(keywordClause);
                    }
                }

                if (allkeywordsQuery.clauses().size() > 0) {
                    query.add(allkeywordsQuery, allKeywordsOccur);
                }
            }
        }

		//
		// digital and paper maps
		//
		String digital = request.getChildText("digital");
        String paper = request.getChildText("paper");

        // if both are off or both are on then no clauses are added
        if (StringUtils.hasText(digital) && digital.equals("on") && (!StringUtils.hasText(paper) || paper.equals("off"))) {
            TermQuery digitalQuery = new TermQuery(new Term(LuceneIndexField.DIGITAL, "true"));
            BooleanClause.Occur digitalOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
            BooleanClause digitalClause = new BooleanClause(digitalQuery, digitalOccur);
            query.add(digitalClause);
        }

        if (StringUtils.hasText(paper) && paper.equals("on") && (!StringUtils.hasText(digital)|| digital.equals("off"))) {
            TermQuery paperQuery = new TermQuery(new Term(LuceneIndexField.PAPER, "true"));
            BooleanClause.Occur paperOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
            BooleanClause paperClause = new BooleanClause(paperQuery,paperOccur);
            query.add(paperClause);
        }

		//
		// title
		//
		BooleanClause titleQuery = requiredTextField(request.getChildText("title"), LuceneIndexField.TITLE, similarity);
		if(titleQuery != null) {
			query.add(titleQuery);
		}

		//
		// abstract
		//
		BooleanClause abstractQuery = requiredTextField(request.getChildText("abstract"), LuceneIndexField.ABSTRACT, similarity);
		if(abstractQuery != null) {
			query.add(abstractQuery);
		}

		//
		// bounding box
		//
		// TODO handle regions if set
		// Note that this has been removed from the NGR search options
		Element region = request.getChild("region");
		Element regionData = request.getChild("regions");


		String eastBL = request.getChildText("eastBL");
		String westBL = request.getChildText("westBL");
		String northBL = request.getChildText("northBL");
		String southBL = request.getChildText("southBL");
		String relation = request.getChildText("relation");

		addBoundingBox(query, relation, eastBL, westBL, northBL, southBL);

		//System.out.println("#### query: " + query);
		System.out.println("\n\nLuceneQueryBuilder: query is\n" + query + "\n\n");

		return query;
	}

	private void addBoundingBox(BooleanQuery query, String relation, String eastBL, String westBL, String northBL, String southBL) {

		// ignore negative values
		if(eastBL != null) {
			double eastBLi = Double.parseDouble(eastBL);
			eastBL = new Double(360 + eastBLi).toString();
		}
		if(westBL != null){
            double westBLi = Double.parseDouble(westBL);
			westBL = new Double(360 + westBLi).toString();
		}
		if(northBL != null) {
            double northBLi = Double.parseDouble(northBL);
			northBL = new Double(360 + northBLi).toString();
		}
		if(southBL != null){
            double southBLi = Double.parseDouble(southBL);
			southBL = new Double(360 + southBLi).toString();
		}


		//
		// equal: coordinates of the target rectangle within 1 degree from corresponding ones of metadata rectangle
		//
		if(relation != null && relation.equals("equal")) {
			// eastBL
			if(eastBL != null) {
					String lowerTxt = Double.toString(Double.parseDouble(eastBL) - 1);
					String upperTxt = Double.toString(Double.parseDouble(eastBL) + 1);
					Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.EAST, lowerTxt.toLowerCase()));
					Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.EAST, upperTxt.toLowerCase()));
					boolean inclusive = true ;
					RangeQuery eastBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
					BooleanClause.Occur eastBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
					query.add(eastBLQuery, eastBLOccur);
				}
			// westBL
			if(westBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(westBL) - 1);
				String upperTxt = Double.toString(Double.parseDouble(westBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.WEST, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.WEST, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery westBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur westBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(westBLQuery, westBLOccur);
			}
			// northBL
			if(northBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(northBL) - 1);
				String upperTxt = Double.toString(Double.parseDouble(northBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.NORTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.NORTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery northBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur northBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(northBLQuery, northBLOccur);
			}
			// southBL
			if(southBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(southBL) - 1);
				String upperTxt = Double.toString(Double.parseDouble(southBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.SOUTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.SOUTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery southBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur southBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(southBLQuery, southBLOccur);
			}
		}
		//
		// encloses: metadata rectangle encloses target rectangle shrunk by 1 degree
		//
		else if(relation != null && relation.equals("encloses")) {
			// eastBL
			if(eastBL != null) {
					String lowerTxt = Double.toString(Double.parseDouble(eastBL) - 1);
					// 180 + 360
					String upperTxt = "540";
					Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.EAST, lowerTxt.toLowerCase()));
					Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.EAST, upperTxt.toLowerCase()));
					boolean inclusive = true ;
					RangeQuery eastBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
					BooleanClause.Occur eastBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
					query.add(eastBLQuery, eastBLOccur);
				}
			// westBL
			if(westBL != null) {
				// -180 + 360
				String lowerTxt = "180";
				String upperTxt = Double.toString(Double.parseDouble(westBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.WEST, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.WEST, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery westBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur westBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(westBLQuery, westBLOccur);
			}
			// northBL
			if(northBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(northBL) - 1);
				// 90 + 360
				String upperTxt = "450";
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.NORTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.NORTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery northBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur northBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(northBLQuery, northBLOccur);
			}
			// southBL
			if(southBL != null) {
				// -90 + 360
				String lowerTxt = "270";
				String upperTxt = Double.toString(Double.parseDouble(southBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.SOUTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.SOUTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery southBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur southBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(southBLQuery, southBLOccur);
			}
		}
		//
		// fullyEnclosedWithin: metadata rectangle fully enclosed within target rectangle augmented by 1 degree
		//
		else if(relation != null && relation.equals("fullyEnclosedWithin")) {
			// eastBL
			if(eastBL != null) {
					String lowerTxt = Double.toString(Double.parseDouble(westBL) - 1);
					String upperTxt = Double.toString(Double.parseDouble(eastBL) + 1);
					Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.EAST, lowerTxt.toLowerCase()));
					Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.EAST, upperTxt.toLowerCase()));
					boolean inclusive = true ;
					RangeQuery eastBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
					BooleanClause.Occur eastBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
					query.add(eastBLQuery, eastBLOccur);
				}
			// westBL
			if(westBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(westBL) - 1);
				String upperTxt = Double.toString(Double.parseDouble(eastBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.WEST, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.WEST, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery westBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur westBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(westBLQuery, westBLOccur);
			}
			// northBL
			if(northBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(southBL) - 1);
				String upperTxt = Double.toString(Double.parseDouble(northBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.NORTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.NORTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery northBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur northBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(northBLQuery, northBLOccur);
			}
			// southBL
			if(southBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(southBL) - 1);
				String upperTxt = Double.toString(Double.parseDouble(northBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.SOUTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.SOUTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery southBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur southBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(southBLQuery, southBLOccur);
			}
		}
		//
		// fullyOutsideOf: one or more of the 4 forbidden halfplanes contains the metadata
		// rectangle, that is, not true that all the 4 forbidden halfplanes do not contain
		// the metadata rectangle
		//
		else if(relation != null && relation.equals("fullyOutsideOf")) {
			// eastBL
			if(westBL != null) {
					// -180 + 360
					String lowerTxt = "180";
					String upperTxt = Double.toString(Double.parseDouble(westBL) + 1);

					Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.EAST, lowerTxt.toLowerCase()));
					Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.EAST, upperTxt.toLowerCase()));
					boolean inclusive = true ;
					RangeQuery eastBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
					BooleanClause.Occur eastBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(false, false);
					query.add(eastBLQuery, eastBLOccur);
				}
			// westBL
			if(eastBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(eastBL) - 1);
				// 180 + 360
				String upperTxt = "540";
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.WEST, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.WEST, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery westBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur westBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(westBLQuery, westBLOccur);
			}
			// northBL
			if(southBL != null) {
				// -90 + 360
				String lowerTxt = "270";
				String upperTxt = Double.toString(Double.parseDouble(southBL) + 1);
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.NORTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.NORTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery northBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur northBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(northBLQuery, northBLOccur);
			}
			// southBL
			if(northBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(northBL) - 1);
				// 90 + 360
				String upperTxt = "540";
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.SOUTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.SOUTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery southBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur southBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(southBLQuery, southBLOccur);
			}
		}
		//
		// overlaps : uses the equivalence
		// -(a + b + c + d) = -a * -b * -c * -d
		//
		else if(relation != null && relation.equals("overlaps")) {
			// eastBL
			if(westBL != null) {
					String lowerTxt = Double.toString(Double.parseDouble(westBL) + 1);
					// 180 + 360
					String upperTxt = "540";
					Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.EAST, lowerTxt.toLowerCase()));
					Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.EAST, upperTxt.toLowerCase()));
					boolean inclusive = true ;
					RangeQuery eastBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
					BooleanClause.Occur eastBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
					query.add(eastBLQuery, eastBLOccur);
				}
			// westBL
			if(eastBL != null) {
				String upperTxt = Double.toString(Double.parseDouble(eastBL) - 1);
				// -180 + 360
				String lowerTxt = "180";
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.WEST, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.WEST, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery westBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur westBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(westBLQuery, westBLOccur);
			}
			// northBL
			if(southBL != null) {
				String lowerTxt = Double.toString(Double.parseDouble(southBL) + 1);
				// 90 + 360
				String upperTxt = "450";
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.NORTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.NORTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery northBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur northBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(northBLQuery, northBLOccur);
			}
			// southBL
			if(northBL != null) {
				String upperTxt = Double.toString(Double.parseDouble(northBL) - 1);
				// -90 + 360
				String lowerTxt = "270";
				Term lowerTerm = (lowerTxt == null ? null : new Term(LuceneIndexField.SOUTH, lowerTxt.toLowerCase()));
				Term upperTerm = (upperTxt == null ? null : new Term(LuceneIndexField.SOUTH, upperTxt.toLowerCase()));
				boolean inclusive = true ;
				RangeQuery southBLQuery = new RangeQuery(lowerTerm, upperTerm, inclusive);
				BooleanClause.Occur southBLOccur = LuceneUtils.convertRequiredAndProhibitedToOccur(true, false);
				query.add(southBLQuery, southBLOccur);
			}
		}
	}
}