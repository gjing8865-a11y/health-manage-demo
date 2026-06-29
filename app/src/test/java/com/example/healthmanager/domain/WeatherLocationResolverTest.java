package com.example.healthmanager.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherLocationResolverTest {
    @Test
    public void normalizeCityNameRemovesCommonAdministrativeSuffixes() {
        assertEquals(
                "\u676d\u5dde",
                WeatherLocationResolver.INSTANCE.normalizeCityName("\u676d\u5dde\u5e02")
        );
        assertEquals(
                "\u9999\u6e2f",
                WeatherLocationResolver.INSTANCE.normalizeCityName("\u9999\u6e2f\u7279\u522b\u884c\u653f\u533a")
        );
        assertNull(WeatherLocationResolver.INSTANCE.normalizeCityName("   "));
    }

    @Test
    public void buildCandidatePrefersDistrictForDisplayAndCityForQuery() {
        WeatherLocationCandidate candidate = WeatherLocationResolver.INSTANCE.buildCandidate(
                "\u676d\u5dde\u5e02",
                null,
                "\u6d59\u6c5f\u7701",
                "\u897f\u6e56\u533a"
        );

        assertEquals("\u897f\u6e56", candidate.getDisplayCity());
        assertEquals(2, candidate.getQueryCities().size());
        assertEquals("\u676d\u5dde", candidate.getQueryCities().get(0));
        assertEquals("\u897f\u6e56", candidate.getQueryCities().get(1));
    }

    @Test
    public void buildCandidateKeepsMunicipalityButDropsProvinceOnlyQueries() {
        WeatherLocationCandidate beijing = WeatherLocationResolver.INSTANCE.buildCandidate(
                "\u5317\u4eac\u5e02",
                null,
                "\u5317\u4eac\u5e02",
                "\u671d\u9633\u533a"
        );

        assertEquals("\u671d\u9633", beijing.getDisplayCity());
        assertEquals("\u5317\u4eac", beijing.getQueryCities().get(0));
        assertFalse(WeatherLocationResolver.INSTANCE.isProvinceLevelName("\u5317\u4eac"));
        assertTrue(WeatherLocationResolver.INSTANCE.isProvinceLevelName("\u6d59\u6c5f"));

        assertNull(WeatherLocationResolver.INSTANCE.buildCandidate(
                null,
                null,
                "\u6d59\u6c5f\u7701",
                null
        ));
    }
}
