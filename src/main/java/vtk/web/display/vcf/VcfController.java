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
package vtk.web.display.vcf;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.codec.Base64;
import vtk.web.RequestContext;
import vtk.web.service.URL;

public class VcfController implements Controller {

    private ResourceTypeTree resourceTypeTree;
    private String firstNamePropDefPointer;
    private String surnamePropDefPointer;
    private String usernamePropDefPointer;
    private String positionPropDefPointer;
    private String phonePropDefPointer;
    private String alternativeCellPhonePropDefPointer;
    private String mobilePropDefPointer;
    private String faxPropDefPointer;
    private String postalAddressPropDefPointer;
    private String alternativeVisitingAddressPropDefPointer;
    private String visitingAddressPropDefPointer;
    private String emailPropDefPointer;
    private String picturePropDefPointer;
    private String thumbnailPropDefPointer;
    private String imageWidthPropDefPointer;
    private String maxImageWidth;

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        URL requestURL = URL.create(request);

        Resource person = repository.retrieve(token, uri, true);

        String vcard = createVcard(person, repository, token, uri.getParent(), requestURL);
        if (vcard == null)
            return null;

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/x-vcard;charset=utf-8");
        String vcardFileName = getVcardFileName(person);
        response.setHeader("Content-Disposition", "filename=" + vcardFileName + ".vcf");
        ServletOutputStream out = response.getOutputStream();
        out.print(vcard);
        out.close();

        return null;
    }

    private String getVcardFileName(Resource person) {
        String name;

        if ((name = person.getTitle()) != null)
            name = name.replace(' ', '_');
        else {
            name = person.getName();
            if (name.contains("."))
                name = name.substring(0, name.indexOf("."));
        }

        return name;
    }

    private String createVcard(Resource person, Repository repository, String token, Path currenturi, URL requestURL)
            throws Exception {

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCARD\n");
        sb.append("VERSION:3.0\n");

        Property surnameProp = getProperty(person, surnamePropDefPointer);
        Property firstNameProp = getProperty(person, firstNamePropDefPointer);
        Property usernameProp = getProperty(person, usernamePropDefPointer);
        if (surnameProp != null) {
            sb.append("N:" + surnameProp.getFormattedValue());
            if (firstNameProp != null)
                sb.append(";" + firstNameProp.getFormattedValue());
            sb.append("\n");
        } else if (firstNameProp != null)
            sb.append("N:" + firstNameProp.getFormattedValue() + "\n");
        else if (usernameProp != null)
            sb.append("N:" + usernameProp.getFormattedValue() + "\n");

        if (firstNameProp != null) {
            sb.append("FN:" + firstNameProp.getFormattedValue());
            if (surnameProp != null)
                sb.append(" " + surnameProp.getFormattedValue());
            sb.append("\n");
        } else if (surnameProp != null)
            sb.append("FN:" + surnameProp.getFormattedValue() + "\n");
        else if (usernameProp != null)
            sb.append("FN:" + usernameProp.getFormattedValue() + "\n");

        Property positionProp = getProperty(person, positionPropDefPointer);
        if (positionProp != null)
            sb.append("TITLE:" + positionProp.getFormattedValue() + "\n");

        Property phoneProp = getProperty(person, phonePropDefPointer);
        if (phoneProp != null)
            sb.append("TEL;TYPE=WORK,VOICE:" + phoneProp.getFormattedValue() + "\n");

        Property mobileProp = getProperty(person, mobilePropDefPointer);
        if (mobileProp == null || mobileProp.getFormattedValue().equals(""))
                mobileProp = getProperty(person, alternativeCellPhonePropDefPointer);
        if (mobileProp != null)
            sb.append("TEL;TYPE=CELL:" + mobileProp.getFormattedValue() + "\n");

        Property faxProp = getProperty(person, faxPropDefPointer);
        if (faxProp != null)
            sb.append("TEL;TYPE=FAX:" + faxProp.getFormattedValue() + "\n");

        /*
         * For data input reasons addresses has to be put in one (the street
         * address) field. ADR;TYPE=WORK:PO;Address Line 1;Address Line
         * 2;City;Province;PostalCode;Country could however be used if this
         * should change in the future.
         */
        Property postalAddressProp = getProperty(person, postalAddressPropDefPointer);
        if (postalAddressProp != null)
            sb.append("ADR;TYPE=WORK,POSTAL:;;" + postalAddressProp.getFormattedValue() + ";;;;\n");

        Property visitingAddressProp = getProperty(person, alternativeVisitingAddressPropDefPointer);
        if (visitingAddressProp == null || visitingAddressProp.getFormattedValue().equals(""))
            visitingAddressProp = getProperty(person, visitingAddressPropDefPointer);
        if (visitingAddressProp != null)
            sb.append("ADR;TYPE=WORK:;;" + visitingAddressProp.getFormattedValue() + ";;;;\n");

        Property emailProp = getProperty(person, emailPropDefPointer);
        if (emailProp != null)
            sb.append("EMAIL;TYPE=INTERNET:" + emailProp.getFormattedValue() + "\n");

        Property pictureProp = getProperty(person, picturePropDefPointer);
        if (pictureProp != null) {
            String pic = b64Thumbnail(repository, token, currenturi, requestURL, pictureProp);
            if (pic != null)
                sb.append(pic);
        }

        sb.append("REV:" + getDtstamp() + "\n");
        sb.append("END:VCARD");

        return sb.toString();
    }

    private Property getProperty(Resource person, String propDefPointer) {
        PropertyTypeDefinition propDef = this.resourceTypeTree.getPropertyDefinitionByName(propDefPointer);
        if (propDef != null)
            return person.getProperty(propDef);

        return null;
    }

    private String getDtstamp() {
        String dateFormat = "yyyyMMdd'T'HHmmss'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        return sdf.format(Calendar.getInstance().getTime());
    }

    private String b64Thumbnail(Repository repository, String token, Path currenturi, URL requestURL,
            Property pictureProp) {

        String picturePath = pictureProp.getFormattedValue();
        Path p = null;
        try {
            URL pURL = URL.parse(picturePath);
            if (requestURL.getHost().equals(pURL.getHost()))
                p = pURL.getPath();
        } catch (Exception e) {
        }

        try {
            if (p == null && !picturePath.startsWith("/"))
                p = currenturi.extend(picturePath);
            else if (p == null)
                p = Path.fromString(picturePath);
        } catch (Exception e) {
        }

        if (p == null)
            return null;

        Resource r;
        try {
            r = repository.retrieve(token, p, true);
        } catch (Exception e) {
            return null;
        }

        Property thumbnail = this.getProperty(r, thumbnailPropDefPointer);
        InputStream is;

        if (thumbnail == null) {
            Property prop;
            if ((prop = getProperty(r, imageWidthPropDefPointer)) == null)
                return null;

            int width = prop.getIntValue();

            if (width > Integer.parseInt(maxImageWidth))
                return null;

            try {
                is = repository.getInputStream(token, p, true);
            } catch (Exception e) {
                return null;
            }
        } else {
            is = thumbnail.getBinaryStream();
        }

        /* Base64 encodes the thumbnail. */
        String encoded;
        try {
            encoded = Base64.encode(is);
        } catch (Exception e) {
            return null;
        }

        /*
         * Base64 encoding in vCards needs to be 75 characters on each line,
         * starting with a white space.
         */
        String output = "";
        int j = 0, k = 76, len = encoded.length();
        while (k < len) {
            output += "\n  " + encoded.substring(j, k);
            j = k;
            k += 76;
        }
        output += "\n  " + encoded.substring(j, len);

        return "PHOTO;BASE64:" + output + "\n";
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setFirstNamePropDefPointer(String firstNamePropDefPointer) {
        this.firstNamePropDefPointer = firstNamePropDefPointer;
    }

    @Required
    public void setSurnamePropDefPointer(String surnamePropDefPointer) {
        this.surnamePropDefPointer = surnamePropDefPointer;
    }

    @Required
    public void setUsernamePropDefPointer(String usernamePropDefPointer) {
        this.usernamePropDefPointer = usernamePropDefPointer;
    }

    @Required
    public void setPositionPropDefPointer(String positionPropDefPointer) {
        this.positionPropDefPointer = positionPropDefPointer;
    }

    @Required
    public void setPhonePropDefPointer(String phonePropDefPointer) {
        this.phonePropDefPointer = phonePropDefPointer;
    }

    @Required
    public void setAlternativeCellPhonePropDefPointer(String alternativeCellPhonePropDefPointer) {
        this.alternativeCellPhonePropDefPointer = alternativeCellPhonePropDefPointer;
    }

    @Required
    public void setMobilePropDefPointer(String mobilePropDefPointer) {
        this.mobilePropDefPointer = mobilePropDefPointer;
    }

    @Required
    public void setFaxPropDefPointer(String faxPropDefPointer) {
        this.faxPropDefPointer = faxPropDefPointer;
    }

    @Required
    public void setPostalAddressPropDefPointer(String postalAddressPropDefPointer) {
        this.postalAddressPropDefPointer = postalAddressPropDefPointer;
    }

    @Required
    public void setAlternativeVisitingAddressPropDefPointer(String alternativeVisitingAddressPropDefPointer) {
        this.alternativeVisitingAddressPropDefPointer = alternativeVisitingAddressPropDefPointer;
    }

    @Required
    public void setVisitingAddressPropDefPointer(String visitingAddressPropDefPointer) {
        this.visitingAddressPropDefPointer = visitingAddressPropDefPointer;
    }

    @Required
    public void setEmailPropDefPointer(String emailPropDefPointer) {
        this.emailPropDefPointer = emailPropDefPointer;
    }

    @Required
    public void setPicturePropDefPointer(String picturePropDefPointer) {
        this.picturePropDefPointer = picturePropDefPointer;
    }

    @Required
    public void setThumbnailPropDefPointer(String thumbnailPropDefPointer) {
        this.thumbnailPropDefPointer = thumbnailPropDefPointer;
    }

    @Required
    public void setImageWidthPropDefPointer(String imageWidthPropDefPointer) {
        this.imageWidthPropDefPointer = imageWidthPropDefPointer;
    }

    @Required
    public void setMaxImageWidth(String maxImageWidth) {
        this.maxImageWidth = maxImageWidth;
    }
}
