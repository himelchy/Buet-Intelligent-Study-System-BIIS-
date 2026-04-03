package com.example;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class SampleData {
    private SampleData() {
    }

    public static AppState defaultState() {
        ObservableList<StudentAccount> studentSeeds = buildStudentAccounts();
        ObservableList<CourseSelection> courseSelections = buildCourseSelections();
        ObservableList<TeacherAccount> teacherSeeds = buildTeacherAccounts();
        ObservableList<FacultyProfile> facultyProfiles = buildFacultyProfiles();
        ObservableList<AcademicDeadline> deadlineSeeds = buildDeadlineSeeds(teacherSeeds);
        ObservableList<StudentAttendanceRecord> attendanceSeeds = buildAttendanceSeeds(studentSeeds, teacherSeeds);

        validateFacultyCoverage(courseSelections, facultyProfiles);

        DatabaseService databaseService = DatabaseService.createDefault();
        databaseService.seedDefaults(studentSeeds, teacherSeeds, facultyProfiles, deadlineSeeds, attendanceSeeds);

        StudentProfile defaultStudent = databaseService.firstStudentProfile();

        return new AppState(
                databaseService,
                defaultStudent,
                courseSelections,
                buildTeacherWorkspaceTemplates(teacherSeeds),
                buildAuthorityNotices()
        );
    }

    public static ObservableList<StudentAccount> buildStudentAccounts() {
        ObservableList<StudentAccount> students = FXCollections.observableArrayList();
        Random random = new Random(12052026L);

        for (int index = 1; index <= 50; index++) {
            String studentId = "stu" + index;
            students.add(new StudentAccount(
                    new StudentProfile(
                            studentId,
                            studentId,
                            "CSE",
                            "Level 1 Term 2",
                            randomCg(random),
                            studentId + "@buet.ac.bd",
                            samplePhone(index)
                    ),
                    "1234"
            ));
        }

        return students;
    }

    private static String randomCg(Random random) {
        int hundredths = 220 + random.nextInt(181);
        return String.format(Locale.US, "%.2f", hundredths / 100.0);
    }

    private static String samplePhone(int index) {
        return String.format(Locale.US, "+8801700%06d", index);
    }

    public static ObservableList<CourseSelection> buildCourseSelections() {
        return FXCollections.observableArrayList(
                new CourseSelection(new CourseDetail(
                        "CSE-110",
                        "Structured Programming",
                        3,
                        "Dr. Farzana Rahman",
                        "Build a strong foundation in structured programming with C and problem decomposition.",
                        "Dr. Farzana Rahman focuses on clean coding habits and disciplined problem solving.",
                        List.of("Week 6 slides", "Problem set 3 solutions", "Coding style guide"),
                        List.of("CSE-110 Week 6 Slides.pdf", "CSE-110 Assignment 3.pdf")
                ), true),
                new CourseSelection(new CourseDetail(
                        "CSE-111",
                        "Structured Programming Lab",
                        1,
                        "Engr. T. Hasan",
                        "Practice coding fundamentals, debugging, and lab assessments with weekly tasks.",
                        "Engr. T. Hasan mentors students with hands-on lab guidance and review sessions.",
                        List.of("Lab sheet 5", "Lab viva checklist"),
                        List.of("CSE-111 Lab Sheet 5.pdf")
                ), true),
                new CourseSelection(new CourseDetail(
                        "EEE-103",
                        "Basic Electrical Circuits",
                        3,
                        "Dr. M. Karim",
                        "Understand circuit analysis, electrical laws, and practical applications.",
                        "Dr. M. Karim emphasizes conceptual clarity and real-world circuit examples.",
                        List.of("Circuit analysis notes", "Tutorial 2 solved"),
                        List.of("EEE-103 Week 5 Notes.pdf")
                ), true),
                new CourseSelection(new CourseDetail(
                        "EEE-104",
                        "Basic Electrical Circuits Lab",
                        1,
                        "Engr. S. Nabila",
                        "Apply electrical circuit theory through guided experiments and reports.",
                        "Engr. S. Nabila leads lab experiments with a focus on safety and accuracy.",
                        List.of("Lab safety instructions", "Experiment 3 manual"),
                        List.of("EEE-104 Experiment 3.pdf")
                ), false),
                new CourseSelection(new CourseDetail(
                        "MAT-105",
                        "Engineering Mathematics",
                        3,
                        "Dr. A. Chowdhury",
                        "Strengthen mathematical tools for engineering analysis and modeling.",
                        "Dr. A. Chowdhury specializes in applied mathematics for engineering students.",
                        List.of("Matrix methods cheat sheet", "Problem set 4"),
                        List.of("MAT-105 Problem Set 4.pdf")
                ), true),
                new CourseSelection(new CourseDetail(
                        "PHY-109",
                        "Physics Fundamentals",
                        3,
                        "Dr. R. Hossain",
                        "Explore physics principles that underpin core engineering concepts.",
                        "Dr. R. Hossain highlights intuitive explanations and problem practice.",
                        List.of("Lab safety rules", "Oscillations summary"),
                        List.of("PHY-109 Lab Manual.pdf")
                ), true),
                new CourseSelection(new CourseDetail(
                        "HUM-101",
                        "Technical Communication",
                        2,
                        "Ms. L. Akter",
                        "Improve communication skills for reports, presentations, and teamwork.",
                        "Ms. L. Akter coaches students on professional writing and presentation skills.",
                        List.of("Presentation rubric", "Report outline"),
                        List.of("HUM-101 Presentation Rubric.pdf")
                ), false)
        );
    }

    public static ObservableList<TeacherAccount> buildTeacherAccounts() {
        TeacherAccount farzana = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1001",
                                "Dr. Farzana Rahman",
                                "CSE",
                                "Professor",
                                "farzana.rahman@cse.buet.ac.bd",
                                "Dept. CSE, Room 508",
                                "+8801888000001"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Sun", "09:30-10:50", "CSE-110 Structured Programming", "A", "Room 401"),
                                new TeacherRoutineRow("Mon", "13:00-14:20", "CSE-110 Structured Programming", "B", "Room 402"),
                                new TeacherRoutineRow("Tue", "10:00-11:20", "CSE-205 Data Structures", "A", "Room 405"),
                                new TeacherRoutineRow("Wed", "11:30-12:50", "CSE-205 Data Structures", "B", "Room 406")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-10", "CSE-110", "A", "Uploaded"),
                                new AttendanceEntry("2026-02-12", "CSE-110", "B", "Uploaded"),
                                new AttendanceEntry("2026-02-17", "CSE-205", "A", "Pending")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("CSE-110", "Week 6 Slides", "PDF", "week-6-slides.pdf", "Student", "2026-02-09"),
                                new UploadedResource("CSE-205", "Graph Tutorial", "PPT", "graph-tutorial.pptx", "Student", "2026-02-14")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        TeacherAccount hasan = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1003",
                                "Engr. T. Hasan",
                                "CSE",
                                "Lecturer",
                                "t.hasan@cse.buet.ac.bd",
                                "Dept. CSE, Room 210",
                                "+8801888000003"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Sun", "14:00-16:00", "CSE-111 Structured Programming Lab", "A", "Lab 3"),
                                new TeacherRoutineRow("Tue", "14:00-16:00", "CSE-111 Structured Programming Lab", "B", "Lab 4")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-11", "CSE-111", "A", "Uploaded"),
                                new AttendanceEntry("2026-02-18", "CSE-111", "B", "Pending")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("CSE-111", "Lab Sheet 5", "PDF", "lab-sheet-5.pdf", "Student", "2026-02-11")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        TeacherAccount karim = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1002",
                                "Dr. M. Karim",
                                "EEE",
                                "Associate Professor",
                                "m.karim@eee.buet.ac.bd",
                                "Dept. EEE, Room 312",
                                "+8801888000002"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Sun", "11:00-12:20", "EEE-103 Basic Electrical Circuits", "A", "Room 403"),
                                new TeacherRoutineRow("Tue", "14:00-16:00", "EEE-104 Circuits Lab", "A", "Lab 1"),
                                new TeacherRoutineRow("Thu", "09:30-10:50", "EEE-103 Basic Electrical Circuits", "B", "Room 407")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-08", "EEE-103", "A", "Uploaded"),
                                new AttendanceEntry("2026-02-15", "EEE-103", "B", "Uploaded"),
                                new AttendanceEntry("2026-02-18", "EEE-104", "A", "Pending")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("EEE-103", "Circuit Laws Notes", "PDF", "circuit-laws-notes.pdf", "Student", "2026-02-08"),
                                new UploadedResource("EEE-104", "Experiment 3 Sheet", "DOC", "experiment-3-sheet.docx", "Student", "2026-02-18")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        TeacherAccount nabila = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1004",
                                "Engr. S. Nabila",
                                "EEE",
                                "Lecturer",
                                "s.nabila@eee.buet.ac.bd",
                                "Dept. EEE, Room 214",
                                "+8801888000004"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Tue", "14:00-16:00", "EEE-104 Basic Electrical Circuits Lab", "A", "Lab 1")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-18", "EEE-104", "A", "Pending")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("EEE-104", "Experiment 3 Manual", "PDF", "experiment-3-manual.pdf", "Student", "2026-02-18")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        TeacherAccount chowdhury = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1005",
                                "Dr. A. Chowdhury",
                                "Mathematics",
                                "Professor",
                                "a.chowdhury@math.buet.ac.bd",
                                "Dept. Mathematics, Room 402",
                                "+8801888000005"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Mon", "08:00-09:20", "MAT-105 Engineering Mathematics", "A", "Room 305"),
                                new TeacherRoutineRow("Wed", "08:00-09:20", "MAT-105 Engineering Mathematics", "B", "Room 306")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-16", "MAT-105", "A", "Uploaded")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("MAT-105", "Problem Set 4", "PDF", "problem-set-4.pdf", "Student", "2026-02-16")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        TeacherAccount hossain = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1006",
                                "Dr. R. Hossain",
                                "Physics",
                                "Associate Professor",
                                "r.hossain@physics.buet.ac.bd",
                                "Dept. Physics, Room 118",
                                "+8801888000006"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Tue", "09:30-10:50", "PHY-109 Physics Fundamentals", "A", "Room 203"),
                                new TeacherRoutineRow("Thu", "09:30-10:50", "PHY-109 Physics Fundamentals", "B", "Room 204")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-17", "PHY-109", "A", "Uploaded")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("PHY-109", "Oscillations Summary", "PDF", "oscillations-summary.pdf", "Student", "2026-02-17")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        TeacherAccount akter = new TeacherAccount(
                new TeacherWorkspace(
                        new TeacherProfile(
                                "TCH-1007",
                                "Ms. L. Akter",
                                "Humanities",
                                "Lecturer",
                                "l.akter@hum.buet.ac.bd",
                                "Dept. Humanities, Room 108",
                                "+8801888000007"
                        ),
                        FXCollections.observableArrayList(
                                new TeacherRoutineRow("Mon", "11:30-12:50", "HUM-101 Technical Communication", "A", "Room 109")
                        ),
                        FXCollections.observableArrayList(
                                new AttendanceEntry("2026-02-09", "HUM-101", "A", "Uploaded")
                        ),
                        FXCollections.observableArrayList(
                                new UploadedResource("HUM-101", "Presentation Rubric", "PDF", "presentation-rubric.pdf", "Student", "2026-02-09")
                        ),
                        FXCollections.observableArrayList()
                ),
                "teacher123"
        );

        return FXCollections.observableArrayList(
                farzana,
                karim,
                hasan,
                nabila,
                chowdhury,
                hossain,
                akter);
    }

    public static ObservableList<String> buildAuthorityNotices() {
        return FXCollections.observableArrayList(
                "Upload continuous assessment marks by 28 February.",
                "Lab attendance must be uploaded within 48 hours of each class.",
                "Department review meeting on Monday at 11:00 AM in Dean's conference room.",
                "Ensure all course resources are tagged with course code and week number."
        );
    }

    public static ObservableList<FacultyProfile> buildFacultyProfiles() {
        return FXCollections.observableArrayList(
                new FacultyProfile("Dr. Farzana Rahman", "PhD in Computer Science", "CSE", "farzana.rahman@cse.buet.ac.bd"),
                new FacultyProfile("Engr. T. Hasan", "MSc in Computer Science and Engineering", "CSE", "t.hasan@cse.buet.ac.bd"),
                new FacultyProfile("Dr. M. Karim", "PhD in Electrical Engineering", "EEE", "m.karim@eee.buet.ac.bd"),
                new FacultyProfile("Engr. S. Nabila", "MSc in Electrical Engineering", "EEE", "s.nabila@eee.buet.ac.bd"),
                new FacultyProfile("Dr. A. Chowdhury", "PhD in Applied Mathematics", "Mathematics", "a.chowdhury@math.buet.ac.bd"),
                new FacultyProfile("Dr. R. Hossain", "PhD in Physics", "Physics", "r.hossain@physics.buet.ac.bd"),
                new FacultyProfile("Ms. L. Akter", "MA in English", "Humanities", "l.akter@hum.buet.ac.bd")
        );
    }

    private static ObservableList<TeacherWorkspace> buildTeacherWorkspaceTemplates(ObservableList<TeacherAccount> teacherSeeds) {
        return FXCollections.observableArrayList(
                teacherSeeds.stream()
                        .map(TeacherAccount::workspace)
                        .toList()
        );
    }

    private static ObservableList<AcademicDeadline> buildDeadlineSeeds(ObservableList<TeacherAccount> teacherSeeds) {
        ObservableList<AcademicDeadline> deadlines = FXCollections.observableArrayList();
        teacherSeeds.forEach(account -> {
            TeacherProfile profile = account.workspace().profile();
            switch (profile.id()) {
                case "TCH-1001" -> {
                    deadlines.add(sampleDeadline(profile, "CSE-110", "CT", "CT 1 - Loops and Arrays",
                            "In-class class test covering loops, arrays, and dry-run tracing.", 2026, 4, 5, 13, 30));
                    deadlines.add(sampleDeadline(profile, "CSE-110", "Assignment", "Assignment 3 - Functions",
                            "Submit a clean C solution with dry-run notes.", 2026, 4, 7, 23, 59));
                    deadlines.add(sampleDeadline(profile, "CSE-205", "Quiz", "Quiz 2 - Trees and Heaps",
                            "Short quiz covering tree traversals and binary heaps.", 2026, 4, 10, 10, 30));
                }
                case "TCH-1003" -> deadlines.add(sampleDeadline(profile, "CSE-111", "Exam",
                        "Lab Evaluation - Debugging Sprint",
                        "Bring completed lab notebook and working source files.", 2026, 4, 6, 14, 0));
                case "TCH-1002" -> deadlines.add(sampleDeadline(profile, "EEE-103", "Assignment",
                        "Assignment 2 - Mesh Analysis",
                        "Solve all tutorial problems and attach circuit diagrams.", 2026, 4, 8, 21, 0));
                case "TCH-1005" -> deadlines.add(sampleDeadline(profile, "MAT-105", "Quiz",
                        "Quiz 1 - Matrices and Determinants",
                        "Closed-book in-class quiz on determinant properties.", 2026, 4, 5, 11, 0));
                case "TCH-1006" -> deadlines.add(sampleDeadline(profile, "PHY-109", "Assignment",
                        "Assignment 1 - Oscillations Sheet",
                        "Upload scanned solutions before the evening slot closes.", 2026, 4, 4, 18, 30));
                default -> {
                }
            }
        });
        return deadlines;
    }

    private static ObservableList<StudentAttendanceRecord> buildAttendanceSeeds(
            ObservableList<StudentAccount> studentSeeds,
            ObservableList<TeacherAccount> teacherSeeds) {
        ObservableList<StudentAttendanceRecord> records = FXCollections.observableArrayList();

        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1001"), "CSE-110", "A", "2026-02-03", studentSeeds, 0);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1001"), "CSE-110", "A", "2026-02-10", studentSeeds, 1);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1001"), "CSE-110", "A", "2026-02-17", studentSeeds, 2);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1003"), "CSE-111", "A", "2026-02-04", studentSeeds, 3);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1003"), "CSE-111", "A", "2026-02-11", studentSeeds, 4);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1002"), "EEE-103", "A", "2026-02-05", studentSeeds, 5);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1002"), "EEE-103", "A", "2026-02-12", studentSeeds, 6);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1005"), "MAT-105", "A", "2026-02-02", studentSeeds, 7);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1005"), "MAT-105", "A", "2026-02-09", studentSeeds, 8);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1006"), "PHY-109", "A", "2026-02-06", studentSeeds, 9);
        addAttendanceSession(records, teacherProfileById(teacherSeeds, "TCH-1006"), "PHY-109", "A", "2026-02-13", studentSeeds, 10);

        return records;
    }

    private static TeacherProfile teacherProfileById(ObservableList<TeacherAccount> teacherSeeds, String teacherId) {
        return teacherSeeds.stream()
                .map(TeacherAccount::workspace)
                .map(TeacherWorkspace::profile)
                .filter(profile -> profile.id().equalsIgnoreCase(teacherId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing teacher seed for " + teacherId));
    }

    private static void addAttendanceSession(ObservableList<StudentAttendanceRecord> records,
            TeacherProfile teacher,
            String courseCode,
            String section,
            String attendanceDate,
            ObservableList<StudentAccount> studentSeeds,
            int sessionOffset) {
        long markedAt = LocalDateTime.parse(attendanceDate + "T09:30")
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        studentSeeds.forEach(studentAccount -> {
            StudentProfile student = studentAccount.profile();
            records.add(new StudentAttendanceRecord(
                    0L,
                    teacher.id(),
                    courseCode,
                    section,
                    attendanceDate,
                    student.roll(),
                    student.name(),
                    sampleAttendance(student.roll(), courseCode, attendanceDate, sessionOffset),
                    markedAt));
        });
    }

    private static boolean sampleAttendance(String studentRoll, String courseCode, String attendanceDate, int sessionOffset) {
        int rollNumber;
        try {
            rollNumber = Integer.parseInt(studentRoll.replaceAll("\\D", ""));
        } catch (NumberFormatException ex) {
            rollNumber = 0;
        }

        int baseRate = switch (rollNumber % 6) {
            case 0 -> 55;
            case 1 -> 68;
            case 2 -> 74;
            default -> 86;
        };
        int variation = Math.abs(Objects.hash(studentRoll, courseCode, attendanceDate, sessionOffset)) % 21;
        return variation < baseRate;
    }

    private static AcademicDeadline sampleDeadline(TeacherProfile profile,
            String courseCode,
            String type,
            String title,
            String details,
            int year,
            int month,
            int day,
            int hour,
            int minute) {
        long dueAt = LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return new AcademicDeadline(
                profile.id(),
                profile.name(),
                courseCode,
                type,
                title,
                details,
                dueAt);
    }

    private static void validateFacultyCoverage(ObservableList<CourseSelection> courseSelections,
                                                ObservableList<FacultyProfile> facultyProfiles) {
        Set<String> registeredFaculty = facultyProfiles.stream()
                .map(profile -> profile.name().toUpperCase())
                .collect(Collectors.toSet());

        List<String> missingProfiles = courseSelections.stream()
                .map(CourseSelection::instructor)
                .filter(instructor -> !registeredFaculty.contains(instructor.toUpperCase()))
                .distinct()
                .toList();

        if (!missingProfiles.isEmpty()) {
            throw new IllegalStateException("Missing faculty profiles for: " + String.join(", ", missingProfiles));
        }
    }
}
