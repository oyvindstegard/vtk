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
package vtk.repository.resourcetype;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.MessageSource;

/**
 * Provides a default intermediate and final separator, as well as possibility
 * to provide a message source with format and locale sensitive variants.
 *
 * <p>Keys in message sources must be on the form:
 *  {@code "valueSeparator.intermediate.<format>"} and {@code "valueSeparator.final.<format>"}. For the
 * default {@code null} format, the {@code ".<format>"} part of the
 * message key may be omitted.
 */
public class ConfigurableValueSeparator implements ValueSeparator {
    
    private final String defaultIntermediateSeparator;
    private final String defaultFinalSeparator;
    private final Optional<MessageSource> separators;

    public ConfigurableValueSeparator() {
        this(", ", ", ", null);
    }

    public ConfigurableValueSeparator(String defaultIntermediateSeparator, String defaultFinalSeparator) {
        this(defaultIntermediateSeparator, defaultFinalSeparator, null);
    }

    public ConfigurableValueSeparator(String defaultIntermediateSeparator, String defaultFinalSeparator, MessageSource messages) {
        this.defaultIntermediateSeparator = Objects.requireNonNull(defaultIntermediateSeparator);
        this.defaultFinalSeparator = Objects.requireNonNull(defaultFinalSeparator);
        this.separators = Optional.ofNullable(messages);
    }

    @Override
    public String getFinalSeparator(Value value, String format, Locale locale) {
        if (separators.isPresent()) {
            return separators.get().getMessage("valueSeparator.final"
                    + (format != null ? "." + format : ""), null, defaultFinalSeparator, locale);
        }

        return defaultFinalSeparator;
    }

    @Override
    public String getIntermediateSeparator(Value value, String format, Locale locale) {
        if (separators.isPresent()) {
            return separators.get().getMessage("valueSeparator.intermediate"
                    + (format != null ? "." + format : ""), null, defaultIntermediateSeparator, locale);
        }

        return defaultIntermediateSeparator;
    }


}
