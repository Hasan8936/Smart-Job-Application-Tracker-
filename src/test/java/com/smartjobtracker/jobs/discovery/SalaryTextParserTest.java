package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.SalaryInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SalaryTextParserTest {
    private final SalaryTextParser parser = new SalaryTextParser();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Salary: $274,456.00 - $334,600.00/yr.                      | 274456 | 334600 | USD | YEAR",
            "The annual US base salary range is $170,400 – $255,700.     | 170400 | 255700 | USD | YEAR",
            "Compensation: $120k - $150k plus equity                     | 120000 | 150000 | USD | YEAR",
            "Base pay $120-150K depending on level                       | 120000 | 150000 | USD | YEAR",
            "Pay: $45/hr for this contract                               | 45     | null   | USD | HOUR",
            "We pay USD 30 to 40 per hour                                | 30     | 40     | USD | HOUR",
            "Salary £55,000 - £65,000 per annum                          | 55000  | 65000  | GBP | YEAR",
            "Gehalt: €60.000 – €70.000 pro Jahr, salary negotiable       | 60000  | 70000  | EUR | YEAR",
            "Monthly stipend of ₹40,000 per month                        | 40000  | null   | INR | MONTH",
            "CTC: 12 - 18 LPA                                            | 1200000| 1800000| INR | YEAR",
            "Offered salary ₹ 8 LPA for freshers                         | 800000 | null   | INR | YEAR",
            "Salary: 10 to 14 lakhs per annum                            | 1000000| 1400000| INR | YEAR",
            "CTC: ₹6,00,000 - ₹12,00,000 per annum                       | 600000 | 1200000| INR | YEAR",
            "Salary INR 12,50,000 to 18,00,000 p.a.                      | 1250000| 1800000| INR | YEAR",
            "Stipend: Rs. 10,000 per month                               | 10000  | null   | INR | MONTH",
            "Location Surat Salary range 3,25,000 LPA - 3,60,000 LPA      | 325000 | 360000 | INR | YEAR",
            "Package: 4.5 LPA for freshers                               | 450000 | null   | INR | YEAR",
            "Compensation: ₹ 1,20,00,000 per year for this leadership role | 12000000 | null | INR | YEAR",
    })
    void extractsStatedSalaries(String text, Integer min, Integer max, String currency, String period) {
        SalaryInfo s = parser.parse(text);
        assertTrue(s.present(), "expected a salary in: " + text);
        assertEquals(min, s.min());
        assertEquals(max, s.max());
        assertEquals(currency, s.currency());
        assertEquals(period, s.period());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "We automate how over $200B in annualized spend flows in and out of 70,000+ companies",
            "Fertility HRA (up to $10,000 per year)",
            "We raised $50M in our Series B led by top investors",
            "Our platform serves 5 lakh users every month",
            "A $1,500 annual learning budget",
            "Home office setup allowance of $500",
            "Revenue grew to $30 million last year",
            "We have 3 offices and 120 engineers",
            "Relocation assistance up to $15,000 per year of your move",
            "",
    })
    void ignoresAmountsThatAreNotPay(String text) {
        assertFalse(parser.parse(text).present(), "should not read a salary from: " + text);
    }
}
