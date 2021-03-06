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
package org.helios.tsdb.plugins.stats;

/**
 * <p>Title: ComponentStatsAccumulator</p>
 * <p>Description: Accepts individual named stats dropped from the owning component and provides aggregated stats flushes to the TSDB on collections.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>stats.ComponentStatsAccumulator</code></p>
 */

public class ComponentStatsAccumulator {
	/** The object logically providing the stats */
	protected final Object source;
	/** The name of the object logically providing the stats */
	protected final String name;

	/**
	 * Creates a new ComponentStatsAccumulator
	 * @param source The object logically providing the stats
	 * @param name The name of the object logically providing the stats
	 */
	public ComponentStatsAccumulator(Object source, String name) {
		this.source = source;
		this.name = name;
	}

	/**
	 * Creates a new ComponentStatsAccumulator
	 * @param source The object logically providing the stats
	 */
	public ComponentStatsAccumulator(Object source) {
		this(source, source.getClass().getSimpleName());
	}

}
