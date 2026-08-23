package com.example.springboot.homeassistant.jobs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.springboot.homeassistant.automations.BathroomOccupancyAutomation;
import com.example.springboot.homeassistant.services.DelayedActionService;
import com.example.springboot.homeassistant.services.LightBrightnessService;

class DelayedLightOffJobTest {

    private static final String LIGHT_ENTITY_ID = "light.gledopto_light";

    @Test
    void skipsTurnOffWhenBathroomManualLeaseIsActive() throws Exception {
        DelayedLightOffJob job = new DelayedLightOffJob();
        LightBrightnessService lightBrightnessService = mock(LightBrightnessService.class);
        DelayedActionService delayedActionService = mock(DelayedActionService.class);
        BathroomOccupancyAutomation bathroomOccupancyAutomation = mock(BathroomOccupancyAutomation.class);

        ReflectionTestUtils.setField(job, "lightBrightnessService", lightBrightnessService);
        ReflectionTestUtils.setField(job, "delayedActionService", delayedActionService);
        ReflectionTestUtils.setField(job, "bathroomOccupancyAutomation", bathroomOccupancyAutomation);

        when(bathroomOccupancyAutomation.isManualLeaseActiveForLight(LIGHT_ENTITY_ID)).thenReturn(true);

        JobDataMap dataMap = new JobDataMap();
        dataMap.put(DelayedLightOffJob.LIGHT_ENTITY_ID_KEY, LIGHT_ENTITY_ID);
        dataMap.put(DelayedLightOffJob.PHASE_KEY, DelayedLightOffJob.REQUEST_PHASE);
        dataMap.put(DelayedLightOffJob.ATTEMPT_KEY, 0);
        dataMap.put(DelayedLightOffJob.ROOT_SCHEDULED_AT_EPOCH_MS_KEY, System.currentTimeMillis());

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);
        when(context.getMergedJobDataMap()).thenReturn(dataMap);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("test-action", "delayed-actions"));

        job.execute(context);

        verify(bathroomOccupancyAutomation).isManualLeaseActiveForLight(LIGHT_ENTITY_ID);
        verifyNoInteractions(lightBrightnessService);
        verifyNoInteractions(delayedActionService);
    }
}
