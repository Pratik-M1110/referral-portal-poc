package com.referralportal.demo;

import com.referralportal.common.ChipLists;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sample companies, roles and referrers so every screen has something to show. Runs only when SEED_DEMO=true;
 * keep it off for the real launch. The people are fictional and use reserved .example domains.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    record Person(String name, String title) {}

    record Role(String title, String level, List<String> skills) {}

    record Co(String name, String domain, String booth, boolean sponsor, List<Role> roles, List<Person> people) {}

    private static final List<Co> COMPANIES = List.of(
            new Co("Nimbus Cloud", "nimbuscloud.example", "Booth A1", true,
                    List.of(new Role("Frontend Developer", "fresher", List.of("React", "TypeScript")),
                            new Role("Backend Engineer", "1-3", List.of("Java", "Spring Boot", "MySQL")),
                            new Role("Cloud Engineer", "1-3", List.of("GCP", "Kubernetes"))),
                    List.of(new Person("Asha Kulkarni", "Engineering Manager"), new Person("Rohan Deshmukh", "Senior Backend Engineer"))),
            new Co("Kiwi Pay", "kiwipay.example", "Booth B2", true,
                    List.of(new Role("Android Developer", "fresher", List.of("Kotlin", "Android")),
                            new Role("Full Stack Developer", "1-3", List.of("React", "Node.js", "SQL"))),
                    List.of(new Person("Meera Joshi", "Android Lead"), new Person("Aditya Patil", "Staff Engineer"))),
            new Co("Orbit Analytics", "orbitanalytics.example", null, false,
                    List.of(new Role("Data Engineer", "1-3", List.of("Python", "SQL", "Spark")),
                            new Role("ML Engineer", "3-6", List.of("Python", "TensorFlow"))),
                    List.of(new Person("Sneha Bhosale", "Data Science Manager"))),
            new Co("Zenith Mobility", "zenithmobility.example", "Booth C3", true,
                    List.of(new Role("Flutter Developer", "fresher", List.of("Flutter", "Dart")),
                            new Role("QA Automation Engineer", "1-3", List.of("Selenium", "Java"))),
                    List.of(new Person("Karan Mehta", "Engineering Manager"))),
            new Co("Lotus Health", "lotushealth.example", null, false,
                    List.of(new Role("Frontend Developer", "fresher", List.of("React", "TypeScript")),
                            new Role("DevOps Engineer", "3-6", List.of("Docker", "Terraform"))),
                    List.of(new Person("Priya Nair", "Product Engineer"))),
            new Co("BrightPath Learning", "brightpath.example", null, false,
                    List.of(new Role("Web Developer", "student", List.of("HTML", "CSS", "JavaScript"))),
                    List.of(new Person("Vikram Shinde", "Developer Advocate"))),
            new Co("Sahyadri Systems", "sahyadrisystems.example", null, false,
                    List.of(new Role("Java Developer", "fresher", List.of("Java", "Spring Boot")),
                            new Role("Site Reliability Engineer", "3-6", List.of("Linux", "Python"))),
                    List.of(new Person("Neha Gokhale", "Tech Lead"))),
            new Co("Orchid Games", "orchidgames.example", null, false,
                    List.of(new Role("Unity Developer", "fresher", List.of("C#", "Unity"))),
                    List.of(new Person("Ishaan Rao", "Game Programmer"))));

    private final JdbcClient jdbc;
    private final boolean seedDemo;

    public DemoDataSeeder(JdbcClient jdbc, @Value("${app.seed-demo:false}") boolean seedDemo) {
        this.jdbc = jdbc;
        this.seedDemo = seedDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDemo) {
            return;
        }
        for (Co co : COMPANIES) {
            jdbc.sql("INSERT IGNORE INTO companies (name, email_domain, booth_label, is_sponsor) VALUES (?, ?, ?, ?)")
                    .params(co.name(), co.domain(), co.booth(), co.sponsor()).update();
            long companyId = jdbc.sql("SELECT id FROM companies WHERE email_domain = ?").param(co.domain())
                    .query(Long.class).single();
            long roleCount = jdbc.sql("SELECT COUNT(*) FROM open_roles WHERE company_id = ?").param(companyId)
                    .query(Long.class).single();
            if (roleCount == 0) {
                for (Role role : co.roles()) {
                    jdbc.sql("INSERT INTO open_roles (company_id, title, level, skills) VALUES (?, ?, ?, ?)")
                            .params(companyId, role.title(), role.level(), ChipLists.toJson(role.skills())).update();
                }
            }
            for (Person person : co.people()) {
                String email = emailOf(person, co);
                jdbc.sql("INSERT IGNORE INTO users (name, email, auth_provider) VALUES (?, ?, 'demo')")
                        .params(person.name(), email).update();
                long userId = userId(email);
                jdbc.sql("""
                        INSERT IGNORE INTO referrer_profiles (user_id, company_id, work_email, verified_at, open_to_meet, job_title)
                        VALUES (?, ?, ?, NOW(), TRUE, ?)""")
                        .params(userId, companyId, email, person.title()).update();
                jdbc.sql("INSERT IGNORE INTO badges (user_id, qr_token) VALUES (?, REPLACE(UUID(), '-', ''))").param(userId).update();
            }
        }
    }

    /** Gives a brand-new account a lively inbox: an open chat, a pending invite and two referrals. */
    public void seedWelcome(long userId) {
        Long asha = userIdOrNull("asha.kulkarni@nimbuscloud.example");
        Long meera = userIdOrNull("meera.joshi@kiwipay.example");
        Long sneha = userIdOrNull("sneha.bhosale@orbitanalytics.example");
        if (asha == null || meera == null || sneha == null) {
            return;
        }
        jdbc.sql("""
                INSERT IGNORE INTO conversations (seeker_id, referrer_id, initiated_by, first_message, status, message_count)
                VALUES (?, ?, 'referrer', 'Saw your profile, would love to chat about our frontend role.', 'accepted', 4)""")
                .params(userId, asha).update();
        long chatId = jdbc.sql("SELECT id FROM conversations WHERE seeker_id = ? AND referrer_id = ?").params(userId, asha)
                .query(Long.class).single();
        message(chatId, asha, "Hi! I saw your profile. React and Spring Boot is a great mix.");
        message(chatId, userId, "Thank you! I would love to learn more about the Frontend role.");
        message(chatId, asha, "Sure. Come to the Nimbus Cloud booth after the keynote.");
        message(chatId, userId, "Great, see you there!");
        jdbc.sql("""
                INSERT IGNORE INTO conversations (seeker_id, referrer_id, initiated_by, first_message, status)
                VALUES (?, ?, 'referrer', 'Your Android project looks great. Want to meet at our booth?', 'pending')""")
                .params(userId, meera).update();
        referral(asha, userId, "nimbuscloud.example", "Frontend Developer", "interview");
        referral(sneha, userId, "orbitanalytics.example", "Data Engineer", "under_review");
    }

    private void message(long conversationId, long senderId, String body) {
        jdbc.sql("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, ?)")
                .params(conversationId, senderId, body).update();
    }

    private void referral(long referrerId, long candidateId, String domain, String roleTitle, String status) {
        jdbc.sql("""
                INSERT IGNORE INTO referrals (referrer_id, candidate_id, company_id, role_id, status)
                SELECT ?, ?, c.id, (SELECT r.id FROM open_roles r WHERE r.company_id = c.id AND r.title = ? LIMIT 1), ?
                FROM companies c WHERE c.email_domain = ?""")
                .params(referrerId, candidateId, roleTitle, status, domain).update();
    }

    private static String emailOf(Person person, Co co) {
        return person.name().toLowerCase(Locale.ROOT).replace(' ', '.') + "@" + co.domain();
    }

    private long userId(String email) {
        return jdbc.sql("SELECT id FROM users WHERE email = ?").param(email).query(Long.class).single();
    }

    private Long userIdOrNull(String email) {
        return jdbc.sql("SELECT id FROM users WHERE email = ?").param(email).query(Long.class).optional().orElse(null);
    }
}
