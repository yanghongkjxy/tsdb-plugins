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
package org.helios.tsdb.plugins.test.async;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;


import org.junit.Assert;
import net.opentsdb.core.TSDB;
import net.opentsdb.meta.Annotation;

import org.helios.tsdb.plugins.event.TSDBEventType;
import org.helios.tsdb.plugins.event.TSDBSearchEvent;
import org.helios.tsdb.plugins.handlers.impl.QueuedResultSearchEventHandler;
import org.helios.tsdb.plugins.test.BaseTest;
import org.junit.Test;

/**
 * <p>Title: BasicSearchLoggingTestCase</p>
 * <p>Description: Scratch test just to see some stuff happening through the logs</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.tsdb.plugins.test.async.BasicSearchLoggingTestCase</code></p>
 */

public class BasicSearchLoggingTestCase extends BaseTest {

	@Test(timeout=5000)
	//@Test
	public void testLoggingSetup() throws Exception {
		createSearchShellJar();
		TSDB tsdb = newTSDB("BasicSearchConfig");
		BlockingQueue<Object> events = QueuedResultSearchEventHandler.getInstance().getResultQueue();
		int eventCount = 10000;

		Map<String, Annotation> annotations = startAnnotationStream(tsdb, eventCount, 2, 0);
		
		for(int i = 0; i < eventCount; i++) {
			TSDBSearchEvent event = (TSDBSearchEvent)events.take();			
			Annotation annot = annotations.get(event.annotation.getTSUID() + "/" + event.annotation.getStartTime());
			Assert.assertEquals("[" + i + "] Unexpected event type", TSDBEventType.ANNOTATION_INDEX, event.eventType);
			Assert.assertEquals("[" + i + "] Annotation mismatch: EndTime", annot.getEndTime(), event.annotation.getEndTime());
			Assert.assertEquals("[" + i + "] Annotation mismatch: StartTime", annot.getStartTime(), event.annotation.getStartTime());
			Assert.assertEquals("[" + i + "] Annotation mismatch: Custom", annot.getCustom().toString(), event.annotation.getCustom().toString());
			Assert.assertEquals("[" + i + "] Annotation mismatch: Description", annot.getDescription(), event.annotation.getDescription());
			Assert.assertEquals("[" + i + "] Annotation mismatch: Notes", annot.getNotes(), event.annotation.getNotes());
			Assert.assertEquals("[" + i + "] Annotation mismatch: TSUID", annot.getTSUID(), event.annotation.getTSUID());
			
		}
//		
//		
//		
//		for(Annotation an: annotations.values()) {
//			tsdb.deleteAnnotation(an);
//		}
		//Thread.currentThread().join(30000);
	}

}

