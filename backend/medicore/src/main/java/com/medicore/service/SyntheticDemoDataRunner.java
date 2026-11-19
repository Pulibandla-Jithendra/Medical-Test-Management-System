package com.medicore.service;

import com.medicore.entity.DoctorPatientAssignment;
import com.medicore.entity.DoctorProfile;
import com.medicore.entity.MedicalTest;
import com.medicore.entity.PatientConsent;
import com.medicore.entity.PatientProfile;
import com.medicore.entity.RequestStatus;
import com.medicore.entity.TestRequest;
import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import com.medicore.repository.DoctorPatientAssignmentRepository;
import com.medicore.repository.DoctorProfileRepository;
import com.medicore.repository.MedicalTestRepository;
import com.medicore.repository.PatientConsentRepository;
import com.medicore.repository.PatientProfileRepository;
import com.medicore.repository.TestRequestRepository;
import com.medicore.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in, synthetic data for classroom demonstrations; disabled by default. */
@Component
@Order(2)
public class SyntheticDemoDataRunner implements ApplicationRunner {
    private final boolean enabled;
    private final String demoPassword;
    private final String consentTextVersion;
    private final UserAccountRepository users;
    private final PatientProfileRepository patients;
    private final DoctorProfileRepository doctors;
    private final MedicalTestRepository tests;
    private final DoctorPatientAssignmentRepository assignments;
    private final PatientConsentRepository consents;
    private final TestRequestRepository requests;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public SyntheticDemoDataRunner(@Value("${medicore.demo.seed-enabled:false}") boolean enabled,
                                   @Value("${medicore.demo.password:}") String demoPassword,
                                   @Value("${medicore.consent.text-version:v1}") String consentTextVersion,
                                   UserAccountRepository users, PatientProfileRepository patients,
                                   DoctorProfileRepository doctors, MedicalTestRepository tests,
                                   DoctorPatientAssignmentRepository assignments, PatientConsentRepository consents,
                                   TestRequestRepository requests, PasswordEncoder passwordEncoder, AuditService audit) {
        this.enabled = enabled;
        this.demoPassword = demoPassword;
        this.consentTextVersion = consentTextVersion;
        this.users = users;
        this.patients = patients;
        this.doctors = doctors;
        this.tests = tests;
        this.assignments = assignments;
        this.consents = consents;
        this.requests = requests;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (demoPassword == null || demoPassword.length() < 12
                || demoPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Synthetic demo seeding requires MEDICORE_DEMO_PASSWORD to be at least 12 characters and no more than 72 UTF-8 bytes.");
        }
        List<String> demoEmails = List.of("patient.one@example.test", "patient.two@example.test",
                "doctor.demo@example.test", "technician.demo@example.test");
        long existingDemoAccounts = demoEmails.stream().filter(email -> users.findByEmailIgnoreCase(email).isPresent()).count();
        if (existingDemoAccounts == demoEmails.size()) return;
        if (existingDemoAccounts > 0) {
            throw new IllegalStateException("Some synthetic demo accounts already exist; no records were changed. Resolve the partial seed or disable demo seeding.");
        }

        UserAccount patientOneUser = createUser("Casey Sample", demoEmails.get(0), UserRole.PATIENT);
        UserAccount patientTwoUser = createUser("Taylor Example", demoEmails.get(1), UserRole.PATIENT);
        UserAccount doctorUser = createUser("Morgan Demo", demoEmails.get(2), UserRole.DOCTOR);
        UserAccount technician = createUser("Riley Laboratory", demoEmails.get(3), UserRole.LAB_TECHNICIAN);

        PatientProfile patientOne = patient(patientOneUser, 34, "Synthetic", "5550100101", "100 Demo Street");
        PatientProfile patientTwo = patient(patientTwoUser, 42, "Synthetic", "5550100102", "200 Example Avenue");
        DoctorProfile doctor = new DoctorProfile();
        doctor.user = doctorUser;
        doctor.specialization = "General Practice (Demo)";
        doctors.save(doctor);

        MedicalTest bloodCount = test("Complete Blood Count", "Synthetic catalogue entry.", "0.00", "Demo reference range");
        MedicalTest lipid = test("Lipid Panel", "Synthetic catalogue entry.", "0.00", "Demo reference range");
        test("Thyroid Panel", "Synthetic catalogue entry.", "0.00", "Demo reference range");

        UserAccount admin = users.findAll().stream().filter(u -> u.enabled && u.role == UserRole.ADMIN).findFirst()
                .orElseThrow(() -> new IllegalStateException("Create the bootstrap administrator before seeding synthetic demo data."));
        DoctorPatientAssignment firstAssignment = assignment(doctor, patientOne, admin);
        DoctorPatientAssignment secondAssignment = assignment(doctor, patientTwo, admin);
        consent(firstAssignment, patientOneUser);
        consent(secondAssignment, patientTwoUser);

        requests.save(request(patientOne, doctor, firstAssignment, bloodCount, null));
        TestRequest scheduled = request(patientTwo, doctor, secondAssignment, lipid, technician);
        scheduled.scheduledDate = LocalDate.now().plusDays(1);
        scheduled.scheduledTime = LocalTime.of(9, 30);
        scheduled.status = RequestStatus.SCHEDULED;
        requests.save(scheduled);

        audit.record("DEMO_DATA_SEEDED", admin, null,
                "DEMO_DATA", null, null, java.util.Map.of("syntheticUsers", 4, "catalogueTests", 3, "requests", 2));
    }

    private DoctorPatientAssignment assignment(DoctorProfile doctor, PatientProfile patient, UserAccount admin) {
        DoctorPatientAssignment assignment = new DoctorPatientAssignment();
        assignment.doctor = doctor;
        assignment.patient = patient;
        assignment.assignedAt = AuthService.now();
        assignment.assignedBy = admin;
        return assignments.save(assignment);
    }

    private void consent(DoctorPatientAssignment assignment, UserAccount patientUser) {
        PatientConsent consent = new PatientConsent();
        consent.assignment = assignment;
        consent.consentedBy = patientUser;
        consent.consentTextVersion = consentTextVersion;
        consent.consentedAt = AuthService.now();
        consents.save(consent);
    }

    private TestRequest request(PatientProfile patient, DoctorProfile doctor, DoctorPatientAssignment assignment,
                                MedicalTest test, UserAccount technician) {
        TestRequest request = new TestRequest();
        request.patient = patient;
        request.doctor = doctor;
        request.assignment = assignment;
        request.medicalTest = test;
        request.assignedTechnician = technician;
        request.requestDate = AuthService.now();
        request.status = technician == null ? RequestStatus.REQUESTED : RequestStatus.SCHEDULED;
        return request;
    }

    private UserAccount createUser(String name, String email, UserRole role) {
        UserAccount user = new UserAccount();
        user.name = name;
        user.email = email;
        user.passwordHash = passwordEncoder.encode(demoPassword);
        user.role = role;
        user.createdAt = AuthService.now();
        user.enabled = true;
        user.securityVersion = 0;
        return users.save(user);
    }

    private PatientProfile patient(UserAccount user, int age, String gender, String phone, String address) {
        PatientProfile profile = new PatientProfile();
        profile.user = user;
        profile.age = age;
        profile.gender = gender;
        profile.phone = phone;
        profile.address = address;
        return patients.save(profile);
    }

    private MedicalTest test(String name, String description, String price, String referenceRange) {
        MedicalTest test = new MedicalTest();
        test.name = name;
        test.description = description;
        test.price = new BigDecimal(price);
        test.referenceRange = referenceRange;
        test.active = true;
        return tests.save(test);
    }
}
