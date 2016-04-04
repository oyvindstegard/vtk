/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.webdav;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.web.servlet.View;
import vtk.repository.Lock;
import vtk.repository.Resource;
import vtk.web.InvalidModelException;

/**
 * Renderer for LOCK requests.
 */
public class LockView implements View {

    private static Logger logger = LoggerFactory.getLogger(PropfindView.class);

    @SuppressWarnings("rawtypes")
    public void render(Map model, HttpServletRequest request,
                                  HttpServletResponse response) throws Exception {

        Resource resource = (Resource) model.get(
            WebdavConstants.WEBDAVMODEL_REQUESTED_RESOURCE);
        if (resource == null) {
            throw new InvalidModelException(
                "Missing resource in model " +
                "(expected a Resource object having key" +
                " `" + WebdavConstants.WEBDAVMODEL_REQUESTED_RESOURCE + "')");
        }

        Lock lock = resource.getLock();

        if (lock == null) {
            throw new InvalidModelException(
                "Unable to render non-locked resource " + resource.getURI());
        }

        Element lockDiscovery = buildLockDiscovery(lock);


        Document doc = new Document(lockDiscovery);

        Format format = Format.getPrettyFormat();
        format.setEncoding("utf-8");
        //format.setLineSeparator("\r\n");
        //format.setIndent("  ");
        XMLOutputter outputter = new XMLOutputter(format);
        
        String xml = outputter.outputString(doc);
        byte[] buffer = null;
        try {
            buffer = xml.getBytes("utf-8");
        } catch (UnsupportedEncodingException ex) {
            logger.warn("Warning: UTF-8 encoding not supported", ex);
            throw new RuntimeException("UTF-8 encoding not supported");
        }

        response.setHeader("Lock-Token", lock.getLockToken());
        response.setHeader("Content-Type", "text/xml;charset=utf-8");
        response.setIntHeader("Content-Length", buffer.length);
        response.setStatus(HttpServletResponse.SC_OK);

        OutputStream out = null;
        try {
            out = response.getOutputStream();
            out.write(buffer, 0, buffer.length);

            out.flush();
            out.close();

        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }
    


    public static Element buildLockDiscovery(Lock lockInfo) {

        String type = "write";
        String scope = "exclusive";

        Element lockDiscovery = new Element("lockdiscovery", WebdavConstants.DAV_NAMESPACE);
        Element activeLock = new Element("activelock", WebdavConstants.DAV_NAMESPACE);

        activeLock.addContent(
            new Element("locktype", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element(type, WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent(
            new Element("lockscope", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element(scope, WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent(
            new Element("depth", WebdavConstants.DAV_NAMESPACE).addContent(
                lockInfo.getDepth().toString()));

        activeLock.addContent(            
            buildLockOwnerElement(lockInfo.getOwnerInfo()));

        String timeoutStr = "Second-0";
        long timeout = lockInfo.getTimeout().getTime() -
            System.currentTimeMillis();
        if (timeout > 0) {

            timeoutStr = "Second-" + (timeout / 1000);
        }

//         String timeoutStr = "Second-410000000";
        
        activeLock.addContent(
            new Element("timeout", WebdavConstants.DAV_NAMESPACE).addContent(
                /*"Infinite"*/            // MS fails
                /*"Second-4100000000"*/   // Cadaver fails
                /*"Second-410000000"*/    // Works..
                timeoutStr
            ));

        activeLock.addContent(
            new Element("locktoken", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("href", WebdavConstants.DAV_NAMESPACE).addContent(
                    lockInfo.getLockToken())));

        lockDiscovery.addContent(activeLock);
        Element propElement = new Element("prop", WebdavConstants.DAV_NAMESPACE);
        propElement.addContent(lockDiscovery);
        return propElement;
    }


    // FIXME: quick and dirty method for allowing lock-owner info to
    // be stored both as arbitraty XML content and plain text strings.
    public static Element buildLockOwnerElement(String content) {
        Element ownerElement = new Element("owner", WebdavConstants.DAV_NAMESPACE);
        
        try {
            if (!content.startsWith("<")) {
                // Simple content:
                ownerElement.addContent(content);
            } else {
                // XML content:
                StringBuffer xmlContent = new StringBuffer("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
                xmlContent.append(content);
                
                SAXBuilder builder = new SAXBuilder();
                org.jdom.Document doc = builder.build(
                    new ByteArrayInputStream(xmlContent.toString().getBytes()));
                
                Element rootElement = (Element) doc.getRootElement().detach();

                ownerElement.addContent(rootElement);
            }
        } catch (RuntimeException e) {
            logger.warn("Run time exception building lock owner info element: " 
                    + e.getMessage()); 
            ownerElement.addContent(content);
        } catch (JDOMException e) {
            logger.warn("JDOMException while building lock owner info element: " 
                    + e.getMessage());
            ownerElement.addContent(content);
        } catch (IOException e) {
            logger.warn("IOException while building lock owner info: " 
                       + e.getMessage());
            ownerElement.addContent(content);
        } 
        return ownerElement;
    }

    public String getContentType() {
        return null;
    }

}
