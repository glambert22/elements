/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.jobs;


import com.j256.simplejmx.common.JmxAttributeMethod;
import com.j256.simplejmx.common.JmxOperation;
import com.j256.simplejmx.common.JmxResource;
import net.e6tech.elements.common.launch.LaunchListener;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.util.SystemException;
import org.quartz.*;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.triggers.CronTriggerImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by futeh.
 */
@JmxResource(description = "Job", domainName = "Jobs")
public class Job implements Initializable, Startable, LaunchListener {

    private static Logger logger = Logger.getLogger();

    private JobServer jobServer;
    private Scheduler scheduler;
    private String name;
    private String group = "default";
    private String cronExpression;
    private int priority = 5;
    private TimeZone timezone = TimeZone.getDefault();
    private long initialDelay = 0;
    private boolean concurrent = false;
    private Method invocation;
    private Object target;
    private String targetMethod;

    public JobServer getJobServer() {
        return jobServer;
    }

    public void setJobServer(JobServer jobServer) {
        this.jobServer = jobServer;
    }

    @JmxOperation
    public void stop() throws SchedulerException {
        scheduler.deleteJob(new JobKey(name, group));
    }

    @JmxOperation
    public void resume() throws SchedulerException {
        if (scheduler.getJobDetail(new JobKey(name, group))!= null) {
            // job is still scheduled so that we just make sure the trigger is still running.
            CronTriggerImpl trigger = (CronTriggerImpl) scheduler.getTrigger(new TriggerKey(name, group));
            if (trigger != null) {
                scheduler.resumeTrigger(new TriggerKey(name, group));
            }
        } else {
            JobDetail jobDetail = newJobDetail();
            CronTriggerImpl trigger = (CronTriggerImpl) scheduler.getTrigger(new TriggerKey(name, group));
            try {
                if (trigger != null) {
                    updateTrigger(trigger);
                } else {
                    trigger = newCronTrigger();
                }
            } catch (ParseException ex) {
                throw new SchedulerException(ex);
            }
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    @JmxOperation
    public void reschedule(String cronExpression) throws SchedulerException {
        setCronExpression(cronExpression);
        stop();
        resume();
    }

    @JmxAttributeMethod
    public String getNextFireTime() {
        try {
            Trigger trigger = scheduler.getTrigger(new TriggerKey(name, group));
            return trigger.getNextFireTime().toString();
        } catch (SchedulerException e) {
            Logger.suppress(e);
        } catch (Exception th) {
            Logger.suppress(th);
        }
        return "NA";
    }

    @JmxAttributeMethod
    public boolean isRunning() {
        try {
            return scheduler.getJobDetail(new JobKey(name, group)) != null;
        } catch (SchedulerException e) {
            Logger.suppress(e);
        } catch (Exception th) {
            Logger.suppress(th);
        }
        return false;
    }

    private void init() {
        try {
            if (target instanceof  Class) {
                target = ((Class) target).newInstance();
            }
            invocation = target.getClass().getMethod(targetMethod);
        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }

    @Override
    public void initialize(Resources resources) {
        // do nothing
    }

    public void start() {
        try {
            logger.info("Scheduled job=" + getName());
            init();
            JobDetail jobDetail = newJobDetail();
            CronTrigger trigger = newCronTrigger();
            if (jobServer != null) {
                scheduler = jobServer.getScheduler();
            } else {
                if (scheduler == null) {
                    StdSchedulerFactory factory = new StdSchedulerFactory();
                    scheduler = factory.getScheduler();
                    scheduler.start();
                }
            }
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
    }

    @Override
    public void launched(Provision provision) {
        // do nothing
    }

    protected JobDetail newJobDetail() {
        Class jobClass = (this.concurrent ? ConcurrentRunner.class : NonConcurrentRunner.class);
        JobDetailImpl jobDetail = new JobDetailImpl();
        jobDetail.setName(name);
        jobDetail.setGroup(group);
        jobDetail.setJobClass(jobClass);
        jobDetail.getJobDataMap().put("job", this);
        jobDetail.setDurability(true);
        return jobDetail;
    }

    protected CronTriggerImpl newCronTrigger() throws ParseException {
        CronTriggerImpl trigger = new CronTriggerImpl();
        updateTrigger(trigger);
        return trigger;
    }

    protected CronTrigger updateTrigger(CronTriggerImpl trigger) throws ParseException {

        trigger.setName(name);
        trigger.setGroup(group);
        trigger.setCronExpression(cronExpression);
        trigger.setPriority(priority);
        trigger.setTimeZone(timezone);
        trigger.setStartTime(new Date(System.currentTimeMillis() + initialDelay));
        return trigger;
    }

    @SuppressWarnings("squid:S00112")
    public Object execute() throws Throwable {
        try {
            // this call is executed using a different thread so that we need to set up
            // logging context.
            if (jobServer != null &&
                    jobServer.resourceManager != null) {
                jobServer.resourceManager.createLoggerContext();
            }
            return invocation.invoke(target);
        } catch (InvocationTargetException ex) {
            Logger.suppress(ex);
            throw ex.getTargetException();
        }
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @JmxAttributeMethod
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JmxAttributeMethod
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @JmxAttributeMethod
    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    @JmxAttributeMethod
    public int getPriority() {
        return priority;
    }

    @JmxAttributeMethod
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public TimeZone getTimezone() {
        return timezone;
    }

    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    @JmxAttributeMethod
    public long getInitialDelay() {
        return initialDelay;
    }

    @JmxAttributeMethod
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    @JmxAttributeMethod
    public boolean isConcurrent() {
        return concurrent;
    }

    @JmxAttributeMethod
    public void setConcurrent(boolean concurrent) {
        this.concurrent = concurrent;
    }

    public Method getInvocation() {
        return invocation;
    }

    public void setInvocation(Method invocation) {
        this.invocation = invocation;
    }

    public Object getTarget() {
        return target;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    public static class ConcurrentRunner implements org.quartz.Job {
        Job job;

        public void setJob(Job job) {
            this.job = job;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                context.setResult(job.execute());
            } catch (Throwable e) {
                throw new JobExecutionException(e);
            }
        }
    }

    @PersistJobDataAfterExecution
    @DisallowConcurrentExecution
    public static class NonConcurrentRunner extends ConcurrentRunner {
    }
}
