/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.util.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vladsch.flexmark.Extension;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.DataHolder;
import com.vladsch.flexmark.util.options.MutableDataHolder;
import com.vladsch.flexmark.util.options.MutableDataSet;

public final class Markdown {
    
    public static enum Flavor {
        DEFAULT,
        GFM
    }

    private static final DataHolder DEFAULT_OPTIONS;
    private static final DataHolder GFM_OPTIONS;
    
    static {
        List<Extension> extensions = new ArrayList<>();
        extensions.add(AutolinkExtension.create());
        extensions.add(TablesExtension.create());
        extensions.add(DefinitionExtension.create());
        extensions.add(StrikethroughExtension.create());
        MutableDataHolder options = new MutableDataSet();
        options.set(TablesExtension.TRIM_CELL_WHITESPACE, false);
        options.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, false);
        options.set(Parser.HEADING_NO_ATX_SPACE, true);
        options.set(Parser.EXTENSIONS, Collections.unmodifiableList(extensions));
        DEFAULT_OPTIONS = options.toImmutable();
        
        extensions = new ArrayList<>(extensions);
        extensions.add(TaskListExtension.create());
        options = new MutableDataSet();
        options.set(TablesExtension.TRIM_CELL_WHITESPACE, false);
        options.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, false);
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        options.set(HtmlRenderer.HARD_BREAK, "<br />\n");
        options.set(Parser.HEADING_NO_ATX_SPACE, true);
        options.set(Parser.EXTENSIONS, Collections.unmodifiableList(extensions));
        GFM_OPTIONS = options.toImmutable();
    }
    
    
    public static String html(String input) {
        return html(input, Flavor.DEFAULT);
    }
    
    public static String html(String input, Flavor flavor) {
        DataHolder options = flavor == Flavor.GFM ? GFM_OPTIONS : DEFAULT_OPTIONS;
        Parser parser = Parser.builder(options).build();
        Node document = parser.parse(input);
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        return renderer.render(document);
    }
    
}
