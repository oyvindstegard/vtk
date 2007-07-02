/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.web.controller.properties;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.repository.HierarchicalVocabulary;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.Vocabulary;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;


/**
 * Controller for setting the <code>characterEncoding</code> property
 * on resources.
 */
public class VocabularyResolvingController implements Controller {

    private static Log logger = LogFactory.getLog(VocabularyResolvingController.class);

    private String viewName;
    private ResourceTypeTree resourceTypeTree;
    
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        String namespaceUrl = request.getParameter("namespace");
        String name = request.getParameter("name");
        String selected = request.getParameter("selected");
        Namespace namespace = this.resourceTypeTree.getNamespace(namespaceUrl);

        PropertyTypeDefinition propDef = 
            this.resourceTypeTree.findPropertyTypeDefinition(namespace, name);

        Vocabulary<Value> vocabulary = propDef.getVocabulary();
        
        Map<String, Object> model = new HashMap<String, Object>();
        
        if (vocabulary != null && (vocabulary instanceof HierarchicalVocabulary)) {
            HierarchicalVocabulary<Value> hv = (HierarchicalVocabulary<Value>) vocabulary;
            model.put("propertyDefinition", propDef);
            model.put("rootNodes", hv.getRootNodes());
            String[] selectedItems = getList(selected, hv);
            model.put("selected_nodes", selectedItems);
            String[] tmp = (String[]) model.get("selected_nodes");
            logger.warn(Arrays.asList(tmp));
        }
        
        return new ModelAndView(this.viewName, model );
    }

    private String[] getList(String selected, HierarchicalVocabulary<Value> hv) {
        List<String> result = new ArrayList<String>();
        if (selected == null || selected.equals("")) {
            return result.toArray(new String[0]);
        }
        StringTokenizer st = new StringTokenizer(selected, ",");

        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim();
            if (hv.getDescendantsAndSelf(new Value(s)) != null) {
                result.add(s);
            }
        }
                
        return result.toArray(new String[result.size()]);
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
    

}

