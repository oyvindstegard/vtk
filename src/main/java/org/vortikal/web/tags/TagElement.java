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

import org.vortikal.web.service.URL;

public class TagElement extends Tag implements Comparable<TagElement> {

    private int magnitude;
    private URL linkUrl;
    private int occurences;

    public TagElement(int magnitude, URL linkUrl, String text, int occurences) {
        super(text);
        this.magnitude = magnitude;
        this.linkUrl = linkUrl;
        this.occurences = occurences;
    }

    public int getMagnitude() {
        return magnitude;
    }

    public URL getLinkUrl() {
        return linkUrl;
    }

    // VTK-1107: Sets the text to compare to lowercase,
    // thus avoiding problem with sorting.
    @Override
    public int compareTo(TagElement other) {
        return this.getText().toLowerCase().compareTo(other.getText().toLowerCase());
    }

    public int getOccurences() {
        return occurences;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getText());
        if (linkUrl != null) {
            sb.append(": ").append(linkUrl);
        }
        sb.append(" [occurences: ").append(occurences).append(", magnitude: ").append(magnitude).append("]");
        return sb.toString();
    }

}
