/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.http;

import java.net.URL;
import java.io.IOException;
import java.net.Authenticator.RequestorType;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.www.HeaderParser;
import static sun.net.www.protocol.http.AuthScheme.NEGOTIATE;
import static sun.net.www.protocol.http.AuthScheme.KERBEROS;
import sun.security.action.GetPropertyAction;

/**
 * NegotiateAuthentication:
 *
 * @author weijun.wang@sun.com
 * @since 1.6
 */

class NegotiateAuthentication extends AuthenticationInfo {

    @java.io.Serial
    private static final long serialVersionUID = 100L;

    @SuppressWarnings("serial") // Not statically typed as Serializable
    private final HttpCallerInfo hci;

    // These maps are used to manage the GSS availability for diffrent
    // hosts. The key for both maps is the host name.
    // <code>supported</code> is set when isSupported is checked,
    // if it's true, a cached Negotiator is put into <code>cache</code>.
    // the cache can be used only once, so after the first use, it's cleaned.
    static HashMap <String, Boolean> supported = null;
    static ThreadLocal <HashMap <String, Negotiator>> cache = null;
    private static final ReentrantLock negotiateLock = new ReentrantLock();

    /* Whether cache is enabled for Negotiate/Kerberos */
    private static final boolean cacheSPNEGO;
    static {
        String spnegoCacheProp =
            GetPropertyAction.privilegedGetProperty("jdk.spnego.cache", "true");
        cacheSPNEGO = Boolean.parseBoolean(spnegoCacheProp);
    }

    // The HTTP Negotiate Helper
    @SuppressWarnings("serial") // Not statically typed as Serializable
    private Negotiator negotiator = null;

   /**
    * Constructor used for both WWW and proxy entries.
    * @param hci a schemed object.
    */
    public NegotiateAuthentication(HttpCallerInfo hci) {
        super(RequestorType.PROXY==hci.authType ? PROXY_AUTHENTICATION : SERVER_AUTHENTICATION,
              hci.scheme.equalsIgnoreCase("Negotiate") ? NEGOTIATE : KERBEROS,
              hci.url,
              "",
              AuthenticatorKeys.getKey(hci.authenticator));
        this.hci = hci;
    }

    /**
     * @return true if this authentication supports preemptive authorization
     */
    @Override
    public boolean supportsPreemptiveAuthorization() {
        return false;
    }

    /**
     * Find out if the HttpCallerInfo supports Negotiate protocol. In order to
     * find out yes or no, an initialization of a Negotiator object against it
     * is tried. The generated object will be cached under the name of ths
     * hostname at a success try.<br>
     *
     * If this method is called for the second time on an HttpCallerInfo with
     * the same hostname, the answer is retrieved from cache.
     *
     * @return true if supported
     */
    public static boolean isSupported(HttpCallerInfo hci) {
        negotiateLock.lock();
        try {
            if (supported == null) {
                supported = new HashMap<>();
            }
            String hostname = hci.host;
            hostname = hostname.toLowerCase();
            if (supported.containsKey(hostname)) {
                return supported.get(hostname);
            }

            Negotiator neg = Negotiator.getNegotiator(hci);
            if (neg != null) {
                supported.put(hostname, true);
                // the only place cache.put is called. here we can make sure
                // the object is valid and the oneToken inside is not null
                if (cache == null) {
                    cache = new ThreadLocal<>() {
                        @Override
                        protected HashMap<String, Negotiator> initialValue() {
                            return new HashMap<>();
                        }
                    };
                }
                cache.get().put(hostname, neg);
                return true;
            } else {
                supported.put(hostname, false);
                return false;
            }
        } finally {
            negotiateLock.unlock();
        }
    }

    private static HashMap<String, Negotiator> getCache() {
        negotiateLock.lock();
        try {
            if (cache == null) return null;
            return cache.get();
        } finally {
            negotiateLock.unlock();
        }
    }

    @Override
    protected boolean useAuthCache() {
        return false;
    }

    /**
     * Not supported. Must use the setHeaders() method
     */
    @Override
    public String getHeaderValue(URL url, String method) {
        throw new RuntimeException ("getHeaderValue not supported");
    }

    /**
     * Check if the header indicates that the current auth. parameters are stale.
     * If so, then replace the relevant field with the new value
     * and return true. Otherwise return false.
     * returning true means the request can be retried with the same userid/password
     * returning false means we have to go back to the user to ask for a new
     * username password.
     */
    @Override
    public boolean isAuthorizationStale (String header) {
        return false; /* should not be called for Negotiate */
    }

    /**
     * Set header(s) on the given connection.
     * @param conn The connection to apply the header(s) to
     * @param p A source of header values for this connection, not used because
     *          HeaderParser converts the fields to lower case, use raw instead
     * @param raw The raw header field.
     * @return true if all goes well, false if no headers were set.
     */
    @Override
    public boolean setHeaders(HttpURLConnection conn, HeaderParser p, String raw) {
        // no need to synchronize here:
        //   already locked by s.n.w.p.h.HttpURLConnection
        assert conn.isLockHeldByCurrentThread();

        try {
            String response;
            byte[] incoming = null;
            String[] parts = raw.split("\\s+");
            if (parts.length > 1) {
                incoming = Base64.getDecoder().decode(parts[1]);
            }
            response = hci.scheme + " " + Base64.getEncoder().encodeToString(
                        incoming==null?firstToken():nextToken(incoming));

            conn.setAuthenticationProperty(getHeaderName(), response);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * return the first token.
     * @return the token
     * @throws IOException if <code>Negotiator.getNegotiator()</code> or
     *                     <code>Negotiator.firstToken()</code> failed.
     */
    private byte[] firstToken() throws IOException {
        negotiator = null;
        HashMap <String, Negotiator> cachedMap = getCache();
        if (cachedMap != null) {
            negotiator = cachedMap.get(getHost());
            if (negotiator != null) {
                cachedMap.remove(getHost()); // so that it is only used once
            }
        }
        if (negotiator == null) {
            negotiator = Negotiator.getNegotiator(hci);
            if (negotiator == null) {
                IOException ioe = new IOException("Cannot initialize Negotiator");
                throw ioe;
            }
        }

        return negotiator.firstToken();
    }

    /**
     * return more tokens
     * @param token the token to be fed into <code>negotiator.nextToken()</code>
     * @return the token
     * @throws IOException if <code>negotiator.nextToken()</code> throws Exception.
     *  May happen if the input token is invalid.
     */
    private byte[] nextToken(byte[] token) throws IOException {
        return negotiator.nextToken(token);
    }

    /**
     * Releases any system resources and cryptographic information stored in
     * the context object and invalidates the context.
     */
    @Override
    public void disposeContext() {
        if (negotiator != null) {
            try {
                negotiator.disposeContext();
            } catch (IOException ioEx) {
                //do not rethrow IOException
            }
            negotiator = null;
        }
    }

    // MS will send a final WWW-Authenticate even if the status is already
    // 200 OK. The token can be fed into initSecContext() again to determine
    // if the server can be trusted. This is not the same concept as Digest's
    // Authentication-Info header.
    //
    // Currently we ignore this header.

}
