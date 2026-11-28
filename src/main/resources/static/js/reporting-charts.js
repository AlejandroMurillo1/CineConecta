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
        new Chart(canvas, config);
    }

    if (canvas.dataset.report === 'revenue') {
        render({
            data: {
                labels: plans,
                datasets: [
                    {
                        label: 'Total recaudado',
                        data: rows.map(function (r) { return Number(r.dataset.revenue); }),
                        backgroundColor: PALETTE[0],
                        borderRadius: 6,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Suscripciones adquiridas',
                        data: rows.map(function (r) { return Number(r.dataset.count); }),
                        backgroundColor: PALETTE[1],
                        borderRadius: 6,
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                plugins: {
                    legend: {labels: {color: TEXT}},
                    tooltip: Object.assign({
                        callbacks: {
                            label: function (ctx) {
                                return ctx.dataset.yAxisID === 'y'
                                    ? ctx.dataset.label + ': $' + money.format(ctx.parsed.y) + ' COP'
                                    : ctx.dataset.label + ': ' + ctx.parsed.y;
                            }
                        }
                    }, TOOLTIP)
                },
                scales: {
                    x: xAxis,
                    y: {
                        beginAtZero: true,
                        grid: {color: GRID},
                        title: {display: true, text: 'COP'},
                        ticks: {callback: function (v) { return '$' + money.format(v); }}
                    },
                    y1: {
                        beginAtZero: true,
                        position: 'right',
                        grid: {drawOnChartArea: false},
                        title: {display: true, text: 'Suscripciones'},
                        ticks: {precision: 0}
                    }
                }
            }
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
