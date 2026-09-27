package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Shapes taken from the live Greenhouse, Lever and Ashby public job-board APIs. */
class SalaryInfoTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception { return mapper.readTree(s); }

    @Test
    void greenhouseRangesAreCentsAndSpanAllZones() throws Exception {
        SalaryInfo s = SalaryInfo.fromGreenhouse(json("""
                [{"min_cents":14650000,"max_cents":19830000,"currency_type":"USD","title":"US Zone 2"},
                 {"min_cents":13020000,"max_cents":17620000,"currency_type":"USD","title":"US Zone 3"}]"""));
        assertEquals(130_200, s.min());
        assertEquals(198_300, s.max());
        assertEquals("USD", s.currency());
        assertEquals("YEAR", s.period());
    }

    @Test
    void greenhouseSmallAmountsAreHourlyAndOtherCurrenciesAreNotMixed() throws Exception {
        SalaryInfo hourly = SalaryInfo.fromGreenhouse(json("[{\"min_cents\":2900,\"max_cents\":2900,\"currency_type\":\"USD\"}]"));
        assertEquals(29, hourly.min());
        assertEquals("HOUR", hourly.period());

        SalaryInfo mixed = SalaryInfo.fromGreenhouse(json("""
                [{"min_cents":10000000,"max_cents":12000000,"currency_type":"USD"},
                 {"min_cents":9000000,"max_cents":20000000,"currency_type":"CAD"}]"""));
        assertEquals(100_000, mixed.min());
        assertEquals(120_000, mixed.max());
        assertFalse(SalaryInfo.fromGreenhouse(json("[]")).present());
    }

    @Test
    void leverSalaryRangeWithInterval() throws Exception {
        SalaryInfo s = SalaryInfo.fromLever(json("{\"currency\":\"usd\",\"interval\":\"per-year-salary\",\"min\":150000,\"max\":190000}"));
        assertEquals(150_000, s.min());
        assertEquals(190_000, s.max());
        assertEquals("USD", s.currency());
        assertEquals("YEAR", s.period());
        assertEquals("HOUR", SalaryInfo.fromLever(json("{\"currency\":\"USD\",\"interval\":\"per-hour-wage\",\"min\":40,\"max\":55}")).period());
        assertFalse(SalaryInfo.fromLever(json("{\"currency\":\"USD\",\"interval\":\"one-time\",\"min\":5000,\"max\":5000}")).present());
        assertFalse(SalaryInfo.fromLever(mapper.missingNode()).present());
    }

    @Test
    void ashbyUsesTheSalaryComponentNotEquity() throws Exception {
        SalaryInfo s = SalaryInfo.fromAshby(json("""
                {"compensationTierSummary":"$211.4K – $290.6K • Offers Equity",
                 "summaryComponents":[
                   {"compensationType":"EquityPercentage","interval":"NONE","currencyCode":null,"minValue":null,"maxValue":null},
                   {"compensationType":"Salary","interval":"1 YEAR","currencyCode":"USD","minValue":211400,"maxValue":290600}]}"""));
        assertEquals(211_400, s.min());
        assertEquals(290_600, s.max());
        assertEquals("USD", s.currency());
        assertEquals("YEAR", s.period());
        assertFalse(SalaryInfo.fromAshby(json("{\"summaryComponents\":[]}")).present());
    }

    @Test
    void ofOrdersBoundsAndDropsNonPositiveValues() {
        SalaryInfo s = SalaryInfo.of(200, 100, " eur ", "YEAR");
        assertEquals(100, s.min());
        assertEquals(200, s.max());
        assertEquals("EUR", s.currency());
        assertFalse(SalaryInfo.of(0, -5, "USD", null).present());
    }
}
