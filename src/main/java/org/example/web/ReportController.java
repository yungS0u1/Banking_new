package org.example.web;

import org.example.domain.LeaseApplication;
import org.example.domain.LeaseContract;
import org.example.domain.ActualPayment;
import org.example.repo.LeaseApplicationRepository;
import org.example.repo.LeaseContractRepository;
import org.example.repo.ActualPaymentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

    // страница дашборда
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("active", "reports"); // если подсветка меню нужна
        return "reports/dashboard";
    }

    // данные для графиков / KPI
    @GetMapping("/dashboard-data")
    @ResponseBody
    public Map<String, Object> dashboardData() {

        // --- KPI ---
        long appsTotal = appRepo.count();
        long contractsTotal = contractRepo.count();

        // статусы заявок (подстрой под свой enum)
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

        // --- Серии для графиков (пример): платежи по месяцам ---
        Map<YearMonth, BigDecimal> paidByMonth = buildPaidByMonth();
        Map<String, Object> paidSeries = toSeries(paidByMonth);

        // --- Серии: количество заявок по месяцам (по createdDate) ---
        Map<YearMonth, BigDecimal> appsByMonth = buildAppsByMonth();
        Map<String, Object> appsSeries = toSeries(appsByMonth);

        Map<String, Object> res = new HashMap<String, Object>();
        res.put("kpi", kpi);
        res.put("paidByMonth", paidSeries);
        res.put("appsByMonth", appsSeries);

        return res;
    }

    // ====== helpers ======

    private long countAppsByStatus(String statusName) {
        // Если у тебя есть методы репозитория типа countByStatus(...) — используй их.
        // Тут универсальный вариант: грузим и считаем.
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

    private Map<YearMonth, BigDecimal> buildPaidByMonth() {
        Iterable<ActualPayment> payments = paymentRepo.findAll();

        Map<YearMonth, BigDecimal> map = new TreeMap<YearMonth, BigDecimal>(); // сортировка по месяцу
        for (ActualPayment p : payments) {
            LocalDate d = p.getPaymentDate();
            if (d == null) continue;

            YearMonth ym = YearMonth.of(d.getYear(), d.getMonthValue());
            BigDecimal cur = map.get(ym);
            if (cur == null) cur = BigDecimal.ZERO;

            BigDecimal add = (p.getAmount() == null) ? BigDecimal.ZERO : p.getAmount();
            map.put(ym, cur.add(add));
        }
        return fillLastMonths(map, 12);
    }

    private Map<YearMonth, BigDecimal> buildAppsByMonth() {
        Iterable<LeaseApplication> apps = appRepo.findAll();

        Map<YearMonth, BigDecimal> map = new TreeMap<YearMonth, BigDecimal>();
        for (LeaseApplication a : apps) {
            LocalDate d = a.getCreatedDate();
            if (d == null) continue;

            YearMonth ym = YearMonth.of(d.getYear(), d.getMonthValue());
            BigDecimal cur = map.get(ym);
            if (cur == null) cur = BigDecimal.ZERO;

            map.put(ym, cur.add(BigDecimal.ONE));
        }
        return fillLastMonths(map, 12);
    }

    // чтобы график всегда имел последние N месяцев (даже если 0)
    private Map<YearMonth, BigDecimal> fillLastMonths(Map<YearMonth, BigDecimal> src, int months) {
        Map<YearMonth, BigDecimal> out = new TreeMap<YearMonth, BigDecimal>();
        YearMonth now = YearMonth.now();

        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            BigDecimal v = src.get(ym);
            out.put(ym, v == null ? BigDecimal.ZERO : v);
        }
        return out;
    }

    private Map<String, Object> toSeries(Map<YearMonth, BigDecimal> map) {
        List<String> labels = new ArrayList<String>();
        List<BigDecimal> values = new ArrayList<BigDecimal>();

        for (Map.Entry<YearMonth, BigDecimal> e : map.entrySet()) {
            labels.add(e.getKey().toString());  // YYYY-MM
            values.add(e.getValue());
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("labels", labels);
        result.put("values", values);
        return result;
    }
}
