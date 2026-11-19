package com.medicore.controller;

import com.medicore.dto.ApiDtos;
import com.medicore.security.MediCorePrincipal;
import com.medicore.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/patients/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiDtos.UserView patient(@AuthenticationPrincipal MediCorePrincipal principal) {
        return accounts.currentUser(principal.userId());
    }

    @PatchMapping("/patients/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiDtos.UserView updatePatient(@AuthenticationPrincipal MediCorePrincipal principal,
                                          @Valid @RequestBody ApiDtos.ProfileUpdateRequest request) {
        return accounts.updatePatient(principal.userId(), request);
    }

    @PatchMapping("/doctors/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiDtos.UserView updateDoctor(@AuthenticationPrincipal MediCorePrincipal principal,
                                         @Valid @RequestBody ApiDtos.DoctorProfileUpdateRequest request) {
        return accounts.updateDoctor(principal.userId(), request);
    }

    @GetMapping("/doctors/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiDtos.UserView doctor(@AuthenticationPrincipal MediCorePrincipal principal) {
        return accounts.currentUser(principal.userId());
    }

    @GetMapping("/doctors/me/patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<ApiDtos.UserView> doctorPatients(@AuthenticationPrincipal MediCorePrincipal principal) {
        return accounts.doctorPatients(principal.userId());
    }

    @GetMapping("/patients/me/doctor-assignments")
    @PreAuthorize("hasRole('PATIENT')")
    public List<ApiDtos.AssignmentView> patientAssignments(@AuthenticationPrincipal MediCorePrincipal principal) {
        return accounts.patientAssignments(principal.userId());
    }

    @PostMapping("/patients/me/doctor-assignments/{assignmentId}/consent")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AssignmentView consent(@AuthenticationPrincipal MediCorePrincipal principal,
                                          @PathVariable Long assignmentId,
                                          @Valid @RequestBody ApiDtos.ConsentRequest request) {
        return accounts.consent(principal.userId(), assignmentId, request);
    }

    @PostMapping("/patients/me/doctor-assignments/{assignmentId}/withdraw-consent")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiDtos.AssignmentView withdrawConsent(@AuthenticationPrincipal MediCorePrincipal principal,
                                                  @PathVariable Long assignmentId) {
        return accounts.withdrawConsent(principal.userId(), assignmentId);
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.UserView> users() { return accounts.adminUsers(); }

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.UserView createUser(@AuthenticationPrincipal MediCorePrincipal principal,
                                       @Valid @RequestBody ApiDtos.CreateUserRequest request) {
        return accounts.createUser(principal.userId(), request);
    }

    @PatchMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.UserView updateUser(@AuthenticationPrincipal MediCorePrincipal principal,
                                       @PathVariable Long userId,
                                       @Valid @RequestBody ApiDtos.UpdateUserRequest request) {
        return accounts.updateUser(principal.userId(), userId, request);
    }

    @GetMapping("/admin/patients")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.UserView> patients() { return accounts.patientsForAdmin(); }

    @GetMapping("/admin/doctors")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.UserView> doctors() { return accounts.doctorsForAdmin(); }

    @PostMapping("/admin/doctor-patient-assignments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AssignmentView assign(@AuthenticationPrincipal MediCorePrincipal principal,
                                         @Valid @RequestBody ApiDtos.AssignmentRequest request) {
        return accounts.createAssignment(principal.userId(), request);
    }

    @GetMapping("/admin/doctor-patient-assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.AssignmentView> assignments() { return accounts.adminAssignments(); }

    @PostMapping("/admin/doctor-patient-assignments/{assignmentId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.AssignmentView revoke(@AuthenticationPrincipal MediCorePrincipal principal,
                                         @PathVariable Long assignmentId) {
        return accounts.revokeAssignment(principal.userId(), assignmentId);
    }
}
