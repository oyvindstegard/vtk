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
package vtk.repository.search.preprocessor;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.time.FastDateFormat;

import vtk.repository.search.QueryException;
import vtk.repository.search.preprocessor.QueryStringPreProcessor.ProcessorContext;

public class CurrentTimeExpressionEvaluator implements ExpressionEvaluator {

    private String variableName = "currentTime";
    private Pattern pattern = compilePattern();

    private Pattern compilePattern() {
        return Pattern.compile("(" + this.variableName + ")" + "(([+-])(\\d+[ymwdhMs](\\d+[ymwdhMs])*))?(\\\\|.+)?");
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
        this.pattern = compilePattern();
    }

    public boolean matches(String token) {
        Matcher m = this.pattern.matcher(token);
        return m.matches();
    }

    protected Calendar getCalendar() {
        return Calendar.getInstance();
    }

    @Override
    public String evaluate(String token, ProcessorContext ctx)
            throws QueryException {
        Matcher m = this.pattern.matcher(token);
        if (!m.matches()) {
            throw new QueryException("Query token: '" + token + "' does not match pattern");
        }
        Calendar calendar = getCalendar();

        String params = m.group(2);
        if (params != null) {
            String operator = m.group(3);
            String qtyString = m.group(4);
            processQuantityString(calendar, "+".equals(operator), qtyString);
        }
        String format = m.group(6);
        if (format == null) {
            return String.valueOf(calendar.getTimeInMillis());
        }
        format = format.substring(1);
        FastDateFormat f = FastDateFormat.getInstance(format);
        return f.format(calendar.getTime());
    }

    private void processQuantityString(Calendar currentDate, boolean add, String params) {
        Integer years = findNamedQuantity(params, "y", add);
        Integer months = findNamedQuantity(params, "m", add);
        Integer weeks = findNamedQuantity(params, "w", add);
        Integer days = findNamedQuantity(params, "d", add);
        Integer hours = findNamedQuantity(params, "h", add);
        Integer minutes = findNamedQuantity(params, "M", add);
        Integer seconds = findNamedQuantity(params, "s", add);

        if (years != null)
            currentDate.add(Calendar.YEAR, years.intValue());
        if (months != null)
            currentDate.add(Calendar.MONTH, months.intValue());
        if (weeks != null)
            currentDate.add(Calendar.DAY_OF_YEAR, weeks.intValue() * 7);
        if (days != null)
            currentDate.add(Calendar.DAY_OF_YEAR, days.intValue());
        if (hours != null)
            currentDate.add(Calendar.HOUR, hours.intValue());
        if (minutes != null)
            currentDate.add(Calendar.MINUTE, minutes.intValue());
        if (seconds != null)
            currentDate.add(Calendar.SECOND, seconds.intValue());

    }

    private Integer findNamedQuantity(String params, String identifier, boolean add) {
        // pattern: \\d+<identifier>(EOL|\\d)
        int idx = params.indexOf(identifier);
        if (idx == -1) {
            return null;
        }
        if (idx - 1 < 0) {
            return null;
        }
        if (idx + identifier.length() > params.length()) {
            return null;
        }
        if (idx + identifier.length() < params.length()) {
            // check for number after
            if (!Character.isDigit(params.charAt(idx + identifier.length()))) {
                return null;
            }
        }

        StringBuilder num = new StringBuilder();
        int numIdx = idx;
        while (numIdx > 0) {
            char c = params.charAt(numIdx - 1);
            if (!Character.isDigit(c)) {
                break;
            }
            num.insert(0, c);
            numIdx--;
        }
        try {
            int qty = Integer.parseInt(num.toString());
            if (!add) {
                qty = qty * -1;
            }
            return new Integer(qty);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
