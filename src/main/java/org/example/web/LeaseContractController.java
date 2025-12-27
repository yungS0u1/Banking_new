package org.example.web;

import org.example.domain.ActualPayment;
import org.example.domain.LeaseApplication;
import org.example.domain.LeaseContract;
import org.example.domain.LeasedAsset;
import org.example.domain.PaymentScheduleItem;
import org.example.repo.ActualPaymentRepository;
import org.example.repo.LeaseApplicationRepository;
import org.example.repo.LeaseContractRepository;
import org.example.repo.PaymentScheduleItemRepository;
import org.example.service.PaymentsAnalyticsService;
import org.example.web.dto.ActualPaymentForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/contracts")
public class LeaseContractController {

    private final LeaseContractRepository contractRepo;
    private final LeaseApplicationRepository appRepo;
    private final PaymentScheduleItemRepository scheduleRepo;
    private final ActualPaymentRepository paymentRepo;
    private final PaymentsAnalyticsService analytics;

    public LeaseContractController(LeaseContractRepository contractRepo,
                                   LeaseApplicationRepository appRepo,
                                   PaymentScheduleItemRepository scheduleRepo,
                                   ActualPaymentRepository paymentRepo,
                                   PaymentsAnalyticsService analytics) {
        this.contractRepo = contractRepo;
        this.appRepo = appRepo;
        this.scheduleRepo = scheduleRepo;
        this.paymentRepo = paymentRepo;
        this.analytics = analytics;
    }

    // ----------------- helpers -----------------

    private String nz(String v, String def) {
        if (v == null) return def;
        String t = v.trim();
        return t.isEmpty() ? def : t;
    }

    private String fmtDate(LocalDate d) {
        if (d == null) return "___ . ___ . ______";
        return String.format("%02d.%02d.%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private String fmtMoney(BigDecimal v) {
        if (v == null) return "0,00";
        BigDecimal x = v.setScale(2, RoundingMode.HALF_UP);
        return x.toPlainString().replace('.', ',');
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String fmtRate(BigDecimal v) {
        if (v == null) return "__";
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // ----------------- create contract -----------------

    @PostMapping("/from-application/{appId}")
    public String createFromApplication(@PathVariable Long appId) {
        LeaseApplication app = appRepo.findById(appId).orElseThrow(IllegalArgumentException::new);

        if (app.getStatus() != LeaseApplication.Status.APPROVED) {
            return "redirect:/applications/" + appId;
        }

        // если договор уже есть — просто открыть его
        return contractRepo.findByApplicationId(appId)
                .map(c -> "redirect:/contracts/" + c.getId())
                .orElseGet(() -> {
                    LeaseContract c = new LeaseContract();
                    c.setContractNumber("CN-" + System.currentTimeMillis());
                    c.setContractDate(LocalDate.now());
                    c.setApplication(app);

                    c.setFinancedAmount(app.getFinancedAmount());
                    c.setTermMonths(app.getTermMonths());
                    c.setAnnualRatePercent(app.getAnnualRatePercent());
                    c.setStartDate(app.getStartDate());

                    c = contractRepo.save(c);

                    app.setStatus(LeaseApplication.Status.CONTRACTED);
                    appRepo.save(app);

                    return "redirect:/contracts/" + c.getId();
                });
    }

    // ----------------- list & view -----------------

    @GetMapping
    public String list(Model model) {
        model.addAttribute("active", "contracts");
        model.addAttribute("contracts", contractRepo.findAll());
        return "contracts/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);

        model.addAttribute("active", "contracts");
        model.addAttribute("contract", c);
        model.addAttribute("app", c.getApplication());
        model.addAttribute("schedule", scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(c.getApplication().getId()));

        // фактические платежи
        List<ActualPayment> payments = paymentRepo.findByContractIdOrderByPaymentDateAsc(c.getId());
        model.addAttribute("payments", payments);

        // форма по умолчанию
        ActualPaymentForm form = new ActualPaymentForm();
        form.setPaymentDate(LocalDate.now().toString()); // yyyy-MM-dd
        model.addAttribute("paymentForm", form);

        // план/факт на сегодня
        LocalDate today = LocalDate.now();
        BigDecimal planned = analytics.plannedPaidUpTo(c.getApplication().getId(), today);
        BigDecimal paid = analytics.actuallyPaidUpTo(c.getId(), today);

        BigDecimal diff = planned.subtract(paid);
        BigDecimal arrears = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
        BigDecimal overpaid = diff.compareTo(BigDecimal.ZERO) < 0 ? diff.abs() : BigDecimal.ZERO;

        model.addAttribute("plannedToDate", planned);
        model.addAttribute("paidToDate", paid);
        model.addAttribute("arrearsToDate", arrears);
        model.addAttribute("overpaidToDate", overpaid);

        return "contracts/view";
    }

    // ----------------- actual payments -----------------

    @PostMapping("/{id}/payments")
    public String addPayment(@PathVariable Long id, @ModelAttribute("paymentForm") ActualPaymentForm form) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);

        if (form.getAmount() == null || form.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "redirect:/contracts/" + id;
        }

        ActualPayment p = new ActualPayment();
        p.setContract(c);
        p.setPaymentDate(LocalDate.parse(form.getPaymentDate())); // yyyy-MM-dd
        p.setAmount(form.getAmount());
        p.setComment(form.getComment());

        paymentRepo.save(p);
        return "redirect:/contracts/" + id;
    }

    // ----------------- chart data -----------------

    @GetMapping("/{id}/chart-data")
    @ResponseBody
    public Map<String, Object> chartData(@PathVariable Long id) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        Long appId = c.getApplication().getId();

        List<PaymentScheduleItem> plan = scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(appId);
        List<ActualPayment> facts = paymentRepo.findByContractIdOrderByPaymentDateAsc(id);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> plannedCum = new ArrayList<>();
        List<BigDecimal> paidCum = new ArrayList<>();
        List<BigDecimal> arrears = new ArrayList<>();

        BigDecimal planSum = BigDecimal.ZERO;
        BigDecimal paidSum = BigDecimal.ZERO;

        int fi = 0;

        for (PaymentScheduleItem p : plan) {
            labels.add(p.getDueDate() != null ? p.getDueDate().toString() : "");

            planSum = planSum.add(nvl(p.getPaymentTotal()));
            plannedCum.add(planSum);

            while (fi < facts.size()
                    && facts.get(fi).getPaymentDate() != null
                    && p.getDueDate() != null
                    && !facts.get(fi).getPaymentDate().isAfter(p.getDueDate())) {
                paidSum = paidSum.add(nvl(facts.get(fi).getAmount()));
                fi++;
            }
            paidCum.add(paidSum);

            arrears.add(planSum.subtract(paidSum));
        }

        Map<String, Object> res = new HashMap<>();
        res.put("labels", labels);
        res.put("plannedCum", plannedCum);
        res.put("paidCum", paidCum);
        res.put("arrears", arrears);
        return res;
    }

    // ----------------- print (old) -----------------

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);

        model.addAttribute("active", "contracts");
        model.addAttribute("companySignerName", "Петров П.П.");
        model.addAttribute("companySignerBasis", "Устава");

        model.addAttribute("contract", c);
        model.addAttribute("app", c.getApplication());
        model.addAttribute("schedule", scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(c.getApplication().getId()));
        return "contracts/print";
    }

    // ----------------- print purchase -----------------

    @GetMapping("/{id}/print-purchase")
    public String printPurchase(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);

        model.addAttribute("active", "contracts");
        model.addAttribute("contract", c);
        model.addAttribute("app", c.getApplication());
        model.addAttribute("asset", c.getApplication() != null ? c.getApplication().getAsset() : null);

        model.addAttribute("lessorName", "ООО «Banking»");
        model.addAttribute("lessorSigner", "Петров П.П.");
        model.addAttribute("lessorSignerRole", "Директор");

        return "contracts/print-purchase";
    }

    // ----------------- print lease (FIXED) -----------------

    @GetMapping("/{id}/print-lease")
    public String printLease(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        LeaseApplication app = c.getApplication();
        LeasedAsset asset = app != null ? app.getAsset() : null;

        model.addAttribute("active", "contracts");
        model.addAttribute("contract", c);
        model.addAttribute("app", app);

        // ---- ВАЖНО: отдаём готовые строки, шаблон "тупой" ----

        model.addAttribute("contractNumberStr", nz(c.getContractNumber(), "__________"));
        model.addAttribute("contractDateStr", fmtDate(c.getContractDate()));

        // Лизингодатель (заглушки)
        model.addAttribute("lessorName", "ООО «Banking»");
        model.addAttribute("lessorCity", "г. Москва");
        model.addAttribute("companySignerName", "Иванов И.И.");
        model.addAttribute("companySignerBasis", "Устава");

        // Лизингополучатель
        String clientName = (app != null && app.getClient() != null)
                ? nz(app.getClient().getName(), "________________")
                : "________________";
        model.addAttribute("clientName", clientName);

        // Предмет лизинга
        String assetName = asset != null ? nz(asset.getName(), "________________") : "________________";
        model.addAttribute("assetName", assetName);

        // VIN/серийный номер (у тебя поле serialNumber)
        String assetSerial = asset != null ? nz(asset.getSerialNumber(), "________________") : "________________";
        model.addAttribute("assetSerial", assetSerial);

        // Финансирование / срок / ставка / дата начала
        model.addAttribute("financedStr", app != null ? fmtMoney(app.getFinancedAmount()) : "0,00");
        model.addAttribute("termStr", (app != null && app.getTermMonths() != null) ? String.valueOf(app.getTermMonths()) : "__");
        model.addAttribute("rateStr", app != null ? fmtRate(app.getAnnualRatePercent()) : "__");
        model.addAttribute("startDateStr", app != null ? fmtDate(app.getStartDate()) : "___ . ___ . ______");

        // Поставщик и страховая (если у сущностей есть getName())
        String supplierName = "________________";
        String insurerName = "________________";
        if (asset != null) {
            if (asset.getSupplier() != null) {
                supplierName = nz(asset.getSupplier().getName(), "________________");
            }
            if (asset.getInsurer() != null) {
                insurerName = nz(asset.getInsurer().getName(), "________________");
            }
        }
        model.addAttribute("supplierName", supplierName);
        model.addAttribute("insurerName", insurerName);

        return "contracts/print_lease";
    }

    // ----------------- print schedule -----------------

    @GetMapping("/{id}/print-schedule")
    public String printSchedule(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        Long appId = c.getApplication().getId();

        model.addAttribute("active", "contracts");
        model.addAttribute("contract", c);
        model.addAttribute("app", c.getApplication());

        List<PaymentScheduleItem> rows = scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(appId);
        model.addAttribute("schedule", rows);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPrincipal = BigDecimal.ZERO;

        for (PaymentScheduleItem r : rows) {
            if (r.getPaymentTotal() != null) total = total.add(r.getPaymentTotal());
            if (r.getPaymentInterest() != null) totalInterest = totalInterest.add(r.getPaymentInterest());
            if (r.getPaymentPrincipal() != null) totalPrincipal = totalPrincipal.add(r.getPaymentPrincipal());
        }

        model.addAttribute("totalPayments", total);
        model.addAttribute("totalInterest", totalInterest);
        model.addAttribute("totalPrincipal", totalPrincipal);

        return "contracts/print_schedule";
    }
}
