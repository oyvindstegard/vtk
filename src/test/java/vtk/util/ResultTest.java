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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class ResultTest {

    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testInvalidArgs() {
        expectIllegalArgument(() -> Result.attempt(() -> null));
        expectIllegalArgument(() -> Result.failure(null));
        expectIllegalArgument(() -> Result.success(null));
        expectIllegalArgument(() -> Result.success(new Object()).map(o -> null));
        expectIllegalArgument(() -> Result.success(new Object()).flatMap(o -> null));
        expectIllegalArgument(() -> Result.failure(new Exception()).recover(t -> null));
    }

    @Test
    public void testSuccess() {
        Object o = new Object();
        Result<Object> succ = Result.success(o);
        assertNotNull(succ.failure());
        assertNotNull(succ.result());
        assertNotNull(succ.result().get());
        assertTrue(succ.result().isPresent());
        assertTrue(o == succ.result().get());
        assertTrue(succ.result().isPresent());
    }
    
    @Test
    public void testFailure() {
        Throwable t = new Throwable();
        Result<Void> fail = Result.failure(t);
        assertNotNull(fail.failure());
        assertNotNull(fail.result());
        assertNotNull(fail.failure().get());
        assertFalse(fail.result().isPresent());
        assertTrue(t == fail.failure().get());
        assertFalse(fail.result().isPresent());
    }

    @Test
    public void testLambda() {
        Object o = new Object();
        Result<Object> succ = Result.attempt(() -> o);
        assertNotNull(succ.failure());
        assertNotNull(succ.result());
        assertNotNull(succ.result().get());
        assertTrue(succ.result().isPresent());
        assertTrue(o == succ.result().get());
        assertTrue(succ.result().isPresent());
        
        RuntimeException t = new RuntimeException();
        Result<Void> fail = Result.attempt(() -> { throw t; });
        assertNotNull(fail.failure());
        assertNotNull(fail.result());
        assertNotNull(fail.failure().get());
        assertFalse(fail.result().isPresent());
        assertTrue(t == fail.failure().get());
        assertFalse(fail.result().isPresent());
    }
    
    @Test
    public void testMap() {
        Result<Integer> integer = Result.success(22);
        Result<String> string = integer.map(i -> String.valueOf(i));
        assertTrue(string.result().isPresent());
        assertEquals("22", string.result().get());
        
        string = string.map(str -> "NaN");
        integer = string.map(str -> Integer.parseInt(str));
        assertTrue(integer.failure().isPresent());
    }

    @Test
    public void testFlatMap() {
        Result<Integer> integer = Result.success(22);
        Result<String> string = integer.flatMap(i -> Result.success(String.valueOf(i)));
        assertTrue(string.result().isPresent());
        assertEquals("22", string.result().get());
        
        string = string.flatMap(str -> Result.success("NaN"));
        integer = string.flatMap(str -> Result.attempt(() -> Integer.parseInt(str)));
        assertTrue(integer.failure().isPresent());
    }

    @Test
    public void testToOptional() {
        Result<Integer> integer = Result.success(-537);
        assertEquals(integer.toOptional(), Optional.of(-537));
        Result<Integer> failure = Result.attempt(() -> Integer.parseInt("NaN"));
        assertEquals(failure.toOptional(), Optional.empty());
    }
    
    @Test
    public void testRecover() {
        Result<String> ok = Result.success("ok");
        Result<String> err = Result.failure(new Throwable("err"));
        assertEquals(ok, err.recover(t -> "ok"));
        assertEquals(ok, err.recoverWith(t -> Result.success("ok")));
    }
    
    private void expectIllegalArgument(Runnable r) {
        try {
            r.run();
            throw new IllegalStateException(
                    "Code block should have thrown NullPointerException "
                    + "or IllegalArgumentException, but did not");
        }
        catch (NullPointerException | IllegalArgumentException expected) {}
    }
    
}
