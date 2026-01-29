/**
 * Client-side logging utility for sending diagnostic logs to the server.
 * Logs are sent to POST /api/logs endpoint.
 * Format matches LoggingEntry.java: { date, time, message, logLevel }
 */

const LogLevel = {
    INFO: 'INFO',
    DEBUG: 'DEBUG',
    WARN: 'WARN',
    ERROR: 'ERROR'
};

class Logger {
    constructor() {
        this.queue = [];
        this.isProcessing = false;
        this.batchSize = 5; // Send logs in batches
        this.batchTimeout = 5000; // Wait max 5 seconds before sending
        this.batchTimer = null;
    }

    /**
     * Log a message with the specified level
     * @param {string} message - The log message
     * @param {string} level - LogLevel (INFO, DEBUG, WARN, ERROR)
     * @param {any} context - Optional context object to include in message
     */
    log(message, level = LogLevel.INFO, context = null) {
        const fullMessage = context ? `${message} | ${JSON.stringify(context)}` : message;
        
        // Also log to console for development visibility
        console[level.toLowerCase()] !== undefined 
            ? console[level.toLowerCase()](`[APP] ${fullMessage}`)
            : console.log(`[${level}] ${fullMessage}`);
        
        this.queue.push({
            message: fullMessage,
            logLevel: level
        });

        // Flush if batch is full, otherwise set timeout
        if (this.queue.length >= this.batchSize) {
            this.flush();
        } else {
            this.resetBatchTimer();
        }
    }

    info(message, context = null) {
        this.log(message, LogLevel.INFO, context);
    }

    debug(message, context = null) {
        this.log(message, LogLevel.DEBUG, context);
    }

    warn(message, context = null) {
        this.log(message, LogLevel.WARN, context);
    }

    error(message, context = null) {
        this.log(message, LogLevel.ERROR, context);
    }

    /**
     * Flush pending logs to server
     */
    async flush() {
        if (this.queue.length === 0 || this.isProcessing) {
            return;
        }

        this.isProcessing = true;
        clearTimeout(this.batchTimer);

        const logsToSend = [...this.queue];
        this.queue = [];

        try {
            const now = new Date();
            const entries = logsToSend.map(log => ({
                date: now.toISOString().split('T')[0], // YYYY-MM-DD
                time: now.getHours().toString().padStart(2, '0') + ':' +
                      now.getMinutes().toString().padStart(2, '0') + ':' +
                      now.getSeconds().toString().padStart(2, '0') + '.' +
                      now.getMilliseconds().toString().padStart(3, '0'), // HH:MM:SS.sss
                message: log.message,
                logLevel: log.logLevel
            }));

            const response = await fetch('/api/logs/batch', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(entries)
            });

            if (!response.ok) {
                console.error(`Failed to send logs: HTTP ${response.status}`);
                // Re-queue logs if send failed
                this.queue.unshift(...logsToSend);
            }
        } catch (err) {
            console.error('Error sending logs:', err);
            // Re-queue logs if network error
            this.queue.unshift(...logsToSend);
        } finally {
            this.isProcessing = false;
        }
    }

    /**
     * Reset the batch timer
     */
    resetBatchTimer() {
        clearTimeout(this.batchTimer);
        this.batchTimer = setTimeout(() => {
            this.flush();
        }, this.batchTimeout);
    }

    /**
     * Ensure logs are sent before page unload
     */
    flushOnUnload() {
        window.addEventListener('beforeunload', () => {
            if (this.queue.length > 0) {
                // Use sendBeacon for reliable delivery on page unload
                const now = new Date();
                const entries = this.queue.map(log => ({
                    date: now.toISOString().split('T')[0],
                    time: now.getHours().toString().padStart(2, '0') + ':' +
                          now.getMinutes().toString().padStart(2, '0') + ':' +
                          now.getSeconds().toString().padStart(2, '0') + '.' +
                          now.getMilliseconds().toString().padStart(3, '0'), // HH:MM:SS.sss
                    message: log.message,
                    logLevel: log.logLevel
                }));
                
                navigator.sendBeacon('/api/logs/batch', JSON.stringify(entries));
            }
        });
    }
}

// Global logger instance
window.appLogger = new Logger();
window.appLogger.flushOnUnload();

// Log when the logger is initialized
window.appLogger.info('Logger initialized');
