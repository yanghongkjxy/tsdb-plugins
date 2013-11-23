package net.opentsdb.catalog.h2.triggers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;

import net.opentsdb.catalog.h2.H2Support;

import org.helios.tsdb.plugins.util.SystemClock;
import org.helios.tsdb.plugins.util.SystemClock.ElapsedTime;

/**
 * <p>Title: AnnotationTrigger</p>
 * <p>Description: Trigger for Annotation table to capture deferred sync operations</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>net.opentsdb.catalog.h2.triggers.AnnotationTrigger</code></p>
 */
public class AnnotationTrigger extends AbstractSyncQueueTrigger {


	/**
	 * {@inheritDoc}
	 * @see org.h2.api.Trigger#fire(java.sql.Connection, java.lang.Object[], java.lang.Object[])
	 */
	@Override
	public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
		ElapsedTime et = SystemClock.startClock();
		try {
			if(isSQProcessor(conn)) return;
			callCount.incrementAndGet();
			final boolean EQ = isEQProcessor(conn);
			
			if(oldRow==null) {			
				// ======  INSERT  ======
				if(!EQ) {
					incrementVersion(newRow);
					incrementVersion(7, newRow);				
				}
				addSyncQueueEvent(conn, tableName, "I", newRow[0].toString());
			} else if(newRow==null) {
				// ======  DELETE  ======
				addSyncQueueEvent(conn, tableName, "D", String.format("%s:%s", ((Timestamp)oldRow[2]).getTime(), oldRow[5]==null ? "" : H2Support.tsuid(conn, (Long)oldRow[5])));
			} else {
				// ======  UPDATE  ======
				if(Arrays.deepEquals(oldRow, newRow)) return;			
				if(!EQ) {
					incrementVersion(newRow);
					incrementVersion(7, newRow);
				}
				addSyncQueueEvent(conn, tableName, "U", newRow[0].toString());			
			}
		} finally {
			elapsedTimes.insert(et.elapsed());
		}

	}

}
