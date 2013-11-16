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

import javax.management.MXBean;

/**
 * <p>Title: CatalogDBMXBean</p>
 * <p>Description: JMX MXBean interface for catalog db instrumentation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>net.opentsdb.catalog.CatalogDBMXBean</code></p>
 */

@MXBean
public interface CatalogDBMXBean {
	/** The metric name insert counter */
	public static final String METRIC_INSERT_CNT = "metric-insert"; 
	/** The metric name update counter */
	public static final String METRIC_UPDATE_CNT = "metric-update"; 
	/** The tag key insert counter */
	public static final String TAGK_INSERT_CNT = "tagk-insert"; 
	/** The tag key name update counter */
	public static final String TAGK_UPDATE_CNT = "tagk-update"; 
	/** The tag value insert counter */
	public static final String TAGV_INSERT_CNT = "tagv-insert"; 
	/** The tag value name update counter */
	public static final String TAGV_UPDATE_CNT = "tagv-update";
	
	/** The annotation insert counter */
	public static final String ANN_INSERT_CNT = "ann-insert"; 
	/** The annotation update counter */
	public static final String ANN_UPDATE_CNT = "ann-update";
    
	/** The tsmeta insert counter */
	public static final String TSMETA_INSERT_CNT = "tsmeta-insert"; 
	/** The tsmeta update counter */
	public static final String TSMETA_UPDATE_CNT = "tsmeta-update";
	
	
	/**
	 * Returns the cummlative number of metric name inserts
	 * @return the cummlative number of metric name inserts
	 */
	public long getMetricInsertCount();
	
	/**
	 * Returns the cummlative number of metric name updates
	 * @return the cummlative number of metric name updates
	 */
	public long getMetricUpdateCount();
	
	/**
	 * Returns the cummlative number of tag key inserts
	 * @return the cummlative number of tag key inserts
	 */
	public long getTagKInsertCount();
	
	/**
	 * Returns the cummlative number of tag key updates
	 * @return the cummlative number of tag key updates
	 */
	public long getTagKUpdateCount();
	
	/**
	 * Returns the cummlative number of tag value inserts
	 * @return the cummlative number of tag value inserts
	 */
	public long getTagVInsertCount();
	
	/**
	 * Returns the cummlative number of tag value updates
	 * @return the cummlative number of tag value updates
	 */
	public long getTagVUpdateCount();
	
	/**
	 * Returns the cummlative number of tsmeta inserts
	 * @return the cummlative number of tsmeta inserts
	 */
	public long getTSMetaInsertCount();
	
	/**
	 * Returns the cummlative number of tsmeta updates
	 * @return the cummlative number of tsmeta updates
	 */
	public long getTSMetaUpdateCount();
	
	/**
	 * Returns the cummlative number of annotation inserts
	 * @return the cummlative number of annotation inserts
	 */
	public long getAnnotationInsertCount();
	
	/**
	 * Returns the cummlative number of annotation updates
	 * @return the cummlative number of annotation updates
	 */
	public long getAnnotationUpdateCount();
	
	/**
	 * Returns the URL of the connected database
	 * @return the URL of the connected database
	 */
	public String getURL();

	/**
	 * Returns the username of the database user
	 * @return the username of the database user
	 */
	public String getUserName();

	/**
	 * Returns the driver name 
	 * @return the driver name 
	 */
	public String getDriverName();
	
	/**
	 * Returns the driver version
	 * @return the driver version
	 */
	public String getDriverVersion();	
	
	
	/**
	 * Retrieves the name of this database product.
	 * @return the name of this database product
	 */
	public String getDatabaseProductName();
	
	/**
	 * Retrieves the version number of this database product.
	 * @return the version number name of this database product
	 */
	public String getDatabaseProductVersion();
	
	
	

}