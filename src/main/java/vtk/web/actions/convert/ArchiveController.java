/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.web.actions.convert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vtk.repository.Path;

public class ArchiveController extends CopyController<ArchiveCommand> {

    @Override
    protected ArchiveCommand createCommand(String name, String url) {
        return new ArchiveCommand(name, url);
    }

    @Override
    protected void processCopyAction(Path originalUri, Path copyUri, ArchiveCommand archiveCommand) 
            throws Exception {
        Map<String, Object> properties = null;
        String ignorableResources = archiveCommand.getIgnorableResources();
        if (ignorableResources != null) {
            properties = new HashMap<String, Object>();
            properties.put("ignore", this.getIgnoreList(ignorableResources));
        }

        this.copyAction.process(originalUri, copyUri, properties);
    }

    private List<String> getIgnoreList(String ignorableResources) {
        List<String> ignoreList = new ArrayList<String>();
        String[] ss = ignorableResources.split(",");
        for (String s : ss) {
            s = s.trim();
            if (!"".equals(s)) {
                ignoreList.add(s);
            }
        }
        return ignoreList;
    }

}
