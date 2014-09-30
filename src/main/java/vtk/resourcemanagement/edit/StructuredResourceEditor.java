/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.resourcemanagement.edit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.Repository.Depth;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.repository.store.Revisions;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.resourcemanagement.property.EditablePropertyDescription;
import vtk.resourcemanagement.property.JSONPropertyAttributeDescription;
import vtk.resourcemanagement.property.JSONPropertyDescription;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.security.Principal;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

@Controller
public class StructuredResourceEditor  {
    private StructuredResourceManager resourceManager;
    private HtmlPageFilter safeHtmlFilter;
    private Service listComponentsService;
    private Locale defaultLocale;
    private String formView;
    private String successView;

    @RequestMapping(method=RequestMethod.GET)
    public ModelAndView get() throws Exception {
        FormSubmitCommand cmd = formBackingObject();
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("form", cmd);
        return new ModelAndView(getFormView(), model);
    }
    
    @RequestMapping(method=RequestMethod.POST)
    public ModelAndView post(HttpServletRequest request) throws Exception {
        FormSubmitCommand form = formBackingObject();
        FormDataBinder binder = new FormDataBinder(form, "form", form.getResource().getType());
        binder.bind(request);
        
        if (form.getCancelAction() != null) {
            unlock();
            return new ModelAndView(getSuccessView());
        }
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("form", form);
        form.sync();
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        String token = requestContext.getSecurityToken();

        boolean saveWorkingCopy = form.getSaveWorkingCopyAction() != null
                || (form.getUpdateAction() != null && form.isWorkingCopy())
                || (form.getUpdateViewAction() != null && form.isWorkingCopy());

        boolean makePublicVersion = form.getMakePublicVersionAction() != null;
        boolean deleteWorkingCopy = form.getDeleteWorkingCopyAction() != null;

        Revision workingCopy = null;

        for (Revision rev : repository.getRevisions(token, uri)) {
            if (rev.getType() == Revision.Type.WORKING_COPY) {
                workingCopy = rev;
                break;
            }
        }

        if (deleteWorkingCopy && workingCopy != null) {
            repository.deleteRevision(token, uri, workingCopy);
            model.put("form", formBackingObject());
            return new ModelAndView(getFormView(), model);
        }

        byte[] buffer = form.getResource().toJSON().toString(3).getBytes("utf-8");
        InputStream stream = new ByteArrayInputStream(buffer);

        if (saveWorkingCopy && workingCopy == null) {
            workingCopy = repository.createRevision(token, uri, Revision.Type.WORKING_COPY);
        }

        if (makePublicVersion && workingCopy != null) {
            repository.createRevision(token, uri, Revision.Type.REGULAR);

            repository.storeContent(token, uri, stream, workingCopy);
            repository.storeContent(token, uri, repository.getInputStream(token, uri, false, workingCopy));
            repository.deleteRevision(token, uri, workingCopy);
            form.setWorkingCopy(false);

        } else if (saveWorkingCopy) {
            repository.storeContent(token, uri, stream, workingCopy);
            form.setWorkingCopy(true);

        } else {
            List<Revision> revisions = repository.getRevisions(token, uri);
            Revision prev = revisions.size() == 0 ? null : revisions.get(0);
            String checksum = Revisions.checksum(buffer);

            if (prev == null || !checksum.equals(prev.getChecksum())) {
                // Take snapshot of previous version:
                repository.createRevision(token, uri, Revision.Type.REGULAR);
            }
            repository.storeContent(token, uri, stream);
            form.setWorkingCopy(false);
        }

        if (form.getUpdateViewAction() != null) {
            unlock();
            return new ModelAndView(getSuccessView());
        }
        return new ModelAndView(getFormView(), model);
    }


    private FormSubmitCommand formBackingObject() throws Exception {
        lock();
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Principal principal = requestContext.getPrincipal();
        Resource resource = repository.retrieve(token, uri, false);

        boolean published = resource.isPublished();
        boolean hasPublishDate = resource.hasPublishDate();
        boolean onlyWriteUnpublished = !repository.authorize(principal, resource.getAcl(), Privilege.READ_WRITE);

        Revision workingCopy = null;
        for (Revision rev : repository.getRevisions(token, uri)) {
            if (rev.getType() == Revision.Type.WORKING_COPY) {
                workingCopy = rev;
                break;
            }
        }
        InputStream stream = null;
        if (workingCopy != null) {
            stream = repository.getInputStream(token, uri, true, workingCopy);
        } else {
            stream = repository.getInputStream(token, uri, true);
        }
        String encoding = resource.getCharacterEncoding();
        if (encoding == null) {
            encoding = "utf-8";
        }
        StructuredResourceDescription description = resourceManager.get(resource.getResourceType());
        StructuredResource structuredResource = description.buildResource(stream);

        URL url = RequestContext.getRequestContext().getService().constructURL(uri);
        URL listComponentServiceURL = listComponentsService.constructURL(uri);

        
        Locale locale = resource.getContentLocale();
        if(locale == null) {
            locale = defaultLocale;
        }
        
        return new FormSubmitCommand(structuredResource, url, listComponentServiceURL, 
                workingCopy != null, published, hasPublishDate, onlyWriteUnpublished, locale);
    }

    private class FormDataBinder extends ServletRequestDataBinder {
        private StructuredResourceDescription description;

        public FormDataBinder(FormSubmitCommand target, String objectName, 
                StructuredResourceDescription description) {
            super(target, objectName);
            this.description = description;
        }

        @Override
        public void bind(ServletRequest request) {
            List<PropertyDescription> props = description.getAllPropertyDescriptions();
            FormSubmitCommand form = (FormSubmitCommand) getTarget();
            for (PropertyDescription desc : props) {
                if (desc instanceof EditablePropertyDescription) {

                    if ("simple_html".equals(desc.getType())) {
                        runSimpleHtmlFilter(request, form, desc);

                    } else if (desc instanceof JSONPropertyDescription) {
                        buildJSONFromInput(request, form, desc);

                    } else {
                        storePostedValue(request, form, desc);
                    }
                }
            }
            super.bind(request);
        }

        private void storePostedValue(ServletRequest request, FormSubmitCommand form, PropertyDescription desc) {
            String posted = request.getParameter(desc.getName());
            if (desc.isTrim()) {
                posted = posted.trim();
            }
            bindObjectToForm(form, desc, posted);
        }

        private void runSimpleHtmlFilter(ServletRequest request, FormSubmitCommand form, PropertyDescription desc) {
            String filteredValue;
            try {
                filteredValue = filterValue(request.getParameter(desc.getName()));
                bindObjectToForm(form, desc, filteredValue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void buildJSONFromInput(ServletRequest request, FormSubmitCommand form, PropertyDescription desc) {
            JSONPropertyDescription jsonDesc = (JSONPropertyDescription) desc;

            if (!jsonDesc.isMultiple()) {
                JSONObject obj = new JSONObject();
                if (jsonDesc.isWildcard()) {
                    String str = request.getParameter(desc.getName());
                    obj = JSONObject.fromObject(str);
                    
                } else {
                    for (JSONPropertyAttributeDescription attr : jsonDesc.getAttributes()) {
                        String param = desc.getName() + "." + attr.getName() + ".0";
                        String posted = request.getParameter(param);
                        if (posted != null && !"".equals(posted.trim())) {
                            obj.put(attr.getName(), posted);
                        }
                    }
                }
                bindObjectToForm(form, desc, obj);
                return;
            }

            if (jsonDesc.isWildcard()) {
                throw new IllegalStateException(
                        "Multi-valued properties of type json-wildcard not supported");
            }
            
            @SuppressWarnings("unchecked")
            Enumeration<String> names = request.getParameterNames();
            int maxIndex = 0;
            while (names.hasMoreElements()) {
                String input = names.nextElement();
                for (JSONPropertyAttributeDescription attr : jsonDesc.getAttributes()) {
                    String prefix = desc.getName() + "." + attr.getName() + ".";
                    if (input.startsWith(prefix)) {
                        int i = Integer.parseInt(input.substring(prefix.length()));
                        maxIndex = Math.max(maxIndex, i);
                    }
                }
            }
            List<JSONObject> resultList = new ArrayList<JSONObject>();
            for (int i = 0; i <= maxIndex; i++) {
                JSONObject obj = new JSONObject();
                for (JSONPropertyAttributeDescription attr : jsonDesc.getAttributes()) {
                    String input = desc.getName() + "." + attr.getName() + "." + i;
                    String posted = request.getParameter(input);
                    if (posted != null && !"".equals(posted.trim())) {
                        obj.put(attr.getName(), posted);
                    }
                }
                if (!obj.isEmpty()) {
                    resultList.add(obj);
                }
            }
            bindObjectToForm(form, desc, resultList);

        }

        private void bindObjectToForm(FormSubmitCommand form, PropertyDescription desc, Object obj) {
            try {
                form.bind(desc.getName(), obj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String filterValue(String value) throws Exception {
        HtmlPageParser parser = new HtmlPageParser();
        HtmlFragment fragment;
        fragment = parser.parseFragment(value);
        fragment.filter(safeHtmlFilter);
        return fragment.getStringRepresentation();
    }

    public void unlock() throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        requestContext.getRepository().unlock(token, uri, null);
    }

    public void lock() throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Principal principal = requestContext.getPrincipal();
        requestContext.getRepository().lock(token, uri, principal.getQualifiedName(), Depth.ZERO, 600, null);
    }

    public void setResourceManager(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void setSafeHtmlFilter(HtmlPageFilter safeHtmlFilter) {
        this.safeHtmlFilter = safeHtmlFilter;
    }

    public HtmlPageFilter getSafeHtmlFilter() {
        return safeHtmlFilter;
    }

    public void setListComponentsService(Service listComponentsService) {
        this.listComponentsService = listComponentsService;
    }

    public Service getListComponentsService() {
        return listComponentsService;
    }
    
    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getFormView() {
        return formView;
    }

    public void setFormView(String formView) {
        this.formView = formView;
    }

    public String getSuccessView() {
        return successView;
    }

    public void setSuccessView(String successView) {
        this.successView = successView;
    }

}
