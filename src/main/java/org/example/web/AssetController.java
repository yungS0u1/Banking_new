package org.example.web;

import org.example.domain.LeasedAsset;
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
        model.addAttribute("assets", assetRepo.findAll());
        return "assets/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("asset", new LeasedAsset());
        model.addAttribute("suppliers", supplierRepo.findAll());
        model.addAttribute("insurers", insurerRepo.findAll());
        return "assets/form.html";
    }

    @PostMapping
    public String save(@ModelAttribute LeasedAsset asset) {
        assetRepo.save(asset);
        return "redirect:/assets";
    }
}
