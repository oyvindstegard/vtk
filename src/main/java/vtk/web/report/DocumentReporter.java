/* Copyright (c) 2010???2015, University of Oslo, Norway
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
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Acl;
import vtk.repository.Privilege;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.security.PrincipalFactory;
import vtk.web.ACLTooltipHelper;
import vtk.web.service.Service;
import vtk.web.service.URL;

public abstract class DocumentReporter extends AbstractReporter {

    private static Logger logger = LoggerFactory.getLogger(DocumentReporter.class.getName());

    private int pageSize = DEFAULT_SEARCH_LIMIT;
    private Service manageService, reportService;
    private String backReportName;
    private ACLTooltipHelper aclTooltipHelper;

    protected abstract Search getSearch(String token, Resource currentResource, HttpServletRequest request);

    @Override
    public Map<String, Object> getReportContent(String token, Resource resource, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put(REPORT_NAME, getName());

        if (backReportName != null) {
            URL backURL = new URL(reportService.constructURL(resource));
            backURL.addParameter(REPORT_TYPE_PARAM, backReportName);

            result.put("backURLname", backReportName);
            result.put("backURL", backURL);
        }

        Search search = getSearch(token, resource, request);
        if (search == null) {
            return result;
        }

        Position pos = Position.create(request, pageSize);
        if (pos.cursor >= Search.DEFAULT_LIMIT) {
            return result;
        }
        search.setCursor(pos.cursor);
        search.setLimit(pageSize);
        search.setPropertySelect(PropertySelect.ALL); // Require all props AND also ACLs

        ResultSet rs = searcher.execute(token, search);
        if (pos.cursor + Math.min(pageSize, rs.getSize()) >= rs.getTotalHits()) {
            pos.next = null;
        }
        if (pos.cursor + Math.min(pageSize, rs.getSize()) >= Search.DEFAULT_LIMIT) {
            pos.next = null;
        }

        result.put("from", pos.cursor + 1);
        result.put("to", pos.cursor + Math.min(pageSize, rs.getSize()));
        result.put("total", rs.getTotalHits());
        result.put("next", pos.next);
        result.put("prev", pos.prev);

        boolean[] isReadRestricted = new boolean[rs.getSize()];
        boolean[] isInheritedAcl = new boolean[rs.getSize()];
        URL[] viewURLs = new URL[rs.getSize()];
        String[] permissionTooltips = new String[rs.getSize()];
        List<PropertySet> list = new ArrayList<PropertySet>();
        
        
        List<PropertySet> allResults = rs.getAllResults();
        for (int i = 0; i < allResults.size(); i++) {
            PropertySet propSet = allResults.get(i);
            Acl acl = rs.getAcl(i);
            isReadRestricted[i] = !acl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL)
                    && !acl.hasPrivilege(Privilege.READ_PROCESSED, PrincipalFactory.ALL);
            isInheritedAcl[i] = rs.isInheritedAcl(i);
            if (manageService != null) {
                viewURLs[i] = manageService.constructURL(propSet.getURI()).setProtocol("http");
            }
            if (aclTooltipHelper != null) {
                permissionTooltips[i] = aclTooltipHelper.generateTitle(
                        propSet, acl, rs.isInheritedAcl(i), request);
            }
            handleResult(propSet, result);
            list.add(propSet);
        }
        
        result.put("result", list);
        result.put("isReadRestricted", isReadRestricted);
        result.put("isInheritedAcl", isInheritedAcl);
        result.put("viewURLs", viewURLs);
        result.put("permissionTooltips", permissionTooltips);
        return result;
    }

    // To be overridden where necessary
    protected void handleResult(PropertySet resource, Map<String, Object> model) {
    }

    public void setManageService(Service manageService) {
        this.manageService = manageService;
    }

    public void setReportService(Service reportService) {
        this.reportService = reportService;
    }

    public Service getReportService() {
        return this.reportService;
    }

    public void setBackReportName(String backReportName) {
        this.backReportName = backReportName;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public void setAclTooltipHelper(ACLTooltipHelper aclTooltipHelper) {
        this.aclTooltipHelper = aclTooltipHelper;
    }
    
    private static class Position {
        int cursor = 0;
        int limit = 0;
        URL next = null;
        URL prev = null;

        private Position() {
        }

        static Position create(HttpServletRequest req, int limit) {
            Position position = new Position();
            position.limit = limit;

            int page = 1;
            String pageParam = req.getParameter("page");
            if (pageParam != null) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                } catch (Throwable t) {
                }
            }
            if (page <= 0) {
                page = 1;
            }
            int cursor = (page - 1) * position.limit;
            if (cursor < 0) {
                cursor = 0;
            }
            position.cursor = cursor;
            URL url = URL.create(req);
            position.next = new URL(url).setParameter("page", String.valueOf(page + 1));
            if (page > 1) {
                position.prev = new URL(url).setParameter("page", String.valueOf(page - 1));
            }
            if (page == 2) {
                position.prev.removeParameter("page");
            }
            return position;
        }
    }
}
