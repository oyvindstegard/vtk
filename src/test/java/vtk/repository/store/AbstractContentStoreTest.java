/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package vtk.repository.store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;
import org.junit.Test;
import vtk.repository.ContentInputSource;
import vtk.repository.ContentInputSources;

import vtk.repository.IllegalOperationException;
import vtk.repository.Path;
import vtk.util.io.IO;

/**
 * Tests for <code>vtk.repository.store.ContentStore</code> 
 * implementations.
 * 
 */
public abstract class AbstractContentStoreTest {

    public abstract ContentStore getStore();

    /*
     * Test method for
     * 'vtk.repositoryimpl.dao.MemoryContentStore.createResource(String,
     * boolean)'
     */
    @Test
    public void nonExistantParent() throws IOException {

        // Create a test directory
        getStore().createResource(Path.fromString("/test"), true);

        // Now create a valid content resource
        getStore().createResource(Path.fromString("/test/empty-file.txt"), false);

        // Create a new resource under invalid parent
        try {
            getStore().createResource(Path.fromString("/non-existant-parent/new-resource.txt"),
                            false);
            fail("Expected Exception when creating new node under non-existing parent.");
        } catch (Exception e) {
            // OK
        }
    }

    /*
     * Test method for
     * 'vtk.repositoryimpl.dao.MemoryContentStore.getContentLength(String)'
     */
    @Test
    public void getContentLength() throws IOException {
        String testString = "HELLO WORLD, THIS IS CONTENT";
        byte[] content = testString.getBytes();

        getStore().createResource(Path.fromString("/file.txt"), false);
        getStore().storeContent(Path.fromString("/file.txt"), ContentInputSources.fromBytes(content));

        // Test that content length is correct
        assertEquals(content.length, getStore().getContentLength(Path.fromString("/file.txt")));

        // Test that content length on directory throws
        // IllegalOperationException
        getStore().createResource(Path.fromString("/dir"), true);
        try {
            getStore().getContentLength(Path.fromString("/dir"));
            fail("Expected IllegalOperationException when trying to get content length of directory");
        } catch (IllegalOperationException ioe) {
            // OK
        }
    }

    /*
     * Test method for
     * 'vtk.repositoryimpl.dao.MemoryContentStore.deleteResource(String)'
     */
    @Test
    public void deleteResource() {
        // Test delete of simple file
        getStore().createResource(Path.fromString("/short-lived-file.txt"), false);

         assertEquals(true, fileExists(Path.fromString("/short-lived-file.txt")));


         // Now delete file ..
         getStore().deleteResource(Path.fromString("/short-lived-file.txt"));

         // .. and test that it does not exist anymore.
         assertEquals(false, fileExists(Path.fromString("/short-lived-file.txt")));

         // Create two small sub-directories
         getStore().createResource(Path.fromString("/a"), true);
         getStore().createResource(Path.fromString("/a/b"), true);
         getStore().createResource(Path.fromString("/a/b/file1.txt"), false);
         getStore().createResource(Path.fromString("/a/b/file2.txt"), false);
         getStore().createResource(Path.fromString("/a/b/file3.txt"), false);

         getStore().createResource(Path.fromString("/foo-file.bar"), false);

         // Delete subtree '/a/b'
         getStore().deleteResource(Path.fromString("/a/b"));

         // Check that subtree does not exist (or any if its sub-nodes)
         assertEquals(false, fileExists(Path.fromString("/a/b/file1.txt")));
         assertEquals(false, fileExists(Path.fromString("/a/b/file2.txt")));
         assertEquals(false, fileExists(Path.fromString("/a/b/file3.txt")));

         getStore().deleteResource(Path.fromString("/a"));

         assertEquals(true, fileExists(Path.fromString("/foo-file.bar")));

         // Check that root node cannot be deleted
         getStore().deleteResource(Path.fromString("/"));
    }
    
    private boolean fileExists(Path path) {
        try {
            getStore().getContentLength(path);
            return true;
        } catch (DataAccessException e) {
            return false;
        } 
    }

    /*
     * Test method for
     * 'vtk.repositoryimpl.dao.MemoryContentStore.getInputStream(String)'
     */
    @Test
    public void getInputStreamAndStoreContent() throws IOException {
        
        String testString = "I AM A CONTENT STRING";
        byte[] content = testString.getBytes();
        
        Path p = Path.fromString("/content.msg"); 
        Path p2 = Path.fromString("/content2.msg"); 
        
        // Create a node and store some content in it
        getStore().createResource(p, false);
        getStore().storeContent(p, ContentInputSources.fromBytes(content));
        
        // Verify length
        InputStream input = getStore().getInputStream(p);
        assertNotNull(input);
        
        byte[] content2 = getContent(input);
        
        // Check that received content is what we originally stored
        assertTrue(blobsEqual(content, content2));
        
        // Copy content and test again
        getStore().copy(p, p2);
        
        // Truncate original file (make sure the copy is a real clone)
        getStore().storeContent(p, ContentInputSources.fromBytes("".getBytes()));
        assertEquals(0, getStore().getContentLength(p));
        
        input = getStore().getInputStream(p2);
        content2 = getContent(input);
        assertTrue(blobsEqual(content, content2));
        
    }
    
    /*
     * Test method for
     * 'vtk.repositoryimpl.dao.MemoryContentStore.copy(String, String)'
     */
    @Test
    public void copy() throws IOException {

        byte[] contentFile1 = "I'm the contents of file1.txt".getBytes();
        byte[] contentFile2 = "I'm the contents of file2.txt".getBytes();
        byte[] contentFile3 = "foo bar baz mik mak hey ho ØÆÅ øæå".getBytes();

        // Create content tree
        getStore().createResource(Path.fromString("/a"), true);
        getStore().createResource(Path.fromString("/a/b"), true);
        getStore().createResource(Path.fromString("/a/b/file1.txt"), false);
        getStore().createResource(Path.fromString("/a/b/file2.txt"), false);
        getStore().createResource(Path.fromString("/a/b/file3.txt"), false);
        getStore().createResource(Path.fromString("/d"), true);
        getStore().createResource(Path.fromString("/d/e"), true);
        getStore().createResource(Path.fromString("/d/file4.txt"), false);

        // Insert some content
        getStore().storeContent(Path.fromString("/a/b/file1.txt"), ContentInputSources.fromBytes(contentFile1));
        getStore().storeContent(Path.fromString("/a/b/file2.txt"), ContentInputSources.fromBytes(contentFile2));
        getStore().storeContent(Path.fromString("/a/b/file3.txt"), ContentInputSources.fromBytes(contentFile3));

        // Copy subtree '/d' to '/a/d', then check consistency      
        getStore().copy(Path.fromString("/d"), Path.fromString("/a/d"));
        assertTrue(fileExists(Path.fromString("/a/d/file4.txt")));

        // Delete subtree '/d'
        getStore().deleteResource(Path.fromString("/d"));
        assertFalse(fileExists(Path.fromString("/d/file4.txt")));

        // Rename/move subtree '/a/b' to '/a/x'
        getStore().move(Path.fromString("/a/b"), Path.fromString("/a/x"));

        // Check consistency of '/a' subtree
        assertTrue(fileExists(Path.fromString("/a/x/file1.txt")));
        assertTrue(fileExists(Path.fromString("/a/x/file2.txt")));
        assertTrue(fileExists(Path.fromString("/a/x/file3.txt")));
        assertTrue(fileExists(Path.fromString("/a/d/file4.txt")));

        // Verify content
        byte[] content = getContent(getStore().getInputStream(Path.fromString("/a/x/file1.txt")));
        assertTrue(blobsEqual(contentFile1, content));
        content = getContent(getStore().getInputStream(Path.fromString("/a/x/file2.txt")));
        assertTrue(blobsEqual(contentFile2, content));
        content = getContent(getStore().getInputStream(Path.fromString("/a/x/file3.txt")));
        assertTrue(blobsEqual(contentFile3, content));

        content = getContent(getStore().getInputStream(Path.fromString("/a/d/file4.txt")));
        assertTrue(blobsEqual(new byte[0], content));

        // Rename '/a' subtree, then re-check consistency
        getStore().copy(Path.fromString("/a"), Path.fromString("/Copy of a"));
        getStore().deleteResource(Path.fromString("/a"));
        assertFalse(fileExists(Path.fromString("/a")));

        // Check consistency of '/Copy of a' subtree
        assertTrue(fileExists(Path.fromString("/Copy of a/x/file1.txt")));
        assertTrue(fileExists(Path.fromString("/Copy of a/x/file2.txt")));
        assertTrue(fileExists(Path.fromString("/Copy of a/x/file3.txt")));
        assertTrue(fileExists(Path.fromString("/Copy of a/d/file4.txt")));

        // Verify content
        content = getContent(getStore().getInputStream(Path.fromString("/Copy of a/x/file1.txt")));
        assertTrue(blobsEqual(contentFile1, content));
        content = getContent(getStore().getInputStream(Path.fromString("/Copy of a/x/file2.txt")));
        assertTrue(blobsEqual(contentFile2, content));
        content = getContent(getStore().getInputStream(Path.fromString("/Copy of a/x/file3.txt")));
        assertTrue(blobsEqual(contentFile3, content));

        content = getContent(getStore().getInputStream(Path.fromString("/Copy of a/d/file4.txt")));
        assertTrue(blobsEqual(new byte[0], content));

    }

    private boolean blobsEqual(byte[] content1, byte[] content2) {
        if (content1.length != content2.length) return false;
        for (int i=0; i< content1.length; i++) {
            if (content1[i] != content2[i]) return false;
        }
        
        return true;
    }

    private byte[] getContent(InputStream input) throws IOException {
        byte[] buffer = new byte[1000];
        int n;
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while ((n = input.read(buffer, 0, buffer.length)) != -1) {
            bout.write(buffer, 0, n);
        }
        
        return bout.toByteArray();
    }
    
    /*
     * Test access and modifications by multiple concurrent threads (important).
     */
    @Test
    public void multithreadedAccessAndModification() throws IOException {
        
        // The number of threads to run concurrently against content store
        int numWorkers = 100;
        
        final class Worker implements Runnable {
            ContentStore store;
            String name;
            String workdir;
            
            public Worker(String name, String workdir, ContentStore store) {
                this.store = store;
                this.name = name;
                this.workdir = workdir;
            }
            
            @Override
            public void run() {
                // Create a small structure under workdir, insert thread name
                // into a file, and generally mess about ..
                
                // Sleep a random amount of time before starting
                try {
                    Thread.sleep((long)Math.random()*500);
                } catch (InterruptedException ie) {}
                
                try {
                    this.store.createResource(Path.fromString(this.workdir), true);
                    this.store.createResource(Path.fromString(this.workdir + "/a"), true);
                    this.store.createResource(Path.fromString(this.workdir + "/a/AN_EMPTY_FILE.dat"), false);
                    this.store.createResource(Path.fromString(this.workdir + "/a/AN_EMPTY_FILE2.dat"), false);
                    
                    this.store.createResource(Path.fromString(this.workdir + "/worker_name.txt"), false);
                    this.store.storeContent(Path.fromString(this.workdir + "/worker_name.txt"), ContentInputSources.fromBytes(this.name.getBytes()));
                    
                    this.store.copy(Path.fromString(this.workdir + "/a"), Path.fromString(this.workdir + "/Copy of a (1)"));
                    this.store.copy(Path.fromString(this.workdir + "/a"), Path.fromString(this.workdir + "/Copy of a (2)"));
                    
                    this.store.copy(Path.fromString(this.workdir + "/a"), Path.fromString(this.workdir + "/x"));
                    this.store.deleteResource(Path.fromString(this.workdir + "/a"));
                    this.store.deleteResource(Path.fromString(this.workdir + "/Copy of a (1)"));
                    this.store.deleteResource(Path.fromString(this.workdir + "/Copy of a (2)"));
                    
                    this.store.copy(Path.fromString(this.workdir + "/worker_name.txt"), Path.fromString(this.workdir + "/name.txt"));
                    this.store.deleteResource(Path.fromString(this.workdir + "/worker_name.txt"));
                    
                } catch (DataAccessException de) {
                    fail("Un-expected Exception while working in '" + this.workdir + "': " + de.getMessage());
                }
            }
        }
        
        // Set up store, create one subtree for workers, and one off-limit tree
        getStore().createResource(Path.fromString("/workers_play_area"), true);
        getStore().createResource(Path.fromString("/off_limits"), true);
        getStore().createResource(Path.fromString("/off_limits/i_will_survive.txt"), false);
        getStore().storeContent(Path.fromString("/off_limits/i_will_survive.txt"), 
                ContentInputSources.fromBytes("I Will Surviveeee !".getBytes()));
        
        // Create the worker threads, add them all to a single thread group
        Thread[] threads = new Thread[numWorkers];
        ThreadGroup group = new ThreadGroup("workers");
        for (int i=0; i<numWorkers; i++) {
            Worker worker = new Worker("Worker # " + i, 
                            "/workers_play_area/worker" + i, getStore());
            
            threads[i] = new Thread(group, worker, worker.name);
        }
        
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
        
        // Assert that all threads are in fact finished.
        assertEquals(0, group.activeCount());
        
        // Verify content store structures:
        // Verify off-limits subtree
        assertTrue(fileExists(Path.fromString("/off_limits/i_will_survive.txt")));
        byte[] originalContent = "I Will Surviveeee !".getBytes();
        byte[] content = getContent(getStore().getInputStream(Path.fromString("/off_limits/i_will_survive.txt")));
        assertTrue(blobsEqual(originalContent, content));

        // Verify all worker areas in content store (should conform to specific pattern)
        for (int i = 0; i < numWorkers; i++) {
             assertTrue(fileExists(Path.fromString("/workers_play_area/worker" + i + "/name.txt")));
             content = getContent(getStore().getInputStream(Path.fromString("/workers_play_area/worker" + i + "/name.txt")));
             assertTrue(blobsEqual(content, ("Worker # " + i).getBytes()));
             assertTrue(fileExists(Path.fromString("/workers_play_area/worker" + i + "/x/AN_EMPTY_FILE.dat")));
             assertTrue(fileExists(Path.fromString("/workers_play_area/worker" + i + "/x/AN_EMPTY_FILE2.dat")));
        }
    }

    @Test
    public void testCreateResource() throws IOException {
        Path uri = Path.fromString("/test.html");
        getStore().createResource(uri, false);
        try (InputStream is = getStore().getInputStream(uri)) {
            assertNotNull(is);
        }
        assertEquals(0, getStore().getContentLength(uri));
    }

    @Test
    public void testStoreFileContentAndRetrieve() throws IOException {
        Path uri = Path.fromString("/test.html");
        getStore().createResource(uri, false);
        String testString = "This is a test æøå ÆØÅ";

        try (InputStream inputStreamBeforeStoringContent = getStore().getInputStream(uri)) {
            assertNotNull(inputStreamBeforeStoringContent);
        }
        assertEquals(0, getStore().getContentLength(uri));

        ContentInputSource inputSourceForStoringContent =
            ContentInputSources.fromString(testString);

        getStore().createResource(uri, false);

        getStore().storeContent(uri, inputSourceForStoringContent);

        assertEquals(testString.getBytes().length, getStore().getContentLength(uri));
        try (InputStream inputStreamAfterStoringContent = getStore().getInputStream(uri)) {
            assertNotNull(inputStreamAfterStoringContent);
            assertEquals(testString, IO.readString(inputStreamAfterStoringContent).perform());
        }

        getStore().deleteResource(uri);
        //assertEquals(0, getStore().getContentLength(uri));

        try {
            getStore().getInputStream(uri);
            fail("file does not exist");
        } catch (Exception e) {
            // ok
        }
    }
}
