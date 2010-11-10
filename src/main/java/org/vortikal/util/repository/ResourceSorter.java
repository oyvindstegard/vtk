/* Copyright (c) 2004, University of Oslo, Norway
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
package org.vortikal.util.repository;

import java.util.Arrays;
import java.util.Comparator;

import org.vortikal.repository.Acl;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Resource;
import org.vortikal.security.PrincipalFactory;

/**
 * Utility class for sorting a set of resources in various ways.
 *
 * NB String value order is not locale-aware.
 */
public class ResourceSorter {

    public static enum Order {
        BY_NAME,
        BY_DATE,
        BY_OWNER,
        BY_LOCKS,
        BY_FILESIZE,
        BY_CONTENT_TYPE,
        BY_PERMISSIONS
    }

    public static void sort(Resource[] resources, Order order, boolean inverted) {
        Comparator<Resource> comparator = null;

        switch (order) {
        case BY_NAME:
            comparator = new ResourceNameComparator(inverted);
            break;

        case BY_DATE:
            comparator = new ResourceDateComparator(inverted);
            break;

        case BY_OWNER:
            comparator = new ResourceOwnerComparator(inverted);
            break;

        case BY_LOCKS:
            comparator = new ResourceLockComparator(inverted);
            break;

        case BY_FILESIZE:
            comparator = new FileSizeComparator(inverted);
            break;

        case BY_CONTENT_TYPE:
            comparator = new ContentTypeComparator(inverted);
            break;

        case BY_PERMISSIONS:
            comparator = new PermissionsComparator(inverted);
            break;

        default:
            comparator = new ResourceNameComparator(inverted);
            break;
        }

        Arrays.sort(resources, comparator);
    }

    private static class ResourceNameComparator implements Comparator<Resource> {
        private boolean invert = false;

        public ResourceNameComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {
            if (!this.invert) {
                return r1.getName().compareTo(r2.getName());
            }
            return r2.getName().compareTo(r1.getName());
        }
    }

    private static class ResourceDateComparator implements Comparator<Resource> {
        private boolean invert = false;

        public ResourceDateComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {
            if (!this.invert) {
                return r1.getLastModified().compareTo(r2.getLastModified());
            }
            return r2.getLastModified().compareTo(r1.getLastModified());
        }
    }

    private static class ResourceOwnerComparator implements Comparator<Resource> {
        private boolean invert = false;

        public ResourceOwnerComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {
            if (!this.invert) {
                return r1.getOwner().getQualifiedName().compareTo(
                        r2.getOwner().getQualifiedName());
            }
            return r2.getOwner().getQualifiedName().compareTo(
                    r1.getOwner().getQualifiedName());
        }
    }

    private static class ResourceLockComparator implements Comparator<Resource> {
        private boolean invert = false;

        public ResourceLockComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {

            String owner1 = "";
            String owner2 = "";

            if (r1.getLock() != null) {
                owner1 = r1.getLock().getOwnerInfo();
            }

            if (r2.getLock() != null) {
                owner2 = r2.getLock().getOwnerInfo();
            }

            if (!this.invert) {
                return owner1.compareTo(owner2);
            }
            return owner2.compareTo(owner1);
        }
    }

    private static class FileSizeComparator implements Comparator<Resource> {
        private boolean invert = false;

        public FileSizeComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {
            Long size1 = r1.getContentLength();
            Long size2 = r2.getContentLength();

            if (!this.invert) {
                return size1.compareTo(size2);
            }
            return size2.compareTo(size1);
        }
    }

    private static class ContentTypeComparator implements Comparator<Resource> {
        private boolean invert = false;

        public ContentTypeComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {
            if (r1.isCollection() && r2.isCollection()) {
                return this.invert ? r2.getName().compareTo(r1.getName()) : r1.getName()
                        .compareTo(r2.getName());
            }

            if (r1.isCollection()) {
                return this.invert ? -1 : 1;
            }

            if (r2.isCollection()) {
                return this.invert ? 1 : -1;
            }

            return this.invert ? r2.getContentType().compareTo(r1.getContentType()) : r1
                    .getContentType().compareTo(r2.getContentType());
        }
    }

    private static class PermissionsComparator implements Comparator<Resource> {
        private boolean invert = false;

        public PermissionsComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(Resource r1, Resource r2) {
            boolean r1ReadAll = isReadAll(r1);
            boolean r2ReadAll = isReadAll(r2);
            if (!this.invert) {
                return compare(r1ReadAll, r2ReadAll);
            }
            return compare(r2ReadAll, r1ReadAll);
        }

        private boolean isReadAll(Resource r) {
            Acl acl = r.getAcl();
            return acl.containsEntry(Privilege.READ, PrincipalFactory.ALL)
                || acl.containsEntry(Privilege.READ_PROCESSED, PrincipalFactory.ALL);
        }
        
        private int compare(boolean r1ReadAll, boolean r2ReadAll) {
            if (r1ReadAll == true && r2ReadAll == false) {
                return 1;
            } else if (r1ReadAll == false && r2ReadAll == true) {
                return -1;
            }
            return 0;
        }
        
    }
}
