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
package vtk.text.tl;


public final class Symbol implements Token {

    private String value;
    
    public Symbol(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Argument is NULL");
        }
        if ("".equals(value.trim())) {
            throw new IllegalArgumentException("Empty symbol");
        }
        this.value = value;
    }
    
    public String getRawValue() {
        return this.value;
    }
    
    public String getSymbol() {
        return this.value;
    }
    
    public boolean isDefined(Context ctx) {
        return ctx.isDefined(this.value);
    }
    
    public Object getValue(Context ctx) {
        if (!ctx.isDefined(this.value)) {
            throw new RuntimeException(
                    "Symbol '" + this.value + "' not defined");
        }
        return ctx.get(this.value);
    }
    
    public String toString() {
        return "symbol:" + this.value;
    }
    
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof Symbol)) {
            return false;
        }
        return this.value.equals(((Symbol) o).value);
    }

    public int hashCode() {
        return this.value.hashCode();
    }

}
