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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import net.opentsdb.core.TSDB;
import net.opentsdb.meta.TSMeta;
import net.opentsdb.meta.UIDMeta;
import net.opentsdb.uid.UniqueId.UniqueIdType;

import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.tools.Server;
import org.helios.tsdb.plugins.event.TSDBSearchEvent;
import org.helios.tsdb.plugins.util.ConfigurationHelper;
import org.helios.tsdb.plugins.util.SystemClock;
import org.helios.tsdb.plugins.util.SystemClock.ElapsedTime;
import org.helios.tsdb.plugins.util.URLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Title: H2DBCatalog</p>
 * <p>Description: DB catalog interface for H2 DB</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>net.opentsdb.catalog.H2DBCatalog</code></p>
 */

public class H2DBCatalog implements CatalogDBInterface {
	/** Instance logger */
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	/** The JDBC URL */
	protected String jdbcUrl = null;
	/** The JDBC Driver name */
	protected String jdbcDriver = null;
	/** The JDBC User name */
	protected String jdbcUser = null;
	/** The JDBC User password */
	protected String jdbcPassword = null;	
	/** The constructed datasource */
	protected JdbcConnectionPool dataSource = null;
	/** The H2 DDL resource */
	protected String[] ddlResources = null;	
	
	/** The H2 TCP Port */
	protected int tcpPort = -1;
	/** The H2 TCP Allow Others */
	protected boolean tcpAllowOthers = false;
	/** The H2 HTTP Port */
	protected int httpPort = -1;
	/** The H2 HTTP Allow Others */
	protected boolean httpAllowOthers = false;
	
	/** The container/manager for the TCP Listener */
	protected Server tcpServer = null;
	/** The container/manager for the Web Listener */
	protected Server httpServer = null;
	
	
	/** The config property name for the H2 Init DDL resource */
	public static final String DB_H2_DDL = "helios.search.catalog.h2.ddl";
	/** The default H2 Init DDL resource */
	public static final String DEFAULT_DB_H2_DDL = "classpath:/ddl/catalog.sql";
	
	
	/** The config property name for the H2 TCP Listener port */
	public static final String DB_H2_TCP_PORT = "helios.search.catalog.h2.port.tcp";
	/** The default H2 TCP Listener port*/
	public static final int DEFAULT_DB_H2_TCP_PORT = 9092;
	/** The config property name for the H2 TCP Listener accessibility from other hosts */
	public static final String DB_H2_TCP_ALLOW_OTHERS = "helios.search.catalog.h2.allowothers.tcp";
	/** The default H2 TCP Listener port*/
	public static final boolean DEFAULT_DB_H2_TCP_ALLOW_OTHERS = false;
	
	/** The config property name for the H2 HTTP Console Listener port */
	public static final String DB_H2_HTTP_PORT = "helios.search.catalog.h2.port.tcp";
	/** The default H2 HTTP Console Listener port*/
	public static final int DEFAULT_DB_H2_HTTP_PORT = 8082;
	/** The config property name for the H2 HTTP Console Listener accessibility from other hosts */
	public static final String DB_H2_HTTP_ALLOW_OTHERS = "helios.search.catalog.h2.allowothers.tcp";
	/** The default H2 HTTP Console Listener port*/
	public static final boolean DEFAULT_DB_H2_HTTP_ALLOW_OTHERS = false;
	
	/** The UIDMeta indexing SQL template */	
	public static final String UID_INDEX_SQL_TEMPLATE = "INSERT INTO %s (UID,NAME,CREATED,DESCRIPTION,DISPLAY_NAME,NOTES,CUSTOM) VALUES(?,?,?,?,?,?,?)";

	/** The SQL template for verification of whether a UIDMeta has been saved or not */
	public static String UID_EXISTS_SQL = "SELECT COUNT(*) FROM %s WHERE UID = ?";
	/** The SQL for verification of whether a UIDMeta pair has been saved or not */
	public static String UID_PAIR_EXISTS_SQL = "SELECT COUNT(*) FROM  TSD_TAGPAIR WHERE UID = ?";

	/** The SQL for verification of whether a TSMeta has been saved or not */
	public static String TSUID_EXISTS_SQL = "SELECT COUNT(*) FROM TSD_FQN WHERE TSUID = ?";

	/**
	 * Creates a new H2DBCatalog
	 */
	public H2DBCatalog() {
		log.info("Created DB Initializer");
	}
	
	/**
	 * Returns the created data source
	 * @return a connection pool data source
	 */
	public DataSource getDataSource() {
		return dataSource;
	}
	
	/**
	 * Returns the indexing SQL for a TagK UIDMeta
	 * @return the indexing SQL for a TagK UIDMeta
	 */
	public String getUIDMetaTagKIndexSQL() {
		return String.format(UID_INDEX_SQL_TEMPLATE, "TSD_TAGK");
	}
	
	/**
	 * Returns the indexing SQL for a TagV UIDMeta
	 * @return the indexing SQL for a TagV UIDMeta
	 */
	public String getUIDMetaTagVIndexSQL() {
		return String.format(UID_INDEX_SQL_TEMPLATE, "TSD_TAGV");
	}

	/**
	 * Returns the indexing SQL for a Metric UIDMeta
	 * @return the indexing SQL for a Metric UIDMeta
	 */
	public String getUIDMetaMetricIndexSQL() {
		return String.format(UID_INDEX_SQL_TEMPLATE, "TSD_METRIC");
	}

	
	/**
	 * Runs the initialization routine
	 * @param tsdb The parent TSDB instance
	 * @param extracted The extracted configuration
	 */
	public void initialize(TSDB tsdb, Properties extracted) {
		log.info("\n\t================================================\n\tStarting DB Initializer\n\tName:{}\n\t================================================", getClass().getSimpleName());
		jdbcUrl = ConfigurationHelper.getSystemThenEnvProperty(DB_JDBC_URL, DEFAULT_DB_JDBC_URL, extracted);
		jdbcDriver = ConfigurationHelper.getSystemThenEnvProperty(DB_JDBC_DRIVER, DEFAULT_DB_JDBC_DRIVER, extracted);
		jdbcUser = ConfigurationHelper.getSystemThenEnvProperty(DB_JDBC_USER, DEFAULT_DB_JDBC_USER, extracted);
		jdbcPassword = ConfigurationHelper.getSystemThenEnvProperty(DB_JDBC_PW, DEFAULT_DB_JDBC_PW, extracted);
		ddlResources = ConfigurationHelper.getSystemThenEnvPropertyArray(DB_H2_DDL, DEFAULT_DB_H2_DDL, extracted);
		tcpPort = ConfigurationHelper.getIntSystemThenEnvProperty(DB_H2_TCP_PORT, DEFAULT_DB_H2_TCP_PORT, extracted);
		tcpAllowOthers = ConfigurationHelper.getBooleanSystemThenEnvProperty(DB_H2_TCP_ALLOW_OTHERS, DEFAULT_DB_H2_TCP_ALLOW_OTHERS, extracted);
		httpPort = ConfigurationHelper.getIntSystemThenEnvProperty(DB_H2_HTTP_PORT, DEFAULT_DB_H2_HTTP_PORT, extracted);
		httpAllowOthers = ConfigurationHelper.getBooleanSystemThenEnvProperty(DB_H2_HTTP_ALLOW_OTHERS, DEFAULT_DB_H2_HTTP_ALLOW_OTHERS, extracted);		
		dataSource = JdbcConnectionPool.create(jdbcUrl, jdbcUser, jdbcPassword);
		log.info("Processing DDL Resources:{}", Arrays.toString(ddlResources));
		runDDLResources();
		log.info("DDL Resources Processed");
		if(tcpPort>0 || httpPort >0) {
			log.info("Starting H2 Listeners: TCP:{}, Web:{}", tcpPort>0, httpPort>0);
			startServers();
		} else {
			log.info("No H2 Listeners Configured");
		}
		
		log.info("\n\t================================================\n\tDB Initializer Started\n\tJDBC URL:{}\n\t================================================", jdbcUrl);
	}
	
	/**
	 * Terminates the database resources
	 */
	public void shutdown() {
		log.info("\n\t================================================\n\tStopping TSDB Catalog DB\n\tName:{}\n\t================================================", jdbcUrl);
		if(tcpServer!=null) {
			log.info("Stopping TCP Server.....");
			try { 
				tcpServer.stop(); 
				tcpServer = null;
				log.info("TCP Server Stopped");
			} catch (Exception ex) {/* No Op */}  
		}
		if(httpServer!=null) {
			log.info("Stopping Web Server.....");
			try { 
				httpServer.stop(); 
				httpServer = null;
				log.info("Web Server Stopped");
			} catch (Exception ex) {/* No Op */}  
		}		
		if(dataSource!=null) {
			dataSource.dispose();
		}
		log.info("\n\t================================================\n\tTSDB Catalog DB Stopped\n\tName:{}\n\t================================================", jdbcUrl);
	}
	
	
	/**
	 * Determines if the passed UIDMeta has been stored
	 * @param conn A supplied connection
	 * @param uidMeta the UIDMeta to test
	 * @return true if stored, false otherwise
	 */
	public boolean stored(Connection conn, UIDMeta uidMeta) {
		PreparedStatement ps = null;
		ResultSet rset = null;		
		try {
			switch(uidMeta.getType()) {
				case METRIC:
					ps = conn.prepareStatement(String.format(UID_EXISTS_SQL, "TSD_METRIC"));
					break;
				case TAGK:
					ps = conn.prepareStatement(String.format(UID_EXISTS_SQL, "TSD_TAGK"));
					break;
				case TAGV:
					ps = conn.prepareStatement(String.format(UID_EXISTS_SQL, "TSD_TAGV"));
					break;
				default:
					throw new RuntimeException("yeow. Unrecognized UIDMeta type [" + uidMeta.getType().name() + "]");			
			}
			ps.setString(1, uidMeta.getUID());
			rset = ps.executeQuery();
			rset.next();
			return rset.getInt(1)>0;
		} catch (SQLException sex) {
			throw new RuntimeException("Failed to lookup UIDMeta [" + uidMeta + uidMeta.getName() + "]", sex);
		} finally {
			if(rset!=null) try { rset.close(); } catch (Exception x) {/* No Op */}
			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Determines if the passed TSMeta has been stored
	 * @param conn A supplied connection
	 * @param tsMeta the TSMeta to test
	 * @return true if stored, false otherwise
	 */
	public boolean stored(Connection conn, TSMeta tsMeta) {
		PreparedStatement ps = null;
		ResultSet rset = null;		
		try {
			ps = conn.prepareStatement(TSUID_EXISTS_SQL);
			ps.setString(1, tsMeta.getTSUID());
			rset = ps.executeQuery();
			rset.next();
			return rset.getInt(1)>0;
		} catch (SQLException sex) {
			throw new RuntimeException("Failed to lookup TSMeta [" + tsMeta  + "]", sex);
		} finally {
			if(rset!=null) try { rset.close(); } catch (Exception x) {/* No Op */}
			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Determines if the passed UIDMeta pair has been stored
	 * @param conn A supplied connection
	 * @param tagPairUid The UID of the tag pair
	 * @return true if stored, false otherwise
	 */
	public boolean tagPairStored(Connection conn, String tagPairUid) {
		PreparedStatement ps = null;
		ResultSet rset = null;
		try {
			ps = conn.prepareStatement(UID_PAIR_EXISTS_SQL);
			ps.setString(1, tagPairUid);
			rset = ps.executeQuery();
			rset.next();
			return rset.getInt(1)>0;
		} catch (SQLException sex) {
			throw new RuntimeException("Failed to lookup UIDMeta Pair [" + tagPairUid + "]", sex);
		} finally {
			if(rset!=null) try { rset.close(); } catch (Exception x) {/* No Op */}
			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	
	/** batched ps for tag key inserts */
	protected PreparedStatement uidMetaTagKIndexPs = null;
	/** batched ps for tag value inserts */
	protected PreparedStatement uidMetaTagVIndexPs = null;
	/** batched ps for metric name inserts */
	protected PreparedStatement uidMetaMetricIndexPs = null;
	/** batched ps for tag pair inserts */
	protected PreparedStatement uidMetaTagPairPs = null;
	
	/**
	 * Processes a batch of events
	 * @param conn The connection to execute the events against
	 * @param events An ordered batch of events to process
	 */
	public void processEvents(Connection conn, Set<TSDBSearchEvent> events) {		
		
		int ops = 0;
		ElapsedTime et = SystemClock.startClock();
		Set<String> batchedUids = new HashSet<String>(events.size());
		Set<String> batchedUidPairs = new HashSet<String>(events.size());
		try {			
			for(TSDBSearchEvent event: events) {
				switch(event.eventType) {
				case ANNOTATION_DELETE:
					break;
				case ANNOTATION_INDEX:
					break;
				case TSMETA_DELETE:
					break;
				case TSMETA_INDEX:
					TSMeta tsMeta = event.tsMeta;
					if(stored(conn, tsMeta)) continue;
					processTSMeta(batchedUidPairs, conn, tsMeta);
					break;
				case UIDMETA_DELETE:
					break;
				case UIDMETA_INDEX:					
					UIDMeta uidMeta = event.uidMeta;
					if(batchedUids.contains(uidMeta.toString()) || stored(conn, uidMeta)) continue;
					switch(uidMeta.getType()) {
						case METRIC:							
							if(uidMetaMetricIndexPs==null) uidMetaMetricIndexPs = conn.prepareStatement(getUIDMetaMetricIndexSQL());							
							bindUIDMeta(uidMeta, uidMetaMetricIndexPs);														
							break;
						case TAGK:
							if(uidMetaTagKIndexPs==null) uidMetaTagKIndexPs = conn.prepareStatement(getUIDMetaTagKIndexSQL());
							bindUIDMeta(uidMeta, uidMetaTagKIndexPs);							
							break;
						case TAGV:
							if(uidMetaTagVIndexPs==null) uidMetaTagVIndexPs = conn.prepareStatement(getUIDMetaTagVIndexSQL());
							bindUIDMeta(uidMeta, uidMetaTagVIndexPs);														
							break;
						default:
							log.warn("yeow. Unexpected UIDMeta type:{}", uidMeta.getType().name());
							break;
					}
					batchedUids.add(uidMeta.toString());
					ops++;
					log.info("Bound {} Index [{}]-[{}]", uidMeta.getType().name(), uidMeta.getName(), uidMeta.getUID());
					break;
				default:
					log.warn("Unexpected event type found in event queue [{}]", event.eventType.name());
					break;
					
				}
			} // end for event processor for loop
			executeUIDBatches(conn);
			if(uidMetaTagPairPs!=null) {
				executeBatch(uidMetaTagPairPs);
				uidMetaTagPairPs.clearBatch();
			}
			
			conn.commit();
			log.info("Executed {} ops in {} ms.", ops, et.elapsedMs());
		} catch (Exception ex) {
			log.error("batch operations failed", ex);
		} finally {
			if(uidMetaTagKIndexPs!=null) try { uidMetaTagKIndexPs.close(); uidMetaTagKIndexPs = null;} catch (Exception x) {/* No Op */}
			if(uidMetaTagVIndexPs!=null) try { uidMetaTagVIndexPs.close(); uidMetaTagVIndexPs = null; } catch (Exception x) {/* No Op */}
			if(uidMetaMetricIndexPs!=null) try { uidMetaMetricIndexPs.close(); uidMetaMetricIndexPs = null;} catch (Exception x) {/* No Op */}
			if(uidMetaTagPairPs!=null) try { uidMetaTagPairPs.close(); uidMetaTagPairPs = null;} catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * 
	 */
	private void executeUIDBatches(Connection conn) {
		try {
			if(uidMetaMetricIndexPs!=null) {
				executeBatch(uidMetaMetricIndexPs);
				uidMetaMetricIndexPs.clearBatch();
			}
			if(uidMetaTagVIndexPs!=null) {
				executeBatch(uidMetaTagVIndexPs);
				uidMetaTagVIndexPs.clearBatch();
			}
			if(uidMetaTagKIndexPs!=null) {
				executeBatch(uidMetaTagKIndexPs);
				uidMetaTagKIndexPs.clearBatch();
			}
			conn.commit();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to execute UID Batches", ex);
		}		
	}

	/**
	 * Stores the passed TSMeta
	 * @param batchUidPairs A set of UIDMeta tag pair keys that have already been saved in this batch.
	 * @param conn The connection to write to
	 * @param tsMeta The TSMeta to save
	 */
	protected void processTSMeta(final Set<String> batchUidPairs, Connection conn, TSMeta tsMeta) {
		UIDMeta[] tagPair = new UIDMeta[2];
		String tagPairUid = null;
		for(UIDMeta meta: tsMeta.getTags()) {
			if(tagPair[0]==null) {
				tagPair[0] = meta;
				continue;
			} else if(tagPair[1]==null) {
				tagPair[1] = meta;
				tagPairUid = processUIDMetaPair(batchUidPairs, conn, tagPair);
				tagPair[0] = null; tagPair[1] = null; 
			}
		}
	}

	/**
	 * Saves a tag UIDMeta pair
	 * @param batchUidPairs A set of UIDMeta tag pair keys that have already been saved in this batch.
	 * @param conn The connection to write to
	 * @param tagPair A pair of UIDMetas where [0] is the key and [1] is the value
	 * @return the pair key
	 */
	protected String processUIDMetaPair(final Set<String> batchUidPairs, Connection conn, UIDMeta[] tagPair) {
		if(tagPair[0].getType()!=UniqueIdType.TAGK) throw new IllegalArgumentException("Provided uidMetaKey was expected to be of type TAGK but was actually [" + tagPair[0].getType() + "]");
		if(tagPair[1].getType()!=UniqueIdType.TAGV) throw new IllegalArgumentException("Provided uidMetaValue was expected to be of type TAGV but was actually [" + tagPair[1].getType() + "]");
		String tagPairUid = tagPair[0].getUID() + tagPair[1].getUID();
		
		if(batchUidPairs.contains(tagPairUid)) return tagPairUid; 
		
		if(tagPairStored(conn, tagPairUid)) return tagPairUid;
		String INSERT_TAGPAIR_SQL = "INSERT INTO TSD_TAGPAIR (UID, TAGK, TAGV, NAME) VALUES (?,?,?,?)";
		try {
			if(uidMetaTagPairPs==null) {
				uidMetaTagPairPs = conn.prepareStatement(INSERT_TAGPAIR_SQL);
			}
			uidMetaTagPairPs.setString(1, tagPairUid);
			uidMetaTagPairPs.setString(2, tagPair[0].getUID());
			uidMetaTagPairPs.setString(3, tagPair[1].getUID());
			uidMetaTagPairPs.setString(4, tagPair[0].getName() + "=" + tagPair[1].getName());
			uidMetaTagPairPs.addBatch();
			batchUidPairs.add(tagPairUid);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to write tagpair [" + tagPairUid + "]", ex);
		} 
		return null;
	}
	/**
	 * Executes the batched statements in the passed statement and validates the results.
	 * @param ps The statement to execute batch on
	 * @throws SQLException thrown on a batch execution error
	 */
	protected void executeBatch(PreparedStatement ps) throws SQLException {
		int[] results = ps.executeBatch();
		if(results!=null && results.length>0) {
			Arrays.sort(results);
			if(results[0] < Statement.SUCCESS_NO_INFO) {
				ps.getConnection().rollback();
				throw new SQLException("Batch results had failed result code [" + results[0] + "]");
			}
		}
		log.info("Processed Batch of Size:{}", results.length);
		
		
	}
	
	/**
	 * Binds and batches a UIDMeta indexing operation
	 * @param uidMeta The UIDMeta to index
	 * @param ps The prepared statement to bind against and batch
	 * @throws SQLException thrown on binding or batching failures
	 */
	protected void bindUIDMeta(UIDMeta uidMeta, PreparedStatement ps) throws SQLException {
		ps.setString(1, uidMeta.getUID());
		ps.setString(2, uidMeta.getName());
		ps.setTimestamp(3, new Timestamp(uidMeta.getCreated()));
		ps.setString(4, uidMeta.getDescription());
		ps.setString(5, uidMeta.getDisplayName());
		ps.setString(6, uidMeta.getNotes());
		Map<String, String> custom = uidMeta.getCustom();
		if(custom==null) custom = Collections.emptyMap();
		ps.setString(7, custom.toString());
		ps.addBatch();
	}
	
	/**
	 * Starts the H2 Web and TCP listeners if configured
	 */
	protected void startServers() {
		List<String> args = new ArrayList<String>();
		if(tcpPort>0) {
			args.addAll(Arrays.asList("-tcp", "-tcpDaemon", "-tcpPort", "" + tcpPort));
			if(tcpAllowOthers) {
				args.add("-tcpAllowOthers");
			}
			log.info("Starting H2 TCP Listener on port [{}]. Allow Others:[{}]", tcpPort, tcpAllowOthers);
			tcpServer = new Server();
			try {
				tcpServer.runTool(args.toArray(new String[0]));
			} catch (Exception ex) {
				log.error("Failed to start TCP Server on port [" + tcpPort + "]", ex);
				throw new RuntimeException("Failed to start TCP Server on port [" + tcpPort + "]", ex);
				
			}
		}
		
		if(httpPort>0) {
			args.clear();
			args.addAll(Arrays.asList("-web", "-webDaemon", "-webPort", "" + httpPort));
			if(httpAllowOthers) {
				args.add("-httpAllowOthers");
			}
			log.info("Starting H2 Web Listener on port [{}]. Allow Others:[{}]", httpPort, httpAllowOthers);
			httpServer = new Server();
			try {
				httpServer.runTool(args.toArray(new String[0]));
			} catch (Exception ex) {
				log.error("Failed to start Web Server on port [" + tcpPort + "]", ex);
				throw new RuntimeException("Failed to start Web Server on port [" + tcpPort + "]", ex);				
			}
		}
	}
	
	/**
	 * Executes the configured DDL resources against the created DB
	 */
	protected void runDDLResources() {
		String pResource = null;
		Connection conn = null;
		Statement st = null;
		try {
			conn = dataSource.getConnection();
			st = conn.createStatement();
			log.info("Connected to [{}]", conn.getMetaData().getURL());
			for(String rez: ddlResources) {
				pResource = rez; 
				log.info("Processing DDL Resource [{}]", rez);
				String fileName = getDDLFileName(rez);
				st.execute("RUNSCRIPT FROM '" + fileName + "'");
				log.info("DDL Resource [{}] Processed", rez);
				new File(fileName).delete();
			}
		} catch (Exception ex) {
			log.error("Failed to run DDL Resource [" + pResource + "]", ex);
			throw new RuntimeException("Failed to run DDL Resource [" + pResource + "]", ex);
		} finally {
			if(st!=null) try { st.close(); } catch (Exception x) { /* No Op */ }
			if(conn!=null) try { conn.close(); } catch (Exception x) { /* No Op */ }
		}
	}
	
	/**
	 * Returns the readable file system file name for the passed DDL resource name
	 * @param ddlResource The resource path
	 * @return the readable DDL file
	 */
	protected String getDDLFileName(String ddlResource) {		
		InputStream is = null;
		FileOutputStream fos = null;
		File tmpFile = null;
		try {
			is = getDDLStream(ddlResource);
			tmpFile = File.createTempFile("tsdb-catalog", ".sql");
			tmpFile.deleteOnExit();
			fos = new FileOutputStream(tmpFile);
			byte[] buffer = new byte[8192];
			int bytesRead = -1;
			while((bytesRead=is.read(buffer))!=-1) {
				fos.write(buffer, 0, bytesRead);
			}
			fos.flush(); fos.close(); fos = null;
			is.close(); is = null;
			return tmpFile.getAbsolutePath();
		} catch (Exception ex) {
			log.error("Failed to get DDL file name", ex);
			throw new RuntimeException("Failed to get DDL file name", ex);
		} finally {
			if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
			if(fos!=null) try { fos.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Returns an inputstream for reading the H2 DDL
	 * @param ddlResource The resource path
	 * @return an inputstream for the H2 DDL
	 * @throws IOException thrown on errors reading from a file or URL
	 */
	protected InputStream getDDLStream(String ddlResource) throws IOException {
		if(ddlResource.startsWith("classpath:")) {
			String resource = ddlResource.substring("classpath:".length());		
			log.info("Looking Up DDL Resource [{}]", resource);
			InputStream is = getClass().getResourceAsStream(resource);
			if(is==null) {
				is = new FileInputStream("./src/main/resources" + resource);
			}
			return is;
		}
		if(URLHelper.isValidURL(ddlResource)) {
			return URLHelper.toURL(ddlResource).openStream();
		} else if(new File(ddlResource).canRead()) {
			return new FileInputStream(ddlResource);
		} else {
			throw new RuntimeException("Failed to locate DDL resource [" + ddlResource + "]");
		}
	}


}