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
package vtk.edit.xml;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.jdom.Text;
import org.jdom.filter.Filter;
import org.jdom.input.SAXBuilder;

import vtk.util.text.DefaultStructuredText;
import vtk.util.text.EvenStructuredText;
import vtk.util.text.StructuredText;
import vtk.util.web.LinkTypesPrefixes;



/**
 * A representation of a schema document definition. Allows for simple
 * document structure queries against an unspecified subset of the XML
 * schema standard.
 *
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public class SchemaDocumentDefinition implements Serializable {

    private static final long serialVersionUID = -4330910522619544861L;

    private static Map<String, Class<? extends StructuredText>> structuredTextImplementations = 
            new HashMap<String, Class<? extends StructuredText>>();
    static {
        structuredTextImplementations.put(
                "no.uio.vortex.util.text.EvenStructuredText", EvenStructuredText.class);
        structuredTextImplementations.put(
                "org.vortikal.util.text.EvenStructuredText", EvenStructuredText.class);
        structuredTextImplementations.put(
                "org.vortikal.util.text.DefaultStructuredText", DefaultStructuredText.class);
        structuredTextImplementations.put(
                "no.uio.vortex.util.text.DefaultStructuredText", DefaultStructuredText.class);
        structuredTextImplementations.put(
                EvenStructuredText.class.getName(), EvenStructuredText.class);
        structuredTextImplementations.put(
                DefaultStructuredText.class.getName(), DefaultStructuredText.class);
    }
    
    private static final Namespace XSD_NAMESPACE = Namespace.getNamespace("xsd",
    "http://www.w3.org/2001/XMLSchema");

    /* The types of elements that can exist */
    /* element is a single data-element (handles only string for now) */
    final static public String REQUIRED_STRING_ELEMENT = "REQUIRED_STRING_ELEMENT";

    /* element is a single optional data-element (handles only string for now) */
    final static public String OPTIONAL_STRING_ELEMENT = "OPTIONAL_STRING_ELEMENT";

    /* element contains an unbounded list of elements */
    final static public String UNBOUNDED_ELEMENT = "UNBOUNDED_ELEMENT";

    /* element contains a sequence of sub-elements */
    final static public String SEQUENCE_ELEMENT = "SEQUENCE_ELEMENT";

    /* element contains a sequence of sub-elements */
    final static public String UNBOUNDED_CHOICE_ELEMENT = "UNBOUNDED_CHOICE_ELEMENT";

    /*
     * The docType string of this schema, as it appears in XML instance
     * documents
     */
    private String docType;

    /* The URL of this schema */
    private URL schemaURL;

    /* The root element ( <code> xsd:schema </code> ) element of this schema */
    protected Element schema = null;
    

    private Log logger = LogFactory.getLog(this.getClass());



    public SchemaDocumentDefinition(String docType, URL schemaURL)
            throws JDOMException, MalformedURLException, IOException {

        this.docType = docType;
        this.schemaURL = schemaURL;
        this.schema = new SAXBuilder().build(schemaURL).getRootElement();
        include(this.schema, this.schemaURL);
    }



    /**
     * Follows include elements in schema, collecting 'complexType' and
     * 'simpleType' elements into a list. Will not check for include loops.
     * 
     * @param currentSchema
     *            the root element of the current schema possibly containing
     *            <code>include</code> elements
     * @param currentURL
     *            the URL of the current schema, used for resolving relative
     *            includes.
     * @exception JDOMException
     *                if an error occurs while building an incluedd document
     * @exception MalformedURLException
     *                if an include element causes a malformed URL to be built
     * @exception IOException
     *                if an I/O error occurs
     */
    private void include(Element currentSchema, URL currentURL)
            throws JDOMException, MalformedURLException, IOException {

        List l = currentSchema.getChildren("include", XSD_NAMESPACE);
        List<Element> al = new ArrayList<Element>();
        for (Iterator iter = l.iterator(); iter.hasNext();) {
            al.add((Element) (((Element) iter.next()).clone()));
        }
        for (Element element: al) {
   
            String location = element.getAttributeValue("schemaLocation");

            URL includeURL = null;

            if (location.startsWith(File.separator)
                    || location.matches("[a-zA-Z]:.*")) {

                // Unsupported URL (i.e. a file path, etc. is specified)
                throw new XMLEditException("Unable to include schema "
                        + location + " (referenced from " + currentURL + "). "
                        + "Only relative URLs or 'file:' and 'http[s]:' "
                        + "type URLs are supported.");

            } else if (location.startsWith(LinkTypesPrefixes.FILE)
                    || location.matches("https?://.*")) {
                // Absolute URL
                includeURL = new URL(location);

            } else {

                // Relative URL
                String base = currentURL.toString();
                base = base.substring(0, base.lastIndexOf("/"));
                // FIXME: validate this:
                includeURL = new URL(base + "/" + location);
            }

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Including schema " + includeURL + " from "
                             + currentURL);
            }

            Document included = new SAXBuilder().build(includeURL);

            include(included.getRootElement(), includeURL);

            /* The elements searched for and added from the included schemas */
            String[] includedElements = new String[] { "element",
                    "complexType", "simpleType"};

            for (int i = 0; i < includedElements.length; i++) {
                List li = included.getRootElement().getChildren(
                        includedElements[i], XSD_NAMESPACE);
                for (Iterator lIter = li.iterator(); lIter.hasNext();) {
                    Element e = (Element) lIter.next();

                    String schemaElementType = includedElements[i];
                    String schemaElementName = e.getAttributeValue("name");
                    List currentSchemaExistingChildren = currentSchema
                            .getChildren(schemaElementType, XSD_NAMESPACE);

                    if (findInElementList(schemaElementName, currentSchemaExistingChildren) != null) { 
                        throw new XMLEditException("The included schema " + includeURL + 
                                " contains a conflicting element of type " + schemaElementType + 
                                " with name " + schemaElementName);
                    }
                    
                    Element clone = (Element) e.clone();
                    currentSchema.addContent(clone);
                }
            }

        }
    }



    public String getStructuredTextClassName(Element e) {
        e = e.getChild("annotation", XSD_NAMESPACE);
        if (e == null) return null;
        e = e.getChild("appinfo", XSD_NAMESPACE);
        if (e == null) return null;
        e = e.getChild("structuredText");

        if (e != null) return e.getTextNormalize();

        return null;
    } 
    


    public String getXSLPath() {
        Element e = this.schema;
        e = e.getChild("annotation", XSD_NAMESPACE);
        e = e.getChild("appinfo", XSD_NAMESPACE);
        e = e.getChild("edit");
        e = e.getChild("xsl");
        return e.getTextNormalize();
    }



    public String getButtonFrameURI() {
        Element e = this.schema;
        e = e.getChild("annotation", XSD_NAMESPACE);
        e = e.getChild("appinfo", XSD_NAMESPACE);
        e = e.getChild("edit");
        Element f = e.getChild("buttonframe");
        String buttonFrame = f.getTextNormalize();
        return buttonFrame;
    }



    private Map<String, String> getTextMappings(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }

        String elementName = element.getName();
        Map<String, String> textMap = new HashMap<String, String>();
        Element e = findElementDefinition(element);
        /* Get the type definition of the element in question */
        e = findInElementList(e.getAttributeValue("type"), this.schema.getChildren(
                "complexType", XSD_NAMESPACE));
        /* Get the texthackmappings from the sub elements */
        /* Go through the list of children */
        getTextMappings(textMap, e);
        textMap.put("root", elementName);

        return textMap;
    }



    /**
     * Takes an element in a document of this type and parses the schema for
     * the elements type.
     * 
     * @param element
     * @return the elements type, null if the type can't be found
     * @throws IllegalArgumentException,
     *             if the element isn't part of a document of this type
     */
    public String elementType(Element element) {

        if (element.getDocument() == null) {
            throw new IllegalArgumentException(
                    "element doesn't have a document");
        } else if (!this.docType.equals(element.getDocument().getRootElement()
                .getName())) { throw new IllegalArgumentException(
                "element doesn't have a document of correct type"); }
        Element elementDef = findElementDefinition(element);

        /* Start tests */
        if ("0".equals(elementDef.getAttributeValue("minOccurs")))
                return OPTIONAL_STRING_ELEMENT;
        if ("xsd:string".equals(elementDef.getAttributeValue("type")))
                return REQUIRED_STRING_ELEMENT;

        /* Check if element is an UNBOUNDED_ELEMENT sub type */
        Element e = elementDef.getChild("annotation", XSD_NAMESPACE);
        if (e != null) {
            e = e.getChild("appinfo", XSD_NAMESPACE);
            if (e != null) {
                String appinfo = e.getTextTrim();
                StructuredText structuredText = null;
                
                if (element.getParent() instanceof Element)
                    structuredText = getStructuredText(((Element) element.getParent()));
                
                if (structuredText != null && structuredText.getTagNames().contains(appinfo))
                        return appinfo;
     }
        }
        String typeName = elementDef.getAttributeValue("type");

        /* Check for enumeration simpleType */
        Element simpleType = findInElementList(typeName, this.schema.getChildren(
                "simpleType", XSD_NAMESPACE));
        if (simpleType != null) {
            /* Start tests */
            if ("0".equals(elementDef.getAttributeValue("minOccurs")))
                return OPTIONAL_STRING_ELEMENT;
            
            return REQUIRED_STRING_ELEMENT;
        }

        Element complexType = findInElementList(typeName, this.schema.getChildren(
                "complexType", XSD_NAMESPACE));

        /* Check if element is UNBOUNDED_ELEMENT */

        e = complexType.getChild("annotation", XSD_NAMESPACE);
        if (e != null) {
            e = e.getChild("appinfo", XSD_NAMESPACE);
            if (e != null) {
                e = e.getChild("elementType");
                if (e != null) {
                    String elementType = e.getTextTrim();
                    if (UNBOUNDED_ELEMENT.equals(elementType)) return UNBOUNDED_ELEMENT;
                }
            }
        }

        Element child = (Element) complexType.getChildren().get(0);

        if (child.getName().equals("simpleContent"))
                return REQUIRED_STRING_ELEMENT;

        if (child.getName().equals("sequence")) return SEQUENCE_ELEMENT;

        if (child.getName().equals("choice")) return UNBOUNDED_CHOICE_ELEMENT;

        throw new XMLEditException(
            "Unable to determine type of element " + element.getName());
    }



    /**
     * Recursively walks up to the root of the document tree, parsing the
     * schema for the <code>xsd:element</code> definition of the element,
     * within the parents <code>xsd:complexType</code> definition, on the way
     * down.
     * 
     * @param element
     * @return the <code>xsd:element</code> schema definition of the input
     *         element
     */
    private Element findElementDefinition(Element element) {
        if (element.isRootElement()) {
            List elements = this.schema.getChildren("element", XSD_NAMESPACE);
            for (Iterator iter = elements.iterator(); iter.hasNext();) {
                Element e = (Element) iter.next();
                if (this.docType.equals(e.getAttributeValue("name"))) { return e; }
            }
            throw new XMLEditException(
                    "The schema does not contain an element definition of docType "
                            + this.docType);
        }
        Element parentDefinition = findElementDefinition((Element) element
                .getParent());
        Element parentType = findInElementList(parentDefinition
                .getAttributeValue("type"), this.schema.getChildren("complexType",
                XSD_NAMESPACE));
        for (Iterator iter = parentType.getDescendants(); iter.hasNext();) {
            Object o = iter.next();
            if (o instanceof Element) {
                Element descendant = (Element) o;
                if ("element".equals(descendant.getName())
                        && element.getName().equals(
                                descendant.getAttributeValue("name"))) { return descendant; }
            }
        }
        throw new XMLEditException(
                "The schema does not contain an element definition for element "
                        + element.getName());
    }

    private Element findElementTypeDefinition(Element e) {
        Element element = findElementDefinition(e);
        element = findInElementList(element.getAttributeValue("type"), this.schema.getChildren(
                "complexType", XSD_NAMESPACE));
        return element;
    }

    /**
     * Set the contents of this element based on the data string and the type
     * of element.
     * 
     * @param element
     *            an <code>Element</code> value
     * @param data
     *            a <code>String</code> value
     */
    public void setElementContents(Element element, String data) {
        if (data == null)
                throw new IllegalArgumentException("Missing data for "
                        + element.getName());
        if (element.getDocument() == null) {
            throw new IllegalArgumentException(
                    "element doesn't have a document");
        } else if (!this.docType.equals(element.getDocument().getRootElement()
                .getName())) { throw new IllegalArgumentException(
                "element doesn't have a document of correct type"); }

        if (elementType(element).equals(REQUIRED_STRING_ELEMENT)) {
            element.setText(data);

        } else if (elementType(element).equals(OPTIONAL_STRING_ELEMENT)) {
            if (data.trim().equals("") && !element.getParent().equals(element.getDocument().getRootElement())) {
                element.detach();
            } else {
                element.setText(data);
            }

        } else if (elementType(element).equals(UNBOUNDED_ELEMENT)) {
            parseUnbounded(data, element);
        } else {
            throw new IllegalArgumentException(
                    "Supplied element " + element.getName() + " is not of legal type");
        }
    }
    
    
    private StructuredText getStructuredText(Element element) {

        Element schemaElementType = findElementTypeDefinition(element);
        if (schemaElementType == null) return null;
        
        String identifier = getStructuredTextClassName(schemaElementType);
        if (identifier == null) return null;
        
        Class<? extends StructuredText> cls = structuredTextImplementations.get(identifier);
        if (cls == null) return null;
        
        StructuredText structuredText = null;
        try {
            structuredText = cls.newInstance();
        } catch (Exception e) {
            this.logger.error("Unable to instantiate StructuredText for element: " 
                    + element.getName(), e);

            throw new XMLEditException(
                    "Couldn't instatiate StructuredText instance for element: " 
                    + element.getName(), e);
        }
        

        if (structuredText == null) 
            throw new XMLEditException("Asked to parse element as unbounded, " +
                    "but unable to find StructuredText implementation for " +
                    "element '" + element.getName() + "'");
        structuredText.setTextMappings(getTextMappings(element));

        return structuredText;
    }
    
    /**
     * Replace the element's contents with the parseData transformed by <code>StructuredText</code>
     * 
     * @param parseData
     * @param element
     * @see vtk.util.text.StructuredText#parseStructuredText(String)
     */
    private void parseUnbounded(String parseData, Element element) {
        StructuredText structuredText = getStructuredText(element);
        
        Element root = structuredText.parseStructuredText(parseData);

        ArrayList<Element> l = new ArrayList<Element>();
        for (Iterator i = root.getChildren().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            child = (Element) child.clone();
            l.add(child);
        }

        element.setText(null);
        element.setContent(l);
    }



    private void generateUnbounded(Element element) {
        StructuredText structuredText = getStructuredText(element);
        
        String text = structuredText.parseElement(element);

        // Preserve some of the content (processing instructions) in
        // a list:
        List keepContent = element.removeContent(
            new Filter() {
                public boolean matches(Object o) {
                    return (o instanceof ProcessingInstruction);

                }
                private static final long serialVersionUID = -305689634836226453L;
            });

        // Add the generated text to the preserved content
        keepContent.add(new Text(text));

        // Remove all content
        element.removeContent();
        
        // Set new content
        element.addContent(keepContent);
    }



    /**
     * Contruct the element from the schema definition
     * 
     * @param element
     *            an empty element at the correct position in the document
     */
    public void buildElement(Element element) {

        Element elementDef = null;
        Element e = null;

        if (element.getDocument() == null) 
            throw new IllegalArgumentException("Element is not in document");

        elementDef = findElementDefinition(element);

        String type = elementType(element);
        if (SEQUENCE_ELEMENT.equals(type)) {
            String typeName = elementDef.getAttributeValue("type");

            if (typeName == null) 
                throw new XMLEditException(
                    "The definition of element " + element.getName()
                            + " is illegal. The element definition "
                            + elementDef + " with name attribute "
                            + elementDef.getAttributeValue("name")
                            + " is missing required type attribute");

            e = findInElementList(typeName, this.schema.getChildren("complexType",
                    XSD_NAMESPACE));

            e = e.getChild("sequence", XSD_NAMESPACE);
            for (Iterator i = e.getChildren().iterator(); i.hasNext();) {
                Element childDef = (Element) i.next();
                Element child = new Element(childDef.getAttributeValue("name"));
                element.addContent(child);
                buildElement(child);
            }
            buildAttributes(element);

        } else if (OPTIONAL_STRING_ELEMENT.equals(type)
                || REQUIRED_STRING_ELEMENT.equals(type)
                || UNBOUNDED_CHOICE_ELEMENT.equals(type)
                || UNBOUNDED_ELEMENT.equals(type)) {
            buildAttributes(element);
        } else {
            throw new XMLEditException("element " + element.getName()
                    + " doesn't have the required xsd:appinfo definition");
        }

    }



    private void buildAttributes(Element element) {
        Element elementDef = null;
        Element e = null;

        if (element.getDocument() == null) { throw new IllegalArgumentException(
                "Element is not in document"); }

        elementDef = findElementDefinition(element);

        String typeName = elementDef.getAttributeValue("type");

        if (typeName == null) {
            throw new XMLEditException(
                "The definition of element " + element.getName()
                        + " is illegal. The element definition " + elementDef
                        + " with name attribute "
                        + elementDef.getAttributeValue("name")
                        + " is missing required type attribute"); }

        e = findInElementList(typeName, this.schema.getChildren("complexType",
                XSD_NAMESPACE));
        if (e == null) return;

        Iterator i = e.getDescendants(new XSDAttributeElementFilter());

        if (i == null) return; // Nothing to do

        while (i.hasNext()) {
            Element a = (Element) i.next();
            element.setAttribute(new Attribute(a.getAttributeValue("name"), ""));
        }
    }



    public void translateToEditingElement(Element element) {

        String elementType = elementType(element);
        if (UNBOUNDED_ELEMENT.equals(elementType)) {
            generateUnbounded(element);
        } else if (SEQUENCE_ELEMENT.equals(elementType)) {
            Element original = (Element) element.clone();
            element.removeContent();
            buildElement(element);

            List originalContent = original.getContent();
            Element e = (Element) element.clone();
            List newContent = e.getContent();

            for (Iterator iter = original.getAttributes().iterator(); iter
                    .hasNext();) {
                Attribute a = (Attribute) iter.next();
                element.setAttribute((Attribute) a.clone());

            }

            for (int posInOriginal = 0, posInElement = 0, posInNewContent = 0; posInOriginal < originalContent
                    .size(); posInNewContent++, posInOriginal++, posInElement++) {

                if (posInNewContent >= newContent.size()) {
                    Content c = (Content) originalContent.get(posInOriginal);
                    Content clonedContent = (Content) c.clone();
                    element.addContent(clonedContent);
                    continue;
                }

                if (!(originalContent.get(posInOriginal) instanceof Element)) {
                    Content c = (Content) originalContent.get(posInOriginal);
                    element.addContent(posInElement, (Content) c.clone());
                    posInNewContent--;
                    continue;
                }

                Element child = (Element) newContent.get(posInNewContent);
                Element originalChild = (Element) originalContent
                        .get(posInOriginal);
                if (/* OPTIONAL_STRING_ELEMENT.equals(elementType(child)) && */!child
                        .getName().equals(originalChild.getName())) {
                    posInOriginal--;
                    continue;
                }
                element.removeContent(posInElement);
                Element clonedChild = (Element) originalChild.clone();
                element.addContent(posInElement, clonedChild);
                translateToEditingElement(clonedChild);
            }

        } else if (UNBOUNDED_CHOICE_ELEMENT.equals(elementType)) {
            List children = element.getChildren();
            for (int i = 0; i < children.size(); i++) {
                translateToEditingElement((Element) children.get(i));
            }
        }
    }




    /** * Utilities: ** */



    public Element findInElementList(String elementName, List list) {
        Element e = null;
        for (Iterator it = list.iterator(); it.hasNext();) {
            e = (Element) it.next();
            if (elementName.equals(e.getAttributeValue("name"))) {
                break;
            }
            e = null;
        }
        return e;
    }

    
    /**
     * Recursive helper method for <code>getTextMappings</code>, picking up the mappings.
     * @param map
     * @param elementDefinition
     */
    private void getTextMappings(Map map, Element elementDefinition) {
        /*
         * Type definition elements for texthack elements must be defined with
         * choice or sequence elements
         */
    	Element element = elementDefinition.getChild("choice", XSD_NAMESPACE);
        if (element == null) {
            element = elementDefinition.getChild("sequence", XSD_NAMESPACE);
            /*
             * To make it possible to add xml:space
             * attribute we must check for simpleContent
             * as well. simpleContent = xsd:string Any
             * other construct will justf return...
             */
            if (element == null) {
                Element simpleContent = elementDefinition.getChild("simpleContent", 
                        XSD_NAMESPACE);
                if (simpleContent == null) {
                    this.logger.debug("Element '"
                            + elementDefinition.getAttributeValue("name")
                            + "' is defined in Schema to have null content");
                    return;
                }
                element = simpleContent.getChild("extension", XSD_NAMESPACE);
            }
            if (element == null) return;
        }
    		
        for (Iterator it = element.getChildren().iterator(); it.hasNext();) {
            element = (Element) it.next();
            String name = element.getAttributeValue("name");
            String type = element.getAttributeValue("type"); 
            Element appInfo = element.getChild("annotation", XSD_NAMESPACE);
            
            if (appInfo == null) continue;	
            
            appInfo = appInfo.getChild("appinfo", XSD_NAMESPACE);            	
            map.put(appInfo.getText(), name);    
            
            // 'type' returns NULL for elements (in Schema) withtout type definition
            if (type == null) {
                this.logger.warn("XML element '" + name + "' has no type, probably " +
                            "incorrect XML Schema definition syntax for the element");
                continue;
            }
                        
            /* Check if the child is a SEQUENCE_ELEMENT */
            if (!"xsd:string".equals(type)) {
                element = findInElementList(type,
                        this.schema.getChildren("complexType", XSD_NAMESPACE));
                getTextMappings(map, element);
            }
        }
    }

    public Element findElementRecursivly(String elementName,
            Element complexType, Element rootElement) {
        Element e = null; /* Reusable variable ... */
        /* Look in child list */
        for (Iterator it = complexType.getChildren().iterator(); it.hasNext();) {
            e = (Element) it.next();
            if (elementName.equals(e.getAttributeValue("name"))) {
                /* it's the element we want */
                break;
            } else if ((e.getAttributeValue("type") != null)
                    && (!e.getAttributeValue("type").equals("xsd:string"))) {
                /*
                 * Find type declaration for this element, and call this method
                 * recursively
                 */
                e = findInElementList(e.getAttributeValue("type"), rootElement
                        .getChildren("complexType", XSD_NAMESPACE));
                e = findElementRecursivly(elementName, e, rootElement);
                if (e != null) {
                    /* We found the element */
                    break;
                }
            } else if (e.getName().equals("sequence")
                    || e.getName().equals("choice")) {
                /* Call this method recursively */
                e = findElementRecursivly(elementName, e, rootElement);
                if (e != null) {
                    /* We found the element */
                    break;
                }
            }
            e = null;
        }
        return e;
    }

    private static class XSDAttributeElementFilter implements Filter {

        private static final long serialVersionUID = -5181435871154415499L;

        public boolean matches(Object object) {
            return (object instanceof Element)
                    && ((Element) object).getName().equals("attribute");
        }
    }

    /**
     * @return Returns the docType.
     */
    public String getDocType() {
        return this.docType;
    }

    /**
     * @param docType
     *            The docType to set.
     */
    public void setDocType(String docType) {
        this.docType = docType;
    }
}
