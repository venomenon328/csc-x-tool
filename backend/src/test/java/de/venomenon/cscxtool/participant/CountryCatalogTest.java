package de.venomenon.cscxtool.participant;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CountryCatalogTest {

    private final CountryCatalog countries = new CountryCatalog(new ObjectMapper());

    @Test
    void exposesAllIsoAndCscSpecificCountriesWithStableCodesAndGermanSorting() {
        assertThat(countries.findAll()).hasSize(254).contains(
                new Country("XE", "England"),
                new Country("XN", "Nordirland"),
                new Country("XL", "Saarland"),
                new Country("XS", "Schottland"),
                new Country("XW", "Wales")
        );

        assertThat(countries.findRequired("xe")).isEqualTo(new Country("XE", "England"));
        assertThat(countries.findRequired("XN")).isEqualTo(new Country("XN", "Nordirland"));
        assertThat(countries.findRequired("xl")).isEqualTo(new Country("XL", "Saarland"));
        assertThat(countries.findRequired("XS")).isEqualTo(new Country("XS", "Schottland"));
        assertThat(countries.findRequired("xw")).isEqualTo(new Country("XW", "Wales"));

        assertThat(countries.findRequired("CG").name()).isEqualTo("Kongo");
        assertThat(countries.findRequired("CV").name()).isEqualTo("Kap Verde");
        assertThat(countries.findRequired("GB").name()).isNotIn("England", "Nordirland", "Schottland", "Wales");
        assertThat(countries.findRequired("DE").name()).isEqualTo("Deutschland");

        List<String> names = countries.findAll().stream().map(Country::name).toList();
        List<String> independentlySorted = new ArrayList<>(names);
        independentlySorted.sort(Collator.getInstance(Locale.GERMAN));
        assertThat(names).containsExactlyElementsOf(independentlySorted);
    }
}
