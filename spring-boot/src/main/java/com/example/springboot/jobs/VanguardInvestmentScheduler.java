package com.example.springboot.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.springboot.logging.BufferedBackendFileLogger;
import com.example.springboot.models.LogLevel;
import com.example.springboot.services.VanguardInvestmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class VanguardInvestmentScheduler {

    private final VanguardInvestmentService vanguardInvestmentService;
    private final BufferedBackendFileLogger backendFileLogger;

    @Scheduled(cron = "${investments.vanguard.daily-cron:0 0 18 * * *}")
    public void runDailyImport() {
        var result = vanguardInvestmentService.importDaily();
        backendFileLogger.log(
            VanguardInvestmentScheduler.class,
            LogLevel.INFO,
            "scheduled-job",
            "Scheduled job running: vanguard-daily-import fundsProcessed=" + result.fundsProcessed()
                + ", inserted=" + result.rowsInserted()
                + ", updated=" + result.rowsUpdated()
        );
        log.info(
            "Daily Vanguard import complete for {} funds: {} inserted, {} updated",
            result.fundsProcessed(),
            result.rowsInserted(),
            result.rowsUpdated()
        );
    }
}
