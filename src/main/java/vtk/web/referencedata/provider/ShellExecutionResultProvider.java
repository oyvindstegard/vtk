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
package vtk.web.referencedata.provider;



import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.shell.AbstractConsole;
import vtk.web.referencedata.ReferenceDataProvider;

/**
 */
public class ShellExecutionResultProvider implements ReferenceDataProvider {

    private String modelName;
    private AbstractConsole shell;
    private Map<String, Map<String, String>> groups;
    

    @Required
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }


    @Required
    public void setShell(AbstractConsole shell) {
        this.shell = shell;
    }

    @Required
    public void setGroups(Map<String, Map<String, String>> groups) {
        this.groups = groups;
    }
    

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        Map<String, Object> subModel = new HashMap<>();

        for (Iterator<String> groupIter = this.groups.keySet().iterator(); groupIter.hasNext();) {
            String groupName = groupIter.next();
            Map<String, String> group = this.groups.get(groupName);
            
            Map<String, String> resultGroupMap = new HashMap<>();

            for (Iterator<String> itemIter = group.keySet().iterator(); itemIter.hasNext();) {
                String itemName = itemIter.next();
                String expression = group.get(itemName);
                String result = null;

                try {
                    ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
                    PrintStream resultStream = new PrintStream(bufferStream);
                    this.shell.eval(expression, resultStream);
                    result = new String(bufferStream.toByteArray());
                } catch (Throwable t) {
                    result = "Error: " + t.getMessage();
                }
                resultGroupMap.put(itemName, result);
            }
            subModel.put(groupName, resultGroupMap);
        }

        model.put(this.modelName, subModel);
    }
}
