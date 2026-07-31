package com.example.springboot.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.springboot.services.VanguardInvestmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class VanguardInvestmentScheduler {

    private final VanguardInvestmentService vanguardInvestmentService;

    @Scheduled(cron = "${investments.vanguard.daily-cron:0 0 18 * * *}")
    public void runDailyImport() {
        var result = vanguardInvestmentService.importDaily();
        log.info(
            "Daily Vanguard import complete for {} funds: {} inserted, {} updated",
            result.fundsProcessed(),
            result.rowsInserted(),
            result.rowsUpdated()
        );
    }
}
