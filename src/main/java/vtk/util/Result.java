/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.util;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wrapper class representing the result of some operation,
 * which was either a success or a failure. 
 *
 * @param <T> the type of the result
 */
public final class Result<T> {
    public final Optional<Throwable> failure;
    public final Optional<T> result;
    
    public static <T> Result<T> success(T result) {
        return new Result<>(Optional.of(result), Optional.empty());
    }
    
    public static <T> Result<T> failure(Throwable error) {
        return new Result<>(Optional.empty(), Optional.of(error));
    }
    
    public static <T> Result<T> attempt(Supplier<T> supplier) {
        try {
            return new Result<>(Optional.of(supplier.get()), Optional.empty());
        }
        catch (Throwable t) {
            return new Result<>(Optional.empty(), Optional.of(t));
        }
    }
    
    public <U> Result<U> map(Function<T,U> mapper) {
        if (failure.isPresent()) {
            return new Result<>(Optional.empty(), Optional.of(failure.get()));
        }
        try {
            U u = mapper.apply(result.get());
            return new Result<>(Optional.of(u), Optional.empty());
        }
        catch (Throwable t) {
            return new Result<>(Optional.empty(), Optional.of(t));
        }
    }
    
    private Result(Optional<T> result, Optional<Throwable> failure) {
        this.result = result;
        this.failure = failure;
    }
    
    @Override
    public String toString() {
        if (failure.isPresent()) {
            return "Failure(" + failure.get() + ")";
        }
        return "Success(" + result.get() + ")";
    }
}
