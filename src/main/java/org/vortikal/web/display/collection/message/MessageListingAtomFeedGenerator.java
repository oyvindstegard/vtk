/* Copyright (c) 2012, University of Oslo, Norway
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
package org.vortikal.web.display.collection.message;

import org.apache.abdera.model.Entry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.text.html.HtmlFragment;
import org.vortikal.web.RequestContext;
import org.vortikal.web.display.collection.CollectionListingAtomFeedGenerator;
import org.vortikal.web.service.URL;

public class MessageListingAtomFeedGenerator extends CollectionListingAtomFeedGenerator {

    private final Log logger = LogFactory.getLog(MessageListingAtomFeedGenerator.class);

    @Override
    protected void setFeedEntrySummary(Entry entry, PropertySet result) throws Exception {
        Property messageProp = result.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "listingDisplayedMessage");
        if (messageProp != null) {
            try {
                URL baseURL = viewService.constructURL(result.getURI());
                HtmlFragment summary = htmlUtil.linkResolveFilter(messageProp.getStringValue(), baseURL, RequestContext
                        .getRequestContext().getRequestURL(), useProtocolRelativeImages);
                entry.setSummaryAsXhtml(summary.getStringRepresentation());
            } catch (Exception e) {
                logger.warn("Could not set feed entry summary as XHTML" + e.getMessage());

                // XXX Attempt to set as HTML?
                entry.setSummaryAsHtml(messageProp.getStringValue());
            }
        }
    }

}
