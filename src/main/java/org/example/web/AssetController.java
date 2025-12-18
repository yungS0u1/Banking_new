package org.example.web;

import org.example.domain.InsuranceCompany;
import org.example.domain.LeasedAsset;
import org.example.domain.Supplier;
import org.example.repo.InsuranceCompanyRepository;
import org.example.repo.LeasedAssetRepository;
import org.example.repo.SupplierRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/assets")
public class AssetController {

    private final LeasedAssetRepository assetRepo;
    private final SupplierRepository supplierRepo;
    private final InsuranceCompanyRepository insurerRepo;

    public AssetController(LeasedAssetRepository assetRepo,
                           SupplierRepository supplierRepo,
                           InsuranceCompanyRepository insurerRepo) {
        this.assetRepo = assetRepo;
        this.supplierRepo = supplierRepo;
        this.insurerRepo = insurerRepo;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("active", "assets");
        model.addAttribute("assets", assetRepo.findAll());
        return "assets/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("active", "assets");
        model.addAttribute("asset", new LeasedAsset());
        model.addAttribute("suppliers", supplierRepo.findAll());
        model.addAttribute("insurers", insurerRepo.findAll());
        return "assets/form";
    }

    @PostMapping
    public String save(@ModelAttribute LeasedAsset asset,
                       @RequestParam(value = "supplierId", required = false) Long supplierId,
                       @RequestParam(value = "insurerId", required = false) Long insurerId) {

        if (supplierId != null) {
            Supplier s = supplierRepo.findById(supplierId).orElseThrow(IllegalArgumentException::new);
            asset.setSupplier(s);
        } else {
            asset.setSupplier(null);
        }

        if (insurerId != null) {
            InsuranceCompany i = insurerRepo.findById(insurerId).orElseThrow(IllegalArgumentException::new);
            asset.setInsurer(i);
        } else {
            asset.setInsurer(null);
        }

        assetRepo.save(asset);
        return "redirect:/assets";
    }
}
