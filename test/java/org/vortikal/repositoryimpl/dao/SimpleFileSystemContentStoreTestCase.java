package org.vortikal.repositoryimpl.dao;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SimpleFileSystemContentStoreTestCase extends TestCase {

    private SimpleFileSystemContentStore contentStore;

    private String tmpDir;

    private static Log logger = LogFactory.getLog(SimpleFileSystemContentStoreTestCase.class);

    protected void setUp() throws Exception {
        super.setUp();
        contentStore = new SimpleFileSystemContentStore();
        tmpDir = System.getProperty("java.io.tmpdir") + "/contentStore"
                + Thread.currentThread().hashCode();
        
        File tmpDirFile = new File(tmpDir);
        tmpDirFile.mkdir();
        contentStore.setRepositoryDataDirectory(tmpDir);
        // System.out.println("tmpDir: " + tmpDir);

    }

    protected void tearDown() throws Exception {
        super.tearDown();
        
    }

    public void testCreateResource() throws IOException {
        String uri = "/test.html";
        contentStore.createResource(uri, false);
        InputStream is = contentStore.getInputStream(uri);
        assertNotNull(is);
        is.close();
        assertEquals(0, contentStore.getContentLength(uri));
    }

    public void testStoreFileContentAndRetrive() throws IOException {
        String uri = "/test.html";
        String testString = "This is a test æøå ÆØÅ";

        InputStream inputStreamBeforeStoringContent = contentStore.getInputStream(uri);
        assertNotNull(inputStreamBeforeStoringContent);
        inputStreamBeforeStoringContent.close();
        assertEquals(0, contentStore.getContentLength(uri));

        ByteArrayInputStream inputStreamForStoringContent = 
            new ByteArrayInputStream(testString.getBytes());
        
        contentStore.createResource(uri, false);

        contentStore.storeContent(uri, inputStreamForStoringContent);
        inputStreamForStoringContent.close();

        assertEquals(testString.getBytes().length, contentStore.getContentLength(uri));
        InputStream inputStreamAfterStoringContent = contentStore.getInputStream(uri);
        assertNotNull(inputStreamAfterStoringContent);
        // Write some assertions to make sure the content of the file is ok
        inputStreamAfterStoringContent.close();

        contentStore.deleteResource(uri);
        assertEquals(0, contentStore.getContentLength(uri));

        try {
            InputStream inputStreamAfterDelete = contentStore.getInputStream(uri);
            fail("file does not exists");
        } catch (FileNotFoundException e) {
            // ok
        }
    }

    public void testCreateDirectory() {
    }

    public void testCopy() {
    }

    // public static boolean deleteDir(File dir) {
    // if (dir.isDirectory()) {
    // String[] children = dir.list();
    // for (int i=0; i<children.length; i++) {
    // boolean success = deleteDir(new File(dir, children[i]));
    // if (!success) {
    // return false;
    // }
    // }
    // }
    //    
    // // The directory is now empty so delete it
    // return dir.delete();
    // }

}
