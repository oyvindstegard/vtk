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
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.BeanInitializationException;

/**
 * File system content store with options to set POSIX file attributes on newly
 * created file system objects.
 *
 * <p>Only requried if you need special permissions set on the files and directories
 * that the content store creates on the file system.
 */
public class PosixFileSystemContentStore extends FileSystemContentStore {

    private Optional<Set<PosixFilePermission>> filePermissions = Optional.empty();
    private Optional<Set<PosixFilePermission>> directoryPermissions = Optional.empty();
    private Optional<UserPrincipal> owner = Optional.empty();
    private Optional<GroupPrincipal> group = Optional.empty();

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        checkFsSupport(super.repositoryDataDirectory);
        checkFsSupport(super.repositoryTrashCanDirectory);
        try {
            applyAttributes(new File(this.repositoryDataDirectory));
            applyAttributes(new File(this.repositoryTrashCanDirectory));
        } catch (IOException io) {
            throw new BeanInitializationException("Failed to set configured attributes on content store data or trash root directory", io);
        }
    }

    @Override
    protected void applyAttributes(File f) throws IOException {
        if (! (filePermissions.isPresent() || directoryPermissions.isPresent()
                || owner.isPresent() || group.isPresent())) {
            return;
        }

        java.nio.file.Path fsPath = f.toPath();
        PosixFileAttributeView attrs = Files.getFileAttributeView(fsPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (f.isDirectory()) {
            if (directoryPermissions.isPresent()) {
                attrs.setPermissions(directoryPermissions.get());
            }
        } else {
            if (filePermissions.isPresent()) {
                attrs.setPermissions(filePermissions.get());
            }
        }
        if (owner.isPresent()) {
            attrs.setOwner(owner.get());
        }
        if (group.isPresent()) {
            attrs.setGroup(group.get());
        }
    }

    private void checkFsSupport(String fsPathString) throws IOException {
        java.nio.file.Path fsPath = Paths.get(fsPathString);
        FileStore fs = Files.getFileStore(fsPath);
        if (!fs.supportsFileAttributeView(PosixFileAttributeView.class)) {
            throw new IllegalStateException("File system containing path "
                    + fsPathString + " does not support POSIX file attributes.");
        }
    }

    /**
     * Set the permissions on newly created files.
     *
     * <p>When unconfigured, no attempt will be made to set permissions on
     * newly created files.
     *
     * @see PosixFilePermissions#fromString(java.lang.String)
     * @param permissions
     */
    public void setFilePermissions(String permissions) {
        this.filePermissions = Optional.of(PosixFilePermissions.fromString(permissions));
    }

    /**
     * Set the POSIX permissions on newly created directories.
     *
     * <p>When unconfigured, no attempt will be made to set permissions on
     * newly created directories.
     *
     * @see PosixFilePermissions#fromString(java.lang.String)
     * @param permissions
     */
    public void setDirectoryPermissions(String permissions) {
        this.directoryPermissions = Optional.of(PosixFilePermissions.fromString(permissions));
    }

    /**
     * Set POSIX owner of newly created files and directories.
     *
     * <p>Note that setting owner to any other value than the owner of the JVM
     * process is normally disallowed unless the JVM is running as the super user.
     *
     * <p>When unconfigured, no attempt will be made to set owner on
     * newly created files and directories.
     *
     * @param fileOwner the owner username (not numeric)
     * @throws IOException
     */
    public void setOwner(String fileOwner) throws IOException {
        UserPrincipalLookupService u = FileSystems.getDefault().getUserPrincipalLookupService();
        this.owner = Optional.of(u.lookupPrincipalByName(fileOwner));
    }

    /**
     * Set POSIX group of newly created files and directories.
     *
     * <p>When unconfigured, no attempt will be made to set group on
     * newly created files and directories.
     *
     * @param fileGroup the file group name (not numeric)
     * @throws IOException
     */
    public void setGroup(String fileGroup) throws IOException {
        UserPrincipalLookupService u = FileSystems.getDefault().getUserPrincipalLookupService();
        this.group = Optional.of(u.lookupPrincipalByGroupName(fileGroup));
    }
}
