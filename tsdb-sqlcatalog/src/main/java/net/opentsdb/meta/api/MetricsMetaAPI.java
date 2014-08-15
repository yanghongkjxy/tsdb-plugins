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
package net.opentsdb.meta.api;

import java.util.Map;
import java.util.Set;

import net.opentsdb.meta.TSMeta;
import net.opentsdb.meta.UIDMeta;

import com.stumbleupon.async.Deferred;

/**
 * <p>Title: MetricsMetaAPI</p>
 * <p>Description: Defines a proposed OpenTSDB metrics meta-data access API</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>net.opentsdb.meta.api.MetricsMetaAPI</code></p>
 * <p>NOTES: <ul>
 * 	<li>Read Only</li>
 * 	<li>No meta requiring extended reads, such as {@link net.opentsdb.meta.api.TSMeta#getLastReceived()} or {@link net.opentsdb.meta.api.TSMeta#getTotalDatapoints()}</li>
 * </ul></p>
 */

public interface MetricsMetaAPI {
	

	/**
	 * Returns the tag keys associated with the passed metric name.
	 * Wildcards will be honoured on metric names and tag keys.
	 * @param queryOptions The query options for this call
	 * @param metric The metric name to match
	 * @param tagKeys The tag keys to match
	 * @return A deferred set of matching UIDMetas
	 */
	public Deferred<Set<UIDMeta>> getTagKeys(QueryContext queryOptions, String metric, String...tagKeys);
	
	/**
	 * <p>Returns the tag values associated with the passed metric name and tag keys.</p>
	 * <p>The combined metric name and tag keys may not resolve to any directly associated tag values 
	 * due to missing intermediary tag keys, or they may resolve partially to some tag values.
	 * In other words, the resolution of a metric name and tag keys may produce tree leafs, tree nodes,
	 * a combination of both, or zero of either.</p>
	 * <p>Accordingly, the returned value is a 2 sized array of {@link UIDMeta} sets.
	 * The two sets indicate the located tree leafs (tag values) and nodes (tag keys) at the 
	 * resolved location. Position zero contains the resolved tag values and position one
	 * contains the resolved tag keys.</p>
	 * position zero is the resolved tag values at the tree position 
	 * Wildcards will be honoured on metric names and tag keys.
	 * @param queryOptions The query options for this call
	 * @param metric The metric name to match
	 * @param tagKeys The tag keys to match
	 * @return A deferred 2 sized array containing sets of : <ul>
	 * 	<li><b>Index 0</b>: matching tag value UIDMetas</li>
	 *  <li><b>Index 1</b>: matching tag key UIDMetas</li>
	 *  </ul>
	 */
	public Deferred<Set<UIDMeta>> getTagValues(QueryContext queryOptions, String metric, Map<String, String> tagPairs, String tagKey);

	
	/**
	 * Returns the associated metric names (metric UIDs) for the passed tag keys.
	 * Wildcards will be honoured on tag keys.
	 * @param queryOptions The query options for this call
	 * @param tagKeys The tag keys to match
	 * @return A deferred set of matching UIDMetas
	 */
	public Deferred<Set<UIDMeta>> getMetricNames(QueryContext queryOptions, String...tagKeys);
	
	/**
	 * Returns the associated metric names (metric UIDs) for the passed tag pairs.
	 * Wildcards will be honoured on metric names and tag keys.
	 * @param queryOptions The query options for this call
	 * @param tags The tag pairs to match
	 * @return A deferred set of matching UIDMetas
	 */
	public Deferred<Set<UIDMeta>> getMetricNames(QueryContext queryOptions, Map<String, String> tags);
	
	/**
	 * Returns the TSMetas matching the passed metric name and tags
	 * @param queryOptions The query options for this call
	 * @param overflow If true, TSMetas that are a partial match will be included, otherwise only exact matches will be returned
	 * but which have additional tags beyond the supplied ones, false to return exact matches only 
	 * @param metricName The metric name to match
	 * @param tags The tag pairs to match
	 * @return A deferred set of matching TSMetas 
	 */
	public Deferred<Set<TSMeta>> getTSMetas(QueryContext queryOptions, boolean overflow, String metricName, Map<String, String> tags);
	
	

	
	/**
	 * Evaluates the passed TSUIDEXPR expression and returns the matches.
	 * Wildcards will be honoured on metric names, tag keys and tag values.
	 * @param expressions The TSUIDEXPR expressions to evaluate
	 * @param queryOptions The query options for this call
	 * @return the result object in the format specified
	 */
	public Deferred<Set<TSMeta>> evaluate(QueryContext queryOptions, String...expressions);
	
}
