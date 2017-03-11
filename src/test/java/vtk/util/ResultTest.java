package vtk.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ResultTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testSuccess() {
        Object o = new Object();
        Result<Object> succ = Result.success(o);
        assertNotNull(succ.failure);
        assertNotNull(succ.result);
        assertNotNull(succ.result.get());
        assertTrue(succ.result.isPresent());
        assertTrue(o == succ.result.get());
        assertTrue(succ.result.isPresent());
    }
    
    @Test
    public void testFailure() {
        Throwable t = new Throwable();
        Result<Void> fail = Result.failure(t);
        assertNotNull(fail.failure);
        assertNotNull(fail.result);
        assertNotNull(fail.failure.get());
        assertFalse(fail.result.isPresent());
        assertTrue(t == fail.failure.get());
        assertFalse(fail.result.isPresent());
    }

    @Test
    public void testLambda() {
        Object o = new Object();
        Result<Object> succ = Result.attempt(() -> o);
        assertNotNull(succ.failure);
        assertNotNull(succ.result);
        assertNotNull(succ.result.get());
        assertTrue(succ.result.isPresent());
        assertTrue(o == succ.result.get());
        assertTrue(succ.result.isPresent());
        
        RuntimeException t = new RuntimeException();
        Result<Void> fail = Result.attempt(() -> { throw t; });
        assertNotNull(fail.failure);
        assertNotNull(fail.result);
        assertNotNull(fail.failure.get());
        assertFalse(fail.result.isPresent());
        assertTrue(t == fail.failure.get());
        assertFalse(fail.result.isPresent());
    }
    
    @Test
    public void testMap() {
        Result<Integer> integer = Result.success(22);
        Result<String> string = integer.map(i -> String.valueOf(i));
        assertTrue(string.result.isPresent());
        assertEquals("22", string.result.get());
        
        string = string.map(str -> "NaN");
        integer = string.map(str -> Integer.parseInt(str));
        assertTrue(integer.failure.isPresent());
    }
}
