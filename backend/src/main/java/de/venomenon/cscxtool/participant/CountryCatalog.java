package de.venomenon.cscxtool.participant;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class CountryCatalog {

    private static final String CATALOG_RESOURCE = "countries/countries-de.json";
    // X* is the ISO 3166-1 user-assigned range. Scotland is a real CSC country but has no own alpha-2 code.
    private static final Country CSC_SCOTLAND = new Country("XS", "Schottland");

    private final List<Country> countries;
    private final Map<String, Country> countriesByCode;

    public CountryCatalog(ObjectMapper objectMapper) {
        List<Country> loadedCountries = new ArrayList<>(load(objectMapper));
        loadedCountries.replaceAll(CountryCatalog::withCscDisplayName);
        loadedCountries.add(CSC_SCOTLAND);
        Collator germanCollator = Collator.getInstance(Locale.GERMAN);
        List<Country> sortedCountries = loadedCountries.stream()
                .sorted(Comparator.comparing(Country::name, germanCollator))
                .toList();
        Map<String, Country> byCode = new LinkedHashMap<>();
        for (Country country : sortedCountries) {
            if (country == null || country.code() == null || !country.code().matches("[A-Z]{2}")
                    || country.name() == null || country.name().isBlank() || byCode.putIfAbsent(country.code(), country) != null) {
                throw new IllegalStateException("Der lokale Länderkatalog ist ungültig.");
            }
        }
        if (byCode.isEmpty()) {
            throw new IllegalStateException("Der lokale Länderkatalog darf nicht leer sein.");
        }
        this.countries = List.copyOf(sortedCountries);
        this.countriesByCode = Map.copyOf(byCode);
    }

    public List<Country> findAll() {
        return countries;
    }

    public Country findRequired(String countryCode) {
        String normalizedCode = normalize(countryCode);
        Country country = countriesByCode.get(normalizedCode);
        if (country == null) {
            throw new ApiBadRequestException(
                    "INVALID_COUNTRY_CODE",
                    "Der gewählte Ländercode wird nicht unterstützt."
            );
        }
        return country;
    }

    private static Country withCscDisplayName(Country country) {
        if (country == null) {
            return null;
        }
        return switch (country.code()) {
            case "CG" -> new Country(country.code(), "Kongo");
            case "CV" -> new Country(country.code(), "Kap Verde");
            default -> country;
        };
    }

    private static String normalize(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new ApiBadRequestException("VALIDATION_ERROR", "Das Land darf nicht leer sein.");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private static List<Country> load(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Der lokale Länderkatalog konnte nicht geladen werden.", exception);
        }
    }
}
