package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class DatabaseService {
    private final String jdbcUrl;

    private DatabaseService(Path databasePath) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        loadDriver();
        initializeSchema();
    }

    public static DatabaseService createDefault() {
        try {
            Path databaseDirectory = Path.of(System.getProperty("user.dir"), "data");
            Files.createDirectories(databaseDirectory);
            return new DatabaseService(databaseDirectory.resolve("biss.db"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare the database directory.", ex);
        }
    }

    public void seedDefaults(List<StudentAccount> studentSeeds,
            List<TeacherAccount> teacherSeeds,
            List<FacultyProfile> facultySeeds,
            List<AcademicDeadline> deadlineSeeds,
            List<StudentAttendanceRecord> attendanceSeeds) {
        studentSeeds.forEach(seed -> seedStudent(seed.profile(), seed.password()));
        teacherSeeds.forEach(seed -> seedTeacher(seed.workspace().profile(), seed.password()));
        facultySeeds.forEach(this::upsertFacultyProfile);
        deadlineSeeds.forEach(this::seedDeadline);
        attendanceSeeds.forEach(this::seedAttendanceRecord);
    }

    public StudentProfile firstStudentProfile() {
        String sql = """
                SELECT name, roll, department, term, cgpa, email, phone
                FROM students
                ORDER BY rowid
                LIMIT 1
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? mapStudent(resultSet) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load the first student profile.", ex);
        }
    }

    public StudentProfile findStudentByRoll(String roll) {
        if (roll == null || roll.isBlank()) return null;
        String sql = """
                SELECT name, roll, department, term, cgpa, email, phone
                FROM students
                WHERE roll = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roll.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapStudent(resultSet) : null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find the student account.", ex);
        }
    }

    public java.util.List<StudentProfile> loadAllStudents() {
        java.util.List<StudentProfile> students = new java.util.ArrayList<>();
        String sql = """
                SELECT name, roll, department, term, cgpa, email, phone
                FROM students
                ORDER BY name COLLATE NOCASE, roll COLLATE NOCASE
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                students.add(mapStudent(resultSet));
            }
            return students;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load student accounts.", ex);
        }
    }

    public TeacherProfile findTeacherById(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) return null;
        String sql = """
                SELECT id, name, department, designation, email, office_room, phone
                FROM teachers
                WHERE id = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacherId.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapTeacher(resultSet) : null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find the teacher account.", ex);
        }
    }

    public java.util.List<TeacherProfile> loadAllTeachers() {
        java.util.List<TeacherProfile> teachers = new java.util.ArrayList<>();
        String sql = """
                SELECT id, name, department, designation, email, office_room, phone
                FROM teachers
                ORDER BY name COLLATE NOCASE, id COLLATE NOCASE
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                teachers.add(mapTeacher(resultSet));
            }
            return teachers;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load teacher accounts.", ex);
        }
    }

    public TeacherProfile findTeacherByName(String teacherName) {
        if (teacherName == null || teacherName.isBlank()) return null;
        String sql = """
                SELECT id, name, department, designation, email, office_room, phone
                FROM teachers
                WHERE name = ? COLLATE NOCASE
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacherName.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapTeacher(resultSet) : null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find the teacher account.", ex);
        }
    }

    public StudentProfile authenticateStudent(String roll, String password) {
        if (roll == null || password == null) return null;
        String sql = """
                SELECT name, roll, department, term, cgpa, email, phone,
                       password_hash, password_salt
                FROM students
                WHERE roll = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roll.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                if (!PasswordUtil.matches(password,
                        resultSet.getString("password_salt"),
                        resultSet.getString("password_hash"))) return null;
                return mapStudent(resultSet);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to authenticate the student account.", ex);
        }
    }

    public TeacherProfile authenticateTeacher(String teacherId, String password) {
        if (teacherId == null || password == null) return null;
        String sql = """
                SELECT id, name, department, designation, email, office_room, phone,
                       password_hash, password_salt
                FROM teachers
                WHERE id = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacherId.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                if (!PasswordUtil.matches(password,
                        resultSet.getString("password_salt"),
                        resultSet.getString("password_hash"))) return null;
                return mapTeacher(resultSet);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to authenticate the teacher account.", ex);
        }
    }

    public FacultyProfile findFacultyByName(String name) {
        if (name == null || name.isBlank()) return null;
        String sql = """
                SELECT name, degree, department, email
                FROM faculty_profiles
                WHERE name = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapFaculty(resultSet) : null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load the faculty profile.", ex);
        }
    }

    public StudentProfile registerStudent(StudentProfile profile, String password) {
        if (findStudentByRoll(profile.roll()) != null)
            throw new IllegalArgumentException("A student account with this roll already exists.");
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        String sql = """
                INSERT INTO students
                    (roll, name, department, term, cgpa, email, phone, password_hash, password_salt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.roll());
            statement.setString(2, profile.name());
            statement.setString(3, profile.department());
            statement.setString(4, profile.term());
            statement.setString(5, profile.cgpa());
            statement.setString(6, profile.email());
            statement.setString(7, profile.phone());
            statement.setString(8, hash);
            statement.setString(9, salt);
            statement.executeUpdate();
            return profile;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to register the student account.", ex);
        }
    }

    public TeacherProfile registerTeacher(TeacherProfile profile, String password) {
        if (findTeacherById(profile.id()) != null)
            throw new IllegalArgumentException("A teacher account with this ID already exists.");
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        String sql = """
                INSERT INTO teachers
                    (id, name, department, designation, email, office_room, phone,
                     password_hash, password_salt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.id());
            statement.setString(2, profile.name());
            statement.setString(3, profile.department());
            statement.setString(4, profile.designation());
            statement.setString(5, profile.email());
            statement.setString(6, profile.officeRoom());
            statement.setString(7, profile.phone());
            statement.setString(8, hash);
            statement.setString(9, salt);
            statement.executeUpdate();
            return profile;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to register the teacher account.", ex);
        }
    }

    public StudentProfile updateStudentProfile(StudentProfile profile) {
        if (profile == null || profile.roll() == null || profile.roll().isBlank())
            throw new IllegalArgumentException("Student roll is required to update the profile.");
        String sql = """
                UPDATE students
                SET name = ?, department = ?, term = ?, cgpa = ?, email = ?, phone = ?
                WHERE roll = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.name());
            statement.setString(2, profile.department());
            statement.setString(3, profile.term());
            statement.setString(4, profile.cgpa());
            statement.setString(5, profile.email());
            statement.setString(6, profile.phone());
            statement.setString(7, profile.roll());
            if (statement.executeUpdate() == 0)
                throw new IllegalArgumentException("No student account was found for this roll.");
            return findStudentByRoll(profile.roll());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update the student profile.", ex);
        }
    }

    public TeacherProfile updateTeacherProfile(TeacherProfile profile) {
        if (profile == null || profile.id() == null || profile.id().isBlank())
            throw new IllegalArgumentException("Teacher ID is required to update the profile.");
        String sql = """
                UPDATE teachers
                SET name = ?, department = ?, designation = ?, email = ?,
                    office_room = ?, phone = ?
                WHERE id = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.name());
            statement.setString(2, profile.department());
            statement.setString(3, profile.designation());
            statement.setString(4, profile.email());
            statement.setString(5, profile.officeRoom());
            statement.setString(6, profile.phone());
            statement.setString(7, profile.id());
            if (statement.executeUpdate() == 0)
                throw new IllegalArgumentException("No teacher account was found for this ID.");
            return findTeacherById(profile.id());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update the teacher profile.", ex);
        }
    }

    public FacultyProfile upsertFacultyProfile(FacultyProfile profile) {
        if (profile == null || profile.name() == null || profile.name().isBlank())
            throw new IllegalArgumentException("Faculty name is required.");
        String sql = """
                INSERT INTO faculty_profiles (name, degree, department, email)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                    degree = excluded.degree,
                    department = excluded.department,
                    email = excluded.email
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.name());
            statement.setString(2, profile.degree());
            statement.setString(3, profile.department());
            statement.setString(4, profile.email());
            statement.executeUpdate();
            return findFacultyByName(profile.name());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save the faculty profile.", ex);
        }
    }

    public void saveChatMessage(ChatMessage message) {
        String sql = """
                INSERT INTO chat_messages
                    (course_code, sender_roll, sender_name, message, timestamp)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, message.courseCode());
            statement.setString(2, message.senderRoll());
            statement.setString(3, message.senderName());
            statement.setString(4, message.content());
            statement.setLong(5, message.timestamp());
            statement.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] saveChatMessage: " + ex.getMessage());
        }
    }

    public java.util.List<ChatMessage> loadChatMessages(String courseCode, int limit) {
        java.util.List<ChatMessage> messages = new java.util.ArrayList<>();
        String sql = """
                SELECT course_code, sender_roll, sender_name, timestamp, message
                FROM chat_messages
                WHERE course_code = ?
                ORDER BY timestamp DESC
                LIMIT ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseCode);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    messages.add(new ChatMessage(
                            rs.getString("course_code"),
                            rs.getString("sender_roll"),
                            rs.getString("sender_name"),
                            rs.getLong("timestamp"),
                            rs.getString("message")));
                }
            }
        } catch (SQLException ex) {
            System.err.println("[DB] loadChatMessages: " + ex.getMessage());
        }
        java.util.Collections.reverse(messages);
        return messages;
    }

    public StudyNote saveNote(StudyNote note) {
        if (note.id() == 0) {
            String sql = """
                    INSERT INTO study_notes
                        (owner_roll, course_code, title, content, is_public, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?)
                    """;
            try (Connection c = connect();
                    PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                long now = System.currentTimeMillis();
                st.setString(1, note.ownerRoll());
                st.setString(2, note.courseCode());
                st.setString(3, note.title());
                st.setString(4, note.content());
                st.setInt(5, note.isPublic() ? 1 : 0);
                st.setLong(6, now);
                st.setLong(7, now);
                st.executeUpdate();
                try (ResultSet keys = st.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new StudyNote(keys.getLong(1), note.ownerRoll(),
                                note.courseCode(), note.title(), note.content(),
                                note.isPublic(), note.isPinned(), now, now);
                    }
                }
            } catch (SQLException ex) {
                System.err.println("[DB] saveNote insert: " + ex.getMessage());
            }
        } else {
            String sql = """
                    UPDATE study_notes
                    SET course_code=?, title=?, content=?, is_public=?, updated_at=?
                    WHERE id=? AND owner_roll=?
                    """;
            try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                st.setString(1, note.courseCode());
                st.setString(2, note.title());
                st.setString(3, note.content());
                st.setInt(4, note.isPublic() ? 1 : 0);
                st.setLong(5, now);
                st.setLong(6, note.id());
                st.setString(7, note.ownerRoll());
                st.executeUpdate();
                return new StudyNote(note.id(), note.ownerRoll(), note.courseCode(),
                        note.title(), note.content(), note.isPublic(), note.isPinned(),
                        note.createdAt(), now);
            } catch (SQLException ex) {
                System.err.println("[DB] saveNote update: " + ex.getMessage());
            }
        }
        return note;
    }

    public void deleteNote(long noteId, String ownerRoll) {
        String deletePinsSql = """
                DELETE FROM study_note_pins
                WHERE note_id IN (
                    SELECT id FROM study_notes WHERE id=? AND owner_roll=?
                )
                """;
        String deleteNoteSql = "DELETE FROM study_notes WHERE id=? AND owner_roll=?";
        try (Connection c = connect();
                PreparedStatement pinStatement = c.prepareStatement(deletePinsSql);
                PreparedStatement noteStatement = c.prepareStatement(deleteNoteSql)) {
            pinStatement.setLong(1, noteId);
            pinStatement.setString(2, ownerRoll);
            pinStatement.executeUpdate();
            noteStatement.setLong(1, noteId);
            noteStatement.setString(2, ownerRoll);
            noteStatement.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] deleteNote: " + ex.getMessage());
        }
    }

    public java.util.List<StudyNote> loadMyNotes(String ownerRoll, String courseCode) {
        String sql = """
                SELECT n.id, n.owner_roll, n.course_code, n.title, n.content,
                       n.is_public,
                       CASE WHEN p.note_id IS NULL THEN 0 ELSE 1 END AS is_pinned,
                       n.created_at, n.updated_at
                FROM study_notes n
                LEFT JOIN study_note_pins p
                    ON p.note_id = n.id AND p.owner_roll = ?
                WHERE n.owner_roll=? AND n.course_code=? COLLATE NOCASE
                ORDER BY is_pinned DESC, n.updated_at DESC
                """;
        java.util.List<StudyNote> result = new java.util.ArrayList<>();
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, ownerRoll);
            st.setString(2, ownerRoll);
            st.setString(3, courseCode);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) result.add(mapNote(rs));
            }
        } catch (SQLException ex) {
            System.err.println("[DB] loadMyNotes: " + ex.getMessage());
        }
        return result;
    }

    public java.util.List<StudyNote> loadSharedNotes(String viewerRoll, String courseCode) {
        String sql = """
                SELECT n.id, n.owner_roll, n.course_code, n.title, n.content,
                       n.is_public,
                       CASE WHEN p.note_id IS NULL THEN 0 ELSE 1 END AS is_pinned,
                       n.created_at, n.updated_at
                FROM study_notes n
                LEFT JOIN study_note_pins p
                    ON p.note_id = n.id AND p.owner_roll = ?
                WHERE n.course_code=? COLLATE NOCASE AND n.is_public=1
                ORDER BY is_pinned DESC, n.updated_at DESC
                """;
        java.util.List<StudyNote> result = new java.util.ArrayList<>();
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, viewerRoll);
            st.setString(2, courseCode.trim());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) result.add(mapNote(rs));
            }
        } catch (SQLException ex) {
            System.err.println("[DB] loadSharedNotes: " + ex.getMessage());
        }
        return result;
    }

    public void setNotePinned(String ownerRoll, long noteId, boolean pinned) {
        if (ownerRoll == null || ownerRoll.isBlank() || noteId <= 0) {
            return;
        }

        if (pinned) {
            String sql = """
                    INSERT INTO study_note_pins (owner_roll, note_id, pinned_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(owner_roll, note_id) DO UPDATE SET
                        pinned_at = excluded.pinned_at
                    """;
            try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
                st.setString(1, ownerRoll);
                st.setLong(2, noteId);
                st.setLong(3, System.currentTimeMillis());
                st.executeUpdate();
            } catch (SQLException ex) {
                System.err.println("[DB] setNotePinned insert: " + ex.getMessage());
            }
            return;
        }

        String sql = "DELETE FROM study_note_pins WHERE owner_roll=? AND note_id=?";
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, ownerRoll);
            st.setLong(2, noteId);
            st.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] setNotePinned delete: " + ex.getMessage());
        }
    }

    private StudyNote mapNote(ResultSet rs) throws SQLException {
        return new StudyNote(
                rs.getLong("id"),
                rs.getString("owner_roll"),
                rs.getString("course_code"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getInt("is_public") == 1,
                rs.getInt("is_pinned") == 1,
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    public record BookBookmark(long id, long bookId, int pageIndex, String label, long createdAt) {}

    public java.util.List<BookBookmark> loadBookBookmarks(String ownerRoll, long bookId) {
        String sql = """
                SELECT bm.id, bm.book_id, bm.page_index, bm.label, bm.created_at
                FROM study_bookmarks bm
                JOIN study_books sb ON sb.id = bm.book_id
                WHERE sb.owner_roll=? AND bm.book_id=?
                ORDER BY bm.page_index ASC, bm.created_at ASC
                """;
        java.util.List<BookBookmark> result = new java.util.ArrayList<>();
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, ownerRoll);
            st.setLong(2, bookId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(mapBookBookmark(rs));
                }
            }
        } catch (SQLException ex) {
            System.err.println("[DB] loadBookBookmarks: " + ex.getMessage());
        }
        return result;
    }

    public BookBookmark saveBookBookmark(String ownerRoll, long bookId, int pageIndex, String label) {
        if (ownerRoll == null || ownerRoll.isBlank() || bookId <= 0 || pageIndex < 0) {
            return null;
        }

        String normalizedLabel = label == null || label.isBlank()
                ? "Page " + (pageIndex + 1)
                : label.trim();
        String findSql = """
                SELECT bm.id, bm.book_id, bm.page_index, bm.label, bm.created_at
                FROM study_bookmarks bm
                JOIN study_books sb ON sb.id = bm.book_id
                WHERE sb.owner_roll=? AND bm.book_id=? AND bm.page_index=?
                """;
        String insertSql = """
                INSERT INTO study_bookmarks (book_id, page_index, label, created_at)
                VALUES (?, ?, ?, ?)
                """;
        String updateSql = "UPDATE study_bookmarks SET label=? WHERE id=?";

        try (Connection c = connect()) {
            try (PreparedStatement find = c.prepareStatement(findSql)) {
                find.setString(1, ownerRoll);
                find.setLong(2, bookId);
                find.setInt(3, pageIndex);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        long bookmarkId = rs.getLong("id");
                        long createdAt = rs.getLong("created_at");
                        try (PreparedStatement update = c.prepareStatement(updateSql)) {
                            update.setString(1, normalizedLabel);
                            update.setLong(2, bookmarkId);
                            update.executeUpdate();
                        }
                        return new BookBookmark(bookmarkId, bookId, pageIndex, normalizedLabel, createdAt);
                    }
                }
            }

            if (!bookOwnedBy(ownerRoll, bookId, c)) {
                return null;
            }

            long createdAt = System.currentTimeMillis();
            try (PreparedStatement insert = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, bookId);
                insert.setInt(2, pageIndex);
                insert.setString(3, normalizedLabel);
                insert.setLong(4, createdAt);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new BookBookmark(keys.getLong(1), bookId, pageIndex, normalizedLabel, createdAt);
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("[DB] saveBookBookmark: " + ex.getMessage());
        }
        return null;
    }

    public void deleteBookBookmark(String ownerRoll, long bookmarkId) {
        if (ownerRoll == null || ownerRoll.isBlank() || bookmarkId <= 0) {
            return;
        }

        String sql = """
                DELETE FROM study_bookmarks
                WHERE id=? AND book_id IN (
                    SELECT id FROM study_books WHERE owner_roll=?
                )
                """;
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setLong(1, bookmarkId);
            st.setString(2, ownerRoll);
            st.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] deleteBookBookmark: " + ex.getMessage());
        }
    }

    private boolean bookOwnedBy(String ownerRoll, long bookId, Connection connection) throws SQLException {
        String sql = "SELECT 1 FROM study_books WHERE id=? AND owner_roll=?";
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setLong(1, bookId);
            st.setString(2, ownerRoll);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private BookBookmark mapBookBookmark(ResultSet rs) throws SQLException {
        return new BookBookmark(
                rs.getLong("id"),
                rs.getLong("book_id"),
                rs.getInt("page_index"),
                rs.getString("label"),
                rs.getLong("created_at"));
    }

    public long addBook(String ownerRoll, String title, String sourceType,
            String sourcePath, String courseCode) {
        String sql = """
                INSERT INTO study_books
                    (owner_roll, title, source_type, source_path, course_code)
                VALUES (?,?,?,?,?)
                """;
        try (Connection c = connect();
                PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, ownerRoll);
            st.setString(2, title);
            st.setString(3, sourceType);
            st.setString(4, sourcePath);
            st.setString(5, courseCode);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException ex) {
            System.err.println("[DB] addBook: " + ex.getMessage());
        }
        return -1;
    }

    public void updateBookProgress(long bookId, int lastPage, String theme) {
        String sql = "UPDATE study_books SET last_page=?, theme=? WHERE id=?";
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setInt(1, lastPage);
            st.setString(2, theme);
            st.setLong(3, bookId);
            st.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] updateBookProgress: " + ex.getMessage());
        }
    }

    public void deleteBook(long bookId, String ownerRoll) {
        String deleteBookmarksSql = """
                DELETE FROM study_bookmarks
                WHERE book_id IN (
                    SELECT id FROM study_books WHERE id=? AND owner_roll=?
                )
                """;
        String deleteBookSql = "DELETE FROM study_books WHERE id=? AND owner_roll=?";
        try (Connection c = connect();
                PreparedStatement bookmarkStatement = c.prepareStatement(deleteBookmarksSql);
                PreparedStatement bookStatement = c.prepareStatement(deleteBookSql)) {
            bookmarkStatement.setLong(1, bookId);
            bookmarkStatement.setString(2, ownerRoll);
            bookmarkStatement.executeUpdate();
            bookStatement.setLong(1, bookId);
            bookStatement.setString(2, ownerRoll);
            bookStatement.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] deleteBook: " + ex.getMessage());
        }
    }

    public record BookEntry(long id, String title, String sourceType,
            String sourcePath, String courseCode, int lastPage, String theme) {}

    public java.util.List<BookEntry> loadBookshelfFull(String ownerRoll) {
        String sql = """
                SELECT id, title, source_type, source_path, course_code, last_page, theme
                FROM study_books
                WHERE owner_roll=?
                ORDER BY added_at DESC
                """;
        java.util.List<BookEntry> result = new java.util.ArrayList<>();
        try (Connection c = connect(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, ownerRoll);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new BookEntry(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("source_type"),
                            rs.getString("source_path"),
                            rs.getString("course_code"),
                            rs.getInt("last_page"),
                            rs.getString("theme")));
                }
            }
        } catch (SQLException ex) {
            System.err.println("[DB] loadBookshelfFull: " + ex.getMessage());
        }
        return result;
    }

    public AcademicDeadline saveDeadline(AcademicDeadline deadline) {
        if (deadline == null) {
            throw new IllegalArgumentException("Deadline data is required.");
        }
        if (deadline.teacherId() == null || deadline.teacherId().isBlank()) {
            throw new IllegalArgumentException("Teacher ID is required.");
        }
        if (deadline.courseCode() == null || deadline.courseCode().isBlank()) {
            throw new IllegalArgumentException("Course code is required.");
        }
        if (deadline.type() == null || deadline.type().isBlank()) {
            throw new IllegalArgumentException("Deadline type is required.");
        }
        if (deadline.title() == null || deadline.title().isBlank()) {
            throw new IllegalArgumentException("Deadline title is required.");
        }
        if (deadline.dueAtEpochMillis() <= 0L) {
            throw new IllegalArgumentException("A due date and time is required.");
        }

        if (deadline.id() == 0L) {
            String sql = """
                    INSERT INTO deadline_items
                        (teacher_id, teacher_name, course_code, item_type, title, details, due_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection connection = connect();
                    PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                long createdAt = deadline.createdAtEpochMillis() > 0L
                        ? deadline.createdAtEpochMillis()
                        : System.currentTimeMillis();
                statement.setString(1, deadline.teacherId());
                statement.setString(2, deadline.teacherName());
                statement.setString(3, deadline.courseCode());
                statement.setString(4, deadline.type());
                statement.setString(5, deadline.title());
                statement.setString(6, deadline.details());
                statement.setLong(7, deadline.dueAtEpochMillis());
                statement.setLong(8, createdAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new AcademicDeadline(
                                keys.getLong(1),
                                deadline.teacherId(),
                                deadline.teacherName(),
                                deadline.courseCode(),
                                deadline.type(),
                                deadline.title(),
                                deadline.details(),
                                deadline.dueAtEpochMillis(),
                                createdAt);
                    }
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save the deadline.", ex);
            }
            throw new IllegalStateException("Failed to generate a deadline ID.");
        }

        String sql = """
                UPDATE deadline_items
                SET teacher_name = ?, course_code = ?, item_type = ?, title = ?, details = ?, due_at = ?
                WHERE id = ? AND teacher_id = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deadline.teacherName());
            statement.setString(2, deadline.courseCode());
            statement.setString(3, deadline.type());
            statement.setString(4, deadline.title());
            statement.setString(5, deadline.details());
            statement.setLong(6, deadline.dueAtEpochMillis());
            statement.setLong(7, deadline.id());
            statement.setString(8, deadline.teacherId());
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Deadline not found for this teacher.");
            }
            return deadline;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update the deadline.", ex);
        }
    }

    public void deleteDeadline(long deadlineId, String teacherId) {
        if (deadlineId <= 0L || teacherId == null || teacherId.isBlank()) {
            return;
        }
        String deleteProgressSql = "DELETE FROM student_deadline_progress WHERE deadline_id = ?";
        String deleteSubmissionsSql = "DELETE FROM assignment_submissions WHERE deadline_id = ?";
        String deleteDeadlineSql = "DELETE FROM deadline_items WHERE id = ? AND teacher_id = ?";
        try (Connection connection = connect();
                PreparedStatement progressStatement = connection.prepareStatement(deleteProgressSql);
                PreparedStatement submissionStatement = connection.prepareStatement(deleteSubmissionsSql);
                PreparedStatement deadlineStatement = connection.prepareStatement(deleteDeadlineSql)) {
            progressStatement.setLong(1, deadlineId);
            progressStatement.executeUpdate();

            submissionStatement.setLong(1, deadlineId);
            submissionStatement.executeUpdate();

            deadlineStatement.setLong(1, deadlineId);
            deadlineStatement.setString(2, teacherId);
            deadlineStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete the deadline.", ex);
        }
    }

    public java.util.List<AcademicDeadline> loadDeadlinesByTeacher(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) {
            return java.util.List.of();
        }
        String sql = """
                SELECT id, teacher_id, teacher_name, course_code, item_type, title, details, due_at, created_at
                FROM deadline_items
                WHERE teacher_id = ?
                ORDER BY due_at ASC, created_at DESC
                """;
        java.util.List<AcademicDeadline> result = new java.util.ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacherId.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapDeadline(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load teacher deadlines.", ex);
        }
        return result;
    }

    public java.util.List<AcademicDeadline> loadDeadlinesForCourse(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            return java.util.List.of();
        }
        String sql = """
                SELECT id, teacher_id, teacher_name, course_code, item_type, title, details, due_at, created_at
                FROM deadline_items
                WHERE course_code = ? COLLATE NOCASE
                ORDER BY due_at ASC, created_at DESC
                """;
        java.util.List<AcademicDeadline> result = new java.util.ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseCode.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapDeadline(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load course deadlines.", ex);
        }
        return result;
    }

    public java.util.List<AcademicDeadline> loadDeadlinesForCourses(java.util.List<String> courseCodes) {
        if (courseCodes == null || courseCodes.isEmpty()) {
            return java.util.List.of();
        }

        java.util.List<String> normalized = courseCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return java.util.List.of();
        }

        String placeholders = normalized.stream().map(code -> "?").collect(java.util.stream.Collectors.joining(", "));
        String sql = """
                SELECT id, teacher_id, teacher_name, course_code, item_type, title, details, due_at, created_at
                FROM deadline_items
                WHERE course_code IN (%s)
                ORDER BY due_at ASC, created_at DESC
                """.formatted(placeholders);

        java.util.List<AcademicDeadline> result = new java.util.ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < normalized.size(); index++) {
                statement.setString(index + 1, normalized.get(index));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapDeadline(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load deadlines for the selected courses.", ex);
        }
        return result;
    }

    public java.util.Set<Long> loadCompletedDeadlineIds(String ownerRoll) {
        if (ownerRoll == null || ownerRoll.isBlank()) {
            return java.util.Set.of();
        }
        String sql = """
                SELECT deadline_id
                FROM student_deadline_progress
                WHERE owner_roll = ? AND is_completed = 1
                """;
        java.util.Set<Long> result = new java.util.LinkedHashSet<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerRoll.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("deadline_id"));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load deadline progress.", ex);
        }
        return result;
    }

    public void setDeadlineCompleted(String ownerRoll, long deadlineId, boolean completed) {
        if (ownerRoll == null || ownerRoll.isBlank() || deadlineId <= 0L) {
            return;
        }

        if (completed) {
            String sql = """
                    INSERT INTO student_deadline_progress
                        (owner_roll, deadline_id, is_completed, completed_at)
                    VALUES (?, ?, 1, ?)
                    ON CONFLICT(owner_roll, deadline_id) DO UPDATE SET
                        is_completed = excluded.is_completed,
                        completed_at = excluded.completed_at
                    """;
            try (Connection connection = connect();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, ownerRoll.trim());
                statement.setLong(2, deadlineId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to update deadline progress.", ex);
            }
            return;
        }

        String sql = "DELETE FROM student_deadline_progress WHERE owner_roll = ? AND deadline_id = ?";
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerRoll.trim());
            statement.setLong(2, deadlineId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update deadline progress.", ex);
        }
    }

    public void saveAttendanceRecords(java.util.List<StudentAttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO student_attendance
                    (teacher_id, course_code, section_name, attendance_date, student_roll, student_name, is_present, marked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(teacher_id, course_code, section_name, attendance_date, student_roll) DO UPDATE SET
                    student_name = excluded.student_name,
                    is_present = excluded.is_present,
                    marked_at = excluded.marked_at
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (StudentAttendanceRecord record : records) {
                validateAttendanceRecord(record);
                statement.setString(1, record.teacherId().trim());
                statement.setString(2, record.courseCode().trim());
                statement.setString(3, clean(record.section()));
                statement.setString(4, record.attendanceDate().trim());
                statement.setString(5, record.studentRoll().trim());
                statement.setString(6, record.studentName().trim());
                statement.setInt(7, record.present() ? 1 : 0);
                statement.setLong(8, record.markedAtEpochMillis() > 0L
                        ? record.markedAtEpochMillis()
                        : System.currentTimeMillis());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save attendance records.", ex);
        }
    }

    public java.util.List<StudentAttendanceRecord> loadAttendanceRecordsForTeacher(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) {
            return java.util.List.of();
        }

        String sql = """
                SELECT id, teacher_id, course_code, section_name, attendance_date,
                       student_roll, student_name, is_present, marked_at
                FROM student_attendance
                WHERE teacher_id = ?
                ORDER BY attendance_date DESC, course_code COLLATE NOCASE, section_name COLLATE NOCASE, student_roll COLLATE NOCASE
                """;
        return queryAttendanceRecords(sql, teacherId.trim());
    }

    public java.util.List<StudentAttendanceRecord> loadAttendanceRecordsForStudent(String studentRoll) {
        if (studentRoll == null || studentRoll.isBlank()) {
            return java.util.List.of();
        }

        String sql = """
                SELECT id, teacher_id, course_code, section_name, attendance_date,
                       student_roll, student_name, is_present, marked_at
                FROM student_attendance
                WHERE student_roll = ?
                ORDER BY course_code COLLATE NOCASE, attendance_date DESC, section_name COLLATE NOCASE
                """;
        return queryAttendanceRecords(sql, studentRoll.trim());
    }

    public AssignmentSubmission loadSubmission(long deadlineId, String studentRoll) {
        if (deadlineId <= 0L || studentRoll == null || studentRoll.isBlank()) {
            return null;
        }

        String sql = """
                SELECT id, deadline_id, teacher_id, course_code, deadline_type, deadline_title, due_at,
                       student_roll, student_name, submission_text, attachment_name, attachment_path,
                       submitted_at, grade, feedback, graded_at, graded_by_id, graded_by_name
                FROM assignment_submissions
                WHERE deadline_id = ? AND student_roll = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, deadlineId);
            statement.setString(2, studentRoll.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapSubmission(rs) : null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load the submission.", ex);
        }
    }

    public java.util.List<AssignmentSubmission> loadSubmissionsForStudent(String studentRoll) {
        if (studentRoll == null || studentRoll.isBlank()) {
            return java.util.List.of();
        }

        String sql = """
                SELECT id, deadline_id, teacher_id, course_code, deadline_type, deadline_title, due_at,
                       student_roll, student_name, submission_text, attachment_name, attachment_path,
                       submitted_at, grade, feedback, graded_at, graded_by_id, graded_by_name
                FROM assignment_submissions
                WHERE student_roll = ?
                ORDER BY due_at ASC, submitted_at DESC
                """;
        java.util.List<AssignmentSubmission> result = new java.util.ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentRoll.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSubmission(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load student submissions.", ex);
        }
        return result;
    }

    public java.util.List<AssignmentSubmission> loadSubmissionsForTeacher(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) {
            return java.util.List.of();
        }

        String sql = """
                SELECT id, deadline_id, teacher_id, course_code, deadline_type, deadline_title, due_at,
                       student_roll, student_name, submission_text, attachment_name, attachment_path,
                       submitted_at, grade, feedback, graded_at, graded_by_id, graded_by_name
                FROM assignment_submissions
                WHERE teacher_id = ?
                ORDER BY due_at ASC, submitted_at DESC
                """;
        java.util.List<AssignmentSubmission> result = new java.util.ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacherId.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSubmission(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load teacher submissions.", ex);
        }
        return result;
    }

    public AssignmentSubmission saveSubmission(AssignmentSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission data is required.");
        }
        if (submission.deadlineId() <= 0L) {
            throw new IllegalArgumentException("A deadline is required for submission.");
        }
        if (submission.teacherId() == null || submission.teacherId().isBlank()) {
            throw new IllegalArgumentException("Teacher ID is required for submission.");
        }
        if (submission.courseCode() == null || submission.courseCode().isBlank()) {
            throw new IllegalArgumentException("Course code is required for submission.");
        }
        if (submission.deadlineTitle() == null || submission.deadlineTitle().isBlank()) {
            throw new IllegalArgumentException("Deadline title is required for submission.");
        }
        if (submission.studentRoll() == null || submission.studentRoll().isBlank()) {
            throw new IllegalArgumentException("Student roll is required for submission.");
        }
        if (submission.studentName() == null || submission.studentName().isBlank()) {
            throw new IllegalArgumentException("Student name is required for submission.");
        }

        String text = submission.submissionText() == null ? "" : submission.submissionText().trim();
        String attachmentName = submission.attachmentName() == null ? "" : submission.attachmentName().trim();
        String attachmentPath = submission.attachmentPath() == null ? "" : submission.attachmentPath().trim();
        if (text.isBlank() && attachmentPath.isBlank()) {
            throw new IllegalArgumentException("Enter submission text or attach a file.");
        }

        long submittedAt = submission.submittedAtEpochMillis() > 0L
                ? submission.submittedAtEpochMillis()
                : System.currentTimeMillis();
        AssignmentSubmission existing = loadSubmission(submission.deadlineId(), submission.studentRoll());
        if (existing == null) {
            String sql = """
                    INSERT INTO assignment_submissions
                        (deadline_id, teacher_id, course_code, deadline_type, deadline_title, due_at,
                         student_roll, student_name, submission_text, attachment_name, attachment_path,
                         submitted_at, grade, feedback, graded_at, graded_by_id, graded_by_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', '', 0, '', '')
                    """;
            try (Connection connection = connect();
                    PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, submission.deadlineId());
                statement.setString(2, submission.teacherId());
                statement.setString(3, submission.courseCode());
                statement.setString(4, submission.deadlineType());
                statement.setString(5, submission.deadlineTitle());
                statement.setLong(6, submission.dueAtEpochMillis());
                statement.setString(7, submission.studentRoll());
                statement.setString(8, submission.studentName());
                statement.setString(9, text);
                statement.setString(10, attachmentName);
                statement.setString(11, attachmentPath);
                statement.setLong(12, submittedAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new AssignmentSubmission(
                                keys.getLong(1),
                                submission.deadlineId(),
                                submission.teacherId(),
                                submission.courseCode(),
                                submission.deadlineType(),
                                submission.deadlineTitle(),
                                submission.dueAtEpochMillis(),
                                submission.studentRoll(),
                                submission.studentName(),
                                text,
                                attachmentName,
                                attachmentPath,
                                submittedAt,
                                "",
                                "",
                                0L,
                                "",
                                "");
                    }
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save the submission.", ex);
            }
            throw new IllegalStateException("Failed to generate a submission ID.");
        }

        String sql = """
                UPDATE assignment_submissions
                SET teacher_id = ?, course_code = ?, deadline_type = ?, deadline_title = ?, due_at = ?,
                    student_name = ?, submission_text = ?, attachment_name = ?, attachment_path = ?, submitted_at = ?,
                    grade = '', feedback = '', graded_at = 0, graded_by_id = '', graded_by_name = ''
                WHERE deadline_id = ? AND student_roll = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, submission.teacherId());
            statement.setString(2, submission.courseCode());
            statement.setString(3, submission.deadlineType());
            statement.setString(4, submission.deadlineTitle());
            statement.setLong(5, submission.dueAtEpochMillis());
            statement.setString(6, submission.studentName());
            statement.setString(7, text);
            statement.setString(8, attachmentName);
            statement.setString(9, attachmentPath);
            statement.setLong(10, submittedAt);
            statement.setLong(11, submission.deadlineId());
            statement.setString(12, submission.studentRoll());
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("No submission record was found for this student.");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update the submission.", ex);
        }
        return loadSubmission(submission.deadlineId(), submission.studentRoll());
    }

    public AssignmentSubmission gradeSubmission(long submissionId,
            String teacherId,
            String teacherName,
            String grade,
            String feedback) {
        if (submissionId <= 0L) {
            throw new IllegalArgumentException("Select a submission first.");
        }
        if (teacherId == null || teacherId.isBlank()) {
            throw new IllegalArgumentException("Teacher ID is required.");
        }

        String normalizedGrade = grade == null ? "" : grade.trim();
        if (normalizedGrade.isBlank()) {
            throw new IllegalArgumentException("Enter a grade before saving.");
        }

        String sql = """
                UPDATE assignment_submissions
                SET grade = ?, feedback = ?, graded_at = ?, graded_by_id = ?, graded_by_name = ?
                WHERE id = ? AND teacher_id = ?
                """;
        long gradedAt = System.currentTimeMillis();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedGrade);
            statement.setString(2, feedback == null ? "" : feedback.trim());
            statement.setLong(3, gradedAt);
            statement.setString(4, teacherId.trim());
            statement.setString(5, teacherName == null ? "" : teacherName.trim());
            statement.setLong(6, submissionId);
            statement.setString(7, teacherId.trim());
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Submission not found for this teacher.");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save the grade.", ex);
        }

        String selectSql = """
                SELECT id, deadline_id, teacher_id, course_code, deadline_type, deadline_title, due_at,
                       student_roll, student_name, submission_text, attachment_name, attachment_path,
                       submitted_at, grade, feedback, graded_at, graded_by_id, graded_by_name
                FROM assignment_submissions
                WHERE id = ?
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setLong(1, submissionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapSubmission(rs);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to reload the graded submission.", ex);
        }
        throw new IllegalStateException("Failed to reload the graded submission.");
    }

    private void seedStudent(StudentProfile profile, String password) {
        if (findStudentByRoll(profile.roll()) == null) registerStudent(profile, password);
    }

    private void seedTeacher(TeacherProfile profile, String password) {
        if (findTeacherById(profile.id()) == null) registerTeacher(profile, password);
    }

    private void seedDeadline(AcademicDeadline deadline) {
        if (!deadlineExists(deadline)) {
            saveDeadline(deadline);
        }
    }

    private void seedAttendanceRecord(StudentAttendanceRecord record) {
        String sql = """
                INSERT OR IGNORE INTO student_attendance
                    (teacher_id, course_code, section_name, attendance_date, student_roll, student_name, is_present, marked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            validateAttendanceRecord(record);
            statement.setString(1, record.teacherId().trim());
            statement.setString(2, record.courseCode().trim());
            statement.setString(3, clean(record.section()));
            statement.setString(4, record.attendanceDate().trim());
            statement.setString(5, record.studentRoll().trim());
            statement.setString(6, record.studentName().trim());
            statement.setInt(7, record.present() ? 1 : 0);
            statement.setLong(8, record.markedAtEpochMillis() > 0L
                    ? record.markedAtEpochMillis()
                    : System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to seed attendance data.", ex);
        }
    }

    private void validateAttendanceRecord(StudentAttendanceRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Attendance record is required.");
        }
        if (record.teacherId() == null || record.teacherId().isBlank()) {
            throw new IllegalArgumentException("Teacher ID is required for attendance.");
        }
        if (record.courseCode() == null || record.courseCode().isBlank()) {
            throw new IllegalArgumentException("Course code is required for attendance.");
        }
        if (record.attendanceDate() == null || record.attendanceDate().isBlank()) {
            throw new IllegalArgumentException("Attendance date is required.");
        }
        if (record.studentRoll() == null || record.studentRoll().isBlank()) {
            throw new IllegalArgumentException("Student roll is required for attendance.");
        }
        if (record.studentName() == null || record.studentName().isBlank()) {
            throw new IllegalArgumentException("Student name is required for attendance.");
        }
    }

    private java.util.List<StudentAttendanceRecord> queryAttendanceRecords(String sql, String parameter) {
        java.util.List<StudentAttendanceRecord> result = new java.util.ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapAttendanceRecord(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load attendance records.", ex);
        }
        return result;
    }

    private String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean deadlineExists(AcademicDeadline deadline) {
        String sql = """
                SELECT 1
                FROM deadline_items
                WHERE teacher_id = ? AND course_code = ? COLLATE NOCASE
                      AND title = ? AND due_at = ?
                LIMIT 1
                """;
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deadline.teacherId());
            statement.setString(2, deadline.courseCode());
            statement.setString(3, deadline.title());
            statement.setLong(4, deadline.dueAtEpochMillis());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to seed the deadline data.", ex);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("SQLite JDBC driver not found.", ex);
        }
    }

    private void initializeSchema() {
        String studentTableSql = """
                CREATE TABLE IF NOT EXISTS students (
                    roll          TEXT PRIMARY KEY COLLATE NOCASE,
                    name          TEXT NOT NULL,
                    department    TEXT NOT NULL,
                    term          TEXT NOT NULL,
                    cgpa          TEXT NOT NULL,
                    email         TEXT NOT NULL,
                    phone         TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        String teacherTableSql = """
                CREATE TABLE IF NOT EXISTS teachers (
                    id            TEXT PRIMARY KEY COLLATE NOCASE,
                    name          TEXT NOT NULL,
                    department    TEXT NOT NULL,
                    designation   TEXT NOT NULL,
                    email         TEXT NOT NULL,
                    office_room   TEXT NOT NULL,
                    phone         TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        String facultyTableSql = """
                CREATE TABLE IF NOT EXISTS faculty_profiles (
                    name       TEXT PRIMARY KEY COLLATE NOCASE,
                    degree     TEXT NOT NULL,
                    department TEXT NOT NULL,
                    email      TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        String chatTableSql = """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    course_code TEXT    NOT NULL,
                    sender_roll TEXT    NOT NULL,
                    sender_name TEXT    NOT NULL,
                    message     TEXT    NOT NULL,
                    timestamp   INTEGER NOT NULL,
                    created_at  INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                )
                """;
        String studyBooksTableSql = """
                CREATE TABLE IF NOT EXISTS study_books (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_roll  TEXT    NOT NULL,
                    title       TEXT    NOT NULL,
                    source_type TEXT    NOT NULL,
                    source_path TEXT    NOT NULL,
                    course_code TEXT,
                    last_page   INTEGER NOT NULL DEFAULT 0,
                    theme       TEXT    NOT NULL DEFAULT 'LIGHT',
                    added_at    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                )
                """;
        String studyBookmarksTableSql = """
                CREATE TABLE IF NOT EXISTS study_bookmarks (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id      INTEGER NOT NULL,
                    page_index   INTEGER NOT NULL,
                    label        TEXT    NOT NULL DEFAULT '',
                    created_at   INTEGER NOT NULL,
                    UNIQUE(book_id, page_index)
                )
                """;
        String studyNotesTableSql = """
                CREATE TABLE IF NOT EXISTS study_notes (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_roll  TEXT    NOT NULL,
                    course_code TEXT    NOT NULL,
                    title       TEXT    NOT NULL,
                    content     TEXT    NOT NULL DEFAULT '',
                    is_public   INTEGER NOT NULL DEFAULT 0,
                    created_at  INTEGER NOT NULL,
                    updated_at  INTEGER NOT NULL
                )
                """;
        String studyNotePinsTableSql = """
                CREATE TABLE IF NOT EXISTS study_note_pins (
                    owner_roll  TEXT    NOT NULL,
                    note_id     INTEGER NOT NULL,
                    pinned_at   INTEGER NOT NULL,
                    PRIMARY KEY (owner_roll, note_id)
                )
                """;
        String deadlineTableSql = """
                CREATE TABLE IF NOT EXISTS deadline_items (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id  TEXT    NOT NULL,
                    teacher_name TEXT   NOT NULL,
                    course_code TEXT    NOT NULL,
                    item_type   TEXT    NOT NULL,
                    title       TEXT    NOT NULL,
                    details     TEXT    NOT NULL DEFAULT '',
                    due_at      INTEGER NOT NULL,
                    created_at  INTEGER NOT NULL
                )
                """;
        String deadlineProgressTableSql = """
                CREATE TABLE IF NOT EXISTS student_deadline_progress (
                    owner_roll   TEXT    NOT NULL,
                    deadline_id  INTEGER NOT NULL,
                    is_completed INTEGER NOT NULL DEFAULT 1,
                    completed_at INTEGER,
                    PRIMARY KEY (owner_roll, deadline_id)
                )
                """;
        String assignmentSubmissionsTableSql = """
                CREATE TABLE IF NOT EXISTS assignment_submissions (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    deadline_id     INTEGER NOT NULL,
                    teacher_id      TEXT    NOT NULL,
                    course_code     TEXT    NOT NULL,
                    deadline_type   TEXT    NOT NULL,
                    deadline_title  TEXT    NOT NULL,
                    due_at          INTEGER NOT NULL,
                    student_roll    TEXT    NOT NULL,
                    student_name    TEXT    NOT NULL,
                    submission_text TEXT    NOT NULL DEFAULT '',
                    attachment_name TEXT    NOT NULL DEFAULT '',
                    attachment_path TEXT    NOT NULL DEFAULT '',
                    submitted_at    INTEGER NOT NULL,
                    grade           TEXT    NOT NULL DEFAULT '',
                    feedback        TEXT    NOT NULL DEFAULT '',
                    graded_at       INTEGER NOT NULL DEFAULT 0,
                    graded_by_id    TEXT    NOT NULL DEFAULT '',
                    graded_by_name  TEXT    NOT NULL DEFAULT '',
                    UNIQUE(deadline_id, student_roll)
                )
                """;
        String studentAttendanceTableSql = """
                CREATE TABLE IF NOT EXISTS student_attendance (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id      TEXT    NOT NULL,
                    course_code     TEXT    NOT NULL,
                    section_name    TEXT    NOT NULL DEFAULT '',
                    attendance_date TEXT    NOT NULL,
                    student_roll    TEXT    NOT NULL,
                    student_name    TEXT    NOT NULL,
                    is_present      INTEGER NOT NULL,
                    marked_at       INTEGER NOT NULL,
                    UNIQUE(teacher_id, course_code, section_name, attendance_date, student_roll)
                )
                """;
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(studentTableSql);
            statement.executeUpdate(teacherTableSql);
            statement.executeUpdate(facultyTableSql);
            statement.executeUpdate(chatTableSql);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_chat_ts ON chat_messages (course_code, timestamp)");
            statement.executeUpdate(studyBooksTableSql);
            statement.executeUpdate(studyBookmarksTableSql);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_bookmarks_book ON study_bookmarks (book_id, page_index)");
            statement.executeUpdate(studyNotesTableSql);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_notes_oc ON study_notes (owner_roll, course_code)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_notes_pub ON study_notes (course_code, is_public)");
            statement.executeUpdate(studyNotePinsTableSql);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_note_pins_owner ON study_note_pins (owner_roll, pinned_at)");
            statement.executeUpdate(deadlineTableSql);
            statement.executeUpdate(deadlineProgressTableSql);
            statement.executeUpdate(assignmentSubmissionsTableSql);
            statement.executeUpdate(studentAttendanceTableSql);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_deadlines_teacher ON deadline_items (teacher_id, due_at)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_deadlines_course ON deadline_items (course_code, due_at)");
            statement.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_deadlines_seed "
                            + "ON deadline_items (teacher_id, course_code, title, due_at)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_deadline_progress_owner "
                            + "ON student_deadline_progress (owner_roll, deadline_id)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_assignment_submissions_teacher "
                            + "ON assignment_submissions (teacher_id, due_at, submitted_at)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_assignment_submissions_student "
                            + "ON assignment_submissions (student_roll, due_at, submitted_at)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_student_attendance_teacher_course "
                            + "ON student_attendance (teacher_id, course_code, attendance_date)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_student_attendance_student_course "
                            + "ON student_attendance (student_roll, course_code, attendance_date)");
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize the database schema.", ex);
        }
    }

    private StudentProfile mapStudent(ResultSet rs) throws SQLException {
        return new StudentProfile(
                rs.getString("name"), rs.getString("roll"),
                rs.getString("department"), rs.getString("term"),
                rs.getString("cgpa"), rs.getString("email"), rs.getString("phone"));
    }

    private TeacherProfile mapTeacher(ResultSet rs) throws SQLException {
        return new TeacherProfile(
                rs.getString("id"), rs.getString("name"),
                rs.getString("department"), rs.getString("designation"),
                rs.getString("email"), rs.getString("office_room"), rs.getString("phone"));
    }

    private FacultyProfile mapFaculty(ResultSet rs) throws SQLException {
        return new FacultyProfile(
                rs.getString("name"), rs.getString("degree"),
                rs.getString("department"), rs.getString("email"));
    }

    private AcademicDeadline mapDeadline(ResultSet rs) throws SQLException {
        return new AcademicDeadline(
                rs.getLong("id"),
                rs.getString("teacher_id"),
                rs.getString("teacher_name"),
                rs.getString("course_code"),
                rs.getString("item_type"),
                rs.getString("title"),
                rs.getString("details"),
                rs.getLong("due_at"),
                rs.getLong("created_at"));
    }

    private AssignmentSubmission mapSubmission(ResultSet rs) throws SQLException {
        return new AssignmentSubmission(
                rs.getLong("id"),
                rs.getLong("deadline_id"),
                rs.getString("teacher_id"),
                rs.getString("course_code"),
                rs.getString("deadline_type"),
                rs.getString("deadline_title"),
                rs.getLong("due_at"),
                rs.getString("student_roll"),
                rs.getString("student_name"),
                rs.getString("submission_text"),
                rs.getString("attachment_name"),
                rs.getString("attachment_path"),
                rs.getLong("submitted_at"),
                rs.getString("grade"),
                rs.getString("feedback"),
                rs.getLong("graded_at"),
                rs.getString("graded_by_id"),
                rs.getString("graded_by_name"));
    }

    private StudentAttendanceRecord mapAttendanceRecord(ResultSet rs) throws SQLException {
        return new StudentAttendanceRecord(
                rs.getLong("id"),
                rs.getString("teacher_id"),
                rs.getString("course_code"),
                rs.getString("section_name"),
                rs.getString("attendance_date"),
                rs.getString("student_roll"),
                rs.getString("student_name"),
                rs.getInt("is_present") == 1,
                rs.getLong("marked_at"));
    }
}
