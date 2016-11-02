/* Copyright (c) 2006,2007,2014 University of Oslo, Norway
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
package vtk.repository.index.consistency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.PropertySetImpl;
import vtk.repository.index.IndexException;
import vtk.repository.index.PropertySetIndex;
import vtk.repository.index.PropertySetIndexRandomAccessor;
import vtk.repository.index.PropertySetIndexRandomAccessor.PropertySetInternalData;
import vtk.repository.index.StorageCorruptionException;
import vtk.repository.index.mapping.DocumentMappingException;
import vtk.repository.store.IndexDao;
import vtk.repository.store.PropertySetHandler;

/**
 * Check consistency and optionally repair errors afterwords.
 * 
 * NOTE ! Usage of this class requires external locking of the index in question, if 
 * writing operations are known to occur during testing. This is necessary
 * so that the index isn't modified during testing, or between testing
 * and the call to {@link #repairErrors(boolean)} (in case of errors present).
 * 
 * @author oyviste
 */
public class ConsistencyCheck {

    private static final Logger LOG = LoggerFactory.getLogger(ConsistencyCheck.class);

    /**
     * Error limit. If more than this number of errors are encountered during check, 
     * an exception will be thrown. This is so as to avoid filling memory with
     * error instances, which do carry some amount of data.
     */
    public static final int ERROR_LIMIT = 10000;

    private final IndexDao indexDao;
    private final PropertySetIndex index;

    private final List<AbstractConsistencyError> errors = 
        new ArrayList<AbstractConsistencyError>(); // List of detected inconsistencies
    private boolean completed = false; 
    

    /**
     * 
     * @param index
     * @param indexDataAccessor
     */
    private ConsistencyCheck(PropertySetIndex index,
                             IndexDao indexDao) {
        this.indexDao = indexDao;
        this.index = index;
    }

    /**
     * Prepare a ConsistencyCheck instance by running the check.
     * 
     * 
     * @param index
     * @param indexDataAccessor
     * @return
     */
    public static ConsistencyCheck run(PropertySetIndex index,
            IndexDao indexDao) throws IndexException,
            ConsistencyCheckException, StorageCorruptionException {

        ConsistencyCheck check = new ConsistencyCheck(index, indexDao);
        check.runInternal();
        return check;
    }

    @SuppressWarnings("unchecked")
    private void runInternal() throws IndexException,
            ConsistencyCheckException, StorageCorruptionException {

        String indexId = this.index.getId();

        LOG.info("Running consistency check on index '" + indexId + "'");

        Iterator<Path> indexUriIterator = null;
        PropertySetIndexRandomAccessor randomIndexAccessor = null;

        try {
            
            LOG.info("Running storage corruption test ..");
            // This has the positive side effect of warming up the Lucene reader cache
            this.index.validateStorageFacility();
            LOG.info("Storage corruption test passed.");

            indexUriIterator = this.index.orderedUriIterator();
            randomIndexAccessor = this.index.randomAccessor();
            
            LOG.info("Running consistency check ..");
            runConsistencyCheck(randomIndexAccessor, indexUriIterator);
            
            if (this.errors.size() > 0) {
                LOG.warn("Consistency check completed, " + this.errors.size() + " inconsistencies detected.");
            } else {
                LOG.info("Consistency check completed successfully without any errors detected.");
            }

        } catch (StorageCorruptionException sce) {
            LOG.warn("Storage corruption test failed: " + sce.getMessage());
            throw sce; // Re-throw, since we can't work on or fix a corrupted index
        } finally {
            // Clean up resources
            if (indexUriIterator != null) this.index.close(indexUriIterator);
            if (randomIndexAccessor != null) randomIndexAccessor.close();
        }

        this.completed = true;
    }
    
    @SuppressWarnings("unchecked")
    private void runConsistencyCheck(final PropertySetIndexRandomAccessor randomIndexAccessor,
                                     final Iterator<Path> indexUriIterator) 
        throws IndexException {
        
        final Set<Path> validURIs = new HashSet<Path>(30000);
        
        PropertySetHandler handler = new PropertySetHandler() {

            private int progressCount = 0;
            
            @Override
            public void handlePropertySet(PropertySet propertySet, 
                                          Acl acl) {

                PropertySetImpl daoPropSet = (PropertySetImpl)propertySet;
                Path currentUri = daoPropSet.getURI();
                int indexInstances = 
                    randomIndexAccessor.countInstances(currentUri);
                
                if (indexInstances == 0) {
                    // Missing in index
                    ConsistencyCheck.this.addError(
                            new MissingInconsistency(currentUri, daoPropSet, acl));
                    return;
                } else  if (indexInstances == 1) {
                    // OK, only a single instance exists, verify the instance data
                    try {
                        ConsistencyCheck.this.checkPropertySet(currentUri,
                                                  randomIndexAccessor, 
                                                  daoPropSet,
                                                  acl);
                    } catch (IOException io) {
                        throw new ConsistencyCheckException("IOException while running consistency check", io);
                    }
                } else {
                    // Multiples inconsistency
                    ConsistencyCheck.this.addError(
                            new MultiplesInconsistency(currentUri, indexInstances, daoPropSet, acl));
                }
                // Add to set of valid index property set URIs
                validURIs.add(currentUri);
                
                // Progress logging
                if (++this.progressCount % 10000 == 0) {
                    LOG.info("Progress: " + this.progressCount + " property sets checked");
                }

                // Interrupt checking
                if (Thread.interrupted()) {
                    throw new IndexException("Interrupted during consistency check");
                }
            }
        };
        
        this.indexDao.orderedPropertySetIteration(handler);
        
        // Need to make a complete pass over index URIs to detect dangling inconsistencies
        LOG.info("Checking for dangling property sets in index ..");
        while (indexUriIterator.hasNext()) {
            Path currentUri = indexUriIterator.next();
            if (! validURIs.contains(currentUri)) {
                this.addError(new DanglingInconsistency(currentUri));
            }

            // Interrupt checking
            if (Thread.interrupted()) {
                throw new IndexException("Interrupted during consistency check");
            }
        }
    }
    
    /**
     * Verify consistency of a single index property set. Checks resource ID (= index UUID) and
     * the ACL_INHERITED_FROM ID.
     * 
     * @param indexUri
     * @param randomIndexAccessor
     * @param repoPropSet
     * @throws IOException
     * @throws IndexException
     */
    private void checkPropertySet(Path indexUri, 
                                  PropertySetIndexRandomAccessor randomIndexAccessor, 
                                  PropertySetImpl repoPropSet,
                                  Acl repoAcl) 
        throws IOException, IndexException {
    
        try {
            PropertySet indexPropSet = randomIndexAccessor.getPropertySetByURI(indexUri);
            PropertySetInternalData indexPropSetInternalData = randomIndexAccessor.getPropertySetInternalData(indexUri);
            
            int indexUUID = indexPropSetInternalData.getResourceId();
            int daoUUID = repoPropSet.getID();
            
            if (indexUUID != daoUUID) {
                // Invalid UUID (this can also be considered a dangling inconsistency)
                addError(new InvalidUUIDInconsistency(indexUri, repoPropSet, repoAcl, indexUUID, daoUUID));
                return;
            }
            
            // Check ACL data
            Acl indexAcl = indexPropSetInternalData.getAcl();
            
            if (!repoAcl.equals(indexAcl)) {
                addError(new InvalidACLInconsistency(indexUri, repoPropSet, repoAcl, indexAcl));
                return;
            }
            
            // Check ACL inherited from
            int indexAclInheritedFrom = indexPropSetInternalData.getAclInheritedFromId();
            int daoACL = repoPropSet.getAclInheritedFrom();
            if (indexAclInheritedFrom != daoACL) {
                // Invalid ACL inherited from
                addError(new InvalidACLInheritedFromInconsistency(indexUri, repoPropSet, repoAcl, indexAclInheritedFrom, daoACL));
                return;
            }
            
            // Check resource type
            if (!repoPropSet.getResourceType().equals(indexPropSet.getResourceType())) {
                addError(new InvalidResourceTypeInconsistency(indexUri, repoPropSet, repoAcl,
                                                              indexPropSet.getResourceType(), repoPropSet.getResourceType()));
                return;
            }
            
            // Verify that all indexed properties exist in repo prop set.
            // Note that index prop set will typically contain fewer properties due
            // to no indexing of dead or binary properties.
            for (Property indexProp: indexPropSet) {
                Property repoProp = repoPropSet.getProperty(indexProp.getDefinition());
                if (repoProp != null) {
                    if (!propertyValuesEqual(indexProp, repoProp)) {
                        addError(new PropertyValueInconsistency(indexUri, repoPropSet, repoAcl, indexProp, repoProp));
                        return;
                    }
                } else {
                    // Dangling property in index propset
                    addError(new DanglingPropertyInconsistency(indexUri, repoPropSet, repoAcl, indexProp));
                    return;
                }
            }
            
        } catch (DocumentMappingException dme) {
            // Unmappable inconsistency
            addError(new UnmappableConsistencyError(indexUri, dme, repoPropSet, repoAcl));
        }
    
    }
    
    private boolean propertyValuesEqual(Property p1, Property p2) {
        if (p1.getDefinition().isMultiple()) {
            if (!p2.getDefinition().isMultiple()) {
                return false;
            }
            
            if (!Arrays.deepEquals(p1.getValues(), p2.getValues())) {
                return false;
            }
            
            return true;
        } else {
            if (p2.getDefinition().isMultiple()) return false;
            
            return p1.getValue().equals(p2.getValue());
        }
    }

    /**
     * Repair all encountered errors.
     * 
     * @param abortOnFailure
     *            If <code>true</code>, repairing will abort if an error
     *            occurs. If <code>false</code>, the repairing will continue,
     *            even if exceptions occur.
     * 
     */
    public void repairErrors(boolean abortOnFailure) throws IndexException {

        if (!this.completed) {
            throw new IllegalStateException(
                    "Cannot repair errors, the consistency check did not complete successfully.");
        }

        for(AbstractConsistencyError error: this.errors) {
            try {
              if (error.canRepair()) {
                  error.repair(this.index);
              } else {
                  LOG.warn("Error cannot be repaired: '" + error.getDescription() + "'");
              }
            } catch (IndexException ie) {
                if (abortOnFailure) {
                    LOG.warn("Aborting error repairing, exception '" + ie.getMessage() 
                            + "' while repairing error with description '" 
                            + error.getDescription() + "'");
                    throw ie;
                }
            }
        }
        this.index.commit();
    }
    
    private void addError(AbstractConsistencyError error) throws ConsistencyCheckException {

        if (this.errors.size() > ERROR_LIMIT) {
            this.completed = true;
            throw new TooManyErrorsException("Too many errors, limit is " + ERROR_LIMIT + ", consider re-indexing.", this);
        }

        LOG.warn("Error found: " + error);
        
        this.errors.add(error);
    }

    public List<AbstractConsistencyError> getErrors() {
        return Collections.unmodifiableList(this.errors);
    }
    
}
