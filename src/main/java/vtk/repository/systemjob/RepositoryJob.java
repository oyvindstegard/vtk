/* Copyright (c) 2012, 2016, University of Oslo, Norway
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

package vtk.repository.systemjob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.cluster.ClusterAware;
import vtk.cluster.ClusterRole;
import vtk.repository.Repository;
import vtk.repository.ResourceTypeTree;
import vtk.repository.SystemChangeContext;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.scheduling.AbstractTask;
import vtk.security.SecurityContext;

/**
 * Sets up thread local security context, system change context and executes
 * repository job.
 *
 * <p>It is assumed that jobs of this type will potentially perform modifications
 * to the repository, and if running in a clustered scenario, these jobs will not
 * execute on SLAVE nodes.
 *
 * <p>XXX not sure if cluster role should be checked at this level, or some lower or higher level..
 */
public abstract class RepositoryJob extends AbstractTask implements ClusterAware {
    private Optional<ClusterRole> clusterRole = Optional.empty();

    private SecurityContext securityContext;
    private Repository repository;

    private PropertyTypeDefinition systemJobStatusPropDef;
    private ResourceTypeTree resourceTypeTree;
    private List<String> affectedPropertyNames = Collections.emptyList();
    private List<PropertyType.Type> affectedPropertyTypes = Collections.emptyList();
    private boolean ignoreLockingOnStore = false;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void run() {
        if (clusterRole.isPresent() && clusterRole.get() == ClusterRole.SLAVE) {
            logger.debug("Not running scheduled repository job " + getId() + ": cluster node has slave role");
            return;
        }

        try {
            SystemChangeContext systemChangeContext =
                    new SystemChangeContext(getId(), securityContext,
                            lookupPropDefs(affectedPropertyNames),
                            affectedPropertyTypes,
                            systemJobStatusPropDef,
                            ignoreLockingOnStore);

            executeWithRepository(repository, systemChangeContext);
        }
        catch (Throwable t) {
            logger.error("Error executing repository job", t);
        }
    }

    private List<PropertyTypeDefinition> lookupPropDefs(List<String> qualifiedNames) {
        List<PropertyTypeDefinition> propDefs = new ArrayList<>(qualifiedNames.size());
        for (String qname: qualifiedNames) {
            PropertyTypeDefinition def = resourceTypeTree.getPropertyDefinitionByName(qname);
            if (def == null) {
                throw new IllegalStateException("Unable to find property definition '" + qname + "'");
            }
            propDefs.add(def);
        }
        return propDefs;
    }

    /**
     * This method will be called every time job i scheduled to run, but it will
     * not be called if running in a clustered setup and cluster role is {@link ClusterRole#SLAVE}.
     * @param repository
     * @param context
     * @throws Exception
     */
    public abstract void executeWithRepository(Repository repository,
            SystemChangeContext context) throws Exception;

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setSecurityContext(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Required
    public void setSystemJobStatusPropDef(PropertyTypeDefinition systemJobStatusPropDef) {
        this.systemJobStatusPropDef = systemJobStatusPropDef;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public void setAffectedPropertyNames(List<String> affectedPropertyNames) {
        this.affectedPropertyNames = Objects.requireNonNull(affectedPropertyNames);
    }

    public void setAffectedPropertyTypes(List<PropertyType.Type> affectedPropertyTypes) {
        this.affectedPropertyTypes = Collections
                .unmodifiableList(Objects.requireNonNull(affectedPropertyTypes));
    }

    /**
     * Set whether the provided {@link SystemChangeContext} should have the
     * {@link SystemChangeContext#ignoreLocking} flag set.
     *
     * Defaults to <code>false</code>.
     *
     * @param ignoreLockingOnStore
     */
    public void setIgnoreLockingOnStore(boolean ignoreLockingOnStore) {
        this.ignoreLockingOnStore = ignoreLockingOnStore;
    }

    @Override
    public void roleChange(ClusterRole role) {
        this.clusterRole = Optional.of(role);
    }

    /**
     * Get current current cluster role for this node.
     *
     * <p>
     * Jobs may need to alter their behaviour in accordance with the current
     * cluster role, and this can be used to query status. Also note that {@link RepositoryJob} will
     * not invoke
     * {@link #executeWithRepository(vtk.repository.Repository, vtk.repository.SystemChangeContext) executeWithRepository}
     * if cluster role is present and is SLAVE.
     *
     * @return an optional <code>ClusterRole</code> instance
     */
    protected Optional<ClusterRole> clusterRole() {
        return clusterRole;
    }
}
