/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.util.net;

/**
 * 
 * @author oyviste
 *
 */
public abstract class AbstractIPv4Matcher implements IPv4Matcher {

    /**
     * Parse a number in dotted (unsigned-)decimal notation 
     * (octets separated by a dot).
     * 
     * Example of a valid 32 bit number in correct notation: '192.168.0.1'
     *  
     * @param dottedDecimalNumber
     * @return A byte-array with all the parsed octets (big endian).
     * @throws IllegalArgumentException
     * 
     */
    protected int[] parseDottedDecimalNumber(String dottedDecimalNumber) {
        
        String[] parts = dottedDecimalNumber.split("\\.");
        
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid number of parts in IPv4 address (must be 4 octets)");
        }
        
        int[] octets = new int[parts.length];
        
        for (int i = 0; i<parts.length; i++) {
            int n = Integer.parseInt(parts[i]);
            if (n < 0 || n > 0xFF) {
                throw new IllegalArgumentException("Invalid octet: " + parts[i]);
            }
            octets[i] = n;
        }
        
        return octets;
    }
    
    public abstract boolean matches(String ipv4Address);
    
}
