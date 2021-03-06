package org.helios.tsdb.plugins;

import java.lang.management.ManagementFactory;

import org.helios.tsdb.plugins.async.EventBusEventDispatcher;
import org.helios.tsdb.plugins.event.TSDBEventDispatcher;

import com.lmax.disruptor.SleepingWaitStrategy;
import com.stumbleupon.async.Deferred;

/**
 * <p>Title: Constants</p>
 * <p>Description: TSDB-Plugins Common Constants</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.tsdb.plugins.Constants</code></p>
 * <p>Native (i.e. built in OpenTSDB) configuration items are prefixed with <b><code>tsd.</code></b>.
 * When the {@link org.helios.tsdb.plugins.util.ConfigurationHelper} loads values for these config items, they can only come from 
 * the {@link net.opentsdb.core.TSDB} supplied configuration or the designated default. i.e. the should <i>not</i> be derrived
 * from system properties or environmental variables, since they would be out of sync with the actual TSDB instance.</p>
 * <p>Non native configuration properties should be written back into the {@link net.opentsdb.core.TSDB} config so that
 * their resolved values are visible through the config API</p>. 
 */
public class Constants {

	/** Config property name for unsafe mem tracking */
	public static final String TRACK_MEM_PROP = "org.helios.tsdb.trackmem";
	/** The default unsafe mem tracking */
	public static final boolean DEFAULT_TRACK_MEM = false;
	
	/** The default plugin version to publish */
	public static final String PLUGIN_VERSION = "2.0.1";
	
	/** The number of processors available to the JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** A null deferred response const */
	public static final Deferred<Object> NULL_DEFERED = Deferred.fromResult(null);
	
	
	/** The TSDB config for enabling new/edited TSMeta through Tree rule sets */
	public static final String CONFIG_ENABLE_TREE = "tsd.core.tree.enable_processing";
	
	/** The config property name for disabling background polling of plugin stats  */
	public static final String PLUGIN_ENABLE_STATS_POLLING = "tsd.plugins.disableStatsCollect";
	/** The default disabling background polling of plugin stats  */
	public static final boolean DEFAULT_PLUGIN_ENABLE_STATS_POLLING = false;
	
	// ===========================================================================================	
	//		ITSDBPluginService
	// ===========================================================================================
	
	/** The config property name for the class name of the ITSDBPluginService to load */
	public static final String PLUGIN_SERVICE_CLASS_NAME = "helios.tsd.pluginservice";
	/** The default class name of the ITSDBPluginService to load */
	public static final String DEFAULT_PLUGIN_SERVICE_CLASS_NAME = TSDBEventDispatcher.class.getName();


	// ===========================================================================================	
	//		Native TSD General Plugin Config
	// ===========================================================================================
	
	/** The TSDB config for the plugin jar config path */
	public static final String CONFIG_PLUGIN_PATH = "tsd.core.plugin_path";

	
	// ===========================================================================================	
	//		tsdb-plugins General Plugin Config
	// ===========================================================================================
	/** The config property name for the class name of the event dispatcher asynch dispatcher */
	public static final String ASYNC_DISPATCHER = "helios.events.async.dispatcher";
	/** The default name for the class name of the event dispatcher asynch dispatcher */
	public static final String DEFAULT_ASYNC_DISPATCHER = EventBusEventDispatcher.class.getName();
	/** The tsdb-plugins config for the plugin support jar config path */
	public static final String CONFIG_PLUGIN_SUPPORT_PATH = "helios.plugin_support_path";
	/** The config property name for the class names of event handlers to register */
	public static final String EVENT_HANDLERS = "helios.events.handlers";


	// ===========================================================================================	
	//		Search Plugin Config
	// ===========================================================================================
	
	/** The TSDB config for enabling search */
	public static final String CONFIG_ENABLE_SEARCH = "tsd.search.enable";
	/** The TSDB config for specifying the search plugin */
	public static final String CONFIG_SEARCH_PLUGIN = "tsd.search.plugin";
	/** The TSDB config for specifying the search plugin.  TODO:  Is this a real property ? */
	public static final String CONFIG_ENABLE_TRACKING = "tsd.core.meta.enable_tracking";
	/** The TSDB config for enabling realtime meta processing */  // REQUIRED FOR REALTIME CATALOG INDEXING
	public static final String CONFIG_ENABLE_REALTIME = "tsd.core.meta.enable_realtime_uid";

	// ===========================================================================================	
	//		Publish Plugin Config
	// ===========================================================================================
	
	/** The TSDB config for enabling publishing */
	public static final String CONFIG_ENABLE_PUBLISH = "tsd.rtpublisher.enable";
	/** The TSDB config for specifying the publish plugin */
	public static final String CONFIG_PUBLISH_PLUGIN = "tsd.rtpublisher.plugin";
	/** The TSDBPluginService config for specifying publisher service class names */
	public static final String CONFIG_PUBLISH_SERVICES = "helios.tsd.publish.services";
	
	// ===========================================================================================	
	//		RPC Plugin Config
	// ===========================================================================================

	/** The TSDB config for specifying RPC plugin class names */
	public static final String CONFIG_RPC_PLUGINS = "tsd.rpc.plugins";
	/** The TSDBPluginService config for specifying RPC service class names */
	public static final String CONFIG_RPC_SERVICES = "helios.tsd.rpc.services";
	
	// org.helios.tsdb.plugins.event.TSDBEventDispatcher
	
	
	
	// *******************************************************************************************
	//       Event Handler Config
	// *******************************************************************************************

	
	// ===========================================================================================	
	//		Event Handler Executor Config
	// ===========================================================================================
	/** The config property name for the async dispatcher core pool size */
	public static final String ASYNC_CORE_SIZE = "helios.events.async.core";
	/** The default async dispatcher core pool size */
	public static final int DEFAULT_ASYNC_CORE_SIZE = Constants.CORES;
	/** The config property name for the async dispatcher max pool size */
	public static final String ASYNC_MAX_SIZE = "helios.events.async.max";
	/** The default async dispatcher max pool size */
	public static final int DEFAULT_ASYNC_MAX_SIZE = Constants.CORES * 2;
	/** The config property name for the async dispatcher keep alive time in ms. */
	public static final String ASYNC_KEEPALIVE_TIME = "helios.events.async.keepalive";
	/** The default async dispatcher keep alive time in ms. */
	public static final long DEFAULT_ASYNC_KEEPALIVE_TIME = 60000;
	/** The config property name for the async dispatcher work queue size */
	public static final String ASYNC_QUEUE_SIZE = "helios.events.async.queuesize";
	/** The default async dispatcher work queue size */
	public static final int DEFAULT_ASYNC_QUEUE_SIZE = 10240;
	
	/** The config property name for the thread pool name */
	public static final String ASYNC_EXECUTOR_NAME = "helios.events.async.name";
	/** The default async dispatcher work queue size */
	public static final String DEFAULT_ASYNC_EXECUTOR_NAME = "TSDBPluginAsyncDispatcher";
	// ===========================================================================================	
	//		Disruptor AsyncHandler Config
	// ===========================================================================================
	/** The config property name for the number of slots in the ring buffer. Must be a power of 2 */
	public static final String RING_BUFFER_SIZE = "helios.events.async.disruptor.ringsize";
	/** The default number of slots in the ring buffer */
	public static final int DEFAULT_RING_BUFFER_SIZE = 1024;
	
	/** The config property name for the ring buffer wait strategy */
	public static final String RING_BUFFER_WAIT_STRAT = "helios.events.async.disruptor.waitstrat";
	/** The default number of slots in the ring buffer */
	public static final String DEFAULT_RING_BUFFER_WAIT_STRAT = SleepingWaitStrategy.class.getSimpleName();
	/** The config property name for the ring buffer wait strategy ctor parameters */
	public static final String RING_BUFFER_WAIT_STRAT_ARGS = "helios.events.async.disruptor.waitstrat.args";
	/** The optional wait strategy class ctor parameters */
	public static final String DEFAULT_RING_BUFFER_WAIT_STRAT_ARGS = "";

	// ===========================================================================================	
	//		Netty Unified Protocol Server Config
	// ===========================================================================================
	/** The config property name for the port number the Netty listener will listen on. -1 will disable. */
	public static final String NETTY_REMOTING_PORT = "helios.netty.listen.port";
	/** The default JMXMP listener port */
	public static final int DEFAULT_NETTY_REMOTING_PORT = 4243;
	/** The config property name for the interface the Netty service listener will bind to */
	public static final String NETTY_REMOTING_INTERFACE = "helios.netty.listen.iface";
	/** The default JMXMP listener port */
	public static final String DEFAULT_NETTY_REMOTING_INTERFACE = "0.0.0.0";
	
	
	// ===========================================================================================	
	//		JMXMP JMXConnectorServer Config
	// ===========================================================================================
	/** The config property name for the port number the JMXMP listener will listen on. -1 will disable. */
	public static final String JMXMP_PORT = "helios.jmxmp.listen.port";
	/** The default JMXMP listener port */
	public static final int DEFAULT_JMXMP_PORT = 4245;
	/** The config property name for the interface the JMXMP listener will bind to */
	public static final String JMXMP_INTERFACE = "helios.jmxmp.listen.iface";
	/** The default JMXMP listener port */
	public static final String DEFAULT_JMXMP_INTERFACE = "0.0.0.0";
	
	
	
	/*
	 * tsd.core.meta.enable_realtime_uid
	 * tsd.core.meta.enable_tsuid_tracking
	 * tsd.core.meta.enable_tsuid_incrementing
	 * tsd.core.meta.enable_realtime_ts
	 */
	
	
	
	
	
	
	
	private Constants() {
	}

}
