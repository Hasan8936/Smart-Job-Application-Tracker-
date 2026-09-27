package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.SalaryInfo;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a salary the job description itself states, e.g. "$120,000 – $150,000 per year",
 * "USD 60/hour", "£55k", "₹12–18 LPA". Only returns a figure literally present in the text; a match
 * must look like pay (a range, a pay keyword nearby, or an explicit period) and not like funding or revenue.
 */
@Component
public class SalaryTextParser {

    private static final String CUR = "(US\\$|CA\\$|C\\$|A\\$|S\\$|\\$|€|£|₹|Rs\\.?|INR|USD|EUR|GBP|CAD|AUD|SGD)";
    /** "120,000", "120.000", "274,456.00", "120", "1.5" — optionally followed by k/M. */
    private static final String AMT = "(\\d{1,3}(?:[,.]\\d{3})+(?:[.,]\\d{2}(?!\\d))?|\\d+(?:\\.\\d+)?)\\s*([kKmM](?![a-zA-Z]))?";
    private static final String DASH = "\\s*(?:-|–|—|to)\\s*";

    /** currency amount [- [currency] amount]; e.g. "$120k - $150k", "EUR 60.000 to 70.000". */
    private static final Pattern MONEY = Pattern.compile(
            CUR + "\\s?" + AMT + "(?:" + DASH + CUR + "?\\s?" + AMT + ")?", Pattern.CASE_INSENSITIVE);

    /** Indian "12 - 18 LPA", "12 to 18 lakhs", "₹ 8 LPA". */
    private static final Pattern LPA = Pattern.compile(
            "(?:₹|Rs\\.?|INR)?\\s?(\\d+(?:\\.\\d+)?)(?:" + DASH + "(?:₹|Rs\\.?|INR)?\\s?(\\d+(?:\\.\\d+)?))?\\s*(LPA|L\\.P\\.A|lakhs?|lacs?)(?![a-zA-Z])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAY_WORDS = Pattern.compile(
            "salary|compensation|pay|base|ctc|remuneration|wage|stipend|ote|earn", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_PAY_WORDS = Pattern.compile(
            "fund|raised|revenue|valuation|arr\\b|series [a-e]|investment|budget|market|customers|users|assets|billion|profit|grant",
            Pattern.CASE_INSENSITIVE);

    /** Benefit amounts ("Fertility HRA (up to $10,000 per year)", "$1,500 learning stipend") are not salary. */
    private static final Pattern BENEFIT_WORDS = Pattern.compile(
            "hra|allowance|reimburs|benefit|401|fertility|learning|wellness|relocation|sign[- ]?on|signing|equity|perk|gym|"
                    + "tuition|education|insurance|coverage|match|credit|home office|internet|phone|commuter|childcare|donation",
            Pattern.CASE_INSENSITIVE);

    /** True when the words just before an amount name a benefit and no pay word is closer to the amount. */
    private static boolean benefitContext(String text, int amountStart) {
        String near = window(text, amountStart - 45, amountStart);
        Matcher benefit = BENEFIT_WORDS.matcher(near);
        int lastBenefit = -1;
        while (benefit.find()) lastBenefit = benefit.end();
        if (lastBenefit < 0) return false;
        Matcher pay = PAY_WORDS.matcher(near);
        while (pay.find()) if (pay.start() >= lastBenefit) return false;
        return true;
    }

    public SalaryInfo parse(String text) {
        if (text == null || text.isBlank()) return SalaryInfo.NONE;

        Matcher lpa = LPA.matcher(text);
        while (lpa.find()) {
            double lo = Double.parseDouble(lpa.group(1));
            Double hi = lpa.group(2) == null ? null : Double.parseDouble(lpa.group(2));
            if (lo <= 0 || lo > 500 || (hi != null && hi > 500)) continue;
            // "LPA" is unambiguous; a bare "lakh" ("5 lakh users") needs a pay word before it.
            boolean explicitLpa = lpa.group(3).toUpperCase(Locale.ROOT).replace(".", "").equals("LPA");
            if (NOT_PAY_WORDS.matcher(window(text, lpa.start() - 90, lpa.end() + 30)).find()) continue;
            if (!explicitLpa && !PAY_WORDS.matcher(window(text, lpa.start() - 90, lpa.start())).find()) continue;
            return SalaryInfo.of(lo * 100_000, hi == null ? null : hi * 100_000, "INR", "YEAR");
        }

        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            String currency = currency(m.group(1));
            double lo = amount(m.group(2), m.group(3));
            boolean range = m.group(5) != null;
            double hi = range ? amount(m.group(5), m.group(6)) : lo;
            // "$120-150k": the suffix on the upper bound applies to both.
            if (range && m.group(3) == null && m.group(6) != null && lo < hi / 100) lo = amount(m.group(2), m.group(6));
            if (lo <= 0 || hi <= 0) continue;

            String before = window(text, m.start() - 90, m.start());
            String after = window(text, m.end(), m.end() + 30);
            if (NOT_PAY_WORDS.matcher(before + " " + after).find()) continue;
            if (benefitContext(text, m.start())) continue;
            String period = period(after);
            boolean looksLikePay = range || period != null || PAY_WORDS.matcher(before).find();
            if (!looksLikePay) continue;

            double top = Math.max(lo, hi);
            if (period == null) period = top < 1000 ? "HOUR" : "YEAR";
            if (!plausible(top, period, currency)) continue;
            return SalaryInfo.of(lo, range ? hi : null, currency, period);
        }
        return SalaryInfo.NONE;
    }

    private static String currency(String symbol) {
        String s = symbol.toUpperCase(Locale.ROOT).replace(".", "");
        return switch (s) {
            case "US$", "$", "USD" -> "USD";
            case "CA$", "C$", "CAD" -> "CAD";
            case "A$", "AUD" -> "AUD";
            case "S$", "SGD" -> "SGD";
            case "€", "EUR" -> "EUR";
            case "£", "GBP" -> "GBP";
            default -> "INR"; // ₹, RS, INR
        };
    }

    private static double amount(String digits, String suffix) {
        if (digits == null) return 0;
        String d = digits;
        // Drop cents ("274,456.00", "60.000,00"), then thousands separators; "1.5" (with k/m) stays a decimal.
        if (d.matches("\\d{1,3}(?:[,.]\\d{3})+[.,]\\d{2}")) d = d.substring(0, d.length() - 3);
        if (d.matches("\\d{1,3}(?:[,.]\\d{3})+")) d = d.replaceAll("[,.]", "");
        double v;
        try { v = Double.parseDouble(d); } catch (NumberFormatException e) { return 0; }
        if (suffix != null) v *= suffix.equalsIgnoreCase("m") ? 1_000_000 : 1_000;
        return v;
    }

    private static String period(String after) {
        String a = after.toLowerCase(Locale.ROOT);
        if (a.matches("(?s)^\\s*(?:/|per|an|a|each)?\\s*(?:hour|hr)\\b.*") || a.matches("(?s)^\\s*hourly.*")) return "HOUR";
        if (a.matches("(?s)^\\s*(?:/|per|a)?\\s*(?:month|mo)\\b.*") || a.matches("(?s)^\\s*monthly.*")) return "MONTH";
        if (a.matches("(?s)^\\s*(?:/|per|a)?\\s*week\\b.*") || a.matches("(?s)^\\s*weekly.*")) return "WEEK";
        if (a.matches("(?s)^\\s*(?:/|per|a)?\\s*day\\b.*") || a.matches("(?s)^\\s*daily.*")) return "DAY";
        if (a.matches("(?s)^\\s*(?:/|per|a|an)?\\s*(?:year|yr|annum|annually|annual)\\b.*") || a.matches("(?s)^\\s*(?:p\\.?a\\.?|pa)\\b.*")) return "YEAR";
        return null;
    }

    /** Rejects numbers that can't be pay for the stated period (e.g. "$5 per year", "$2,000,000/hour"). */
    private static boolean plausible(double top, String period, String currency) {
        double scale = "INR".equals(currency) ? 80 : 1; // rough USD→INR magnitude, only for bounds
        return switch (period) {
            case "HOUR" -> top >= 5 * scale && top <= 1_000 * scale;
            case "DAY" -> top >= 30 * scale && top <= 8_000 * scale;
            case "WEEK" -> top >= 150 * scale && top <= 40_000 * scale;
            case "MONTH" -> top >= 300 * scale && top <= 200_000 * scale;
            default -> top >= 5_000 * scale && top <= 5_000_000 * scale;
        };
    }

    private static String window(String text, int from, int to) {
        return text.substring(Math.max(0, from), Math.min(text.length(), to));
    }
}
