package org.example.web;

import org.example.domain.LeaseApplication;
import org.example.domain.LeasedAsset;
import org.example.domain.PaymentScheduleItem;
import org.example.repo.ClientRepository;
import org.example.repo.LeaseApplicationRepository;
import org.example.repo.LeasedAssetRepository;
import org.example.repo.PaymentScheduleItemRepository;
import org.example.service.LeasingCalculationService;
import org.example.web.dto.LeaseApplicationForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/applications")
public class LeaseApplicationController {

    private final LeaseApplicationRepository appRepo;
    private final PaymentScheduleItemRepository scheduleRepo;
    private final ClientRepository clientRepo;
    private final LeasedAssetRepository assetRepo;
    private final LeasingCalculationService calcService;

    public LeaseApplicationController(
            LeaseApplicationRepository appRepo,
            PaymentScheduleItemRepository scheduleRepo,
            ClientRepository clientRepo,
            LeasedAssetRepository assetRepo,
            LeasingCalculationService calcService
    ) {
        this.appRepo = appRepo;
        this.scheduleRepo = scheduleRepo;
        this.clientRepo = clientRepo;
        this.assetRepo = assetRepo;
        this.calcService = calcService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("apps", appRepo.findAll());
        return "applications/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        LeaseApplicationForm form = new LeaseApplicationForm();

        form.setTermMonths(36);
        form.setAnnualRatePercent(new BigDecimal("14"));
        form.setAdvanceAmount(new BigDecimal("0"));
        form.setStartDate(LocalDate.now().toString()); // если startDate в форме String

        model.addAttribute("form", form);
        model.addAttribute("clients", clientRepo.findAll());
        model.addAttribute("assets", assetRepo.findAll());
        return "applications/form";
    }


    @PostMapping
    public String create(@ModelAttribute("form.html") LeaseApplicationForm form) {
        LeaseApplication app = new LeaseApplication();
        app.setApplicationNumber("APP-" + System.currentTimeMillis());
        app.setCreatedDate(LocalDate.now());
        app.setStatus(LeaseApplication.Status.NEW);

        app.setClient(clientRepo.findById(form.getClientId()).orElseThrow(IllegalArgumentException::new));
        LeasedAsset asset = assetRepo.findById(form.getAssetId()).orElseThrow(IllegalArgumentException::new);
        app.setAsset(asset);

        BigDecimal price = asset.getPrice() == null ? BigDecimal.ZERO : asset.getPrice();
        BigDecimal advance = form.getAdvanceAmount() == null ? BigDecimal.ZERO : form.getAdvanceAmount();
        if (advance.compareTo(BigDecimal.ZERO) < 0) advance = BigDecimal.ZERO;

        BigDecimal financed = price.subtract(advance);
        if (financed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Financed amount must be > 0 (price - advance)");
        }

        if (form.getStartDate() == null || form.getStartDate().trim().isEmpty()) {
            throw new IllegalArgumentException("Start date is required");
        }

        app.setAssetPrice(price);
        app.setAdvanceAmount(advance);
        app.setFinancedAmount(financed);

        app.setTermMonths(form.getTermMonths());
        app.setAnnualRatePercent(form.getAnnualRatePercent());
        LocalDate start = LocalDate.parse(form.getStartDate()); // ожидает yyyy-MM-dd
        app.setStartDate(start);


        app = appRepo.save(app);

        List<PaymentScheduleItem> schedule = calcService.buildAnnuitySchedule(app);
        for (PaymentScheduleItem item : schedule) {
            item.setApplication(app);
        }
        scheduleRepo.saveAll(schedule);

        return "redirect:/applications/" + app.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        LeaseApplication app = appRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        model.addAttribute("app", app);
        model.addAttribute("schedule", scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(id));
        return "applications/view";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id) {
        LeaseApplication app = appRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        if (app.getStatus() == LeaseApplication.Status.NEW) {
            app.setStatus(LeaseApplication.Status.APPROVED);
            appRepo.save(app);
        }
        return "redirect:/applications/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam("reason") String reason) {
        LeaseApplication app = appRepo.findById(id).orElseThrow(IllegalArgumentException::new);

        if (app.getStatus() == LeaseApplication.Status.NEW || app.getStatus() == LeaseApplication.Status.APPROVED) {
            app.setStatus(LeaseApplication.Status.REJECTED);
            app.setRejectionReason(reason);
            appRepo.save(app);
        }

        return "redirect:/applications/" + id;
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        LeaseApplication app = appRepo.findById(id).orElseThrow(IllegalArgumentException::new);
        model.addAttribute("app", app);
        model.addAttribute("schedule", scheduleRepo.findByApplicationIdOrderByPaymentNoAsc(id));
        return "applications/print";
    }




}
