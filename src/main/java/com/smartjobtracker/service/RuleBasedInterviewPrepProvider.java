package com.smartjobtracker.service;

import com.smartjobtracker.model.InterviewQuestionCategory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic, offline interview-question generator. Used whenever Gemini is not configured or fails.
 * Answers are first-person drafts built from the candidate's own resume bullets (picked for relevance to each
 * question) plus a short note on how to deliver them. They only restate resume lines -- no invented employers,
 * projects or metrics -- and where the resume can't answer (a conflict, a mistake, a skill not on it) the draft
 * says so and names what the candidate has to add from memory.
 */
@Component("ruleBasedInterviewPrepProvider")
public class RuleBasedInterviewPrepProvider implements InterviewPrepProvider {

    private static final List<String> SKILL_VOCABULARY = List.of(
            "java", "python", "javascript", "typescript", "c++", "c#", "sql", "nosql", "react", "angular", "vue",
            "node", "spring boot", "spring", "django", "flask", "docker", "kubernetes", "aws", "azure", "gcp",
            "rest api", "graphql", "microservices", "ci/cd", "git", "linux", "machine learning", "deep learning",
            "data analysis", "pandas", "numpy", "tensorflow", "pytorch", "excel", "power bi", "tableau", "agile",
            "scrum", "testing", "unit testing", "automation", "html", "css", "postgresql", "mysql", "mongodb",
            "redis", "kafka", "communication", "leadership", "project management", "quality assurance", "qa",
            "manual testing", "selenium", "api testing", "postman", "jira", "matlab", "embedded systems",
            "control systems", "instrumentation", "plc", "scada", "signal processing", "terraform");

    private static final Map<String, String> DISPLAY = Map.ofEntries(
            Map.entry("sql", "SQL"), Map.entry("nosql", "NoSQL"), Map.entry("aws", "AWS"), Map.entry("gcp", "GCP"),
            Map.entry("css", "CSS"), Map.entry("html", "HTML"), Map.entry("qa", "QA"), Map.entry("plc", "PLC"),
            Map.entry("scada", "SCADA"), Map.entry("ci/cd", "CI/CD"), Map.entry("rest api", "REST APIs"),
            Map.entry("node", "Node.js"), Map.entry("javascript", "JavaScript"), Map.entry("typescript", "TypeScript"),
            Map.entry("postgresql", "PostgreSQL"), Map.entry("mysql", "MySQL"), Map.entry("mongodb", "MongoDB"),
            Map.entry("graphql", "GraphQL"), Map.entry("pytorch", "PyTorch"), Map.entry("tensorflow", "TensorFlow"),
            Map.entry("numpy", "NumPy"), Map.entry("matlab", "MATLAB"), Map.entry("power bi", "Power BI"),
            Map.entry("c++", "C++"), Map.entry("c#", "C#"), Map.entry("git", "Git"), Map.entry("jira", "Jira"));

    /** Behavioral prompt, words that point at a fitting resume bullet, and what only the candidate can add (or null). */
    private record Behavioral(String question, List<String> hints, String personal) {}

    private static final List<Behavioral> BEHAVIORAL = List.of(
            new Behavioral("Tell me about yourself and what led you to this role.", List.of(), null),
            new Behavioral("Describe a time you had to meet a tight deadline. What did you do?",
                    List.of("deadline", "delivered", "shipped", "launched", "automated", "deployed", "reduced", "production"), "how much time you had and what you cut or prioritised to make it"),
            new Behavioral("Tell me about a mistake you made at work or in a project, and how you handled it.",
                    List.of("fixed", "bug", "debug", "incident", "monitoring", "test", "reliability", "optimized"), "the actual mistake (a wrong assumption, a missed case), how you found it, and the process change you made"),
            new Behavioral("Describe a situation where you disagreed with a teammate or supervisor. How did you resolve it?",
                    List.of("collaborated", "team", "stakeholders", "designed", "architected", "review"), "what the disagreement was about and how you reached a decision together"),
            new Behavioral("Tell me about a time you had to learn something new quickly to complete a task.",
                    List.of("learned", "new", "migrated", "adopted", "containerized", "deployed", "implemented"), "what was new to you and how you got productive with it quickly"),
            new Behavioral("Give an example of when you took initiative without being asked.",
                    List.of("automated", "improved", "introduced", "optimized", "added", "initiated", "proposed"), "what you noticed that nobody was fixing and why you decided to act"),
            new Behavioral("Describe a project you're most proud of and why.", List.of("designed", "architected", "built", "end-to-end"), null),
            new Behavioral("Tell me about a time you received critical feedback. How did you respond?",
                    List.of("review", "improved", "refactored", "iterated", "redesigned"), "the feedback you got and what you changed because of it"),
            new Behavioral("Describe a time you had to work with a difficult team member.",
                    List.of("collaborated", "cross-functional", "team", "coordinated", "stakeholders"), "the friction (described professionally, without blame) and what you did to make the collaboration work"),
            new Behavioral("Tell me about a time you had to juggle multiple priorities at once.",
                    List.of("multiple", "several", "three", "pipelines", "services", "concurrent"), "the competing priorities and how you decided the order"),
            new Behavioral("Describe a time you helped a colleague or team succeed.",
                    List.of("mentored", "helped", "documented", "team", "collaborated", "trained"), "who you helped and what changed for them"),
            new Behavioral("Tell me about a goal you set and how you achieved it.", List.of("improved", "increased", "reduced", "achieved"), null),
            new Behavioral("Describe a time you had to adapt quickly to a significant change.",
                    List.of("migrated", "moved", "changed", "containerized", "redesigned", "cloud"), "what changed and how you adjusted your plan"),
            new Behavioral("Tell me about a time you went above and beyond what was expected.", List.of("improved", "optimized", "automated"), "what was actually expected versus what you delivered"),
            new Behavioral("Describe a situation where you had to convince others to adopt your idea.",
                    List.of("proposed", "designed", "architected", "introduced", "led"), "who needed convincing and the evidence that won them over"));

    private record Situational(String question, String approach, List<String> hints) {}

    private static final List<Situational> SITUATIONAL = List.of(
            new Situational("If you were assigned a task with unclear requirements, how would you approach it?",
                    "I'd ask targeted questions up front, write down my assumptions, build the smallest version that tests them, and check in early rather than at the end.",
                    List.of("designed", "requirements", "prototype", "built")),
            new Situational("How would you handle discovering a serious bug or issue right before a deadline?",
                    "First I'd size the impact, then tell the team and stakeholders straight away so there are no surprises, decide together whether to fix now or ship with a guard, and follow up with a short post-mortem.",
                    List.of("latency", "fixed", "monitoring", "production", "incident", "reliability", "performance")),
            new Situational("If two stakeholders gave you conflicting priorities, how would you decide what to work on first?",
                    "I'd find out each deadline and the real cost of delay, make the trade-off explicit, and get both of them to agree on the order instead of quietly picking one.",
                    List.of("collaborated", "stakeholders", "teams", "coordinated")),
            new Situational("How would you approach a project in a technology you haven't used before?",
                    "I'd build a small working prototype first to surface the real unknowns, lean on the docs and someone experienced, timebox the learning, and share progress often.",
                    List.of("containerized", "deployed", "implemented", "adopted", "new")),
            new Situational("If you noticed a process that was inefficient, how would you go about improving it?",
                    "I'd measure what it costs today, find the root cause, propose a fix with its trade-offs, and get buy-in before changing anything other people rely on.",
                    List.of("automated", "reduced", "cut", "improved", "streamlined", "optimized")),
            new Situational("How would you handle a situation where you didn't understand feedback you were given?",
                    "I'd ask about it right away instead of guessing, repeat back what I understood, and ask for an example of what good looks like.",
                    List.of("review", "improved", "iterated")),
            new Situational("What would you do if you realized, midway through a task, that your approach wasn't working?",
                    "I'd stop early rather than sink more time into it, work out whether the assumption or the execution was wrong, tell the people depending on me, and switch approach.",
                    List.of("optimized", "redesigned", "reduced", "improved")),
            new Situational("If a team member was consistently missing deadlines, how would you handle it?",
                    "I'd talk to them privately first to understand what's blocking them, offer help or re-split the work, and only escalate if it keeps affecting the team's commitments.",
                    List.of("collaborated", "team", "coordinated")),
            new Situational("How would you handle receiving a task that was much larger in scope than expected?",
                    "I'd break it down, estimate each part, raise the new size with whoever owns the timeline, and agree on what ships first.",
                    List.of("designed", "architected", "pipelines", "services", "end-to-end")),
            new Situational("If you had to deliver a project solo with minimal guidance, how would you plan your approach?",
                    "I'd pin down what done means, split the work into milestones with something demoable at each, and send short progress updates so nobody is surprised.",
                    List.of("built", "designed", "end-to-end", "full-stack")),
            new Situational("How would you manage your work if you were simultaneously onboarding to a new codebase?",
                    "I'd pick small, low-risk tasks to learn the code, read the tests and deployment path first, and keep a running notes file of how things fit together.",
                    List.of("services", "codebase", "migrated", "refactored")),
            new Situational("If you discovered a security vulnerability in production code, what steps would you take?",
                    "I'd report it privately to the owner or security contact straight away, avoid discussing it in public channels, help assess the exposure, and help ship and verify the fix.",
                    List.of("auth", "security", "production", "monitoring")),
            new Situational("How would you ensure quality if you had limited time for testing?",
                    "I'd test the riskiest paths first, automate the checks that catch the most, and be explicit about what wasn't tested so the risk is a shared decision.",
                    List.of("test", "ci/cd", "automated", "pipelines", "monitoring")),
            new Situational("If stakeholders asked for a feature you thought was technically risky, how would you respond?",
                    "I'd explain the specific risk in their terms, offer a safer alternative or a staged rollout, and leave the final call with them once they have the full picture.",
                    List.of("designed", "architected", "scalable", "reliability")),
            new Situational("How would you approach mentoring a junior team member who is struggling?",
                    "I'd find out where they're actually stuck, pair with them on a real task, give them work that stretches them a little, and check in regularly without taking over.",
                    List.of("mentored", "helped", "documented", "team")));

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
            + "position:|ctc|lpa|notice period|joining|onsite|remote|hybrid|equal opportunity|benefits|"
            + "apply now|click here|\\d+\\s*lpa|\\d+\\s*years?\\s+experience)");

    @Override
    public List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count) {
        Context ctx = new Context(jobDescription, facts);
        int perCategory = Math.max(1, (count + 4) / 5);

        List<QuestionAnswer> out = new ArrayList<>();
        for (int i = 0; i < perCategory && i < BEHAVIORAL.size(); i++) out.add(behavioral(ctx, BEHAVIORAL.get(i), i));
        for (int i = 0; i < perCategory; i++) out.add(technical(ctx, i));
        addRoleSpecific(out, perCategory, ctx);
        for (int i = 0; i < perCategory && i < SITUATIONAL.size(); i++) out.add(situational(ctx, SITUATIONAL.get(i)));
        int companyCount = Math.max(perCategory, count - out.size());
        for (int i = 0; i < companyCount && i < COMPANY_MOTIVATION_QUESTIONS.size(); i++) out.add(company(ctx, i));

        if (out.size() > count) return new ArrayList<>(out.subList(0, count));
        int idx = perCategory;
        while (out.size() < count) out.add(technical(ctx, idx++)); // top up with technical if still short
        return out;
    }

    /** Everything derived once per generation: resume bullets, JD skills, and which bullets were already used. */
    private static final class Context {
        final FactProfile facts;
        final ResumeBullets bullets;
        final List<String> jdSkills;       // in the JD, resume-backed first
        final List<String> sharedSkills;   // in the JD and on the resume
        final List<String> responsibilities;
        final Set<ResumeBullets.Bullet> used = new HashSet<>();

        Context(String jd, FactProfile facts) {
            this.facts = facts;
            this.bullets = ResumeBullets.from(facts);
            List<String> inJd = new ArrayList<>();
            String lower = jd == null ? "" : jd.toLowerCase(Locale.ROOT);
            for (String s : SKILL_VOCABULARY) if (containsSkill(lower, s) && inJd.stream().noneMatch(x -> x.contains(s) || s.contains(x))) inJd.add(s);
            List<String> shared = new ArrayList<>(), missing = new ArrayList<>();
            for (String s : inJd) (onResume(facts, bullets, s) ? shared : missing).add(s);
            this.sharedSkills = shared;
            List<String> ordered = new ArrayList<>(shared); ordered.addAll(missing);
            this.jdSkills = ordered;
            this.responsibilities = responsibilitySentences(jd);
        }

        Optional<ResumeBullets.Bullet> pick(Collection<String> hints) {
            Optional<ResumeBullets.Bullet> b = bullets.best(hints, used);
            b.ifPresent(used::add);
            return b;
        }
    }

    // ── Behavioral ────────────────────────────────────────────────────────────

    private QuestionAnswer behavioral(Context ctx, Behavioral b, int index) {
        if (index == 0) return tellMeAboutYourself(ctx, b.question());
        List<String> hints = new ArrayList<>(b.hints()); hints.addAll(ctx.sharedSkills);
        Optional<ResumeBullets.Bullet> bullet = ctx.pick(hints);
        if (bullet.isEmpty()) {
            return new QuestionAnswer(InterviewQuestionCategory.BEHAVIORAL, b.question(),
                    "Your resume didn't give me a specific example to build this answer on. Pick one real project and tell it as a short story: "
                            + "the situation, what you personally did, and the result"
                            + (b.personal() == null ? "." : ", making sure to cover " + b.personal() + "."), "");
        }
        ResumeBullets.Bullet ex = bullet.get();
        String metric = ResumeBullets.metric(ex);
        StringBuilder a = new StringBuilder("Draft: ").append(ResumeBullets.restate(ex));
        if (b.personal() != null) a.append("\n\nThe resume shows the result, not the story. Add ").append(b.personal()).append(" — that's what the interviewer is really asking about.");
        else a.append("\n\nWalk through why it mattered, the key decision you made, and ")
                .append(metric.isEmpty() ? "what changed as a result." : "the result (" + metric + ").");
        return new QuestionAnswer(InterviewQuestionCategory.BEHAVIORAL, b.question(), a.toString(), ResumeBullets.evidence(ex));
    }

    private QuestionAnswer tellMeAboutYourself(Context ctx, String question) {
        StringBuilder a = new StringBuilder("Draft: ");
        String education = degree(ctx.facts == null ? null : ctx.facts.education());
        if (!education.isEmpty()) a.append(education).append(' ');
        List<String> evidence = new ArrayList<>();
        Optional<ResumeBullets.Bullet> exp = ctx.bullets.all().stream().filter(x -> x.section() == ResumeBullets.Section.EXPERIENCE).findFirst();
        Optional<ResumeBullets.Bullet> proj = ctx.bullets.all().stream().filter(x -> x.section() == ResumeBullets.Section.PROJECTS).findFirst();
        exp.ifPresent(x -> { a.append("Most recently, ").append(lowerFirst(ResumeBullets.restate(x))).append(' '); evidence.add(ResumeBullets.evidence(x)); ctx.used.add(x); });
        proj.ifPresent(x -> { a.append("I also ").append(stripSubject(ResumeBullets.firstPerson(x)))
                .append(ResumeBullets.intro(x).isEmpty() ? "" : " (" + ResumeBullets.intro(x).replace("On my ", "").replace(" project", "") + ")").append(". ");
                evidence.add(ResumeBullets.evidence(x)); ctx.used.add(x); });
        if (!ctx.sharedSkills.isEmpty()) a.append("That's why this role stands out to me: it's built around ").append(join(display(ctx.sharedSkills, 3))).append(", which is the work I've been doing.");
        if (evidence.isEmpty()) a.setLength(0);
        if (a.length() == 0) a.append("Keep it to about a minute: where you are now, the one or two experiences on your resume most related to this job, and why this role is the logical next step.");
        else a.append("\n\nKeep it to about a minute and end on why this role is the next step.");
        return new QuestionAnswer(InterviewQuestionCategory.BEHAVIORAL, question, a.toString().replaceAll("[ \\t]+\\n", "\n").trim(), String.join(" | ", evidence));
    }

    // ── Technical ─────────────────────────────────────────────────────────────

    private QuestionAnswer technical(Context ctx, int index) {
        if (ctx.jdSkills.isEmpty()) {
            Optional<ResumeBullets.Bullet> b = ctx.pick(List.of());
            String q = index == 0 ? "Walk me through the technical skills on your resume most relevant to this role."
                    : "What technical area would you most like to grow in, and how are you approaching it?";
            String a = b.map(x -> "Draft: " + ResumeBullets.restate(x) + "\n\nGo one level deeper than the resume: the design choice you made and why.")
                    .orElse("Pick your strongest technical skill, name the project where you used it, the specific problem it solved, and the outcome.");
            return new QuestionAnswer(InterviewQuestionCategory.TECHNICAL, q, a, b.map(ResumeBullets::evidence).orElse(""));
        }
        String skill = ctx.jdSkills.get(index % ctx.jdSkills.size());
        String name = display(skill);
        String question = index < ctx.jdSkills.size()
                ? "This role requires " + name + ". Walk me through your hands-on experience with it."
                : "Tell me about a hard problem you solved with " + name + ", and what you'd do differently now.";
        if (onResume(ctx.facts, ctx.bullets, skill)) {
            List<ResumeBullets.Bullet> hits = ctx.bullets.mentioning(skill);
            if (hits.isEmpty()) {
                return new QuestionAnswer(InterviewQuestionCategory.TECHNICAL, question,
                        "You list " + name + " as a skill, but no project bullet shows how you used it. Name the project where you used it, the problem it solved, and the result — and consider adding that bullet to your resume.",
                        evidenceFor(ctx.facts, skill));
            }
            ResumeBullets.Bullet first = hits.get((index / Math.max(1, ctx.jdSkills.size())) % hits.size());
            StringBuilder a = new StringBuilder("Draft: I've used ").append(name).append(" hands-on. ").append(ResumeBullets.restate(first));
            hits.stream().filter(h -> h != first).findFirst().ifPresent(h -> a.append(' ').append(
                    Objects.equals(h.heading(), first.heading()) && ResumeBullets.firstPerson(h).startsWith("I ")
                            ? "I also " + stripSubject(ResumeBullets.firstPerson(h)) + "."   // same role: don't repeat "As X at Y"
                            : capFirst(ResumeBullets.restate(h))));
            a.append("\n\nExpect a follow-up on the how: be ready to explain the design choices and trade-offs behind this ").append(name).append(" work.");
            return new QuestionAnswer(InterviewQuestionCategory.TECHNICAL, question, a.toString(), ResumeBullets.evidence(first));
        }
        String closest = closestSkill(ctx, skill);
        StringBuilder a = new StringBuilder(name).append(" isn't explicitly on my resume, so I'd say that plainly");
        if (!closest.isEmpty()) {
            Optional<ResumeBullets.Bullet> bridge = ctx.bullets.mentioning(closest).stream().findFirst();
            a.append(" and bridge to ").append(display(closest)).append(", which I have used");
            bridge.ifPresent(x -> a.append(": ").append(lowerFirst(ResumeBullets.restate(x))));
            if (bridge.isEmpty()) a.append('.');
        } else a.append('.');
        a.append(" Then explain how I'd get productive with ").append(name).append(": a small hands-on project, the official docs, and pairing with someone who knows it.");
        return new QuestionAnswer(InterviewQuestionCategory.TECHNICAL, question, a.toString(), "");
    }

    // ── Role-specific ─────────────────────────────────────────────────────────

    private void addRoleSpecific(List<QuestionAnswer> out, int max, Context ctx) {
        int added = 0;
        for (int i = 0; added < max && i < ctx.responsibilities.size(); i++) {
            String sentence = ctx.responsibilities.get(i);
            String q = "The job description says: \"" + sentence + "\" How does your background prepare you for this?";
            List<String> hints = ResumeBullets.keywords(sentence);
            Optional<ResumeBullets.Bullet> best = ctx.bullets.best(hints, ctx.used)
                    .filter(b -> ResumeBullets.score(b, hints) - (b.hasMetric() ? 2 : 0) > 0);
            String a;
            if (best.isPresent()) {
                ctx.used.add(best.get());
                a = "Draft: The closest thing I've done: " + lowerFirst(ResumeBullets.restate(best.get()))
                        + "\n\nConnect it explicitly: say which part of this requirement it covers, and be upfront about any part it doesn't.";
            } else {
                a = "Draft: I haven't done exactly this yet, and I'd say so. The most transferable experience I have is "
                        + ctx.bullets.best(ctx.sharedSkills, ctx.used).map(b -> lowerFirst(ResumeBullets.restate(b))).orElse("the projects on my resume")
                        + "\n\nThen describe how you'd approach this responsibility in your first weeks.";
            }
            out.add(new QuestionAnswer(InterviewQuestionCategory.ROLE_SPECIFIC, q, a, best.map(ResumeBullets::evidence).orElse("")));
            added++;
        }
        while (added < max) {
            Optional<ResumeBullets.Bullet> b = ctx.pick(ctx.sharedSkills);
            String q = added == 0 ? "Which of your past experiences best prepares you for the day-to-day of this role?"
                    : "Walk us through another experience on your resume that's relevant to this role.";
            String a = b.map(x -> "Draft: " + ResumeBullets.restate(x) + "\n\nTie it to the role: name the responsibility in the job description it maps to.")
                    .orElse("Pick the experience on your resume closest to this job's core responsibilities and explain the overlap concretely.");
            out.add(new QuestionAnswer(InterviewQuestionCategory.ROLE_SPECIFIC, q, a, b.map(ResumeBullets::evidence).orElse("")));
            added++;
        }
    }

    // ── Situational ───────────────────────────────────────────────────────────

    private QuestionAnswer situational(Context ctx, Situational s) {
        List<String> hints = new ArrayList<>(s.hints());
        Optional<ResumeBullets.Bullet> ex = ctx.bullets.best(hints, ctx.used)
                .filter(b -> ResumeBullets.score(b, hints) - (b.hasMetric() ? 2 : 0) > 0);
        ex.ifPresent(ctx.used::add);
        String a = "Draft: " + s.approach()
                + ex.map(b -> "\n\nBack it with something real — for example: " + lowerFirst(ResumeBullets.restate(b))).orElse("");
        return new QuestionAnswer(InterviewQuestionCategory.SITUATIONAL, s.question(), a, ex.map(ResumeBullets::evidence).orElse(""));
    }

    // ── Company & motivation ─────────────────────────────────────────────────

    private QuestionAnswer company(Context ctx, int index) {
        String q = COMPANY_MOTIVATION_QUESTIONS.get(index);
        String skills = join(display(ctx.sharedSkills, 3));
        String firstResponsibility = ctx.responsibilities.isEmpty() ? "" : ctx.responsibilities.get(0);
        Optional<ResumeBullets.Bullet> strongest = ctx.bullets.all().stream().filter(ResumeBullets.Bullet::hasMetric).findFirst()
                .or(() -> ctx.bullets.all().stream().findFirst());
        String a;
        String evidence = "";
        switch (index) {
            case 0, 5 -> {
                a = skills.isEmpty()
                        ? "Name the specific part of the job description that appeals to you and connect it to a project on your resume."
                        : "Draft: The role is built around " + skills + ", which is what I've been doing"
                          + strongest.map(b -> " — " + lowerFirst(ResumeBullets.restate(b))).orElse(".")
                          + (firstResponsibility.isEmpty() ? "" : "\n\nPoint at one line from the posting that excites you, e.g. \"" + firstResponsibility + "\"");
                evidence = strongest.map(ResumeBullets::evidence).orElse("");
            }
            case 4 -> {
                List<ResumeBullets.Bullet> top = ctx.bullets.all().stream().filter(ResumeBullets.Bullet::hasMetric).limit(2).toList();
                if (top.isEmpty()) top = ctx.bullets.all().stream().limit(2).toList();
                StringBuilder sb = new StringBuilder("Draft: ");
                if (!skills.isEmpty()) sb.append("I already work with ").append(skills).append(". ");
                for (ResumeBullets.Bullet b : top) sb.append(capFirst(ResumeBullets.restate(b))).append(' ');
                sb.append("\n\nKeep it to two or three concrete reasons, each backed by one of these results.");
                a = top.isEmpty() && skills.isEmpty()
                        ? "Give two or three concrete reasons, each backed by a project or result from your resume."
                        : sb.toString().trim();
                evidence = top.isEmpty() ? "" : ResumeBullets.evidence(top.get(0));
            }
            case 1 -> a = "The job description tells you what the team does"
                    + (firstResponsibility.isEmpty() ? "" : " (\"" + firstResponsibility + "\")")
                    + ". Before the interview, add one specific thing from the company's own website, product or engineering blog, and say why it connects to your work"
                    + (skills.isEmpty() ? "." : " with " + skills + ".");
            case 6 -> a = "Ask two or three real questions, for example: what the team's biggest challenge is this quarter, how success is measured in the first 90 days"
                    + (ctx.jdSkills.isEmpty() ? "" : ", and how the team uses " + display(ctx.jdSkills.get(0)) + " today") + ".";
            case 13 -> a = "Draft: In the first 90 days I'd want to understand the systems and the team's priorities, ship a first real change"
                    + (skills.isEmpty() ? "" : " using " + skills) + ", and take ownership of one area end to end.";
            default -> a = motivationAdvice(index);
        }
        return new QuestionAnswer(InterviewQuestionCategory.COMPANY_AND_MOTIVATION, q, a, evidence);
    }

    private String motivationAdvice(int index) {
        return switch (index) {
            case 2, 7 -> "Be honest and specific: name the skills or domain you want to be strong in over the next few years and show how this role builds them. Focus on mastery and impact before titles.";
            case 3 -> "Name what you're looking for (more ownership, a particular kind of problem, a specific stack) without criticising your current or past role, and show how this role offers it.";
            case 8 -> "Describe the conditions you actually do your best work in (clear goals, fast feedback, room to own things) and give a short example of when that happened.";
            case 9 -> "Name the concrete sources you use (docs, release notes, a newsletter, side projects) and one thing you learned recently and applied.";
            case 10 -> "Give a genuine answer and tie it to your work: the kind of problem or result that energises you, with a short example.";
            case 11 -> "Say what works for you and show flexibility: how you stay reachable and keep others unblocked whatever the setup.";
            case 12 -> "Be honest without oversharing: how you notice it, what you do about it (breaking work into smaller wins, talking to your manager), and how you protect quality meanwhile.";
            default -> "Be specific: describe what would be different and why, and make clear this role already covers most of it.";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean containsSkill(String haystack, String skill) {
        if (skill.length() <= 4 || skill.contains(" ") || !skill.chars().allMatch(Character::isLetter)) {
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(skill) + "(?![a-z0-9])").matcher(haystack).find();
        }
        return haystack.contains(skill);
    }

    private static boolean onResume(FactProfile facts, ResumeBullets bullets, String skill) {
        if (facts == null) return false;
        String all = String.join(" ", nz(facts.skills()), nz(facts.experience()), nz(facts.projects()), nz(facts.resumeText())).toLowerCase(Locale.ROOT);
        return containsSkill(all, skill) || !bullets.mentioning(skill).isEmpty();
    }

    private String closestSkill(Context ctx, String missing) {
        for (String s : ctx.sharedSkills) if (!s.equals(missing)) return s;
        String all = String.join(" ", nz(ctx.facts == null ? null : ctx.facts.skills()), nz(ctx.facts == null ? null : ctx.facts.resumeText())).toLowerCase(Locale.ROOT);
        for (String s : SKILL_VOCABULARY) if (!s.equalsIgnoreCase(missing) && containsSkill(all, s)) return s;
        return "";
    }

    private String evidenceFor(FactProfile facts, String skill) {
        if (facts == null) return "";
        if (containsIgnoreCase(facts.experience(), skill)) return trimmed(facts.experience(), 250);
        if (containsIgnoreCase(facts.projects(), skill)) return trimmed(facts.projects(), 250);
        return trimmed(facts.skills(), 250);
    }

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+|\\n+");

    private static List<String> responsibilitySentences(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) return List.of();
        List<String> sentences = new ArrayList<>();
        for (String raw : SENTENCE_SPLIT.split(jobDescription)) {
            String s = raw.replaceAll("^[\\s\\-•*►▸]+", "").trim();
            if (s.length() < 30 || s.length() > 220) continue;
            if (s.split("\\s+").length < 7) continue;                   // titles and headings, e.g. "Backend Engineer, Payments Platform."
            if (LOGISTICS_PATTERN.matcher(s).find()) continue;          // skip location/timing/type lines
            if (s.matches(".*\\b(certified|ISO|CMMI|SOC 2|established in \\d{4}).*")) continue;
            sentences.add(s);
            if (sentences.size() >= 12) break;
        }
        return sentences;
    }

    private static String display(String skill) {
        String d = DISPLAY.get(skill);
        if (d != null) return d;
        StringBuilder sb = new StringBuilder();
        for (String w : skill.split(" ")) sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }

    private static List<String> display(List<String> skills, int max) {
        return skills.stream().limit(max).map(RuleBasedInterviewPrepProvider::display).toList();
    }

    private static String join(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    private static final Pattern DEGREE = Pattern.compile(
            "(?i)\\b(b\\.?\\s?s\\.?c?|b\\.?\\s?e\\.?|b\\.?\\s?tech|b\\.?\\s?a\\.?|bachelor'?s?|m\\.?\\s?s\\.?c?|m\\.?\\s?tech|m\\.?\\s?e\\.?|master'?s?|mba|ph\\.?\\s?d|diploma|associate)\\b");
    private static final Pattern NOT_DEGREE_DETAIL = Pattern.compile(
            "(?i)\\s*(\\(|\\b)(c?gpa|percentage|grade)\\b.*$|\\b(19|20)\\d{2}\\b.*$|\\s+\\d{1,3}(\\.\\d+)?\\s*%.*$");

    /** "I have a B.S. Computer Science from State University." -- degree line plus the institution line before it. */
    private static String degree(String education) {
        if (education == null || education.isBlank()) return "";
        List<String> lines = Arrays.stream(education.split("\\n+")).map(String::trim).filter(l -> !l.isEmpty()).toList();
        for (int i = 0; i < lines.size(); i++) {
            if (!DEGREE.matcher(lines.get(i)).find()) continue;
            String degree = NOT_DEGREE_DETAIL.matcher(lines.get(i)).replaceAll("").replaceAll("[\\s,:;-]+$", "").trim();
            String school = "";
            if (i > 0 && !DEGREE.matcher(lines.get(i - 1)).find()) {
                school = NOT_DEGREE_DETAIL.matcher(lines.get(i - 1)).replaceAll("").replaceAll("[\\s,:;-]+$", "").trim();
            }
            if (degree.isEmpty()) return "";
            return "I have a " + degree + (school.isEmpty() || degree.toLowerCase(Locale.ROOT).contains(school.toLowerCase(Locale.ROOT)) ? "" : " from " + school) + ".";
        }
        return "";
    }

    private static String stripSubject(String firstPerson) {
        return firstPerson.startsWith("I ") ? firstPerson.substring(2).replaceAll("\\.$", "") : firstPerson.replaceAll("\\.$", "");
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty() || s.startsWith("I ")) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String capFirst(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String nz(String s) { return s == null ? "" : s; }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String trimmed(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String v = value.replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) + "..." : v;
    }
}
