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
 * License adouble with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package org.helios.tsdb.plugins.test.containers;

import java.util.Map;

import net.opentsdb.core.TSDB;

/**
 * <p>Title: DoubleDataPoint</p>
 * <p>Description: A double value data point</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.tsdb.plugins.test.containers.DoubleDataPoint</code></p>
 */

public class DoubleDataPoint extends DataPoint {
	/** The double value */
	public final double value;
	/**
	 * Creates a new DoubleDataPoint
	 * @param value The double value for this datapoint
	 * @param metricName The metric name, a non-empty string.
	 * @param tags The metric tags. Must be non empty
	 * @param timestamp  The timestamp associated with the value.
	 */
	public DoubleDataPoint(double value, String metricName, Map<String, String> tags, long timestamp) {
		super(metricName, tags, timestamp);
		this.value = value;
	}

	/**
	 * Creates a new DoubleDataPoint
	 * @param value The double value for this datapoint
	 * @param metricName The metric name, a non-empty string.
	 * @param tags The metric tags. Must be non empty
	 */
	public DoubleDataPoint(double value, String metricName, Map<String, String> tags) {
		super(metricName, tags);
		this.value = value;
	}

	/**
	 * Creates a new DoubleDataPoint
	 * @param value The double value for this datapoint
	 * @param metricName The metric name, a non-empty string.
	 * @param tags The metric tags. Must be a non empty, even numbered number of non empty strings
	 */
	public DoubleDataPoint(double value, String metricName, String... tags) {
		super(metricName, tags);
		this.value = value;
	}

	/**
	 * Publishes this datapoint the passed TSDB
	 * @param tsdb the TSDB to publish to
	 */
	public void publish(TSDB tsdb) {
		tsdb.addPoint(metricName, timestamp, value, tags);
	}

}