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
package vtk.repository.index.consistency;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.PropertySetImpl;
import vtk.repository.index.IndexException;
import vtk.repository.index.PropertySetIndex;

/**
 * Represents error where multiple index documents (property sets) exist for a single URI.
 * 
 */
public class MultiplesInconsistency extends RequireOriginalDataConsistencyError {

    private static final Logger LOG = LoggerFactory.getLogger(MultiplesInconsistency.class);
    
    private final int multiples;
    
    public MultiplesInconsistency(Path uri, int multiples, 
                                  PropertySetImpl repositoryPropSet, 
                                  Acl acl) {
        super(uri, repositoryPropSet, acl);
        this.multiples = multiples;
    }
    
    @Override
    public boolean canRepair() {
        return true;
    }
    
    @Override
    public String getDescription() {
        return "Multiples inconsistency, there are " 
            + multiples + " property sets in index at URI '" + getUri() + "'";
    }

    /**
     * Repair by removing all property sets for the URI, then re-adding a pristine copy from the
     * repository.
     * @param index
     */
    @Override
    protected void repair(PropertySetIndex index) throws IndexException {
        LOG.info("Repairing multiples inconsistency for URI '" + getUri() 
                                                    + "' (" + multiples + " multiples)");
        index.updatePropertySet(super.repositoryPropSet, super.acl);
    }

    @Override
    public String toString() {
        return "MultipleConsistencyError[URI = '" + getUri() + "', number of multiples in index: " 
                                                                            + this.multiples + "]";
    }
    
}
