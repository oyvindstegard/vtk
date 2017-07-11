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
package vtk.web.display.autocomplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.web.reporting.TagsReportingComponent;
import vtk.web.reporting.TagsReportingComponent.TagFrequency;
import vtk.web.tags.Tag;

/**
 * Provide keywords completion data from repository.
 */
public class RepositoryTagsDataProvider implements VocabularyDataProvider<Tag> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private TagsReportingComponent tagsReporter;

    /*
     * (non-Javadoc)
     * 
     * @see
     * vtk.web.display.autocomplete.VocabularyDataProvider#getCompletions
     * (java.lang.String, vtk.repository.Path, java.lang.String)
     */
    @Override
    public List<Tag> getCompletions(String prefix, CompletionContext context) {
        List<Tag> tags = getCompletions(context);
        filterTagListByPrefix(prefix, tags);
        return tags;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * vtk.web.display.autocomplete.VocabularyDataProvider#getCompletions
     * (vtk.repository.Path, java.lang.String)
     */
    @Override
    public List<Tag> getCompletions(CompletionContext context) {
        // TODO might consider adding limit on number of unique tags that are
        // fetched.
        try {
            List<TagFrequency> reportResult = tagsReporter
                    .getTags(context.request(), context.getContextUri(), null, -1, -1,
                            context.getToken());
            List<Tag> retVal = new ArrayList<>(reportResult.size());
            for (TagFrequency tf : reportResult) {
                retVal.add(new Tag(tf.getTag()));
            }

            return retVal;
        }
        catch (Exception qe) {
            logger.warn("Failed to execute query", qe);

            // Return empty list when failed, for now.
            return Collections.emptyList();
        }
    }

    // Filter *case-insensitively* by prefix
    private void filterTagListByPrefix(String prefix, List<Tag> list) {
        Iterator<Tag> i = list.iterator();
        while (i.hasNext()) {
            String tagText = i.next().getText();
            if (!(prefix.length() <= tagText.length() && 
                    tagText.substring(0, prefix.length()).equalsIgnoreCase(prefix))) {
                i.remove();
            }
        }
    }

    @Required
    public void setTagsReporter(TagsReportingComponent tagsReporter) {
        this.tagsReporter = tagsReporter;
    }

}
