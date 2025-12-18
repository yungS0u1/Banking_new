package org.example.config;

import org.example.domain.*;
import org.example.repo.*;
import org.example.service.LeasingCalculationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
public class DemoDataSeeder implements CommandLineRunner {

    private final ClientRepository clientRepo;
    private final SupplierRepository supplierRepo;
    private final InsuranceCompanyRepository insurerRepo;
    private final LeasedAssetRepository assetRepo;

    private final LeaseApplicationRepository appRepo;
    private final PaymentScheduleItemRepository scheduleRepo;

    private final LeaseContractRepository contractRepo;
    private final ActualPaymentRepository paymentRepo;

    private final LeasingCalculationService calcService;

    public DemoDataSeeder(
            ClientRepository clientRepo,
            SupplierRepository supplierRepo,
            InsuranceCompanyRepository insurerRepo,
            LeasedAssetRepository assetRepo,
            LeaseApplicationRepository appRepo,
            PaymentScheduleItemRepository scheduleRepo,
            LeaseContractRepository contractRepo,
            ActualPaymentRepository paymentRepo,
            LeasingCalculationService calcService
    ) {
        this.clientRepo = clientRepo;
        this.supplierRepo = supplierRepo;
        this.insurerRepo = insurerRepo;
        this.assetRepo = assetRepo;
        this.appRepo = appRepo;
        this.scheduleRepo = scheduleRepo;
        this.contractRepo = contractRepo;
        this.paymentRepo = paymentRepo;
        this.calcService = calcService;
    }

    @Override
    public void run(String... args) {
        // 1) чтобы не плодить данные при перезапуске:
        // если уже есть заявки/договоры — значит сидер отработал ранее.
        if (appRepo.count() > 0 || contractRepo.count() > 0) {
            return;
        }

        Random rnd = new Random(42); // детерминированно

        // --- Справочники ---
        List<InsuranceCompany> insurers = seedInsurers();
        List<Supplier> suppliers = seedSuppliers();
        List<Client> clients = seedClients();
        List<LeasedAsset> assets = seedAssets(suppliers, insurers);

        // --- Заявки + графики ---
        List<LeaseApplication> apps = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Client c = clients.get(rnd.nextInt(clients.size()));
            LeasedAsset a = assets.get(rnd.nextInt(assets.size()));

            BigDecimal price = nvl(a.getPrice());
            BigDecimal advance = price.multiply(new BigDecimal(rndFrom(rnd, 0.05, 0.30))).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal financed = price.subtract(advance);

            LeaseApplication app = new LeaseApplication();
            app.setApplicationNumber("APP-DEMO-" + (10000 + i));
            app.setCreatedDate(LocalDate.now().minusDays(rnd.nextInt(40)));
            app.setClient(c);
            app.setAsset(a);

            app.setAssetPrice(price);
            app.setAdvanceAmount(advance);
            app.setFinancedAmount(financed);

            int term = pick(rnd, new int[]{12, 24, 36, 48});
            app.setTermMonths(term);

            BigDecimal rate = new BigDecimal(pick(rnd, new int[]{10, 12, 14, 16, 18})); // % годовых
            app.setAnnualRatePercent(rate);

            LocalDate start = LocalDate.now().minusDays(rnd.nextInt(10));
            app.setStartDate(start);

            // статусы для разнообразия (важно для аналитики)
            int roll = rnd.nextInt(100);
            if (roll < 55) {
                app.setStatus(LeaseApplication.Status.APPROVED);
            } else if (roll < 75) {
                app.setStatus(LeaseApplication.Status.REJECTED);
                app.setRejectionReason("Недостаточно документов");
            } else {
                app.setStatus(LeaseApplication.Status.NEW);
            }

            app = appRepo.save(app);
            apps.add(app);

            // график платежей создаём всем, даже NEW/REJECTED — для демонстрации расчёта
            List<PaymentScheduleItem> schedule = calcService.buildAnnuitySchedule(app);
            for (PaymentScheduleItem it : schedule) {
                it.setApplication(app);
            }
            scheduleRepo.saveAll(schedule);
        }

        // --- Договоры из APPROVED + платежи ---
        for (LeaseApplication app : apps) {
            if (app.getStatus() != LeaseApplication.Status.APPROVED) continue;

            LeaseContract c = new LeaseContract();
            c.setContractNumber("CN-DEMO-" + app.getApplicationNumber().substring(app.getApplicationNumber().length() - 5));
            c.setContractDate(LocalDate.now().minusDays(new Random(app.getId()).nextInt(30)));
            c.setApplication(app);

            c.setFinancedAmount(app.getFinancedAmount());
            c.setTermMonths(app.getTermMonths());
            c.setAnnualRatePercent(app.getAnnualRatePercent());
            c.setStartDate(app.getStartDate());

            c = contractRepo.save(c);

            // переводим заявку в CONTRACTED иногда (чтобы было разнообразие)
            if (new Random(app.getId()).nextBoolean()) {
                app.setStatus(LeaseApplication.Status.CONTRACTED);
                appRepo.save(app);
            }

            // Фактические платежи: 0..5 штук
            int paymentsCount = rnd.nextInt(6);
            for (int k = 0; k < paymentsCount; k++) {
                ActualPayment p = new ActualPayment();
                p.setContract(c);

                LocalDate payDate = app.getStartDate().plusMonths(k).plusDays(rnd.nextInt(10)); // иногда сдвиг
                p.setPaymentDate(payDate);

                // платёж: чаще около "по графику", иногда меньше → появится arrears
                BigDecimal base = guessPlannedInstallment(app);
                BigDecimal fact = base.multiply(new BigDecimal(rndFrom(rnd, 0.7, 1.15))).setScale(2, BigDecimal.ROUND_HALF_UP);

                p.setAmount(fact);
                p.setComment(fact.compareTo(base) < 0 ? "Частичное погашение" : "Оплата по графику");

                paymentRepo.save(p);
            }
        }
    }

    // ---------- helpers ----------

    private List<InsuranceCompany> seedInsurers() {
        if (insurerRepo.count() > 0) return (List<InsuranceCompany>) insurerRepo.findAll();

        List<InsuranceCompany> list = new ArrayList<>();
        list.add(makeInsurer("Ингосстрах", "7705042179", "+7-495-956-55-55", "info@ingos.ru"));
        list.add(makeInsurer("РЕСО-Гарантия", "7710045520", "+7-495-730-30-00", "info@reso.ru"));
        list.add(makeInsurer("АльфаСтрахование", "7715002459", "+7-495-788-09-99", "info@alfastrah.ru"));
        return (List<InsuranceCompany>) insurerRepo.saveAll(list);
    }

    private InsuranceCompany makeInsurer(String name, String inn, String phone, String email) {
        InsuranceCompany i = new InsuranceCompany();
        i.setName(name);
        i.setInn(inn);
        i.setPhone(phone);
        i.setEmail(email);
        return i;
    }

    private List<Supplier> seedSuppliers() {
        if (supplierRepo.count() > 0) return (List<Supplier>) supplierRepo.findAll();

        List<Supplier> list = new ArrayList<>();
        list.add(makeSupplier("Автосалон Север", "7701000001", "+7-495-111-11-11", "sales@north-auto.ru"));
        list.add(makeSupplier("ТехноПоставка", "7701000002", "+7-495-222-22-22", "info@techno-post.ru"));
        list.add(makeSupplier("Лизинг-Трейд", "7701000003", "+7-495-333-33-33", "office@leasing-trade.ru"));
        return (List<Supplier>) supplierRepo.saveAll(list);
    }

    private Supplier makeSupplier(String name, String inn, String phone, String email) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setInn(inn);
        s.setPhone(phone);
        s.setEmail(email);
        return s;
    }

    private List<Client> seedClients() {
        if (clientRepo.count() > 0) return (List<Client>) clientRepo.findAll();

        List<Client> list = new ArrayList<>();

        list.add(makeClient("ООО Ромашка", "UL", "7700000000", "+7-900-333-44-55", "info@romashka.ru"));
        list.add(makeClient("Иванов Иван", "FL", "4000123456", "+7-900-444-55-66", "ivanov@mail.ru"));
        list.add(makeClient("ООО Альтаир", "UL", "7701234567", "+7-901-111-22-33", "office@altair.ru"));
        list.add(makeClient("Петров Пётр", "FL", "4500123987", "+7-902-222-33-44", "petrov@mail.ru"));
        list.add(makeClient("ООО Сигма", "UL", "7709876543", "+7-903-333-44-55", "contact@sigma.ru"));

        // добиваем до 12–15
        for (int i = 0; i < 10; i++) {
            list.add(makeClient("ООО Клиент-" + (i + 1), "UL",
                    "77000" + (10000 + i), "+7-905-100-10-" + pad2(i),
                    "client" + (i + 1) + "@demo.ru"));
        }

        return (List<Client>) clientRepo.saveAll(list);
    }

    private Client makeClient(String name, String type, String innPassport, String phone, String email) {
        Client c = new Client();
        c.setName(name);
        c.setClientType(type); // у тебя сейчас строка
        c.setIdentifier(innPassport);
        c.setPhone(phone);
        c.setEmail(email);
        return c;
    }

    private List<LeasedAsset> seedAssets(List<Supplier> suppliers, List<InsuranceCompany> insurers) {
        if (assetRepo.count() > 0) return (List<LeasedAsset>) assetRepo.findAll();

        Supplier auto = suppliers.get(0);
        Supplier tech = suppliers.get(1);

        InsuranceCompany ing = insurers.get(0);
        InsuranceCompany reso = insurers.get(1);

        List<LeasedAsset> list = new ArrayList<>();

        list.add(asset("AUTO", "VW Transporter T6", "VIN-VWT6-0001", bd("3200000"), auto, ing));
        list.add(asset("AUTO", "Ford Transit", "VIN-FTR-0002", bd("2900000"), auto, reso));
        list.add(asset("AUTO", "Ford Mondeo", "VIN-FMD-0003", bd("2700000"), auto, ing));
        list.add(asset("EQUIPMENT", "Станок ЧПУ Haas", "SN-HAAS-9001", bd("5500000"), tech, reso));
        list.add(asset("EQUIPMENT", "Компрессор Atlas Copco", "SN-ACP-4412", bd("1300000"), tech, ing));

        // добиваем ещё 10–15
        for (int i = 0; i < 12; i++) {
            String type = (i % 2 == 0) ? "AUTO" : "EQUIPMENT";
            Supplier sup = (i % 2 == 0) ? auto : tech;
            InsuranceCompany ins = (i % 3 == 0) ? ing : reso;

            list.add(asset(type,
                    (type.equals("AUTO") ? "Авто " : "Оборудование ") + (i + 1),
                    (type.equals("AUTO") ? "VIN-" : "SN-") + "DEMO-" + (100 + i),
                    bd(String.valueOf(800000 + i * 250000)),
                    sup, ins));
        }

        return (List<LeasedAsset>) assetRepo.saveAll(list);
    }

    private LeasedAsset asset(String assetType, String name, String serial, BigDecimal price, Supplier s, InsuranceCompany ins) {
        LeasedAsset a = new LeasedAsset();
        a.setAssetType(assetType);
        a.setName(name);
        a.setSerialNumber(serial);
        a.setPrice(price);
        a.setSupplier(s);
        a.setInsurer(ins);
        return a;
    }

    private BigDecimal guessPlannedInstallment(LeaseApplication app) {
        // грубая оценка “платёж по графику” для генерации факта (не идеально, но достаточно)
        BigDecimal financed = nvl(app.getFinancedAmount());
        int term = app.getTermMonths() == null ? 36 : app.getTermMonths();
        if (term <= 0) term = 36;
        return financed.divide(new BigDecimal(term), 2, BigDecimal.ROUND_HALF_UP);
    }

    private BigDecimal bd(String s) { return new BigDecimal(s); }
    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private int pick(Random rnd, int[] vals) { return vals[rnd.nextInt(vals.length)]; }

    private double rndFrom(Random rnd, double min, double max) {
        return min + (max - min) * rnd.nextDouble();
    }

    private String pad2(int i) { return (i < 10 ? "0" + i : String.valueOf(i)); }
}
