/* Copyright (c) 2013,2015 University of Oslo, Norway
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
package vtk.resourcemanagement.parser;

import org.junit.BeforeClass;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import vtk.resourcemanagement.StructuredResourceDescription;

import java.util.List;

public abstract class StructuredResourceParserTest {
    private static final ResourceLoader resourceLoader = new DefaultResourceLoader();
    protected static List<StructuredResourceParser.ParsedNode> PARSE_TREE;

    public static Resource getSourceResource() {
        return new ClassPathResource("/vtk/beans/structured-resources-test.vrtx");
    }

    @BeforeClass
    public static void init() throws Exception {
        StructuredResourceParser parser = new StructuredResourceParser(getSourceResource(), resourceLoader);
        PARSE_TREE = parser.parse();
    }

    public StructuredResourceDescription getNodeWithName(String name) {
        return getNodeWithName(PARSE_TREE, name);
    }

    private StructuredResourceDescription getNodeWithName(
            List<StructuredResourceParser.ParsedNode> nodes,
            String name
    ) {
        for (StructuredResourceParser.ParsedNode node : nodes) {
            if (node.getName().equals(name)) {
                return node.getStructuredResourceDescription();
            }
            if (node.hasChildren()) {
                StructuredResourceDescription foundChild = getNodeWithName(node.getChildren(), name);
                if (foundChild != null) {
                    return foundChild;
                }
            }
        }
        return null;
    }

}
