package com.smartjobtracker.jobs.discovery;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ISO country code for a free-text job location, or null when it can't be told (e.g. plain "Remote"). */
public final class CountryDetector {
    private CountryDetector() {}

    private static final Map<String, List<String>> COUNTRY_WORDS = Map.ofEntries(
            Map.entry("US", List.of("united states", "usa", "u.s.", "san francisco", "new york", "nyc", "seattle", "austin",
                    "boston", "chicago", "los angeles", "denver", "atlanta", "menlo park", "palo alto", "mountain view",
                    "sunnyvale", "san jose", "bellevue", "washington, dc", "washington dc", "washington, d.c.", "washington d.c.",
                    "sf", "bay area", "brooklyn", "miami", "dallas", "houston", "philadelphia", "san diego", "portland", "pittsburgh", "salt lake city")),
            Map.entry("IN", List.of("india", "bharat", "bengaluru", "bangalore", "hyderabad", "secunderabad", "pune", "mumbai",
                    "navi mumbai", "thane", "new delhi", "delhi", "delhi ncr", "ncr", "gurgaon", "gurugram", "noida", "greater noida",
                    "faridabad", "ghaziabad", "chennai", "kolkata", "ahmedabad", "gandhinagar", "vadodara", "surat", "kochi", "cochin",
                    "thiruvananthapuram", "trivandrum", "coimbatore", "madurai", "jaipur", "indore", "bhopal", "nagpur", "lucknow",
                    "chandigarh", "mohali", "bhubaneswar", "visakhapatnam", "vizag", "vijayawada", "mysuru", "mysore", "mangaluru",
                    "mangalore", "goa", "karnataka", "maharashtra", "telangana", "tamil nadu", "kerala", "haryana", "uttar pradesh",
                    "west bengal", "gujarat", "rajasthan", "andhra pradesh", "odisha", "punjab", "madhya pradesh")),
            Map.entry("GB", List.of("united kingdom", "england", "scotland", "london", "manchester", "edinburgh", "cambridge, uk")),
            Map.entry("CA", List.of("canada", "toronto", "vancouver", "montreal", "ottawa", "waterloo", "calgary")),
            Map.entry("DE", List.of("germany", "deutschland", "berlin", "munich", "münchen", "hamburg", "frankfurt")),
            Map.entry("FR", List.of("france", "paris")),
            Map.entry("NL", List.of("netherlands", "amsterdam")),
            Map.entry("IE", List.of("ireland", "dublin")),
            Map.entry("ES", List.of("spain", "madrid", "barcelona")),
            Map.entry("PL", List.of("poland", "warsaw", "krakow", "kraków")),
            Map.entry("SG", List.of("singapore")),
            Map.entry("AU", List.of("australia", "sydney", "melbourne")),
            Map.entry("JP", List.of("japan", "tokyo")),
            Map.entry("BR", List.of("brazil", "são paulo", "sao paulo")),
            Map.entry("MX", List.of("mexico", "méxico")),
            Map.entry("AE", List.of("united arab emirates", "dubai", "abu dhabi")),
            Map.entry("RO", List.of("romania", "bucharest")),
            Map.entry("TW", List.of("taiwan", "taipei")),
            Map.entry("CN", List.of("china", "shanghai", "beijing", "shenzhen")),
            Map.entry("IL", List.of("israel", "tel aviv")),
            Map.entry("SE", List.of("sweden", "stockholm")),
            Map.entry("CH", List.of("switzerland", "zurich", "zürich", "geneva")));

    private static final Set<String> US_STATES = Set.of("AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID",
            "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY",
            "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY", "DC");
    private static final Set<String> CA_PROVINCES = Set.of("ON", "BC", "QC", "AB", "MB", "NS", "NB", "SK", "NL", "PE");
    /**
     * JobSpy/Indeed's "[City, ]ST, CC" format — "Pune, MH, IN", "KA, IN", "Austin, TX, US": a region code followed by a
     * country code. (A US "City, IN" has a city name, not a region code, before IN, so Indiana isn't mistaken for India.)
     */
    private static final Pattern TRAILING_COUNTRY = Pattern.compile("(?:^|,\\s*)[A-Z]{2},\\s*(IN|US|CA|GB|UK|AU|SG|DE|IE)\\s*$");
    /** Indeed India's remote jobs: "Remote, IN". */
    private static final Pattern REMOTE_INDIA = Pattern.compile("^\\s*remote,\\s*IN\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGION_CODE = Pattern.compile(",\\s*([A-Z]{2})\\b");
    private static final Pattern US_TOKEN = Pattern.compile("\\b(US|USA)\\b");
    private static final Pattern UK_TOKEN = Pattern.compile("\\bUK\\b");

    public static String country(String location) {
        if (location == null || location.isBlank()) return null;
        if (REMOTE_INDIA.matcher(location).matches()) return "IN";
        Matcher trailing = TRAILING_COUNTRY.matcher(location.trim());
        if (trailing.find()) return "UK".equals(trailing.group(1)) ? "GB" : trailing.group(1);

        String lower = location.toLowerCase(Locale.ROOT);
        String best = null; int bestAt = Integer.MAX_VALUE;
        // The earliest mention wins, so "London (Remote from Germany)" → GB.
        for (Map.Entry<String, List<String>> e : COUNTRY_WORDS.entrySet()) {
            for (String word : e.getValue()) {
                int at = indexOfWord(lower, word);
                if (at >= 0 && at < bestAt) { bestAt = at; best = e.getKey(); }
            }
        }
        if (best != null) return best;
        Matcher code = REGION_CODE.matcher(location);
        while (code.find()) {
            if (US_STATES.contains(code.group(1))) return "US";
            if (CA_PROVINCES.contains(code.group(1))) return "CA";
        }
        if (US_TOKEN.matcher(location).find()) return "US";
        if (UK_TOKEN.matcher(location).find()) return "GB";
        return null;
    }

    private static int indexOfWord(String haystack, String word) {
        Matcher m = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(word) + "(?![\\p{L}])").matcher(haystack);
        return m.find() ? m.start() : -1;
    }
}
