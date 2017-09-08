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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wrapper class representing the result of some operation,
 * which was either a success or a failure. This is indicated by the fields
 * {@link #result} and {@link #failure}, of which only one can be present.
 *
 * @param <T> the type of the result
 */
public final class Result<T> {
    
    /**
     * The failure of this result, if present
     */
    public final Optional<Throwable> failure;
    
    /**
     * The value of this result, if present
     */
    public final Optional<T> result;
    
    /**
     * Gets the value of this result if this is a success,
     * otherwise throws this failure.
     * @return the value of the {@link #result} field, if this is a success
     * @throws the value of the {@link #failure} field if this is a failure
     */
    public T get() throws Throwable {
        if (failure.isPresent()) {
            throw failure.get();
        }
        return result.get();
    }
    
    /**
     * Creates a result representing a successful operation.
     * @param result the result of the operation
     * @return the newly created instance
     */
    public static <T> Result<T> success(T result) {
        return new Result<>(Optional.of(result), Optional.empty());
    }
    
    /**
     * Creates a result representing a failed operation.
     * @param error the failure cause
     * @return the newly created instance
     */
    public static <T> Result<T> failure(Throwable error) {
        return new Result<>(Optional.empty(), Optional.of(error));
    }
   
    /**
     * Attempts to create a {@link Result} from a {@link Supplier}. If 
     * the supplier throws an exception this method returns a failure, 
     * otherwise it returns a successful result.
     * @param supplier the {@link Supplier} of the value
     * @return the newly created instance
     */
    public static <T> Result<T> attempt(Supplier<T> supplier) {
        try {
            return new Result<>(Optional.of(supplier.get()), Optional.empty());
        }
        catch (Throwable t) {
            return new Result<>(Optional.empty(), Optional.of(t));
        }
    }

    /**
     * Takes a function that returns a {@link Result} and applies it to the 
     * value of this result (if this result is successful). The resulting 
     * {@link Result} instance is returned. If this result represents a failure,
     * it will be propagated as the failure of the next result.
     * @param mapper the function from {@code T} to {@code Result<U>}
     * @return a success consisting of the mapped value, or this failure
     */
    public <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
        if (failure.isPresent()) {
            return new Result<>(Optional.empty(), Optional.of(failure.get()));
        }
        try {
            return mapper.apply(result.get());
        }
        catch (Throwable t) {
            return new Result<>(Optional.empty(), Optional.of(t));
        }
    }
    
    /**
     * Applies the given function to the value from this success, 
     * otherwise returns this failure.
     * @param mapper the function from {@code T} to {@code U}
     * @return a success consisting of the mapped value, or this failure
     */
    public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
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
    
    /**
     * Applies the given consumer function to the value from this success, 
     * otherwise does nothing.
     */
    public void forEach(Consumer<? super T> consumer) {
        if (result.isPresent()) {
            consumer.accept(result.get());
        }
    }
    
    /**
     * Applies the given function to this failure and returns a new result with 
     * the mapped value, or returns this if this is a success.
     * @param recovery the recovery function}
     * @return this if this is a success, otherwise the a new success result 
     * with the value as mapped by the recovery function
     */
    public Result<T> recover(Function<? super Throwable, ? extends T> recovery) {
        if (failure.isPresent()) {
            T t = recovery.apply(failure.get());
            return new Result<>(Optional.of(t), Optional.empty());
        }
        else {
            return this;
        }
    }

    /**
     * Applies the given function to this failure and returns its result, 
     * or returns this if this is a success
     * @param recovery the recovery function
     * @return this if this is a success, otherwise the result returned 
     * by the recovery function 
     */
    public Result<T> recoverWith(Function<? super Throwable, ? extends Result<T>> recovery) {
        if (failure.isPresent()) {
            return recovery.apply(failure.get());
        }
        else {
            return this;
        }
    }

    /**
     * Converts this result to an instance of {@link Optional}. 
     * @return an optional with the result if this is a success, 
     * otherwise {@link Optional#empty()}
     */
    public Optional<T> toOptional() {
        return result;
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
