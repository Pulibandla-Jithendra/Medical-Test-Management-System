package com.medicore.service;

import com.medicore.dto.ApiDtos;
import com.medicore.entity.DoctorPatientAssignment;
import com.medicore.entity.DoctorProfile;
import com.medicore.entity.PatientConsent;
import com.medicore.entity.PatientProfile;
import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import com.medicore.exception.DomainException;
import com.medicore.repository.DoctorPatientAssignmentRepository;
import com.medicore.repository.DoctorProfileRepository;
import com.medicore.repository.PatientConsentRepository;
import com.medicore.repository.PatientProfileRepository;
import com.medicore.repository.RefreshTokenRepository;
import com.medicore.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserAccountRepository users;
    private final PatientProfileRepository patients;
    private final DoctorProfileRepository doctors;
    private final DoctorPatientAssignmentRepository assignments;
    private final PatientConsentRepository consents;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final String consentTextVersion;

    public AccountService(UserAccountRepository users, PatientProfileRepository patients,
                          DoctorProfileRepository doctors, DoctorPatientAssignmentRepository assignments,
                          PatientConsentRepository consents, RefreshTokenRepository refreshTokens,
                          PasswordEncoder passwordEncoder, AuditService audit,
                          @Value("${medicore.consent.text-version:v1}") String consentTextVersion) {
        this.users = users;
        this.patients = patients;
        this.doctors = doctors;
        this.assignments = assignments;
        this.consents = consents;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.consentTextVersion = consentTextVersion;
    }

    @Transactional(readOnly = true)
    public ApiDtos.UserView currentUser(Long userId) { return view(requireUser(userId)); }

    @Transactional
    public ApiDtos.UserView updatePatient(Long userId, ApiDtos.ProfileUpdateRequest input) {
        UserAccount user = requireRole(userId, UserRole.PATIENT);
        PatientProfile profile = patients.findByUser_Id(userId).orElseThrow(() -> DomainException.notFound("Patient profile not found."));
        user.name = input.name().trim();
        profile.age = input.age();
        profile.gender = AuthService.normalized(input.gender());
        profile.phone = AuthService.normalized(input.phone());
        profile.address = AuthService.normalized(input.address());
        users.save(user);
        patients.save(profile);
        audit.record("PATIENT_PROFILE_UPDATED", user, user, "PATIENT", profile.id, null, Map.of("updated", true));
        return view(user);
    }

    @Transactional
    public ApiDtos.UserView updateDoctor(Long userId, ApiDtos.DoctorProfileUpdateRequest input) {
        UserAccount user = requireRole(userId, UserRole.DOCTOR);
        DoctorProfile profile = doctors.findByUser_Id(userId).orElseThrow(() -> DomainException.notFound("Doctor profile not found."));
        user.name = input.name().trim();
        profile.specialization = input.specialization().trim();
        users.save(user);
        doctors.save(profile);
        audit.record("DOCTOR_PROFILE_UPDATED", user, user, "DOCTOR", profile.id, null, Map.of("updated", true));
        return view(user);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.UserView> adminUsers() {
        return users.findAll().stream().map(this::view).toList();
    }

    @Transactional
    public ApiDtos.UserView createUser(Long actorId, ApiDtos.CreateUserRequest input) {
        UserAccount actor = requireRole(actorId, UserRole.ADMIN);
        AuthService.requireBcryptSafePassword(input.password());
        String email = AuthService.normalizeEmail(input.email());
        if (users.findByEmailIgnoreCase(email).isPresent()) throw DomainException.conflict("An account with that email already exists.");
        UserAccount user = new UserAccount();
        user.name = input.name().trim();
        user.email = email;
        user.passwordHash = passwordEncoder.encode(input.password());
        user.role = input.role();
        user.createdAt = AuthService.now();
        user.enabled = true;
        user.securityVersion = 0;
        users.save(user);
        ensureRoleProfile(user, input.specialization(), input.age(), input.gender(), input.phone(), input.address());
        audit.record("USER_CREATED", actor, user, "USER", user.id, null, Map.of("role", user.role.name(), "enabled", true));
        return view(user);
    }

    @Transactional
    public ApiDtos.UserView updateUser(Long actorId, Long targetId, ApiDtos.UpdateUserRequest input) {
        UserAccount actor = requireRole(actorId, UserRole.ADMIN);
        UserAccount user = requireUser(targetId);
        Map<String, Object> before = Map.of("role", user.role.name(), "enabled", user.enabled);
        boolean securityChange = false;
        if (input.role() != null && input.role() != user.role) {
            user.role = input.role();
            securityChange = true;
        }
        if (input.enabled() != null && input.enabled() != user.enabled) {
            user.enabled = input.enabled();
            securityChange = true;
        }
        if (securityChange) {
            user.securityVersion++;
            refreshTokens.revokeAllForUser(user.id, AuthService.now());
        }
        users.save(user);
        ensureRoleProfile(user, input.specialization(), input.age(), input.gender(), input.phone(), input.address());
        audit.record("USER_SECURITY_UPDATED", actor, user, "USER", user.id, before,
                Map.of("role", user.role.name(), "enabled", user.enabled, "securityVersion", user.securityVersion));
        return view(user);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.UserView> patientsForAdmin() { return patients.findAll().stream().map(p -> view(p.user)).toList(); }
    @Transactional(readOnly = true)
    public List<ApiDtos.UserView> doctorsForAdmin() { return doctors.findAll().stream().map(d -> view(d.user)).toList(); }

    @Transactional
    public ApiDtos.AssignmentView createAssignment(Long actorId, ApiDtos.AssignmentRequest input) {
        UserAccount actor = requireRole(actorId, UserRole.ADMIN);
        DoctorProfile doctor = doctors.findByIdForUpdate(input.doctorId()).orElseThrow(() -> DomainException.notFound("Doctor profile not found."));
        PatientProfile patient = patients.findByIdForUpdate(input.patientId()).orElseThrow(() -> DomainException.notFound("Patient profile not found."));
        if (!doctor.user.enabled || doctor.user.role != UserRole.DOCTOR) throw DomainException.invalid("The doctor account is not enabled.");
        if (!patient.user.enabled || patient.user.role != UserRole.PATIENT) throw DomainException.invalid("The patient account is not enabled.");
        if (assignments.existsByDoctor_IdAndPatient_IdAndRevokedAtIsNull(doctor.id, patient.id)) {
            throw DomainException.conflict("An active assignment already exists for this doctor and patient.");
        }
        DoctorPatientAssignment assignment = new DoctorPatientAssignment();
        assignment.doctor = doctor;
        assignment.patient = patient;
        assignment.assignedBy = actor;
        assignment.assignedAt = AuthService.now();
        assignments.save(assignment);
        audit.record("DOCTOR_PATIENT_ASSIGNED", actor, patient.user, "DOCTOR_PATIENT_ASSIGNMENT", assignment.id,
                null, Map.of("doctorId", doctor.id, "patientId", patient.id));
        return assignmentView(assignment);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.AssignmentView> adminAssignments() {
        return assignments.findAll().stream().map(this::assignmentView).toList();
    }

    @Transactional
    public ApiDtos.AssignmentView revokeAssignment(Long actorId, Long assignmentId) {
        UserAccount actor = requireRole(actorId, UserRole.ADMIN);
        DoctorPatientAssignment assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> DomainException.notFound("Assignment not found."));
        if (assignment.revokedAt != null) throw DomainException.conflict("The assignment has already been revoked.");
        assignment.revokedAt = AuthService.now();
        assignment.revokedBy = actor;
        assignments.save(assignment);
        audit.record("DOCTOR_PATIENT_ASSIGNMENT_REVOKED", actor, assignment.patient.user,
                "DOCTOR_PATIENT_ASSIGNMENT", assignment.id, Map.of("revokedAt", "active"),
                Map.of("revokedAt", assignment.revokedAt.toString()));
        return assignmentView(assignment);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.AssignmentView> patientAssignments(Long patientUserId) {
        requireRole(patientUserId, UserRole.PATIENT);
        return assignments.findAllByPatient_User_IdOrderByAssignedAtDesc(patientUserId).stream().map(this::assignmentView).toList();
    }

    @Transactional
    public ApiDtos.AssignmentView consent(Long patientUserId, Long assignmentId, ApiDtos.ConsentRequest input) {
        UserAccount patientUser = requireRole(patientUserId, UserRole.PATIENT);
        DoctorPatientAssignment assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> DomainException.notFound("Assignment not found."));
        if (!assignment.patient.user.id.equals(patientUserId) || assignment.revokedAt != null) {
            throw DomainException.forbidden("Consent can only be recorded for your active assignment.");
        }
        if (!consentTextVersion.equals(input.consentTextVersion().trim())) {
            throw DomainException.invalid("The consent text version is no longer current. Reload the page and review the consent wording.");
        }
        if (consents.existsByAssignment_IdAndRevokedAtIsNull(assignmentId)) throw DomainException.conflict("Consent is already active for this assignment.");
        PatientConsent consent = new PatientConsent();
        consent.assignment = assignment;
        consent.consentedBy = patientUser;
        consent.consentTextVersion = input.consentTextVersion().trim();
        consent.consentedAt = AuthService.now();
        consents.save(consent);
        audit.record("PATIENT_CONSENT_GRANTED", patientUser, patientUser, "PATIENT_CONSENT", consent.id, null,
                Map.of("assignmentId", assignmentId, "textVersion", consent.consentTextVersion));
        return assignmentView(assignment);
    }

    @Transactional
    public ApiDtos.AssignmentView withdrawConsent(Long patientUserId, Long assignmentId) {
        UserAccount patientUser = requireRole(patientUserId, UserRole.PATIENT);
        DoctorPatientAssignment assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> DomainException.notFound("Assignment not found."));
        if (!assignment.patient.user.id.equals(patientUserId)) throw DomainException.forbidden("This assignment belongs to another patient.");
        PatientConsent consent = consents.findFirstByAssignment_IdAndRevokedAtIsNull(assignmentId)
                .orElseThrow(() -> DomainException.conflict("There is no active consent to withdraw."));
        consent.revokedAt = AuthService.now();
        consent.revokedBy = patientUser;
        consents.save(consent);
        audit.record("PATIENT_CONSENT_WITHDRAWN", patientUser, patientUser, "PATIENT_CONSENT", consent.id,
                Map.of("revokedAt", "active"), Map.of("revokedAt", consent.revokedAt.toString()));
        return assignmentView(assignment);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.UserView> doctorPatients(Long doctorUserId) {
        requireRole(doctorUserId, UserRole.DOCTOR);
        return assignments.findAllByDoctor_User_IdAndRevokedAtIsNullOrderByAssignedAtDesc(doctorUserId).stream()
                .filter(a -> consents.findFirstByAssignment_IdAndRevokedAtIsNull(a.id)
                        .filter(c -> c.consentedBy.id.equals(a.patient.user.id)).isPresent())
                .map(a -> view(a.patient.user)).toList();
    }

    @Transactional(readOnly = true)
    public boolean doctorCanAccess(Long doctorUserId, DoctorPatientAssignment assignment) {
        return assignment != null && assignment.doctor.user.id.equals(doctorUserId) && assignment.revokedAt == null
                && assignment.doctor.user.enabled && assignment.patient.user.enabled
                && assignment.doctor.user.role == UserRole.DOCTOR && assignment.patient.user.role == UserRole.PATIENT
                && consents.findFirstByAssignment_IdAndRevokedAtIsNull(assignment.id)
                .filter(c -> c.consentedBy.id.equals(assignment.patient.user.id)).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean patientOwns(Long patientUserId, PatientProfile patient) {
        return patient != null && patient.user.id.equals(patientUserId);
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> DomainException.notFound("User not found."));
    }

    @Transactional(readOnly = true)
    public UserAccount requireRole(Long id, UserRole role) {
        UserAccount user = requireUser(id);
        if (user.role != role || !user.enabled) throw DomainException.forbidden("The account does not have the required role.");
        return user;
    }

    private void ensureRoleProfile(UserAccount user, String specialization, Integer age, String gender, String phone, String address) {
        if (user.role == UserRole.PATIENT) {
            PatientProfile profile = patients.findByUser_Id(user.id).orElseGet(PatientProfile::new);
            profile.user = user;
            profile.age = age == null ? profile.age : age;
            profile.gender = gender == null ? profile.gender : AuthService.normalized(gender);
            profile.phone = phone == null ? profile.phone : AuthService.normalized(phone);
            profile.address = address == null ? profile.address : AuthService.normalized(address);
            patients.save(profile);
        } else if (user.role == UserRole.DOCTOR) {
            DoctorProfile profile = doctors.findByUser_Id(user.id).orElseGet(DoctorProfile::new);
            if (specialization != null && !specialization.isBlank()) profile.specialization = specialization.trim();
            if (profile.specialization == null || profile.specialization.isBlank()) throw DomainException.invalid("Doctor specialization is required.");
            profile.user = user;
            doctors.save(profile);
        }
    }

    private ApiDtos.UserView view(UserAccount user) {
        PatientProfile patient = patients.findByUser_Id(user.id).orElse(null);
        DoctorProfile doctor = doctors.findByUser_Id(user.id).orElse(null);
        return new ApiDtos.UserView(user.id, patient == null ? null : patient.id, doctor == null ? null : doctor.id,
                user.name, user.email, user.role, user.enabled,
                patient == null ? null : patient.age, patient == null ? null : patient.gender,
                patient == null ? null : patient.phone, patient == null ? null : patient.address,
                doctor == null ? null : doctor.specialization);
    }

    private ApiDtos.AssignmentView assignmentView(DoctorPatientAssignment assignment) {
        boolean activeConsent = consents.existsByAssignment_IdAndRevokedAtIsNull(assignment.id);
        return new ApiDtos.AssignmentView(assignment.id, assignment.doctor.id, assignment.doctor.user.name,
                assignment.patient.id, assignment.assignedAt, assignment.revokedAt, activeConsent && assignment.revokedAt == null);
    }
}
