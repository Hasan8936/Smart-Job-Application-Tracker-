package com.smartjobtracker.jobs.discovery;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Location formats seen from LinkedIn, Indeed India and the official job boards. */
class CountryDetectorTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            // Indeed India (country_indeed=India)
            "KA, IN                             | IN",
            "AP, IN                             | IN",
            "Remote, IN                         | IN",
            "Pune, MH, IN                       | IN",
            // LinkedIn India
            "Hyderabad, Telangana, India        | IN",
            "Pune Division, Maharashtra, India  | IN",
            "Gurugram, Haryana                  | IN",
            "Noida                              | IN",
            // US formats must not be read as India (IN = Indiana)
            "Indianapolis, IN                   | US",
            "Austin, TX, US                     | US",
            "San Francisco, CA                  | US",
            // others
            "London                             | GB",
            "Toronto, ON                        | CA",
            "Remote                             | null",
            "null                               | null",
    })
    void detectsCountry(String location, String expected) {
        assertEquals(expected, CountryDetector.country(location));
    }
}
