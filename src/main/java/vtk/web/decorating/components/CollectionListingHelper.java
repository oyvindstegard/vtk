package vtk.web.decorating.components;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Lock;
import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.util.repository.DocumentPrincipalMetadataRetriever;
import vtk.web.search.EditInfo;

public class CollectionListingHelper {

    private Set<String> applicableResourceTypes;
    private DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever;

    /**
     * Check if a given principal has edit privileges on a property set and if other principal has locked it
     * 
     * @param repo
     *            The backend used to fetch the actual resource and
     *            corresponding acl
     * 
     * @param propSet
     *            The property set to check
     * 
     * @param token
     *            The security token used to retrieve the resource
     * 
     * @param principal
     *            The principal for whom we perform the check
     * 
     */
    public EditInfo checkResourceForEditLink(Repository repo, PropertySet propSet, String token, Principal principal)
            throws Exception {
        
        EditInfo editInfo = new EditInfo(false, false, null);
        if (token == null || principal == null) {
            return editInfo;
        }
        // Attempt check only if resource is NOT from Solr, AND resource-type is applicable for editing
        if (propSet.getPropertyByPrefix(null, MultiHostSearcher.MULTIHOST_RESOURCE_PROP_NAME) == null
         && isApplicableResourceType(propSet.getResourceType())) {
            try {
                Resource res = repo.retrieve(token, propSet.getURI(), true);
                return checkResourceForEditLink(repo, res, principal);
            } catch (Exception exception) {}
        }
        return editInfo;
    }

    /**
     * Check if a given principal has edit privileges on a resource and if other principal has locked it
     * 
     * @param repo
     *            The backend used to perform check
     * 
     * @param resource
     *            The resource to check
     * 
     * @param principal
     *            The principal for whom we perform the check
     * 
     */
    public EditInfo checkResourceForEditLink(Repository repo, Resource resource, Principal principal) {
        boolean canEdit = false;
        boolean canEditLocked = false;
        Principal lockedBy = null;

        try {

            boolean hasReadWrite = repo.isAuthorized(resource, RepositoryAction.READ_WRITE, principal,
                    false);
            if (hasReadWrite) {
                Lock lock = resource.getLock();
                boolean locked = lock != null && (principal == null || !principal.equals(lock.getPrincipal()));
                if (!locked) {
                    canEdit = true;
                } else {
                    canEditLocked = true;
                    lockedBy = lock.getPrincipal();
                }
            }
        } catch (Exception ex) {
            // Ignore, just don't allow editing
        }

        return new EditInfo(canEdit, canEditLocked, lockedBy);
    }

    /**
     * For a set of resources (property sets), get links to principal documents
     * (if they exist) for each principal of the type given by the supplied
     * principal type.
     * 
     * @param propertySets
     *            The set of resources to fetch principal objects from
     * 
     * @param preferredLocale
     *            The preferred locale to use when searching for principal
     *            documents
     * 
     * @param principalTypePropDef
     *            The principal type provided by the property set to search for,
     *            e.g. last modified of owner.
     * 
     */
    public Map<String, Principal> getPrincipalDocumentLinks(Set<PropertySet> propertySets, Locale preferredLocale,
            PropertyTypeDefinition principalTypePropDef) {

        Set<String> uids = new HashSet<String>();
        for (PropertySet ps : propertySets) {

            Property principalTypeProp = principalTypePropDef != null ? ps.getProperty(principalTypePropDef) : ps
                    .getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.MODIFIEDBY_PROP_NAME);

            if (principalTypeProp != null) {
                uids.add(principalTypeProp.getPrincipalValue().getName());
            }

        }

        return documentPrincipalMetadataRetriever.getPrincipalDocumentsMapByUid(uids, preferredLocale);
    }

    private boolean isApplicableResourceType(String rt) {
        return this.applicableResourceTypes.contains(rt);
    }

    @Required
    public void setApplicableResourceTypes(Set<String> applicableResourceTypes) {
        this.applicableResourceTypes = applicableResourceTypes;
    }

    @Required
    public void setDocumentPrincipalMetadataRetriever(
            DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever) {
        this.documentPrincipalMetadataRetriever = documentPrincipalMetadataRetriever;
    }

}
