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
package net.opentsdb.catalog.syncqueue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import net.opentsdb.catalog.CatalogDBInterface;
import net.opentsdb.catalog.datasource.CatalogDataSource;
import net.opentsdb.core.TSDB;
import net.opentsdb.meta.Annotation;
import net.opentsdb.meta.TSMeta;
import net.opentsdb.meta.UIDMeta;
import net.opentsdb.uid.UniqueId.UniqueIdType;

import org.helios.tsdb.plugins.service.PluginContext;
import org.helios.tsdb.plugins.util.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractService;
import com.stumbleupon.async.Callback;

/**
 * <p>Title: SyncQueueProcessor</p>
 * <p>Description: Service to poll the SyncQueue table in the catalog database
 * for new entries and synchronize the changes back to the OpenTSDB store.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>net.opentsdb.catalog.syncqueue.SyncQueueProcessor</code></p>
 */

public class SyncQueueProcessor extends AbstractService implements Runnable, ThreadFactory {
	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	/** The plugin context to provide the processor's configuration */
	protected final PluginContext pluginContext;
	/** The catalog datasource */
	protected final DataSource dataSource;
	/** The DB interface */
	protected final CatalogDBInterface dbInterface;
	/** The tsdb instance to synchronize changes to */
	protected final TSDB tsdb;
	/** The sync queue polling period in ms. */
	protected long pollingPeriod = DEFAULT_DB_SYNCQ_POLLER_PERIOD;
	/** The scheduler for kicking off poll events */
	protected ScheduledExecutorService scheduler = null;
	/** The handle to the scheduled task */
	protected ScheduledFuture<?> taskHandle = null;
	/** The thread factory serial number factory */
	protected final AtomicInteger serial = new AtomicInteger(0);
	/** Flag indicating that a sync is in progress */
	protected final AtomicBoolean syncInProgress = new AtomicBoolean(false);
	
	
	/** The config property name for the Sync Queue polling period in ms. */
	public static final String DB_SYNCQ_POLLER_PERIOD = "helios.search.catalog.syncq.period";
	/** The default Sync Queue polling period in ms. */
	public static final long DEFAULT_DB_SYNCQ_POLLER_PERIOD = 5000;
	/** The SQL to retrieve a TSMeta instance from the DB */
	public static final String GET_TSMETA_SQL = "SELECT * FROM TSD_TSMETA WHERE FQNID = ?";
	/** The SQL to retrieve an Annotation instance from the DB */
	public static final String GET_ANNOTATION_SQL = "SELECT * FROM TSD_ANNOTATION WHERE ANNID = ?";
	/** The SQL template to retrieve a TSMeta instance from the DB */
	public static final String GET_UIDMETA_SQL = "SELECT * FROM %s WHERE XUID = ?";
	
	/** Custom tag to place in a meta custom map to make sure the fake meta is ignored */
	public static final String IGNORE_TAG_NAME = "syncqueue.processor.ignore";

	
	/** A set of the UIDMeta type tables */
	public static final Set<String> UIDMETA_TABLES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"TSD_METRIC", "TSD_TAGK", "TSD_TAGV"
	)));
	

	
	/**
	 * Creates a new SyncQueueProcessor
	 * @param pluginContext The plugin context to provide the processor's configuration
	 */
	public SyncQueueProcessor(PluginContext pluginContext) {
		this.pluginContext = pluginContext;
		tsdb = pluginContext.getTsdb();
		dataSource = pluginContext.getResource(CatalogDataSource.class.getSimpleName(), DataSource.class);
		dbInterface = pluginContext.getResource(CatalogDBInterface.class.getSimpleName(), CatalogDBInterface.class);
	}


	/**
	 * {@inheritDoc}
	 * @see com.google.common.util.concurrent.AbstractService#doStart()
	 */
	@Override
	protected void doStart() {
		log.info("\n\t=========================================\n\tStarting SyncQueueProcessor\n\t=========================================");
		scheduler = Executors.newScheduledThreadPool(2, this);
		taskHandle = scheduler.scheduleWithFixedDelay(this, 1000, pollingPeriod, TimeUnit.MILLISECONDS);
		log.info("Sync Poller Scheduled for [{}] ms. period", pollingPeriod);
		notifyStarted();
		log.info("\n\t=========================================\n\tSyncQueueProcessor Started\n\t=========================================");
	}


	/**
	 * {@inheritDoc}
	 * @see com.google.common.util.concurrent.AbstractService#doStop()
	 */
	@Override
	protected void doStop() {
		log.info("\n\t=========================================\n\tStopping SyncQueueProcessor\n\t=========================================");
		if(taskHandle!=null) {
			taskHandle.cancel(false);
			taskHandle = null;
		}
		scheduler.shutdownNow();
		notifyStopped();
		log.info("\n\t=========================================\n\tSyncQueueProcessor Stopped\n\t=========================================");
	}


	/**
	 * {@inheritDoc}
	 * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
	 */
	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r, getClass().getSimpleName() + "Thread#" + serial.incrementAndGet());
		t.setDaemon(true);
		return t;
	}
	
	
	//===================================================================================
	//	SYNC QUEUE Column Indexes
	//===================================================================================
	/** The SyncQueue QID */
	public static int SQ_QID = 0; 
	/** The SyncQueue Event Type (TSMeta, TSAnnotation etc.) */
	public static int SQ_EVENT_TYPE = 1; 
	/** The PK of the event in its own table */
	public static int SQ_EVENT_PK = 2;
	/** The OpType ( Insert, Update, Delete) */
	public static int SQ_OP_TYPE = 3;
	/** The timestamp of the event */
	public static int SQ_EVENT_TIME = 4;
	/** The timestamp of the last synch failure */
	public static int SQ_LAST_SYNC_ATTEMPT = 5; 
	/** The stack trace of the last synch failure */
	public static int SQ_LAST_SYNC_ERROR = 6;
	


	/**
	 * <p>The implementation of the scheduled polling event</p>.
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if(!syncInProgress.compareAndSet(false, true)) {
			log.debug("Sync already in progress. Ejecting....");
		}
		log.debug("Starting SyncQueue poll cycle");
		Connection conn = null;
		PreparedStatement pollPs = null;
		ResultSet pollRset = null;
		int synchsCompleted = 0, deletesCompleted = 0;
		try {
			Map<String, Object[]> pendingSynchs = new TreeMap<String, Object[]>();
			Map<String, Object[]> pendingDeletes = new TreeMap<String, Object[]>();
			conn = dataSource.getConnection();
			conn.setAutoCommit(false);
			pollPs = conn.prepareStatement("SELECT * FROM SYNC_QUEUE WHERE LAST_SYNC_ATTEMPT IS NULL ORDER BY OP_TYPE, EVENT_TIME");
			pollRset = pollPs.executeQuery();
			int colsize = pollRset.getMetaData().getColumnCount();
			int rowsRetrieved = 0;
			while(pollRset.next()) {
				rowsRetrieved++;
				Object[] row = new Object[colsize];
				for(int i = 0; i < colsize; i++) {
					row[i] = pollRset.getObject(i+1); 
				}
				log.trace("Retrieved [{}] Type SyncQueue Item", row[SQ_OP_TYPE].toString());
				if("D".equals(row[SQ_OP_TYPE].toString())) {
					pendingDeletes.put(row[SQ_EVENT_PK].toString(), row);
				} else {
					pendingSynchs.put(String.format("%s|%s", row[SQ_EVENT_TYPE], row[SQ_EVENT_PK]), row);
				}				
			}
			log.debug("Retrieved [{}] SyncQueue Rows", rowsRetrieved);
			log.debug("Processing [{}] SyncQueue Delete Items", pendingDeletes.size());
			for(Object[] crow: pendingDeletes.values()) {
				long QID = (Long)crow[SQ_QID];
				try {
					synchObject(createDeletionPkMeta(crow), false, QID);
					//  clear pending synchs
					Object[] obsolete = pendingSynchs.remove(String.format("%s|%s", crow[SQ_EVENT_TYPE], crow[SQ_EVENT_PK]));
					if(obsolete!=null) {
						deleteSyncQueueEntry((Long)obsolete[SQ_QID]);
						log.info("Purged Obsolete SyncQueue QID [{}]", obsolete[SQ_QID]);
					}
					log.trace("Processed SyncQueue Delete QID [{}]", QID);
					deletesCompleted++;
				} catch (Exception ex) {
					log.warn("Deletion failed for QID [{}]", QID);
				}				
			}
			pendingDeletes.clear();
			log.debug("Processing [{}] SyncQueue Update Items", pendingSynchs.size());
			for(Object[] crow: pendingSynchs.values()) {
				long QID = (Long)crow[SQ_QID];
				try {
					synchObject(getStorePkMeta(crow), true, QID);
				} catch (Throwable t) {
					log.error("SyncQueue Update Failure", t);
				}
				log.trace("Processed SyncQueue Modified QID [{}]", QID);
				synchsCompleted++;
			}
			pendingSynchs.clear();
			pollRset.close(); pollRset = null;
			pollPs.close(); pollPs = null;
			conn.commit(); conn.close(); conn = null;
			log.debug("SyncQueue poll cycle complete");
			log.info("SyncQueue Loop: Deletes:[{}]  Synchs:[{}]", deletesCompleted, synchsCompleted);
		} catch (Exception ex) {
			log.warn("SyncQueueProcessor Poll Cycle Exception", ex);
		} finally {
			if(pollRset!=null) try { pollRset.close(); } catch (Exception x) {/* No Op */}
			if(pollPs!=null) try { pollPs.close(); } catch (Exception x) {/* No Op */}
			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
			syncInProgress.set(false);
		} 		
	}
	
	/**
	 * Attempts to synchronize an Object to the OpenTSDB store
	 * @param obj The object to synchronized
	 * @param update true for an update, false for a delete
	 * @param syncQueuePk The QID of the SyncQueue entry
	 */
	protected void synchObject(final Object obj, final boolean update, final long syncQueuePk) {
		try {
			if(update) {
				if(obj instanceof UIDMeta) {
					((UIDMeta)obj).syncToStorage(tsdb, true).addCallback(storeCallback(obj, syncQueuePk));
				} else if(obj instanceof TSMeta) {
					((TSMeta)obj).syncToStorage(tsdb, true).addCallback(storeCallback(obj, syncQueuePk));
				} else if(obj instanceof Annotation) {
					((Annotation)obj).syncToStorage(tsdb, true).addCallback(storeCallback(obj, syncQueuePk));
				} else {
					log.warn("Unsupported Type for OpenTSDB Synch Update:{}", obj.getClass().getName());
				}
			} else {
				if(obj instanceof UIDMeta) {
					((UIDMeta)obj).delete(tsdb).addCallback(deleteCallback(obj, syncQueuePk));
				} else if(obj instanceof TSMeta) {
					((TSMeta)obj).delete(tsdb).addCallback(deleteCallback(obj, syncQueuePk));
				} else if(obj instanceof Annotation) {
					((Annotation)obj).delete(tsdb).addCallback(deleteCallback(obj, syncQueuePk));
				} else {
					log.warn("Unsupported Type for OpenTSDB Synch Delete:{}", obj.getClass().getName());
				}				
			}
		} catch (Exception ex) {
			handleSyncQueueException(syncQueuePk, ex);
		}
	}
	
	
	/**
	 * Retrieves a meta object from the DB
	 * @param row The SyncQueue row representing the target object to read from the DB 
	 * @return the read object
	 */
	protected Object getStorePkMeta(Object[] row) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rset = null;
		try {
			conn = dataSource.getConnection();
			if(UIDMETA_TABLES.contains(row[1])) {
				ps = conn.prepareStatement(String.format(GET_UIDMETA_SQL, row[1]));
				ps.setString(1, row[SQ_EVENT_PK].toString());
				rset = ps.executeQuery();
				return dbInterface.readUIDMetas(rset).iterator().next();
			} else if("TSD_TSMETA".equals(row[1])) {
				ps = conn.prepareStatement(GET_TSMETA_SQL);
				ps.setLong(1, Long.parseLong(row[SQ_EVENT_PK].toString()));
				rset = ps.executeQuery();
				return dbInterface.readTSMetas(rset).iterator().next();
			} else if("TSD_ANNOTATION".equals(row[1])) {
				ps = conn.prepareStatement(GET_ANNOTATION_SQL);
				ps.setLong(1, Long.parseLong(row[SQ_EVENT_PK].toString()));
				rset = ps.executeQuery();
				return dbInterface.readAnnotations(rset).iterator().next();
			} else {
				throw new RuntimeException("Unrecognized SyncQueue type [" + row[1] + "]");
			}
		} catch (Exception dex) {
			throw new RuntimeException("Failed to retrieve Synch target [" + Arrays.toString(row) + "]", dex);
		} finally {
			if(rset!=null) try { rset.close(); } catch (Exception x) {/* No Op */}
			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
		}				
	}
	
	
	/**
	 * Recreates a pk only TSDB meta object from the passed SyncQueue row
	 * @param row The SyncQueue row representing a deleted meta object
	 * @return the pk only deleted object
	 */
	protected Object createDeletionPkMeta(Object[] row) {
		if(UIDMETA_TABLES.contains(row[1])) {
			UniqueIdType type = UniqueIdType.valueOf(row[1].toString().replace("TSD_", ""));
			UIDMeta uidMeta = new UIDMeta(type, (String)row[SQ_EVENT_PK]);
			uidMeta.setCustom(new HashMap<String, String>(Collections.singletonMap(IGNORE_TAG_NAME, "true")));
			return uidMeta; 			
		} else if("TSD_ANNOTATION".equals(row[1])) {
			Annotation annotation = new Annotation();
			annotation.setCustom(new HashMap<String, String>(Collections.singletonMap(IGNORE_TAG_NAME, "true")));
			String[] frags = row[SQ_EVENT_PK].toString().split(":");
			annotation.setStartTime(TimeUnit.SECONDS.convert(Long.parseLong(frags[0]), TimeUnit.MILLISECONDS));
			if(!frags[1].isEmpty()) {
				annotation.setTSUID(frags[1]);
			}
			return annotation;
		} else if("TSD_TSMETA".equals(row[1])) {
			TSMeta tsMeta = new TSMeta(row[SQ_EVENT_PK].toString()); 
			tsMeta.setCustom(new HashMap<String, String>(Collections.singletonMap(IGNORE_TAG_NAME, "true")));
			return tsMeta;
		} else {
			log.error("yeow. Unrecognized SyncQueue type [{}]", row[1]);
			return null;
		}
	}
	
	
	
	

	
	/**
	 * Creates a new delete from store completion deferred callback
	 * @param obj The object being deleted
	 * @param syncQueuePk The SyncQueue QID
	 * @return the created callback
	 */
	protected Callback<Boolean, Object> deleteCallback(final Object obj, final long syncQueuePk) {
		return new Callback<Boolean, Object>(){
			@Override
			public Boolean call(Object arg) throws Exception {
				log.info("Purged [{}]:[{}]", obj.getClass().getSimpleName(), obj);
				deleteSyncQueueEntry(syncQueuePk);
				return true;
			}
		};
	}
	
	/**
	 * Creates a new synch to store completion deferred callback
	 * @param obj The object being synched
	 * @param syncQueuePk The SyncQueue QID
	 * @return the created callback
	 */
	protected Callback<Object, Boolean> storeCallback(final Object obj, final long syncQueuePk) {
		return new Callback<Object, Boolean>(){
			@Override
			public Boolean call(Boolean success) throws Exception {
				if(success) {
					log.trace("Synched [{}]:[{}]", obj.getClass().getSimpleName(), obj);
					deleteSyncQueueEntry(syncQueuePk);
					return true;
				}
				// Synch failed. Retry next polling period.
				return false;
			}
		};
	}

	
	
	
	/**
	 * Handles a sync to store failure
	 * @param syncQueuePk The QID of the SyncQueue entry that failed
	 * @param ex The exception thrown on the QID sync to store error
	 */
	protected void handleSyncQueueException(long syncQueuePk, Exception ex) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			conn = dataSource.getConnection();
			ps = conn.prepareStatement("UPDATE SYNC_QUEUE SET LAST_SYNC_ATTEMPT = ?,  LAST_SYNC_ERROR = ? WHERE QID = ?");
			ps.setTimestamp(1, new Timestamp(SystemClock.time()));
			ps.setClob(2, getExceptionReader(ex));
			ps.setLong(3, syncQueuePk);
			int result = ps.executeUpdate();
			if(result==0) {
				log.warn("No rows updated for SyncQueue item [{}]", syncQueuePk);
			} else {
				log.debug("Updated SyncQueue item [{}]", syncQueuePk);
			}
			conn.commit();
		} catch (Exception dex) {
			throw new RuntimeException("Failed to update SyncQueueEntry with PK [" + syncQueuePk + "]", dex);
		} finally {
			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
		}				
	}
	
	/**
	 * Returns a reader that will read the content of the passed exception.
	 * Used to write the exception to a DB CLOB.
	 * @param ex The exception to create the reader for
	 * @return The created reader
	 * @throws IOException thrown on any stream error
	 */
	protected Reader getExceptionReader(Exception ex) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024); 
		ex.printStackTrace(new PrintStream(baos));
		baos.flush();
		return new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()));
	}
	
	/**
	 * Deletes a SyncQueue entry
	 * @param pk The pk of the SyncQueue entry
	 */
	protected void deleteSyncQueueEntry(long pk) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			conn = dataSource.getConnection();
			ps = conn.prepareStatement("DELETE FROM SYNC_QUEUE WHERE QID = ?");
			ps.setLong(1, pk);
			int result = ps.executeUpdate();
			if(result==0) {
				log.debug("No rows deleted for SyncQueue item [{}]", pk);
			} else {
				log.debug("Deleted SyncQueue item [{}]", pk);
			}
			conn.commit();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to delete SyncQueueEntry with PK [" + pk + "]", ex);
		} finally {
			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
		}		
	}
	
//	protected Annotation getAnnotation(Object pk) {
//		Connection conn = null;
//		PreparedStatement ps = null;
//		try {
//			conn = dataSource.getConnection();
//		} catch (Exception ex) {
//			throw new RuntimeException("Failed to get annotation for PK [" + pk + "]", ex);
//		} finally {
//			if(rset!=null) try { rset.close(); } catch (Exception x) {/* No Op */}
//			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
//			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
//		}
//	}

	
}
