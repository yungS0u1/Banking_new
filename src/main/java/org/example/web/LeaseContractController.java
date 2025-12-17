package org.example.web;

import org.example.domain.LeaseApplication;
import org.example.domain.LeaseContract;
import org.example.repo.LeaseApplicationRepository;
import org.example.repo.LeaseContractRepository;
import org.example.repo.PaymentScheduleItemRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.example.repo.ActualPaymentRepository;
import org.example.service.PaymentsAnalyticsService;
import org.example.web.dto.ActualPaymentForm;


import org.example.domain.ActualPayment;

import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDate;

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


    @PostMapping("/from-application/{appId}")
    public String createFromApplication(@PathVariable Long appId) {
        LeaseApplication app = appRepo.findById(appId).orElseThrow(IllegalArgumentException::new);

        if (app.getStatus() != LeaseApplication.Status.APPROVED) {
            // по-простому: не даём создать договор пока не APPROVED
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

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);

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
        BigDecimal arrears = planned.subtract(paid);

        model.addAttribute("plannedToDate", planned);
        model.addAttribute("paidToDate", paid);
        model.addAttribute("arrearsToDate", arrears);

        return "contracts/view";
    }

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


    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        model.addAttribute("contract", c);
        model.addAttribute("app", c.getApplication());
        model.addAttribute("schedule", scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(c.getApplication().getId()));
        return "contracts/print";
    }

    @GetMapping("/{id}/chart-data")
    @ResponseBody
    public java.util.Map<String, Object> chartData(@PathVariable Long id) {
        LeaseContract c = contractRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        Long appId = c.getApplication().getId();

        // План (график платежей)
        java.util.List<org.example.domain.PaymentScheduleItem> plan =
                scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(appId);

        // Факт (платежи)
        java.util.List<org.example.domain.ActualPayment> facts =
                paymentRepo.findByContractIdOrderByPaymentDateAsc(id);

        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<java.math.BigDecimal> plannedCum = new java.util.ArrayList<>();
        java.util.List<java.math.BigDecimal> paidCum = new java.util.ArrayList<>();
        java.util.List<java.math.BigDecimal> arrears = new java.util.ArrayList<>();

        java.math.BigDecimal planSum = java.math.BigDecimal.ZERO;
        java.math.BigDecimal paidSum = java.math.BigDecimal.ZERO;

        // индекс по фактам
        int fi = 0;

        for (org.example.domain.PaymentScheduleItem p : plan) {
            labels.add(p.getDueDate().toString());

            planSum = planSum.add(nvl(p.getPaymentTotal()));
            plannedCum.add(planSum);

            // прибавляем все факты, которые <= dueDate
            while (fi < facts.size()
                    && facts.get(fi).getPaymentDate() != null
                    && !facts.get(fi).getPaymentDate().isAfter(p.getDueDate())) {
                paidSum = paidSum.add(nvl(facts.get(fi).getAmount()));
                fi++;
            }
            paidCum.add(paidSum);

            arrears.add(planSum.subtract(paidSum));
        }

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("labels", labels);
        res.put("plannedCum", plannedCum);
        res.put("paidCum", paidCum);
        res.put("arrears", arrears);
        return res;
    }

    private java.math.BigDecimal nvl(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }


}
