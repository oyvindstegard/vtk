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
package org.vortikal.web.tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.repository.search.QueryException;
import org.vortikal.web.RequestContext;
import org.vortikal.web.reporting.TagsReportingComponent;
import org.vortikal.web.reporting.TagsReportingComponent.TagFrequency;
import org.vortikal.web.search.Listing;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

public class RepositoryTagElementsDataProvider {

    private TagsReportingComponent tagsReporter;
    private Service tagService;

    private boolean servesRoot = true;

    @Required
    public void setTagsReporter(TagsReportingComponent tagsReporter) {
        this.tagsReporter = tagsReporter;
    }

    @Required
    // to be removed..
    public void setServesRoot(boolean servesRoot) {
        this.servesRoot = servesRoot;
    }

    @Required
    public void setTagService(Service tagService) {
        this.tagService = tagService;
    }

    public List<TagElement> getTagElements(Path scopeUri, String token, int magnitudeMin, int magnitudeMax, int limit,
            int tagOccurenceMin) throws Exception {
        return this.getTagElements(scopeUri, token, magnitudeMin, magnitudeMax, limit, tagOccurenceMin, null, null,
                null, false);
    }

    public List<TagElement> getTagElements(Path scopeUri, String token, int magnitudeMin, int magnitudeMax, int limit,
            int tagOccurenceMin, List<ResourceTypeDefinition> resourceTypeDefs, List<String> urlSortingParams,
            String overrideResourceTypeTitle, boolean displayScope) throws Exception {

        // Do data report query
        List<TagFrequency> result = this.tagsReporter
                .getTags(scopeUri, resourceTypeDefs, limit, tagOccurenceMin, token);

        // Generate list of tag elements
        List<TagElement> tagElements = generateTagElementList(scopeUri, resourceTypeDefs, urlSortingParams,
                overrideResourceTypeTitle, displayScope, result, magnitudeMax, magnitudeMin);

        return tagElements;
    }

    private List<TagElement> generateTagElementList(Path scopeUri, List<ResourceTypeDefinition> resourceTypeDefs,
            List<String> urlSortingParmas, String overrideResourceTypeTitle, boolean displayScope,
            List<TagFrequency> freqList, int magnitudeMax, int magnitudeMin) {

        // Makes a list with TagElement instances
        List<TagElement> tagElements = new ArrayList<TagElement>(freqList.size());

        if (!freqList.isEmpty()) {

            int minFreq = freqList.get(freqList.size() - 1).getFrequency();
            int maxFreq = freqList.get(0).getFrequency();

            for (TagFrequency tf : freqList) {
                String tagName = tf.getTag();
                URL link = this.getUrl(tagName, scopeUri, resourceTypeDefs, urlSortingParmas,
                        overrideResourceTypeTitle, displayScope);

                int magnitude = getNormalizedMagnitude(tf.getFrequency(), maxFreq, minFreq, magnitudeMin, magnitudeMax);

                tagElements.add(new TagElement(magnitude, link, tagName, tf.getFrequency()));
            }

            // Sort alphabetically
            // XXX: locale-dependent sorting ?
            Collections.sort(tagElements);
        }

        return tagElements;
    }

    private int getNormalizedMagnitude(int frequency, int maxFreq, int minFreq, int magnitudeMin, int magnitudeMax) {
        if (maxFreq == minFreq || magnitudeMin == magnitudeMax) {
            return (magnitudeMin + magnitudeMax) / 2;
        }

        int maxLeveled = maxFreq - minFreq;
        int frequencyLeveled = frequency - minFreq;
        float magnitude = (float) frequencyLeveled / (float) maxLeveled;

        return (int) Math.round(magnitude * (magnitudeMax - magnitudeMin) + magnitudeMin);
    }

    private URL getUrl(String tagName, Path scopeUri, List<ResourceTypeDefinition> resourceTypeDefs,
            List<String> urlSortingParmas, String overrideResourceTypeTitle, boolean displayScope) {

        URL url = this.tagService.constructURL(scopeUri);
        if (scopeUri.isRoot() && !this.servesRoot) {
            scopeUri = RequestContext.getRequestContext().getCurrentCollection();
            url = this.tagService.constructURL(scopeUri);
            url.addParameter(TagsHelper.SCOPE_PARAMETER, Path.ROOT.toString());
        }
        url.addParameter(TagsHelper.TAG_PARAMETER, tagName);
        if (resourceTypeDefs != null && resourceTypeDefs.size() > 0) {
            for (ResourceTypeDefinition resourceTypeDef : resourceTypeDefs) {
                url.addParameter(TagsHelper.RESOURCE_TYPE_PARAMETER, resourceTypeDef.getName());
            }
        }
        if (urlSortingParmas != null && urlSortingParmas.size() > 0) {
            for (String urlSortingParam : urlSortingParmas) {
                url.addParameter(Listing.SORTING_PARAM, urlSortingParam);
            }
        }
        if (!StringUtils.isBlank(overrideResourceTypeTitle)) {
            url.addParameter(TagsHelper.OVERRIDE_RESOURCE_TYPE_TITLE_PARAMETER, overrideResourceTypeTitle);
        }
        if (displayScope) {
            url.addParameter(TagsHelper.DISPLAY_SCOPE_PARAMETER, Boolean.TRUE.toString());
        }

        return url;
    }
}
