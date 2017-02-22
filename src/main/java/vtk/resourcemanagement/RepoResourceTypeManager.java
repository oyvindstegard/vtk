/* Copyright (c) 2017 University of Oslo, Norway
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
package vtk.resourcemanagement;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.resourcemanagement.parser.StructuredResourceParser;
import vtk.resourcemanagement.parser.StructuredResourceParser.ParsedNode;

public class RepoResourceTypeManager {
    
    private Repository repository;
    private Path collectionName;
    private StructuredResourceManager resourceManager;

    private class RepoDefinitionLoader 
        implements StructuredResourceParser.DefinitionSource {
        private Path uri;
        
        public RepoDefinitionLoader(Path uri) {
            this.uri = uri;
        }

        @Override
        public String description() {
            return uri.getName();
        }

        @Override
        public InputStream content() throws IOException {
            try {
                return repository.getInputStream(null, uri, false);
            }
            catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
    
    public RepoResourceTypeManager(Repository repository, String collectionName, 
            StructuredResourceManager resourceManager) {
        this.repository = repository;
        this.collectionName = Path.fromString(collectionName);
        this.resourceManager = resourceManager;
        load();
    }

    
    public void load() {
        try {
            Resource[] children = repository.listChildren(null, collectionName, false);
            for (Resource r: children) {
                if (r.isCollection()) continue;
                if (!r.getName().endsWith(".vrtx")) continue;
                parse(r);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void parse(Resource r) throws Exception {
        StructuredResourceParser parser = new StructuredResourceParser(
                new RepoDefinitionLoader(r.getURI()));
        List<ParsedNode> nodes = parser.parse();
        for (StructuredResourceParser.ParsedNode node : nodes) {
            
            StructuredResourceDescription desc = node.getStructuredResourceDescription();
            // XXX: Don't allow required properties and resource type inheritance?
            if (resourceManager.get(desc.getName()) != null) {

                resourceManager.refresh(node.getStructuredResourceDescription());
            }
            else {
                resourceManager.register(node.getStructuredResourceDescription());
            }
        }
    }
}
