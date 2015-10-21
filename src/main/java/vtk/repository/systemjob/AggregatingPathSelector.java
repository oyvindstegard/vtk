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

package vtk.repository.systemjob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.SystemChangeContext;

/**
 * Path selector which aggregates selected paths of a configured list of
 * sub-selectors.
 */
public class AggregatingPathSelector implements PathSelector {

    private List<PathSelector> pathSelectors;
    
    public AggregatingPathSelector(List<PathSelector> selectors) {
        if (selectors == null) {
            throw new IllegalArgumentException("Selectors cannot be null");
        }
        this.pathSelectors = selectors;
    }
    
    @Override
    public void selectWithCallback(Repository repository,
                                   SystemChangeContext context,
                                   PathSelectCallback callback) throws Exception {

        Set<Path> aggregated = new HashSet<>();

        for (PathSelector selector: this.pathSelectors) {
            selector.selectWithCallback(repository, context, new PathSelectCallback() {
                @Override
                public void beginBatch(int total) throws Exception { }
                @Override
                public void select(Path path) throws Exception {
                    aggregated.add(path);
                }
            });
        }
        
        callback.beginBatch(aggregated.size());
        for (Path path: aggregated) callback.select(path);
    }
}
