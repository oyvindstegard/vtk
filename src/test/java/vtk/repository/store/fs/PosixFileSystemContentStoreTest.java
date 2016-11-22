/* Copyright (c) 2016, University of Oslo, Norway
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import vtk.repository.Path;

import static org.junit.Assert.*;
import vtk.repository.ContentInputSource;
import vtk.repository.ContentInputSources;
import vtk.repository.store.AbstractContentStoreTest;
import vtk.repository.store.ContentStore;
import vtk.util.io.IO;

/**
 *
 */
public class PosixFileSystemContentStoreTest extends AbstractContentStoreTest {

    private PosixFileSystemContentStore store;

    private String storeDir;
    private String trashDir;

    @Override
    public ContentStore getStore() {
        return store;
    }

    @BeforeClass
    public static void checkPlatform() {
        // Skip all tests on Windows platform
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @Before
    public void setUp() throws Exception {
        storeDir = Files.createTempDirectory("contentStore").toString();
        trashDir = Files.createTempDirectory("trashStore").toString();
        store = new PosixFileSystemContentStore();
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

    @Test
    public void testCreateResourceWithFileAttributes() throws IOException {
        Path uri = Path.fromString("/test-perms-file.html");

        store.setFilePermissions("r--r--r--");
        store.createResource(uri, false);

        java.nio.file.Path nsPath = Paths.get(storeDir, "test-perms-file.html");
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(nsPath, LinkOption.NOFOLLOW_LINKS);

        assertEquals(3, perms.size());

        assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
        assertTrue(perms.contains(PosixFilePermission.GROUP_READ));
        assertTrue(perms.contains(PosixFilePermission.OTHERS_READ));

        assertFalse(perms.contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));

        assertFalse(perms.contains(PosixFilePermission.OWNER_EXECUTE));
        assertFalse(perms.contains(PosixFilePermission.GROUP_EXECUTE));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    @Test
    public void testCreateResourceWithDirAttributes() throws IOException {
        Path uri = Path.fromString("/test-perms-dir");

        store.setDirectoryPermissions("r-xr-xr-x");
        store.createResource(uri, true);

        java.nio.file.Path nsPath = Paths.get(storeDir, "test-perms-dir");
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(nsPath, LinkOption.NOFOLLOW_LINKS);

        assertEquals(6, perms.size());

        assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
        assertTrue(perms.contains(PosixFilePermission.GROUP_READ));
        assertTrue(perms.contains(PosixFilePermission.OTHERS_READ));

        assertFalse(perms.contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));

        assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue(perms.contains(PosixFilePermission.GROUP_EXECUTE));
        assertTrue(perms.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    @Test
    public void testOwnerAndGroup() throws IOException {
        // Try to explicitly set owner and group to same value as default
        store.createResource(Path.fromString("/test-perms-dir"), true);
        java.nio.file.Path fsPath = Paths.get(storeDir, "test-perms-dir");
        PosixFileAttributes attrs = Files.getFileAttributeView(fsPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
        String groupName = attrs.group().getName();
        String ownerName = attrs.owner().getName();
        store.setGroup(groupName);
        store.setOwner(ownerName);
        store.createResource(Path.fromString("/test-owner-group"), false); // Verify no system call failure only
    }

    @Test
    public void testMoveIntoContentStoreGetsCorrectPerms() throws IOException {

        store.setFilePermissions("rw-r--r--");

        try (IO.TempFile tf = IO.tempFile()) {
            java.nio.file.Path fsPath = tf.file().toPath();
            PosixFileAttributeView attrs = Files.getFileAttributeView(fsPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            attrs.setPermissions(PosixFilePermissions.fromString("r-------x"));

            ContentInputSource content = ContentInputSources.fromFile(tf.file(), true);
            store.storeContent(Path.fromString("/test-move-into-perms.txt"), content);
            assertFalse(tf.file().exists()); // Check that has been moved

            fsPath = Paths.get(storeDir, "test-move-into-perms.txt");
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(fsPath, LinkOption.NOFOLLOW_LINKS);

            assertEquals(4, perms.size());

            assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
            assertTrue(perms.contains(PosixFilePermission.GROUP_READ));
            assertTrue(perms.contains(PosixFilePermission.OTHERS_READ));

            assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE));
            assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
            assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));

            assertFalse(perms.contains(PosixFilePermission.OWNER_EXECUTE));
            assertFalse(perms.contains(PosixFilePermission.GROUP_EXECUTE));
            assertFalse(perms.contains(PosixFilePermission.OTHERS_EXECUTE));

        }

    }

}
