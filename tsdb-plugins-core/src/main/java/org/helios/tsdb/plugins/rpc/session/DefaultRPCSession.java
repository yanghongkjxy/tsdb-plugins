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
package org.helios.tsdb.plugins.rpc.session;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.helios.tsdb.plugins.util.SystemClock;

/**
 * <p>Title: DefaultRPCSession</p>
 * <p>Description: The default RPC session implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.tsdb.plugins.rpc.session.DefaultRPCSession</code></p>
 */

public class DefaultRPCSession implements IRPCSession {
	/** The session lifecycle control */
	protected final ISessionLifecycle sessionLifecycle;
	/** The start time of this session */
	protected final long startTime;
	/** The last accessed time by the remote */
	protected final AtomicLong lastAccessTime = new AtomicLong();
	/** The session attributes */
	protected final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();
	/** The session attributes */
	protected final Map<RPCSessionAttribute, String> sessionAttributes = new EnumMap<RPCSessionAttribute, String>(RPCSessionAttribute.class);
	
	/** A reference to the RPCSessionManager */
	protected final RPCSessionManager sessionManager;
	/**
	 * Creates a new DefaultRPCSession
	 * @param sessionLifecycle The session lifecycle
	 */
	public DefaultRPCSession(ISessionLifecycle sessionLifecycle) {
		this.sessionLifecycle = sessionLifecycle;
		startTime = SystemClock.time();
		lastAccessTime.set(startTime);
		sessionManager = RPCSessionManager.getInstance();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#isExpired()
	 */
	@Override
	public boolean isExpired() {
		return sessionLifecycle.isExpired();
	}
	

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#expire()
	 */
	@Override
	public void expire() {
		sessionLifecycle.onSessionExpire();
		sessionManager.expired(this);
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getSessionId()
	 */
	@Override
	public String getSessionId() {
		return sessionLifecycle.getSessionId();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#touch()
	 */
	@Override
	public void touch() {
		lastAccessTime.set(SystemClock.time());

	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(String name) {
		if(name==null) throw new IllegalArgumentException("The passed attribute name was null");
		return attributes.get(name);
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getAttribute(java.lang.String, java.lang.Class)
	 */
	@Override
	public <T> T getAttribute(String name, Class<T> type) {
		if(name==null) throw new IllegalArgumentException("The passed attribute name was null");
		return (T)attributes.get(name);
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#setAttribute(java.lang.String, java.lang.Object)
	 */
	@Override
	public <T> T setAttribute(String name, T value) {
		if(name==null) throw new IllegalArgumentException("The passed attribute name was null");
		if(value==null) throw new IllegalArgumentException("The passed attribute value for name [" + name + "] was null");		
		return (T)attributes.remove(name);
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#clearAttribute(java.lang.String)
	 */
	@Override
	public <T> T clearAttribute(String name) {
		if(name==null) throw new IllegalArgumentException("The passed attribute name was null");
		return (T)attributes.remove(name);
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getAttributeNames()
	 */
	@Override
	public String[] getAttributeNames() {
		Set<String> set = new HashSet<String>(attributes.keySet());
		return set.toArray(new String[set.size()]);
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getCreationTime()
	 */
	@Override
	public long getCreationTime() {
		return startTime;
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getLastAccessedTime()
	 */
	@Override
	public long getLastAccessedTime() {
		return lastAccessTime.get();
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getTimeToExpiry()
	 */
	@Override
	public int getTimeToExpiry() {
		int lat = (int)TimeUnit.SECONDS.convert(lastAccessTime.get(), TimeUnit.MILLISECONDS);
		return (int) (getMaxInactiveInterval() - (SystemClock.unixTime()-lat));
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getMaxInactiveInterval()
	 */
	@Override
	public int getMaxInactiveInterval() {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#getSessionAttributes()
	 */
	@Override
	public Map<String, String> getSessionAttributes() {
		Map<String, String> sattrs = new HashMap<String, String>(sessionAttributes.size());
		for(Map.Entry<RPCSessionAttribute, String> entry: sessionAttributes.entrySet()) {
			sattrs.put(entry.getKey().name(), entry.getValue());
		}
		return sattrs;
	}

	/**
	 * {@inheritDoc}
	 * @see org.helios.tsdb.plugins.rpc.session.IRPCSession#addSessionAttribute(org.helios.tsdb.plugins.rpc.session.RPCSessionAttribute, java.lang.String)
	 */
	@Override
	public void addSessionAttribute(RPCSessionAttribute type, String value) {
		if(type==null) throw new IllegalArgumentException("The passed type was null");
		if(value==null) throw new IllegalArgumentException("The passed value was null");
		sessionAttributes.put(type, value);		
	}

}
