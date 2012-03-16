package org.vortikal.resourcemanagement.edit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.apache.velocity.runtime.resource.ResourceManager;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.resourcemanagement.StructuredResourceDescription;
import org.vortikal.security.AuthenticationException;
import org.vortikal.text.html.HtmlFragment;
import org.vortikal.text.html.HtmlPageFilter;
import org.vortikal.text.html.HtmlPageParser;
import org.vortikal.util.text.JSON;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;

public class SharedTextProvider implements ReferenceDataProvider {

    private ResourceTypeTree resourceTypeTree;
    private HtmlPageFilter safeHtmlFilter;
    private HtmlPageParser htmlParser;

    /*TODO: Need better error handling */
    public Map<String, JSONObject> getSharedTextValues(String docType, String propName) {
        Path p = Path.fromString("/vrtx/fellestekst/" + docType + "/" + propName + ".html");
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        
        Map<String, JSONObject> m = new HashMap<String, JSONObject>();
        
        try {
            Resource r = repository.retrieve(token, p, false);
            if (!r.isPublished()) {
                return m;
            }
        } catch (Exception e) {
            return m;
        }
        try {
            List json = (List) JSON.getResource(p).getJSONObject("properties").get("shared-text-box");

            for (Object x : json) {
                JSONObject j = JSONObject.fromObject(x);
                m.put(j.getString("id"), filterDescription(j));
            }
            return m;
        } catch (Exception e) {
            return m;
        }
    }
    
    private JSONObject filterDescription(JSONObject j){
        
        String[] list = {"description-no","description-nn","description-en"};
        
        for(String descriptionKey : list){
            HtmlFragment fragment;
            try {
                fragment = htmlParser.parseFragment(j.getString(descriptionKey));
                fragment.filter(safeHtmlFilter);
                j.put(descriptionKey,fragment.getStringRepresentation());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        }
        return j;
    }


    @Override
    public void referenceData(Map model, HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path currentResource = requestContext.getResourceURI();
        Resource r = repository.retrieve(token, currentResource, false);

        ResourceTypeDefinition rtd = resourceTypeTree.getResourceTypeDefinitionByName(r.getResourceType());
        PropertyTypeDefinition[] ptdl = rtd.getPropertyTypeDefinitions();
        if (rtd.getPropertyTypeDefinitions() != null) {
            Map<String, Map<String, JSONObject>> m = new HashMap<String, Map<String, JSONObject>>();
            for (int i = 0; i < ptdl.length; i++) {
                if (ptdl[i] != null) {
                    Map editHints = (Map) ptdl[i].getMetadata().get("editingHints");
                    if (editHints != null && "vrtx-shared-text".equals(editHints.get("class"))) {
                        m.put(ptdl[i].getName(), getSharedTextValues(r.getResourceType(), ptdl[i].getName()));
                    }
                }
            }

            model.put("sharedTextProps", m);
        }
    }

    public ResourceTypeTree getResourceTypeTree() {
        return resourceTypeTree;
    }

    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public void setSafeHtmlFilter(HtmlPageFilter safeHtmlFilter) {
        this.safeHtmlFilter = safeHtmlFilter;
    }
    
    public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }
}
