package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One backtest, kept whole: the request, its assumptions, the summary, and the full result. */
@Entity
@Table(name = "backtest_run")
public class BacktestRun extends UserOwned {

    @Column(name = "strategy_id")
    private Long strategyId;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "params", nullable = false)
    private String params;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "timeframe", nullable = false, length = 8)
    private String timeframe;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "initial_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialCash;

    @Column(name = "slippage_bps", nullable = false, precision = 8, scale = 2)
    private BigDecimal slippageBps;

    @Column(name = "commission", nullable = false, precision = 19, scale = 4)
    private BigDecimal commission;

    @Column(name = "out_of_sample_fraction", nullable = false, precision = 4, scale = 2)
    private BigDecimal outOfSampleFraction;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "bars", nullable = false)
    private int bars;

    @Column(name = "trades", nullable = false)
    private int trades;

    @Column(name = "day_trades", nullable = false)
    private int dayTrades;

    @Column(name = "total_return_pct", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalReturnPct;

    @Column(name = "benchmark_return_pct", nullable = false, precision = 10, scale = 2)
    private BigDecimal benchmarkReturnPct;

    @Column(name = "max_drawdown_pct", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxDrawdownPct;

    @Column(name = "sharpe", precision = 10, scale = 2)
    private BigDecimal sharpe;

    @Column(name = "final_equity", nullable = false, precision = 19, scale = 4)
    private BigDecimal finalEquity;

    @Column(name = "in_sample_return_pct", precision = 10, scale = 2)
    private BigDecimal inSampleReturnPct;

    @Column(name = "out_of_sample_return_pct", precision = 10, scale = 2)
    private BigDecimal outOfSampleReturnPct;

    /** One per line: the honesty notes, kept where a list can show them without parsing JSON. */
    @Column(name = "warnings")
    private String warnings;

    /** The full result as the Python service returned it. */
    @Column(name = "result", nullable = false)
    private String result;

    protected BacktestRun() {
    }

    public BacktestRun(Long userId, Long strategyId, String kind, String params, Security security,
                       String timeframe, LocalDate startDate, LocalDate endDate, BigDecimal initialCash,
                       BigDecimal slippageBps, BigDecimal commission, BigDecimal outOfSampleFraction) {
        super(userId);
        this.strategyId = strategyId;
        this.kind = kind;
        this.params = params;
        this.security = security;
        this.timeframe = timeframe;
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialCash = initialCash;
        this.slippageBps = slippageBps;
        this.commission = commission;
        this.outOfSampleFraction = outOfSampleFraction;
    }

    public void recordResult(String provider, int bars, int trades, int dayTrades,
                             BigDecimal totalReturnPct, BigDecimal benchmarkReturnPct,
                             BigDecimal maxDrawdownPct, BigDecimal sharpe, BigDecimal finalEquity,
                             BigDecimal inSampleReturnPct, BigDecimal outOfSampleReturnPct,
                             String warnings, String result) {
        this.provider = provider;
        this.bars = bars;
        this.trades = trades;
        this.dayTrades = dayTrades;
        this.totalReturnPct = totalReturnPct;
        this.benchmarkReturnPct = benchmarkReturnPct;
        this.maxDrawdownPct = maxDrawdownPct;
        this.sharpe = sharpe;
        this.finalEquity = finalEquity;
        this.inSampleReturnPct = inSampleReturnPct;
        this.outOfSampleReturnPct = outOfSampleReturnPct;
        this.warnings = warnings;
        this.result = result;
    }

    public Long getStrategyId() { return strategyId; }
    public String getKind() { return kind; }
    public String getParams() { return params; }
    public Security getSecurity() { return security; }
    public String getTimeframe() { return timeframe; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getInitialCash() { return initialCash; }
    public BigDecimal getSlippageBps() { return slippageBps; }
    public BigDecimal getCommission() { return commission; }
    public BigDecimal getOutOfSampleFraction() { return outOfSampleFraction; }
    public String getProvider() { return provider; }
    public int getBars() { return bars; }
    public int getTrades() { return trades; }
    public int getDayTrades() { return dayTrades; }
    public BigDecimal getTotalReturnPct() { return totalReturnPct; }
    public BigDecimal getBenchmarkReturnPct() { return benchmarkReturnPct; }
    public BigDecimal getMaxDrawdownPct() { return maxDrawdownPct; }
    public BigDecimal getSharpe() { return sharpe; }
    public BigDecimal getFinalEquity() { return finalEquity; }
    public BigDecimal getInSampleReturnPct() { return inSampleReturnPct; }
    public BigDecimal getOutOfSampleReturnPct() { return outOfSampleReturnPct; }
    public String getWarnings() { return warnings; }
    public String getResult() { return result; }
}
