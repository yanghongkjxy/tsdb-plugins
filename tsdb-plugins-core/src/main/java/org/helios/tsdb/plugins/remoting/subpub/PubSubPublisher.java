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
package org.helios.tsdb.plugins.remoting.subpub;

import org.helios.jmx.metrics.ewma.ConcurrentDirectEWMA;
import org.helios.tsdb.plugins.event.TSDBEvent;
import org.helios.tsdb.plugins.event.TSDBEventType;
import org.helios.tsdb.plugins.handlers.EmptyPublishEventHandler;
import org.helios.tsdb.plugins.service.IPluginContextResourceFilter;
import org.helios.tsdb.plugins.service.IPluginContextResourceListener;
import org.helios.tsdb.plugins.service.PluginContext;

import reactor.core.composable.Promise;
import reactor.function.Consumer;


/**
 * <p>Title: PubSubPublisher</p>
 * <p>Description: RT Publisher event handler to stream incoming events to the SubscriptionManager.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.tsdb.plugins.remoting.subpub.PubSubPublisher</code></p>
 */

public class PubSubPublisher extends EmptyPublishEventHandler implements PubSubPublisherMXBean {
	/** The sub manager to push events to */
	protected SubscriptionManager subManager = null;
	/** A EWMA for measuring the elapsed time of processing an event */
	protected final ConcurrentDirectEWMA ewma = new ConcurrentDirectEWMA(1024);

	
	
	/**
	 * Creates a new PubSubPublisher
	 */
	public PubSubPublisher() {
		log.info("Created PubSubPublisher Instance");
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.handlers.AbstractTSDBEventHandler#initialize(org.helios.tsdb.plugins.service.PluginContext)
	 */
	@Override
	public void initialize(final PluginContext pc) {
		final PubSubPublisher rez = this;
		pc.addResourceListener(new IPluginContextResourceListener() {
			public void onResourceRegistered(String name, Object resource) {
				subManager = (SubscriptionManager)resource;
				pc.setResource(PubSubPublisher.class.getSimpleName(), rez);
				log.info("PubSubPublisher ready for business");
			}
		}, new IPluginContextResourceFilter() {
			public boolean include(String name, Object resource) {				
				return (resource!=null && (resource instanceof SubscriptionManager));
			}
		});
		super.initialize(pc);				
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.handlers.EmptyPublishEventHandler#onEvent(org.helios.tsdb.plugins.event.TSDBEvent, long, boolean)
	 */
	@Override
	public void onEvent(TSDBEvent event, long sequence, boolean endOfBatch) throws Exception {
		if(log.isTraceEnabled()) log.trace("Processing Sequence {} for Event [{}]", sequence, event);
		if(event.eventType==null || !event.eventType.isForPulisher()) return;
		final long startTime = System.nanoTime();
		try {
			subManager.onDataPoint(
					event.metric, event.timestamp, event.longValue, 
					(event.eventType==TSDBEventType.DPOINT_LONG), 
					event.tags, event.tsuidBytes)
					.onComplete(new Consumer<Promise<Void>>() {
						@Override
						public void accept(Promise<Void> t) {
							ewma.append(System.nanoTime() - startTime);
						}
					});
		} catch (Exception ex) {
			log.error("Failed to process event [{}]", ex);
			ewma.error();
		}
	}	
	

	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getLastSample()
	 */
	@Override
	public long getLastSample() {
		return ewma.getLastSample();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getLastValue()
	 */
	@Override
	public double getLastValue() {
		return ewma.getLastValue();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#resetEWMA()
	 */
	@Override
	public void resetEWMA() {
		ewma.reset();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getErrors()
	 */
	@Override
	public long getErrors() {
		return ewma.getErrors();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getAverage()
	 */
	@Override
	public double getAverage() {
		return ewma.getAverage();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getCount()
	 */
	@Override
	public long getCount() {
		return ewma.getCount();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getMaximum()
	 */
	@Override
	public double getMaximum() {
		return ewma.getMaximum();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getMean()
	 */
	@Override
	public double getMean() {
		return ewma.getMean();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getMinimum()
	 */
	@Override
	public double getMinimum() {
		return ewma.getMinimum();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.PubSubPublisherMXBean#getWindow()
	 */
	@Override
	public long getWindow() {
		return ewma.getWindow();
	}



}
