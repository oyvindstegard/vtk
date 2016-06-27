/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.text.tl.expr;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;

public class DateCompareFunction extends Function {

    public DateCompareFunction(Symbol symbol) {
        super(symbol, 2);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        Object o1 = args[0];
        Object o2 = args[1];
        if (o1 == null || o2 == null) {
            throw new IllegalArgumentException("Argument is NULL");
        }
        if (!(o1 instanceof String) || !(o2 instanceof String)) {
            throw new IllegalArgumentException("Illegal argument type");
        }

        Date d1 = parseDate((String) o1);
        Date d2 = parseDate((String) o2);

        return d1.compareTo(d2);
    }

    private Date parseDate(String dateStringToParse) {
        final String[] formats = {"yyyy-MM-dd HH:mm", "yyyy-MM-dd"};

        Date ret = null;

        if (dateStringToParse.equals("now")) {
            ret = new Date();
        }

        for (String format : formats) {
            try {
                ret = new SimpleDateFormat(format).parse(dateStringToParse);
                break;
            } catch (ParseException pe) {
                // Ignore and throw new IllegalArgumentException if ret is null after for loop
            }
        }

        if (ret == null) {
            throw new IllegalArgumentException("Could not parse '" + dateStringToParse + "' to Date");
        }

        return ret;
    }

}
