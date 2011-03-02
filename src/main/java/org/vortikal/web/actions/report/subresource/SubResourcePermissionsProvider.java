/* Copyright (c) 2011, University of Oslo, Norway
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

import javax.servlet.http.HttpServletRequest;

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
import org.vortikal.security.PrincipalFactory;

public class SubResourcePermissionsProvider {

    private Searcher searcher;
    private Repository repository;

    private static Log logger = LogFactory.getLog(SubResourcePermissionsProvider.class);
    
    public List<SubResourcePermissions> buildSearchAndPopulateSubresources(String uri, String token, HttpServletRequest request) {

        // MainQuery (depth + 1 from uri and all resources)
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
        
        List<SubResourcePermissions> subresources = populateSubResources(token, rs, request);
        return subresources;
    }
    
    @SuppressWarnings("unchecked")
    private List<SubResourcePermissions> populateSubResources(String token, ResultSet rs, HttpServletRequest request) {
        List<PropertySet> results = rs.getAllResults();
        List<SubResourcePermissions> subresources = new ArrayList();
        
        Resource r = null;
        
        for(PropertySet result : results) {
          String rURI = result.getURI().toString();
          String rName = result.getName();
          String rTitle = "";
          boolean rIsCollection = false;
          boolean rIsReadRestricted = false;
          boolean rIsInheritedAcl = false;
          String rRead = "";
          String rWrite = "";
          String rAdmin = "";
          
          try {
            r = this.repository.retrieve(token, result.getURI(), true);
            if (r != null) {
              rTitle = r.getTitle();
              rIsCollection = r.isCollection();
              if(r.isReadRestricted()) {
                rIsReadRestricted = true;
              }
              if(r.isInheritedAcl()) {
                rIsInheritedAcl = true;
              }
              
              Acl acl = r.getAcl();
              for (Privilege action: Privilege.values()) {
                  String actionName = action.getName();
                  Principal[] privilegedUsers = acl.listPrivilegedUsers(action);
                  Principal[] privilegedGroups = acl.listPrivilegedGroups(action);
                  Principal[] privilegedPseudoPrincipals = acl.listPrivilegedPseudoPrincipals(action);
                  StringBuilder combined = new StringBuilder();
                  int i = 0; 
                  int len = privilegedPseudoPrincipals.length + privilegedUsers.length + privilegedGroups.length;
                  boolean all = false;
                  
                  for(Principal p : privilegedPseudoPrincipals) {
                    String pseudo = this.getLocalizedTitle(request, "pseudoPrincipal." + p.getName(), null);
                    if(p.getName() == PrincipalFactory.NAME_OWNER) {
                      pseudo += "&nbsp;(" + r.getOwner().getDescription() + ")";
                    }
                    if(p.getName() == PrincipalFactory.NAME_ALL) {
                      all = true;
                      combined.append(pseudo);
                    }
                    if((len == 1 || i == len - 1) && !all) {
                      combined.append(pseudo);
                    } else if(!all) {
                      combined.append(pseudo + ", ");
                    }
                    i++;
                  }
                  if(!all) {
                    for(Principal p : privilegedUsers) {
                      if(len == 1 || i == len - 1) {
                        combined.append(p.getDescription());  
                      } else {
                        combined.append(p.getDescription() + ", ");
                      }
                      i++;
                    }
                    for(Principal p : privilegedGroups) {
                      if(len == 1 || i == len - 1) {
                        combined.append(p.getDescription());  
                      } else {
                        combined.append(p.getDescription() + ", ");
                      }
                      i++;
                    }
                  }
                  if(actionName == "read") {
                    rRead = combined.toString();
                  } else if(actionName == "write") {
                    rWrite = combined.toString();
                  } else if(actionName == "all") {
                    rAdmin = combined.toString();
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
          subresources.add(new SubResourcePermissions(rURI, rName, rTitle, rIsCollection, rIsReadRestricted, 
                                                      rIsInheritedAcl, rRead, rWrite, rAdmin));
        }
        return subresources;
    }
    
    public String getLocalizedTitle(HttpServletRequest request, String key, Object[] params) {
        org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(request);
        if (params != null) {
            return springRequestContext.getMessage(key, params);
        }
        return springRequestContext.getMessage(key);
    }

    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
}
