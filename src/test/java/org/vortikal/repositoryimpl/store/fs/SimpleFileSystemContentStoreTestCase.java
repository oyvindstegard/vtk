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
package org.vortikal.repositoryimpl.store.fs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

import org.vortikal.repositoryimpl.store.ContentStore;
import org.vortikal.repositoryimpl.store.AbstractContentStoreTestCase;


public class SimpleFileSystemContentStoreTestCase extends AbstractContentStoreTestCase {

    private ContentStore store;

    private String tmpDir;

    private static Log logger = LogFactory.getLog(SimpleFileSystemContentStoreTestCase.class);

    protected void setUp() throws Exception {
        BasicConfigurator.configure();
        super.setUp();
        SimpleFileSystemContentStore store = new SimpleFileSystemContentStore();
        this.tmpDir = System.getProperty("java.io.tmpdir") + "/contentStore" + getRandomIntAsString();
        
        File tmpDirFile = new File(this.tmpDir);
        tmpDirFile.mkdir();
        store.setRepositoryDataDirectory(this.tmpDir);
        setStore(store);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        
    }
    
    private String getRandomIntAsString() {
        Random generator = new Random(Calendar.getInstance().getTimeInMillis());
        return String.valueOf(generator.nextInt());
    }

    public ContentStore getStore() {
        return this.store;
    }

    public void setStore(ContentStore store) {
        this.store = store;
    }
    
    
    public void testCreateResource() throws IOException {
        String uri = "/test.html";
        getStore().createResource(uri, false);
        InputStream is = getStore().getInputStream(uri);
        assertNotNull(is);
        is.close();
        assertEquals(0, getStore().getContentLength(uri));
    }

    public void testStoreFileContentAndRetrieve() throws IOException {
        String uri = "/test.html";
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
            InputStream inputStreamAfterDelete = getStore().getInputStream(uri);
            fail("file does not exists");
        } catch (Exception e) {
            // ok
        }
    }
    
    
    

}
