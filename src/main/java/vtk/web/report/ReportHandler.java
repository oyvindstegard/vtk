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
package vtk.web.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.security.Principal;
import vtk.util.repository.DocumentPrincipalMetadataRetriever;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ReportHandler implements Controller {

    private Repository repository;
    private String viewName;
    private DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever;
    private LocaleResolver localeResolver;

    // Primary reports, i.e. most visible and "important" reports
    protected List<Reporter> primaryReporters;

    // Set of simple reports, displayed as simple list to choose from
    protected List<Reporter> reporters;

    // Reports used as part of other reports, i.e. not explicit reports on
    // report service, but only accessible via other reports (primarily via
    // "primaryReportes")
    protected List<Reporter> hiddenReporters;

    // Reports only visible on specific collections
    protected Map<String, List<Reporter>> collectionPrimaryReporters;
    protected Map<String, List<Reporter>> collectionReporters;

    private Service viewReportService;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);

        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);

        Service service = requestContext.getService();
        URL serviceURL = service.urlConstructor(requestContext.getRequestURL())
                .withResource(resource)
                .withPrincipal(requestContext.getPrincipal())
                .constructURL();

        Map<String, Object> model = new HashMap<>();
        model.put("serviceURL", serviceURL);

        String reportType = request.getParameter(AbstractReporter.REPORT_TYPE_PARAM);
        if (reportType != null && !"".equals(reportType.trim())) {
            Reporter reporter = getReporter(reportType, resource.getResourceType());
            if (reporter != null) {
                Map<String, Object> report = reporter.getReportContent(request, token, resource);

                if (reporter.isResolvePrincipalLink()) {
                    Locale locale = localeResolver.resolveLocale(request);
                    Map<String, Principal> principalDocuments = getPrincipalDocuments(report, locale);
                    model.put("principalDocuments", principalDocuments);
                }

                model.put("report", report);

                Map<String, List<String>> typeParam = new HashMap<>();
                typeParam.computeIfAbsent(AbstractReporter.REPORT_TYPE_PARAM, k -> new ArrayList<>()).add(reportType);
                URL viewReportServiceURL = viewReportService.urlConstructor(requestContext.getRequestURL())
                        .withResource(resource)
                        .withPrincipal(requestContext.getPrincipal())
                        .withParameters(typeParam)
                        .constructURL();
                
                model.put("viewReportServiceURL", viewReportServiceURL);

                String selectedViewName = reporter.getViewName();
                
                /* Possible to switch to an alternative view */
                String alternativeViewName = reporter.getAlternativeViewName();
                String alternativeName = reporter.getAlternativeName();
                if(alternativeViewName != null && alternativeName != null) {
                    model.put("alternativeName", alternativeName);
                    if(request.getParameter(alternativeName) != null) {
                        selectedViewName = alternativeViewName;
                        model.put("isAlternativeView", true);
                    } else {
                        model.put("isAlternativeView", false);
                    }
                }
                
                return new ModelAndView(selectedViewName, model);
            }
        }

        if (collectionPrimaryReporters != null && collectionPrimaryReporters.containsKey(resource.getResourceType())) {
            List<Reporter> primaryReportersList = new ArrayList<>(primaryReporters);

            for (Reporter reporter : collectionPrimaryReporters.get(resource.getResourceType())) {
                primaryReportersList.add(reporter);
            }

            addReports(primaryReportersList, "primaryReporters", model, serviceURL);
        } else {
            addReports(primaryReporters, "primaryReporters", model, serviceURL);
        }

        if (collectionReporters != null && collectionReporters.containsKey(resource.getResourceType())) {
            List<Reporter> reportersList = new ArrayList<>(reporters);

            for (Reporter reporter : collectionReporters.get(resource.getResourceType())) {
                reportersList.add(reporter);
            }

            addReports(reportersList, "reporters", model, serviceURL);
        } else {
            addReports(reporters, "reporters", model, serviceURL);
        }

        return new ModelAndView(viewName, model);
    }

    private Map<String, Principal> getPrincipalDocuments(Map<String, Object> report, Locale locale) {

        Object reportResourceList = report.get("result");
        if (reportResourceList != null && reportResourceList instanceof List<?>) {
            List<?> reportResources = (ArrayList<?>) reportResourceList;
            Set<String> uids = new HashSet<>();
            for (Object obj : reportResources) {
                if (obj instanceof PropertySet) {
                    PropertySet ps = (PropertySet) obj;
                    Property modifiedBy = ps
                            .getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.MODIFIEDBY_PROP_NAME);
                    if (modifiedBy != null) {
                        uids.add(modifiedBy.getPrincipalValue().getName());
                    }
                }
            }
            if (uids.size() > 0) {
                return documentPrincipalMetadataRetriever.getPrincipalDocumentsMapByUid(uids, locale);
            }
        }
        return null;
    }

    private void addReports(List<Reporter> reportList, String modelKey, Map<String, Object> model, URL serviceURL) {
        if (reportList != null) {
            List<ReporterObject> reporterObjects = new ArrayList<>();
            for (Reporter reporter : reportList) {
                if (!reporter.isEnabled()) {
                    continue;
                }
                URL reporterURL = new URL(serviceURL);
                reporterURL.addParameter(AbstractReporter.REPORT_TYPE_PARAM, reporter.getName());
                reporterObjects.add(new ReporterObject(reporter.getName(), reporterURL));
            }
            model.put(modelKey, reporterObjects);
        }
    }

    private Reporter getReporter(String reportType, String resourceType) {
        Reporter reporter = getReporter(primaryReporters, reportType);
        reporter = reporter == null ? getReporter(reporters, reportType) : reporter;
        reporter = reporter == null ? getReporter(hiddenReporters, reportType) : reporter;

        if (reporter == null && collectionPrimaryReporters != null
                && collectionPrimaryReporters.containsKey(resourceType)) {
            reporter = getReporter(collectionPrimaryReporters.get(resourceType), reportType);
        }

        if (reporter == null && collectionReporters != null && collectionReporters.containsKey(resourceType)) {
            reporter = getReporter(collectionReporters.get(resourceType), reportType);
        }

        return reporter;
    }

    private Reporter getReporter(List<Reporter> reportList, String reportType) {
        if (reportList != null) {
            for (Reporter reporter : reportList) {
                if (reporter.getName().equals(reportType)) {
                    return reporter;
                }
            }
        }
        return null;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public static class ReporterObject {

        private String name;
        private URL url;

        public ReporterObject(String name, URL url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public URL getUrl() {
            return url;
        }

    }

    public void setPrimaryReporters(List<Reporter> primaryReporters) {
        this.primaryReporters = primaryReporters;
    }

    public void setReporters(List<Reporter> reporters) {
        this.reporters = reporters;
    }

    public void setHiddenReporters(List<Reporter> hiddenReporters) {
        this.hiddenReporters = hiddenReporters;
    }

    public void setCollectionPrimaryReporters(Map<String, List<Reporter>> collectionPrimaryReporters) {
        this.collectionPrimaryReporters = collectionPrimaryReporters;
    }

    public void setCollectionReporters(Map<String, List<Reporter>> collectionReporters) {
        this.collectionReporters = collectionReporters;
    }

    @Required
    public void setDocumentPrincipalMetadataRetriever(
            DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever) {
        this.documentPrincipalMetadataRetriever = documentPrincipalMetadataRetriever;
    }

    @Required
    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Required
    public void setViewReportService(Service viewReportService) {
        this.viewReportService = viewReportService;
    }

}
