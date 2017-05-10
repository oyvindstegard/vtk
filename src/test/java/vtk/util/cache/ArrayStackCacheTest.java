/* Copyright (c) 2006, University of Oslo, Norway
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test case for {@link ArrayStackCache}
 *  
 * @author oyviste
 *
 */
public class ArrayStackCacheTest {

    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss:S z";

    private ReusableObjectCache<SimpleDateFormat> makeCache(int capacity) {
        return new ArrayStackCache<>(() -> new SimpleDateFormat(PATTERN), capacity);
    }

    private final Logger logger = LoggerFactory.getLogger(ArrayStackCacheTest.class.getName());
    
    @Test
    public void basics() {
        
        final ReusableObjectCache<SimpleDateFormat> cache = makeCache(1);
        
        SimpleDateFormat inst = cache.getInstance(); // Empty cache should produce a fresh instance
        assertNotNull(inst);
        assertEquals(0, cache.size());
        
        assertTrue(cache.putInstance(inst));
        assertEquals(1, cache.size());
        
        inst = cache.getInstance();
        assertEquals(0, cache.size());
        
        assertTrue(cache.putInstance(inst));
        assertFalse(cache.putInstance(new SimpleDateFormat(PATTERN))); // Should cause overflow and not be kept
        
        assertTrue(inst == cache.getInstance());
        
    }

    @Test
    public void noDefaultFactory() {
        ReusableObjectCache<Object> c = new ArrayStackCache<>();
        assertNull(c.getInstance());

        Object o = new Object();
        assertTrue(c.putInstance(o));
        assertEquals(1, c.size());

        Object fromCache = c.getInstance();
        assertEquals(0, c.size());
        assertTrue(o == fromCache);
    }

    @Test
    public void overrideDefaultFactory() {
        ReusableObjectCache<SimpleDateFormat> c = makeCache(10);
        SimpleDateFormat sdf = c.getInstance(() -> new SimpleDateFormat("yyyy"));
        assertEquals("yyyy", sdf.toPattern());
    }
    
    @Test
    public void stackCaching() {
        final ReusableObjectCache<SimpleDateFormat> cache = makeCache(3);

        assertEquals(0, cache.size());
        
        // Get some instances
        SimpleDateFormat f1 = cache.getInstance();
        SimpleDateFormat f2 = cache.getInstance();
        SimpleDateFormat f3 = cache.getInstance();
        assertEquals(0, cache.size());
        
        // Put them back
        assertTrue(cache.putInstance(f1)); 
        assertTrue(cache.putInstance(f2));
        assertTrue(cache.putInstance(f3));
        
        // Test that the instances are cached properly
        // (we know that it works like a stack internally)
        assertTrue(f3 == cache.getInstance());
        assertTrue(f2 == cache.getInstance());
        assertTrue(f1 == cache.getInstance());
        assertNotNull(cache.getInstance());
        assertEquals(0, cache.size());
    }
    
    // Test multithreaded access and performance difference.
    @Test
    public void multithreadedAccess() {

        final ReusableObjectCache<SimpleDateFormat> cache = makeCache(50);
        
        int numWorkers = 100;
        int iterationsPerWorker = 50;
        Thread[] threads = new Thread[numWorkers];
        TestWorker[] workers = new TestWorker[numWorkers];
        
        for (int i=0; i<numWorkers; i++) {
            workers[i] = new TestWorker(cache, iterationsPerWorker, false);
            threads[i] = new Thread(workers[i]);
        }
        
        long start = System.currentTimeMillis();
        
        // Start all threads concurrently, as fast as possible
        for (int i=0; i<numWorkers; i++) {
            threads[i].start();
        }
        
        // Wait for all threads to finish the work
        for (int i=0; i<numWorkers; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ie) {}
        }
        long end = System.currentTimeMillis();
        
        logger.info("testMultithreadedAccess(): Time used without caching: "
                + (end-start) + " ms.");
        
        // Check that none of the workers failed
        for (int i=0; i<numWorkers; i++) {
            TestWorker w = workers[i];
            if (w.failed) {
                fail("Worker # " + i + " failed");
            }
        }

        System.gc();
        
        // Test with cache enabled
        for (int i=0; i<numWorkers; i++) {
            workers[i] = new TestWorker(cache, iterationsPerWorker, true);
            threads[i] = new Thread(workers[i]);
        }
        
        start = System.currentTimeMillis();
        // Start all threads concurrently, as fast as possible
        for (int i=0; i<numWorkers; i++) {
            threads[i].start();
        }
        
        // Wait for all threads to finish the work
        for (int i=0; i<numWorkers; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ie) {}
        }
        end = System.currentTimeMillis();
        
        logger.info("testMultithreadedAccess(): Time used with caching enabled: "
                + (end-start) + " ms.");
        logger.info("testMultithreadedAccess(): Size of cache at the end: "
                + cache.size());
        
    }
    
    private class TestWorker implements Runnable {
        
        boolean failed = false;
        boolean useCache;
        int iterations;
        ReusableObjectCache<SimpleDateFormat> dateFormatCache;
        
        public TestWorker(ReusableObjectCache<SimpleDateFormat> dateFormatCache, 
                            int iterations, boolean useCache) {
            this.dateFormatCache = dateFormatCache;
            this.iterations = iterations;
            this.useCache = useCache;
        }
        
        @Override
        public void run() {
            for (int i=0; i<this.iterations; i++) {
                Date d = new Date();

                SimpleDateFormat f;
                if (this.useCache) {
                    f = this.dateFormatCache.getInstance();
                } else {
                    f = new SimpleDateFormat(PATTERN);
                }
                
                String formatted = f.format(d);
                Date parsed = null;
                try {
                    parsed = f.parse(formatted);
                } catch (ParseException pe) {
                    this.failed = true;
                    break;
                }
                
                // NOTE: assuming millisecond resolution in date format !
                if (d.getTime() != parsed.getTime()) {
                    this.failed = true;
                    break;
                }
                
                if (this.useCache) {
                    this.dateFormatCache.putInstance(f);
                }
            }
        }
    }
}
