package de.venomenon.cscxtool.participant;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/countries")
class CountryController {

    private final CountryCatalog countryCatalog;

    CountryController(CountryCatalog countryCatalog) {
        this.countryCatalog = countryCatalog;
    }

    @GetMapping
    List<Country> findAll() {
        return countryCatalog.findAll();
    }
}
