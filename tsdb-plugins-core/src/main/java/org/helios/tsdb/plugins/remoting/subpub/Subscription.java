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

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import net.opentsdb.meta.TSMeta;
import net.opentsdb.meta.api.MetricsMetaAPI;
import net.opentsdb.uid.UniqueId;

import org.cliffc.high_scale_lib.NonBlockingHashSet;
import org.hbase.async.jsr166e.LongAdder;
import org.helios.jmx.metrics.ewma.ConcurrentDirectEWMA;
import org.helios.tsdb.plugins.async.SingletonEnvironment;
import org.helios.tsdb.plugins.event.TSDBEvent;
import org.helios.tsdb.plugins.util.JMXHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Reactor;
import reactor.core.composable.Deferred;
import reactor.core.composable.Promise;
import reactor.core.composable.Stream;
import reactor.core.composable.spec.Promises;
import reactor.core.composable.spec.Streams;
import reactor.event.Event;
import reactor.event.dispatch.Dispatcher;
import reactor.event.registry.Registration;
import reactor.event.selector.HeaderResolver;
import reactor.event.selector.Selector;
import reactor.function.Consumer;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.PrimitiveSink;


/**
 * <p>Title: Subscription</p>
 * <p>Description: Represents a SubscriptionManager subscription</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.tsdb.plugins.remoting.subpub.Subscription</code></p>
 */
public class Subscription implements SubscriptionMBean, Selector, Consumer<Event<TSDBEvent>>, SubscriberEventListener {
	/** Static class logger */
	protected static final Logger log = LoggerFactory.getLogger(Subscription.class);

	/** The filter to quickly determine if an incoming message matches this subscription */
	private BloomFilter<byte[]> filter; 
	/** The charset of the incoming messages */
	public static final Charset DEFAULT_CHARSET = Charset.defaultCharset();
	/** The default number of insertions */
	public static final int DEFAULT_INSERTIONS = 10000;
	/** The ingestion funnel for incoming qualified messages */
	public enum SubFunnel implements Funnel<byte[]> {
	     /** The singleton funnel */
	    INSTANCE;	     
	     private final Funnel<byte[]> myfunnel = Funnels.byteArrayFunnel();

		@Override
		public void funnel(byte[] from, PrimitiveSink into) {
			myfunnel.funnel(from, into);
		}
	   }	
	
	/** The subscribers receiving notifications from this subscription */
	private final NonBlockingHashSet<Subscriber> subscribers = new NonBlockingHashSet<Subscriber>(); 
	
	/** A serial number sequence for Subscription instances */
	private static final AtomicLong serial = new AtomicLong();
	
	/** A EWMA for measuring the elapsed time of isMemberOf */
	protected final ConcurrentDirectEWMA ewma = new ConcurrentDirectEWMA(1024);
	/** The reactor  */
	protected final Reactor reactor;
	
	/** The reactor async dispatcher */
	protected final Dispatcher dispatcher;
	/** The metrics meta access service */
	protected final MetricsMetaAPI metricsMeta;
	
	protected Registration<Consumer<Event<TSDBEvent>>>  registration;
	
	/** The subscription pattern */
	protected final String pattern;
	/** The subscription pattern as an ObjectName */
	protected final ObjectName patternObjectName;
	/** The subscription id for this subscription */
	protected final long subscriptionId;
	/** The initial expected insertions for the bloom filter */
	protected final int expectedInsertions;
	
	/** The total number of matched incoming messages */
	protected final LongAdder totalMatched = new LongAdder();
	/** The total number of bloom filter "might" failures */
	protected final LongAdder mightDropped = new LongAdder();
	
	/** The current number of retained (inserted) patterns */
	protected final AtomicInteger retained = new AtomicInteger();
	
	/** The default false positive probability */
	public static final double DEFAULT_PROB = 0.3d;
	
	/**
	 * Creates a new Subscription
	 * @param reactor The reactor for event listening and async dispatch
	 * @param metricsMeta The metrics meta access service
	 * @param pattern The subscription pattern
	 * @param expectedInsertions The number of expected insertions
	 */
	public Subscription(final Reactor reactor, final MetricsMetaAPI metricsMeta, final CharSequence pattern, final int expectedInsertions) {
		filter = BloomFilter.create(SubFunnel.INSTANCE, expectedInsertions, DEFAULT_PROB);
		this.pattern = pattern.toString().trim();
		this.expectedInsertions = expectedInsertions;
		this.reactor = reactor;
		this.dispatcher = reactor.getDispatcher();
		this.metricsMeta = metricsMeta;
		this.patternObjectName = JMXHelper.objectName(pattern);
		subscriptionId = serial.incrementAndGet();		
		final Stream<Event<TSDBEvent>> batchStream =  Streams.<Event<TSDBEvent>>defer(SingletonEnvironment.getEnvironment(), this.dispatcher).compose();
//		final Stream<Event<TSDBEvent>> batchStream =  new Stream<Event<TSDBEvent>>(null, 100, this, SingletonEnvironment.getEnvironment());
		batchStream.consume(this);
		registration = this.reactor.on(this, batchStream);
		
				
//				new MovingWindowAction<Event<TSDBEvent>>(reactor,
//                d.getAcceptKey(),
//                d.getError().getObject(),
//                timer,
//                period, timeUnit, delay, backlog)).connectErrors(d););
	}
	
	
	/**
	 * Creates a new Subscription
	 * @param reactor The reactor for event listening and async dispatch
	 * @param metricsMeta The metrics meta access service
	 * @param pattern The subscription pattern
	 * @param expectedInsertions The number of expected insertions
	 */
	public Subscription(final Reactor reactor, final MetricsMetaAPI metricsMeta, final ObjectName pattern, final int expectedInsertions) {
		this(reactor, metricsMeta, pattern.toString(), expectedInsertions);
	}
	
	
	/**
	 * Terminates this subscription
	 */
	public void terminate() {
		registration.cancel();
		subscribers.clear();
	}
	
	/**
	 * Adds a subscriber to this subscription
	 * @param sub The subscriber to add
	 * @return the new total number of subscribers
	 */
	public int addSubscriber(final Subscriber sub) {
		if(sub==null) throw new IllegalArgumentException("The passed subscriber was null");
		subscribers.add(sub);
		sub.registerListener(this);			
		return subscribers.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriberEventListener#onDisconnect(org.helios.tsdb.plugins.remoting.subpub.Subscriber)
	 */
	@Override
	public void onDisconnect(Subscriber subscriber) {
		// TODO Auto-generated method stub
		
	}
	
	
	/**
	 * Removes a subscriber
	 * @param sub The subscriber to remove
	 * @return the new total number of subscribers
	 */
	public int removeSubscriber(final Subscriber sub) {
		if(sub==null) throw new IllegalArgumentException("The passed subscriber was null");
		subscribers.remove(sub);
		return subscribers.size();
	}
	
	
	/**
	 * Creates a new Subscription with the default expected insertions
	 * @param reactor The reactor for event listening and async dispatch
	 * @param metricsMeta The metrics meta access service
	 * @param pattern The subscription pattern
	 */
	public Subscription(final Reactor reactor, final MetricsMetaAPI metricsMeta, ObjectName pattern) {
		this(reactor, metricsMeta, pattern, DEFAULT_INSERTIONS);
	}
	
	/**
	 * Creates a new Subscription with the default expected insertions
	 * @param reactor The reactor for event listening and async dispatch
	 * @param metricsMeta The metrics meta access service
	 * @param pattern The subscription pattern
	 */
	public Subscription(final Reactor reactor, final MetricsMetaAPI metricsMeta, CharSequence pattern) {
		this(reactor, metricsMeta, pattern, DEFAULT_INSERTIONS);
	}
	
	/**
	 * {@inheritDoc}
	 * @see reactor.function.Consumer#accept(java.lang.Object)
	 */
	@Override
	public void accept(final Event<TSDBEvent> event) {
		if(!subscribers.isEmpty()) {
			dispatcher.execute(new Runnable(){
				public void run() {
					for(final Subscriber s: subscribers) {
						s.accept(event.getData());
					}
				}
			});
		}
	}
	
		
	
	/**
	 * Indicates if the passed message is a member of this subscription
	 * @param bytes The message to determine the membership of
	 * @param metric The metric name
	 * @param tags The metric tags
	 * @return false if the message is definitely NOT a member of this subscription, true if it is a member.
	 */
	public Promise<Boolean> isMemberOf(final byte[] bytes, final String metric, final Map<String, String> tags) {
		if(bytes==null) return Promises.success(false).get();
		final Deferred<Boolean, Promise<Boolean>> def = Promises.<Boolean>defer().dispatcher(this.dispatcher).get();
		final Promise<Boolean> promise = def.compose();
		dispatcher.execute(new Runnable() {
			public void run() {
				final long start = System.nanoTime();
				if(filter.mightContain(bytes)) {
					try {
						if(metric!=null && tags!=null) {
//							final String fqn = SubscriptionManager.buildObjectName(metric, tags).toString();							
							metricsMeta.match(pattern, bytes).consume(new Consumer<Boolean>() {
								@Override
								public void accept(final Boolean t) {
									final boolean b = t==null ? false : t;
									def.accept(b);
									if(b) {
										index(bytes);
										totalMatched.increment();
									} else {
										mightDropped.increment();
									}
									ewma.append(System.nanoTime() - start);
								}
							}).when(Throwable.class, new Consumer<Throwable>() {
								@Override
								public void accept(final Throwable t) {
									def.accept(t);
									ewma.append(System.nanoTime() - start);
								}
							});
						} else {
							def.accept(false);
						}						
					} catch (Exception ex) {
						def.accept(ex);
					}					
				} else {
					def.accept(false);
					ewma.append(System.nanoTime() - start);
				}
			}
		});
		return promise;		
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#test(java.lang.String)
	 */
	@Override
	public boolean test(String message) {
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		isMemberOf(message.getBytes(Charset.defaultCharset()), null, null).consume(new Consumer<Boolean>() {
			@Override
			public void accept(Boolean t) {
				result.set(t);
				latch.countDown();
			}
		}).when(Throwable.class, new Consumer<Throwable>() {
			@Override
			public void accept(Throwable t) {
				result.set(false);
				latch.countDown();				
			}
		});
		try {
			latch.await(3, TimeUnit.SECONDS);
		} catch (Exception ex) {
			/* No Op */
		}
		return result.get();
		
	}
	
	/**
	 * Indexes the passed message after it has been determined to be a member of this subscription 
	 * @param bytes the message to index
	 */
	public void index(final byte[] bytes) {
		if(bytes!=null) {
			if(filter.put(bytes)) {
				retained.incrementAndGet();
			}
		}
	}

	/**
	 * Indexes a time series id
	 * @param tsMeta the time series
	 */
	public void index(final TSMeta tsMeta) {		
		if(tsMeta!=null) {
			if(filter.put(UniqueId.stringToUid(tsMeta.getTSUID()))) {
				retained.incrementAndGet();
			}
		}		
	}
	

	/**
	 * Indexes a time series id. Internal version that does not trigger stats or pubs.
	 * @param tsuid the time series TSUID bytes
	 */
	void _internalIndex(final byte[] tsuid) {		
		if(tsuid!=null) {
			if(filter.put(tsuid)) {
				retained.incrementAndGet();
			}
		}		
	}

	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getMatches()
	 */
	@Override
	public long getMatches() {
		return totalMatched.longValue();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getDrops()
	 */
	@Override
	public long getDrops() {
		return mightDropped.longValue();
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getSize()
	 */
	@Override
	public int getSize() {
		return retained.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getErrorProbability()
	 */
	public double getErrorProbability() {
		return filter.expectedFpp();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getRelativeProbability()
	 */
	@Override
	public double getRelativeProbability() {
		return DEFAULT_PROB - filter.expectedFpp();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getSubscriberCount()
	 */
	@Override
	public int getSubscriberCount() {
		return subscribers.size();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getSubscriptionId()
	 */
	@Override
	public long getSubscriptionId() {
		return subscriptionId;
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getDescription()
	 */
	@Override
	public String getDescription() {
		return toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getMeanMatch()
	 */
	@Override
	public double getMeanMatch() {
		return ewma.getMean();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getAverageMatch()
	 */
	@Override
	public double getAverageMatch() {
		return ewma.getAverage();
	}
	

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Subscription [Id:");
		builder.append(subscriptionId);
		builder.append(", pattern:");
		builder.append(pattern);
		builder.append(", size:");
		builder.append(retained.get());
		builder.append(", subscribers:");
		builder.append(subscribers.size());
		builder.append("]");
		return builder.toString();
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ (int) (subscriptionId ^ (subscriptionId >>> 32));
		return result;
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Subscription other = (Subscription) obj;
		if (subscriptionId != other.subscriptionId)
			return false;
		return true;
	}


	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.remoting.subpub.SubscriptionMBean#getCapacity()
	 */
	@Override
	public int getCapacity() {
		return expectedInsertions;
	}

	/**
	 * {@inheritDoc}
	 * @see reactor.event.selector.Selector#getObject()
	 */
	@Override
	public Object getObject() {
		return pattern;
	}

	/**
	 * {@inheritDoc}
	 * @see reactor.event.selector.Selector#matches(java.lang.Object)
	 */
	@Override
	public boolean matches(Object key) {
		if(key==null) return false;
		final String keyPattern = key.toString().trim();
		if(pattern.equals(keyPattern)) return true;		
		try {
			JMXHelper.objectName(keyPattern);
		} catch (Exception ex) {
			return false;
		}
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean match = new AtomicBoolean(false);
		final long startTime = System.nanoTime();
		try {
			metricsMeta.overlap(pattern, keyPattern).onSuccess(new Consumer<Long>(){
				/**
				 * {@inheritDoc}
				 * @see reactor.function.Consumer#accept(java.lang.Object)
				 */
				@Override
				public void accept(Long t) {					
					if(t!=null && t==0) {
						match.set(true);
						latch.countDown();
					}
				}
			}).onError(new Consumer<Throwable>(){
				/**
				 * {@inheritDoc}
				 * @see reactor.function.Consumer#accept(java.lang.Object)
				 */
				@Override
				public void accept(Throwable t) {
					latch.countDown();					
				}
			});
			if(!latch.await(1500, TimeUnit.MILLISECONDS)) {
				throw new Exception("Timeout on overlap call");
			}
		} catch (Exception ex) {
			throw new RuntimeException("Failed to determine match for [" + key + "] on selector [" + pattern + "]", ex);
		}
		final long elapsedTime = System.nanoTime()-startTime;
		log.info("Selector [{}] match in [{}] ms. Result: {}", pattern, elapsedTime, match.get());
		return match.get();
	}

	/**
	 * Returns null.
	 * {@inheritDoc}
	 * @see reactor.event.selector.Selector#getHeaderResolver()
	 */
	@Override
	public HeaderResolver getHeaderResolver() {
		return null;
	}





	
	
	
	
}
