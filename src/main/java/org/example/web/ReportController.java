package org.example.web;

import org.example.domain.LeaseApplication;
import org.example.domain.LeaseContract;
import org.example.domain.ActualPayment;
import org.example.repo.LeaseApplicationRepository;
import org.example.repo.LeaseContractRepository;
import org.example.repo.ActualPaymentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final LeaseApplicationRepository appRepo;
    private final LeaseContractRepository contractRepo;
    private final ActualPaymentRepository paymentRepo;

    public ReportController(LeaseApplicationRepository appRepo,
                            LeaseContractRepository contractRepo,
                            ActualPaymentRepository paymentRepo) {
        this.appRepo = appRepo;
        this.contractRepo = contractRepo;
        this.paymentRepo = paymentRepo;
    }

    public enum Period {
        DAY, MONTH, QUARTER, YEAR
    }

    // ====== page ======
    @GetMapping
    public String dashboard(
            @RequestParam(value = "period", required = false) Period period,
            Model model
    ) {
        if (period == null) period = Period.MONTH;

        model.addAttribute("active", "reports"); // подсветка меню
        model.addAttribute("period", period.name()); // нужно для кнопок/JS
        return "reports/dashboard";
    }

    // ====== data ======
    @GetMapping("/dashboard-data")
    @ResponseBody
    public Map<String, Object> dashboardData(
            @RequestParam(value = "period", required = false) Period period
    ) {
        if (period == null) period = Period.MONTH;

        // --- KPI ---
        long appsTotal = appRepo.count();
        long contractsTotal = contractRepo.count();

        long appsNew = countAppsByStatus("NEW");
        long appsApproved = countAppsByStatus("APPROVED");
        long appsRejected = countAppsByStatus("REJECTED");

        BigDecimal paidTotal = sumAllPayments();

        Map<String, Object> kpi = new HashMap<String, Object>();
        kpi.put("appsTotal", appsTotal);
        kpi.put("appsNew", appsNew);
        kpi.put("appsApproved", appsApproved);
        kpi.put("appsRejected", appsRejected);
        kpi.put("contractsTotal", contractsTotal);
        kpi.put("paidTotal", paidTotal);

        // --- series ---
        int window = defaultWindow(period);

        LinkedHashMap<String, Integer> appsSeriesRaw = buildAppsSeries(period);
        LinkedHashMap<String, BigDecimal> paidSeriesRaw = buildPaidSeries(period);

        // заполним окнами (последние N периодов) чтобы график не "ломался"
        LinkedHashMap<String, Integer> appsSeriesFilled = fillLastPeriodsInt(appsSeriesRaw, period, window);
        LinkedHashMap<String, BigDecimal> paidSeriesFilled = fillLastPeriodsMoney(paidSeriesRaw, period, window);

        // в JSON
        Map<String, Object> appsSeries = toSeriesInt(appsSeriesFilled);
        Map<String, Object> paidSeries = toSeriesMoney(paidSeriesFilled);

        Map<String, Object> res = new HashMap<String, Object>();
        res.put("period", period.name());
        res.put("kpi", kpi);

        // новые ключи (рекомендую использовать их)
        res.put("appsSeries", appsSeries);
        res.put("paidSeries", paidSeries);

        // совместимость со старым фронтом (если у тебя там уже "appsByMonth"/"paidByMonth")
        res.put("appsByMonth", appsSeries);
        res.put("paidByMonth", paidSeries);

        return res;
    }

    // ====== helpers (KPI) ======

    private long countAppsByStatus(String statusName) {
        Iterable<LeaseApplication> apps = appRepo.findAll();
        long cnt = 0;
        for (LeaseApplication a : apps) {
            if (a.getStatus() != null && statusName.equals(a.getStatus().name())) {
                cnt++;
            }
        }
        return cnt;
    }

    private BigDecimal sumAllPayments() {
        Iterable<ActualPayment> payments = paymentRepo.findAll();
        BigDecimal sum = BigDecimal.ZERO;
        for (ActualPayment p : payments) {
            if (p.getAmount() != null) sum = sum.add(p.getAmount());
        }
        return sum;
    }

    // ====== helpers (series builders) ======

    private LinkedHashMap<String, Integer> buildAppsSeries(Period period) {
        Iterable<LeaseApplication> apps = appRepo.findAll();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>();

        for (LeaseApplication a : apps) {
            LocalDate d = a.getCreatedDate(); // <-- ты это поле уже используешь
            if (d == null) continue;

            String key = bucketKey(d, period);
            Integer cur = map.get(key);
            map.put(key, cur == null ? 1 : cur + 1);
        }

        return sortSeriesInt(map, period);
    }

    private LinkedHashMap<String, BigDecimal> buildPaidSeries(Period period) {
        Iterable<ActualPayment> payments = paymentRepo.findAll();
        LinkedHashMap<String, BigDecimal> map = new LinkedHashMap<String, BigDecimal>();

        for (ActualPayment p : payments) {
            LocalDate d = p.getPaymentDate();
            if (d == null) continue;

            String key = bucketKey(d, period);
            BigDecimal cur = map.get(key);
            if (cur == null) cur = BigDecimal.ZERO;

            BigDecimal add = (p.getAmount() == null) ? BigDecimal.ZERO : p.getAmount();
            map.put(key, cur.add(add));
        }

        return sortSeriesMoney(map, period);
    }

    // ====== bucketing ======

    private String bucketKey(LocalDate d, Period period) {
        int y = d.getYear();
        int m = d.getMonthValue();
        int day = d.getDayOfMonth();

        if (period == Period.DAY) {
            return String.format("%04d-%02d-%02d", y, m, day);
        }
        if (period == Period.MONTH) {
            return String.format("%04d-%02d", y, m);
        }
        if (period == Period.QUARTER) {
            int q = (m - 1) / 3 + 1;
            return String.format("%04d-Q%d", y, q);
        }
        return String.format("%04d", y); // YEAR
    }

    private int defaultWindow(Period period) {
        if (period == Period.DAY) return 30;
        if (period == Period.MONTH) return 12;
        if (period == Period.QUARTER) return 8;
        return 5; // YEAR
    }

    // ====== fill missing buckets ======

    private LinkedHashMap<String, Integer> fillLastPeriodsInt(
            LinkedHashMap<String, Integer> src,
            Period period,
            int window
    ) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();
        LocalDate today = LocalDate.now();

        List<String> keys = lastKeys(today, period, window);
        for (String k : keys) {
            Integer v = src.get(k);
            out.put(k, v == null ? 0 : v);
        }
        return out;
    }

    private LinkedHashMap<String, BigDecimal> fillLastPeriodsMoney(
            LinkedHashMap<String, BigDecimal> src,
            Period period,
            int window
    ) {
        LinkedHashMap<String, BigDecimal> out = new LinkedHashMap<String, BigDecimal>();
        LocalDate today = LocalDate.now();

        List<String> keys = lastKeys(today, period, window);
        for (String k : keys) {
            BigDecimal v = src.get(k);
            out.put(k, v == null ? BigDecimal.ZERO : v);
        }
        return out;
    }

    private List<String> lastKeys(LocalDate today, Period period, int window) {
        List<String> keys = new ArrayList<String>();

        if (period == Period.DAY) {
            for (int i = window - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                keys.add(bucketKey(d, period));
            }
            return keys;
        }

        if (period == Period.MONTH) {
            YearMonth now = YearMonth.of(today.getYear(), today.getMonthValue());
            for (int i = window - 1; i >= 0; i--) {
                YearMonth ym = now.minusMonths(i);
                keys.add(String.format("%04d-%02d", ym.getYear(), ym.getMonthValue()));
            }
            return keys;
        }

        if (period == Period.QUARTER) {
            int y = today.getYear();
            int q = (today.getMonthValue() - 1) / 3 + 1; // текущий квартал
            // генерим window кварталов назад
            for (int i = window - 1; i >= 0; i--) {
                int qq = q;
                int yy = y;

                int back = i;
                while (back > 0) {
                    qq--;
                    if (qq == 0) { qq = 4; yy--; }
                    back--;
                }
                keys.add(String.format("%04d-Q%d", yy, qq));
            }
            return keys;
        }

        // YEAR
        int y = today.getYear();
        for (int i = window - 1; i >= 0; i--) {
            keys.add(String.format("%04d", y - i));
        }
        return keys;
    }

    // ====== sorting ======

    private LinkedHashMap<String, Integer> sortSeriesInt(LinkedHashMap<String, Integer> src, Period period) {
        List<String> keys = new ArrayList<String>(src.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return parseKeyToComparable(a, period).compareTo(parseKeyToComparable(b, period));
            }
        });

        LinkedHashMap<String, Integer> res = new LinkedHashMap<String, Integer>();
        for (String k : keys) res.put(k, src.get(k));
        return res;
    }

    private LinkedHashMap<String, BigDecimal> sortSeriesMoney(LinkedHashMap<String, BigDecimal> src, Period period) {
        List<String> keys = new ArrayList<String>(src.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return parseKeyToComparable(a, period).compareTo(parseKeyToComparable(b, period));
            }
        });

        LinkedHashMap<String, BigDecimal> res = new LinkedHashMap<String, BigDecimal>();
        for (String k : keys) res.put(k, src.get(k));
        return res;
    }

    private ComparableKey parseKeyToComparable(String k, Period period) {
        // DAY: YYYY-MM-DD
        // MONTH: YYYY-MM
        // QUARTER: YYYY-Qn
        // YEAR: YYYY
        if (period == Period.DAY) {
            String[] p = k.split("-");
            return new ComparableKey(
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2])
            );
        }
        if (period == Period.MONTH) {
            String[] p = k.split("-");
            return new ComparableKey(
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    1
            );
        }
        if (period == Period.QUARTER) {
            String[] p = k.split("-Q");
            int y = Integer.parseInt(p[0]);
            int q = Integer.parseInt(p[1]);
            int m = (q - 1) * 3 + 1; // начало квартала как месяц для сортировки
            return new ComparableKey(y, m, 1);
        }
        // YEAR
        return new ComparableKey(Integer.parseInt(k), 1, 1);
    }

    static class ComparableKey implements Comparable<ComparableKey> {
        int y, m, d;
        ComparableKey(int y, int m, int d) { this.y = y; this.m = m; this.d = d; }

        @Override public int compareTo(ComparableKey o) {
            if (y != o.y) return y - o.y;
            if (m != o.m) return m - o.m;
            return d - o.d;
        }
    }

    // ====== toSeries ======

    private Map<String, Object> toSeriesInt(LinkedHashMap<String, Integer> map) {
        List<String> labels = new ArrayList<String>();
        List<Integer> values = new ArrayList<Integer>();

        for (Map.Entry<String, Integer> e : map.entrySet()) {
            labels.add(e.getKey());
            values.add(e.getValue());
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("labels", labels);
        result.put("values", values);
        return result;
    }

    private Map<String, Object> toSeriesMoney(LinkedHashMap<String, BigDecimal> map) {
        List<String> labels = new ArrayList<String>();
        List<BigDecimal> values = new ArrayList<BigDecimal>();

        for (Map.Entry<String, BigDecimal> e : map.entrySet()) {
            labels.add(e.getKey());
            values.add(e.getValue());
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("labels", labels);
        result.put("values", values);
        return result;
    }
}
