/* Copyright (c) 2015, University of Oslo, Norway
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.util.cache;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.constructs.blocking.LockTimeoutException;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 * Test case for {@link EhContentCache}. Focused around known behaviour of
 * EhCache's {@link SelfPopulatingCache} implementation.
 *
 * This test case serves mostly as a documentation and verification on the
 * behaviour of Ehcache under various configurations.
 */
@SuppressWarnings("unchecked")
public class EhContentCacheTest {

    static {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Log4JLogger");
        System.setProperty("log4j.configuration", "log4j.test.xml");
    }

    private static final int DEFAULT_CACHE_LIMIT = 3;
    private static final String DEFAULT_CACHE_NAME = "vtk.content";

    private static CacheManager EHCM;

    private EhContentCache<String, String> cache; // Our content cache, based on decorated ehcache
    private final Mockery context;
    private final ContentCacheLoader<String, String> mockedLoader;

    public EhContentCacheTest() {
        context = new Mockery();
        mockedLoader = context.mock(ContentCacheLoader.class);
    }

    @BeforeClass
    public static void setupCacheManager() {
        Configuration c = new Configuration();
        c.diskStore(new DiskStoreConfiguration().path("java.io.tmpdir/vtk/ehcachetest"));
        EHCM = new CacheManager(c);
    }

    @AfterClass
    public static void shutdownCacheManager() {
        EHCM.shutdown();
    }

    @Before
    public void setUp() throws Exception {
        CacheConfiguration ehCacheConfig = new CacheConfiguration(DEFAULT_CACHE_NAME, DEFAULT_CACHE_LIMIT);
        cache = new EhContentCache<>(makeCache(ehCacheConfig, mockedLoader));
    }

    private SelfPopulatingCache makeCache(CacheConfiguration ehCacheConfig, ContentCacheLoader loader)
            throws Exception {
        Cache ehBackingCache = new Cache(ehCacheConfig);
        SelfPopulatingCache sc = new SelfPopulatingCache(ehBackingCache, new ContentCacheLoaderEhcacheAdapter(loader));

        EHCM.addCache(sc);
        return sc;
    }

    @After
    public void tearDown() {
        cache.clear();
        EHCM.removalAll();
    }

    @Test
    public void simple() throws Exception {
        context.checking(new Expectations() {
            {
                oneOf(mockedLoader).load("foo");
                will(returnValue("bar"));
            }
        });

        assertEquals("bar", cache.get("foo"));
        context.assertIsSatisfied();
    }

    @Test
    public void eviction() throws Exception {
        context.checking(new Expectations() {
            {
                allowing(mockedLoader).load(with(any(String.class)));
                will(returnValue(new Serializable() {}));
            }
        });

        assertEquals(0, cache.getSize());
        cache.get("a");
        cache.get("b");
        cache.get("c");
        cache.get("d");
        assertEquals(DEFAULT_CACHE_LIMIT, cache.getSize());
    }

    @Test
    public void expiry_synchronous_refresh() throws Exception {
        CacheConfiguration cc = new CacheConfiguration("vtk.contentExipry", DEFAULT_CACHE_LIMIT);
        cc.setTimeToIdleSeconds(1);
        EhContentCache<String, String> cache = new EhContentCache<>(makeCache(cc, mockedLoader));

        context.checking(new Expectations() {
            {
                exactly(2).of(mockedLoader).load("a");
                will(onConsecutiveCalls(
                        returnValue("b"),
                        returnValue("c")
                ));
            }
        });

        assertEquals("b", cache.get("a"));
        sleep(1500);
        assertEquals("c", cache.get("a"));
        assertEquals("c", cache.get("a"));
        context.assertIsSatisfied();
    }

    @Test
    public void expiry_asynchronous_refresh() throws Exception {
        CacheConfiguration cc = new CacheConfiguration("vtk.contentExipry", DEFAULT_CACHE_LIMIT);
        cc.setTimeToIdleSeconds(1);
        Mockery threadSafeMockery = new Mockery();
        threadSafeMockery.setThreadingPolicy(new Synchroniser());
        final ContentCacheLoader<String,String> threadSafeMockedLoader = threadSafeMockery.mock(
                ContentCacheLoader.class
        );

        EhContentCache<String, String> cache = new EhContentCache<>(
                makeCache(cc, threadSafeMockedLoader), true, -1, true
        );

        threadSafeMockery.checking(new Expectations() {
            {
                exactly(2).of(threadSafeMockedLoader).load("a");
                will(onConsecutiveCalls(
                        returnValue("b"),
                        returnValue("c")
                ));
            }
        });
        assertEquals("b", cache.get("a"));
        sleep(1500);
        assertEquals("b", cache.get("a"));
        cache.asyncRefreshExecutor.shutdown();
        boolean terminated = cache.asyncRefreshExecutor.awaitTermination(1, TimeUnit.SECONDS);
        assertTrue("asyncRefreshExecutor did not terminate before timeout", terminated);
        assertEquals("c", cache.get("a"));
        threadSafeMockery.assertIsSatisfied();
    }

    @Test
    public void return_stale_data_on_error_in_synchronous_refresh() throws Exception {
        CacheConfiguration cc = new CacheConfiguration("vtk.contentExipry", DEFAULT_CACHE_LIMIT);
        cc.setTimeToIdleSeconds(1);
        EhContentCache<String, String> cache = new EhContentCache<>(makeCache(cc, mockedLoader));

        context.checking(new Expectations() {
            {
                exactly(2).of(mockedLoader).load("a");
                will(onConsecutiveCalls(
                        returnValue("b"),
                        throwException(new CacheException())
                ));
            }
        });

        assertEquals("b", cache.get("a"));
        sleep(1500);
        assertEquals("b", cache.get("a"));
        assertEquals("b", cache.get("a"));
        context.assertIsSatisfied();
    }

    @Test
    public void return_stale_data_on_error_in_asynchronous_refresh() throws Exception {
        CacheConfiguration cc = new CacheConfiguration("vtk.contentExipry", DEFAULT_CACHE_LIMIT);
        cc.setTimeToIdleSeconds(1);
        Mockery threadSafeMockery = new Mockery();
        threadSafeMockery.setThreadingPolicy(new Synchroniser());
        final ContentCacheLoader<String,String> threadSafeMockedLoader = threadSafeMockery.mock(
                ContentCacheLoader.class
        );

        EhContentCache<String, String> cache = new EhContentCache<>(
                makeCache(cc, threadSafeMockedLoader), true, -1, true
        );

        threadSafeMockery.checking(new Expectations() {
            {
                exactly(2).of(threadSafeMockedLoader).load("a");
                will(onConsecutiveCalls(
                        returnValue("b"),
                        throwException(new CacheException())
                ));
            }
        });
        assertEquals("b", cache.get("a"));
        sleep(1500);
        assertEquals("b", cache.get("a"));
        cache.asyncRefreshExecutor.shutdown();
        boolean terminated = cache.asyncRefreshExecutor.awaitTermination(1, TimeUnit.SECONDS);
        assertTrue("asyncRefreshExecutor did not terminate before timeout", terminated);
        assertEquals("b", cache.get("a"));
        threadSafeMockery.assertIsSatisfied();
    }

    @Test
    public void loader_returns_null() throws Exception {

        context.checking(new Expectations() {
            {
                oneOf(mockedLoader).load(with(any(String.class)));
                will(returnValue(null));
            }
        });

        String value = cache.get("a");
        assertNull(value);
    }

    @Test
    public void loader_exception() throws Exception {

        final IOException loaderException = new IOException("could not load object with key 'a'");
        context.checking(new Expectations() {
            {
                oneOf(mockedLoader).load("a");
                will(throwException(loaderException));
            }
        });

        try {
            cache.get("a");
            fail("Expected loader exception to be propagated");
        } catch (CacheException e) {
            assertEquals(e.getCause(), loaderException);
        }
    }

    @Test
    public void concurrent_access_single_key_slow_loader() throws Exception {

        final AtomicInteger loaderCallCount = new AtomicInteger();
        ContentCacheLoader<String, String> slowLoader = new ContentCacheLoader<String, String>() {
            @Override
            public String load(String identifier) throws Exception {
                sleep(1000);
                loaderCallCount.incrementAndGet();
                return identifier.toUpperCase();
            }
        };

        CacheConfiguration cc = new CacheConfiguration(DEFAULT_CACHE_NAME + "-1", DEFAULT_CACHE_LIMIT);
        final EhContentCache<String, String> cache = new EhContentCache<>(
                makeCache(cc, slowLoader), true, -1, true
        );

        ExecutorService es = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            es.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        cache.get("a");
                    } catch (Exception e) {
                    }
                }
            });
        }
        es.shutdown();
        es.awaitTermination(2, TimeUnit.SECONDS);

        assertEquals(1, loaderCallCount.intValue());
    }

    @Test
    public void concurrent_access_lock_timeout() throws Exception {
        ContentCacheLoader<String, String> slowLoader = new ContentCacheLoader<String, String>() {
            @Override
            public String load(String identifier) throws Exception {
                sleep(2000);
                return identifier.toUpperCase();
            }
        };

        CacheConfiguration cc = new CacheConfiguration(DEFAULT_CACHE_NAME + "-1", DEFAULT_CACHE_LIMIT);
        final EhContentCache<String, String> cache = new EhContentCache<>(makeCache(cc, slowLoader));
        SelfPopulatingCache sc = (SelfPopulatingCache) EHCM.getEhcache(DEFAULT_CACHE_NAME + "-1");
        sc.setTimeoutMillis(250);

        ExecutorService es = Executors.newFixedThreadPool(2);
        es.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    cache.get("a"); // Starts slow loader now
                } catch (Exception e) {
                }
            }
        });
        sleep(250);

        class BlockedThread implements Runnable {
            Exception e;
            @Override
            public void run() {
                try {
                    cache.get("a"); // blocks here due to key already in the process of being loaded
                } catch (Exception e) {
                    // expect an exception due to lock timeout
                    this.e = e;
                }
            }
        }
        BlockedThread blockedClient = new BlockedThread();
        es.submit(blockedClient);
        es.shutdown();
        es.awaitTermination(2, TimeUnit.SECONDS);

        assertNotNull(blockedClient.e);
        assertTrue(blockedClient.e.getClass() == LockTimeoutException.class);
    }

    @Test
    public void cache_is_refreshed_when_refresh_interval_seconds_is_set() throws Exception {
        CacheConfiguration cc = new CacheConfiguration("vtk.contentExipry", DEFAULT_CACHE_LIMIT);
        cc.setTimeToLiveSeconds(1);
        Mockery threadSafeMockery = new Mockery();
        threadSafeMockery.setThreadingPolicy(new Synchroniser());
        final ContentCacheLoader<String,String> threadSafeMockedLoader = threadSafeMockery.mock(
                ContentCacheLoader.class
        );

        EhContentCache<String, String> cache = new EhContentCache<>(
                makeCache(cc, threadSafeMockedLoader), true, 1, false
        );

        threadSafeMockery.checking(new Expectations() {
            {
                exactly(2).of(threadSafeMockedLoader).load("a");
                will(onConsecutiveCalls(
                        returnValue("b"),
                        returnValue("c")
                ));
            }
        });
        assertEquals("b", cache.get("a"));
        sleep(1500);
        cache.refreshAllExecutor.shutdown();
        boolean terminated = cache.refreshAllExecutor.awaitTermination(1, TimeUnit.SECONDS);
        assertTrue("asyncRefreshExecutor did not terminate before timeout", terminated);
        threadSafeMockery.assertIsSatisfied();
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

}
