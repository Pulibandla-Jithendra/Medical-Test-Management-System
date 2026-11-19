/* All records in this file are invented, static preview data. No API calls are made. */
window.MediCoreDemo = Object.freeze({
  users: {
    patient: { name: "Nora Patel", initials: "NP", email: "nora.patel@example.test", id: "PT-2048", role: "Patient", avatar: "avatar" },
    doctor: { name: "Dr. Mina Shah", initials: "MS", email: "mina.shah@example.test", id: "DR-014", role: "Doctor", avatar: "avatar-blue" },
    technician: { name: "Jordan Lee", initials: "JL", email: "jordan.lee@example.test", id: "LT-022", role: "Lab Technician", avatar: "avatar-orange" },
    admin: { name: "Avery Morgan", initials: "AM", email: "avery.morgan@example.test", id: "AD-003", role: "Administrator", avatar: "avatar-lilac" }
  },
  patient: {
    metrics: [
      { label: "Active requests", value: "2", foot: "Both are on track", trend: "On track", icon: "clipboard" },
      { label: "Next scheduled test", value: "Nov 21", foot: "Saturday · 9:30 AM", trend: "", icon: "calendar" },
      { label: "Reports available", value: "4", foot: "Your report library", trend: "+1 new", icon: "file" },
      { label: "Profile completeness", value: "86%", foot: "A few details to review", trend: "Good", icon: "person" }
    ],
    requests: [
      { id: "TR-4291", test: "Complete Blood Count", detail: "Ordered by Dr. Mina Shah", date: "Nov 18, 2026", status: "IN_PROGRESS", color: "" },
      { id: "TR-4278", test: "Lipid Panel", detail: "Ordered by Dr. Mina Shah", date: "Nov 16, 2026", status: "SCHEDULED", color: "blue" },
      { id: "TR-4183", test: "Vitamin D", detail: "Ordered by Dr. Mina Shah", date: "Nov 04, 2026", status: "REPORT_GENERATED", color: "orange" }
    ],
    reports: [
      { id: "RP-1084", test: "Vitamin D", date: "Nov 04, 2026", meta: "PDF · 2 pages" },
      { id: "RP-1041", test: "Thyroid Panel", date: "Oct 20, 2026", meta: "PDF · 2 pages" }
    ]
  },
  doctor: {
    metrics: [
      { label: "Authorized patients", value: "24", foot: "Active assignment + consent", trend: "Current", icon: "users" },
      { label: "Open requests", value: "8", foot: "Across your active patients", trend: "+2 this week", icon: "clipboard" },
      { label: "Reports ready", value: "5", foot: "Available to review", trend: "Updated", icon: "file" },
      { label: "Tests in catalogue", value: "32", foot: "Active test definitions", trend: "View", icon: "flask" }
    ],
    patients: [
      { initials: "NP", name: "Nora Patel", id: "PT-2048", detail: "2 active requests", avatar: "avatar" },
      { initials: "TB", name: "Theo Brooks", id: "PT-1935", detail: "Report ready · Nov 17", avatar: "avatar-blue" },
      { initials: "EC", name: "Elena Cruz", id: "PT-2071", detail: "1 scheduled test", avatar: "avatar-rose" }
    ],
    requests: [
      { id: "TR-4291", patient: "Nora Patel", test: "Complete Blood Count", date: "Nov 18, 2026", status: "IN_PROGRESS" },
      { id: "TR-4284", patient: "Theo Brooks", test: "Metabolic Panel", date: "Nov 17, 2026", status: "COMPLETED" },
      { id: "TR-4278", patient: "Nora Patel", test: "Lipid Panel", date: "Nov 16, 2026", status: "SCHEDULED" }
    ]
  },
  technician: {
    metrics: [
      { label: "Assigned to you", value: "5", foot: "Your current work queue", trend: "Assigned", icon: "clipboard" },
      { label: "In progress", value: "2", foot: "Active processing", trend: "In lab", icon: "activity" },
      { label: "Due today", value: "3", foot: "Scheduled for today", trend: "Today", icon: "calendar" },
      { label: "Completed today", value: "8", foot: "Results entered", trend: "Complete", icon: "check" }
    ],
    queue: [
      { id: "TR-4291", initials: "NP", patient: "Nora Patel", test: "Complete Blood Count", time: "09:30 AM", status: "IN_PROGRESS", avatar: "avatar" },
      { id: "TR-4302", initials: "EC", patient: "Elena Cruz", test: "Thyroid Panel", time: "10:15 AM", status: "SCHEDULED", avatar: "avatar-rose" },
      { id: "TR-4307", initials: "TB", patient: "Theo Brooks", test: "Metabolic Panel", time: "11:00 AM", status: "SCHEDULED", avatar: "avatar-blue" },
      { id: "TR-4310", initials: "SK", patient: "Sam Kim", test: "Lipid Panel", time: "01:30 PM", status: "SCHEDULED", avatar: "avatar-orange" }
    ]
  },
  admin: {
    metrics: [
      { label: "Patient accounts", value: "1,248", foot: "Synthetic demo count", trend: "+6.4%", icon: "users" },
      { label: "Requests this month", value: "386", foot: "Across all statuses", trend: "+8.2%", icon: "clipboard" },
      { label: "Active technicians", value: "12", foot: "4 assigned today", trend: "On duty", icon: "activity" },
      { label: "Reports generated", value: "342", foot: "This month", trend: "+5.1%", icon: "file" }
    ],
    requests: [
      { id: "TR-4310", patient: "Sam Kim", test: "Lipid Panel", tech: "Jordan Lee", date: "Nov 19 · 01:30 PM", status: "SCHEDULED" },
      { id: "TR-4307", patient: "Theo Brooks", test: "Metabolic Panel", tech: "Jordan Lee", date: "Nov 19 · 11:00 AM", status: "SCHEDULED" },
      { id: "TR-4291", patient: "Nora Patel", test: "Complete Blood Count", tech: "Jordan Lee", date: "Nov 19 · 09:30 AM", status: "IN_PROGRESS" }
    ],
    activity: [
      { icon: "calendar", title: "Request scheduled", detail: "TR-4310 · Assigned to Jordan Lee", time: "12 min ago", tone: "tone-green" },
      { icon: "check", title: "Result completed", detail: "TR-4284 · Result version 1", time: "38 min ago", tone: "tone-blue" },
      { icon: "file", title: "Report generated", detail: "RP-1084 · TR-4183", time: "1 hr ago", tone: "tone-purple" }
    ]
  }
});

