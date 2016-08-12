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
package vtk.repository.store.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import vtk.repository.Path;
import vtk.repository.store.AbstractContentStoreTest;
import vtk.repository.store.ContentStore;


public class FileSystemContentStoreTest extends AbstractContentStoreTest {

    private ContentStore store;

    private String storeDir;

    @Before
    public void setUp() throws Exception {
        this.storeDir = System.getProperty("java.io.tmpdir") + "/contentStore" + getRandomIntAsString();
        
        File storeDirFile = new File(this.storeDir);
        if (!storeDirFile.mkdir()) {
            throw new IOException("could not make temp dir " + this.storeDir);
        }
        
        FileSystemContentStore store = new FileSystemContentStore();
        store.setRepositoryDataDirectory(this.storeDir);
        this.store = store;
    }

    @After
    public void tearDown() throws Exception {

        // Clean out store before next test or at end
        File storeDirFile = new File(this.storeDir);
        for (String rootChild: storeDirFile.list()) {
            this.store.deleteResource(Path.fromString("/" + rootChild));
        }
        storeDirFile.delete();
    }
    
    private String getRandomIntAsString() {
        Random generator = new Random(Calendar.getInstance().getTimeInMillis());
        return String.valueOf(generator.nextInt());
    }

    @Override
    public ContentStore getStore() {
        return this.store;
    }
    

    @Test
    public void testCreateResource() throws IOException {
        Path uri = Path.fromString("/test.html");
        getStore().createResource(uri, false);
        InputStream is = getStore().getInputStream(uri);
        assertNotNull(is);
        is.close();
        assertEquals(0, getStore().getContentLength(uri));
    }

    @Test
    public void testStoreFileContentAndRetrieve() throws IOException {
        Path uri = Path.fromString("/test.html");
        getStore().createResource(uri, false);
        String testString = "This is a test æøå ÆØÅ";

        InputStream inputStreamBeforeStoringContent = getStore().getInputStream(uri);
        assertNotNull(inputStreamBeforeStoringContent);
        inputStreamBeforeStoringContent.close();
        assertEquals(0, getStore().getContentLength(uri));

        ByteArrayInputStream inputStreamForStoringContent = 
            new ByteArrayInputStream(testString.getBytes());
        
        getStore().createResource(uri, false);

        getStore().storeContent(uri, inputStreamForStoringContent);
        inputStreamForStoringContent.close();

        assertEquals(testString.getBytes().length, getStore().getContentLength(uri));
        InputStream inputStreamAfterStoringContent = getStore().getInputStream(uri);
        assertNotNull(inputStreamAfterStoringContent);
        // Write some assertions to make sure the content of the file is ok
        inputStreamAfterStoringContent.close();

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
