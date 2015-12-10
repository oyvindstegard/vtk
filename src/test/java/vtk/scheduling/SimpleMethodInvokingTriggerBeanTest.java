package vtk.scheduling;

import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;
import org.junit.Test;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.event.ContextRefreshedEvent;



/**
 * Test case  for {@link vtk.scheduling.SimpleMethodInvokingTriggerBean}.
 * 
 */
public class SimpleMethodInvokingTriggerBeanTest {
    
    private SimpleMethodInvokingTriggerBean trigger;
    private int triggerCount;
    private boolean testFailed;
    private boolean triggered;
    private StringBuffer errors;
    private final String threadName = "test-trigger-thread";
    private final Object[] args = new Object[]{
            "test-arg-1",
            new Integer(100)
    };

    @Before
    public void setUp() throws Exception {
        this.triggerCount = 0;
        this.testFailed = false;
        this.triggered = false;
        this.errors = new StringBuffer();
        this.trigger = new SimpleMethodInvokingTriggerBean();
    }
    
    @After
    public void tearDown() throws Exception {
        this.trigger.stop(true);
    }

    @Test
    public void wrongArgumentsProperAbort() throws Exception {
        
        this.trigger.setStartDelay(0);
        this.trigger.setRepeatInterval(50);
        this.trigger.setTargetObject(this);
        this.trigger.setTargetMethodName("triggerMe");
        this.trigger.setBeanName("triggerBean");
        this.trigger.setTriggerThreadName(this.threadName);
        this.trigger.setStartTriggerAfterInitialization(true);
        this.trigger.setAbortTriggerOnTargetMethodException(false);
        
        this.trigger.setArgumentTypes(new Class[]{String.class, Integer.TYPE});
        this.trigger.setArguments(new Object[]{new Character('x'), new Object()});
        
        try {
            this.trigger.afterPropertiesSet();
            this.trigger.onApplicationEvent((ContextRefreshedEvent)null);  // Starts the trigger
        } catch (BeanInitializationException e) {
            fail("Failed: " + e.getMessage());
        }
        
        try { // Give trigger a chance to do its thing
            Thread.sleep(250);
        } catch (InterruptedException ie) {}
        
        // No triggering should have happened because of wrong arguments
        // and the trigger should be disabled
        assertFalse(this.trigger.isEnabled());
        
        assertFalse(this.triggered);
        
    }
    
    @Test
    public void triggeringTargetMethodThrowsException() throws Exception {
        
        this.trigger.setStartDelay(0);
        this.trigger.setRepeatInterval(50);
        this.trigger.setTargetObject(this);
        this.trigger.setTargetMethodName("triggerMeException");
        this.trigger.setBeanName("triggerBean");
        this.trigger.setTriggerThreadName(this.threadName);
        this.trigger.setStartTriggerAfterInitialization(true);
        this.trigger.setAbortTriggerOnTargetMethodException(false);
        
        try {
            this.trigger.afterPropertiesSet(); // Starts the trigger
            this.trigger.onApplicationEvent((ContextRefreshedEvent)null);  // Starts the trigger
        } catch (BeanInitializationException e) {
            fail("Failed: " + e.getMessage());
        }

        try { // Give trigger a chance to do its thing
            Thread.sleep(250);
        } catch (InterruptedException ie) {}
        this.trigger.stop(true);
        
        // Test that trigger count is at least 2
        assertTrue(this.triggerCount > 1);
        
        this.trigger.setAbortTriggerOnTargetMethodException(true);
        this.triggerCount = 0;
        this.triggered = false;
        
        this.trigger.start();
        try { // Give trigger a chance to do its thing
            Thread.sleep(250);
        } catch (InterruptedException ie) {}
        this.trigger.stop(true);
        
        // Test that we got exactly one triggering
        assertEquals(1, this.triggerCount);
        assertTrue(this.triggered);
    }
    
    @Test
    public void triggering() throws Exception {
        
        this.trigger.setStartDelay(0);
        this.trigger.setRepeatInterval(50);
        this.trigger.setTargetObject(this);
        this.trigger.setTargetMethodName("triggerMe");
        this.trigger.setBeanName("triggerBean");
        this.trigger.setTriggerThreadName(this.threadName);
        this.trigger.setArguments(this.args);
        this.trigger.setArgumentTypes(new Class[]{String.class, Integer.TYPE});
        this.trigger.setStartTriggerAfterInitialization(true);
        
        try {
            this.trigger.afterPropertiesSet(); // Starts the trigger
            this.trigger.onApplicationEvent((ContextRefreshedEvent)null);  // Starts the trigger
        } catch (BeanInitializationException e) {
            fail("Failed: " + e.getMessage());
        }
        
        try { // Give trigger a chance to do its thing
            Thread.sleep(250);
        } catch (InterruptedException ie) {}
        
        assertTrue(this.trigger.isEnabled());
        
        this.trigger.stop(false);
        
        assertFalse(this.trigger.isEnabled());
        
        if (! this.triggered) {
            fail("Never triggered");
        }
        
        if (this.testFailed) {
            fail("Test failed: " + this.errors.toString());
        }
        
        this.triggered = false;
        try { // Test that we don't get any more triggers
            Thread.sleep(250);
        } catch (InterruptedException ie) {}
        
        if (this.triggered) {
            fail("Triggered after being stopped !");
        }
    }

    @Test
    public void repeatCount() {
        this.trigger.setStartDelay(0);
        this.trigger.setRepeatInterval(1);
        this.trigger.setRepeatCount(3);
        this.trigger.setTargetObject(this);
        this.trigger.setTargetMethodName("triggerMe");
        this.trigger.setBeanName("triggerBean");
        this.trigger.setTriggerThreadName(this.threadName);
        this.trigger.setArguments(this.args);
        this.trigger.setArgumentTypes(new Class[]{String.class, Integer.TYPE});
        this.trigger.setStartTriggerAfterInitialization(true);
        
        try {
            this.trigger.afterPropertiesSet(); // Starts the trigger
            this.trigger.onApplicationEvent((ContextRefreshedEvent)null);  // Starts the trigger
        } catch (BeanInitializationException e) {
            fail("Failed: " + e.getMessage());
        }
        
        try { // Give trigger a chance to do its thing
            Thread.sleep(100);
        } catch (InterruptedException ie) {}
        
        assertFalse(this.trigger.isEnabled());
        assertEquals(3, this.triggerCount);
        assertTrue(this.triggered);
        
        
        this.trigger.setRepeatCount(0);
        this.triggerCount = 0;
        this.triggered = false;
        
        this.trigger.start();
        
        try { // Give trigger a chance to do its thing
            Thread.sleep(50);
        } catch (InterruptedException ie) {}
        
        assertFalse(this.trigger.isEnabled());
        assertEquals(0, this.triggerCount);
        assertFalse(this.triggered);
        
    }
    
    public synchronized void triggerMe(String arg1, int arg2) {
        
        this.triggered = true;
        ++this.triggerCount;
        
        if (! this.threadName.equals(Thread.currentThread().getName())) {
            this.testFailed = true;
            this.errors.append("Thread name not set correctly\n");
        }
        
        if (! this.args[0].equals(arg1)) {
            this.testFailed = true;
            this.errors.append("First argument wrong\n");
        }
        
        int arg2Orig = ((Integer)this.args[1]).intValue();
        if (arg2Orig != arg2) {
            this.testFailed = true;
            this.errors.append("Second argument wrong\n");
        }
        
    }
    
    public synchronized void triggerMeException () {
        this.triggered = true;
        ++this.triggerCount;
        
        throw new RuntimeException("Runtime exception from target method");
        
    }
    
}
