/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.internal.net.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import jdk.internal.net.http.common.FlowTube;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;

/**
 * Http 1.1 connection pool.
 */
final class ConnectionPool {

    static final long KEEP_ALIVE = Utils.getIntegerNetProperty(
            "jdk.httpclient.keepalive.timeout", 1200); // seconds
    static final long MAX_POOL_SIZE = Utils.getIntegerNetProperty(
            "jdk.httpclient.connectionPoolSize", 0); // unbounded
    final Logger debug = Utils.getDebugLogger(this::dbgString, Utils.DEBUG);

    // Pools of idle connections

    private final HashMap<CacheKey,LinkedList<HttpConnection>> plainPool;
    private final HashMap<CacheKey,LinkedList<HttpConnection>> sslPool;
    private final ExpiryList expiryList;
    private final String dbgTag; // used for debug
    boolean stopped;

    /**
     * Entries in connection pool are keyed by destination address and/or
     * proxy address:
     * case 1: plain TCP not via proxy (destination IP only)
     * case 2: plain TCP via proxy (proxy only)
     * case 3: SSL not via proxy (destination IP+hostname only)
     * case 4: SSL over tunnel (destination IP+hostname and proxy)
     */
    static class CacheKey {
        final InetSocketAddress proxy;
        final InetSocketAddress destination;
        final boolean secure;

        private CacheKey(boolean secure, InetSocketAddress destination,
                         InetSocketAddress proxy) {
            this.proxy = proxy;
            this.destination = destination;
            this.secure = secure;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CacheKey other = (CacheKey) obj;
            if (this.secure != other.secure) {
                return false;
            }
            if (!Objects.equals(this.proxy, other.proxy)) {
                return false;
            }
            if (!Objects.equals(this.destination, other.destination)) {
                return false;
            }
            if (secure && destination != null) {
                String hostString = destination.getHostString();
                if (hostString == null || !hostString.equalsIgnoreCase(
                        other.destination.getHostString())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(proxy, destination);
        }
    }

    ConnectionPool(long clientId) {
        this("ConnectionPool("+clientId+")");
    }

    /**
     * There should be one of these per HttpClient.
     */
    private ConnectionPool(String tag) {
        dbgTag = tag;
        plainPool = new HashMap<>();
        sslPool = new HashMap<>();
        expiryList = new ExpiryList();
    }

    final String dbgString() {
        return dbgTag;
    }

    synchronized void start() {
        assert !stopped : "Already stopped";
    }

    static CacheKey cacheKey(boolean secure, InetSocketAddress destination,
                             InetSocketAddress proxy)
    {
        return new CacheKey(secure, destination, proxy);
    }

    synchronized HttpConnection getConnection(boolean secure,
                                              InetSocketAddress addr,
                                              InetSocketAddress proxy) {
        if (stopped) return null;
        // for plain (unsecure) proxy connection the destination address is irrelevant.
        addr = secure || proxy == null ? addr : null;
        CacheKey key = new CacheKey(secure, addr, proxy);
        HttpConnection c = secure ? findConnection(key, sslPool)
                                  : findConnection(key, plainPool);
        //System.out.println ("getConnection returning: " + c);
        assert c == null || c.isSecure() == secure;
        return c;
    }

    /**
     * Returns the connection to the pool.
     */
    void returnToPool(HttpConnection conn) {
        returnToPool(conn, Instant.now(), KEEP_ALIVE);
    }

    // Called also by whitebox tests
    void returnToPool(HttpConnection conn, Instant now, long keepAlive) {

        assert (conn instanceof PlainHttpConnection) || conn.isSecure()
            : "Attempting to return unsecure connection to SSL pool: "
                + conn.getClass();

        // Don't call registerCleanupTrigger while holding a lock,
        // but register it before the connection is added to the pool,
        // since we don't want to trigger the cleanup if the connection
        // is not in the pool.
        CleanupTrigger cleanup = registerCleanupTrigger(conn);

        // it's possible that cleanup may have been called.
        HttpConnection toClose = null;
        synchronized(this) {
            if (cleanup.isDone()) {
                return;
            } else if (stopped) {
                conn.close();
                return;
            }
            if (MAX_POOL_SIZE > 0 && expiryList.size() >= MAX_POOL_SIZE) {
                toClose = expiryList.removeOldest();
                if (toClose != null) removeFromPool(toClose);
            }
            if (conn instanceof PlainHttpConnection) {
                putConnection(conn, plainPool);
            } else {
                assert conn.isSecure();
                putConnection(conn, sslPool);
            }
            expiryList.add(conn, now, keepAlive);
        }
        if (toClose != null) {
            if (debug.on()) {
                debug.log("Maximum pool size reached: removing oldest connection %s",
                          toClose.dbgString());
            }
            close(toClose);
        }
        //System.out.println("Return to pool: " + conn);
    }

    private CleanupTrigger registerCleanupTrigger(HttpConnection conn) {
        // Connect the connection flow to a pub/sub pair that will take the
        // connection out of the pool and close it if anything happens
        // while the connection is sitting in the pool.
        CleanupTrigger cleanup = new CleanupTrigger(conn);
        FlowTube flow = conn.getConnectionFlow();
        if (debug.on()) debug.log("registering %s", cleanup);
        flow.connectFlows(cleanup, cleanup);
        return cleanup;
    }

    private HttpConnection
    findConnection(CacheKey key,
                   HashMap<CacheKey,LinkedList<HttpConnection>> pool) {
        LinkedList<HttpConnection> l = pool.get(key);
        if (l == null || l.isEmpty()) {
            return null;
        } else {
            HttpConnection c = l.removeFirst();
            expiryList.remove(c);
            return c;
        }
    }

    /* called from cache cleaner only  */
    private boolean
    removeFromPool(HttpConnection c,
                   HashMap<CacheKey,LinkedList<HttpConnection>> pool) {
        //System.out.println("cacheCleaner removing: " + c);
        assert Thread.holdsLock(this);
        CacheKey k = c.cacheKey();
        List<HttpConnection> l = pool.get(k);
        if (l == null || l.isEmpty()) {
            pool.remove(k);
            return false;
        }
        return l.remove(c);
    }

    private void
    putConnection(HttpConnection c,
                  HashMap<CacheKey,LinkedList<HttpConnection>> pool) {
        CacheKey key = c.cacheKey();
        LinkedList<HttpConnection> l = pool.get(key);
        if (l == null) {
            l = new LinkedList<>();
            pool.put(key, l);
        }
        l.add(c);
    }

    /**
     * Purge expired connection and return the number of milliseconds
     * in which the next connection is scheduled to expire.
     * If no connections are scheduled to be purged return 0.
     * @return the delay in milliseconds in which the next connection will
     *         expire.
     */
    long purgeExpiredConnectionsAndReturnNextDeadline() {
        if (!expiryList.purgeMaybeRequired()) return 0;
        return purgeExpiredConnectionsAndReturnNextDeadline(Instant.now());
    }

    // Used for whitebox testing
    long purgeExpiredConnectionsAndReturnNextDeadline(Instant now) {
        long nextPurge = 0;

        // We may be in the process of adding new elements
        // to the expiry list - but those elements will not
        // have outlast their keep alive timer yet since we're
        // just adding them.
        if (!expiryList.purgeMaybeRequired()) return nextPurge;

        List<HttpConnection> closelist;
        synchronized (this) {
            closelist = expiryList.purgeUntil(now);
            for (HttpConnection c : closelist) {
                if (c instanceof PlainHttpConnection) {
                    boolean wasPresent = removeFromPool(c, plainPool);
                    assert wasPresent;
                } else {
                    boolean wasPresent = removeFromPool(c, sslPool);
                    assert wasPresent;
                }
            }
            nextPurge = now.until(
                    expiryList.nextExpiryDeadline().orElse(now),
                    ChronoUnit.MILLIS);
        }
        closelist.forEach(this::close);
        return nextPurge;
    }

    private void close(HttpConnection c) {
        try {
            c.close();
        } catch (Throwable e) {} // ignore
    }

    void stop() {
        List<HttpConnection> closelist = Collections.emptyList();
        try {
            synchronized (this) {
                stopped = true;
                closelist = expiryList.stream()
                    .map(e -> e.connection)
                    .collect(Collectors.toList());
                expiryList.clear();
                plainPool.clear();
                sslPool.clear();
            }
        } finally {
            closelist.forEach(this::close);
        }
    }

    static final class ExpiryEntry {
        final HttpConnection connection;
        final Instant expiry; // absolute time in seconds of expiry time
        ExpiryEntry(HttpConnection connection, Instant expiry) {
            this.connection = connection;
            this.expiry = expiry;
        }
    }

    /**
     * Manages a LinkedList of sorted ExpiryEntry. The entry with the closer
     * deadline is at the tail of the list, and the entry with the farther
     * deadline is at the head. In the most common situation, new elements
     * will need to be added at the head (or close to it), and expired elements
     * will need to be purged from the tail.
     */
    private static final class ExpiryList {
        private final LinkedList<ExpiryEntry> list = new LinkedList<>();
        private volatile boolean mayContainEntries;

        int size() { return list.size(); }

        // A loosely accurate boolean whose value is computed
        // at the end of each operation performed on ExpiryList;
        // Does not require synchronizing on the ConnectionPool.
        boolean purgeMaybeRequired() {
            return mayContainEntries;
        }

        // Returns the next expiry deadline
        // should only be called while holding a synchronization
        // lock on the ConnectionPool
        Optional<Instant> nextExpiryDeadline() {
            if (list.isEmpty()) return Optional.empty();
            else return Optional.of(list.getLast().expiry);
        }

        // should only be called while holding a synchronization
        // lock on the ConnectionPool
        HttpConnection removeOldest() {
            ExpiryEntry entry = list.pollLast();
            return entry == null ? null : entry.connection;
        }

        // should only be called while holding a synchronization
        // lock on the ConnectionPool
        void add(HttpConnection conn) {
            add(conn, Instant.now(), KEEP_ALIVE);
        }

        // Used by whitebox test.
        void add(HttpConnection conn, Instant now, long keepAlive) {
            Instant then = now.truncatedTo(ChronoUnit.SECONDS)
                    .plus(keepAlive, ChronoUnit.SECONDS);

            // Elements with the farther deadline are at the head of
            // the list. It's more likely that the new element will
            // have the farthest deadline, and will need to be inserted
            // at the head of the list, so we're using an ascending
            // list iterator to find the right insertion point.
            ListIterator<ExpiryEntry> li = list.listIterator();
            while (li.hasNext()) {
                ExpiryEntry entry = li.next();

                if (then.isAfter(entry.expiry)) {
                    li.previous();
                    // insert here
                    li.add(new ExpiryEntry(conn, then));
                    mayContainEntries = true;
                    return;
                }
            }
            // last (or first) element of list (the last element is
            // the first when the list is empty)
            list.add(new ExpiryEntry(conn, then));
            mayContainEntries = true;
        }

        // should only be called while holding a synchronization
        // lock on the ConnectionPool
        void remove(HttpConnection c) {
            if (c == null || list.isEmpty()) return;
            ListIterator<ExpiryEntry> li = list.listIterator();
            while (li.hasNext()) {
                ExpiryEntry e = li.next();
                if (e.connection.equals(c)) {
                    li.remove();
                    mayContainEntries = !list.isEmpty();
                    return;
                }
            }
        }

        // should only be called while holding a synchronization
        // lock on the ConnectionPool.
        // Purge all elements whose deadline is before now (now included).
        List<HttpConnection> purgeUntil(Instant now) {
            if (list.isEmpty()) return Collections.emptyList();

            List<HttpConnection> closelist = new ArrayList<>();

            // elements with the closest deadlines are at the tail
            // of the queue, so we're going to use a descending iterator
            // to remove them, and stop when we find the first element
            // that has not expired yet.
            Iterator<ExpiryEntry> li = list.descendingIterator();
            while (li.hasNext()) {
                ExpiryEntry entry = li.next();
                // use !isAfter instead of isBefore in order to
                // remove the entry if its expiry == now
                if (!entry.expiry.isAfter(now)) {
                    li.remove();
                    HttpConnection c = entry.connection;
                    closelist.add(c);
                } else break; // the list is sorted
            }
            mayContainEntries = !list.isEmpty();
            return closelist;
        }

        // should only be called while holding a synchronization
        // lock on the ConnectionPool
        java.util.stream.Stream<ExpiryEntry> stream() {
            return list.stream();
        }

        // should only be called while holding a synchronization
        // lock on the ConnectionPool
        void clear() {
            list.clear();
            mayContainEntries = false;
        }
    }

    // Remove a connection from the pool.
    // should only be called while holding a synchronization
    // lock on the ConnectionPool
    private void removeFromPool(HttpConnection c) {
        assert Thread.holdsLock(this);
        if (c instanceof PlainHttpConnection) {
            removeFromPool(c, plainPool);
        } else {
            assert c.isSecure() : "connection " + c + " is not secure!";
            removeFromPool(c, sslPool);
        }
    }

    // Used by tests
    synchronized boolean contains(HttpConnection c) {
        final CacheKey key = c.cacheKey();
        List<HttpConnection> list;
        if ((list = plainPool.get(key)) != null) {
            if (list.contains(c)) return true;
        }
        if ((list = sslPool.get(key)) != null) {
            if (list.contains(c)) return true;
        }
        return false;
    }

    void cleanup(HttpConnection c, Throwable error) {
        if (debug.on())
            debug.log("%s : ConnectionPool.cleanup(%s)",
                    String.valueOf(c.getConnectionFlow()), error);
        synchronized(this) {
            removeFromPool(c);
            expiryList.remove(c);
        }
        c.close();
    }

    /**
     * An object that subscribes to the flow while the connection is in
     * the pool. Anything that comes in will cause the connection to be closed
     * and removed from the pool.
     */
    private final class CleanupTrigger implements
            FlowTube.TubeSubscriber, FlowTube.TubePublisher,
            Flow.Subscription {

        private final HttpConnection connection;
        private volatile boolean done;

        public CleanupTrigger(HttpConnection connection) {
            this.connection = connection;
        }

        public boolean isDone() { return done;}

        private void triggerCleanup(Throwable error) {
            done = true;
            cleanup(connection, error);
        }

        @Override public void request(long n) {}
        @Override public void cancel() {}

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }
        @Override
        public void onError(Throwable error) { triggerCleanup(error); }
        @Override
        public void onComplete() { triggerCleanup(null); }
        @Override
        public void onNext(List<ByteBuffer> item) {
            triggerCleanup(new IOException("Data received while in pool"));
        }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            subscriber.onSubscribe(this);
        }

        @Override
        public String toString() {
            return "CleanupTrigger(" + connection.getConnectionFlow() + ")";
        }
    }
}
