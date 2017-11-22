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

import java.util.Objects;
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
    private Throwable failure = null;
    private T result = null;

    /**
     * Indicates whether this result is a success
     * @return {@code true} if this is a success, 
     * {@code false} otherwise
     */
    public boolean isSuccess() {
        return failure == null;
    }
    
    /**
     * Indicates whether this result is a failure
     * @return {@code true} if this is a failure, 
     * {@code false} otherwise
     */
    public boolean isFailure() {
        return failure != null;
    }
    
    /**
     * The failure of this result, if present
     */
    public final Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }
    
    /**
     * The value of this result, if present
     */
    public final Optional<T> result() {
        return Optional.ofNullable(result);
    }
    
    /**
     * Gets the value of this result if this is a success,
     * otherwise throws this failure.
     * @return the value of the {@link #result} field, if this is a success
     * @throws the value of the {@link #failure} field if this is a failure
     */
    public T get() throws Throwable {
        if (failure != null) {
            throw failure;
        }
        return result;
    }
    
    /**
     * Creates a result representing a successful operation.
     * @param result the result of the operation
     * @return the newly created instance
     */
    public static <T> Result<T> success(T result) {
        Objects.requireNonNull(result);
        return new Result<>(result, null);
    }
    
    /**
     * Creates a result representing a failed operation.
     * @param error the failure cause
     * @return the newly created instance
     */
    public static <T> Result<T> failure(Throwable error) {
        Objects.requireNonNull(error);
        return new Result<>(null, error);
    }
    
    /**
     * Attempts to create a {@link Result} from a {@link Supplier}. If 
     * the supplier throws an exception this method returns a failure, 
     * otherwise it returns a successful result.
     * @param supplier the {@link Supplier} of the value
     * @return the newly created instance
     */
    public static <T> Result<T> attempt(Supplier<T> supplier) {
        Throwable thrown = null;
        T result = null;
        try {
            result = supplier.get();
        }
        catch (Throwable t) {
            thrown = t;
        }
        if (thrown == null) {
            return new Result<>(result, null);
        }
        else {
            return new Result<>(null, thrown);
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
        if (failure != null) {
            return new Result<>(null, failure);
        }
        Result<U> mapped = null;
        Throwable thrown = null;
        try {
            mapped = mapper.apply(result);
        }
        catch (Throwable t) {
            thrown = t;
        }
        if (thrown == null) {
            return Objects.requireNonNull(mapped);
        }
        else {
            return new Result<>(null, thrown);
        }
    }
    
    /**
     * Applies the given function to the value from this success, 
     * otherwise returns this failure.
     * @param mapper the function from {@code T} to {@code U}
     * @return a success consisting of the mapped value, or this failure
     */
    public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
        if (failure != null) {
            return new Result<>(null, failure);
        }
        U mapped = null;
        Throwable thrown = null;
        try {
            mapped = mapper.apply(result);
        }
        catch (Throwable t) {
            thrown = t;
        }
        if (thrown == null) {
            return new Result<>(mapped, thrown);
        }
        else {
            return new Result<>(null, thrown);
        }
    }
    
    /**
     * Applies the given consumer function to the value from this success, 
     * otherwise does nothing.
     */
    public void forEach(Consumer<? super T> consumer) {
        if (failure == null) {
            consumer.accept(result);
        }
    }
    
    /**
     * Applies the given function to this failure and returns a new result with 
     * the mapped value, or returns this if this is a success.
     * @param recovery the recovery function
     * @return this if this is a success, otherwise the a new success result 
     * with the value as mapped by the recovery function
     */
    public Result<T> recover(Function<? super Throwable, ? extends T> recovery) {
        if (failure == null) {
            return this;
        }
        T mapped = recovery.apply(failure);
        return new Result<>(Objects.requireNonNull(mapped), null);
    }

    /**
     * Applies the given function to this failure and returns its result, 
     * or returns this if this is a success
     * @param recovery the recovery function
     * @return this if this is a success, otherwise the result returned 
     * by the recovery function 
     */
    public Result<T> recoverWith(Function<? super Throwable, ? extends Result<T>> recovery) {
        if (failure == null) {
            return this;
        }
        Result<T> mapped = null;
        Throwable thrown = null;
        try {
            mapped = recovery.apply(failure);
        }
        catch (Throwable t) {
            thrown = t;
        }
        if (thrown != null) {
            return new Result<>(null, thrown);
        }
        else {
            return Objects.requireNonNull(mapped);
        }
    }

    /**
     * Converts this result to an instance of {@link Optional}. 
     * @return an optional with the result if this is a success,
     *  otherwise {@link Optional#empty()}
     */
    public Optional<T> toOptional() {
        if (failure == null) {
            return Optional.ofNullable(result);
        }
        return Optional.empty();
    }
    
    private Result(T result, Throwable failure) {
        if (result != null && failure != null) {
            throw new IllegalArgumentException("Either result or failure must be NULL");
        }
        else if (result == null && failure == null) {
            throw new IllegalArgumentException("Both result and failure cannot be NULL");
        }
        this.result = result;
        this.failure = failure;
    }
    
    @Override
    public String toString() {
        if (failure != null) {
            return "Failure(" + failure + ")";
        }
        return "Success(" + result + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((failure == null) ? 0 : failure.hashCode());
        result = prime * result
                + ((this.result == null) ? 0 : this.result.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Result other = (Result) obj;
        if (failure == null) {
            if (other.failure != null)
                return false;
        }
        else if (!failure.equals(other.failure))
            return false;
        if (result == null) {
            if (other.result != null)
                return false;
        }
        else if (!result.equals(other.result))
            return false;
        return true;
    }
    
    
}
