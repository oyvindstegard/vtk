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
import java.util.Locale;
import org.apache.commons.lang.time.FastDateFormat;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;

public class DateFormatFunction extends Function {

    public DateFormatFunction(Symbol symbol) {
        super(symbol, 4);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        Object o1 = args[0];
        Object o2 = args[1];
        Object o3 = args[2];
        Object o4 = args[3];
        if (o1 == null || o2 == null || o3 == null) {
            throw new IllegalArgumentException("Argument is NULL");
        }
        if (!(o1 instanceof String) || !(o2 instanceof String) || !(o3 instanceof String) || (o4 != null && !(o4 instanceof String))) {
            throw new IllegalArgumentException("Illegal argument type");
        }

        Date d;
        try {
            d = new SimpleDateFormat((String) o2).parse((String) o1);
        } catch (ParseException pe) {
            throw new IllegalArgumentException(pe.getLocalizedMessage());
        }

        FastDateFormat f;

        if (o3.equals("en")) {
            if(o4 == null) {
                f = FastDateFormat.getInstance("MMM d, yyyy hh:mm a", new Locale("en"));
            } else {
                f = FastDateFormat.getInstance((String) o4, new Locale("en"));
            }
        } else {
            if(o4 == null) {
                f = FastDateFormat.getInstance("d. MMM. yyyy HH:mm", new Locale("no"));
            } else {
                f = FastDateFormat.getInstance((String) o4, new Locale("no"));
            }
        }

        return f.format(d);
    }

}
