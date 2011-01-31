/* Copyright (c) 2010, University of Oslo, Norway
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
package org.vortikal.web.actions.report.subresource;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.repository.Acl;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Searcher;
import org.vortikal.repository.search.WildcardPropertySelect;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.UriDepthQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;

public class SubResourcePermissionsProvider {

    private Searcher searcher;
    private Repository repository;

    private static Log logger = LogFactory.getLog(SubResourcePermissionsProvider.class);
    
    public List<SubResourcePermissions> buildSearchAndPopulateSubresources(String uri, String token) {

        // MainQuery (depth + 1 from uri)
        Path url = Path.fromString(uri);
        int depth = url.getDepth() + 1;
        AndQuery mainQuery = new AndQuery();
        mainQuery.add(new UriPrefixQuery(url.toString()));
        mainQuery.add(new UriDepthQuery(depth));
        Search search = new Search();
        search.setQuery(mainQuery);
        search.setLimit(500);
        search.setPropertySelect(new WildcardPropertySelect());
        ResultSet rs = searcher.execute(token, search);
        
        List<SubResourcePermissions> subresources = populateSubResources(token, rs);
        return subresources;
    }
    
    @SuppressWarnings("unchecked")
    private List<SubResourcePermissions> populateSubResources(String token, ResultSet rs) {
        List<PropertySet> results = rs.getAllResults();
        List<SubResourcePermissions> subresources = new ArrayList();
        
        Resource res = null;
        for(PropertySet result : results) {
          String resourceURI = result.getURI().toString();
          String resourceName = result.getName();
          String resourceTitle = "";
          boolean resourceisCollection = false;
          boolean resourceIsReadRestricted = false;
          boolean resourceIsInheritedAcl = false;
          String resourceRead = "";
          String resourceWrite = "";
          String resourceAdmin = "";
          try {
            res = this.repository.retrieve(token, result.getURI(), true);
            if (res != null) {
              resourceTitle = res.getTitle();
              resourceisCollection = res.isCollection();
              if(res.isReadRestricted()) {
                resourceIsReadRestricted = true;
              }
              if(res.isInheritedAcl()) {
                resourceIsInheritedAcl = true;
              }
              
              Acl acl = res.getAcl();
              for (Privilege action: Privilege.values()) {
                  String actionName = action.getName();
                  Principal[] privilegedUsers = acl.listPrivilegedUsers(action);
                  Principal[] privilegedGroups = acl.listPrivilegedGroups(action);
                  Principal[] privilegedPseudoPrincipals = acl.listPrivilegedPseudoPrincipals(action);
                  StringBuilder combined = new StringBuilder();
                  
                  
                  int i = 0; 
                  int len = privilegedPseudoPrincipals.length + privilegedUsers.length + privilegedGroups.length;
                  int breakPoint = 3; // break on every 3 principals
                  for(Principal p : privilegedPseudoPrincipals) {
                    if(i % breakPoint == 0 && i > 0) {
                      combined.append ("<br />");  
                    }
                    if(len == 1 || i == len - 1) {
                      combined.append(p.getName());  
                    } else {
                      combined.append(p.getName() + ", ");
                    }
                    i++;
                  }
                  for(Principal p : privilegedUsers) {
                    if(i % breakPoint == 0 && i > 0) {
                      combined.append ("<br />");  
                    }
                    if(len == 1 || i == len - 1) {
                      combined.append(p.getName());  
                    } else {
                      combined.append(p.getName() + ", ");
                    }
                    i++;
                  }
                  for(Principal p : privilegedGroups) {
                    if(i % breakPoint == 0 && i > 0) {
                      combined.append ("<br />");  
                    }
                    if(len == 1 || i == len - 1) {
                      combined.append(p.getName());  
                    } else {
                      combined.append(p.getName() + ", ");
                    }
                    i++;
                  }
                  if(actionName == "read") {
                    resourceRead = combined.toString();
                  } else if(actionName == "write") {
                    resourceWrite = combined.toString();
                  } else if(actionName == "all") {
                    resourceAdmin = combined.toString();
                  }
              }
              
            }
          } catch (ResourceNotFoundException e) {
            logger.error("ResourceNotFoundException " + e.getMessage());
          } catch (AuthorizationException e) {
            logger.error("AuthorizationException " + e.getMessage());
          } catch (AuthenticationException e) {
            logger.error("AuthenticationException " + e.getMessage());
          } catch (Exception e) {
            logger.error("Exception " + e.getMessage());
          }
          subresources.add(new SubResourcePermissions(resourceURI, resourceName, resourceTitle, resourceisCollection, 
                                                      resourceIsReadRestricted, resourceIsInheritedAcl, resourceRead, resourceWrite, resourceAdmin));
        }
        return subresources;
    }

    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
}
