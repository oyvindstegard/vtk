/* Copyright (c) 2004, 2007, University of Oslo, Norway
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
package vtk.edit.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;

import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.ProcessingInstruction;
import org.jdom.filter.Filter;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.ContentInputSources;
import vtk.repository.Lock;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Repository.Depth;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.security.Principal;
import vtk.util.Xml;
import vtk.web.RequestContext;

/**
 * The document being edited.
 */
public class EditDocument extends Document {

    private static final String MODE_PI_NAME = "mode";

    private static final long serialVersionUID = 3256719598073361459L;

    private static Logger logger = LoggerFactory.getLogger(EditDocument.class);

    private Repository repository;
    private Resource resource;

    // State variables
    private ProcessingInstruction pi;
    private Element element = null;
    private Element clone = null;
    private String newElementName = null;
    private List<Element> elements = null;


    EditDocument(Element root, DocType docType, Resource resource, Repository repository) {
        super(root, docType);
        this.resource = resource;
        this.repository = repository;
        this.setBaseURI(resource.getURI().toString());
    }


    public static EditDocument createEditDocument(HttpServletRequest request, 
            Repository repository, int lockTimeoutSeconds) throws JDOMException, Exception {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Path uri = requestContext.getResourceURI();

        repository.lock(token, uri, principal.getQualifiedName(), Depth.ZERO, lockTimeoutSeconds,
                null, Lock.Type.EXCLUSIVE);

        Resource resource = repository.retrieve(token, uri, false);

        if (logger.isDebugEnabled())
            logger.debug("Locked resource '" + uri + "', principal = '" + principal + "'");

        SAXBuilder builder = new SAXBuilder("org.apache.xerces.parsers.SAXParser");
        builder.setValidation(true);

        /* turn on schema support */
        builder.setFeature("http://apache.org/xml/features/validation/schema", true);


        builder.setXMLFilter(new XMLSpaceCorrectingXMLFilter());

        Document document = builder.build(repository.getInputStream(token, uri, false));

        Element root = document.getRootElement();
        root.detach();
        return new EditDocument(root, document.getDocType(), resource, repository);
    }


    public void finish(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        String token = requestContext.getSecurityToken();

        repository.unlock(token, this.resource.getURI(), null);
    }


    public void save(HttpServletRequest request) throws XMLEditException, Exception {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        String token = requestContext.getSecurityToken();

        Path uri = this.resource.getURI();

        if (logger.isDebugEnabled())
            logger.debug("Saving document '" + uri + "'");

        new Validator().validate(this);

        removeProcessingInstructions();

        Format format = Format.getRawFormat();
        format.setLineSeparator("\n");

        XMLOutputter xmlOutputter = new XMLOutputter();
        xmlOutputter.setFormat(format);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        xmlOutputter.output(this, outputStream);

        InputStream stream = new ByteArrayInputStream(outputStream.toByteArray());
        this.resource = repository.storeContent(token, null, uri, ContentInputSources.fromStream(stream));

        // Remove user specified character encoding if it is something
        // other than UTF-8:
        if (this.resource.getCharacterEncoding() != null) {
            String encoding = this.resource.getCharacterEncoding().toLowerCase();
            if (!"utf-8".equals(encoding)) {
                this.resource.removeProperty(Namespace.DEFAULT_NAMESPACE, 
                        PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME);
                this.resource = repository.store(token, null, this.resource);
            }
        }

        if (logger.isDebugEnabled())
            logger.debug("saved document '" + uri + "'");
    }


    public String getDocumentMode() {
        ProcessingInstruction pi = Xml.findProcessingInstruction(getRootElement(), MODE_PI_NAME);

        if (pi != null)
            return pi.getData();

        return "default";
    }


    public void setDocumentMode(String mode) {
        Element rootElement = getRootElement();
        ProcessingInstruction pi = Xml.findProcessingInstruction(rootElement, MODE_PI_NAME);
        if (pi != null) {
            rootElement.removeContent(pi);
        }
        if (!"default".equals(mode)) {
            rootElement.addContent(new ProcessingInstruction(MODE_PI_NAME, mode));
        }
    }


    public boolean hasDocumentPI() {
        return this.pi != null;
    }


    public void setDocumentPI(ProcessingInstruction newPI) {
        this.pi = newPI;
        addContent(newPI);
    }


    public void removeDocumentPI() {
        removeContent(this.pi);
        this.pi = null;
    }


    public Element getEditingElement() {
        return this.element;
    }


    public void setEditingElement(Element e) {
        this.element = e;
    }


    @SuppressWarnings("unchecked")
    public void resetEditingElement() {
        ProcessingInstruction pi = null;
        for (Iterator i = this.element.getContent().iterator(); i.hasNext();) {
            Object o = i.next();
            if ((o instanceof ProcessingInstruction)
                    && "expanded".equals((((ProcessingInstruction) o).getTarget()))) {
                pi = (ProcessingInstruction) o;
            }
        }
        this.element.removeContent(pi);
        this.element = null;
    }


    public List<Element> getElements() {
        return this.elements;
    }


    public void setElements(List<Element> e) {
        this.elements = e;
    }


    public void resetElements(List<Element> elements) {
        HashMap<Element, ProcessingInstruction> removalSet = new HashMap<>();

        for (Element elem : elements) {
            for (Object o : elem.getContent()) {
                if (o instanceof ProcessingInstruction) {
                    removalSet.put(elem, (ProcessingInstruction) o);
                }
            }
        }
        for (Element e : removalSet.keySet()) {
            ProcessingInstruction pi = removalSet.get(e);
            e.removeContent(pi);
        }
    }


    public void resetElements() {
        resetElements(this.elements);
        this.elements = null;
    }


    protected void removeProcessingInstructions() {
        Stack<ProcessingInstruction> stack = new Stack<>();
        for (Iterator<?> i = getContent().iterator(); i.hasNext();) {
            Object o = i.next();
            if (o instanceof ProcessingInstruction) {
                stack.push((ProcessingInstruction) o);
            }
        }
        for (ProcessingInstruction pi : stack) {
            removeContent(pi);
        }
    }


    /**
     * Go through all descendants of element, and map input against leaf nodes
     * 
     * @param element
     * @param parameters
     * @param documentDefinition
     */
    @SuppressWarnings("unchecked")
    public void addContentsToElement(Element element, Map<String, String> parameters,
            SchemaDocumentDefinition documentDefinition) {

        addAttributesToElement(element, parameters);

        Map<Element, String> modifiedElements = new HashMap<>();

        String path = Xml.createNumericPath(element);
        String input = parameters.get(path);
        if (input != null) {
            modifiedElements.put(element, input);
        }

        for (Iterator iter = element.getDescendants(); iter.hasNext();) {
            Object o = iter.next();

            if (o instanceof Element) {
                Element e = (Element) o;
                path = Xml.createNumericPath(e);
                input = parameters.get(path);
                if (input != null) {
                    modifiedElements.put(e, input);
                }
            }
        }

        for (Element e : modifiedElements.keySet()) {
            documentDefinition.setElementContents(e, modifiedElements.get(e));
        }
    }


    private void addAttributesToElement(Element element, Map<String, String> parameters) {
        for (String key : parameters.keySet()) {
            /*
             * If the input parameter is on the path of the current element and
             * matches the attribute syntax.
             */
            if (key.matches(Xml.createNumericPath(element) + "(\\.\\d+)*:[a-zA-Z].*")) {
                String elementPath = key.substring(0, key.indexOf(":"));
                String attributeName = key.substring(key.indexOf(":") + 1);
                Element e = findElementByPath(elementPath);

                if (e == null)
                    throw new EditException("The document does not contain an element with path "
                            + elementPath, this.resource.getURI());
                e.setAttribute(attributeName, parameters.get(key));
            }
        }
    }


    /**
     * Finds elements in document based on "numeric path" (element positions
     * separated by dots). An example of such a path is "1.3.9", which means
     * "the ninth element of the third element of the first (root) element".
     * 
     * @param path
     *            the path to search
     * @return an <code>Element</code> if found, <code>null</code> otherwise
     */
    public Element findElementByPath(String path) {
        return Xml.findElementByNumericPath(this, path);
    }


    @SuppressWarnings("unchecked")
    public void putElementByPath(String path, Element e) {
        Element currentElement = getRootElement();
        String currentPath = path;
        if (currentPath.indexOf(".") >= 0) {
            // Strip away the leading '1.' (root element)
            currentPath = currentPath.substring(2, currentPath.length());
        }
        while (true) {
            int index = 0;
            if (currentPath.indexOf(".") == -1) {
                index = Integer.parseInt(currentPath);
            } else {
                index = Integer.parseInt(currentPath.substring(0, currentPath.indexOf(".")));
            }
            if (currentPath.indexOf(".") == -1) {
                /*
                 * Found the parent element. Put child elements and processing
                 * instructions into a list
                 */

                List l = new ArrayList(currentElement.getContent(new Filter() {
                    public boolean matches(Object o) {
                        return (o instanceof ProcessingInstruction) || (o instanceof Element);
                    }

                    private static final long serialVersionUID = 4746825449858085648L;
                }));

                /*
                 * Add the new element to the list at the specified index:
                 */
                l.add(index, e);

                /*
                 * Remove old content from parent, replace it with the list:
                 */
                currentElement.removeContent();
                currentElement.setContent(l);
                break;
            }
            currentElement = (Element) currentElement.getChildren().get(index - 1);
            currentPath = currentPath.substring(currentPath.indexOf(".") + 1, currentPath.length());
        }
    }


    public Element getClone() {
        return this.clone;
    }


    public void setClone(Element clone) {
        this.clone = clone;
    }


    public String getNewElementName() {
        return this.newElementName;
    }


    public void setNewElementName(String newElementName) {
        this.newElementName = newElementName;
    }


    public Resource getResource() {
        return this.resource;
    }


    public String toStringDetail() {
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());

        return outputter.outputString(this);
    }
}
