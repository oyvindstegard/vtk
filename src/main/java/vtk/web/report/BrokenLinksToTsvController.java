/* Copyright (c) 2008, University of Oslo, Norway
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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.time.FastDateFormat;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.report.BrokenLinksReport.CollectionStats;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.view.freemarker.MessageLocalizer;

public class BrokenLinksToTsvController implements Controller {

    private Service reportService;
    private BrokenLinksReport brokenLinksReporter;
    private String webHostName;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, requestContext.getResourceURI(), false);

        Map<String, CollectionStats> map = brokenLinksReporter.getAccumulatorMap(token, resource, request);
        if (map.size() <= 0) {
            Map<String, Object> model = new HashMap<>();
            
            URL serviceURL = reportService.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(requestContext.getPrincipal())
                    .constructURL();
            model.put("serviceURL", serviceURL);
            model.put("report", brokenLinksReporter.getReportContent(token, resource, request));
            return new ModelAndView(brokenLinksReporter.getViewName(), model);
        }

        String filename = this.webHostName;
        filename += resource.getName().equals("/") ? "" : "_" + resource.getName();
        filename += "_" + FastDateFormat.getInstance("yyyy-MM-dd").format(new Date());
        filename += "_BrokenLinks";

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/tab-separated-values;charset=utf-8");
        response.setHeader("Content-Disposition", "filename=" + filename + ".tsv");

        org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                request);
        String locDocumentCount = new MessageLocalizer("report.broken-links-collection.document-count", "Error", null,
                springRequestContext).get(null).toString();
        String locBrokenLinksCount = new MessageLocalizer("report.broken-links-collection.broken-links-count", "Error",
                null, springRequestContext).get(null).toString();

        ServletOutputStream out = response.getOutputStream();
        try {
            out.print("Uri:\t" + locDocumentCount + ":\t" + locBrokenLinksCount + ":\n");

            Iterator<Map.Entry<String, CollectionStats>> iter = map.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, CollectionStats> entry = iter.next();
                out.print(entry.getKey() + "\t" + entry.getValue().documentCount + "\t" + entry.getValue().linkCount
                        + "\n");
            }
        } finally {
            out.close();
        }

        return null;

    }

    @Required
    public void setReportService(Service reportService) {
        this.reportService = reportService;
    }

    @Required
    public void setBrokenLinksReporter(BrokenLinksReport brokenLinksReporter) {
        this.brokenLinksReporter = brokenLinksReporter;
    }

    @Required
    public void setWebHostName(String webHostName) {
        String[] names = webHostName.trim().split("\\s*,\\s*");
        this.webHostName = names[0];
    }

}
