package com.smartjobtracker.service;

import com.smartjobtracker.model.InterviewQuestionCategory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic, offline interview-question generator. Used whenever Gemini is not configured
 * or fails. Questions are grounded in resume facts and the job description; answers are written
 * as coaching guidance (first-person, STAR-shaped) rather than raw text dumps.
 */
@Component("ruleBasedInterviewPrepProvider")
public class RuleBasedInterviewPrepProvider implements InterviewPrepProvider {

    private static final List<String> SKILL_VOCABULARY = List.of(
            "java", "python", "javascript", "typescript", "c++", "c#", "sql", "nosql", "react", "angular", "vue",
            "node", "spring", "spring boot", "django", "flask", "docker", "kubernetes", "aws", "azure", "gcp",
            "rest api", "graphql", "microservices", "ci/cd", "git", "linux", "machine learning", "deep learning",
            "data analysis", "pandas", "numpy", "tensorflow", "pytorch", "excel", "power bi", "tableau", "agile",
            "scrum", "testing", "unit testing", "automation", "html", "css", "postgresql", "mysql", "mongodb",
            "redis", "kafka", "communication", "leadership", "project management", "quality assurance", "qa",
            "manual testing", "selenium", "api testing", "postman", "jira", "matlab", "embedded systems",
            "control systems", "instrumentation", "plc", "scada", "signal processing");

    private static final List<String> BEHAVIORAL_QUESTIONS = List.of(
            "Tell me about yourself and what led you to this role.",
            "Describe a time you had to meet a tight deadline. What did you do?",
            "Tell me about a mistake you made at work or in a project, and how you handled it.",
            "Describe a situation where you disagreed with a teammate or supervisor. How did you resolve it?",
            "Tell me about a time you had to learn something new quickly to complete a task.",
            "Give an example of when you took initiative without being asked.",
            "Describe a project you're most proud of and why.",
            "Tell me about a time you received critical feedback. How did you respond?",
            "Describe a time you had to work with a difficult team member.",
            "Tell me about a time you had to juggle multiple priorities at once.",
            "Describe a time you helped a colleague or team succeed.",
            "Tell me about a goal you set and how you achieved it.",
            "Describe a time you had to adapt quickly to a significant change.",
            "Tell me about a time you went above and beyond what was expected.",
            "Describe a situation where you had to convince others to adopt your idea.");

    private static final List<String> SITUATIONAL_QUESTIONS = List.of(
            "If you were assigned a task with unclear requirements, how would you approach it?",
            "How would you handle discovering a serious bug or issue right before a deadline?",
            "If two stakeholders gave you conflicting priorities, how would you decide what to work on first?",
            "How would you approach a project in a technology you haven't used before?",
            "If you noticed a process that was inefficient, how would you go about improving it?",
            "How would you handle a situation where you didn't understand feedback you were given?",
            "What would you do if you realized, midway through a task, that your approach wasn't working?",
            "If a team member was consistently missing deadlines, how would you handle it?",
            "How would you handle receiving a task that was much larger in scope than expected?",
            "If you had to deliver a project solo with minimal guidance, how would you plan your approach?",
            "How would you manage your work if you were simultaneously onboarding to a new codebase?",
            "If you discovered a security vulnerability in production code, what steps would you take?",
            "How would you ensure quality if you had limited time for testing?",
            "If stakeholders asked for a feature you thought was technically risky, how would you respond?",
            "How would you approach mentoring a junior team member who is struggling?");

    private static final List<String> COMPANY_MOTIVATION_QUESTIONS = List.of(
            "Why are you interested in this role specifically?",
            "What do you know about this company or team, and why does it appeal to you?",
            "Where do you see yourself professionally in the next 3–5 years?",
            "What are you looking for in your next opportunity that your current role doesn't offer?",
            "Why should we hire you over other candidates?",
            "What part of this job description excites you the most?",
            "What questions do you have for us about the role or the team?",
            "How does this role fit into your long-term career plan?",
            "What kind of work environment helps you do your best work?",
            "How do you stay up to date with industry trends and new technologies?",
            "What motivates you to do your best work every day?",
            "What's your preferred way to collaborate with a team — async, in-person, or a mix?",
            "How do you handle periods of low motivation or burnout?",
            "What does success look like to you in the first 90 days of this role?",
            "If you could design your ideal role, how would it differ from this one?");

    /** JD sentences that are logistics metadata, not responsibilities — skip these as interview questions. */
    private static final Pattern LOGISTICS_PATTERN = Pattern.compile(
            "(?i)(work from|work mode|shift timing|employment type|location:|experience:|education:|"
            + "monday to|full.time|part.time|\\bwfh\\b|\\bisa\\b|salary|compensation|"
            + "position:|ctc|lpa|notice period|joining|onsite|remote|hybrid|"
            + "apply now|click here|\\d+\\s*lpa|\\d+\\s*years?\\s+experience)");

    @Override
    public List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count) {
        List<String> skills = overlapSkills(jobDescription, facts);
        List<String> jdResponsibilities = jdResponsibilitySentences(jobDescription);
        int perCategory = Math.max(1, (count + 4) / 5);

        List<QuestionAnswer> out = new ArrayList<>();
        addUnique(out, InterviewQuestionCategory.BEHAVIORAL, perCategory, BEHAVIORAL_QUESTIONS, null,
                i -> behavioralAnswer(facts, i), i -> "");
        addUnique(out, InterviewQuestionCategory.TECHNICAL, perCategory, null,
                i -> technicalQuestion(skills, facts, i),
                i -> technicalAnswer(skills, facts, i), i -> technicalEvidence(skills, facts, i));
        addRoleSpecific(out, perCategory, jdResponsibilities, facts);
        addUnique(out, InterviewQuestionCategory.SITUATIONAL, perCategory, SITUATIONAL_QUESTIONS, null,
                i -> situationalAnswer(facts, i), i -> "");
        int companyCount = Math.max(perCategory, count - out.size());
        addUnique(out, InterviewQuestionCategory.COMPANY_AND_MOTIVATION, companyCount, COMPANY_MOTIVATION_QUESTIONS, null,
                i -> companyAnswer(facts, i), i -> "");

        if (out.size() > count) return new ArrayList<>(out.subList(0, count));
        // Top up with technical if still short
        int idx = perCategory;
        while (out.size() < count) {
            String q = technicalQuestion(skills, facts, idx);
            out.add(new QuestionAnswer(InterviewQuestionCategory.TECHNICAL, q,
                    technicalAnswer(skills, facts, idx), technicalEvidence(skills, facts, idx)));
            idx++;
        }
        return out;
    }

    private interface AnswerFn { String apply(int i); }
    private interface QuestionFn { String apply(int i); }

    private void addUnique(List<QuestionAnswer> out, InterviewQuestionCategory cat,
                            int max, List<String> pool, QuestionFn questionFn, AnswerFn answer, AnswerFn evidence) {
        int added = 0;
        for (int i = 0; added < max; i++) {
            if (pool != null && i >= pool.size()) break;
            String q = pool != null ? pool.get(i) : questionFn.apply(i);
            out.add(new QuestionAnswer(cat, q, answer.apply(i), evidence.apply(i)));
            added++;
        }
    }

    private void addRoleSpecific(List<QuestionAnswer> out, int max,
                                  List<String> responsibilities, FactProfile facts) {
        if (responsibilities.isEmpty()) {
            out.add(new QuestionAnswer(InterviewQuestionCategory.ROLE_SPECIFIC,
                    "Which of your past experiences best prepares you for the day-to-day of this role?",
                    roleAnswer(facts, ""), ""));
            return;
        }
        int added = 0;
        for (int i = 0; added < max && i < responsibilities.size(); i++) {
            String sentence = responsibilities.get(i);
            String q = "The job description mentions: \"" + sentence + "\" — how does your background prepare you for this?";
            out.add(new QuestionAnswer(InterviewQuestionCategory.ROLE_SPECIFIC, q,
                    roleAnswer(facts, sentence), trimmed(facts.experience(), 200)));
            added++;
        }
        // If fewer responsibilities than requested, pad with generic role questions
        while (added < max) {
            out.add(new QuestionAnswer(InterviewQuestionCategory.ROLE_SPECIFIC,
                    "Walk us through the experience on your resume that's most relevant to this role.",
                    roleAnswer(facts, ""), ""));
            added++;
        }
    }

    // ── Answer writers ────────────────────────────────────────────────────────

    private String behavioralAnswer(FactProfile facts, int index) {
        String role = firstRole(facts);
        String[] starters = {
            "I — answer this in three stages. **Situation:** briefly set the scene — what project or role, what the challenge was. **Action:** what you specifically did (own the 'I', not 'we'). **Result:** a concrete outcome — time saved, bug fixed, metric improved. Draw from your " + role + " experience.",
            "Use the STAR method. **S:** describe the deadline pressure — project, timeline. **T:** your specific responsibility. **A:** how you prioritised, what you dropped or delegated. **R:** what shipped and any trade-offs you'd make differently. Reference " + role + ".",
            "Be honest — interviewers value learning over perfection. **S:** name the mistake (missed a test case, wrong assumption). **T/A:** how you caught it or were told. **R:** what you changed in your process. Use a real example from " + role + ".",
            "Show maturity. **S:** describe the disagreement (technical approach, priority). **A:** how you listened, shared your reasoning, and reached a decision together or escalated appropriately. **R:** what happened. Avoid blaming — frame as healthy debate.",
            "Pick a genuinely fast ramp — a new language, tool, or domain. **S/T:** what you needed to learn and why it was urgent. **A:** your approach (docs, side project, asking a mentor). **R:** how quickly you shipped something with it.",
            "Describe a proactive contribution from " + role + ". **S:** what you noticed that no one was addressing. **A:** the specific steps you took without being asked. **R:** impact on the team or project. Quantify if possible.",
            "Pick the project you're proudest of from your resume. **S:** what it was and why it mattered. **A:** your key technical decisions. **R:** the outcome and what you learned. Connect to why it's relevant to this role.",
            "Frame feedback positively. **S:** who gave it and what the feedback was. **A:** how you took it — did you ask clarifying questions? **R:** how your work changed and what the impact was. Show you're coachable.",
            "Be diplomatic. **S:** describe the friction professionally. **A:** specific steps you took to improve communication or redistribute work. **R:** how the collaboration improved. Don't name or criticise the person.",
            "Show self-awareness about trade-offs. **S:** the competing priorities. **A:** how you assessed impact and urgency, what you communicated to stakeholders. **R:** what you shipped and what you deferred."
        };
        return starters[index % starters.length];
    }

    private String technicalQuestion(List<String> skills, FactProfile facts, int index) {
        if (skills == null || skills.isEmpty()) return index == 0
                ? "Walk me through the technical skills on your resume most relevant to this role."
                : "What technical area would you most like to grow in, and how are you approaching it?";
        String skill = skills.get(index % skills.size());
        return "This role requires " + skill + ". Walk me through your hands-on experience with it.";
    }

    private String technicalAnswer(List<String> skills, FactProfile facts, int index) {
        if (skills == null || skills.isEmpty())
            return "Walk through your strongest technical skills one by one: for each, name a project where you used it, the specific problem it solved, and the measurable outcome. Then connect each skill to the responsibilities in this job description.";
        String skill = skills.get(index % skills.size());
        boolean inResume = facts != null && (containsIgnoreCase(facts.experience(), skill)
                || containsIgnoreCase(facts.projects(), skill) || containsIgnoreCase(facts.skills(), skill));
        if (inResume) {
            String context = shortContext(facts, skill);
            return "You have direct " + skill + " experience" + (context.isEmpty() ? "" : " (" + context + ")") + ". Structure your answer: (1) the project or role where you used it, (2) a specific problem you solved — go deep on the 'how', (3) the result (performance, reliability, or business impact). If you have metrics, use them.";
        }
        String closest = closestSkill(facts, skill);
        return skill + " isn't explicitly on my resume — address this directly. Acknowledge the gap, then bridge to the closest skill you do have"
                + (closest.isEmpty() ? "" : " (" + closest + ")") + ". Explain your approach to picking up new stacks: a mini-project, reading the docs, or pairing with someone who knows it. Interviewers respect honesty and a clear ramp plan.";
    }

    private String technicalEvidence(List<String> skills, FactProfile facts, int index) {
        if (skills == null || skills.isEmpty() || facts == null) return "";
        String skill = skills.get(index % skills.size());
        boolean inResume = containsIgnoreCase(facts.experience(), skill)
                || containsIgnoreCase(facts.projects(), skill) || containsIgnoreCase(facts.skills(), skill);
        return inResume ? evidenceFor(facts, skill) : "";
    }

    private String roleAnswer(FactProfile facts, String jdSentence) {
        String role = firstRole(facts);
        if (jdSentence.isBlank()) {
            return "Connect your most relevant experience directly to this role. Start with the strongest overlap: what you built or owned that maps to the core responsibilities here. Use numbers where you have them (team size, scale, impact).";
        }
        return "Map your " + role + " experience to this specific requirement. Identify which project or responsibility in your background is the closest match, explain the overlap concretely, and highlight any metric or outcome that shows you can deliver this.";
    }

    private String situationalAnswer(FactProfile facts, int index) {
        String[] answers = {
            "Walk through your process: (1) clarify the ambiguity by asking specific questions, (2) document your assumptions before starting, (3) build a small proof-of-concept to validate the approach, (4) check in with the stakeholder early. Reference a real time you did this.",
            "Be methodical under pressure: (1) assess severity and blast radius first, (2) communicate proactively to the team and stakeholders — no surprises, (3) decide whether to fix-and-ship or delay the release, (4) write a post-mortem so it doesn't recur.",
            "Use an impact × urgency matrix. Clarify each stakeholder's actual deadline and the consequences of delay. Make a transparent call, document the reasoning, and loop both stakeholders in — don't go silent. Show you can escalate cleanly when needed.",
            "Your ramp strategy matters here: (1) build a minimal working prototype first to uncover the real unknowns, (2) find internal or community experts to pair with early, (3) timebox the learning, (4) over-communicate your progress. Reference how you've done this before.",
            "Show you're proactive: (1) identify the root cause, not just the symptom, (2) quantify the cost of the inefficiency (time, error rate), (3) propose a solution with trade-offs, (4) get buy-in before changing shared processes. Describe a concrete example.",
            "Be coachable: (1) pause and ask clarifying questions immediately rather than guessing, (2) repeat back your understanding to confirm, (3) ask for an example of what 'good' looks like. Show you value feedback and don't let ego get in the way.",
            "This shows adaptability: (1) stop early and don't throw good time after bad, (2) diagnose why the approach isn't working — was the assumption wrong or the execution? (3) communicate the pivot to stakeholders, (4) document what you learned."
        };
        return answers[index % answers.length];
    }

    private String companyAnswer(FactProfile facts, int index) {
        String role = firstRole(facts);
        String[] answers = {
            "Personalise this to the company. Research their product, recent news, and the team's engineering blog if they have one. Connect a specific thing they do to your experience in " + role + ". Generic 'great company culture' answers don't land — specifics do.",
            "Show you've done homework: name one specific thing about the company — a product decision, a technical challenge they've written about, or a value that resonates. Then explain why it connects to where you want to grow from your background in " + role + ".",
            "Be honest and forward-looking. Name 2–3 specific skills or domains you want to grow in. Show they align with what this role offers. Don't say 'manager' too early — emphasise mastery and impact first.",
            "Be genuine. Name one thing this role offers that your current search is focused on — more ownership, a specific tech stack, a product domain you care about. Then tie it back to your experience in " + role + " to show you're a natural fit, not just looking for anything.",
            "Be specific about what you bring. Point to 2–3 concrete things: a project that maps directly to their stack, a skill set that fills a gap, or a way of working that fits their team. Support each with a brief example.",
            "Pick one genuine thing — a technical challenge mentioned in the description, the product mission, or the type of work (backend, ML, platform). Explain why it maps to your strengths from " + role + " and where you want to take them.",
            "Prepare 2–3 thoughtful questions: about the team's biggest current challenge, the tech stack decisions they're rethinking, or how success is measured in the first 90 days. Avoid questions answered on their public website."
        };
        return answers[index % answers.length];
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String firstRole(FactProfile facts) {
        if (facts == null || blank(facts.experience())) return "your background";
        String exp = facts.experience().replaceAll("\\s+", " ").trim();
        // Try to extract the first job title line (usually ends with a date or dash)
        String[] lines = exp.split("[\\n,;]");
        for (String line : lines) {
            String l = line.trim();
            if (l.length() > 5 && l.length() < 80) return l.replaceAll("–.*|—.*|\\d{4}.*", "").trim();
        }
        return "your experience";
    }

    private String shortContext(FactProfile facts, String skill) {
        String evidence = evidenceFor(facts, skill);
        if (evidence.isBlank()) return "";
        // Return first ~60 chars of the evidence as a brief context hint
        String cleaned = evidence.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 60 ? cleaned.substring(0, 60) + "..." : cleaned;
    }

    private String closestSkill(FactProfile facts, String missing) {
        if (facts == null) return "";
        String allSkills = (facts.skills() == null ? "" : facts.skills()).toLowerCase(Locale.ROOT);
        // Pick the first skill from vocabulary that's in the resume
        for (String s : SKILL_VOCABULARY) {
            if (!s.equalsIgnoreCase(missing) && allSkills.contains(s)) return s;
        }
        return "";
    }

    private List<String> overlapSkills(String jobDescription, FactProfile facts) {
        String jd = jobDescription == null ? "" : jobDescription.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (String skill : SKILL_VOCABULARY) if (jd.contains(skill)) found.add(skill);
        List<String> grounded = new ArrayList<>(), ungrounded = new ArrayList<>();
        for (String skill : found) {
            boolean inResume = facts != null && (containsIgnoreCase(facts.skills(), skill)
                    || containsIgnoreCase(facts.experience(), skill) || containsIgnoreCase(facts.projects(), skill));
            (inResume ? grounded : ungrounded).add(skill);
        }
        grounded.addAll(ungrounded);
        return grounded;
    }

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+|\\n+");

    private List<String> jdResponsibilitySentences(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) return List.of();
        List<String> sentences = new ArrayList<>();
        for (String raw : SENTENCE_SPLIT.split(jobDescription)) {
            String s = raw.replaceAll("^[\\s\\-•*►▸]+", "").trim();
            if (s.length() < 30 || s.length() > 220) continue;
            if (LOGISTICS_PATTERN.matcher(s).find()) continue;      // skip location/timing/type lines
            if (s.matches(".*\\b(certified|ISO|CMMI|SOC 2|established in \\d{4}).*")) continue;
            sentences.add(s);
            if (sentences.size() >= 12) break;
        }
        return sentences;
    }

    private String evidenceFor(FactProfile facts, String skill) {
        if (containsIgnoreCase(facts.experience(), skill)) return trimmed(facts.experience(), 250);
        if (containsIgnoreCase(facts.projects(), skill)) return trimmed(facts.projects(), 250);
        return trimmed(facts.skills(), 250);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String trimmed(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String v = value.replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) + "..." : v;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
