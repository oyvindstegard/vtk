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
package org.vortikal.repositoryimpl.query;

import java.math.BigInteger;
import java.text.ParseException;

import org.apache.lucene.document.DateTools;

/**
 * Low-level index field value encoder/decoder for some single-value data types.
 * 
 * @author oyviste
 *
 */
public final class FieldValueEncoder {

    private static final char[] HEX_CHARS = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
        
    
    private FieldValueEncoder() {} // Util

    /**
     * TODO: javadoc
     * @param dateValue
     * @return
     */
    public static String encodeDateValue(long dateValue) {
        return DateTools.timeToString(dateValue, DateTools.Resolution.SECOND);
    }
    
    /**
     * TODO: javadoc
     * @param encodedDateValue
     * @return
     */
    public static long decodeDateValue(String encodedDateValue) 
        throws FieldValueEncodingException {
        try {
            return DateTools.stringToTime(encodedDateValue);
        } catch (ParseException pe) {
            throw new FieldValueEncodingException(pe.getMessage());
        }
    }
    
    /**
     * Encode a (signed) <coe>int</code> into a hexadecimal unsigned <code>String</code>
     * representation suitable for indexing and lexicographical sorting.
     * 
     * @param i number to encode
     * @return
     */
   public static String encodeInteger(int i) {
        long uint = (long)i + 0x80000000L;
        char[] hex = Long.toHexString(uint).toCharArray();
        char[] output = {'0','0','0','0','0','0','0','0'};
        System.arraycopy(hex, 0, output, 8-hex.length, hex.length);
        return String.valueOf(output);
    }

    /**
     * TODO: javadoc
     * @param encodedInteger
     * @return
     */
    public static int decodeInteger(String encodedInteger) 
        throws FieldValueEncodingException {
        try {
            long uint = Long.parseLong(encodedInteger, 16);
            return (int)(uint - 0x80000000L);
        } catch (NumberFormatException nfe) {
            throw new FieldValueEncodingException(nfe.getMessage());
        }
    }

    /**
     * Encode a (signed) <coe>long</code> integer into a hexadecimal unsigned 
     * <code>String</code> representation suitable for indexing and 
     * lexicographical sorting.
     * 
     * @param l number to encode
     * @return
     */
    public static String encodeLong(long l) {
        // Create 64 bits unsigned long representation for lexicographical sorting
        byte[] ulong = new byte[8];

        if (l < 0) {
            l += Long.MAX_VALUE+1; // Add using Java arithmetic (no overflow)
        } else {
            ulong[0] |= 0x80;  // Add using bit-ops (avoid overflow)
        }
        
        for (int i=0; i<8; i++) {
            ulong[i] |= (byte)((l >>> (56-i*8)) & 0xFF);
        }
        
        return unsignedLongToPaddedHexString(ulong);
    }

    /**
     * TODO: javadoc
     * @param encodedLong
     * @return
     */
    public static long decodeLong(String encodedLong) 
        throws FieldValueEncodingException {
      
        try {
            BigInteger ulong = new BigInteger(encodedLong, 16);
            return ulong.subtract(
                    BigInteger.valueOf(Long.MAX_VALUE)).subtract(BigInteger.ONE).longValue();
        } catch (NumberFormatException nfe) {
            throw new FieldValueEncodingException(nfe.getMessage());
        }
    }

    /**
     * TODO: javadoc
     * @param ulong
     * @return
     */
    private static String unsignedLongToPaddedHexString(byte[] ulong) {
        if (ulong.length != 8) {
            throw new IllegalArgumentException(
                    "Unsigned long integers must be represented by exactly 8 bytes.");
        }

        char[] output = {
            '0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0'};

        // Dump hex characters
        byte ch = 0x00;
        for (int i=0; i<8; i++) {
            ch = (byte)((ulong[i] >>> 4) & 0x0F);
            output[2*i] = HEX_CHARS[(int)ch];
            ch = (byte)(ulong[i] & 0x0F);
            output[2*i+1] = HEX_CHARS[(int)ch];
        }

        return String.valueOf(output);
    }
    
    
}
