package com.example.springboot.config;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public Scheduler scheduler(ApplicationContext applicationContext) throws SchedulerException {
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.setJobFactory(new AutowiringJobFactory(applicationContext.getAutowireCapableBeanFactory()));
        if (!scheduler.isStarted()) {
            scheduler.start();
        }
        return scheduler;
    }

    private static class AutowiringJobFactory implements JobFactory {

        private final AutowireCapableBeanFactory beanFactory;

        AutowiringJobFactory(AutowireCapableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public org.quartz.Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
            org.quartz.Job job = beanFactory.createBean(bundle.getJobDetail().getJobClass());
            return job;
        }
    }
}
