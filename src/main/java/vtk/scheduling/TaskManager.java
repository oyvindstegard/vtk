/* Copyright (c) 2012, University of Oslo, Norway
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

package vtk.scheduling;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Simple management of system tasks.
 */
public class TaskManager implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private TaskScheduler scheduler;
    
    private ApplicationContext applicationContext;
    
    private final Map<String,TaskHolder> tasks = new HashMap<String,TaskHolder>();
    
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    private static final class TaskHolder {
        final Task task;
        ScheduledFuture future;
        TaskHolder(Task task) {
            this.task = task;
        }
    }
    
    /**
     * @see ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent) 
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Register and schedule all tasks found in bean context initially
        for (Task task: getContextTasks()) {
            scheduleTask(task);
        }
    }
    
    private Set<Task> getContextTasks() {
        Map<String, Task> tasksInContext
                = BeanFactoryUtils.beansOfTypeIncludingAncestors(this.applicationContext,
                        Task.class, false, false);
        
        HashSet<Task> contextTasks = new HashSet<Task>();
        for (Map.Entry<String, Task> entry : tasksInContext.entrySet()) {
            Task task = entry.getValue();
            contextTasks.add(task);
        }
        
        return contextTasks;
    }

    /**
     * Check whether the task has status "done", meaning that its current
     * trigger does not specify any more future invocations and the task
     * is currently not executing. The call is actually delegated directly
     * to the task's {@link ScheduledFuture#isDone() } method provided by
     * the configured {@link TaskScheduler}.
     * 
     * @param taskId the id of the task
     * @return <code>true</code> if the task is done, according to above
     * description, <code>false</code> otherwise.
     * @see ScheduledFuture#isDone() 
     * @throws IllegalArgumentException if no such task id exists in this task manager
     */
    public synchronized boolean isDone(String taskId) {
        TaskHolder th = tasks.get(taskId);
        if (th == null) {
            throw new IllegalArgumentException("No such task: " + taskId);
        }
        if (th.future == null) {
            return true;
        }
        return th.future.isDone();
    }
    
    /**
     * Get a registered task instance by id.
     * 
     * @param taskId the task id
     * @return a <code>Task</code> instance
     * @throws IllegalArgumentException if no such task id exists in this task manager
     */
    public synchronized Task getTask(String taskId) {
        TaskHolder th = tasks.get(taskId);
        if (th == null) {
            throw new IllegalArgumentException("No such task: " + taskId);
        }
        return th.task;
    }
    
    /**
     * Get a set of task ids for all currently scheduled tasks.
     * @return a set of task ids for all currently scheduled tasks.
     */
    public synchronized Set<String> getTaskIds() {
        return Collections.unmodifiableSet(new HashSet<String>(this.tasks.keySet()));
    }
    
    /**
     * Unschedule and remove a task from this task manager. The task will
     * be cancelled with thread interruption before being removed.
     *
     * @param taskId the task id to remove
     * @return the removed task instance
     * @throws IllegalArgumentException if no such task id exists in this task manager
     */
    public synchronized Task removeTask(String taskId) {
        TaskHolder th = tasks.get(taskId);
        if (th == null) {
            throw new IllegalArgumentException("No such task: " + taskId);
        }

        if (th.future != null) {
            cancel(th);
        }
        tasks.remove(taskId);
        return th.task;
    }
    
    /**
     * Schedule the task in this task manager. The task will replace any existing
     * task with the same {@link Task#getId() id}. If such a replacement
     * occurs, the existing task will be cancelled before being
     * removed.
     * 
     * <p>The added task will be scheduled for invocation according to its trigger
     * specification.
     * 
     * @param task the task instance to add
     * @return the old task instance with the same id as the newly added, or
     * <code>null</code> if no replacement occured.
     */
    public synchronized Task scheduleTask(Task task) {
        if (task.getId() == null) {
            throw new IllegalArgumentException("Task id cannot be null. Set id on Task before scheduling.");
        }
        
        final TaskHolder newTask = new TaskHolder(task);
        final TaskHolder oldTask = tasks.put(task.getId(), newTask);
        if (oldTask != null) {
            logger.warn("Replacing an existing task instance with id " + task.getId() 
                    + " [" + oldTask.task.getClass() + "@" + System.identityHashCode(oldTask.task) + "]");
            if (oldTask.future != null) {
                cancel(oldTask);
            }
        }

        schedule(newTask);

        if (oldTask != null) {
            return oldTask.task;
        }
        return null;
    }

    private boolean cancel(TaskHolder th) {
        if (th.future == null) {
            logger.warn("Task " + th.task.getId() + " was not scheduled; nothing to cancel.");
            return false;
        }

        logger.info("Cancelling scheduling for task with id " + th.task.getId() + " (thread will be interrupted)");
        th.future.cancel(true);
        th.future = null;
        return true;
    }
    
    private void schedule(TaskHolder th) {
        if (th.future != null) {
            logger.warn("Task " + th.task.getId() + " already scheduled, cancelling existing schedule and re-scheduling.");
            cancel(th);
        }
        
        logger.info("Scheduling task " + th.task.getId() + " with trigger " + th.task.getTriggerSpecification());
        th.future = scheduler.schedule(th.task, getTriggerImpl(th.task.getTriggerSpecification()));
        if (th.task.getTriggerSpecification() == null) {
            logger.warn("Task " + th.task.getId() + " provided a null trigger and will not be invoked.");
        }
    }
    
    // private factory method for creating Spring Trigger impls.
    private Trigger getTriggerImpl(TriggerSpecification spec) {
        if (spec instanceof PeriodicTriggerSpecification) {
            PeriodicTriggerSpecification s = (PeriodicTriggerSpecification)spec;
            
            PeriodicTrigger p = new PeriodicTrigger(s.getSeconds(), TimeUnit.SECONDS);
            p.setFixedRate(s.isFixedRate());
            if (s.getInitialDelaySeconds() > 0) {
                p.setInitialDelay(s.getInitialDelaySeconds());
            }
            return p;
        }
        
        if (spec instanceof CronTriggerSpecification) {
            CronTriggerSpecification s = (CronTriggerSpecification)spec;
            return new CronTrigger(s.getExpression());
        }
        
        if (spec instanceof OneShotTriggerSpecification) {
            final int delay = ((OneShotTriggerSpecification)spec).getDelaySeconds();
            return new Trigger() {
                @Override
                public Date nextExecutionTime(TriggerContext triggerContext) {
                    if (triggerContext.lastScheduledExecutionTime() != null) return null;
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.SECOND, delay);
                    return c.getTime();
                }
            };
        }
        
        if (spec == null) {
            return new Trigger() {
                @Override
                public Date nextExecutionTime(TriggerContext triggerContext) {
                    return null;
                }
            };
        }
        
        throw new IllegalArgumentException("Unknown trigger specification: " + spec);
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Required
    public void setTaskScheduler(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

}
