/**
 * Shared chart utilities for displaying Chart.js visualizations
 */

/**
 * Displays a Chart.js line chart in the specified container
 * @param {Object} config - Chart configuration object
 * @param {string} config.containerId - ID of the container element where chart will be rendered
 * @param {Array} config.data - Array of data objects to visualize
 * @param {Object} config.datasets - Dataset configuration array
 * @param {Function} config.prepareLabels - Function to extract labels from data
 * @param {string} config.emptyMessage - Message to display when no data available
 * @param {Object} config.chartInstance - Reference to existing chart instance to destroy
 * @param {Object} [config.scales] - Custom scales configuration
 * @returns {Chart|null} - The created Chart.js instance or null if no data
 */
function displayChart(config) {
    try {
        const {
            containerId,
            data,
            datasets,
            prepareLabels,
            emptyMessage = 'No data available',
            chartInstance,
            scales = {}
        } = config;

        if (!data || data.length === 0) {
            window.appLogger?.info(`No data available for chart in ${containerId}`);
            document.getElementById(containerId).innerHTML = `<p style="text-align: center; color: #666;">${emptyMessage}</p>`;
            return null;
        }

        // Prepare labels
        const labels = prepareLabels(data);

        // Log chart rendering details
        if (window.appLogger) {
            const allValues = datasets.flatMap(ds => ds.prepareData(data));
            window.appLogger.debug('Displaying chart', {
                container: containerId,
                dataPoints: labels.length,
                datasets: datasets.length,
                minValue: Math.min(...allValues),
                maxValue: Math.max(...allValues)
            });
        }

        // Create canvas
        const canvasHtml = '<canvas id="chart"></canvas>';
        document.getElementById(containerId).innerHTML = canvasHtml;

        const ctx = document.getElementById('chart').getContext('2d');

        // Destroy existing chart if provided
        if (chartInstance) {
            window.appLogger?.debug('Destroying existing chart instance');
            chartInstance.destroy();
        }

        // Build datasets for Chart.js
        const chartDatasets = datasets.map(ds => ({
            label: ds.label,
            data: ds.prepareData(data),
            borderColor: ds.borderColor,
            backgroundColor: ds.backgroundColor,
            tension: ds.tension || 0.4,
            fill: ds.fill !== undefined ? ds.fill : true,
            borderWidth: ds.borderWidth || 2,
            pointRadius: ds.pointRadius || 4,
            pointBackgroundColor: ds.pointBackgroundColor || ds.borderColor,
            pointBorderColor: ds.pointBorderColor || '#fff',
            pointBorderWidth: ds.pointBorderWidth || 2,
            yAxisID: ds.yAxisID
        }));

        // Merge default scales with custom scales
        const defaultScales = {
            y: {
                beginAtZero: true,
                ticks: {
                    font: {
                        size: window.innerWidth <= 480 ? 10 : 12
                    }
                }
            },
            x: {
                ticks: {
                    font: {
                        size: window.innerWidth <= 480 ? 9 : 12
                    },
                    maxRotation: window.innerWidth <= 480 ? 45 : 0,
                    minRotation: window.innerWidth <= 480 ? 45 : 0
                }
            }
        };

        const mergedScales = { ...defaultScales, ...scales };

        // Create chart
        const chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: chartDatasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                        labels: {
                            font: {
                                size: window.innerWidth <= 480 ? 11 : 14
                            },
                            padding: window.innerWidth <= 480 ? 10 : 20,
                            usePointStyle: true
                        }
                    }
                },
                scales: mergedScales
            }
        });

        window.appLogger?.info('Chart rendered successfully');
        return chart;

    } catch (error) {
        window.appLogger?.error('Error displaying chart', { error: error.message });
        return null;
    }
}
