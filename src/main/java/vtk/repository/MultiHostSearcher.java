/* Copyright (c) 2012, University of Oslo, Norway
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
package vtk.repository;

import java.util.Set;

import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.web.service.URL;

/*
 * XXX this class does not belong in the vtk.repository package,
 * or even in the VTK project in its current form !
 *
 * XXX2: we really need to clean up this mess now !!
 */
public class MultiHostSearcher {

    public static final String URL_PROP_NAME = "solr.url";
    public static final String LANG_PROP_NAME = "solr.lang";
    public static final String MULTIHOST_RESOURCE_PROP_NAME = "solr.isSolrResource";
    public static final String NAME_PROP_NAME = "solr.name";

    private MultiHostSearchComponent multiHostSearchComponent;
    private boolean enabled = true; // hack to allow disabling use Solr for repository queries even though it is configured..

    public ResultSet search(String token, Search search) {
        if (enabled && multiHostSearchComponent != null) {
            return multiHostSearchComponent.search(token, search);
        }
        return null;
    }

    public PropertySet retrieve(String token, URL url) {
        if (enabled && multiHostSearchComponent != null) {
            return multiHostSearchComponent.retrieve(token, url);
        }
        return null;
    }

    public Set<PropertySet> retrieve(String token, Set<URL> urls) {
        if (enabled && multiHostSearchComponent != null) {
            return multiHostSearchComponent.retrieve(token, urls);
        }
        return null;
    }

    public boolean isMultiHostSearchEnabled() {
        return enabled && this.multiHostSearchComponent != null;
    }

    public void setMultiHostSearchComponent(MultiHostSearchComponent multiHostSearchComponent) {
        this.multiHostSearchComponent = multiHostSearchComponent;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
