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
package org.vortikal.webdav;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import org.springframework.web.servlet.View;

import org.vortikal.repository.Lock;
import org.vortikal.repository.Resource;
import org.vortikal.web.InvalidModelException;




/**
 * Renderer for LOCK requests.
 */
public class LockView implements View {

    private static Log logger = LogFactory.getLog(PropfindView.class);


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

        // FIXME: assumes only one lock
        if (resource.getActiveLocks() == null
            || resource.getActiveLocks().length != 1) {
            throw new InvalidModelException(
                "Unable to render non-locked resource " + resource.getURI());
        }

        Lock lock = resource.getActiveLocks()[0];

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

        String type = "invalid";
        String scope = "invalid";

        if (lockInfo.getLockType().equals(Lock.LOCKTYPE_EXCLUSIVE_WRITE)) {
            scope = "exclusive";
            type = "write";
        }
        
        Element lockDiscovery = new Element("lockdiscovery", WebdavConstants.DAV_NAMESPACE);
        Element activeLock = new Element("activelock", WebdavConstants.DAV_NAMESPACE);

        activeLock.addContent((Element)
            new Element("locktype", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element(type, WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent((Element)
            new Element("lockscope", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element(scope, WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent((Element)
            new Element("depth", WebdavConstants.DAV_NAMESPACE).addContent(
                lockInfo.getDepth()));

        activeLock.addContent(            
            buildLockOwnerElement(lockInfo.getOwnerInfo()));

        String timeoutStr = "Second-0";
        long timeout = lockInfo.getTimeout().getTime() -
            System.currentTimeMillis();
        if (timeout > 0) {

            timeoutStr = "Second-" + (timeout / 1000);
        }

//         String timeoutStr = "Second-410000000";
        
        activeLock.addContent((Element)
            new Element("timeout", WebdavConstants.DAV_NAMESPACE).addContent(
                /*"Infinite"*/            // MS fails
                /*"Second-4100000000"*/   // Cadaver fails
                /*"Second-410000000"*/    // Works..
                timeoutStr
            ));

        activeLock.addContent((Element)
            new Element("locktoken", WebdavConstants.DAV_NAMESPACE).addContent(
                (Element) new Element("href", WebdavConstants.DAV_NAMESPACE).addContent(
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
                String xmlContent =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + content;
                SAXBuilder builder = new SAXBuilder();
                org.jdom.Document doc = builder.build(
                    new ByteArrayInputStream(xmlContent.getBytes()));

                Element rootElement = doc.getRootElement();

                rootElement.setNamespace(WebdavConstants.DAV_NAMESPACE);

                ownerElement.addContent(rootElement);
            }
            
        } catch (RuntimeException e) {
            // FIXME:
            ownerElement.addContent(content);

        } catch (JDOMException e) {
            // FIXME:
            ownerElement.addContent(content);

        } catch (IOException e) {
            // FIXME:
            ownerElement.addContent(content);
        } 
        return ownerElement;
    }


}
