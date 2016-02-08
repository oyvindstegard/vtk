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
package vtk.resourcemanagement;

public class EditRule {

    public enum EditRuleType {
        POSITION_BEFORE, POSITION_AFTER, GROUP, EDITHINT, NO_EDIT;
    }

    private static final String LB = "[";
    private static final String RB = "]";

    private String name;
    private EditRuleType type;
    private Object value;
    private boolean isJsonAttributeEditHint;

    public EditRule(String name, EditRuleType type, Object value, boolean isJsonAttributeEditHint) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.isJsonAttributeEditHint = isJsonAttributeEditHint;
    }

    public EditRule(String name, EditRuleType type, Object value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public EditRuleType getType() {
        return type;
    }

    public Object getValue() {
        return this.value;
    }

    public String getEditHintKey() {
        return parseEditHint(true);
    }

    public String getEditHintValue() {
        return parseEditHint(false);
    }

    public boolean isJsonAttributeEditHint() {
        return isJsonAttributeEditHint;
    }

    private String parseEditHint(boolean returnKey) {
        if (EditRuleType.EDITHINT.equals(this.type)) {
            if (this.value instanceof String) {
                String stringValue = (String) this.value;
                if (stringValue.contains(LB) && stringValue.contains(RB)) {
                    return returnKey ? stringValue.substring(0, stringValue.indexOf(LB)) : stringValue.substring(
                            stringValue.indexOf(LB) + 1, stringValue.indexOf(RB));
                }
                return stringValue;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.name);
        sb.append(": " + this.type + ", ");
        sb.append("[" + this.value + "]");
        return sb.toString();
    }

}
