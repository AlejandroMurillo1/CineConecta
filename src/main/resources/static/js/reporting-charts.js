/*
 * Gráficas de los informes (Chart.js). Leen los datos de las filas de la tabla del informe
 * (atributos data-*), de modo que la tabla y la gráfica siempre muestran lo mismo.
 */
(function () {
    'use strict';

    var canvas = document.getElementById('rp-chart');
    if (!canvas || typeof Chart === 'undefined') {
        return;
    }

    var rows = Array.prototype.slice.call(document.querySelectorAll('tr[data-plan]'));
    if (!rows.length) {
        return;
    }

    var PALETTE = ['#e11d48', '#38bdf8', '#10b981', '#f59e0b', '#a78bfa'];
    var TEXT = '#f8fafc';
    var MUTED = '#94a3b8';
    var GRID = 'rgba(148, 163, 184, 0.18)';
    var TOOLTIP = {backgroundColor: '#0f172a', borderColor: '#334155', borderWidth: 1};

    Chart.defaults.color = MUTED;
    Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';

    var money = new Intl.NumberFormat('es-CO', {maximumFractionDigits: 0});
    var decimal = new Intl.NumberFormat('es-CO', {minimumFractionDigits: 2, maximumFractionDigits: 2});

    function unique(values) {
        return values.filter(function (v, i) {
            return values.indexOf(v) === i;
        });
    }

    function findRow(plan, type) {
        return rows.filter(function (r) {
            return r.dataset.plan === plan && r.dataset.type === type;
        })[0];
    }

    var plans = unique(rows.map(function (r) { return r.dataset.plan; }));
    var xAxis = {grid: {display: false}, ticks: {color: TEXT}};

    function render(config) {
        config.type = 'bar';
        config.options.responsive = true;
        config.options.maintainAspectRatio = false;
        config.options.interaction = {mode: 'index', intersect: false};
        return new Chart(canvas, config);
    }

    if (canvas.dataset.report === 'revenue') {
        var revenues = rows.map(function (r) { return Number(r.dataset.revenue); });
        var counts = rows.map(function (r) { return Number(r.dataset.count); });
        var sum = function (values) {
            return values.reduce(function (a, b) { return a + b; }, 0);
        };
        var metrics = {
            revenue: {
                label: 'Total recaudado',
                color: PALETTE[0],
                values: revenues,
                total: sum(revenues),
                format: function (v) { return '$' + money.format(v) + ' COP'; },
                tick: function (v) { return '$' + money.format(v); }
            },
            count: {
                label: 'Suscripciones adquiridas',
                color: PALETTE[1],
                values: counts,
                total: sum(counts),
                format: function (v) { return v + (v === 1 ? ' suscripción' : ' suscripciones'); },
                tick: function (v) { return v; }
            }
        };
        // Con muchos planes las etiquetas se amontonan: barras horizontales.
        var horizontal = plans.length > 6;
        var valueAxis = horizontal ? 'x' : 'y';
        var categoryAxis = horizontal ? 'y' : 'x';
        var current = 'revenue';

        var scales = {};
        scales[categoryAxis] = xAxis;
        scales[valueAxis] = {
            beginAtZero: true,
            grid: {color: GRID},
            ticks: {precision: 0, callback: metrics.revenue.tick}
        };

        var chart = render({
            data: {
                labels: plans,
                datasets: [{
                    label: metrics.revenue.label,
                    data: metrics.revenue.values,
                    backgroundColor: metrics.revenue.color,
                    borderRadius: 6
                }]
            },
            options: {
                indexAxis: horizontal ? 'y' : 'x',
                plugins: {
                    legend: {display: false},
                    tooltip: Object.assign({
                        callbacks: {
                            label: function (ctx) {
                                var m = metrics[current];
                                var other = metrics[current === 'revenue' ? 'count' : 'revenue'];
                                var v = ctx.parsed[valueAxis];
                                var share = m.total > 0 ? ' (' + decimal.format(v * 100 / m.total) + ' % del total)' : '';
                                return [
                                    m.label + ': ' + m.format(v) + share,
                                    other.label + ': ' + other.format(other.values[ctx.dataIndex])
                                ];
                            }
                        }
                    }, TOOLTIP)
                },
                scales: scales
            }
        });

        var buttons = document.querySelectorAll('[data-metric]');
        Array.prototype.forEach.call(buttons, function (button) {
            button.addEventListener('click', function () {
                current = button.dataset.metric;
                var m = metrics[current];
                var dataset = chart.data.datasets[0];
                dataset.label = m.label;
                dataset.data = m.values;
                dataset.backgroundColor = m.color;
                chart.options.scales[valueAxis].ticks.callback = m.tick;
                chart.update();
                Array.prototype.forEach.call(buttons, function (b) {
                    b.setAttribute('aria-pressed', String(b === button));
                });
            });
        });
    } else if (canvas.dataset.report === 'consumption') {
        var types = unique(rows.map(function (r) { return r.dataset.type; }));
        render({
            data: {
                labels: plans,
                datasets: types.map(function (type, i) {
                    return {
                        label: type,
                        backgroundColor: PALETTE[i % PALETTE.length],
                        borderRadius: 6,
                        data: plans.map(function (plan) {
                            var row = findRow(plan, type);
                            return row ? Number(row.dataset.minutes) : 0;
                        }),
                        profiles: plans.map(function (plan) {
                            var row = findRow(plan, type);
                            return row ? Number(row.dataset.profiles) : 0;
                        })
                    };
                })
            },
            options: {
                plugins: {
                    legend: {labels: {color: TEXT}},
                    tooltip: Object.assign({
                        callbacks: {
                            label: function (ctx) {
                                var profiles = ctx.dataset.profiles[ctx.dataIndex];
                                return ctx.dataset.label + ': ' + decimal.format(ctx.parsed.y)
                                    + ' min (' + profiles + (profiles === 1 ? ' perfil)' : ' perfiles)');
                            }
                        }
                    }, TOOLTIP)
                },
                scales: {
                    x: xAxis,
                    y: {
                        beginAtZero: true,
                        grid: {color: GRID},
                        title: {display: true, text: 'Minutos consumidos'}
                    }
                }
            }
        });
    }
})();
