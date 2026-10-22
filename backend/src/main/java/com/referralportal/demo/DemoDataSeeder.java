package com.referralportal.demo;

import com.referralportal.common.ChipLists;
import com.referralportal.entity.Badge;
import com.referralportal.entity.Company;
import com.referralportal.entity.Conversation;
import com.referralportal.entity.Message;
import com.referralportal.entity.OpenRole;
import com.referralportal.entity.Referral;
import com.referralportal.entity.ReferrerProfile;
import com.referralportal.entity.User;
import com.referralportal.repository.jpa.BadgeJpaRepository;
import com.referralportal.repository.jpa.CompanyJpaRepository;
import com.referralportal.repository.jpa.ConversationJpaRepository;
import com.referralportal.repository.jpa.MessageJpaRepository;
import com.referralportal.repository.jpa.OpenRoleJpaRepository;
import com.referralportal.repository.jpa.ReferralJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Five demo users so every screen has something to show. Runs only when SEED_DEMO=true; keep it off for the
 * real launch. The people are fictional and use a single reserved .example domain.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    record Person(String name, String title) {}

    private static final String DEMO_DOMAIN = "demo.example";

    private static final List<Person> PEOPLE = List.of(
            new Person("Asha Kulkarni", "Engineering Manager"),
            new Person("Rohan Deshmukh", "Senior Backend Engineer"),
            new Person("Meera Joshi", "Android Lead"),
            new Person("Sneha Bhosale", "Data Science Manager"),
            new Person("Karan Mehta", "Developer Advocate"));

    private static final List<String> ROLE_TITLES = List.of(
            "Frontend Developer", "Backend Engineer", "Android Developer", "Data Engineer", "DevOps Engineer");

    @Autowired
    private UserJpaRepository users;
    @Autowired
    private CompanyJpaRepository companies;
    @Autowired
    private OpenRoleJpaRepository openRoles;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;
    @Autowired
    private BadgeJpaRepository badges;
    @Autowired
    private ConversationJpaRepository conversations;
    @Autowired
    private MessageJpaRepository messages;
    @Autowired
    private ReferralJpaRepository referrals;

    @Value("${app.seed-demo:false}")
    private boolean seedDemo;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDemo) {
            return;
        }
        Company company = companies.findByEmailDomain(DEMO_DOMAIN).orElseGet(() -> {
            Company created = new Company();
            created.setName("Demo Company");
            created.setEmailDomain(DEMO_DOMAIN);
            created.setBoothLabel("Booth A1");
            created.setSponsor(false);
            return companies.save(created);
        });
        if (openRoles.countByCompanyId(company.getId()) == 0) {
            for (String title : ROLE_TITLES) {
                OpenRole role = new OpenRole();
                role.setCompany(company);
                role.setTitle(title);
                role.setLevel("1-3");
                role.setSkills(ChipLists.toJson(List.of("Java", "React")));
                openRoles.save(role);
            }
        }
        for (Person person : PEOPLE) {
            String email = emailOf(person);
            User user = users.findByEmail(email).orElseGet(() -> {
                User created = new User();
                created.setName(person.name());
                created.setEmail(email);
                created.setAuthProvider("demo");
                created.setCreatedAt(Instant.now());
                return users.save(created);
            });
            if (!referrerProfiles.findById(user.getId()).isPresent()) {
                ReferrerProfile profile = new ReferrerProfile();
                profile.setUser(user);
                profile.setCompany(company);
                profile.setWorkEmail(email);
                profile.setVerifiedAt(Instant.now());
                profile.setOpenToMeet(true);
                profile.setJobTitle(person.title());
                referrerProfiles.save(profile);
            }
            if (!badges.existsById(user.getId())) {
                Badge badge = new Badge();
                badge.setUser(user);
                badge.setQrToken(UUID.randomUUID().toString().replace("-", ""));
                badges.save(badge);
            }
        }
    }

    /** Gives a brand-new account a lively inbox: an open chat, a pending invite and two referrals. */
    public void seedWelcome(long userId) {
        Long asha = userIdOrNull(emailOf(PEOPLE.get(0)));
        Long meera = userIdOrNull(emailOf(PEOPLE.get(2)));
        Long sneha = userIdOrNull(emailOf(PEOPLE.get(3)));
        if (asha == null || meera == null || sneha == null) {
            return;
        }
        if (conversations.findBySeekerIdAndReferrerId(userId, asha).isEmpty()) {
            Conversation chat = new Conversation();
            chat.setSeeker(refUser(userId));
            chat.setReferrer(refUser(asha));
            chat.setInitiatedBy("referrer");
            chat.setFirstMessage("Saw your profile, would love to chat about our frontend role.");
            chat.setStatus("accepted");
            chat.setMessageCount((short) 4);
            chat.setCreatedAt(Instant.now());
            chat = conversations.save(chat);
            long chatId = chat.getId();
            message(chatId, asha, "Hi! I saw your profile. React and Spring Boot is a great mix.");
            message(chatId, userId, "Thank you! I would love to learn more about the Frontend role.");
            message(chatId, asha, "Sure. Come to the Demo Company booth after the keynote.");
            message(chatId, userId, "Great, see you there!");
        }
        if (conversations.findBySeekerIdAndReferrerId(userId, meera).isEmpty()) {
            Conversation invite = new Conversation();
            invite.setSeeker(refUser(userId));
            invite.setReferrer(refUser(meera));
            invite.setInitiatedBy("referrer");
            invite.setFirstMessage("Your Android project looks great. Want to meet at our booth?");
            invite.setStatus("pending");
            invite.setMessageCount((short) 0);
            invite.setCreatedAt(Instant.now());
            conversations.save(invite);
        }
        referral(asha, userId, "Frontend Developer", "interview");
        referral(sneha, userId, "Data Engineer", "under_review");
    }

    private void message(long conversationId, long senderId, String body) {
        Message message = new Message();
        message.setConversation(conversations.getReferenceById(conversationId));
        message.setSender(refUser(senderId));
        message.setBody(body);
        message.setSentAt(Instant.now());
        messages.save(message);
    }

    private void referral(long referrerId, long candidateId, String roleTitle, String status) {
        Company company = companies.findByEmailDomain(DEMO_DOMAIN).orElse(null);
        if (company == null || referrals.existsByReferrerIdAndCandidateIdAndCompanyId(referrerId, candidateId, company.getId())) {
            return;
        }
        Referral referral = new Referral();
        referral.setReferrer(refUser(referrerId));
        referral.setCandidate(refUser(candidateId));
        referral.setCompany(company);
        openRoles.findFirstByCompanyIdAndTitle(company.getId(), roleTitle).ifPresent(referral::setRole);
        referral.setStatus(status);
        referral.setCreatedAt(Instant.now());
        referral.setUpdatedAt(Instant.now());
        referrals.save(referral);
    }

    private User refUser(long id) {
        return users.getReferenceById(id);
    }

    private static String emailOf(Person person) {
        return person.name().toLowerCase(Locale.ROOT).replace(' ', '.') + "@" + DEMO_DOMAIN;
    }

    private Long userIdOrNull(String email) {
        return users.findByEmail(email).map(User::getId).orElse(null);
    }
}
