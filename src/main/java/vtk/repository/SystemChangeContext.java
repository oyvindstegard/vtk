/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.repository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.time.FastDateFormat;
import vtk.repository.resourcetype.PropertyTypeDefinition;

/**
 * Store context used for performing system change stores, where only
 * selected properties are evaluated upon store, and system job status is updated.
 * 
 */
public class SystemChangeContext implements StoreContext {

    private final String jobName;
    private final Date time;
    private final List<PropertyTypeDefinition> affectedProperties;
    private final PropertyTypeDefinition systemJobStatusPropDef;
    private final boolean ignoreLocking;

    /**
     * 
     * @param jobName name/identifier of system job performing the store
     * @param affectedProperties the properties which shall be affected by the store
     * @param systemJobStatusPropDef the property definition for system job status
     */
    public SystemChangeContext(String jobName,
                               List<PropertyTypeDefinition> affectedProperties,
                               PropertyTypeDefinition systemJobStatusPropDef) {
        this.jobName = jobName;
        this.time = new Date();
        this.affectedProperties = Collections.unmodifiableList(affectedProperties);
        this.systemJobStatusPropDef = systemJobStatusPropDef;
        this.ignoreLocking = false;
    }
    
    /**
     * @param jobName name/identifier of system job performing the store
     * @param affectedProperties the properties which shall be affected by the
     * store
     * @param systemJobStatusPropDef the property definition for system job
     * status
     * @param ignoreLocking Flags if repository should ignore any resource lock
     * when the store operation is executed.
     */
    public SystemChangeContext(String jobName,
                               List<PropertyTypeDefinition> affectedProperties,
                               PropertyTypeDefinition systemJobStatusPropDef,
                               boolean ignoreLocking) {
        this.jobName = jobName;
        this.time = new Date();
        this.affectedProperties = Collections.unmodifiableList(affectedProperties);
        this.systemJobStatusPropDef = systemJobStatusPropDef;
        this.ignoreLocking = ignoreLocking;
    }

    /**
     * 
     * @return name of system job
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 
     * @return timestamp of system job
     */
    public Date getTimestamp() {
        return time;
    }
    
    /**
     * Flags if repository should ignore lock checking when the store operation
     * is executed. This will allow store on locked resources. Only enable if
     * certain that the store operation cannot affect user-editable data.
     *
     * @return <code>true</code> if locking shall be ignored.
     */
    public boolean isIgnoreLocking() {
        return ignoreLocking;
    }

    public String getTimestampFormatted() {
        return formatTimestamp(this.time);
    }
    
    public static String formatTimestamp(Date timestamp) {
        return FastDateFormat.getInstance("yyyyMMdd HH:mm:ss").format(timestamp);
    }
    
    public static Date parseTimestamp(String time) throws ParseException {
        return new SimpleDateFormat("yyyyMMdd HH:mm:ss").parse(time);
    }
 
    public List<PropertyTypeDefinition> getAffectedProperties() {
        return affectedProperties;
    }
    
    public PropertyTypeDefinition getSystemJobStatusPropDef() {
        return this.systemJobStatusPropDef;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.jobName);
        sb.append(", created at ").append(this.time);
        sb.append(", affecting ").append(this.affectedProperties);
        return sb.toString();
    }

}
