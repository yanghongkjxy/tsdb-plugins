/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package net.opentsdb.catalog;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Pattern;

import net.opentsdb.core.Const;
import net.opentsdb.core.TSDB;
import net.opentsdb.meta.MetricsMetaAPI;
import net.opentsdb.meta.QueryOptions;
import net.opentsdb.meta.TSMeta;
import net.opentsdb.meta.UIDMeta;
import net.opentsdb.uid.UniqueId;
import net.opentsdb.utils.JSON;

import org.helios.tsdb.plugins.async.AsyncDispatcherExecutor;
import org.helios.tsdb.plugins.remoting.json.JSONRequest;
import org.helios.tsdb.plugins.remoting.json.annotations.JSONRequestHandler;
import org.helios.tsdb.plugins.remoting.json.annotations.JSONRequestService;
import org.helios.tsdb.plugins.service.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;

/**
 * <p>Title: SQLCatalogMetricsMetaAPIImpl</p>
 * <p>Description: The {@link MetricsMetaAPI} implementation for the SQL Catalof</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>net.opentsdb.catalog.SQLCatalogMetricsMetaAPIImpl</code></p>
 */
@JSONRequestService(name="meta")
public class SQLCatalogMetricsMetaAPIImpl implements MetricsMetaAPI, UncaughtExceptionHandler {
	/** The MetricsMetaAPIService service executor */
	protected final AsyncDispatcherExecutor metaQueryExecutor;
	/** The SQLWorker to manage JDBC Ops */
	protected final SQLWorker sqlWorker;
	/** The TSDB instance */
	protected final TSDB tsdb;
	/** The meta-reader for returning pojos */
	protected final MetaReader metaReader;
	/** Instance logger */
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	/** The maximum TSUID in Hex String format */
	public static final String MAX_TSUID;
	/** The dynamic binding SQL block for tag keys */
	public static final String TAGK_SQL_BLOCK = "K.NAME %s ?"; 
	/** The dynamic binding SQL block for tag values */
	public static final String TAGV_SQL_BLOCK = "V.NAME %s ?"; 
	/** The dynamic binding SQL block for metric names */
	public static final String METRIC_SQL_BLOCK = "M.NAME %s ?"; 
	
	
	
	/** Empty string array const */
	public static final String[] EMPTY_STR_ARR = {};
	/** Pipe parser pattern */
	public static final Pattern SPLIT_PIPES = Pattern.compile("\\|");
	
	static {
		char[] fs = new char[4 + (2*4*Const.MAX_NUM_TAGS)];
		Arrays.fill(fs, 'F');
		MAX_TSUID = new String(fs);		
	}


	
	/** The UID Retrieval SQL template.
	 * Tokens are:
	 * 1. The target UID type
	 * 2. The correlation UID type
	 * 3. The bind symbols for #2
	 * 4. The start at XUID expression
	 *  */
	public static final String GET_KEY_TAGS_SQL = 
			"SELECT DISTINCT X.* FROM TSD_%s X, TSD_TSMETA F, TSD_FQN_TAGPAIR T, TSD_TAGPAIR P, TSD_%s K " + 
			"WHERE K.XUID = F.METRIC_UID " + 
			"AND F.FQNID = T.FQNID " + 
			"AND T.XUID = P.XUID " + 
			"AND P.TAGK = X.XUID " + 
			"AND (%s) " +   				//  K.NAME = ? et. al.
			"AND X.NAME NOT IN (%s) " +
			"AND %s " + 					// XUID_START_SQL goes here
			"ORDER BY X.XUID DESC " + 
			"LIMIT ?"; 
		
		/** The initial start range if no starting index is supplied. Token is the target table alias  */
		public static final String INITIAL_XUID_START_SQL = " X.XUID <= 'FFFFFF'";
		/** The initial start range if a starting index is supplied. Token is the target table alias */
		public static final String XUID_START_SQL = " X.XUID < ? " ;

	
	
	/**
	 * Creates a new SQLCatalogMetricsMetaAPIImpl
	 * @param sqlWorker The SQL execution interface 
	 * @param metaReader The meta-reader for converting ResultSets into OpenTSDB pojos.
	 * @param tsdb The TSDB instance
	 * @param ctx The plugin context
	 */
	public SQLCatalogMetricsMetaAPIImpl(SQLWorker sqlWorker, MetaReader metaReader, TSDB tsdb, PluginContext ctx) {
		metaQueryExecutor = new AsyncDispatcherExecutor("MetricsMetaAPIService", ctx.getExtracted());
		metaQueryExecutor.registerUncaughtExceptionHandler(this);
		this.sqlWorker = sqlWorker;
		this.tsdb = tsdb;
		this.metaReader = metaReader;
		
		ctx.setResource(getClass().getSimpleName(), this);				
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.opentsdb.meta.MetricsMetaAPI#getTagKeys(net.opentsdb.meta.QueryOptions, java.lang.String, java.lang.String[])
	 */
	@Override
	public Deferred<Set<UIDMeta>> getTagKeys(final QueryOptions queryOptions, final String metric, final String...tagKeys) {
		return getUIDsFor(UniqueId.UniqueIdType.TAGK, UniqueId.UniqueIdType.METRIC, queryOptions, metric, tagKeys);
	}
	

	/**
	 * Shuts down this service
	 */
	public void shutdown() {
		metaQueryExecutor.shutdown();
	}
	
	/**
	 * Executes a single correlated UID query, where the target is the type we want to query and the filter is the correlation data.
	 * @param targetType The type we're looking up
	 * @param filterType The type we're using to filter
	 * @param queryOptions The query options 
	 * @param filterName The primary filter name driver
	 * @param excludes Values of the target name that we don't want
	 * @return A deferred result to a set of matching UIDMetas of the type defined in the target argument
	 */
	protected Deferred<Set<UIDMeta>> getUIDsFor(final UniqueId.UniqueIdType targetType, final UniqueId.UniqueIdType filterType, final QueryOptions queryOptions, final String filterName, final String...excludes) {
		final Deferred<Set<UIDMeta>> def = new Deferred<Set<UIDMeta>>();
		this.metaQueryExecutor.execute(new Runnable() {
			public void run() {
				final List<Object> binds = new ArrayList<Object>();
				String sql = null;
				final String predicate = expandPredicate(filterName, TAGK_SQL_BLOCK, binds);
				try {
					
//					binds.add(filterName);
					StringBuilder keyBinds = new StringBuilder();
					for(String key: excludes) {
						keyBinds.append("?, ");
						binds.add(key);
					}			
					if(keyBinds.length()>0) {
						keyBinds.deleteCharAt(keyBinds.length()-1); 
						keyBinds.deleteCharAt(keyBinds.length()-1);
					}
//					 * 1. The target UID type
//					 * 2. The correlation UID type
//					 * 3. The bind symbols for #2
//					 * 4. The start at XUID expression
					
					if(queryOptions.getNextIndex()==null) {
						sql = String.format(GET_KEY_TAGS_SQL, targetType, filterType, predicate, keyBinds, INITIAL_XUID_START_SQL); 
					} else {
						sql = String.format(GET_KEY_TAGS_SQL, targetType, filterType, predicate, keyBinds, XUID_START_SQL);
						binds.add(queryOptions.getNextIndex());
					}
					binds.add(queryOptions.getPageSize());
					final Set<UIDMeta> uidMetas = new LinkedHashSet<UIDMeta>(queryOptions.getPageSize());
					log.info("Executing SQL [{}] with binds {}", sql, binds);
					uidMetas.addAll(metaReader.readUIDMetas(sqlWorker.executeQuery(sql, true, binds.toArray(new Object[0])), UniqueId.UniqueIdType.TAGK));
					def.callback(uidMetas);
				} catch (Exception ex) {
					log.error("Failed to execute getTagKeysFor.\nSQL was [{}]", sql, ex);
					def.callback(ex);
				}
			}
		});
		
		return def;
	} 

	
	/** The Metric Name Retrieval SQL template when tag keys are provided */
	public static final String GET_METRIC_NAMES_WITH_KEYS_SQL =
			"SELECT DISTINCT X.* FROM TSD_METRIC X, TSD_TSMETA F, TSD_FQN_TAGPAIR T, TSD_TAGPAIR P, TSD_TAGK K  " +
			"WHERE X.XUID = F.METRIC_UID  " +
			"AND F.FQNID = T.FQNID  " +
			"AND T.XUID = P.XUID " +
			"AND P.TAGK = K.XUID " +
			"AND (%s) ";			// K.NAME = ? 	  --- > TAGK_SQL_BLOCK		

	/** The Metric Name Retrieval SQL template when no tag keys are provided */
	public static final String GET_METRIC_NAMES_SQL =
			"SELECT X.* FROM TSD_METRIC X WHERE %s ORDER BY X.XUID DESC LIMIT ?"; 
	
	
	// SAMPLE {"t":"req", "rid":1, "svc":"meta", "op":"metricnames", "q": { "pageSize" : 100 }, "keys" : ["host"] }
	
	
	
	class ResultCompleteCallback<T> implements Callback<Void, T> {
		private final JSONRequest request;		
		/**
		 * Creates a new ResultCompleteCallback
		 * @param request
		 */
		public ResultCompleteCallback(JSONRequest request) {
			this.request = request;
		}
		@Override
		public Void call(T result) throws Exception {
			try {
				JSON.getMapper().writeValue(request.response().getChannelOutputStream(), result);
			} catch (Exception e) {
				request.error("Failed to get metric names", e);
				log.error("Failed to get metric names", e);
			}			
			return null;
		}
	}

	/**
	 * HTTP and WebSocket exposed interface to {@link #getMetricNames(net.opentsdb.meta.QueryOptions, java.lang.String[])} 
	 * @param request The JSON request
	 * <p>Sample request:<pre>
	 * 				{"t":"req", "rid":1, "svc":"meta", "op":"metricnames", "q": { "pageSize" : 10 }, "keys" : ["host", "type", "cpu"] }
	 * </pre></p>
	 */
	@JSONRequestHandler(name="metricnames", description="Returns the MetricNames that match the passed tag keys")
	public void jsonMetricNames(final JSONRequest request) {
		QueryOptions q = JSON.parseToObject(request.getRequest().get("q").toString(), QueryOptions.class);
		final ArrayNode keysArr = (ArrayNode)request.getRequest().get("keys");
		final String[] keys = new String[keysArr.size()];
		for(int i = 0; i < keysArr.size(); i++) {
			keys[i] = keysArr.get(i).asText();
		}
		log.info("Processing JSONMetricNames. q: [{}], keys: {}", q, Arrays.toString(keys));		
		getMetricNames(q, keys).addCallback(new ResultCompleteCallback<Set<UIDMeta>>(request));
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.opentsdb.meta.MetricsMetaAPI#getMetricNames(net.opentsdb.meta.QueryOptions, java.lang.String[])
	 */
	@Override
	public Deferred<Set<UIDMeta>> getMetricNames(final QueryOptions queryOptions, final String... tagKeys) {
		final Deferred<Set<UIDMeta>> def = new Deferred<Set<UIDMeta>>();
		this.metaQueryExecutor.execute(new Runnable() {
			public void run() {				
				final List<Object> binds = new ArrayList<Object>();
				
				String sql = null;
				try {
					if(tagKeys==null || tagKeys.length==0) {										
						if(queryOptions.getNextIndex()==null) {
							sql = String.format(GET_METRIC_NAMES_SQL,INITIAL_XUID_START_SQL); 
						} else {
							sql = String.format(GET_METRIC_NAMES_SQL, XUID_START_SQL);
							binds.add(queryOptions.getNextIndex());
						}
					} else {
						StringBuilder keySql = new StringBuilder("SELECT * FROM ( ").append(String.format(GET_METRIC_NAMES_WITH_KEYS_SQL, expandPredicate(tagKeys[0], TAGK_SQL_BLOCK, binds))); 
//						binds.add(tagKeys[0]);
						int tagCount = tagKeys.length;
						for(int i = 1; i < tagCount; i++) {
							keySql.append(" INTERSECT ").append(String.format(GET_METRIC_NAMES_WITH_KEYS_SQL, expandPredicate(tagKeys[i], TAGK_SQL_BLOCK, binds)));
//							binds.add(tagKeys[i]);
						}
						keySql.append(") X ");
						if(queryOptions.getNextIndex()==null) {
							keySql.append(" WHERE ").append(INITIAL_XUID_START_SQL);
						} else {
							keySql.append(" WHERE ").append(XUID_START_SQL);
							binds.add(queryOptions.getNextIndex());
						}
						keySql.append(" ORDER BY X.XUID DESC LIMIT ? ");
						sql = keySql.toString();
					}
					binds.add(queryOptions.getPageSize());
					log.info("Executing SQL [{}] with binds {}", sql, binds);
					final Set<UIDMeta> uidMetas = new LinkedHashSet<UIDMeta>(queryOptions.getPageSize());
					uidMetas.addAll(metaReader.readUIDMetas(sqlWorker.executeQuery(sql, true, binds.toArray(new Object[0])), UniqueId.UniqueIdType.METRIC));					
					def.callback(uidMetas);
				} catch (Exception ex) {
					log.error("Failed to execute getMetricNamesFor.\nSQL was [{}]", sql, ex);
					def.callback(ex);					
				}
			}
		});
		return def;
	}

	/** The Metric Name Retrieval SQL template when tag pairs are provided */
	public static final String GET_METRIC_NAMES_WITH_TAGS_SQL =
			"SELECT DISTINCT X.* FROM TSD_METRIC X, TSD_TSMETA F, TSD_FQN_TAGPAIR T, TSD_TAGPAIR P, TSD_TAGK K, TSD_TAGV V " +
			"WHERE X.XUID = F.METRIC_UID  " +
			"AND F.FQNID = T.FQNID  " +
			"AND T.XUID = P.XUID " +
			"AND P.TAGK = K.XUID " +
			"AND P.TAGV = V.XUID " +
			"AND (%s) " +					// K.NAME % ? 	  --- > TAGK_SQL_BLOCK		 			
			"AND (%s) ";					// V.NAME % ? 	  --- > TAGV_SQL_BLOCK
	

	
	/**
	 * HTTP and WebSocket exposed interface to {@link #getMetricNames(net.opentsdb.meta.QueryOptions, java.lang.String[])} 
	 * @param request The JSON request
	 * <p>Sample request:<pre>
	 * 				{"t":"req", "rid":1, "svc":"meta", "op":"metricswtags", "q": { "pageSize" : 10 }, "tags" : {"host" : "*", "type" : "combined"}}
	 * </pre></p>
	 */
	@JSONRequestHandler(name="metricswtags", description="Returns the MetricNames that match the passed tag pairs")
	public void jsonMetricNamesWithTags(final JSONRequest request) {
		QueryOptions q = JSON.parseToObject(request.getRequest().get("q").toString(), QueryOptions.class);
		ObjectNode tagNode = (ObjectNode)request.getRequest().get("tags");
		final Map<String, String> tags = new TreeMap<String, String>();
		Iterator<String> titer = tagNode.fieldNames();
		while(titer.hasNext()) {
			String key = titer.next();
			tags.put(key, tagNode.get(key).asText());
		}
		log.info("Processing jsonMetricNamesWithTags. q: [{}], tags: {}", q, tags);		
		getMetricNames(q, tags).addCallback(new ResultCompleteCallback<Set<UIDMeta>>(request));
	}


	
	/**
	 * {@inheritDoc}
	 * @see net.opentsdb.meta.MetricsMetaAPI#getMetricNames(net.opentsdb.meta.QueryOptions, java.util.Map)
	 */
	@Override
	public Deferred<Set<UIDMeta>> getMetricNames(final QueryOptions queryOptions, final Map<String, String> tags) {
		final Deferred<Set<UIDMeta>> def = new Deferred<Set<UIDMeta>>();
		this.metaQueryExecutor.execute(new Runnable() {
			public void run() {				
				final List<Object> binds = new ArrayList<Object>();
				final List<String> likeOrEquals = new ArrayList<String>();
				String sql = null;
				try {
					if(tags==null || tags.isEmpty()) {										
						if(queryOptions.getNextIndex()==null) {
							sql = String.format(GET_METRIC_NAMES_SQL,INITIAL_XUID_START_SQL); 
						} else {
							sql = String.format(GET_METRIC_NAMES_SQL, XUID_START_SQL);
							binds.add(queryOptions.getNextIndex());
						}
					} else {
//						String predicate = expandPredicate(tagKeys[0], TAGK_SQL_BLOCK, binds)
						Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator();
						Map.Entry<String, String> entry = iter.next();
								
						StringBuilder keySql = new StringBuilder("SELECT * FROM ( ").append(String.format(GET_METRIC_NAMES_WITH_TAGS_SQL, 
								expandPredicate(entry.getKey(), TAGK_SQL_BLOCK, binds),
								expandPredicate(entry.getValue(), TAGV_SQL_BLOCK, binds)								
						));
						
						//processBindsAndTokens(entry, binds, likeOrEquals);
						while(iter.hasNext()) {
//							processBindsAndTokens(iter.next(), binds, likeOrEquals);
							entry = iter.next();
							keySql.append(" INTERSECT ").append(String.format(GET_METRIC_NAMES_WITH_TAGS_SQL,
									expandPredicate(entry.getKey(), TAGK_SQL_BLOCK, binds),
									expandPredicate(entry.getValue(), TAGV_SQL_BLOCK, binds)																	
							));
						}
						keySql.append(") X ");
						if(queryOptions.getNextIndex()==null) {
							keySql.append(" WHERE ").append(INITIAL_XUID_START_SQL);
						} else {
							keySql.append(" WHERE ").append(XUID_START_SQL);
							binds.add(queryOptions.getNextIndex());
						}
						keySql.append(" ORDER BY X.XUID DESC LIMIT ? ");
						sql = String.format(keySql.toString(), likeOrEquals.toArray());
					}
					binds.add(queryOptions.getPageSize());
					log.info("Executing SQL [{}] with binds {}", sql, binds);
					final Set<UIDMeta> uidMetas = new LinkedHashSet<UIDMeta>(queryOptions.getPageSize());
					uidMetas.addAll(metaReader.readUIDMetas(sqlWorker.executeQuery(sql, true, binds.toArray(new Object[0])), UniqueId.UniqueIdType.METRIC));					
					def.callback(uidMetas);
				} catch (Exception ex) {
					log.error("Failed to execute getMetricNamesFor (with tags).\nSQL was [{}]", sql, ex);
					def.callback(ex);					
				}
			}
		});
		return def;
	}

	
	/** The Metric Name Retrieval SQL template when tag pairs are provided */
	public static final String GET_TSMETAS_SQL =
			"SELECT DISTINCT X.* FROM TSD_TSMETA X, TSD_METRIC M, TSD_FQN_TAGPAIR T, TSD_TAGPAIR P, TSD_TAGK K, TSD_TAGV V " +
			"WHERE M.XUID = X.METRIC_UID  " +
			"AND X.FQNID = T.FQNID  " +
			"AND T.XUID = P.XUID " +
			"AND P.TAGK = K.XUID " +
			"AND (%s) " + 						// M.NAME = ?    --> METRIC_SQL_BLOCK
			"AND P.TAGV = V.XUID " +
			"AND (%s) " +					// K.NAME % ? 	  --- > TAGK_SQL_BLOCK		 			
			"AND (%s) ";					// V.NAME % ? 	  --- > TAGV_SQL_BLOCK

	/** The TSMeta Retrieval SQL template when no tags or metric name are provided and overflow is true */
	public static final String GET_TSMETAS_NO_TAGS_NO_METRIC_NAME_SQL =
			"SELECT X.* FROM TSD_TSMETA X WHERE %s ORDER BY X.TSUID DESC LIMIT ?"; 

	/** The TSMeta Retrieval SQL template when no tags are provided and overflow is true */
	public static final String GET_TSMETAS_NO_TAGS_NAME_SQL =
			"SELECT X.* FROM TSD_TSMETA X, TSD_METRIC M WHERE M.XUID = X.METRIC_UID AND %s AND M.NAME = ? ORDER BY X.TSUID DESC LIMIT ?"; 
	
	/** The initial start range if no starting index is supplied. Token is the target table alias  */
	public static final String INITIAL_TSUID_START_SQL = " X.TSUID <= '" + MAX_TSUID + "'";
	/** The initial start range if a starting index is supplied. Token is the target table alias */
	public static final String TSUID_START_SQL = " X.TSUID < ? " ;

	
	/**
	 * HTTP and WebSocket exposed interface to {@link #getTSMetas(net.opentsdb.meta.QueryOptions, boolean, java.lang.String, java.util.Map)} 
	 * @param request The JSON request
	 * <p>Sample request:<pre>
	 * 				{"t":"req", "rid":1, "svc":"meta", "op":"tsmetas", "q": { "pageSize" : 10 }, "m":"sys.cp*", "overflow":false,  "tags" : {"host" : "*NWHI*"}}
	 * </pre></p>
	 */
	@JSONRequestHandler(name="tsmetas", description="Returns the MetricNames that match the passed tag pairs")
	public void jsonTSMetas(final JSONRequest request) {
		final QueryOptions q = JSON.parseToObject(request.getRequest().get("q").toString(), QueryOptions.class);
		final String metricName = request.getRequest().get("m").textValue();
		final boolean overflow = request.getRequest().get("overflow").asBoolean(false);
		final ObjectNode tagNode = (ObjectNode)request.getRequest().get("tags");
		final Map<String, String> tags = new TreeMap<String, String>();
		Iterator<String> titer = tagNode.fieldNames();
		while(titer.hasNext()) {
			String key = titer.next();
			tags.put(key, tagNode.get(key).asText());
		}
		log.info("Processing jsonTSMetas. q: [{}], tags: {}", q, tags);		
		getTSMetas(q, overflow, metricName, tags).addCallback(new ResultCompleteCallback<Set<TSMeta>>(request));
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see net.opentsdb.meta.MetricsMetaAPI#getTSMetas(net.opentsdb.meta.QueryOptions, boolean, java.lang.String, java.util.Map)
	 */
	@Override
	public Deferred<Set<TSMeta>> getTSMetas(final QueryOptions queryOptions, final boolean overflow, final String metricName, final Map<String, String> tags) {
		final Deferred<Set<TSMeta>> def = new Deferred<Set<TSMeta>>();		
		this.metaQueryExecutor.execute(new Runnable() {
			public void run() {				
				final List<Object> binds = new ArrayList<Object>();
				String sql = null;
				final boolean hasMetricName = (metricName==null || metricName.trim().isEmpty());
				try {
					if(tags==null || tags.isEmpty()) {	
						if(!overflow) {
							def.callback(Collections.EMPTY_SET);
							return;
						}
						if(queryOptions.getNextIndex()==null) {							
							sql = String.format(hasMetricName ? GET_TSMETAS_NO_TAGS_NAME_SQL : GET_TSMETAS_NO_TAGS_NO_METRIC_NAME_SQL, INITIAL_XUID_START_SQL); 
						} else {
							sql = String.format(hasMetricName ? GET_TSMETAS_NO_TAGS_NAME_SQL : GET_TSMETAS_NO_TAGS_NO_METRIC_NAME_SQL, XUID_START_SQL);
							binds.add(queryOptions.getNextIndex());
						}
						if(hasMetricName) binds.add(metricName);
						
					} else {
						// expandPredicate(entry.getKey(), TAGK_SQL_BLOCK, binds),
						StringBuilder keySql = new StringBuilder("SELECT * FROM ( ");
						prepareGetTSMetasSQL(metricName, tags, binds, keySql);
						keySql.append(") X ");
						if(queryOptions.getNextIndex()==null) {
							keySql.append(" WHERE ").append(INITIAL_TSUID_START_SQL);
						} else {
							keySql.append(" WHERE ").append(TSUID_START_SQL);
							binds.add(queryOptions.getNextIndex());
						}
						keySql.append(" ORDER BY X.TSUID DESC LIMIT ? ");
						sql = keySql.toString();
					}
					binds.add(queryOptions.getPageSize());
					log.debug("Executing SQL [{}] with binds {}", sql, binds);
					final Set<TSMeta> tsMetas = new LinkedHashSet<TSMeta>(queryOptions.getPageSize());
					// =================================================================================================================================
					//    LOAD TSMETAS FROM TSDB/HBASE
					// =================================================================================================================================
//					ResultSet disconnected = sqlWorker.executeQuery(sql, true, binds.toArray(new Object[0]));
//					while(disconnected.next()) {
//						String tsuid = disconnected.getString(5);
//						tsMetas.add(TSMeta.getTSMeta(tsdb, tsuid).join(1000));  // FIXME:  Chain the callbacks, then add the timeout
//					}
					// =================================================================================================================================
					//	LOADS TSMETAS FROM THE SQL CATALOG DB
					// =================================================================================================================================
					tsMetas.addAll(metaReader.readTSMetas(sqlWorker.executeQuery(sql, true, binds.toArray(new Object[0])), true));					
					def.callback(tsMetas);
				} catch (Exception ex) {
					log.error("Failed to execute getTSMetas (with tags).\nSQL was [{}]", sql, ex);
					def.callback(ex);					
				}
			}
		});
		return def;
	}

	
	/**
	 * Generates a TSMeta query
	 * @param sqlBuffer The sql buffer to append generated SQL into
	 * @param binds The query bind values
	 * @param queryOptions The query options
	 * @param metricName The metric name
	 * @param tags The tag pairs
	 */
	protected void generateTSMetaSQL(final StringBuilder sqlBuffer, final List<Object> binds, final QueryOptions queryOptions, final String metricName, final Map<String, String> tags) {
		final boolean hasMetricName = (metricName==null || metricName.trim().isEmpty());
		if(tags==null || tags.isEmpty()) {	
			if(queryOptions.getNextIndex()==null) {							
				sqlBuffer.append(String.format(hasMetricName ? GET_TSMETAS_NO_TAGS_NAME_SQL : GET_TSMETAS_NO_TAGS_NO_METRIC_NAME_SQL, INITIAL_XUID_START_SQL)); 
			} else {
				sqlBuffer.append(String.format(hasMetricName ? GET_TSMETAS_NO_TAGS_NAME_SQL : GET_TSMETAS_NO_TAGS_NO_METRIC_NAME_SQL, XUID_START_SQL));
				binds.add(queryOptions.getNextIndex());
			}
			if(hasMetricName) binds.add(metricName);
			
		} else {
			StringBuilder keySql = new StringBuilder("SELECT * FROM ( ");
			prepareGetTSMetasSQL(metricName, tags, binds, keySql);
			keySql.append(") X ");
			if(queryOptions.getNextIndex()==null) {
				keySql.append(" WHERE ").append(INITIAL_TSUID_START_SQL);
			} else {
				keySql.append(" WHERE ").append(TSUID_START_SQL);
				binds.add(queryOptions.getNextIndex());
			}
			keySql.append(" ORDER BY X.TSUID DESC LIMIT ? ");
			sqlBuffer.append(keySql.toString());
		}		
	}
		
	protected void doGetTSMetasSQLTagValues(final boolean firstEntry, final String metricName, Map.Entry<String, String> tag, final List<Object> binds, final StringBuilder sql) {
		// // expandPredicate(entry.getKey(), TAGK_SQL_BLOCK, binds),
		if(!firstEntry) {
			sql.append(" INTERSECT ");
		}
		
		sql.append(String.format(GET_TSMETAS_SQL,  
				expandPredicate(metricName, METRIC_SQL_BLOCK, binds),
				expandPredicate(tag.getKey(), TAGK_SQL_BLOCK, binds),
				expandPredicate(tag.getValue(), TAGV_SQL_BLOCK, binds)
		));
	}
	
	protected void prepareGetTSMetasSQL(final String metricName, final Map<String, String> tags, final List<Object> binds, final StringBuilder sql) {
		Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator();
		doGetTSMetasSQLTagValues(true, metricName, iter.next(), binds, sql);
		while(iter.hasNext()) {
			doGetTSMetasSQLTagValues(false, metricName, iter.next(), binds, sql);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.opentsdb.meta.MetricsMetaAPI#getTagValues(net.opentsdb.meta.QueryOptions, java.lang.String, java.util.Map, java.lang.String)
	 */
	@Override
	public Deferred<Set<UIDMeta>> getTagValues(QueryOptions queryOptions, String metric, Map<String, String> tagPairs, String tagKey) {
		//return getUIDsFor(UniqueId.UniqueIdType.TAGV, UniqueId.UniqueIdType.METRIC, queryOptions, metric, tagKeys);
		return null;
	}
		
		
	/**
	 * {@inheritDoc}
	 * @see net.opentsdb.meta.MetricsMetaAPI#evaluate(net.opentsdb.meta.QueryOptions, java.lang.String[])
	 */
	@Override
	public Deferred<Set<TSMeta>> evaluate(QueryOptions queryOptions, String... expressions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		log.error("Caught exception on thread [{}]", t, e);
		
	}

	/**
	 * Expands a SQL predicate for wildcards and multis
	 * @param value The value expression
	 * @param predicateBase The constant predicate base format
	 * @param binds The bind variable accumulator
	 * @return the expanded predicate
	 */
	public static final String expandPredicate(final String value, final String predicateBase, final List<Object> binds) {
		final StringTokenizer st = new StringTokenizer(value.replace(" ", ""), "|", false);
		final int segmentCount = st.countTokens();
		if(segmentCount<1) throw new RuntimeException("Failed to parse expression [" + value + "]. Segment count was 0");
		if(segmentCount==1) {
			String val = st.nextToken();
			binds.add(val.replace('*', '%'));
			return predicateBase.replace("%s", val.indexOf('*')==-1 ? "=" : "LIKE");
		}
		StringBuilder b = new StringBuilder();
		for(int i = 0; i < segmentCount; i++) {			
			if(i!=0) b.append(" OR ");
			String val = st.nextToken();
			binds.add(val.replace('*', '%'));
			b.append(predicateBase.replace("%s", val.indexOf('*')==-1 ? "=" : "LIKE"));			
		}
		return b.toString();				
	}


}