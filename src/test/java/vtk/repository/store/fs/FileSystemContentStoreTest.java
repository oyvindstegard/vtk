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


import java.io.File;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;

import vtk.repository.Path;
import vtk.repository.store.AbstractContentStoreTest;
import vtk.repository.store.ContentStore;


public class FileSystemContentStoreTest extends AbstractContentStoreTest {

    private FileSystemContentStore store;
    private String storeDir;
    private String trashDir;

    @Before
    public void setUp() throws Exception {
        storeDir = Files.createTempDirectory("contentStore").toString();
        trashDir = Files.createTempDirectory("trashStore").toString();
        store = new FileSystemContentStore();
        store.setRepositoryDataDirectory(this.storeDir);
        store.setRepositoryTrashCanDirectory(this.trashDir);
        store.afterPropertiesSet();
    }

    @After
    public void tearDown() throws Exception {

        // Clean out store before next test or at end
        File storeDirFile = new File(this.storeDir);
        for (String rootChild: storeDirFile.list()) {
            this.store.deleteResource(Path.fromString("/" + rootChild));
        }
        storeDirFile.delete();

        // Currently no tests use trash dir, so it's just an empty dir.
        new File(trashDir).delete();
    }
    
    @Override
    public ContentStore getStore() {
        return store;
    }

}
